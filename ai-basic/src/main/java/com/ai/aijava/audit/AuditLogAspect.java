package com.ai.aijava.audit;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.annotation.AuditLog;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.utils.IpUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 审计日志切面
 * 拦截 @AuditLog 注解方法，记录操作信息：
 * 1. 发布 AuditLogEvent → ai-web 侧异步落库
 * 2. 写结构化日志（logger 名为当前类，由 logback 路由到 logs/audit/audit.log）
 * 审计记录失败不影响主业务流程
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    /** 请求参数最大长度（字符） */
    private static final int MAX_PARAMS_LENGTH = 2000;

    /** User-Agent 最大长度（字符） */
    private static final int MAX_USER_AGENT_LENGTH = 256;

    /** 异常信息最大长度（字符） */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    /** 密码等敏感字段脱敏（JSON 字符串形式："password":"xxx" → "password":"***"） */
    private static final Pattern SENSITIVE_FIELD_PATTERN =
            Pattern.compile("(\"(password|oldPassword|newPassword)\"\\s*:\\s*\")[^\"]*(\")");

    private static final String MASK = "$1***$3";

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 环绕拦截所有标注 @AuditLog 的方法
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Throwable error = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            // 审计记录自身异常不向外抛，保证不影响主业务
            try {
                long executionTime = System.currentTimeMillis() - startTime;
                publishAuditLog(joinPoint, auditLog, executionTime, error);
            } catch (Exception ex) {
                log.error("审计日志记录失败: method={}", joinPoint.getSignature(), ex);
            }
        }
    }

    /**
     * 构建审计事件：发布 Spring 事件（落库）+ 写结构化日志（文件）
     */
    private void publishAuditLog(ProceedingJoinPoint joinPoint, AuditLog auditLog,
                                 long executionTime, Throwable error) {
        HttpServletRequest request = currentRequest();

        AuditLogEvent event = AuditLogEvent.builder()
                .userId(UserContext.getUserId())
                .username(UserContext.getUsername())
                .operationType(auditLog.type().name())
                .operationModule(auditLog.module())
                .description(auditLog.description())
                .requestUri(request != null ? request.getRequestURI() : null)
                .requestMethod(request != null ? request.getMethod() : null)
                .requestParams(request != null ? buildParams(joinPoint.getArgs()) : null)
                .ipAddress(request != null ? IpUtils.getClientIp(request) : null)
                .userAgent(request != null ? truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH) : null)
                .result(error != null ? AuditLogResult.FAILURE.name() : AuditLogResult.SUCCESS.name())
                .errorMessage(error != null ? truncate(error.getMessage(), MAX_ERROR_MESSAGE_LENGTH) : null)
                .executionTime(executionTime)
                .methodSignature(joinPoint.getSignature().toShortString())
                .operateTime(LocalDateTime.now())
                .build();

        // 1. 发布事件，由 ai-web 侧 AuditLogService 异步落库
        eventPublisher.publishEvent(event);

        // 2. 写结构化日志（管道分隔，便于 grep/解析），logger 由 logback 路由到审计文件
        log.info("AUDIT|{}|{}|{}|{}|{}|{}|{}|{}ms|{}",
                event.getOperationType(),
                event.getOperationModule(),
                event.getUsername() != null ? event.getUsername() : "anonymous",
                event.getIpAddress(),
                event.getRequestMethod() + " " + event.getRequestUri(),
                event.getResult(),
                event.getErrorMessage() != null ? event.getErrorMessage() : "-",
                event.getExecutionTime(),
                event.getDescription());
    }

    /**
     * 从 RequestContextHolder 获取当前请求（非 Web 环境返回 null）
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 序列化方法参数：过滤 Servlet/文件等不可序列化对象，脱敏密码字段，超长截断
     */
    private String buildParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> serializableArgs = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof ServletRequest || arg instanceof ServletResponse
                    || arg instanceof MultipartFile) {
                continue;
            }
            serializableArgs.add(arg);
        }
        if (serializableArgs.isEmpty()) {
            return null;
        }
        String json;
        try {
            json = JSONUtil.toJsonStr(serializableArgs);
        } catch (Exception e) {
            json = serializableArgs.toString();
        }
        // 密码等敏感字段脱敏，防止明文入库
        json = SENSITIVE_FIELD_PATTERN.matcher(json).replaceAll(MASK);
        return truncate(json, MAX_PARAMS_LENGTH);
    }

    /**
     * 字符串截断（null 安全）
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
