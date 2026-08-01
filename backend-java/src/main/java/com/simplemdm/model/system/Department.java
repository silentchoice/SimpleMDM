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
@Table(name = "sys_department", uniqueConstraints = {
    @UniqueConstraint(name = "uk_department_code", columnNames = {"system_id", "code"}),
    @UniqueConstraint(name = "uk_department_parent_name", columnNames = {"system_id", "parent_id", "name"}),
    @UniqueConstraint(name = "uk_department_system_id", columnNames = {"system_id", "id"})
})
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_id", nullable = false, foreignKey = @ForeignKey(name = "fk_department_system"))
    private SystemEntity system;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
        @JoinColumn(name = "system_id", referencedColumnName = "system_id", insertable = false, updatable = false),
        @JoinColumn(name = "parent_id", referencedColumnName = "id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_department_parent_system"))
    private Department parent;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false, length = 2048)
    private String path;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Department() {
    }

    private Department(SystemEntity system, Department parent, String code, String name) {
        this.system = system;
        this.parent = parent;
        this.parentId = parent == null ? null : parent.getId();
        this.code = code;
        this.name = name;
        this.level = parent == null ? 1 : parent.getLevel() + 1;
        this.path = "/";
        this.sortOrder = 0;
        this.status = "active";
    }

    public static Department create(SystemEntity system, Department parent, String code, String name) {
        return new Department(system, parent, code, name);
    }

    public void relocate(Department newParent, String newPath, int newLevel) {
        this.parent = newParent;
        this.parentId = newParent == null ? null : newParent.getId();
        this.path = newPath;
        this.level = newLevel;
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

    public String getCode() { return code; }

    public String getName() { return name; }

    public SystemEntity getSystem() {
        return system;
    }

    public Department getParent() {
        return parent;
    }

    public int getLevel() {
        return level;
    }

    public String getPath() {
        return path;
    }
}
