package com.ai.aijava.interceptor;

import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.annotation.RequirePermission;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.service.AccountGate;
import com.ai.aijava.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器。
 * 检查登录注解，解析 Token，校验账号状态，并按权限码授权。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AccountGate accountGate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireLogin login = findLogin(handlerMethod);
        RequirePermission permission = findPermission(handlerMethod);
        if (login == null && permission == null) {
            return true;
        }

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            Claims claims = JwtUtils.parseToken(token);
            if (!"access".equals(claims.get("type", String.class))) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token 无效");
            }
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);
            accountGate.requireActive(userId);
            if (permission != null) {
                accountGate.requirePermission(userId, permission.value());
            }
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
        UserContext.clear();
    }

    private RequireLogin findLogin(HandlerMethod handlerMethod) {
        RequireLogin method = handlerMethod.getMethodAnnotation(RequireLogin.class);
        if (method != null) {
            return method;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireLogin.class);
    }

    private RequirePermission findPermission(HandlerMethod handlerMethod) {
        RequirePermission method = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (method != null) {
            return method;
        }
        return handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
    }
}
