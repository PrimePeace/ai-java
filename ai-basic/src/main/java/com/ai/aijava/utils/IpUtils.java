package com.ai.aijava.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析工具类
 * 优先级：X-Forwarded-For → X-Real-IP → getRemoteAddr()，代理链取第一个
 */
public class IpUtils {

    /**
     * 获取客户端真实 IP
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 代理链取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
