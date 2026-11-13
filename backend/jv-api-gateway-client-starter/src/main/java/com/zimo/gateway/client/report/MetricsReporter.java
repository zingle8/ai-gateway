package com.zimo.gateway.client.report;

import com.zimo.gateway.client.properties.GatewayClientProperties;
import com.zimo.gateway.client.support.RestClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主机运行指标上报（v0.2 第 10 项）。
 *
 * <p>每 60 秒采集一次并 POST 到 {@code {server-addr}/discovery/metrics/batch}：
 * CPU / 负载 / 内存 / JVM（堆、GC、线程、fd）/ 磁盘容量 / 网络收发速率 / TCP 连接数，
 * 有 NVIDIA GPU 时通过 {@code nvidia-smi} 附带 GPU 利用率与显存；无 GPU 自动跳过。
 *
 * <p><b>边界</b>：不引入 OSHI 依赖，磁盘读写吞吐与 IOPS 在纯 JDK 下拿不到，
 * 留空由平台展示为「-」；网络速率用两次采集的累计计数差 / 间隔计算；
 * 进程 RSS 读取 {@code /proc/self/status}（Linux），其它平台留空。
 * 关闭开关：{@code zimo.gateway.client.metrics.enabled=false}。
 */
@Slf4j
public class MetricsReporter implements ApplicationRunner, DisposableBean {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GatewayClientProperties properties;
    private final RestClient restClient;
    private final ScheduledExecutorService executor;

    /** 上一次的网络累计计数，用于算速率 */
    private final AtomicReference<long[]> lastNet = new AtomicReference<>();
    private volatile long lastSampleAt = 0L;

    public MetricsReporter(GatewayClientProperties properties) {
        this.properties = properties;
        this.restClient = RestClientFactory.create(normalize(properties.getServerAddr()),
                5000L, properties.getClientToken(), properties.getAppId());
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "zimo-gateway-metrics");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.getMetrics().isEnabled()) {
            return;
        }
        long interval = Math.max(10_000L, properties.getMetrics().getInterval());
        // 首次延迟 20 秒，避免与应用启动争抢资源
        executor.scheduleWithFixedDelay(this::reportQuietly, 20, interval / 1000, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private void reportQuietly() {
        try {
            Map<String, Object> payload = collect();
            restClient.post()
                    .uri("/discovery/metrics/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(payload))
                    .retrieve()
                    .toBodilessEntity();
            if (log.isDebugEnabled()) {
                log.debug("[指标] 主机指标已上报：host={} cpu={} mem={}",
                        payload.get("hostname"), payload.get("cpuUsage"), payload.get("memUsedPct"));
            }
        } catch (Exception e) {
            log.debug("[指标] 上报失败（不影响业务）：{}", e.getMessage());
        }
    }

    /** 采集一次指标（公开供测试直接调用） */
    public Map<String, Object> collect() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hostname", hostname());
        m.put("appId", properties.getAppId());
        m.put("serviceName", properties.effectiveServiceId());
        m.put("osName", System.getProperty("os.name"));
        m.put("osVersion", System.getProperty("os.version"));
        m.put("cpuCores", Runtime.getRuntime().availableProcessors());
        m.put("reportTime", LocalDateTime.now().format(TIME_FMT));

        collectCpu(m);
        collectMemory(m);
        collectDisk(m);
        collectNetwork(m);
        collectGpu(m);
        return m;
    }

    // ------------------------------------------------------------------ CPU
    private void collectCpu(Map<String, Object> m) {
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        double load = os.getSystemLoadAverage();
        if (load >= 0) {
            m.put("loadAvg", round(load));
        }
        try {
            java.lang.reflect.Method method = os.getClass().getMethod("getSystemCpuLoad");
            Object value = method.invoke(os);
            if (value instanceof Double d && d >= 0) {
                m.put("cpuUsage", round(d * 100));
            }
        } catch (Exception e) {
            // 非 HotSpot / 模块未开放：留空
            log.trace("[指标] CPU 使用率不可用：{}", e.getMessage());
        }
    }

    // --------------------------------------------------------------- 内存 / JVM
    private void collectMemory(Map<String, Object> m) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        if (heapMax > 0) {
            m.put("jvmHeapPct", round(heapUsed * 100.0 / heapMax));
        }
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() > 0) {
                gcCount += gc.getCollectionCount();
            }
            if (gc.getCollectionTime() > 0) {
                gcTime += gc.getCollectionTime();
            }
        }
        m.put("gcCount", gcCount);
        m.put("gcTimeMs", gcTime);

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        m.put("threadCount", threads.getThreadCount());
        m.put("fdCount", openFdCount());
        m.put("procRss", readProcRss());

        try {
            java.lang.reflect.Method total = ManagementFactory.getOperatingSystemMXBean().getClass()
                    .getMethod("getTotalMemorySize");
            java.lang.reflect.Method free = ManagementFactory.getOperatingSystemMXBean().getClass()
                    .getMethod("getFreeMemorySize");
            Object totalValue = total.invoke(ManagementFactory.getOperatingSystemMXBean());
            Object freeValue = free.invoke(ManagementFactory.getOperatingSystemMXBean());
            if (totalValue instanceof Long t && freeValue instanceof Long f && t > 0) {
                m.put("memTotal", t);
                m.put("memUsedPct", round((t - f) * 100.0 / t));
            }
        } catch (Exception e) {
            log.trace("[指标] 系统内存不可用：{}", e.getMessage());
        }
    }

    private Object openFdCount() {
        try {
            java.lang.reflect.Method method = ManagementFactory.getOperatingSystemMXBean().getClass()
                    .getMethod("getOpenFileDescriptorCount");
            Object value = method.invoke(ManagementFactory.getOperatingSystemMXBean());
            return value instanceof Long l ? l.intValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long readProcRss() {
        Path status = Path.of("/proc/self/status");
        if (!Files.isReadable(status)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(status, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.trim().split("\\s+");
                    return Long.parseLong(parts[1]) * 1024L;
                }
            }
        } catch (Exception e) {
            log.trace("[指标] 读取进程 RSS 失败：{}", e.getMessage());
        }
        return null;
    }

    // ---------------------------------------------------------------- 磁盘
    private void collectDisk(Map<String, Object> m) {
        try {
            File root = new File("/");
            long total = root.getTotalSpace();
            long usable = root.getUsableSpace();
            if (total > 0) {
                m.put("diskTotal", total);
                m.put("diskUsedPct", round((total - usable) * 100.0 / total));
            }
        } catch (Exception e) {
            log.trace("[指标] 磁盘信息不可用：{}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 网络
    private void collectNetwork(Map<String, Object> m) {
        // Java 17 没有网卡累计字节 API，Linux 下解析 /proc/net/dev；其它平台留空
        long rx = 0;
        long tx = 0;
        long[] counters = readProcNetDev();
        if (counters != null) {
            rx = counters[0];
            tx = counters[1];
        }

        long now = System.currentTimeMillis();
        long[] previous = lastNet.getAndSet(new long[]{rx, tx});
        if (previous != null && previous[0] > 0 && lastSampleAt > 0) {
            double seconds = Math.max(1.0, (now - lastSampleAt) / 1000.0);
            m.put("netRxBps", Math.round((rx - previous[0]) / seconds));
            m.put("netTxBps", Math.round((tx - previous[1]) / seconds));
        }
        lastSampleAt = now;
        Integer established = readEstablishedTcp();
        if (established != null) {
            m.put("netConn", established);
        }
    }

    /** /proc/net/dev：跳过 lo，汇总各网卡的收 / 发字节数 */
    private long[] readProcNetDev() {
        Path path = Path.of("/proc/net/dev");
        if (!Files.isReadable(path)) {
            return null;
        }
        long rx = 0;
        long tx = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains(":") || line.trim().startsWith("Inter-") || line.trim().startsWith("face")) {
                    continue;
                }
                String[] parts = line.split(":");
                if (parts.length != 2 || parts[0].trim().equals("lo")) {
                    continue;
                }
                String[] fields = parts[1].trim().split("\\s+");
                if (fields.length >= 9) {
                    rx += Long.parseLong(fields[0]);
                    tx += Long.parseLong(fields[8]);
                }
            }
            return new long[]{rx, tx};
        } catch (Exception e) {
            return null;
        }
    }

    /** /proc/net/tcp 中状态 01 = ESTABLISHED */
    private Integer readEstablishedTcp() {
        Path path = Path.of("/proc/net/tcp");
        if (!Files.isReadable(path)) {
            return null;
        }
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length > 3 && "01".equals(fields[3])) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- GPU
    private void collectGpu(Map<String, Object> m) {
        if (!properties.getMetrics().isGpuEnabled()) {
            return;
        }
        try {
            Process process = new ProcessBuilder("nvidia-smi",
                    "--query-gpu=utilization.gpu,memory.used,memory.total",
                    "--format=csv,noheader,nounits")
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return;
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        lines.add(line.trim());
                    }
                }
            }
            if (lines.isEmpty()) {
                return;
            }
            double utilSum = 0;
            double memUsed = 0;
            double memTotal = 0;
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    utilSum += Double.parseDouble(parts[0].trim());
                    memUsed += Double.parseDouble(parts[1].trim());
                    memTotal += Double.parseDouble(parts[2].trim());
                }
            }
            m.put("gpuCount", lines.size());
            m.put("gpuUtil", round(utilSum / lines.size()));
            if (memTotal > 0) {
                m.put("gpuMemPct", round(memUsed * 100 / memTotal));
            }
        } catch (Exception e) {
            // 无 nvidia-smi / 无 GPU：静默跳过
            log.trace("[指标] GPU 指标跳过：{}", e.getMessage());
        }
    }

    // --------------------------------------------------------------- 工具
    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getenv().getOrDefault("HOSTNAME", "unknown-host");
        }
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static String normalize(String addr) {
        if (addr == null || addr.isBlank()) {
            return "http://127.0.0.1:28090/api";
        }
        String value = addr.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** 供测试：清空上次采集的累计值 */
    public void resetBaseline() {
        lastNet.set(null);
        lastSampleAt = 0L;
    }
}
