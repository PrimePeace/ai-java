package com.ai.aijava.controller;

import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.annotation.RequirePermission;
import com.ai.aijava.auth.Permissions;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import com.ai.aijava.dto.request.RoleMenuAssignRequest;
import com.ai.aijava.dto.request.RoleUpdateRequest;
import com.ai.aijava.dto.vo.RoleVO;
import com.ai.aijava.service.RoleAdminService;
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
 * 角色维护。
 */
@Tag(name = "角色")
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleAdminService roleAdminService;

    @Operation(summary = "角色列表")
    @RequireLogin
    @RequirePermission(Permissions.ROLE_VIEW)
    @GetMapping("/list")
    public BaseResponse<List<RoleVO>> list() {
        return ResultUtils.success(roleAdminService.list());
    }

    @Operation(summary = "修改角色名称和备注")
    @RequireLogin
    @RequirePermission(Permissions.ROLE_VIEW)
    @PostMapping("/update")
    public BaseResponse<Void> update(@RequestBody @Valid RoleUpdateRequest request) {
        roleAdminService.update(request);
        return ResultUtils.success(null);
    }

    @Operation(summary = "保存角色菜单")
    @RequireLogin
    @RequirePermission(Permissions.ROLE_ASSIGN_MENU)
    @PostMapping("/assign-menu")
    public BaseResponse<Void> assignMenu(@RequestBody @Valid RoleMenuAssignRequest request) {
        roleAdminService.assignMenus(request);
        return ResultUtils.success(null);
    }
}
