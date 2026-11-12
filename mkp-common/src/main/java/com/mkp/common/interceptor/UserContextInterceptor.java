package com.mkp.common.interceptor;

import com.mkp.common.util.UserContext;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-Username");
        String role = request.getHeader("X-User-Role");

        if (userId != null && !userId.isEmpty()) {
            UserContext.setUserId(Long.parseLong(userId));
        }
        if (username != null && !username.isEmpty()) {
            UserContext.setUsername(username);
        }
        if (role != null && !role.isEmpty()) {
            UserContext.setRole(role);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
