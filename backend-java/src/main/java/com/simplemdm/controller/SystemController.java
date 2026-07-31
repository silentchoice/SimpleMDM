package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.system.SystemRepository;
import com.simplemdm.security.JwtInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/systems")
public class SystemController {
    private final SystemRepository systems;

    public SystemController(SystemRepository systems) {
        this.systems = systems;
    }

    @GetMapping
    public ApiResponse list() {
        Long systemId = currentUser().getSystemId();
        return ApiResponse.ok(systems.findAll().stream()
            .filter(system -> systemId.equals(system.getId()))
            .map(system -> java.util.Map.of("id", system.getId(), "code", system.getCode(), "name", system.getName()))
            .toList());
    }

    static User currentUser() {
        User user = JwtInterceptor.CURRENT_USER.get();
        if (user == null || user.getId() == null || user.getSystemId() == null) {
            throw new BusinessException(401, "No authenticated system user is available");
        }
        return user;
    }
}