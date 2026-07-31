package com.simplemdm.security;

import com.simplemdm.model.system.User;
import com.simplemdm.repository.system.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtInterceptorTest {
    private final JwtUtil jwtUtil = new JwtUtil("01234567890123456789012345678901", 60);
    private final UserRepository users = mock(UserRepository.class);
    private final JwtInterceptor interceptor = new JwtInterceptor(jwtUtil, users);

    @AfterEach
    void clearContext() {
        JwtInterceptor.CURRENT_USER.remove();
        JwtInterceptor.CURRENT_SYSTEM_ID.remove();
    }

    @Test
    void authenticatesActiveUserOnlyWhenTokenSystemMatches() throws Exception {
        User user = user(10L, true);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request(jwtUtil.createToken(7L, 10L)), response, new Object())).isTrue();
        assertThat(JwtInterceptor.CURRENT_USER.get()).isSameAs(user);
        assertThat(JwtInterceptor.CURRENT_SYSTEM_ID.get()).isEqualTo(10L);
    }

    @Test
    void rejectsMissingSystemClaimMismatchedSystemAndInactiveUser() throws Exception {
        User active = user(10L, true);
        when(users.findById(7L)).thenReturn(Optional.of(active));
        assertThat(interceptor.preHandle(request(jwtUtil.createToken(7L)), new MockHttpServletResponse(), new Object()))
            .isFalse();
        assertThat(interceptor.preHandle(request(jwtUtil.createToken(7L, 11L)), new MockHttpServletResponse(), new Object()))
            .isFalse();

        User inactive = user(10L, false);
        when(users.findById(7L)).thenReturn(Optional.of(inactive));
        assertThat(interceptor.preHandle(request(jwtUtil.createToken(7L, 10L)), new MockHttpServletResponse(), new Object()))
            .isFalse();

        User disabledSystemUser = user(10L, true);
        when(disabledSystemUser.isSystemActive()).thenReturn(false);
        when(users.findById(7L)).thenReturn(Optional.of(disabledSystemUser));
        assertThat(interceptor.preHandle(request(jwtUtil.createToken(7L, 10L)), new MockHttpServletResponse(), new Object()))
            .isFalse();
        assertThat(JwtInterceptor.CURRENT_USER.get()).isNull();
    }

    @Test
    void clearsRequestContextAfterCompletion() {
        JwtInterceptor.CURRENT_USER.set(mock(User.class));
        JwtInterceptor.CURRENT_SYSTEM_ID.set(10L);
        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);
        assertThat(JwtInterceptor.CURRENT_USER.get()).isNull();
        assertThat(JwtInterceptor.CURRENT_SYSTEM_ID.get()).isNull();
    }

    private User user(Long systemId, boolean active) {
        User user = mock(User.class);
        when(user.getSystemId()).thenReturn(systemId);
        when(user.isActive()).thenReturn(active);
        when(user.isSystemActive()).thenReturn(true);
        return user;
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mdm/object-types");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
