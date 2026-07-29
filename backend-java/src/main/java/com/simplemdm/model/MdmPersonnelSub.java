package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_personnel_sub")
public class MdmPersonnelSub {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "personnel_id", nullable = false)
    private Long personnelId;

    @Column(name = "sub_type", length = 64, nullable = false)
    private String subType;  // salary | project | sales_target | ...

    @Column(name = "data_json", columnDefinition = "TEXT", nullable = false)
    private String dataJson;  // JSON: {"field1":"value1", "field2":"value2"}

    @Column(name = "owner_dept", length = 128, nullable = false)
    private String ownerDept;

    @Column(length = 16, nullable = false)
    private String visibility = "private";  // private | pending_share | shared

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPersonnelId() { return personnelId; }
    public void setPersonnelId(Long personnelId) { this.personnelId = personnelId; }
    public String getSubType() { return subType; }
    public void setSubType(String subType) { this.subType = subType; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    public String getOwnerDept() { return ownerDept; }
    public void setOwnerDept(String ownerDept) { this.ownerDept = ownerDept; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
