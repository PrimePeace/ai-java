package com.ai.aijava.controller;

import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.annotation.RequirePermission;
import com.ai.aijava.auth.Permissions;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import com.ai.aijava.dto.request.UserAssignRoleRequest;
import com.ai.aijava.dto.request.UserStatusRequest;
import com.ai.aijava.dto.vo.UserVO;
import com.ai.aijava.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员用户管理。
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户列表")
    @RequireLogin
    @RequirePermission(Permissions.USER_LIST)
    @GetMapping("/list")
    public BaseResponse<List<UserVO>> list() {
        return ResultUtils.success(adminUserService.list());
    }

    @Operation(summary = "封禁或解封")
    @RequireLogin
    @RequirePermission(Permissions.USER_BAN)
    @PostMapping("/status")
    public BaseResponse<Void> updateStatus(@RequestBody @Valid UserStatusRequest request) {
        adminUserService.updateStatus(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "调整角色")
    @RequireLogin
    @RequirePermission(Permissions.USER_ASSIGN_ROLE)
    @PostMapping("/assign-role")
    public BaseResponse<Void> assignRole(@RequestBody @Valid UserAssignRoleRequest request) {
        adminUserService.assignRole(request);
        return ResultUtils.success(null);
    }
}
