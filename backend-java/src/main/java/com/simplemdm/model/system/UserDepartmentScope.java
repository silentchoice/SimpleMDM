package com.simplemdm.model.system;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user_department_scope", uniqueConstraints = @UniqueConstraint(name = "uk_user_department_scope", columnNames = {"user_id", "department_id", "scope_mode"}))
public class UserDepartmentScope {
    public enum ScopeMode { SELF, SUBTREE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "system_id", nullable = false) private Long systemId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "department_id", nullable = false) private Long departmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_mode", nullable = false, length = 16) private ScopeMode scopeMode;
    @Column(name = "can_view", nullable = false) private boolean canView;
    @Column(name = "can_edit", nullable = false) private boolean canEdit;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected UserDepartmentScope() { }
    private UserDepartmentScope(SystemEntity system, User user, Department department, ScopeMode scopeMode,
                                boolean canView, boolean canEdit) {
        systemId = system.getId(); userId = user.getId(); departmentId = department.getId();
        this.scopeMode = scopeMode; this.canView = canView; this.canEdit = canEdit;
    }
    public static UserDepartmentScope grant(SystemEntity system, User user, Department department,
                                            ScopeMode scopeMode, boolean canView, boolean canEdit) {
        return new UserDepartmentScope(system, user, department, scopeMode, canView, canEdit);
    }
    @PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getDepartmentId() { return departmentId; }
    public ScopeMode getScopeMode() { return scopeMode; }
    public boolean canView() { return canView; }
    public boolean canEdit() { return canEdit; }
}
