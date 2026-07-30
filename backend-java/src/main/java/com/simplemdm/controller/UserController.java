package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.SysUserRepository;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final SysUserRepository userRepo;
    public UserController(SysUserRepository ur) { this.userRepo = ur; }

    @GetMapping
    public ApiResponse list() {
        List<SysUser> users = userRepo.findAll();
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysUser u : users) {
            if ("active".equals(u.getStatus())) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId()); m.put("username", u.getUsername());
                m.put("real_name", u.getRealName()); m.put("is_admin", u.getIsAdmin());
                m.put("department", u.getDepartment()); m.put("status", u.getStatus());
                items.add(m);
            }
        }
        return ApiResponse.ok(items);
    }
}
