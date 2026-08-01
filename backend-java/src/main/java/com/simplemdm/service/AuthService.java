package com.simplemdm.service;

import com.simplemdm.model.system.User;
import com.simplemdm.repository.system.UserRepository;
import com.simplemdm.repository.system.SystemRepository;
import com.simplemdm.security.JwtUtil;
import com.simplemdm.service.system.AuthorizationService;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository users;
    private final SystemRepository systems;
    private final EntityManager entityManager;
    private final JwtUtil jwtUtil;
    private final AuthorizationService authorization;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository users, SystemRepository systems, EntityManager entityManager,
                       JwtUtil jwtUtil, AuthorizationService authorization) {
        this.users = users;
        this.systems = systems;
        this.entityManager = entityManager;
        this.jwtUtil = jwtUtil;
        this.authorization = authorization;
    }

    public Map<String, Object> login(String systemCode, String username, String password) {
        var system = systems.findByCode(systemCode).orElse(null);
        User user = system == null ? null
            : users.findBySystemIdAndUsername(system.getId(), username).orElse(null);
        if (system == null || !system.isActive() || user == null
            || !encoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        if (!user.isActive() || !user.isSystemActive()) {
            throw new IllegalArgumentException("Account is disabled");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwtUtil.createToken(user.getId(), user.getSystemId()));
        result.put("user", userView(user));
        result.put("permissions", permissionViews(user));
        return result;
    }

    public Map<String, Object> userView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("system_id", user.getSystemId());
        view.put("department_id", user.getDepartmentId());
        view.put("username", user.getUsername());
        view.put("real_name", user.getRealName());
        view.put("is_admin", user.isSystemAdmin());
        view.put("status", user.getStatus());
        return view;
    }

    public List<Map<String, Object>> permissionViews(User user) {
        if (user.isSystemAdmin()) {
            return entityManager.createQuery(
                    "select p.code from Permission p where p.status='active' order by p.code", String.class)
                .getResultList().stream().map(code -> permissionView(user, code)).toList();
        }
        return entityManager.createQuery("""
                select distinct p.code
                from UserRole ur, RolePermission rp, Permission p, Role r
                where ur.userId=:userId and ur.systemId=:systemId
                  and ur.roleId=rp.roleId and ur.roleId=r.id and rp.permissionId=p.id
                  and r.system.id=:systemId and r.status='active' and p.status='active'
                order by p.code
                """, String.class)
            .setParameter("userId", user.getId())
            .setParameter("systemId", user.getSystemId())
            .getResultList().stream().map(code -> permissionView(user, code)).toList();
    }

    private Map<String, Object> permissionView(User user, String code) {
        boolean allowed = user.isSystemAdmin()
            || authorization.can(user.getId(), code, user.getDepartmentId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", code);
        view.put("permission_code", code);
        if (code.endsWith("_EDIT") || "MDM_FIELD_MANAGE".equals(code)) view.put("can_edit", allowed);
        if (code.endsWith("_EDIT")) {
            List<Long> editableDepartmentIds = authorization.viewableDepartmentIds(user.getId()).stream()
                .filter(departmentId -> authorization.can(user.getId(), code, departmentId))
                .toList();
            view.put("editable_department_ids", editableDepartmentIds);
        }
        if (code.endsWith("_VIEW")) view.put("can_view", allowed);
        return view;
    }
}
