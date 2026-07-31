package com.simplemdm.model.integration;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="sys_push_endpoint")
public class PushEndpoint {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="system_id",nullable=false) private Long systemId;
 @Column(nullable=false,length=64) private String code; @Column(nullable=false,length=128) private String name; @Column(name="endpoint_url",nullable=false,length=2048) private String endpointUrl;
 @Column(name="authentication_type",nullable=false,length=32) private String authenticationType; @Column(name="encrypted_credentials",length=4096) private String encryptedCredentials;
 @Column(nullable=false,length=32) private String status; @Column(name="created_at",nullable=false) private LocalDateTime createdAt; @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt; @Version private Long version;
 protected PushEndpoint(){}
}
