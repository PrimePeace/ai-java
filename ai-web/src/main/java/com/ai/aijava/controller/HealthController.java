package com.ai.aijava.controller;

import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查 Controller
 * 提供应用健康状态查询接口，用于负载均衡探活和运维监控。
 */
@RestController
@RequestMapping("/health")
@Tag(name = "健康检查", description = "应用健康状态查询")
public class HealthController {

    /**
     * 健康检查接口
     *
     * @return 固定返回 "ok"，表示应用正常运行
     */
    @GetMapping("/")
    @Operation(summary = "健康检查", description = "返回应用健康状态，用于探活检测")
    public BaseResponse<String> healthCheck() {
        return ResultUtils.success("ok");
    }
}
