package com.simplemdm.model.system;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "sys_user_role")
@IdClass(UserRole.Key.class)
public class UserRole {
    @Id @Column(name = "user_id", nullable = false) private Long userId;
    @Id @Column(name = "role_id", nullable = false) private Long roleId;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected UserRole() { }
    private UserRole(SystemEntity system, User user, Role role) {
        systemId = system.getId(); userId = user.getId(); roleId = role.getId();
    }
    public static UserRole assign(SystemEntity system, User user, Role role) { return new UserRole(system, user, role); }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }

    public static class Key implements Serializable {
        private Long userId;
        private Long roleId;
        public Key() { }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(roleId, key.roleId);
        }
        @Override public int hashCode() { return Objects.hash(userId, roleId); }
    }
}
