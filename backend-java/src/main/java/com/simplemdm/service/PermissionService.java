package com.simplemdm.service;

import com.simplemdm.model.SysUserPermission;
import com.simplemdm.repository.SysUserPermissionRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PermissionService {

    private final SysUserPermissionRepository permRepo;

    public PermissionService(SysUserPermissionRepository permRepo) {
        this.permRepo = permRepo;
    }

    /** Get all departments the user can VIEW */
    public List<String> getViewableDepts(Long userId) {
        List<SysUserPermission> perms = permRepo.findByUserIdAndPermType(userId, "VIEW");
        List<String> depts = new ArrayList<>();
        for (SysUserPermission p : perms) {
            if ("ALL".equals(p.getScopeType()) || "DEPT".equals(p.getScopeType())) {
                if (p.getScopeValue() != null) depts.add(p.getScopeValue());
                else return null; // null = wildcard: should query all
            }
        }
        // If any scope is ALL, return null to indicate no department filter
        for (SysUserPermission p : perms) {
            if ("ALL".equals(p.getScopeType())) return null;
        }
        return depts;
    }

    /** Get all departments the user can EDIT */
    public List<String> getEditableDepts(Long userId) {
        List<SysUserPermission> perms = permRepo.findByUserIdAndPermType(userId, "EDIT");
        List<String> depts = new ArrayList<>();
        for (SysUserPermission p : perms) {
            if ("ALL".equals(p.getScopeType())) return null;
            if (p.getScopeValue() != null) depts.add(p.getScopeValue());
        }
        return depts;
    }

    /** Get system codes the user can access for a given permission type.
     *  Returns null if user has ALL scope (admin sees all systems).
     *  Returns empty list if no permissions. */
    public List<String> getPermittedSystems(Long userId, String permType) {
        List<SysUserPermission> perms = permRepo.findByUserIdAndPermType(userId, permType);
        if (perms.isEmpty()) return List.of();
        for (SysUserPermission p : perms) {
            if ("ALL".equals(p.getScopeType())) return null; // null = all systems
        }
        return perms.stream()
            .map(SysUserPermission::getSystemCode)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    public List<SysUserPermission> getUserPermissions(Long userId) {
        return permRepo.findByUserId(userId);
    }

    public SysUserPermission addPermission(Long userId, String permType, String scopeType,
                                            String scopeValue, String systemCode) {
        SysUserPermission p = new SysUserPermission();
        p.setUserId(userId);
        p.setPermType(permType);
        p.setScopeType(scopeType);
        p.setScopeValue(scopeValue);
        p.setSystemCode(systemCode);
        return permRepo.save(p);
    }

    public void removePermission(Long userId, Long permId) {
        permRepo.deleteByUserIdAndId(userId, permId);
    }
}
