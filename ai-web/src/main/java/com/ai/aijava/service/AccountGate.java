package com.ai.aijava.service;

import com.ai.aijava.auth.CredentialRules;
import com.ai.aijava.entity.Menu;
import com.ai.aijava.entity.Role;
import com.ai.aijava.entity.RoleMenu;
import com.ai.aijava.entity.User;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.mapper.MenuMapper;
import com.ai.aijava.mapper.RoleMapper;
import com.ai.aijava.mapper.RoleMenuMapper;
import com.ai.aijava.mapper.UserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.ai.aijava.entity.table.MenuTableDef.MENU;
import static com.ai.aijava.entity.table.RoleMenuTableDef.ROLE_MENU;
import static com.ai.aijava.entity.table.RoleTableDef.ROLE;

/**
 * 登录后的账号可用性与权限校验。每次请求读库，角色变更立即生效。
 */
@Service
@RequiredArgsConstructor
public class AccountGate {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    /**
     * 密码校验通过后调用。状态或角色不可用时拒绝登录。
     */
    public void assertUsable(User user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "账号不可用");
        }
        Role role = user.getRoleId() == null ? null : roleMapper.selectOneById(user.getRoleId());
        if (role == null || role.getStatus() == null || role.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "账号不可用");
        }
    }

    /**
     * 已登录请求：账号不可用时按未登录处理，便于客户端清除登录态。
     */
    public User requireActive(Long userId) {
        User user = userMapper.selectOneById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "账号不可用");
        }
        Role role = user.getRoleId() == null ? null : roleMapper.selectOneById(user.getRoleId());
        if (role == null || role.getStatus() == null || role.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "账号不可用");
        }
        return user;
    }

    public void assertRefreshUsable(User user, Date issuedAt) {
        assertUsable(user);
        if (!CredentialRules.isRefreshIssuedAfter(issuedAt, user.getRefreshInvalidBefore())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "登录已失效，请重新登录");
        }
    }

    public void requirePermission(Long userId, String permission) {
        User user = requireActive(userId);
        if (!hasPermission(user.getRoleId(), permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限");
        }
    }

    public boolean hasPermission(Long roleId, String permission) {
        if (roleId == null || permission == null || permission.isBlank()) {
            return false;
        }
        List<RoleMenu> links = roleMenuMapper.selectListByQuery(
                QueryWrapper.create().where(ROLE_MENU.ROLE_ID.eq(roleId)));
        if (links.isEmpty()) {
            return false;
        }
        List<Long> menuIds = links.stream().map(RoleMenu::getMenuId).filter(Objects::nonNull).toList();
        List<Menu> menus = menuMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(MENU.ID.in(menuIds))
                        .and(MENU.STATUS.eq(1)));
        return menus.stream().anyMatch(menu -> permission.equals(menu.getPermission()));
    }

    public Role findRole(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return roleMapper.selectOneById(roleId);
    }

    public Role requireRoleByCode(String code) {
        Role role = roleMapper.selectOneByQuery(QueryWrapper.create().where(ROLE.CODE.eq(code)));
        if (role == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "角色数据未初始化");
        }
        return role;
    }
}
