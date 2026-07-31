package com.simplemdm.model.integration;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="sys_push_subscription")
public class PushSubscription {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="system_id",nullable=false) private Long systemId;
 @Column(name="endpoint_id",nullable=false) private Long endpointId; @Column(name="object_type_id") private Long objectTypeId;
 @Column(name="event_type",nullable=false,length=64) private String eventType; @Column(nullable=false,length=32) private String status;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt; @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 protected PushSubscription(){} public static PushSubscription active(Long id,Long system,Long endpoint,Long object,String event){PushSubscription s=new PushSubscription();s.id=id;s.systemId=system;s.endpointId=endpoint;s.objectTypeId=object;s.eventType=event;s.status="active";return s;}
 public Long getId(){return id;}public Long getSystemId(){return systemId;}public Long getEndpointId(){return endpointId;}public Long getObjectTypeId(){return objectTypeId;}public String getEventType(){return eventType;}public String getStatus(){return status;} @PrePersist void create(){createdAt=LocalDateTime.now();updatedAt=createdAt;}
}
