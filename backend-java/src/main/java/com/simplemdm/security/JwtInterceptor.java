package com.simplemdm.security;

import com.simplemdm.model.system.User;
import com.simplemdm.repository.system.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository systemUserRepository;
    public static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();
    public static final ThreadLocal<Long> CURRENT_SYSTEM_ID = new ThreadLocal<>();

    public JwtInterceptor(JwtUtil jwtUtil, UserRepository systemUserRepository) {
        this.jwtUtil = jwtUtil;
        this.systemUserRepository = systemUserRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        clearContext();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || "/api/auth/login".equals(request.getRequestURI())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(response);
        }

        String token = authorization.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            return unauthorized(response);
        }

        Long systemId = jwtUtil.getSystemIdFromToken(token);
        if (systemId == null) {
            return unauthorized(response);
        }
        Optional<User> user = systemUserRepository.findWithContextById(userId);
        if (user.isEmpty() || !user.get().isActive() || !user.get().isSystemActive()
            || !systemId.equals(user.get().getSystemId())) {
            return unauthorized(response);
        }
        CURRENT_USER.set(user.get());
        CURRENT_SYSTEM_ID.set(systemId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        clearContext();
    }


    private void clearContext() {
        CURRENT_USER.remove();
        CURRENT_SYSTEM_ID.remove();
    }
    private boolean unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\",\"data\":null}");
        return false;
    }
}
