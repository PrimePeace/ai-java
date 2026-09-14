package com.ai.aijava.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public GroupedOpenApi webApi() {
        return GroupedOpenApi.builder()
                .group("ai-web")
                .packagesToScan("com.ai.aijava.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi agentApi() {
        return GroupedOpenApi.builder()
                .group("ai-agent")
                .packagesToScan("com.ai.aijava.agent.controller")
                .build();
    }
}