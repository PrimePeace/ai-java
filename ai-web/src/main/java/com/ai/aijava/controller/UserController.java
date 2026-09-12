package com.ai.aijava.controller;

import com.ai.aijava.annotation.AuditLog;
import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.audit.AuditLogType;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import com.ai.aijava.dto.request.RefreshTokenRequest;
import com.ai.aijava.dto.request.UserLoginRequest;
import com.ai.aijava.dto.request.UserRegisterRequest;
import com.ai.aijava.dto.vo.UserLoginVO;
import com.ai.aijava.dto.vo.UserVO;
import com.ai.aijava.service.UserService;
import com.ai.aijava.utils.IpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证接口（注册/登录/刷新/用户信息）
 */
@Tag(name = "用户认证")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public BaseResponse<UserVO> register(@RequestBody @Valid UserRegisterRequest request) {
        UserVO userVO = userService.register(request);
        return ResultUtils.success(userVO);
    }

    @Operation(summary = "用户登录")
    @AuditLog(type = AuditLogType.LOGIN, module = "用户认证", description = "用户登录")
    @PostMapping("/login")
    public BaseResponse<UserLoginVO> login(@RequestBody @Valid UserLoginRequest request,
                                           HttpServletRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        UserLoginVO loginVO = userService.login(request, clientIp);
        return ResultUtils.success(loginVO);
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public BaseResponse<UserLoginVO> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        UserLoginVO loginVO = userService.refreshToken(request.getRefreshToken());
        return ResultUtils.success(loginVO);
    }

    @Operation(summary = "获取当前用户信息")
    @RequireLogin
    @GetMapping("/current")
    public BaseResponse<UserVO> getCurrentUser() {
        UserVO userVO = userService.getCurrentUser();
        return ResultUtils.success(userVO);
    }
}