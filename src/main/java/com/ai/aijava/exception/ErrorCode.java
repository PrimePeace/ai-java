package com.ai.aijava.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),
    REGISTER_ERROR(40001, "注册失败，用户名已存在"),
    LOGIN_ERROR(40002, "用户名或密码错误"),
    ACCOUNT_LOCKED(40003, "账号已被锁定，请稍后再试"),
    TOKEN_EXPIRED(40102, "Token 已过期"),
    TOKEN_INVALID(40103, "Token 无效");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
