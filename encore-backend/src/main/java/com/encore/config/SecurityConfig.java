package com.encore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 仅提供密码哈希器(BCrypt)。本项目鉴权由 Sa-Token 负责，这里不引入 Spring Security 过滤器链，
 * 因此不会与 Sa-Token 拦截器冲突。BCrypt 自带盐值，matches(raw, hash) 可直接校验明文。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
