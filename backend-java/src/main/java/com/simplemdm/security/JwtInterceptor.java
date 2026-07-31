package com.simplemdm.security;

import com.simplemdm.model.SysUser;
import com.simplemdm.repository.SysUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserRepository userRepo;

    public static final ThreadLocal<SysUser> CURRENT_USER = new ThreadLocal<>();
    public static final ThreadLocal<Long> CURRENT_SYSTEM_ID = new ThreadLocal<>();

    public JwtInterceptor(JwtUtil jwtUtil, SysUserRepository userRepo) {
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        if ("/api/auth/login".equals(path)) return true;

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\",\"data\":null}");
            return false;
        }

        String token = authHeader.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\",\"data\":null}");
            return false;
        }

        Optional<SysUser> userOpt = userRepo.findByIdAndStatus(userId, "active");
        if (userOpt.isEmpty()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"用户不存在或已禁用\",\"data\":null}");
            return false;
        }

        CURRENT_USER.set(userOpt.get());
        CURRENT_SYSTEM_ID.set(jwtUtil.getSystemIdFromToken(token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CURRENT_USER.remove();
        CURRENT_SYSTEM_ID.remove();
    }
}
