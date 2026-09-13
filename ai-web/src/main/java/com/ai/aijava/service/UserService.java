package com.ai.aijava.service;

import com.ai.aijava.context.UserContext;
import com.ai.aijava.dto.request.UserLoginRequest;
import com.ai.aijava.dto.request.UserRegisterRequest;
import com.ai.aijava.dto.vo.UserLoginVO;
import com.ai.aijava.dto.vo.UserVO;
import com.ai.aijava.entity.User;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.exception.ThrowUtils;
import com.ai.aijava.mapper.UserMapper;
import com.ai.aijava.utils.BCryptUtils;
import com.ai.aijava.utils.JwtUtils;
import com.mybatisflex.core.query.QueryWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

import static com.ai.aijava.entity.table.UserTableDef.USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    private final UserMapper userMapper;

    /**
     * 用户注册
     */
    public UserVO register(UserRegisterRequest request) {
        // BCrypt 加密密码
        String encodedPassword = BCryptUtils.encode(request.getPassword());

        // 构建用户实体
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .username(request.getUsername())
                .password(encodedPassword)
                .nickname(request.getNickname() != null ? request.getNickname() : "")
                .email(request.getEmail() != null ? request.getEmail() : "")
                .phone(request.getPhone() != null ? request.getPhone() : "")
                .loginFailCount(0)
                .status(1)
                .createTime(now)
                .updateTime(now)
                .build();

        try {
            userMapper.insert(user); // ① 交给数据库，唯一索引原子把关
        } catch (DuplicateKeyException e) {  // ② 接住被拒的请求
            // uk_username 唯一索引兜底，并发注册同名用户时在此拦截
            log.warn("用户名重复被唯一索引拦截: username={}", request.getUsername());
            throw new BusinessException(ErrorCode.REGISTER_ERROR, "用户名已存在"); // ③ 转业务提示
        }

        return toUserVO(user);
    }

    /**
     * 用户登录
     */
    public UserLoginVO login(UserLoginRequest request, String clientIp) {
        // 查询用户
        User user = userMapper.selectOneByQuery(
                QueryWrapper.create().where(USER.USERNAME.eq(request.getUsername()))
        );
        ThrowUtils.throwIf(user == null, ErrorCode.LOGIN_ERROR, "用户名或密码错误");

        // 检查账号锁定状态
        if (user.getLockTime() != null && user.getLockTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "账号已被锁定，请于 " +
                    java.time.Duration.between(LocalDateTime.now(), user.getLockTime()).toMinutes() +
                    " 分钟后再试");
        }

        // BCrypt 校验密码
        if (!BCryptUtils.matches(request.getPassword(), user.getPassword())) {
            // 密码错误，更新失败次数
            incrementLoginFailCount(user);
            throw new BusinessException(ErrorCode.LOGIN_ERROR, "用户名或密码错误");
        }

        // 登录成功：清空失败计数，记录登录信息
        User update = new User();
        update.setId(user.getId());
        update.setLoginFailCount(0);
        update.setLockTime(null);
        update.setLoginIp(clientIp);
        update.setLoginTime(LocalDateTime.now());
        userMapper.update(update);

        // 生成双 Token
        String accessToken = JwtUtils.generateToken(user.getId(), user.getUsername());
        String refreshToken = JwtUtils.generateRefreshToken(user.getId());

        return UserLoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(JwtUtils.getAccessTokenExpiration())
                .user(toUserVO(user))
                .build();
    }

    /**
     * 刷新 Token
     */
    public UserLoginVO refreshToken(String refreshToken) {
        if (!JwtUtils.isTokenValid(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 已过期，请重新登录");
        }

        // 验证是否为 refresh 类型
        Claims claims = JwtUtils.parseToken(refreshToken);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "无效的 Token 类型");
        }

        Long userId = JwtUtils.getUserIdFromToken(refreshToken);
        User user = userMapper.selectOneById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 生成新的双 Token
        String newAccessToken = JwtUtils.generateToken(user.getId(), user.getUsername());
        String newRefreshToken = JwtUtils.generateRefreshToken(user.getId());

        return UserLoginVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(JwtUtils.getAccessTokenExpiration())
                .user(toUserVO(user))
                .build();
    }

    /**
     * 获取当前登录用户信息
     */
    public UserVO getCurrentUser() {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        User user = userMapper.selectOneById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        return toUserVO(user);
    }

    /**
     * 增加登录失败次数，超过阈值则锁定账号
     */
    private void incrementLoginFailCount(User user) {
        int newCount = (user.getLoginFailCount() != null ? user.getLoginFailCount() : 0) + 1;
        User update = new User();
        update.setId(user.getId());
        update.setLoginFailCount(newCount);

        if (newCount >= MAX_LOGIN_FAIL_COUNT) {
            // 达到阈值，锁定账号 30 分钟
            update.setLockTime(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("用户 {} 登录失败次数过多，已锁定至 {}", user.getUsername(), update.getLockTime());
        }

        userMapper.update(update);
    }

    /**
     * Entity → VO 转换
     */
    private UserVO toUserVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }
}