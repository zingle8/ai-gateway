package com.zimo.gateway.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户模块启动类
 */
@MapperScan("com.zimo.gateway.user.mapper")
@SpringBootApplication(scanBasePackages = "com.zimo.gateway")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
