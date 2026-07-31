package com.simplemdm.security;

import com.simplemdm.model.SysUser;
import com.simplemdm.model.SysUserPermission;
import com.simplemdm.repository.SysUserPermissionRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Aspect
@Component
public class PermissionAspect {

    private final SysUserPermissionRepository permRepo;

    public PermissionAspect(SysUserPermissionRepository permRepo) {
        this.permRepo = permRepo;
    }

    @Around("@annotation(requirePerm)")
    public Object checkPermission(ProceedingJoinPoint pjp, RequirePerm requirePerm) throws Throwable {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();

        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\",\"data\":null}");
            return null;
        }
        // System-scoped authorization is evaluated by AuthorizationService for new MDM operations.

        List<SysUserPermission> perms = permRepo.findByUserIdAndPermType(user.getId(), requirePerm.value());
        if (perms.isEmpty()) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"\u65e0" +
                ("EDIT".equals(requirePerm.value()) ? "\u7f16\u8f91" : "\u67e5\u770b") + "\u6743\u9650\",\"data\":null}");
            return null;
        }

        return pjp.proceed();
    }
}
