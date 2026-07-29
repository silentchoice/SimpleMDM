package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_field_definition")
public class MdmFieldDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 128, nullable = false)
    private String department;         // 所属部门

    @Column(name = "system_code", length = 32, nullable = false)
    private String systemCode = "HR";

    @Column(name = "table_type", length = 16, nullable = false)
    private String tableType = "sub";   // master | sub  — 主表共享 / 子表隔离

    @Column(name = "sub_type", length = 64, nullable = false)
    private String subType;            // 字段组: salary, project, sales_target ...

    @Column(name = "field_name", length = 128, nullable = false)
    private String fieldName;          // 字段名: 基本工资, 项目名称 ...

    @Column(name = "field_type", length = 32, nullable = false)
    private String fieldType = "string"; // string | number | date | select

    @Column(nullable = false)
    private Boolean required = false;  // 是否必填

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;     // 排序

    @Column(name = "created_by")
    private Long createdBy;            // 创建人用户ID

    @Column(name = "created_by_name", length = 64)
    private String createdByName;      // 创建人姓名

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public String getTableType() { return tableType; }
    public void setTableType(String tableType) { this.tableType = tableType; }
    public String getSubType() { return subType; }
    public void setSubType(String subType) { this.subType = subType; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
