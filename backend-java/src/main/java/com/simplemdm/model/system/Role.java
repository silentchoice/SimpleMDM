package com.simplemdm.model.system;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_role", uniqueConstraints = {
    @UniqueConstraint(name = "uk_role_code", columnNames = {"system_id", "code"}),
    @UniqueConstraint(name = "uk_role_system_id", columnNames = {"system_id", "id"})
})
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_id", nullable = false, foreignKey = @ForeignKey(name = "fk_role_system"))
    private SystemEntity system;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 128) private String name;
    @Column(length = 512) private String description;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Version @Column(nullable = false) private Long version;

    protected Role() { }
    private Role(SystemEntity system, String code, String name) {
        this.system = system; this.code = code; this.name = name; this.status = "active";
    }
    public static Role create(SystemEntity system, String code, String name) { return new Role(system, code, name); }
    @PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public SystemEntity getSystem() { return system; }
    public String getStatus() { return status; }
}
