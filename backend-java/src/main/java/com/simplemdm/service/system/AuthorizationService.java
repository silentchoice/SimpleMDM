package com.simplemdm.service.system;

import com.simplemdm.model.system.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AuthorizationService {
    private final EntityManager entityManager;

    public AuthorizationService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public boolean can(Long userId, String permissionCode, Long departmentId) {
        if (userId == null || permissionCode == null || departmentId == null) return false;
        User user = entityManager.find(User.class, userId);
        Department target = entityManager.find(Department.class, departmentId);
        if (user == null || target == null || !user.getSystemId().equals(target.getSystem().getId())) return false;
        if (user.isSystemAdmin()) return true;
        if (!hasPermission(userId, user.getSystemId(), permissionCode)) return false;
        return scopesFor(userId, user.getSystemId()).stream()
            .filter(scope -> isViewPermission(permissionCode) ? scope.canView() : scope.canEdit())
            .anyMatch(scope -> includes(scope, target));
    }

    public Set<Long> viewableDepartmentIds(Long userId) {
        if (userId == null) return Set.of();
        User user = entityManager.find(User.class, userId);
        if (user == null) return Set.of();
        if (user.isSystemAdmin()) {
            return new LinkedHashSet<>(entityManager.createQuery(
                "select d.id from Department d where d.system.id = :systemId order by d.id", Long.class)
                .setParameter("systemId", user.getSystemId()).getResultList());
        }

        Set<Long> result = new LinkedHashSet<>();
        for (UserDepartmentScope scope : scopesFor(userId, user.getSystemId())) {
            if (!scope.canView()) continue;
            Department scoped = entityManager.find(Department.class, scope.getDepartmentId());
            if (scoped == null) continue;
            if (scope.getScopeMode() == UserDepartmentScope.ScopeMode.SELF) {
                result.add(scoped.getId());
            } else {
                result.addAll(entityManager.createQuery(
                    "select d.id from Department d where d.system.id = :systemId and d.path like concat(:path, '%')",
                    Long.class)
                    .setParameter("systemId", user.getSystemId())
                    .setParameter("path", scoped.getPath()).getResultList());
            }
        }
        return result;
    }

    private boolean hasPermission(Long userId, Long systemId, String permissionCode) {
        Long matches = entityManager.createQuery("""
            select count(ur)
            from UserRole ur, RolePermission rp, Permission p, Role r
            where ur.roleId = rp.roleId
              and ur.roleId = r.id
              and rp.permissionId = p.id
              and ur.userId = :userId
              and ur.systemId = :systemId
              and r.system.id = :systemId
              and r.status = 'active'
              and p.code = :permissionCode
              and p.status = 'active'
            """, Long.class)
            .setParameter("userId", userId)
            .setParameter("systemId", systemId)
            .setParameter("permissionCode", permissionCode)
            .getSingleResult();
        return matches > 0;
    }

    private List<UserDepartmentScope> scopesFor(Long userId, Long systemId) {
        return entityManager.createQuery(
            "select s from UserDepartmentScope s where s.userId = :userId and s.systemId = :systemId",
            UserDepartmentScope.class)
            .setParameter("userId", userId).setParameter("systemId", systemId).getResultList();
    }

    private boolean includes(UserDepartmentScope scope, Department target) {
        if (scope.getScopeMode() == UserDepartmentScope.ScopeMode.SELF) return scope.getDepartmentId().equals(target.getId());
        Department scoped = entityManager.find(Department.class, scope.getDepartmentId());
        return scoped != null && target.getPath().startsWith(scoped.getPath());
    }

    private boolean isViewPermission(String permissionCode) {
        return "VIEW".equals(permissionCode) || permissionCode.endsWith("_VIEW");
    }
}
