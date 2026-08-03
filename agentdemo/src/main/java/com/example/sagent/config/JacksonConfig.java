package com.example.sagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置
 * Spring Boot 4 默认 JSON 引擎为 Jackson 3（tools.jackson.*），只自动配置 JsonMapper，
 * 不再自动注册 Jackson 2（com.fasterxml.jackson.*）的 ObjectMapper Bean。
 * 本项目经 Spring AI 2.0 使用 Jackson 2，故此处显式注册 ObjectMapper 供注入。
 * 独立配置类，不依赖任何业务 Bean，避免循环依赖。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
