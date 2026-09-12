package com.ai.aijava.exception;

import lombok.Getter;

/**
 * 错误码枚举
 *
 * 统一定义所有业务错误码，避免魔法数字。
 * 编码规则：
 * - 0     : 成功
 * - 40000 : 客户端参数错误
 * - 401xx : 认证/授权相关错误
 * - 403xx : 禁止访问
 * - 404xx : 资源不存在
 * - 500xx : 服务端内部 错误
 */
@Getter
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, "ok"),
    /** 请求参数错误 */
    PARAMS_ERROR(40000, "请求参数错误"),
    /** 未登录 */
    NOT_LOGIN_ERROR(40100, "未登录"),
    /** 无权限 */
    NO_AUTH_ERROR(40101, "无权限"),
    /** Token 已过期 */
    TOKEN_EXPIRED(40102, "Token 已过期"),
    /** Token 无效 */
    TOKEN_INVALID(40103, "Token 无效"),
    /** 请求数据不存在 */
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    /** 禁止访问 */
    FORBIDDEN_ERROR(40300, "禁止访问"),
    /** 注册失败（通常因用户名已存在） */
    REGISTER_ERROR(40001, "注册失败，用户名已存在"),
    /** 登录失败（用户名或密码错误） */
    LOGIN_ERROR(40002, "用户名或密码错误"),
    /** 账号被锁定 */
    ACCOUNT_LOCKED(40003, "账号已被锁定，请后再试"),
    /** 系统内部异常 */
    SYSTEM_ERROR(50000, "系统内部异常"),
    /** 操作失败 */
    OPERATION_ERROR(50001, "操作失败");

    /** 状态码 */
    private final int code;

    /** 提示信息 */
    private final String message;

    /**
     * 构造方法
     *
     * @param code    错误码
     * @param message 提示信息
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
