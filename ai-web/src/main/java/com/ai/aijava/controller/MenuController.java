package com.ai.aijava.controller;

import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.annotation.RequirePermission;
import com.ai.aijava.auth.Permissions;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.dto.vo.MenuVO;
import com.ai.aijava.dto.request.MenuSaveRequest;
import com.ai.aijava.service.AccountGate;
import com.ai.aijava.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单导航与维护。
 */
@Tag(name = "菜单")
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final AccountGate accountGate;

    @Operation(summary = "当前用户菜单树")
    @RequireLogin
    @GetMapping("/nav")
    public BaseResponse<List<MenuVO>> nav(@RequestParam(defaultValue = "web") String client) {
        return ResultUtils.success(menuService.nav(accountGate.requireActive(UserContext.getUserId()), client));
    }

    @Operation(summary = "全部菜单")
    @RequireLogin
    @RequirePermission(Permissions.MENU_VIEW)
    @GetMapping("/list")
    public BaseResponse<List<MenuVO>> list() {
        return ResultUtils.success(menuService.listAll());
    }

    @Operation(summary = "新增或修改菜单")
    @RequireLogin
    @RequirePermission(Permissions.MENU_EDIT)
    @PostMapping("/save")
    public BaseResponse<Void> save(@RequestBody @Valid MenuSaveRequest request) {
        menuService.save(request);
        return ResultUtils.success(null);
    }
}
