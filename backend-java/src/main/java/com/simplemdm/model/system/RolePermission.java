package com.simplemdm.model.system;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "sys_role_permission")
@IdClass(RolePermission.Key.class)
public class RolePermission {
    @Id @Column(name = "role_id", nullable = false) private Long roleId;
    @Id @Column(name = "permission_id", nullable = false) private Long permissionId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected RolePermission() { }
    private RolePermission(Role role, Permission permission) {
        roleId = role.getId(); permissionId = permission.getId();
    }
    public static RolePermission grant(Role role, Permission permission) { return new RolePermission(role, permission); }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }

    public static class Key implements Serializable {
        private Long roleId;
        private Long permissionId;
        public Key() { }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(roleId, key.roleId) && Objects.equals(permissionId, key.permissionId);
        }
        @Override public int hashCode() { return Objects.hash(roleId, permissionId); }
    }
}
