package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_push_api")
public class SysPushApi {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(name = "target_system", length = 32, unique = true, nullable = false)
    private String targetSystem;

    @Column(length = 8, nullable = false)
    private String method = "POST";

    @Column(name = "base_url", length = 512, nullable = false)
    private String baseUrl;

    @Column(name = "auth_type", length = 16, nullable = false)
    private String authType = "token";

    @Column(name = "auth_config", columnDefinition = "TEXT")
    private String authConfig;  // JSON

    @Column(length = 16, nullable = false)
    private String status = "active";  // active | inactive

    @Column(length = 512)
    private String description;

    @Column(name = "retry_max", nullable = false)
    private Integer retryMax = 3;

    @Column(name = "timeout_sec", nullable = false)
    private Integer timeoutSec = 30;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAuthConfig() { return authConfig; }
    public void setAuthConfig(String authConfig) { this.authConfig = authConfig; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getRetryMax() { return retryMax; }
    public void setRetryMax(Integer retryMax) { this.retryMax = retryMax; }
    public Integer getTimeoutSec() { return timeoutSec; }
    public void setTimeoutSec(Integer timeoutSec) { this.timeoutSec = timeoutSec; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
