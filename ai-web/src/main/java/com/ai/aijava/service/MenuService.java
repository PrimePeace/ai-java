package com.ai.aijava.service;

import com.ai.aijava.dto.request.MenuSaveRequest;
import com.ai.aijava.dto.vo.MenuVO;
import com.ai.aijava.entity.Menu;
import com.ai.aijava.entity.RoleMenu;
import com.ai.aijava.entity.User;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.mapper.MenuMapper;
import com.ai.aijava.mapper.RoleMenuMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ai.aijava.entity.table.MenuTableDef.MENU;
import static com.ai.aijava.entity.table.RoleMenuTableDef.ROLE_MENU;

/**
 * 菜单查询、导航树与维护。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private static final Set<String> MENU_TYPES = Set.of("CATALOG", "MENU", "BUTTON");
    private static final Set<String> CLIENTS = Set.of("WEB", "APP", "ALL");

    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final AccountGate accountGate;

    /**
     * 当前用户在指定端可见的菜单树。BUTTON 不返回。visible=0 的页面仍返回，供注册路由。
     */
    public List<MenuVO> nav(User user, String client) {
        accountGate.requireActive(user.getId());
        String normalized = normalizeClient(client);
        List<Long> menuIds = roleMenuIds(user.getRoleId());
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<Menu> menus = menuMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(MENU.ID.in(menuIds))
                        .and(MENU.STATUS.eq(1))
                        .and(MENU.MENU_TYPE.ne("BUTTON"))
                        .orderBy(MENU.SORT.asc()));
        List<Menu> matched = menus.stream()
                .filter(menu -> matchesClient(menu.getClient(), normalized))
                .toList();
        return toTree(matched);
    }

    public List<MenuVO> listAll() {
        List<Menu> menus = menuMapper.selectListByQuery(
                QueryWrapper.create().orderBy(MENU.SORT.asc()).orderBy(MENU.ID.asc()));
        return menus.stream().map(this::toVo).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(MenuSaveRequest request) {
        validateSave(request);
        LocalDateTime now = LocalDateTime.now();
        if (request.getId() == null) {
            Menu menu = toEntity(request);
            menu.setCreateTime(now);
            menu.setUpdateTime(now);
            menuMapper.insert(menu);
            return;
        }
        Menu existing = menuMapper.selectOneById(request.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "菜单不存在");
        }
        Menu menu = toEntity(request);
        menu.setId(existing.getId());
        menu.setCreateTime(existing.getCreateTime());
        menu.setUpdateTime(now);
        menuMapper.update(menu, false);
    }

    public List<Long> roleMenuIds(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return roleMenuMapper.selectListByQuery(QueryWrapper.create().where(ROLE_MENU.ROLE_ID.eq(roleId)))
                .stream()
                .map(RoleMenu::getMenuId)
                .filter(Objects::nonNull)
                .toList();
    }

    private void validateSave(MenuSaveRequest request) {
        if (!MENU_TYPES.contains(request.getMenuType())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "菜单类型无效");
        }
        if (!CLIENTS.contains(request.getClient())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "所属端无效");
        }
        if (request.getVisible() != 0 && request.getVisible() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "是否可见无效");
        }
        if (request.getStatus() != 0 && request.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态无效");
        }
        if ("MENU".equals(request.getMenuType())
                && (request.getPath() == null || request.getPath().isBlank()
                || request.getComponent() == null || request.getComponent().isBlank())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "页面菜单必须填写路径和组件键");
        }
    }

    private Menu toEntity(MenuSaveRequest request) {
        return Menu.builder()
                .parentId(request.getParentId())
                .name(request.getName().trim())
                .path(blankToNull(request.getPath()))
                .component(blankToNull(request.getComponent()))
                .icon(blankToNull(request.getIcon()))
                .sort(request.getSort())
                .menuType(request.getMenuType())
                .permission(blankToNull(request.getPermission()))
                .visible(request.getVisible())
                .client(request.getClient())
                .status(request.getStatus())
                .build();
    }

    private List<MenuVO> toTree(List<Menu> menus) {
        Map<Long, MenuVO> nodes = new HashMap<>();
        List<MenuVO> roots = new ArrayList<>();
        for (Menu menu : menus) {
            nodes.put(menu.getId(), toVo(menu));
        }
        for (Menu menu : menus) {
            MenuVO node = nodes.get(menu.getId());
            Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
            MenuVO parent = parentId == 0 ? null : nodes.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<MenuVO> nodes) {
        nodes.sort(Comparator.comparing(MenuVO::getSort, Comparator.nullsLast(Integer::compareTo)));
        for (MenuVO node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortTree(node.getChildren());
            }
        }
    }

    private MenuVO toVo(Menu menu) {
        return MenuVO.builder()
                .id(menu.getId())
                .parentId(menu.getParentId())
                .name(menu.getName())
                .path(menu.getPath())
                .component(menu.getComponent())
                .icon(menu.getIcon())
                .sort(menu.getSort())
                .menuType(menu.getMenuType())
                .permission(menu.getPermission())
                .visible(menu.getVisible())
                .client(menu.getClient())
                .status(menu.getStatus())
                .children(new ArrayList<>())
                .build();
    }

    private boolean matchesClient(String menuClient, String requestClient) {
        if (menuClient == null || "ALL".equals(menuClient)) {
            return true;
        }
        return menuClient.equals(requestClient);
    }

    private String normalizeClient(String client) {
        if (client == null || client.isBlank()) {
            return "WEB";
        }
        String value = client.trim().toUpperCase(Locale.ROOT);
        if (!"WEB".equals(value) && !"APP".equals(value)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "client 只接受 web 或 app");
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
