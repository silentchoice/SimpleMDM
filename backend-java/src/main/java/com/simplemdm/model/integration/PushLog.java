package com.simplemdm.model.integration;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="sys_push_log")
public class PushLog {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="system_id",nullable=false) private Long systemId;
 @Column(name="subscription_id",nullable=false) private Long subscriptionId; @Column(name="record_id") private Long recordId;
 @Column(name="event_id",nullable=false,length=128) private String eventId; @Column(nullable=false,length=32) private String status;
 @Column(name="retry_count",nullable=false) private Integer retryCount; @Column(name="request_snapshot") private String requestSnapshot;
 @Column(name="response_snapshot") private String responseSnapshot; @Column(name="last_attempt_at") private LocalDateTime lastAttemptAt;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 protected PushLog(){} public static PushLog pending(Long system,Long subscription,Long record,String event,String snapshot){PushLog l=new PushLog();l.systemId=system;l.subscriptionId=subscription;l.recordId=record;l.eventId=event;l.requestSnapshot=snapshot;l.status="pending";l.retryCount=0;return l;}
 @PrePersist void create(){createdAt=LocalDateTime.now();}
 public Long getSystemId(){return systemId;}public Long getSubscriptionId(){return subscriptionId;}public Long getRecordId(){return recordId;}public String getEventId(){return eventId;}public String getRequestSnapshot(){return requestSnapshot;}
}
