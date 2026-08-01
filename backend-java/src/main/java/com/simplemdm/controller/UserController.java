package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.system.User;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.security.JwtInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public ApiResponse list() {
        User current = JwtInterceptor.CURRENT_USER.get();
        if (current == null) return ApiResponse.error(401, "Authentication required");
        return ApiResponse.ok(users.findBySystemIdAndStatusOrderById(current.getSystemId(), "active").stream()
            .filter(User::isActive)
            .map(user -> {
                var view = new LinkedHashMap<String, Object>();
                view.put("id", user.getId());
                view.put("username", user.getUsername());
                view.put("real_name", user.getRealName());
                view.put("system_id", user.getSystemId());
                view.put("department_id", user.getDepartmentId());
                view.put("is_admin", user.isSystemAdmin());
                view.put("status", user.getStatus());
                return view;
            }).toList());
    }
}
