package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_personnel")
public class MdmPersonnel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_code", length = 32, nullable = false)
    private String systemCode = "HR";

    @Column(name = "owner_dept", length = 128)
    private String ownerDept;

    @Column(name = "data_json", columnDefinition = "LONGTEXT")
    private String dataJson = "{}";

    @Column(name = "employee_code", length = 32, nullable = false, unique = true)
    private String employeeCode;

    @Column(length = 64, nullable = false)
    private String name;

    @Column(length = 4)
    private String gender;

    @Column(length = 128, nullable = false)
    private String department;

    @Column(length = 128)
    private String position;

    @Column(length = 32)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(length = 32, nullable = false)
    private String status = "active";  // active | inactive | pending_approval

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public String getOwnerDept() { return ownerDept; }
    public void setOwnerDept(String ownerDept) { this.ownerDept = ownerDept; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
