package com.ai.aijava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ai-java 项目启动类
 * Spring Boot 应用入口，负责初始化 IoC 容器、自动配置和组件扫描。
 */
@SpringBootApplication
public class AiJavaApplication {

    /**
     * 应用启动入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiJavaApplication.class, args);
    }

}
