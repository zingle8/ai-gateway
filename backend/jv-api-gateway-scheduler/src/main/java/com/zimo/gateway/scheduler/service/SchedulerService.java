package com.zimo.gateway.scheduler.service;

import com.zimo.gateway.common.constant.GwConfigKeys;
import com.zimo.gateway.common.constant.GwRedisKeys;
import com.zimo.gateway.scheduler.constant.SchedulerConstants;
import com.zimo.gateway.scheduler.entity.SchedulerCleanTableEntity;
import com.zimo.gateway.scheduler.entity.SchedulerJobEntity;
import com.zimo.gateway.scheduler.entity.SchedulerPartitionConfigEntity;
import com.zimo.gateway.scheduler.entity.SchedulerPlanEntity;
import com.zimo.gateway.scheduler.entity.SchedulerRunLogEntity;
import com.zimo.gateway.scheduler.lock.DistributedLockService;
import com.zimo.gateway.scheduler.mapper.SchedulerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * 调度核心服务：配置双存储读写、历史数据清理任务、分区预建任务。
 *
 * 触发方式：每分钟 tick 检查「当前时刻 == 配置的调度时间」且「今日未成功执行」，
 * 再抢分布式锁执行——多实例部署下只有一个实例真正干活。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private static final String CONFIG_CACHE_KEY_CLEAN_RUN_TIME =
            GwRedisKeys.SCHEDULER_CONFIG_PREFIX + "clean-run-time";

    private final SchedulerMapper mapper;
    private final DistributedLockService lockService;
    private final StringRedisTemplate redis;

    @Value("${scheduler.clean-run-time-key:" + GwConfigKeys.SCHEDULER_CLEAN_RUN_TIME + "}")
    private String cleanTimeKey;

    @Value("${scheduler.clean-default-run-time:" + SchedulerConstants.DEFAULT_CLEAN_RUN_TIME + "}")
    private String cleanDefaultRunTime;

    @Value("${scheduler.partition-offset-minutes:10}")
    private int partitionOffsetMinutes;

    @Value("${scheduler.delete-batch-size:5000}")
    private int deleteBatchSize;

    private String hostTag = "unknown";

    @jakarta.annotation.PostConstruct
    void initHost() {
        try {
            hostTag = InetAddress.getLocalHost().getHostAddress() + ":" + 28087;
        } catch (Exception ignore) {
            // 取不到本机地址时保持 unknown，不影响调度
        }
    }

    /* ==================== 配置双存储（Redis 优先，MySQL 持久） ==================== */

    /** 读调度时间：Redis 缓存优先，未命中读 MySQL 并回填 Redis */
    public String getCleanRunTime() {
        try {
            String v = redis.opsForValue().get(CONFIG_CACHE_KEY_CLEAN_RUN_TIME);
            if (v != null) {
                return v;
            }
        } catch (Exception e) {
            log.warn("Redis 配置缓存不可用，读 MySQL: {}", e.getMessage());
        }
        String v = mapper.selectConfig(cleanTimeKey);
        if (v == null || v.isBlank()) {
            v = cleanDefaultRunTime;
        }
        writeThrough(v);
        return v;
    }

    /** 写调度时间：MySQL 与 Redis 双写（系统设置页保存入口） */
    public void updateCleanRunTime(String runTime) {
        if (runTime == null || !runTime.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new IllegalArgumentException("调度时间格式应为 HH:mm");
        }
        mapper.upsertConfig(cleanTimeKey, runTime, "历史数据清理任务每日执行时间");
        mapper.updateJobRunTime(SchedulerConstants.JOB_DATA_CLEAN, runTime);
        String partition = LocalTime.parse(runTime, SchedulerConstants.HH_MM)
                .plusMinutes(partitionOffsetMinutes).format(SchedulerConstants.HH_MM);
        mapper.updateJobRunTime(SchedulerConstants.JOB_PARTITION_DAILY, partition);
        writeThrough(runTime);
    }

    private void writeThrough(String runTime) {
        try {
            // TTL 60 秒：调度时间可能经通用配置页修改，短 TTL 保证最终一致；调度判断本身每日一次，MySQL 直读无压力
            redis.opsForValue().set(CONFIG_CACHE_KEY_CLEAN_RUN_TIME, runTime, Duration.ofSeconds(60));
        } catch (Exception e) {
            log.warn("Redis 配置缓存写入失败（MySQL 已持久化）: {}", e.getMessage());
        }
    }

    /* ==================== 定时触发（每分钟 tick） ==================== */

    /** 每分钟检查：到达调度时间且今日未成功执行 → 清理任务 */
    @Scheduled(cron = "0 * * * * ?")
    public void tickClean() {
        try {
            LocalTime target = LocalTime.parse(getCleanRunTime(), SchedulerConstants.HH_MM);
            if (!LocalTime.now().truncatedTo(ChronoUnit.MINUTES).equals(target)) {
                return;
            }
            if (mapper.countSuccessToday(SchedulerConstants.JOB_DATA_CLEAN) > 0) {
                return;
            }
            runClean(SchedulerConstants.RUN_SCHEDULED);
        } catch (Exception e) {
            log.error("清理任务触发异常", e);
        }
    }

    /** 每分钟检查：清理时间 + offset 分钟 → 分区预建任务 */
    @Scheduled(cron = "0 * * * * ?")
    public void tickPartition() {
        try {
            LocalTime target = LocalTime.parse(getCleanRunTime(), SchedulerConstants.HH_MM)
                    .plusMinutes(partitionOffsetMinutes);
            if (!LocalTime.now().truncatedTo(ChronoUnit.MINUTES).equals(target)) {
                return;
            }
            if (mapper.countSuccessToday(SchedulerConstants.JOB_PARTITION_DAILY) > 0) {
                return;
            }
            runPartition(SchedulerConstants.RUN_SCHEDULED);
        } catch (Exception e) {
            log.error("分区预建任务触发异常", e);
        }
    }

    /* ==================== 历史数据清理（物理删除，分批） ==================== */

    /** 执行清理任务（定时 / 手动共用），返回执行记录 */
    public SchedulerRunLogEntity runClean(String runType) {
        String holder = lockService.tryLock(SchedulerConstants.JOB_DATA_CLEAN, 3600);
        if (holder == null) {
            log.info("清理任务已被其他实例持有锁，跳过");
            return null;
        }
        Date start = new Date();
        long totalDeleted = 0;
        StringBuilder detail = new StringBuilder();
        String status = SchedulerConstants.STATUS_SUCCESS;
        String error = null;
        try {
            List<SchedulerCleanTableEntity> tables = mapper.listCleanTables();
            for (SchedulerCleanTableEntity t : tables) {
                if (mapper.tableExists(t.getTableName()) == 0) {
                    detail.append(t.getTableName()).append(":表不存在，跳过; ");
                    continue;
                }
                long keepDays = t.getKeepDays() == null ? SchedulerConstants.DEFAULT_KEEP_DAYS : t.getKeepDays();
                Date cutoff = Date.from(LocalDateTime.now().minusDays(keepDays)
                        .atZone(ZoneId.systemDefault()).toInstant());
                long deleted = 0;
                int batch;
                int loops = 0;
                while ((batch = mapper.deleteRowsByDate(t.getTableName(), t.getTimeColumn(), cutoff, deleteBatchSize)) > 0
                        && loops++ < 10000) {
                    deleted += batch;
                }
                totalDeleted += deleted;
                detail.append(t.getTableName()).append(":删除").append(deleted).append("行(保留").append(keepDays).append("天); ");
                log.info("清理 {}: {} 行", t.getTableName(), deleted);
            }
        } catch (Exception e) {
            status = SchedulerConstants.STATUS_FAILED;
            error = e.getMessage();
            log.error("清理任务执行失败", e);
        } finally {
            lockService.unlock(SchedulerConstants.JOB_DATA_CLEAN, holder);
        }
        Date end = new Date();
        saveRunLog(SchedulerConstants.JOB_DATA_CLEAN, runType, null, start, end, status, totalDeleted, detail.toString(), error);
        return latestRunLog(SchedulerConstants.JOB_DATA_CLEAN);
    }

    /* ==================== 分区预建（提前 2 天生成计划并执行） ==================== */

    /** 执行分区预建任务：为每张配置表补齐 [今天, 今天+advanceDays] 的每日分区 */
    public SchedulerRunLogEntity runPartition(String runType) {
        String holder = lockService.tryLock(SchedulerConstants.JOB_PARTITION_DAILY, 1800);
        if (holder == null) {
            log.info("分区任务已被其他实例持有锁，跳过");
            return null;
        }
        Date start = new Date();
        long created = 0;
        StringBuilder detail = new StringBuilder();
        String status = SchedulerConstants.STATUS_SUCCESS;
        String error = null;
        try {
            List<SchedulerPartitionConfigEntity> configs = mapper.listPartitionConfigs();
            LocalDate today = LocalDate.now();
            for (SchedulerPartitionConfigEntity cfg : configs) {
                if (mapper.tableExists(cfg.getTableName()) == 0) {
                    detail.append(cfg.getTableName()).append(":表不存在，跳过; ");
                    continue;
                }
                int advance = cfg.getAdvanceDays() == null
                        ? SchedulerConstants.DEFAULT_ADVANCE_DAYS : cfg.getAdvanceDays();
                for (int i = 0; i <= advance; i++) {
                    LocalDate day = today.plusDays(i);
                    String partitionName = "p" + day.format(SchedulerConstants.DAY);
                    if (mapper.partitionExists(cfg.getTableName(), partitionName) > 0) {
                        continue;
                    }
                    String boundary = day.plusDays(1).atStartOfDay().format(SchedulerConstants.BOUNDARY);
                    SchedulerPlanEntity plan = new SchedulerPlanEntity();
                    plan.setJobKey(SchedulerConstants.JOB_PARTITION_DAILY);
                    plan.setPlanType(SchedulerConstants.PLAN_ADD_PARTITION);
                    plan.setPlanDate(java.sql.Date.valueOf(day));
                    plan.setTarget(cfg.getTableName());
                    plan.setPartitionName(partitionName);
                    plan.setPartitionValue(boundary);
                    plan.setStatus(SchedulerConstants.STATUS_PENDING);
                    mapper.insertPlan(plan);
                    try {
                        mapper.addDailyPartition(cfg.getTableName(), partitionName, boundary);
                        mapper.updatePlanStatus(plan.getId(), SchedulerConstants.STATUS_DONE, "ADD PARTITION 成功");
                        created++;
                        detail.append(cfg.getTableName()).append(".").append(partitionName).append(" ");
                    } catch (Exception pe) {
                        mapper.updatePlanStatus(plan.getId(), SchedulerConstants.STATUS_FAILED, pe.getMessage());
                        log.error("分区 {} 创建失败: {}", partitionName, pe.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            status = SchedulerConstants.STATUS_FAILED;
            error = e.getMessage();
            log.error("分区预建任务失败", e);
        } finally {
            lockService.unlock(SchedulerConstants.JOB_PARTITION_DAILY, holder);
        }
        Date end = new Date();
        saveRunLog(SchedulerConstants.JOB_PARTITION_DAILY, runType, null, start, end, status, created, detail.toString(), error);
        return latestRunLog(SchedulerConstants.JOB_PARTITION_DAILY);
    }

    /* ==================== 查询（调度计划页数据支撑） ==================== */

    public List<SchedulerJobEntity> listJobs() {
        List<SchedulerJobEntity> jobs = mapper.listJobs();
        jobs.forEach(j -> {
            if (SchedulerConstants.JOB_DATA_CLEAN.equals(j.getJobKey())) {
                j.setRunTime(getCleanRunTime());
            }
        });
        return jobs;
    }

    /** 执行计划列表（分区预建计划等） */
    public List<SchedulerPlanEntity> listPlans(String jobKey, int limit) {
        return mapper.listPlans(jobKey, Math.min(Math.max(limit, 1), 200));
    }

    /** 执行记录列表 */
    public List<SchedulerRunLogEntity> listRunLogs(String jobKey, int limit) {
        return mapper.listRunLogs(jobKey, Math.min(Math.max(limit, 1), 200));
    }

    /** 清理清单配置 */
    public List<SchedulerCleanTableEntity> listCleanTables() {
        return mapper.listCleanTables();
    }

    /** 任务键是否合法（控制器校验用） */
    public static boolean isKnownJob(String jobKey) {
        return SchedulerConstants.JOB_DATA_CLEAN.equals(jobKey)
                || SchedulerConstants.JOB_PARTITION_DAILY.equals(jobKey);
    }

    private SchedulerRunLogEntity latestRunLog(String jobKey) {
        return mapper.listRunLogs(jobKey, 1).stream().findFirst().orElse(null);
    }

    private void saveRunLog(String jobKey, String runType, String target, Date start, Date end,
                            String status, long rows, String detail, String error) {
        SchedulerRunLogEntity log = new SchedulerRunLogEntity();
        log.setJobKey(jobKey);
        log.setRunType(runType);
        log.setTarget(target);
        log.setStartTime(start);
        log.setEndTime(end);
        log.setStatus(status);
        log.setTotalRows(rows);
        detail = detail == null ? "" : detail;
        log.setDetail(detail.length() > 2000 ? detail.substring(0, 2000) : detail);
        log.setError(error);
        log.setHost(hostTag);
        mapper.insertRunLog(log);
        mapper.updateJobLastRun(jobKey, end, status);
    }
}
