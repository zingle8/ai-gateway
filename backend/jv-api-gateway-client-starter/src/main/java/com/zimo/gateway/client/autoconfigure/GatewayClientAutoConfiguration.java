package com.zimo.gateway.client.autoconfigure;

import com.zimo.gateway.client.client.GatewayPlatformClient;
import com.zimo.gateway.client.client.TracePropagationInterceptor;
import com.zimo.gateway.client.properties.GatewayClientProperties;
import com.zimo.gateway.client.registry.ServiceRegistrar;
import com.zimo.gateway.client.report.TraceReporter;
import com.zimo.gateway.client.web.GatewayTraceFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 网关接入自动装配。
 *
 * 业务系统只需引入依赖并在配置文件中填写 zimo.gateway.client.* 即可完成自动接入：
 * 1. 启动时向网关平台注册服务实例；
 * 2. 运行期采集请求 / 响应并异步批量上报；
 * 3. 进程退出时主动下线。
 */
@AutoConfiguration
@EnableConfigurationProperties(GatewayClientProperties.class)
@ConditionalOnProperty(prefix = GatewayClientProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayClientAutoConfiguration {

    /**
     * 日志上报器：内存队列 + 异步批量 POST
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    public TraceReporter traceReporter(GatewayClientProperties properties) {
        return new TraceReporter(properties);
    }

    /**
     * 平台服务调用客户端：供其他项目调用网关平台能力
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RestClient.class)
    public GatewayPlatformClient gatewayPlatformClient(GatewayClientProperties properties) {
        return new GatewayPlatformClient(properties);
    }

    /**
     * 链路传播：业务方通过 Spring Boot 自动装配的 RestClient.Builder 创建的客户端
     * 自动获得 CLIENT span 采集与 traceparent 注入；业务方自建 client 不受影响。
     */
    @Bean
    @ConditionalOnClass(RestClient.class)
    public RestClientCustomizer tracePropagationCustomizer(GatewayClientProperties properties,
                                                           TraceReporter reporter) {
        TracePropagationInterceptor interceptor = new TracePropagationInterceptor(properties, reporter);
        return builder -> builder.requestInterceptor(interceptor);
    }

    /**
     * 服务注册：应用启动即注册（自动接入）
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceRegistrar serviceRegistrar(GatewayClientProperties properties, Environment environment) {
        return new ServiceRegistrar(properties, environment);
    }

    /**
     * Web 环境下的请求采集过滤器
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({ OncePerRequestFilter.class, jakarta.servlet.http.HttpServletRequest.class })
    public static class WebTraceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public FilterRegistrationBean<GatewayTraceFilter> gatewayTraceFilter(
                GatewayClientProperties properties, TraceReporter reporter) {
            FilterRegistrationBean<GatewayTraceFilter> registration =
                    new FilterRegistrationBean<>(new GatewayTraceFilter(properties, reporter));
            registration.addUrlPatterns("/*");
            registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
            registration.setName("gatewayTraceFilter");
            return registration;
        }
    }
}
