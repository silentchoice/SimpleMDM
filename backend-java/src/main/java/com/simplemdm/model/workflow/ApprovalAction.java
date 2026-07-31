package com.simplemdm.model.workflow;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="wf_approval_action")
public class ApprovalAction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="system_id",nullable=false) private Long systemId; @Column(name="approval_request_id",nullable=false) private Long approvalRequestId;
 @Column(name="actor_id",nullable=false) private Long actorId; @Column(nullable=false,length=32) private String action;
 @Column(length=2048) private String comment; @Column(name="acted_at",nullable=false) private LocalDateTime actedAt;
 protected ApprovalAction(){} public static ApprovalAction approved(Long s,Long r,Long a){ApprovalAction x=new ApprovalAction();x.systemId=s;x.approvalRequestId=r;x.actorId=a;x.action="APPROVE";return x;}
 @PrePersist void create(){actedAt=LocalDateTime.now();} public Long getSystemId(){return systemId;} public Long getActorId(){return actorId;} public String getAction(){return action;}
}
