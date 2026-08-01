package com.simplemdm.service;

import com.simplemdm.model.system.User;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.repository.system.SystemRepository;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.security.JwtUtil;
import com.simplemdm.service.system.AuthorizationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    @Test
    void rejectsLoginWhenOwningSystemIsDisabled() {
        UserRepository users = mock(UserRepository.class);
        SystemRepository systems = mock(SystemRepository.class);
        SystemEntity system = mock(SystemEntity.class);
        User user = mock(User.class);
        when(system.getId()).thenReturn(42L);
        when(system.isActive()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn(new BCryptPasswordEncoder().encode("correct-password"));
        when(user.isActive()).thenReturn(true);
        when(user.isSystemActive()).thenReturn(false);
        when(systems.findByCode("ERP")).thenReturn(Optional.of(system));
        when(users.findBySystemIdAndUsername(42L, "operator")).thenReturn(Optional.of(user));
        AuthService service = new AuthService(users, systems, mock(EntityManager.class),
            new JwtUtil("01234567890123456789012345678901", 60), mock(AuthorizationService.class));

        assertThatThrownBy(() -> service.login("ERP", "operator", "correct-password"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account is disabled");
    }
}
