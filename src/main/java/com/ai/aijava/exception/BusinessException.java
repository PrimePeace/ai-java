package com.ai.aijava.exception;

import lombok.Getter;

/**
 * 自定义业务异常
 * 当业务逻辑校验失败时抛出此异常，携带错误码和提示信息。
 * 由 GlobalExceptionHandler 统一捕获并转换为响应格式返回前端。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    /**
     * 使用自定义错误码和消息构造
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用 ErrorCode 枚举构造，自动获取错误码和消息
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 使用 ErrorCode 枚举的错误码，但自定义错误消息
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误信息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
