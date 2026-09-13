package com.ai.aijava.agent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ai-agent 模块 Web MVC 配置（内聚在 ai-agent，避免依赖 ai-web 配置）
 */
@Configuration
@RequiredArgsConstructor
public class AgentWebConfig implements WebMvcConfigurer {

    private final McpSecurityInterceptor mcpSecurityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // MCP 端点拦截（STREAMABLE 单端点 /mcp；若 fallback SSE 则还需 /sse + /mcp/messages）
        registry.addInterceptor(mcpSecurityInterceptor)
                .addPathPatterns("/mcp", "/mcp/**", "/sse", "/mcp/messages");
    }
}
