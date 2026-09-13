package com.ai.aijava.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex Mapper 扫描。
 * 数据源 / SqlSessionFactory 由 {@code mybatis-flex-spring-boot4-starter} + {@code spring-boot-starter-jdbc} 自动配置。
 */
@Configuration
@MapperScan({"com.ai.aijava.mapper", "com.ai.aijava.agent.mapper"})
public class MyBatisFlexConfig {
}
