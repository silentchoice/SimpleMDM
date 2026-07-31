package com.simplemdm.security;

import com.simplemdm.model.system.User;
import com.simplemdm.service.system.AuthorizationService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PermissionAspectTest {

    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final PermissionAspect aspect = new PermissionAspect(authorization, null);

    @AfterEach
    void clearContext() {
        JwtInterceptor.CURRENT_USER.remove();
        JwtInterceptor.LEGACY_CURRENT_USER.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void proceedsOnlyWhenSystemUserCanActOnAnnotatedDepartmentArgument() throws Throwable {
        setSystemUser(7L);
        when(authorization.can(7L, "MDM_RECORD_EDIT", 21L)).thenReturn(true);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[] {21L});
        when(joinPoint.proceed()).thenReturn("allowed");

        Object result = aspect.checkPermission(joinPoint, permission());

        assertThat(result).isEqualTo("allowed");
        verify(authorization).can(7L, "MDM_RECORD_EDIT", 21L);
        verify(joinPoint).proceed();
    }

    @Test
    void deniesSystemUserWhenTargetDepartmentIsOutsideAuthorizedScope() throws Throwable {
        setSystemUser(7L);
        when(authorization.can(7L, "MDM_RECORD_EDIT", 22L)).thenReturn(false);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[] {22L});

        Object result = aspect.checkPermission(joinPoint, permission());

        assertThat(result).isNull();
        assertThat(((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
            .getResponse().getStatus()).isEqualTo(403);
        verify(authorization).can(7L, "MDM_RECORD_EDIT", 22L);
        verify(joinPoint, never()).proceed();
    }

    private void setSystemUser(Long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        JwtInterceptor.CURRENT_USER.set(user);
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
    }

    private RequirePerm permission() throws NoSuchMethodException {
        Method method = ProtectedOperation.class.getDeclaredMethod("edit", Long.class);
        return method.getAnnotation(RequirePerm.class);
    }

    private static class ProtectedOperation {
        @RequirePerm(value = "MDM_RECORD_EDIT", departmentArgument = 0)
        void edit(Long departmentId) { }
    }
}
