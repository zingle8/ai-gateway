package com.zimo.gateway.scheduler.mapper;

import com.zimo.gateway.scheduler.entity.SchedulerCleanTableEntity;
import com.zimo.gateway.scheduler.entity.SchedulerJobEntity;
import com.zimo.gateway.scheduler.entity.SchedulerPartitionConfigEntity;
import com.zimo.gateway.scheduler.entity.SchedulerPlanEntity;
import com.zimo.gateway.scheduler.entity.SchedulerRunLogEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 调度中心 Mapper（注解 SQL）
 *
 * 说明：deleteRowsByDate / addDailyPartition 中的表名、列名、分区名
 * 均来自 tb_scheduler_clean_table / tb_scheduler_partition_config 配置表
 * （仅管理员可改），不直接来自外部输入，无注入面。
 */
public interface SchedulerMapper {

    /* ==================== 任务定义 ==================== */

    @Select("SELECT job_key, job_name, schedule_type, run_time, status, last_run_time, last_run_status, remark FROM tb_scheduler_job ORDER BY job_key")
    List<SchedulerJobEntity> listJobs();

    @Select("SELECT job_key, job_name, schedule_type, run_time, status, last_run_time, last_run_status, remark FROM tb_scheduler_job WHERE job_key = #{jobKey}")
    SchedulerJobEntity getJob(@Param("jobKey") String jobKey);

    @Update("UPDATE tb_scheduler_job SET last_run_time = #{time}, last_run_status = #{status} WHERE job_key = #{jobKey}")
    int updateJobLastRun(@Param("jobKey") String jobKey, @Param("time") Date time, @Param("status") String status);

    @Update("UPDATE tb_scheduler_job SET run_time = #{runTime} WHERE job_key = #{jobKey}")
    int updateJobRunTime(@Param("jobKey") String jobKey, @Param("runTime") String runTime);

    /* ==================== 执行计划 ==================== */

    @Select("<script>SELECT id, job_key, plan_type, plan_date, target, partition_name, partition_value, status, detail, create_time, done_time FROM tb_scheduler_plan"
            + " WHERE job_key = #{jobKey} ORDER BY plan_date DESC, id DESC LIMIT #{limit}</script>")
    List<SchedulerPlanEntity> listPlans(@Param("jobKey") String jobKey, @Param("limit") int limit);

    @Insert("INSERT INTO tb_scheduler_plan(job_key, plan_type, plan_date, target, partition_name, partition_value, status, detail, create_time)"
            + " VALUES(#{jobKey}, #{planType}, #{planDate}, #{target}, #{partitionName}, #{partitionValue}, #{status}, #{detail}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPlan(SchedulerPlanEntity plan);

    @Update("UPDATE tb_scheduler_plan SET status = #{status}, detail = #{detail}, done_time = NOW() WHERE id = #{id}")
    int updatePlanStatus(@Param("id") Long id, @Param("status") String status, @Param("detail") String detail);

    @Select("SELECT COUNT(*) FROM tb_scheduler_plan WHERE job_key = #{jobKey} AND target = #{target} AND partition_name = #{partitionName}")
    int countPlan(@Param("jobKey") String jobKey, @Param("target") String target, @Param("partitionName") String partitionName);

    /* ==================== 执行记录 ==================== */

    @Select("<script>SELECT id, job_key, run_type, target, start_time, end_time, status, total_rows, detail, error, host FROM tb_scheduler_run_log"
            + " WHERE job_key = #{jobKey} ORDER BY id DESC LIMIT #{limit}</script>")
    List<SchedulerRunLogEntity> listRunLogs(@Param("jobKey") String jobKey, @Param("limit") int limit);

    @Insert("INSERT INTO tb_scheduler_run_log(job_key, run_type, target, start_time, end_time, status, total_rows, detail, error, host)"
            + " VALUES(#{jobKey}, #{runType}, #{target}, #{startTime}, #{endTime}, #{status}, #{totalRows}, #{detail}, #{error}, #{host})")
    int insertRunLog(SchedulerRunLogEntity log);

    @Select("SELECT COUNT(*) FROM tb_scheduler_run_log WHERE job_key = #{jobKey} AND status = 'SUCCESS' AND start_time >= CURDATE()")
    int countSuccessToday(@Param("jobKey") String jobKey);

    /* ==================== 清理 / 分区配置 ==================== */

    @Select("SELECT id, table_name, time_column, keep_days, enabled, remark FROM tb_scheduler_clean_table WHERE enabled = 1")
    List<SchedulerCleanTableEntity> listCleanTables();

    @Select("SELECT id, table_name, partition_column, partition_type, advance_days, enabled FROM tb_scheduler_partition_config WHERE enabled = 1")
    List<SchedulerPartitionConfigEntity> listPartitionConfigs();

    /* ==================== 物理清理（动态表名/列名，来源受控） ==================== */

    @Select("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = #{tableName}")
    int tableExists(@Param("tableName") String tableName);

    @Delete("DELETE FROM ${tableName} WHERE ${timeColumn} < #{cutoff} LIMIT #{batchSize}")
    int deleteRowsByDate(@Param("tableName") String tableName, @Param("timeColumn") String timeColumn,
                         @Param("cutoff") Date cutoff, @Param("batchSize") int batchSize);

    /* ==================== 分区管理（动态表名，来源受控） ==================== */

    @Select("SELECT IFNULL(MAX(PARTITION_DESCRIPTION), 0) FROM information_schema.PARTITIONS"
            + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = #{tableName}")
    long maxPartitionDesc(@Param("tableName") String tableName);

    @Select("SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE()"
            + " AND TABLE_NAME = #{tableName} AND PARTITION_NAME = #{partitionName}")
    int partitionExists(@Param("tableName") String tableName, @Param("partitionName") String partitionName);

    @Delete("ALTER TABLE ${tableName} ADD PARTITION (PARTITION ${partitionName} VALUES LESS THAN (UNIX_TIMESTAMP(#{boundary})))")
    int addDailyPartition(@Param("tableName") String tableName, @Param("partitionName") String partitionName,
                          @Param("boundary") String boundary);

    /* ==================== 分布式锁（MySQL 兜底） ==================== */

    @Delete("DELETE FROM tb_scheduler_lock WHERE lock_key = #{lockKey} AND (expire_at < NOW() OR holder = #{holder})")
    int releaseOrExpireLock(@Param("lockKey") String lockKey, @Param("holder") String holder);

    @Insert("INSERT IGNORE INTO tb_scheduler_lock(lock_key, holder, expire_at, create_time) VALUES(#{lockKey}, #{holder}, #{expireAt}, NOW())")
    int tryLockMysql(@Param("lockKey") String lockKey, @Param("holder") String holder, @Param("expireAt") Date expireAt);

    @Delete("DELETE FROM tb_scheduler_lock WHERE lock_key = #{lockKey} AND holder = #{holder}")
    int unlockMysql(@Param("lockKey") String lockKey, @Param("holder") String holder);

    /* ==================== 系统配置（tb_sys_config 读写） ==================== */

    @Select("SELECT config_value FROM tb_sys_config WHERE config_key = #{key} LIMIT 1")
    String selectConfig(@Param("key") String key);

    @Insert("INSERT INTO tb_sys_config(config_key, config_value, value_type, config_source, platform_name, app_name, service_name, remark, update_time)"
            + " VALUES(#{key}, #{value}, 'STRING', 'DB', 'API网关平台', 'jv-api-gateway', 'jv-api-gateway-scheduler', #{remark}, NOW())"
            + " ON DUPLICATE KEY UPDATE config_value = #{value}, update_time = NOW()")
    int upsertConfig(@Param("key") String key, @Param("value") String value, @Param("remark") String remark);

    /* ==================== 分区表当日数据量统计（任务状态详情用） ==================== */

    @Select("SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = #{tableName}")
    int partitionCount(@Param("tableName") String tableName);
}
