package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.SysUserPermission;
import com.simplemdm.service.PermissionService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/users/{userId}/permissions")
public class PermissionController {

    private final PermissionService permService;
    public PermissionController(PermissionService ps) { this.permService = ps; }

    @GetMapping
    public ApiResponse list(@PathVariable Long userId) {
        List<SysUserPermission> perms = permService.getUserPermissions(userId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysUserPermission p : perms) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId()); m.put("perm_type", p.getPermType());
            m.put("scope_type", p.getScopeType()); m.put("scope_value", p.getScopeValue());
            items.add(m);
        }
        return ApiResponse.ok(items);
    }

    @PostMapping
    public ApiResponse add(@PathVariable Long userId, @RequestBody PermissionDTO dto) {
        SysUserPermission p = permService.addPermission(userId, dto.permType, dto.scopeType, dto.scopeValue);
        return ApiResponse.ok("权限已添加", Map.of("id", p.getId()));
    }

    @DeleteMapping("/{permId}")
    public ApiResponse remove(@PathVariable Long userId, @PathVariable Long permId) {
        permService.removePermission(userId, permId);
        return ApiResponse.ok("权限已移除", null);
    }
}
