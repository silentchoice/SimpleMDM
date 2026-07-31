package com.simplemdm.service.mdm;

import com.simplemdm.model.system.User;
import com.simplemdm.security.JwtInterceptor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class JwtCurrentUserProvider implements CurrentUserProvider {
    @Override
    public Optional<Long> currentSystemUserId() {
        User user = JwtInterceptor.CURRENT_USER.get();
        return user == null ? Optional.empty() : Optional.ofNullable(user.getId());
    }
}
