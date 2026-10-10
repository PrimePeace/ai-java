package com.ai.aijava.config;

import com.ai.aijava.auth.CredentialRules;
import com.ai.aijava.entity.Role;
import com.ai.aijava.entity.User;
import com.ai.aijava.mapper.UserMapper;
import com.ai.aijava.service.AccountGate;
import com.ai.aijava.utils.BCryptUtils;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.ai.aijava.entity.table.UserTableDef.USER;

/**
 * 按环境变量创建第一个管理员。已存在同名账号时不覆盖密码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final AccountGate accountGate;

    @Value("${app.admin.username:admin}")
    private String username;

    @Value("${app.admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            log.warn("未配置 APP_ADMIN_PASSWORD，跳过初始管理员创建");
            return;
        }
        if (!CredentialRules.isPasswordValid(password) || !CredentialRules.isUsernameValid(username)) {
            log.warn("初始管理员用户名或密码不符合规则，跳过创建");
            return;
        }
        User existing = userMapper.selectOneByQuery(QueryWrapper.create().where(USER.USERNAME.eq(username)));
        if (existing != null) {
            return;
        }
        Role admin = accountGate.requireRoleByCode("ADMIN");
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .username(username)
                .password(BCryptUtils.encode(password))
                .nickname("管理员")
                .email("")
                .phone("")
                .loginFailCount(0)
                .status(1)
                .roleId(admin.getId())
                .createTime(now)
                .updateTime(now)
                .build();
        userMapper.insert(user);
        log.info("已创建初始管理员 {}", username);
    }
}
