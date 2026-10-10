package com.ai.aijava.agent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MCP 端点默认关闭。只有 mcp.security.enabled=true 且 token 匹配时放行。
 */
@Component
public class McpSecurityInterceptor implements HandlerInterceptor {

    private static final String HEADER_MCP_TOKEN = "X-MCP-Token";

    @Value("${mcp.security.enabled:false}")
    private boolean enabled;

    @Value("${mcp.security.token:}")
    private String mcpToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled || mcpToken == null || mcpToken.isBlank()) {
            writeUnauthorized(response, "MCP 未开启");
            return false;
        }
        String header = request.getHeader(HEADER_MCP_TOKEN);
        if (!mcpToken.equals(header)) {
            writeUnauthorized(response, "MCP token 无效或为空");
            return false;
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"code\":40103,\"message\":\"" + message + "\"}");
        } catch (Exception ignored) {
            // 响应已提交时忽略
        }
    }
}
