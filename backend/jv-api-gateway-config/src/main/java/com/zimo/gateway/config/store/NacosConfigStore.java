package com.zimo.gateway.config.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Nacos 来源：以 properties 文本承载多个 key（dataId 级文件，field = config_key）。
 *
 * <p>走 Nacos 3.x 的 v3 admin OpenAPI（gRPC 端口之外的 HTTP 8999/8848 均可）：
 * <ul>
 *   <li>登录取 token：POST /nacos/v3/auth/user/login</li>
 *   <li>读配置：GET /nacos/v3/admin/cs/config?dataId=&amp;groupName=&amp;namespaceId=</li>
 *   <li>发布：POST /nacos/v3/admin/cs/config（表单）</li>
 *   <li>删除：DELETE /nacos/v3/admin/cs/config</li>
 * </ul>
 *
 * <p>写单个 key 时先读整份文件、替换目标行再整体发布，避免覆盖同文件里的其他配置。
 */
@Slf4j
@Component
public class NacosConfigStore implements ConfigStore {

    public static final String SOURCE = "NACOS";

    private final NacosStoreProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public NacosConfigStore(NacosStoreProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeoutMs());
        factory.setReadTimeout(props.getTimeoutMs());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    private volatile String accessToken;
    private volatile long tokenExpireAt;

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String label(String sourceRef) {
        NacosTarget t = target(sourceRef);
        return "Nacos · " + props.getServerAddr() + " · " + t.group() + "/" + t.dataId();
    }

    @Override
    public String read(String key, String sourceRef) {
        String content = fetchContent(target(sourceRef));
        return parse(content).get(key);
    }

    @Override
    public void write(String key, String value, String sourceRef) {
        NacosTarget t = target(sourceRef);
        String content = fetchContent(t);
        publish(t, upsert(content, key, value));
    }

    @Override
    public void delete(String key, String sourceRef) {
        NacosTarget t = target(sourceRef);
        String content = fetchContent(t);
        if (content == null) {
            return;
        }
        String next = removeLine(content, key);
        if (hasEntries(next)) {
            publish(t, next);
        } else {
            removeConfig(t);
        }
    }

    // ------------------------------------------------------------------ HTTP

    private String fetchContent(NacosTarget t) {
        try {
            String body = callWithToken(token -> http.get()
                    .uri(props.getServerAddr().startsWith("http") ? props.getServerAddr() : "http://" + props.getServerAddr()
                            + "/nacos/v3/admin/cs/config?dataId={dataId}&groupName={group}&namespaceId={ns}&accessToken={token}",
                            t.dataId(), t.group(), props.getNamespace(), token)
                    .retrieve()
                    .body(String.class));
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            JsonNode content = data.path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (ConfigStoreException e) {
            // 配置不存在时 Nacos 返回 404（code=20004），等价于"暂无值"，交由上层用快照/新建处理
            if (e.getCause() instanceof RestClientResponseException rce && rce.getStatusCode().value() == 404) {
                return null;
            }
            throw e;
        } catch (Exception e) {
            throw new ConfigStoreException("Nacos 读取失败：" + e.getMessage(), e);
        }
    }

    private void publish(NacosTarget t, String content) {
        String form = "dataId=" + enc(t.dataId())
                + "&groupName=" + enc(t.group())
                + "&namespaceId=" + enc(props.getNamespace())
                + "&type=properties"
                + "&content=" + enc(content);
        String body = callWithToken(token -> http.post()
                .uri(props.getServerAddr().startsWith("http") ? props.getServerAddr() : "http://" + props.getServerAddr()
                        + "/nacos/v3/admin/cs/config?accessToken={token}", token)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class));
        assertSuccess(body, "发布");
    }

    private void removeConfig(NacosTarget t) {
        String body = callWithToken(token -> http.delete()
                .uri(props.getServerAddr().startsWith("http") ? props.getServerAddr() : "http://" + props.getServerAddr()
                        + "/nacos/v3/admin/cs/config?dataId={dataId}&groupName={group}&namespaceId={ns}&accessToken={token}",
                        t.dataId(), t.group(), props.getNamespace(), token)
                .retrieve()
                .body(String.class));
        assertSuccess(body, "删除");
    }

    /** 带 token 调用；401/403 时强制重登并重试一次 */
    private String callWithToken(TokenCall call) {
        String token = token();
        try {
            return call.execute(token);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                invalidateToken();
                return call.execute(token());
            }
            throw new ConfigStoreException("Nacos HTTP " + e.getStatusCode().value() + "：" + e.getResponseBodyAsString(), e);
        }
    }

    private String token() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < tokenExpireAt) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpireAt) {
                return accessToken;
            }
            String form = "username=" + enc(props.getUsername()) + "&password=" + enc(props.getPassword());
            String body;
            try {
                body = http.post()
                        .uri(base() + "/nacos/v3/auth/user/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String.class);
                JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
                String token = root.path("accessToken").asText(null);
                long ttl = root.path("tokenTtl").asLong(18000);
                if (token == null || token.isBlank()) {
                    throw new ConfigStoreException("Nacos 登录失败：" + body);
                }
                accessToken = token;
                // 提前 5 分钟过期，避免边界失效
                tokenExpireAt = System.currentTimeMillis() + Math.max(60, ttl - 300) * 1000L;
                return token;
            } catch (ConfigStoreException e) {
                throw e;
            } catch (Exception e) {
                throw new ConfigStoreException("Nacos 登录失败：" + e.getMessage(), e);
            }
        }
    }

    private void invalidateToken() {
        accessToken = null;
        tokenExpireAt = 0;
    }

    private void assertSuccess(String body, String action) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            if (root.path("code").asInt(-1) != 0 || !root.path("data").asBoolean(false)) {
                throw new ConfigStoreException("Nacos " + action + "失败：" + body);
            }
        } catch (ConfigStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigStoreException("Nacos " + action + "响应无法解析：" + body, e);
        }
    }

    // ------------------------------------------------------------- 文本处理

    /** 解析 properties：忽略注释与空行，值保留等号后的全部内容 */
    private java.util.Map<String, String> parse(String content) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return map;
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            map.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return map;
    }

    /** 替换或追加 key=value 行，保留其他行与顺序 */
    private String upsert(String content, String key, String value) {
        List<String> lines = content == null || content.isBlank()
                ? new ArrayList<>() : new ArrayList<>(Arrays.asList(content.split("\n")));
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0 && trimmed.substring(0, eq).trim().equals(key)) {
                lines.set(i, key + "=" + value);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lines.add(key + "=" + value);
        }
        return String.join("\n", lines);
    }

    /** 删除指定 key 行（注释行原样保留） */
    private String removeLine(String content, String key) {
        List<String> kept = new ArrayList<>();
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            int eq = trimmed.indexOf('=');
            boolean target = !trimmed.startsWith("#") && !trimmed.startsWith("!") && eq > 0
                    && trimmed.substring(0, eq).trim().equals(key);
            if (!target) {
                kept.add(line);
            }
        }
        return String.join("\n", kept).replaceAll("\\n+$", "");
    }

    private boolean hasEntries(String content) {
        return !parse(content).isEmpty();
    }

    // ------------------------------------------------------------------ utils

    private String base() {
        return props.getServerAddr().startsWith("http") ? props.getServerAddr() : "http://" + props.getServerAddr();
    }

    private String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private NacosTarget target(String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank()) {
            return new NacosTarget(props.getDataId(), props.getGroup());
        }
        String[] parts = sourceRef.split("\\|", -1);
        String dataId = parts[0].trim();
        String group = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : props.getGroup();
        return new NacosTarget(dataId, group);
    }

    private record NacosTarget(String dataId, String group) {
    }

    @FunctionalInterface
    private interface TokenCall {
        String execute(String token);
    }
}
