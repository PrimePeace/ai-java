package com.ai.aijava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限码。标注在方法或类上，由 JwtInterceptor 按当前用户角色校验。
 * 有此注解时同时要求登录。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限码，对应 menu.permission */
    String value();
}
