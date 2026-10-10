package com.ai.aijava.service;

import com.ai.aijava.dto.request.UserAssignRoleRequest;
import com.ai.aijava.dto.request.UserStatusRequest;
import com.ai.aijava.dto.vo.UserVO;
import com.ai.aijava.entity.Role;
import com.ai.aijava.entity.User;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.mapper.RoleMapper;
import com.ai.aijava.mapper.UserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.ai.aijava.entity.table.RoleTableDef.ROLE;
import static com.ai.aijava.entity.table.UserTableDef.USER;

/**
 * 管理员对用户的查询、封禁和角色调整。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String ADMIN = "ADMIN";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserService userService;
    private final AccountGate accountGate;

    public List<UserVO> list() {
        List<User> users = userMapper.selectListByQuery(QueryWrapper.create().orderBy(USER.ID.desc()));
        return users.stream()
                .map(user -> userService.toView(user, accountGate.findRole(user.getRoleId())))
                .toList();
    }

    public void updateStatus(UserStatusRequest request) {
        if (request.getStatus() != 1 && request.getStatus() != 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态只允许正常或封禁");
        }
        User user = requireUser(request.getUserId());
        if (request.getStatus() != 1) {
            assertNotLastAdmin(user);
        }
        User update = new User();
        update.setId(user.getId());
        update.setStatus(request.getStatus());
        update.setUpdateTime(LocalDateTime.now());
        userMapper.update(update);
    }

    public void assignRole(UserAssignRoleRequest request) {
        User user = requireUser(request.getUserId());
        Role role = roleMapper.selectOneById(request.getRoleId());
        if (role == null || role.getStatus() == null || role.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "角色不存在或已停用");
        }
        Role admin = accountGate.requireRoleByCode(ADMIN);
        if (admin.getId().equals(user.getRoleId()) && !admin.getId().equals(role.getId())) {
            assertNotLastAdmin(user);
        }
        User update = new User();
        update.setId(user.getId());
        update.setRoleId(role.getId());
        update.setUpdateTime(LocalDateTime.now());
        userMapper.update(update);
    }

    private void assertNotLastAdmin(User user) {
        Role admin = roleMapper.selectOneByQuery(QueryWrapper.create().where(ROLE.CODE.eq(ADMIN)));
        if (admin == null || !admin.getId().equals(user.getRoleId()) || user.getStatus() == null || user.getStatus() != 1) {
            return;
        }
        long enabledAdmins = userMapper.selectCountByQuery(
                QueryWrapper.create().where(USER.ROLE_ID.eq(admin.getId())).and(USER.STATUS.eq(1)));
        if (enabledAdmins <= 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "至少保留一个可用的管理员");
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectOneById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return user;
    }
}
