package com.simplemdm.model.workflow;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="sys_approver_assignment")
public class ApproverAssignment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="system_id",nullable=false) private Long systemId; @Column(name="object_type_id") private Long objectTypeId;
 @Column(name="department_id",nullable=false) private Long departmentId; @Column(name="approver_user_id",nullable=false) private Long approverUserId;
 @Column(nullable=false,length=32) private String status; @Column(name="created_at",nullable=false) private LocalDateTime createdAt; @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 protected ApproverAssignment(){} @PrePersist void create(){createdAt=updatedAt=LocalDateTime.now();} @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}
