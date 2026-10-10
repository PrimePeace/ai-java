package com.ai.aijava.service;

import com.ai.aijava.dto.request.RoleMenuAssignRequest;
import com.ai.aijava.dto.request.RoleUpdateRequest;
import com.ai.aijava.dto.vo.RoleVO;
import com.ai.aijava.entity.Menu;
import com.ai.aijava.entity.Role;
import com.ai.aijava.entity.RoleMenu;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.mapper.MenuMapper;
import com.ai.aijava.mapper.RoleMapper;
import com.ai.aijava.mapper.RoleMenuMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.ai.aijava.entity.table.RoleTableDef.ROLE;

/**
 * 角色维护与菜单分配。内置角色不可删除、不可改编码。
 */
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuService menuService;

    public List<RoleVO> list() {
        List<Role> roles = roleMapper.selectListByQuery(QueryWrapper.create().orderBy(ROLE.ID.asc()));
        return roles.stream().map(role -> RoleVO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .status(role.getStatus())
                .remark(role.getRemark())
                .menuIds(menuService.roleMenuIds(role.getId()))
                .build()).toList();
    }

    public void update(RoleUpdateRequest request) {
        Role role = requireRole(request.getId());
        Role update = new Role();
        update.setId(role.getId());
        update.setName(request.getName().trim());
        update.setRemark(request.getRemark() == null ? "" : request.getRemark().trim());
        update.setUpdateTime(LocalDateTime.now());
        roleMapper.update(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(RoleMenuAssignRequest request) {
        requireRole(request.getRoleId());
        List<Long> menuIds = request.getMenuIds() == null ? List.of() : request.getMenuIds().stream().distinct().toList();
        if (!menuIds.isEmpty()) {
            long found = menuMapper.selectCountByQuery(QueryWrapper.create().where(Menu::getId).in(menuIds));
            if (found != menuIds.size()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "包含不存在的菜单");
            }
        }
        roleMenuMapper.deleteByQuery(QueryWrapper.create().where(RoleMenu::getRoleId).eq(request.getRoleId()));
        for (Long menuId : menuIds) {
            roleMenuMapper.insert(RoleMenu.builder().roleId(request.getRoleId()).menuId(menuId).build());
        }
    }

    private Role requireRole(Long id) {
        Role role = roleMapper.selectOneById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "角色不存在");
        }
        return role;
    }
}
