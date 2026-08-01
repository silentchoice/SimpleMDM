package com.simplemdm.model.system;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_permission", uniqueConstraints = @UniqueConstraint(name = "uk_permission_code", columnNames = "code"))
public class Permission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 128) private String code;
    @Column(nullable = false, length = 128) private String name;
    @Column(length = 512) private String description;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Version @Column(nullable = false) private Long version;

    protected Permission() { }
    private Permission(String code, String name) { this.code = code; this.name = name; this.status = "active"; }
    public static Permission create(String code, String name) { return new Permission(code, name); }
    @PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public String getStatus() { return status; }
}
