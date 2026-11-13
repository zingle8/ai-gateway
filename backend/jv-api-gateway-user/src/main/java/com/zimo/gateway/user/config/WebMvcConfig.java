package com.zimo.gateway.user.config;

import com.zimo.gateway.user.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：CORS + 登录拦截
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /auth/logout 不排除：退出需携带有效令牌，以便服务端自增 token_version 使令牌失效
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        // 登录因子（v0.2 第 4 项）：验证码 / 动态码 / 短信·邮件码 / 扫码，登录前匿名调用
                        "/auth/captcha/**",
                        "/auth/otp/**",
                        "/auth/qr/**",
                        "/auth/login-methods",
                        // 开放认证：给第三方平台对接，自身不带平台令牌（总开关由系统设置控制）
                        "/open/auth/**",
                        // SSO OIDC 协议端点（v0.2 第 15 项）：授权 / 换 token / userinfo / 元数据 / jwks
                        // 均匿名可访问；authorize 靠 gw_token Cookie 判 IdP 会话，token 靠 client_secret
                        "/oauth/**",
                        "/.well-known/**",
                        "/error",
                        "/actuator/**"
                );
    }
}
