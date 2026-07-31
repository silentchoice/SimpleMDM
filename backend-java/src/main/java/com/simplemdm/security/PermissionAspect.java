package com.simplemdm.security;

import com.simplemdm.model.SysUser;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.SysUserPermissionRepository;
import com.simplemdm.service.system.AuthorizationService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletResponse;

@Aspect
@Component
public class PermissionAspect {

    private final AuthorizationService authorizationService;
    private final SysUserPermissionRepository legacyPermissionRepository;

    public PermissionAspect(AuthorizationService authorizationService,
                            SysUserPermissionRepository legacyPermissionRepository) {
        this.authorizationService = authorizationService;
        this.legacyPermissionRepository = legacyPermissionRepository;
    }

    @Around("@annotation(requirePerm)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePerm requirePerm) throws Throwable {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
            .getResponse();

        User systemUser = JwtInterceptor.CURRENT_USER.get();
        if (systemUser != null) {
            Long departmentId = departmentArgument(joinPoint, requirePerm.departmentArgument());
            if (departmentId == null || !authorizationService.can(systemUser.getId(), requirePerm.value(), departmentId)) {
                forbidden(response);
                return null;
            }
            return joinPoint.proceed();
        }

        SysUser legacyUser = JwtInterceptor.LEGACY_CURRENT_USER.get();
        if (legacyUser == null) {
            unauthorized(response);
            return null;
        }
        if (legacyPermissionRepository.findByUserIdAndPermType(legacyUser.getId(), requirePerm.value()).isEmpty()) {
            forbidden(response);
            return null;
        }
        return joinPoint.proceed();
    }

    private Long departmentArgument(ProceedingJoinPoint joinPoint, int argumentIndex) {
        if (argumentIndex < 0 || argumentIndex >= joinPoint.getArgs().length) {
            return null;
        }
        Object argument = joinPoint.getArgs()[argumentIndex];
        return argument instanceof Number number ? number.longValue() : null;
    }

    private void unauthorized(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\",\"data\":null}");
    }

    private void forbidden(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"Forbidden\",\"data\":null}");
    }
}
