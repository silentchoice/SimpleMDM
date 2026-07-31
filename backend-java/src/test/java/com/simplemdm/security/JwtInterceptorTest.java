package com.simplemdm.security;

import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.SysUserRepository;
import com.simplemdm.repository.system.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

class JwtInterceptorTest {

    private final JwtUtil jwtUtil = new JwtUtil("01234567890123456789012345678901", 60);
    private final UserRepository systemUsers = mock(UserRepository.class);
    private final SysUserRepository legacyUsers = mock(SysUserRepository.class);
    private final JwtInterceptor interceptor = new JwtInterceptor(jwtUtil, systemUsers, legacyUsers);

    @AfterEach
    void clearContext() {
        JwtInterceptor.CURRENT_USER.remove();
        JwtInterceptor.LEGACY_CURRENT_USER.remove();
        JwtInterceptor.CURRENT_SYSTEM_ID.remove();
    }

    @Test
    void authenticatesSystemUserOnlyWhenTokenSystemMatchesUserSystem() throws Exception {
        User user = mock(User.class);
        when(user.getSystemId()).thenReturn(10L);
        when(user.isActive()).thenReturn(true);
        when(systemUsers.findById(7L)).thenReturn(java.util.Optional.of(user));
        MockHttpServletRequest request = authorized(jwtUtil.createToken(7L, 10L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(JwtInterceptor.CURRENT_USER.get()).isSameAs(user);
        assertThat(JwtInterceptor.CURRENT_SYSTEM_ID.get()).isEqualTo(10L);
        assertThat(JwtInterceptor.LEGACY_CURRENT_USER.get()).isNull();
    }

    @Test
    void rejectsTokenWhoseSystemClaimDoesNotMatchAuthenticatedSystemUser() throws Exception {
        User user = mock(User.class);
        when(user.getSystemId()).thenReturn(10L);
        when(user.isActive()).thenReturn(true);
        when(systemUsers.findById(7L)).thenReturn(java.util.Optional.of(user));
        MockHttpServletRequest request = authorized(jwtUtil.createToken(7L, 11L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(JwtInterceptor.CURRENT_USER.get()).isNull();
        assertThat(JwtInterceptor.CURRENT_SYSTEM_ID.get()).isNull();
    }

    @Test
    void rejectsDisabledSystemUserAndClearsRequestContext() throws Exception {
        User disabledUser = systemUser();
        ReflectionTestUtils.setField(disabledUser, "status", "disabled");
        when(systemUsers.findById(7L)).thenReturn(java.util.Optional.of(disabledUser));
        JwtInterceptor.CURRENT_USER.set(mock(User.class));
        JwtInterceptor.LEGACY_CURRENT_USER.set(mock(com.simplemdm.model.SysUser.class));
        JwtInterceptor.CURRENT_SYSTEM_ID.set(10L);

        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(authorized(jwtUtil.createToken(7L, 10L)), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(JwtInterceptor.CURRENT_USER.get()).isNull();
        assertThat(JwtInterceptor.LEGACY_CURRENT_USER.get()).isNull();
        assertThat(JwtInterceptor.CURRENT_SYSTEM_ID.get()).isNull();
    }

    @Test
    void rejectsSoftDeletedSystemUserAndClearsRequestContext() throws Exception {
        User deletedUser = systemUser();
        ReflectionTestUtils.setField(deletedUser, "deletedAt", LocalDateTime.of(2026, 7, 31, 0, 0));
        when(systemUsers.findById(7L)).thenReturn(java.util.Optional.of(deletedUser));
        JwtInterceptor.CURRENT_USER.set(mock(User.class));
        JwtInterceptor.LEGACY_CURRENT_USER.set(mock(com.simplemdm.model.SysUser.class));
        JwtInterceptor.CURRENT_SYSTEM_ID.set(10L);

        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(authorized(jwtUtil.createToken(7L, 10L)), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(JwtInterceptor.CURRENT_USER.get()).isNull();
        assertThat(JwtInterceptor.LEGACY_CURRENT_USER.get()).isNull();
        assertThat(JwtInterceptor.CURRENT_SYSTEM_ID.get()).isNull();
    }

    private User systemUser() {
        SystemEntity system = mock(SystemEntity.class);
        when(system.getId()).thenReturn(10L);
        Department department = mock(Department.class);
        when(department.getId()).thenReturn(21L);
        return User.create(system, department, "member", "hash", "Member");
    }
    @Test
    void clearsAnyPriorSystemContextBeforeRejectingToken() throws Exception {
        User staleUser = mock(User.class);
        JwtInterceptor.CURRENT_USER.set(staleUser);
        JwtInterceptor.CURRENT_SYSTEM_ID.set(10L);
        MockHttpServletRequest request = authorized(jwtUtil.createToken(7L, 11L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(JwtInterceptor.CURRENT_USER.get()).isNull();
        assertThat(JwtInterceptor.CURRENT_SYSTEM_ID.get()).isNull();
    }
    private MockHttpServletRequest authorized(String token) {


        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/records");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
