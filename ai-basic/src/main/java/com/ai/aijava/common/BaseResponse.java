package com.ai.aijava.common;

import com.ai.aijava.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应封装类
 * 所有 REST 接口的返回值统一使用此类包装，保证响应结构一致。
 * 成功状态码为 0，失败时使用 ErrorCode 枚举定义的错误码。
 *
 * @param <T> 响应数据类型
 */
@Data
public class BaseResponse<T> implements Serializable {

    /** 业务状态码（0 表示成功，4xxxx 客户端错误，5xxxx 服务端错误） */
    private int code;

    /** 响应数据体 */
    private T data;

    /** 提示信息（成功时通常为空或 "ok"） */
    private String message;

    /**
     * 全参构造
     *
     * @param code    业务状态码
     * @param data    响应数据
     * @param message 提示信息
     */
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    /**
     * 无 message 的便捷构造，message 默认为空字符串
     *
     * @param code 业务状态码
     * @param data 响应数据
     */
    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    /**
     * 基于 ErrorCode 枚举的构造，自动填充 code 和 message
     *
     * @param errorCode 错误码枚举
     */
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
