package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "wf_approval")
public class WfApproval {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "personnel_id", nullable = false)
    private Long personnelId;

    @Column(name = "sub_id")
    private Long subId;  // FK to mdm_personnel_sub, nullable

    @Column(name = "workflow_type", length = 16, nullable = false)
    private String workflowType;  // create | update | sub_update

    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(length = 16, nullable = false)
    private String status = "pending";  // pending | approved | rejected | withdrawn

    @Column(name = "change_data", columnDefinition = "TEXT")
    private String changeData;  // JSON diff

    @Column(name = "submit_time")
    private LocalDateTime submitTime;

    @Column(name = "approve_time")
    private LocalDateTime approveTime;

    @Column(name = "approve_comment", columnDefinition = "TEXT")
    private String approveComment;

    @Column(name = "withdrawn_time")
    private LocalDateTime withdrawnTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); if (submitTime == null) submitTime = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPersonnelId() { return personnelId; }
    public void setPersonnelId(Long personnelId) { this.personnelId = personnelId; }
    public Long getSubId() { return subId; }
    public void setSubId(Long subId) { this.subId = subId; }
    public String getWorkflowType() { return workflowType; }
    public void setWorkflowType(String workflowType) { this.workflowType = workflowType; }
    public Long getSubmitterId() { return submitterId; }
    public void setSubmitterId(Long submitterId) { this.submitterId = submitterId; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getChangeData() { return changeData; }
    public void setChangeData(String changeData) { this.changeData = changeData; }
    public LocalDateTime getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDateTime submitTime) { this.submitTime = submitTime; }
    public LocalDateTime getApproveTime() { return approveTime; }
    public void setApproveTime(LocalDateTime approveTime) { this.approveTime = approveTime; }
    public String getApproveComment() { return approveComment; }
    public void setApproveComment(String approveComment) { this.approveComment = approveComment; }
    public LocalDateTime getWithdrawnTime() { return withdrawnTime; }
    public void setWithdrawnTime(LocalDateTime withdrawnTime) { this.withdrawnTime = withdrawnTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
