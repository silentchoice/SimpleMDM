package com.simplemdm.security;

import com.simplemdm.model.system.User;
import com.simplemdm.repository.SysUserRepository;
import com.simplemdm.repository.system.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        when(systemUsers.findById(7L)).thenReturn(java.util.Optional.of(user));
        MockHttpServletRequest request = authorized(jwtUtil.createToken(7L, 11L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(JwtInterceptor.CURRENT_USER.get()).isNull();
        assertThat(JwtInterceptor.CURRENT_SYSTEM_ID.get()).isNull();
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
