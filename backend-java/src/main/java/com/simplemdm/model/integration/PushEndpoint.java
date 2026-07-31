package com.simplemdm.model.integration;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="sys_push_endpoint")
public class PushEndpoint {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="system_id",nullable=false) private Long systemId;
 @Column(nullable=false,length=64) private String code; @Column(nullable=false,length=128) private String name; @Column(name="endpoint_url",nullable=false,length=2048) private String endpointUrl;
 @Column(name="authentication_type",nullable=false,length=32) private String authenticationType; @Column(name="encrypted_credentials",length=4096) private String encryptedCredentials;
 @Column(nullable=false,length=32) private String status; @Column(name="created_at",nullable=false) private LocalDateTime createdAt; @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt; @Version private Long version;
 protected PushEndpoint(){} public static PushEndpoint create(Long system,String code,String name,String url,String auth){PushEndpoint e=new PushEndpoint();e.systemId=system;e.code=code;e.name=name;e.endpointUrl=url;e.authenticationType=auth==null?"NONE":auth;e.status="active";return e;} @PrePersist void create(){createdAt=LocalDateTime.now();updatedAt=createdAt;} public Long getId(){return id;}public Long getSystemId(){return systemId;}public String getCode(){return code;}public String getName(){return name;}public String getEndpointUrl(){return endpointUrl;}public String getAuthenticationType(){return authenticationType;}public String getStatus(){return status;}
}
