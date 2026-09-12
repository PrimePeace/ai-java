package com.ai.aijava.exception;

import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 * 使用 @RestControllerAdvice 统一拦截所有 Controller 层抛出的异常，
 * 将其转换为 BaseResponse 格式返回，避免异常堆栈直接暴露给前端。
 */
@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理上传文件超过大小限制异常
     * 框架层 multipart 上限（spring.servlet.multipart.max-file-size）触发，
     * 返回参数错误码与友好提示，而非 500
     *
     * @param e 上传超限异常
     * @return 包含错误码和提示信息的响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public BaseResponse<?> maxUploadSizeExceededExceptionHandler(MaxUploadSizeExceededException e) {
        log.warn("MaxUploadSizeExceededException: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "上传文件过大，请压缩后重试");
    }

    /**
     * 处理自定义业务异常（BusinessException）
     * 记录异常日志后，将业务异常的状态码和提示信息返回给前端。
     *
     * @param e 业务异常
     * @return 包含错误码和错误信息的响应
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理运行时异常（RuntimeException）
     * 兜底捕获所有未预期的运行时异常，统一返回系统内部错误，
     * 防止异常堆栈和敏感信息暴露给前端。
     *
     * @param e 运行时异常
     * @return 包含系统错误码和固定提示的响应
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
