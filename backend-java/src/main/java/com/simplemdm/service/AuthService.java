package com.simplemdm.service;

import com.simplemdm.model.SysUser;
import com.simplemdm.model.SysUserPermission;
import com.simplemdm.repository.SysUserRepository;
import com.simplemdm.repository.SysUserPermissionRepository;
import com.simplemdm.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AuthService {

    private final SysUserRepository userRepo;
    private final SysUserPermissionRepository permRepo;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(SysUserRepository userRepo, SysUserPermissionRepository permRepo, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.permRepo = permRepo;
        this.jwtUtil = jwtUtil;
    }

    public String hashPassword(String password) {
        return encoder.encode(password);
    }

    public boolean verifyPassword(String raw, String hashed) {
        return encoder.matches(raw, hashed);
    }

    public Map<String, Object> login(String username, String password) {
        Optional<SysUser> opt = userRepo.findByUsername(username);
        if (opt.isEmpty() || !verifyPassword(password, opt.get().getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }
        SysUser user = opt.get();
        if (!"active".equals(user.getStatus())) {
            throw new RuntimeException("账号已被禁用");
        }

        String token = jwtUtil.createToken(user.getId());
        List<SysUserPermission> perms = permRepo.findByUserId(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("real_name", user.getRealName());
        userMap.put("department", user.getDepartment());
        userMap.put("is_admin", user.getIsAdmin());
        userMap.put("status", user.getStatus());
        result.put("user", userMap);

        List<Map<String, Object>> permList = new ArrayList<>();
        for (SysUserPermission p : perms) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("id", p.getId());
            pm.put("perm_type", p.getPermType());
            pm.put("scope_type", p.getScopeType());
            pm.put("scope_value", p.getScopeValue());
            pm.put("system_code", p.getSystemCode());
            permList.add(pm);
        }
        result.put("permissions", permList);

        return result;
    }

    public SysUser getCurrentUser(Long userId) {
        return userRepo.findById(userId).orElse(null);
    }
}
