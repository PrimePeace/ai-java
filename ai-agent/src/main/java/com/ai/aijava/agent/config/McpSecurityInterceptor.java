package com.ai.aijava.agent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MCP 端点安全拦截器
 * 校验 X-MCP-Token 请求头与 mcp.security.token 一致
 * 未配置 token 时放行 + 打告警（开发便利），已配置时强校验
 */
@Slf4j
@Component
public class McpSecurityInterceptor implements HandlerInterceptor {

    private static final String HEADER_MCP_TOKEN = "X-MCP-Token";

    @Value("${mcp.security.token:}")
    private String mcpToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // mcpToken 未配置 → 放行 + 告警
        if (mcpToken == null || mcpToken.isBlank()) {
            log.warn("MCP 端点未配置 token（mcp.security.token），所有请求将被放行！生产环境必须配置！");
            return true;
        }
        String header = request.getHeader(HEADER_MCP_TOKEN);
        if (!mcpToken.equals(header)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":40103,\"message\":\"MCP token 无效或为空\"}");
            } catch (Exception e) {
                // 忽略写入异常
            }
            return false;
        }
        return true;
    }
}
