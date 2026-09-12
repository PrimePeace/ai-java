# 用户注册登录模块设计文档

> 日期：2026-07-26 | 安全级别：等保三级 | 技术栈：Spring Boot 4.1 + MyBatis-Flex + JWT + BCrypt

---

## 1. 需求概要

- **注册**：用户名+密码+邮箱+手机+昵称，密码最小长度 8 位，BCrypt 加密存储
- **登录**：用户名+密码，成功返回 Access Token（2h）+ Refresh Token（7d）
- **刷新 Token**：有效 Refresh Token 可换取新 Access Token
- **获取用户信息**：登录后获取当前用户详情
- **防暴力破解**：连续 5 次失败锁定账号 30 分钟
- **审计日志**：记录最后登录 IP、最后登录时间、登录失败次数

---

## 2. 架构设计

```
用户请求 → Spring Security（白名单放行） → JwtInterceptor → Controller
                                                    ↓
                                       解析 JWT → UserContext (ThreadLocal)
                                                    ↓
                                       @RequireLogin 标记需要登录
```

**Spring Security 最小化**：

- 全局配置 `SecurityFilterChain`，所有 `/api/**` 放行（交给自定义拦截器处理认证）
- 仅暴露 `BCryptPasswordEncoder` Bean 供密码加密使用
- 禁用 CSRF（前后端分离，JWT 无 Cookie 携带场景）

**JWT 拦截器**：

- 检查方法是否有 `@RequireLogin` 注解
- 有注解 → 从 `Authorization: Bearer <token>` 提取 Token → 校验 → 设置 `UserContext`
- 无注解 → 放行（公开接口）

---

## 3. 数据库设计

```sql
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
    `nickname` VARCHAR(64) DEFAULT '' COMMENT '昵称',
    `email` VARCHAR(128) DEFAULT '' COMMENT '邮箱',
    `phone` VARCHAR(32) DEFAULT '' COMMENT '手机号',
    `login_ip` VARCHAR(64) DEFAULT '' COMMENT '最后登录IP',
    `login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `login_fail_count` INT DEFAULT 0 COMMENT '连续登录失败次数',
    `lock_time` DATETIME DEFAULT NULL COMMENT '账号锁定时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

**等保三级审计要点**：

- `login_fail_count`：连续失败次数，>=5 触发锁定
- `lock_time`：锁定到期时间，过期自动解锁
- `login_ip`：最后登录 IP，用于审计追踪

---

## 4. 核心组件

### 4.1 utils/BCryptUtils.java

- `encode(String rawPassword)` → String：BCrypt 加密
- `matches(String rawPassword, String encodedPassword)` → boolean：校验密码

### 4.2 utils/JwtUtils.java

- `generateToken(Long userId, String username)` → String：生成 Access Token（2h）
- `generateRefreshToken(Long userId)` → String：生成 Refresh Token（7d）
- `parseToken(String token)` → Claims：解析 Token
- `isTokenValid(String token)` → boolean：校验是否过期

JWT Secret 从 `application.yml` 读取，密钥长度 >= 256 bit。

### 4.3 annotation/RequireLogin.java

- `@Target(METHOD)` + `@Retention(RUNTIME)` 自定义注解
- 标注在 Controller 方法上，标识需要登录验证

### 4.4 context/UserContext.java

- `ThreadLocal<Long> userId`、`ThreadLocal<String> username`
- `setUser(Long userId, String username)` / `getUser()` / `clear()`
- 拦截器 `preHandle` 设置，`afterCompletion` 清除（防止线程池复用泄漏）

### 4.5 interceptor/JwtInterceptor.java

- 实现 `HandlerInterceptor`
- `preHandle`：检查 `@RequireLogin` → 无注解放行 → 提取 Bearer Token → 解析校验 → 设置 `UserContext` → 失败抛 `BusinessException(NOT_LOGIN_ERROR)`
- `afterCompletion`：`UserContext.clear()`

### 4.6 service/UserService.java

| 方法                                                     | 职责                                                                                       |
| -------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `register(UserRegisterRequest)` → UserVO                 | 校验用户名唯一性 → 密码 BCrypt 加密 → 保存用户                                             |
| `login(UserLoginRequest, String clientIp)` → UserLoginVO | 检查锁定状态 → 校验密码 → 失败计数+1 → 成功则生成双 Token + 清空失败计数 + 记录登录IP/时间 |
| `refreshToken(String refreshToken)` → UserLoginVO        | 校验 Refresh Token → 生成新双 Token                                                        |
| `getCurrentUser()` → UserVO                              | 从 `UserContext` 获取 userId → 查询数据库返回用户信息                                      |

### 4.7 config/SecurityConfig.java

- `@Bean SecurityFilterChain`：所有请求放行（`permitAll()`），禁用 CSRF
- `@Bean PasswordEncoder`：返回 `BCryptPasswordEncoder()`

### 4.8 config/WebMvcConfig.java

- 注册 `JwtInterceptor`，拦截所有 `/api/**` 路径

---

## 5. 登录流程

```
登录请求 → 查询用户(username) → 用户不存在 → 返回错误
                              ↓
                        检查 lock_time
                              ↓
                  lock_time 不为空且未过期 → 返回"账号已锁定"
                              ↓
                        BCrypt 校验密码
                              ↓
                  密码错误 → failCount++ → failCount>=5 → lock_time = now+30min → 返回错误
                              ↓
                        密码正确 → failCount=0, lockTime=null
                        loginIp = clientIp, loginTime = now
                        生成 Access Token (2h) + Refresh Token (7d)
                        返回 UserLoginVO
```

---

## 6. API 接口定义

| 方法 | 路径                 | 需要登录 | 说明             |
| ---- | -------------------- | -------- | ---------------- |
| POST | `/api/user/register` | 否       | 用户注册         |
| POST | `/api/user/login`    | 否       | 用户登录         |
| POST | `/api/user/refresh`  | 否       | 刷新 Token       |
| GET  | `/api/user/current`  | 是       | 获取当前用户信息 |

**注册请求**：`username`(必填, 4-32位), `password`(必填, 最小8位), `nickname`, `email`, `phone`
**登录请求**：`username`(必填), `password`(必填)
**刷新请求**：`refreshToken`(必填)
**登录响应**：`accessToken`, `refreshToken`, `expiresIn`(秒), `user`(UserVO)
**用户信息响应**：`id`, `username`, `nickname`, `email`, `phone`, `status`, `createTime`

---

## 7. 等保三级安全要点

| 要求           | 实现                                   |
| -------------- | -------------------------------------- |
| 密码加密存储   | BCrypt，强度 10（默认）                |
| 密码最小长度   | 8 位，服务端校验                       |
| 防暴力破解     | 5 次失败锁定 30 分钟（数据库字段控制） |
| Token 有效期   | Access Token 2h，超时需刷新或重新登录  |
| 审计日志       | 登录 IP、登录时间、失败次数记录在库    |
| 敏感信息不返回 | 密码字段不出现在任何响应中             |
| 请求参数校验   | `@Valid` + DTO 字段约束注解            |

---

## 8. 新增依赖

```xml
<!-- Spring Security (BCrypt + 安全基线) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

---

## 9. 配置文件变更

`application-prod.yml` 新增：

```yaml
jwt:
  secret: "your-256-bit-secret-key-here-must-be-at-least-32-chars" # >= 256 bit
  access-token-expiration: 7200 # 2h (秒)
  refresh-token-expiration: 604800 # 7d (秒)
```

---

## 10. 错误码扩展

`ErrorCode.java` 新增：

| 枚举             | Code  | Message                  |
| ---------------- | ----- | ------------------------ |
| `REGISTER_ERROR` | 40001 | 注册失败，用户名已存在   |
| `LOGIN_ERROR`    | 40002 | 用户名或密码错误         |
| `ACCOUNT_LOCKED` | 40003 | 账号已被锁定，请稍后再试 |
| `TOKEN_EXPIRED`  | 40102 | Token 已过期             |
| `TOKEN_INVALID`  | 40103 | Token 无效               |

---

## 11. 完整实现代码

### 11.1 pom.xml — 新增依赖

在 `<dependencies>` 中添加：

```xml
<!-- Spring Security (BCrypt + 安全基线) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

---

### 11.2 ErrorCode.java — 新增错误码

文件路径：`src/main/java/com/ai/aijava/exception/ErrorCode.java`

在枚举中添加：

```java
REGISTER_ERROR(40001, "注册失败，用户名已存在"),
LOGIN_ERROR(40002, "用户名或密码错误"),
ACCOUNT_LOCKED(40003, "账号已被锁定，请稍后再试"),
TOKEN_EXPIRED(40102, "Token 已过期"),
TOKEN_INVALID(40103, "Token 无效"),
```

---

### 11.3 User.java — 用户实体

文件路径：`src/main/java/com/ai/aijava/entity/User.java`

```java
package com.ai.aijava.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user")
public class User {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private String phone;

    private String loginIp;

    private LocalDateTime loginTime;

    private Integer loginFailCount;

    private LocalDateTime lockTime;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
```

---

### 11.4 UserMapper.java — MyBatis-Flex Mapper

文件路径：`src/main/java/com/ai/aijava/mapper/UserMapper.java`

```java
package com.ai.aijava.mapper;

import com.ai.aijava.entity.User;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

> 注：MyBatis-Flex 的 `BaseMapper` 已提供所有 CRUD 方法，无需手写 SQL。

---

### 11.5 DTO/VO

#### UserRegisterRequest.java


```java
package com.ai.aijava.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度必须在 4-32 位之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度不能少于 8 位")
    private String password;

    @Size(max = 64, message = "昵称长度不能超过 64 位")
    private String nickname;

    private String email;

    private String phone;
}
```

#### UserLoginRequest.java


```java
package com.ai.aijava.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

#### UserLoginVO.java


```java
package com.ai.aijava.dto;

import com.ai.aijava.dto.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO {

    private String accessToken;

    private String refreshToken;

    private Long expiresIn;

    private UserVO user;
}
```

#### UserVO.java


```java
package com.ai.aijava.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
```

---

### 11.6 utils/BCryptUtils.java

文件路径：`src/main/java/com/ai/aijava/utils/BCryptUtils.java`

```java
package com.ai.aijava.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 加密工具类
 * 密码加密与校验，等保三级要求密码必须加密存储
 */
public class BCryptUtils {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 对原始密码进行 BCrypt 加密
     *
     * @param rawPassword 原始明文密码
     * @return BCrypt 加密后的密文
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码与 BCrypt 密文是否匹配
     *
     * @param rawPassword     原始明文密码
     * @param encodedPassword BCrypt 加密后的密文
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
```

---

### 11.7 utils/JwtUtils.java

文件路径：`src/main/java/com/ai/aijava/utils/JwtUtils.java`

```java
package com.ai.aijava.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * Access Token（2h）和 Refresh Token（7d）的生成与校验
 */
public class JwtUtils {

    private static String secret;
    private static long accessTokenExpiration;
    private static long refreshTokenExpiration;

    /**
     * 由 Spring 注入配置值（通过构造函数 setter）
     */
    public static void setSecret(String secret) {
        JwtUtils.secret = secret;
    }

    public static void setAccessTokenExpiration(long accessTokenExpiration) {
        JwtUtils.accessTokenExpiration = accessTokenExpiration;
    }

    public static void setRefreshTokenExpiration(long refreshTokenExpiration) {
        JwtUtils.refreshTokenExpiration = refreshTokenExpiration;
    }

    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Access Token（包含 userId 和 username，短期有效）
     */
    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成 Refresh Token（仅包含 userId，长期有效）
     */
    public static String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token，返回 Claims
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 Token 是否有效（未过期且签名正确）
     */
    public static boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从 Token 中获取 userId
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 获取 Access Token 过期时间（秒）
     */
    public static long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}
```

> 注：JwtUtils 使用静态字段存储配置，通过 `JwtConfig` 在启动时注入（见 11.11）。

---

### 11.8 annotation/RequireLogin.java

文件路径：`src/main/java/com/ai/aijava/annotation/RequireLogin.java`

```java
package com.ai.aijava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要登录验证的接口
 * 标注在 Controller 方法上，由 JwtInterceptor 识别
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}
```

---

### 11.9 context/UserContext.java

文件路径：`src/main/java/com/ai/aijava/context/UserContext.java`

```java
package com.ai.aijava.context;

/**
 * 用户上下文（ThreadLocal 存储）
 * 拦截器 preHandle 设置，afterCompletion 清除（防止线程池复用泄漏）
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    public static void setUser(Long userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}
```

---

### 11.10 interceptor/JwtInterceptor.java

文件路径：`src/main/java/com/ai/aijava/interceptor/JwtInterceptor.java`

```java
package com.ai.aijava.interceptor;

import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 * 检查 @RequireLogin 注解，解析 Token 并设置 UserContext
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非 Controller 方法直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 没有 @RequireLogin 注解，公开接口，放行
        if (handlerMethod.getMethodAnnotation(RequireLogin.class) == null) {
            return true;
        }

        // 提取 Token
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            // 解析并校验 Token
            Claims claims = JwtUtils.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);

            // 设置用户上下文
            UserContext.setUser(userId, username);
            return true;
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Token 已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token 无效");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理 ThreadLocal，防止线程池复用泄漏
        UserContext.clear();
    }
}
```

---

### 11.11 config/JwtConfig.java

文件路径：`src/main/java/com/ai/aijava/config/JwtConfig.java`

```java
package com.ai.aijava.config;

import com.ai.aijava.utils.JwtUtils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置初始化，启动时将配置值注入 JwtUtils 静态字段
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    private String secret;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;

    @PostConstruct
    public void init() {
        JwtUtils.setSecret(secret);
        JwtUtils.setAccessTokenExpiration(accessTokenExpiration);
        JwtUtils.setRefreshTokenExpiration(refreshTokenExpiration);
    }
}
```

---

### 11.12 config/SecurityConfig.java

文件路径：`src/main/java/com/ai/aijava/config/SecurityConfig.java`

```java
package com.ai.aijava.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 最小化配置
 * - 所有请求放行（由 JwtInterceptor 处理认证）
 * - 仅暴露 BCryptPasswordEncoder Bean
 * - 禁用 CSRF、禁用 Session（JWT 无状态）
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离，JWT 无 Cookie，禁用 CSRF
                .csrf(AbstractHttpConfigurer::disable)
                // 无状态，不创建 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 所有请求放行，认证由 JwtInterceptor 处理
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

---

### 11.13 config/WebMvcConfig.java

文件路径：`src/main/java/com/ai/aijava/config/WebMvcConfig.java`

```java
package com.ai.aijava.config;

import com.ai.aijava.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置，注册 JWT 拦截器
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**");
    }
}
```

---

### 11.14 AiJavaApplication.java — 添加 @MapperScan

文件路径：`src/main/java/com/ai/aijava/AiJavaApplication.java`

在 `@SpringBootApplication` 下方添加：

```java
@MapperScan("com.ai.aijava.mapper")
```

完整文件：

```java
package com.ai.aijava;

import com.mybatisflex.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ai.aijava.mapper")
public class AiJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiJavaApplication.class, args);
    }

}
```

> 缺少 `@MapperScan` 会导致启动报错：`Parameter 0 of constructor in UserService required a bean of type 'UserMapper' that could not be found.`

---

### 11.15 service/UserService.java

文件路径：`src/main/java/com/ai/aijava/service/UserService.java`

```java
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        // 检查用户名是否已存在
        long count = userMapper.selectCountByQuery(
                QueryWrapper.create().where(USER.USERNAME.eq(request.getUsername()))
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.REGISTER_ERROR, "用户名已存在");
        }

        // BCrypt 加密密码
        String encodedPassword = BCryptUtils.encode(request.getPassword());

        // 构建用户实体
        User user = User.builder()
                .username(request.getUsername())
                .password(encodedPassword)
                .nickname(request.getNickname() != null ? request.getNickname() : "")
                .email(request.getEmail() != null ? request.getEmail() : "")
                .phone(request.getPhone() != null ? request.getPhone() : "")
                .loginFailCount(0)
                .status(1)
                .build();

        userMapper.insert(user);

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
```

> 注：代码中使用了 `USER` 静态导入（MyBatis-Flex 代码生成器产出的 TableDef）。如果 TableDef 尚未生成，可先用 `.eq("username", request.getUsername())` 字符串写法替代。

---

### 11.15 controller/UserController.java

文件路径：`src/main/java/com/ai/aijava/controller/UserController.java`

```java
package com.ai.aijava.controller;

import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import com.ai.aijava.dto.request.UserLoginRequest;
import com.ai.aijava.dto.vo.UserLoginVO;
import com.ai.aijava.dto.request.UserRegisterRequest;
com.ai.aijava.dto.vo.UserVO;
import com.ai.aijava.service.UserService;
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
    @PostMapping("/login")
    public BaseResponse<UserLoginVO> login(@RequestBody @Valid UserLoginRequest request,
                                           HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        UserLoginVO loginVO = userService.login(request, clientIp);
        return ResultUtils.success(loginVO);
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public BaseResponse<UserLoginVO> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        com.ai.aijava.dto.vo.UserLoginVO loginVO = userService.refreshToken(request.getRefreshToken());
        return ResultUtils.success(loginVO);
    }

    @Operation(summary = "获取当前用户信息")
    @RequireLogin
    @GetMapping("/current")
    public BaseResponse<UserVO> getCurrentUser() {
        UserVO userVO = userService.getCurrentUser();
        return ResultUtils.success(userVO);
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 代理链取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
```

---

### 11.16 RefreshTokenRequest.java — 刷新 Token 请求 DTO


```java
package com.ai.aijava.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh Token 不能为空")
    private String refreshToken;
}
```

---

### 11.17 application-prod.yml — JWT 配置

在 `src/main/resources/application-prod.yml` 中添加：

```yaml
# JWT 配置
jwt:
  secret: "your-256-bit-secret-key-here-must-be-at-least-32-chars-long!!!" # 必须 >= 32 字符（256 bit）
  access-token-expiration: 7200 # Access Token 有效期（秒）= 2h
  refresh-token-expiration: 604800 # Refresh Token 有效期（秒）= 7d
```

---

### 11.18 SQL 建表语句

在 MySQL 数据库 `ai_java` 中执行：

```sql
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
    `nickname` VARCHAR(64) DEFAULT '' COMMENT '昵称',
    `email` VARCHAR(128) DEFAULT '' COMMENT '邮箱',
    `phone` VARCHAR(32) DEFAULT '' COMMENT '手机号',
    `login_ip` VARCHAR(64) DEFAULT '' COMMENT '最后登录IP',
    `login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `login_fail_count` INT DEFAULT 0 COMMENT '连续登录失败次数',
    `lock_time` DATETIME DEFAULT NULL COMMENT '账号锁定时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```
