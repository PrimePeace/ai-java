package com.ai.aijava.annotation;

import com.ai.aijava.audit.AuditLogType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 * 标注在 Controller/Service 方法上，由 AuditLogAspect 拦截并记录操作日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作类型
     */
    AuditLogType type();

    /**
     * 业务模块名称（如 "用户管理"）
     */
    String module() default "";

    /**
     * 操作描述
     */
    String description() default "";
}
