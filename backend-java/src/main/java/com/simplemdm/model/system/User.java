package com.simplemdm.model.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_username", columnNames = {"system_id", "username"}),
    @UniqueConstraint(name = "uk_user_system_id", columnNames = {"system_id", "id"})
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_system"))
    private SystemEntity system;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(value = {
        @JoinColumn(name = "system_id", referencedColumnName = "system_id", insertable = false, updatable = false),
        @JoinColumn(name = "department_id", referencedColumnName = "id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_user_department_system"))
    private Department department;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "real_name", nullable = false, length = 128)
    private String realName;

    @Column(length = 255)
    private String email;

    @Column(length = 64)
    private String mobile;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "is_system_admin", nullable = false)
    private boolean systemAdmin;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected User() {
    }

    private User(SystemEntity system, Department department, String username, String passwordHash, String realName) {
        this.system = system;
        this.department = department;
        this.departmentId = department.getId();
        this.username = username;
        this.passwordHash = passwordHash;
        this.realName = realName;
        this.status = "active";
        this.systemAdmin = false;
        this.failedLoginCount = 0;
    }

    public static User create(SystemEntity system, Department department, String username, String passwordHash,
                              String realName) {
        return new User(system, department, username, passwordHash, realName);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public SystemEntity getSystem() {
        return system;
    }

    public Long getSystemId() {
        return system.getId();
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public boolean isActive() {
        return "active".equals(status) && deletedAt == null;
    }

    public boolean isSystemActive() {
        return system != null && system.isActive();
    }
    public boolean isSystemAdmin() {
        return systemAdmin;
    }

    public void makeSystemAdmin() {
        systemAdmin = true;
    }

    public Department getDepartment() {
        return department;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRealName() {
        return realName;
    }

    public String getStatus() {
        return status;
    }
}
