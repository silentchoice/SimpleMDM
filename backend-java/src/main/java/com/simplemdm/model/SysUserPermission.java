package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user_permission")
public class SysUserPermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "perm_type", length = 16, nullable = false)
    private String permType;  // VIEW | EDIT

    @Column(name = "scope_type", length = 16, nullable = false)
    private String scopeType;  // DEPT | POSITION | ALL

    @Column(name = "scope_value", length = 128)
    private String scopeValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPermType() { return permType; }
    public void setPermType(String permType) { this.permType = permType; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeValue() { return scopeValue; }
    public void setScopeValue(String scopeValue) { this.scopeValue = scopeValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
