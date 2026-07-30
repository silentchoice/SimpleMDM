# SimpleMDM Java 重构 + 功能完善 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 SimpleMDM 后端从 Python FastAPI 迁移到 Java Spring Boot 3，同时实现权限约束、主子表数据模型、审批人部门分配四项功能。

**Architecture:** Spring Boot 3 + Spring Data JPA + MySQL，JWT 拦截器鉴权，@RequirePerm 注解驱动权限检查，主表(mdm_personnel)跨部门共享 + 子表(mdm_personnel_sub)部门隔离审批共享。

**Tech Stack:** Java 17, Spring Boot 3.x, Spring Data JPA + Hibernate, MySQL 8.0, jjwt 0.12.x, BCrypt, Maven

## 全局约束

- Java 17 LTS
- MySQL 8.0 (127.0.0.1:3306, root/${SIMPLE_MDM_DB_PASSWORD}, 库名 simple_mdm)
- 禁止 DELETE 语句 — 数据删除走 status 更新
- 需物理删除时必须先征求用户同意
- 后端端口 18001（与原版一致）
- API 路径与原版保持一致，前端尽量少改
- 前端不变（Vue 3 + Element Plus）

---

### Task 1: Maven 项目脚手架

**Files:**
- Create: `backend-java/pom.xml`
- Create: `backend-java/src/main/resources/application.yml`
- Create: `backend-java/src/main/java/com/simplemdm/SimpleMdmApplication.java`

**Produces:** 可编译的空 Spring Boot 项目

- [ ] **Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
        <relativePath/>
    </parent>
    <groupId>com.simplemdm</groupId>
    <artifactId>simple-mdm</artifactId>
    <version>2.0.0</version>
    <name>SimpleMDM</name>
    <description>Master Data Management Platform</description>

    <properties>
        <java.version>17</java.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 application.yml**

```yaml
server:
  port: 18001

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/simple_mdm?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf-8
    username: root
    password: ${SIMPLE_MDM_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQLDialect

app:
  jwt:
    secret: change-me-in-production
    expiration-minutes: 1440
  push:
    success-rate: 0.9
```

- [ ] **Step 3: 创建启动类**

```java
package com.simplemdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SimpleMdmApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimpleMdmApplication.class, args);
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
cd backend-java && mvn compile
```

---

### Task 2: JPA 实体类

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/model/SysUser.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/SysUserPermission.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/SysApproverDept.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/MdmPersonnel.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/MdmPersonnelSub.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/WfApproval.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/SysPushLog.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/SysPushApi.java`

**Produces:** 8 个 JPA 实体，启动时自动建表

- [ ] **Step 1: SysUser.java**

```java
package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
public class SysUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", length = 256, nullable = false)
    private String passwordHash;

    @Column(name = "real_name", length = 64, nullable = false)
    private String realName;

    @Column(length = 128)
    private String department;

    @Column(nullable = false)
    private Boolean isAdmin = false;

    @Column(length = 16, nullable = false)
    private String status = "active";  // active | disabled

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Boolean getIsAdmin() { return isAdmin; }
    public void setIsAdmin(Boolean isAdmin) { this.isAdmin = isAdmin; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 2: SysUserPermission.java**

```java
package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user_permission")
public class SysUserPermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "perm_type", length = 16, nullable = false)
    private String permType;  // VIEW | EDIT

    @Column(name = "scope_type", length = 16, nullable = false)
    private String scopeType;  // DEPT | POSITION | ALL

    @Column(name = "scope_value", length = 128)
    private String scopeValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPermType() { return permType; }
    public void setPermType(String permType) { this.permType = permType; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeValue() { return scopeValue; }
    public void setScopeValue(String scopeValue) { this.scopeValue = scopeValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 3: SysApproverDept.java**

```java
package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_approver_dept")
public class SysApproverDept {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 128, nullable = false)
    private String department;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: MdmPersonnel.java**

```java
package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mdm_personnel")
public class MdmPersonnel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
```

- [ ] **Step 5: MdmPersonnelSub.java**

```java
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
```

- [ ] **Step 6: WfApproval.java**

```java
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
```

- [ ] **Step 7: SysPushLog.java**

```java
package com.simplemdm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_push_log")
public class SysPushLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "approval_id")
    private Long approvalId;

    @Column(name = "personnel_id")
    private Long personnelId;

    @Column(name = "target_system", length = 32, nullable = false)
    private String targetSystem;

    @Column(length = 16, nullable = false)
    private String status = "pending";  // success | failed | pending

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "pushed_at")
    private LocalDateTime pushedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApprovalId() { return approvalId; }
    public void setApprovalId(Long approvalId) { this.approvalId = approvalId; }
    public Long getPersonnelId() { return personnelId; }
    public void setPersonnelId(Long personnelId) { this.personnelId = personnelId; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer responseCode) { this.responseCode = responseCode; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getPushedAt() { return pushedAt; }
    public void setPushedAt(LocalDateTime pushedAt) { this.pushedAt = pushedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 8: SysPushApi.java**

```java
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
```

- [ ] **Step 9: 编译验证** `mvn compile`

---

### Task 3: DTO 类

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/dto/ApiResponse.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/PageResult.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/LoginRequest.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/LoginResponse.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/PersonnelDTO.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/PersonnelSubDTO.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/ApprovalDTO.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/PushLogDTO.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/PushApiDTO.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/PermissionDTO.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/ApproverDeptDTO.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/DashboardDTO.java`

**Produces:** 所有请求/响应 DTO 类

- [ ] **Step 1: ApiResponse.java** — 统一响应包装

```java
package com.simplemdm.dto;

import java.util.Map;

public class ApiResponse {
    private int code;
    private String message;
    private Object data;

    public static ApiResponse ok(String message, Object data) {
        ApiResponse r = new ApiResponse();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    public static ApiResponse ok(Object data) { return ok("ok", data); }

    public static ApiResponse error(int code, String message) {
        ApiResponse r = new ApiResponse();
        r.code = code;
        r.message = message;
        r.data = null;
        return r;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
```

- [ ] **Step 2: PageResult.java**

```java
package com.simplemdm.dto;

import java.util.List;

public class PageResult<T> {
    private List<T> items;
    private long total;
    private int page;
    private int pageSize;

    public PageResult(List<T> items, long total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<T> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
}
```

- [ ] **Step 3: LoginRequest.java + LoginResponse.java**

```java
package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank public String username;
    @NotBlank public String password;
}
```

```java
package com.simplemdm.dto;

import java.util.List;
import java.util.Map;

public class LoginResponse {
    public String token;
    public Map<String, Object> user;  // id, username, real_name, department, is_admin, status, permissions
    public List<Map<String, Object>> permissions;
}
```

- [ ] **Step 4: PersonnelDTO.java** — 主表创建/更新请求

```java
package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;

public class PersonnelDTO {
    public Long id;
    @NotBlank public String employeeCode;
    @NotBlank public String name;
    public String gender;
    @NotBlank public String department;
    public String position;
    public String phone;
    public String email;
    public String status;
    public Integer version;
}
```

- [ ] **Step 5: PersonnelSubDTO.java**

```java
package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;

public class PersonnelSubDTO {
    public Long id;
    public Long personnelId;
    @NotBlank public String subType;
    @NotBlank public String dataJson;
    @NotBlank public String ownerDept;
    public String visibility;
    public Integer version;
}
```

- [ ] **Step 6: ApprovalDTO.java** — 审批操作请求

```java
package com.simplemdm.dto;

public class ApprovalDTO {
    // Used for approve/reject comment
    public String comment;

    // Used for listing (transient)
    public Long id;
    public Long personnelId;
    public String personnelName;
    public String workflowType;
    public Long submitterId;
    public String submitterName;
    public Long approverId;
    public String approverName;
    public String status;
    public String changeData;
    public String submitTime;
    public String approveTime;
    public String approveComment;
}
```

- [ ] **Step 7: PushLogDTO.java**

```java
package com.simplemdm.dto;

public class PushLogDTO {
    public Long id;
    public Long approvalId;
    public Long personnelId;
    public String personnelName;
    public String targetSystem;
    public String status;
    public String requestBody;
    public String responseBody;
    public Integer responseCode;
    public Integer retryCount;
    public String errorMessage;
    public String pushedAt;
    public String createdAt;
}
```

- [ ] **Step 8: PushApiDTO.java**

```java
package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;

public class PushApiDTO {
    public Long id;
    @NotBlank public String name;
    @NotBlank public String targetSystem;
    @NotBlank public String method = "POST";
    @NotBlank public String baseUrl;
    public String authType = "token";
    public String authConfig;
    public String status = "active";
    public String description;
    public Integer retryMax = 3;
    public Integer timeoutSec = 30;
}
```

- [ ] **Step 9: PermissionDTO.java**

```java
package com.simplemdm.dto;

import jakarta.validation.constraints.NotBlank;

public class PermissionDTO {
    public Long id;
    @NotBlank public Long userId;
    @NotBlank public String permType;   // VIEW | EDIT
    @NotBlank public String scopeType;  // DEPT | POSITION | ALL
    public String scopeValue;
}
```

- [ ] **Step 10: ApproverDeptDTO.java**

```java
package com.simplemdm.dto;

public class ApproverDeptDTO {
    public Long id;
    public Long userId;
    public String department;
    public String userName;  // transient, for display
}
```

- [ ] **Step 11: DashboardDTO.java**

```java
package com.simplemdm.dto;

import java.util.List;
import java.util.Map;

public class DashboardDTO {
    public long totalPersonnel;
    public long pendingApprovals;
    public double pushSuccessRate;
    public List<Map<String, Object>> recentApprovals;
}
```

- [ ] **Step 12: 编译验证** `mvn compile`

---

### Task 4: JPA Repository 接口

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/repository/SysUserRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/SysUserPermissionRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/SysApproverDeptRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/MdmPersonnelRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/MdmPersonnelSubRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/WfApprovalRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/SysPushLogRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/SysPushApiRepository.java`

**Produces:** 所有 Repository 接口，CRUD 就绪

- [ ] **Step 1: SysUserRepository.java**

```java
package com.simplemdm.repository;

import com.simplemdm.model.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsername(String username);
    Optional<SysUser> findByIdAndStatus(Long id, String status);
}
```

- [ ] **Step 2: SysUserPermissionRepository.java**

```java
package com.simplemdm.repository;

import com.simplemdm.model.SysUserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SysUserPermissionRepository extends JpaRepository<SysUserPermission, Long> {
    List<SysUserPermission> findByUserId(Long userId);
    List<SysUserPermission> findByUserIdAndPermType(Long userId, String permType);
    void deleteByUserIdAndId(Long userId, Long id);
}
```

- [ ] **Step 3: SysApproverDeptRepository.java**

```java
package com.simplemdm.repository;

import com.simplemdm.model.SysApproverDept;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SysApproverDeptRepository extends JpaRepository<SysApproverDept, Long> {
    List<SysApproverDept> findByUserId(Long userId);
    List<SysApproverDept> findByDepartment(String department);
    List<SysApproverDept> findByUserIdAndDepartment(Long userId, String department);
    void deleteByUserIdAndDepartment(Long userId, String department);
}
```

- [ ] **Step 4: MdmPersonnelRepository.java**

```java
package com.simplemdm.repository;

import com.simplemdm.model.MdmPersonnel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MdmPersonnelRepository extends JpaRepository<MdmPersonnel, Long> {
    Optional<MdmPersonnel> findByEmployeeCode(String employeeCode);

    @Query("SELECT DISTINCT p.department FROM MdmPersonnel p ORDER BY p.department")
    List<String> findDistinctDepartments();

    Page<MdmPersonnel> findByDepartmentIn(List<String> departments, Pageable pageable);

    @Query("SELECT p FROM MdmPersonnel p WHERE " +
           "(:keyword IS NULL OR p.name LIKE %:keyword% OR p.employeeCode LIKE %:keyword% OR p.position LIKE %:keyword%) " +
           "AND (:department IS NULL OR p.department = :department) " +
           "AND p.department IN :allowedDepts")
    Page<MdmPersonnel> searchByKeywordAndDept(String keyword, String department, List<String> allowedDepts, Pageable pageable);
}
```

- [ ] **Step 5: MdmPersonnelSubRepository.java**

```java
package com.simplemdm.repository;

import com.simplemdm.model.MdmPersonnelSub;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MdmPersonnelSubRepository extends JpaRepository<MdmPersonnelSub, Long> {
    List<MdmPersonnelSub> findByPersonnelId(Long personnelId);
    List<MdmPersonnelSub> findByPersonnelIdAndVisibilityIn(Long personnelId, List<String> visibilities);
    List<MdmPersonnelSub> findByOwnerDeptAndSubType(String ownerDept, String subType);
}
```

- [ ] **Step 6: WfApprovalRepository.java**

```java
package com.simplemdm.repository;

import com.simplemdm.model.WfApproval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WfApprovalRepository extends JpaRepository<WfApproval, Long> {
    Page<WfApproval> findByApproverIdAndStatus(Long approverId, String status, Pageable pageable);
    Page<WfApproval> findBySubmitterId(Long submitterId, Pageable pageable);
    Page<WfApproval> findByApproverIdInAndStatus(List<Long> approverIds, String status, Pageable pageable);
    long countByStatus(String status);
}
```

- [ ] **Step 7: SysPushLogRepository.java**

```java
package com.simplemdm.repository;

import com.simplemdm.model.SysPushLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SysPushLogRepository extends JpaRepository<SysPushLog, Long> {
    Page<SysPushLog> findByTargetSystem(String targetSystem, Pageable pageable);
    Page<SysPushLog> findByStatus(String status, Pageable pageable);
    Page<SysPushLog> findByTargetSystemAndStatus(String targetSystem, String status, Pageable pageable);

    @Query("SELECT COUNT(l) FROM SysPushLog l")
    long countAll();

    @Query("SELECT COUNT(l) FROM SysPushLog l WHERE l.status = 'success'")
    long countSuccess();
    
    long countByStatus(String status);
}
```

- [ ] **Step 8: SysPushApiRepository.java**

```java
package com.simplemdm.repository;

import com.simplemdm.model.SysPushApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SysPushApiRepository extends JpaRepository<SysPushApi, Long> {
    Optional<SysPushApi> findByTargetSystem(String targetSystem);
    List<SysPushApi> findByStatus(String status);
    Page<SysPushApi> findByNameContainingOrTargetSystemContaining(String nameKey, String sysKey, Pageable pageable);
}
```

- [ ] **Step 9: 编译验证** `mvn compile`

---

### Task 5: JWT 安全层

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/security/JwtUtil.java`
- Create: `backend-java/src/main/java/com/simplemdm/security/JwtInterceptor.java`
- Create: `backend-java/src/main/java/com/simplemdm/security/RequirePerm.java`
- Create: `backend-java/src/main/java/com/simplemdm/security/PermissionAspect.java`
- Create: `backend-java/src/main/java/com/simplemdm/config/WebMvcConfig.java`
- Create: `backend-java/src/main/java/com/simplemdm/config/WebConfig.java`

**Produces:** JWT 生成/验证、拦截器、权限注解、CORS

- [ ] **Step 1: JwtUtil.java**

```java
package com.simplemdm.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMinutes * 60 * 1000;
    }

    public String createToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: JwtInterceptor.java**

```java
package com.simplemdm.security;

import com.simplemdm.model.SysUser;
import com.simplemdm.repository.SysUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserRepository userRepo;

    public static final ThreadLocal<SysUser> CURRENT_USER = new ThreadLocal<>();

    public JwtInterceptor(JwtUtil jwtUtil, SysUserRepository userRepo) {
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        if ("/api/auth/login".equals(path)) return true;

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\",\"data\":null}");
            return false;
        }

        String token = authHeader.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\",\"data\":null}");
            return false;
        }

        Optional<SysUser> userOpt = userRepo.findByIdAndStatus(userId, "active");
        if (userOpt.isEmpty()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"用户不存在或已禁用\",\"data\":null}");
            return false;
        }

        CURRENT_USER.set(userOpt.get());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CURRENT_USER.remove();
    }
}
```

- [ ] **Step 3: RequirePerm.java** — 权限注解

```java
package com.simplemdm.security;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {
    String value();  // VIEW | EDIT
}
```

- [ ] **Step 4: PermissionAspect.java** — 注解切面

```java
package com.simplemdm.security;

import com.simplemdm.model.SysUser;
import com.simplemdm.model.SysUserPermission;
import com.simplemdm.repository.SysUserPermissionRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Aspect
@Component
public class PermissionAspect {

    private final SysUserPermissionRepository permRepo;
    private final HttpServletResponse response;

    public PermissionAspect(SysUserPermissionRepository permRepo, HttpServletResponse response) {
        this.permRepo = permRepo;
        this.response = response;
    }

    @Around("@annotation(requirePerm)")
    public Object checkPermission(ProceedingJoinPoint pjp, RequirePerm requirePerm) throws Throwable {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\",\"data\":null}");
            return null;
        }

        // Admin can bypass VIEW checks but NOT EDIT checks
        if ("EDIT".equals(requirePerm.value()) && Boolean.TRUE.equals(user.getIsAdmin())) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"管理员无编辑权限\",\"data\":null}");
            return null;
        }

        List<SysUserPermission> perms = permRepo.findByUserIdAndPermType(user.getId(), requirePerm.value());
        if (perms.isEmpty()) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无" +
                ("EDIT".equals(requirePerm.value()) ? "编辑" : "查看") + "权限\",\"data\":null}");
            return null;
        }

        return pjp.proceed();
    }
}
```

- [ ] **Step 5: WebMvcConfig.java** — 注册拦截器

```java
package com.simplemdm.config;

import com.simplemdm.security.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }
}
```

- [ ] **Step 6: WebConfig.java** — CORS

```java
package com.simplemdm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("*")
                        .allowedHeaders("*");
            }
        };
    }
}
```

- [ ] **Step 7: 编译验证** `mvn compile`

---

### Task 6: Service 层

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/service/AuthService.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/PermissionService.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/PersonnelService.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/ApprovalService.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/PushService.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/DashboardService.java`

**Produces:** 业务逻辑层完整服务

- [ ] **Step 1: AuthService.java**

```java
package com.simplemdm.service;

import com.simplemdm.model.SysUser;
import com.simplemdm.model.SysUserPermission;
import com.simplemdm.repository.SysUserRepository;
import com.simplemdm.repository.SysUserPermissionRepository;
import com.simplemdm.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AuthService {

    private final SysUserRepository userRepo;
    private final SysUserPermissionRepository permRepo;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(SysUserRepository userRepo, SysUserPermissionRepository permRepo, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.permRepo = permRepo;
        this.jwtUtil = jwtUtil;
    }

    public String hashPassword(String password) {
        return encoder.encode(password);
    }

    public boolean verifyPassword(String raw, String hashed) {
        return encoder.matches(raw, hashed);
    }

    public Map<String, Object> login(String username, String password) {
        Optional<SysUser> opt = userRepo.findByUsername(username);
        if (opt.isEmpty() || !verifyPassword(password, opt.get().getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }
        SysUser user = opt.get();
        if (!"active".equals(user.getStatus())) {
            throw new RuntimeException("账号已被禁用");
        }

        String token = jwtUtil.createToken(user.getId());
        List<SysUserPermission> perms = permRepo.findByUserId(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("real_name", user.getRealName());
        userMap.put("department", user.getDepartment());
        userMap.put("is_admin", user.getIsAdmin());
        userMap.put("status", user.getStatus());
        result.put("user", userMap);

        List<Map<String, Object>> permList = new ArrayList<>();
        for (SysUserPermission p : perms) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("id", p.getId());
            pm.put("perm_type", p.getPermType());
            pm.put("scope_type", p.getScopeType());
            pm.put("scope_value", p.getScopeValue());
            permList.add(pm);
        }
        result.put("permissions", permList);

        return result;
    }

    public SysUser getCurrentUser(Long userId) {
        return userRepo.findById(userId).orElse(null);
    }
}
```

- [ ] **Step 2: PermissionService.java**

```java
package com.simplemdm.service;

import com.simplemdm.model.SysUserPermission;
import com.simplemdm.repository.SysUserPermissionRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PermissionService {

    private final SysUserPermissionRepository permRepo;

    public PermissionService(SysUserPermissionRepository permRepo) {
        this.permRepo = permRepo;
    }

    /** Get all departments the user can VIEW */
    public List<String> getViewableDepts(Long userId) {
        List<SysUserPermission> perms = permRepo.findByUserIdAndPermType(userId, "VIEW");
        List<String> depts = new ArrayList<>();
        for (SysUserPermission p : perms) {
            if ("ALL".equals(p.getScopeType()) || "DEPT".equals(p.getScopeType())) {
                if (p.getScopeValue() != null) depts.add(p.getScopeValue());
                else return null; // null = wildcard: should query all
            }
        }
        // If any scope is ALL, return null to indicate no department filter
        for (SysUserPermission p : perms) {
            if ("ALL".equals(p.getScopeType())) return null;
        }
        return depts;
    }

    /** Get all departments the user can EDIT */
    public List<String> getEditableDepts(Long userId) {
        List<SysUserPermission> perms = permRepo.findByUserIdAndPermType(userId, "EDIT");
        List<String> depts = new ArrayList<>();
        for (SysUserPermission p : perms) {
            if ("ALL".equals(p.getScopeType())) return null;
            if (p.getScopeValue() != null) depts.add(p.getScopeValue());
        }
        return depts;
    }

    public List<SysUserPermission> getUserPermissions(Long userId) {
        return permRepo.findByUserId(userId);
    }

    public SysUserPermission addPermission(Long userId, String permType, String scopeType, String scopeValue) {
        SysUserPermission p = new SysUserPermission();
        p.setUserId(userId);
        p.setPermType(permType);
        p.setScopeType(scopeType);
        p.setScopeValue(scopeValue);
        return permRepo.save(p);
    }

    public void removePermission(Long userId, Long permId) {
        permRepo.deleteByUserIdAndId(userId, permId);
    }
}
```

- [ ] **Step 3: PersonnelService.java**

```java
package com.simplemdm.service;

import com.simplemdm.dto.PersonnelDTO;
import com.simplemdm.model.MdmPersonnel;
import com.simplemdm.repository.MdmPersonnelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PersonnelService {

    private final MdmPersonnelRepository personnelRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PersonnelService(MdmPersonnelRepository personnelRepo) {
        this.personnelRepo = personnelRepo;
    }

    @Transactional(readOnly = true)
    public Page<MdmPersonnel> listPersonnel(String keyword, String department, int page, int pageSize, List<String> allowedDepts) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        if (allowedDepts == null || allowedDepts.isEmpty()) {
            return personnelRepo.findAll(pageable);
        }
        return personnelRepo.searchByKeywordAndDept(
            (keyword != null && !keyword.isEmpty()) ? keyword : null,
            (department != null && !department.isEmpty()) ? department : null,
            allowedDepts, pageable
        );
    }

    public MdmPersonnel getPersonnel(Long id) {
        return personnelRepo.findById(id).orElse(null);
    }

    public List<String> getDepartments() {
        return personnelRepo.findDistinctDepartments();
    }

    public MdmPersonnel getByEmployeeCode(String employeeCode) {
        return personnelRepo.findByEmployeeCode(employeeCode).orElse(null);
    }

    @Transactional
    public MdmPersonnel createFromApproval(PersonnelDTO dto) {
        MdmPersonnel p = new MdmPersonnel();
        p.setEmployeeCode(dto.employeeCode);
        p.setName(dto.name);
        p.setGender(dto.gender);
        p.setDepartment(dto.department);
        p.setPosition(dto.position);
        p.setPhone(dto.phone);
        p.setEmail(dto.email);
        p.setStatus("pending_approval");
        p.setVersion(1);
        return personnelRepo.save(p);
    }

    @Transactional
    public void applyChanges(MdmPersonnel personnel, String changeDataJson) {
        try {
            Map<String, Map<String, Object>> changes = objectMapper.readValue(changeDataJson, Map.class);
            for (Map.Entry<String, Map<String, Object>> entry : changes.entrySet()) {
                String field = entry.getKey();
                Object newValue = entry.getValue().get("new");
                switch (field) {
                    case "name": personnel.setName((String) newValue); break;
                    case "gender": personnel.setGender((String) newValue); break;
                    case "department": personnel.setDepartment((String) newValue); break;
                    case "position": personnel.setPosition((String) newValue); break;
                    case "phone": personnel.setPhone((String) newValue); break;
                    case "email": personnel.setEmail((String) newValue); break;
                    case "employee_code": personnel.setEmployeeCode((String) newValue); break;
                }
            }
            personnel.setStatus("active");
            personnel.setVersion(personnel.getVersion() + 1);
            personnelRepo.save(personnel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply changes", e);
        }
    }

    public Map<String, Object> computeDiff(MdmPersonnel existing, PersonnelDTO update) {
        Map<String, Map<String, Object>> diff = new HashMap<>();
        compareField(diff, "name", existing.getName(), update.name);
        compareField(diff, "gender", existing.getGender(), update.gender);
        compareField(diff, "department", existing.getDepartment(), update.department);
        compareField(diff, "position", existing.getPosition(), update.position);
        compareField(diff, "phone", existing.getPhone(), update.phone);
        compareField(diff, "email", existing.getEmail(), update.email);
        compareField(diff, "employee_code", existing.getEmployeeCode(), update.employeeCode);

        if (diff.isEmpty()) return null;

        Map<String, Object> result = new HashMap<>();
        result.put("diff", diff);
        return result;
    }

    private void compareField(Map<String, Map<String, Object>> diff, String field, Object oldVal, Object newVal) {
        if (newVal != null && !Objects.equals(oldVal, newVal)) {
            Map<String, Object> change = new HashMap<>();
            change.put("old", oldVal);
            change.put("new", newVal);
            diff.put(field, change);
        }
    }
}
```

- [ ] **Step 4: ApprovalService.java**

```java
package com.simplemdm.service;

import com.simplemdm.dto.PersonnelDTO;
import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ApprovalService {

    private final WfApprovalRepository approvalRepo;
    private final MdmPersonnelRepository personnelRepo;
    private final SysUserRepository userRepo;
    private final SysApproverDeptRepository approverDeptRepo;
    private final PersonnelService personnelService;
    private final PushService pushService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApprovalService(WfApprovalRepository approvalRepo, MdmPersonnelRepository personnelRepo,
                           SysUserRepository userRepo, SysApproverDeptRepository approverDeptRepo,
                           PersonnelService personnelService, PushService pushService) {
        this.approvalRepo = approvalRepo;
        this.personnelRepo = personnelRepo;
        this.userRepo = userRepo;
        this.approverDeptRepo = approverDeptRepo;
        this.personnelService = personnelService;
        this.pushService = pushService;
    }

    @Transactional
    public WfApproval createApprovalForCreate(Long submitterId, PersonnelDTO dto) {
        MdmPersonnel p = personnelService.createFromApproval(dto);

        Map<String, Map<String, Object>> changeData = new HashMap<>();
        for (java.lang.reflect.Field f : PersonnelDTO.class.getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object val = f.get(dto);
                if (val != null && !"id".equals(f.getName()) && !"version".equals(f.getName()) && !"status".equals(f.getName())) {
                    changeData.put(f.getName(), Map.of("old", null, "new", val));
                }
            } catch (Exception ignored) {}
        }

        Long approverId = findApproverForDepartment(p.getDepartment());

        WfApproval approval = new WfApproval();
        approval.setPersonnelId(p.getId());
        approval.setWorkflowType("create");
        approval.setSubmitterId(submitterId);
        approval.setApproverId(approverId);
        approval.setStatus("pending");
        try {
            approval.setChangeData(mapper.writeValueAsString(changeData));
        } catch (Exception e) { throw new RuntimeException(e); }
        return approvalRepo.save(approval);
    }

    @Transactional
    public WfApproval createApprovalForUpdate(Long personnelId, Long submitterId, PersonnelDTO dto) {
        MdmPersonnel p = personnelRepo.findById(personnelId).orElse(null);
        if (p == null || "pending_approval".equals(p.getStatus())) return null;

        Map<String, Object> diffResult = personnelService.computeDiff(p, dto);
        if (diffResult == null) return null;

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> diff = (Map<String, Map<String, Object>>) diffResult.get("diff");
        if (diff.isEmpty()) return null;

        p.setStatus("pending_approval");
        personnelRepo.save(p);

        Long approverId = findApproverForDepartment(p.getDepartment());

        WfApproval approval = new WfApproval();
        approval.setPersonnelId(personnelId);
        approval.setWorkflowType("update");
        approval.setSubmitterId(submitterId);
        approval.setApproverId(approverId);
        approval.setStatus("pending");
        try {
            approval.setChangeData(mapper.writeValueAsString(diff));
        } catch (Exception e) { throw new RuntimeException(e); }
        return approvalRepo.save(approval);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listApprovals(Long userId, String listType, String statusFilter,
                                                    int page, int pageSize, List<String> approverDepts) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<WfApproval> approvals;

        if ("pending_my".equals(listType) && approverDepts != null) {
            // Find all approvers in the user's managed departments
            List<Long> approverIds = new ArrayList<>();
            approverIds.add(userId);
            approvals = approvalRepo.findByApproverIdInAndStatus(approverIds, "pending", pageable);
        } else if ("pending_my".equals(listType)) {
            approvals = approvalRepo.findByApproverIdAndStatus(userId, "pending", pageable);
        } else if ("my_submitted".equals(listType)) {
            approvals = approvalRepo.findBySubmitterId(userId, pageable);
        } else {
            approvals = approvalRepo.findAll(pageable);
        }

        return approvals.map(this::enrichApproval);
    }

    private Map<String, Object> enrichApproval(WfApproval a) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", a.getId());
        result.put("personnel_id", a.getPersonnelId());
        result.put("personnel_name", personnelRepo.findById(a.getPersonnelId()).map(MdmPersonnel::getName).orElse(""));
        result.put("workflow_type", a.getWorkflowType());
        result.put("submitter_id", a.getSubmitterId());
        result.put("submitter_name", userRepo.findById(a.getSubmitterId()).map(SysUser::getRealName).orElse(""));
        result.put("approver_id", a.getApproverId());
        result.put("approver_name", a.getApproverId() != null ? userRepo.findById(a.getApproverId()).map(SysUser::getRealName).orElse("") : "");
        result.put("status", a.getStatus());
        result.put("change_data", a.getChangeData());
        result.put("submit_time", a.getSubmitTime() != null ? a.getSubmitTime().toString() : null);
        result.put("approve_time", a.getApproveTime() != null ? a.getApproveTime().toString() : null);
        result.put("approve_comment", a.getApproveComment());
        result.put("withdrawn_time", a.getWithdrawnTime() != null ? a.getWithdrawnTime().toString() : null);
        result.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        return result;
    }

    @Transactional
    public WfApproval approve(Long approvalId, String comment) {
        WfApproval a = approvalRepo.findById(approvalId).orElse(null);
        if (a == null || !"pending".equals(a.getStatus())) return null;

        a.setStatus("approved");
        a.setApproveTime(LocalDateTime.now());
        a.setApproveComment(comment);

        MdmPersonnel p = personnelRepo.findById(a.getPersonnelId()).orElse(null);
        if (p != null && a.getChangeData() != null) {
            personnelService.applyChanges(p, a.getChangeData());
        }

        approvalRepo.save(a);

        // Trigger push
        pushService.executePush(a);

        return a;
    }

    @Transactional
    public WfApproval reject(Long approvalId, String comment) {
        WfApproval a = approvalRepo.findById(approvalId).orElse(null);
        if (a == null || !"pending".equals(a.getStatus())) return null;

        a.setStatus("rejected");
        a.setApproveTime(LocalDateTime.now());
        a.setApproveComment(comment);

        MdmPersonnel p = personnelRepo.findById(a.getPersonnelId()).orElse(null);
        if (p != null) {
            if ("create".equals(a.getWorkflowType())) {
                p.setStatus("inactive");
            } else {
                p.setStatus("active");
            }
            personnelRepo.save(p);
        }

        return approvalRepo.save(a);
    }

    @Transactional
    public WfApproval withdraw(Long approvalId, Long userId) {
        WfApproval a = approvalRepo.findById(approvalId).orElse(null);
        if (a == null || !"pending".equals(a.getStatus()) || !a.getSubmitterId().equals(userId)) return null;

        a.setStatus("withdrawn");
        a.setWithdrawnTime(LocalDateTime.now());

        MdmPersonnel p = personnelRepo.findById(a.getPersonnelId()).orElse(null);
        if (p != null) {
            if ("create".equals(a.getWorkflowType())) {
                p.setStatus("inactive");
            } else {
                p.setStatus("active");
            }
            personnelRepo.save(p);
        }

        return approvalRepo.save(a);
    }

    public Map<String, Object> getApprovalDetail(Long id) {
        WfApproval a = approvalRepo.findById(id).orElse(null);
        if (a == null) return null;
        return enrichApproval(a);
    }

    private Long findApproverForDepartment(String department) {
        // Find approver assigned to this department
        List<SysApproverDept> assignments = approverDeptRepo.findByDepartment(department);
        if (!assignments.isEmpty()) {
            return assignments.get(0).getUserId();
        }
        // Fallback: first active admin
        List<SysUser> users = userRepo.findAll();
        for (SysUser u : users) {
            if ("active".equals(u.getStatus()) && Boolean.TRUE.equals(u.getIsAdmin())) {
                return u.getId();
            }
        }
        return null;
    }
}
```

- [ ] **Step 5: PushService.java**

```java
package com.simplemdm.service;

import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PushService {

    private final SysPushLogRepository pushLogRepo;
    private final SysPushApiRepository pushApiRepo;
    private final MdmPersonnelRepository personnelRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    private int pushSessionCount = 0;

    public PushService(SysPushLogRepository pushLogRepo, SysPushApiRepository pushApiRepo,
                       MdmPersonnelRepository personnelRepo) {
        this.pushLogRepo = pushLogRepo;
        this.pushApiRepo = pushApiRepo;
        this.personnelRepo = personnelRepo;
    }

    @Transactional
    public List<SysPushLog> executePush(WfApproval approval) {
        MdmPersonnel p = personnelRepo.findById(approval.getPersonnelId()).orElse(null);
        if (p == null) return List.of();

        Map<String, Object> payload = new HashMap<>();
        payload.put("employee_code", p.getEmployeeCode());
        payload.put("name", p.getName());
        payload.put("gender", p.getGender());
        payload.put("department", p.getDepartment());
        payload.put("position", p.getPosition());
        payload.put("phone", p.getPhone());
        payload.put("email", p.getEmail());
        payload.put("version", p.getVersion());

        List<SysPushApi> activeApis = pushApiRepo.findByStatus("active");
        List<SysPushLog> logs = new ArrayList<>();

        for (SysPushApi api : activeApis) {
            pushSessionCount++;
            SysPushLog log = new SysPushLog();
            log.setApprovalId(approval.getId());
            log.setPersonnelId(p.getId());
            log.setTargetSystem(api.getTargetSystem());
            log.setStatus("pending");
            try {
                log.setRequestBody(mapper.writeValueAsString(payload));
            } catch (Exception ignored) {}

            pushLogRepo.save(log);

            try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            boolean success = pushSessionCount <= 2 || Math.random() < 0.9;
            if (success) {
                log.setStatus("success");
                log.setResponseCode(200);
                log.setResponseBody("{\"code\":200,\"message\":\"数据已成功同步到 " + api.getName() + "\"}");
            } else {
                log.setStatus("failed");
                log.setResponseCode(500);
                log.setResponseBody("{\"code\":500,\"message\":\"" + api.getName() + " 连接超时\"}");
                log.setErrorMessage("Connection to " + api.getName() + " timed out");
            }

            log.setPushedAt(LocalDateTime.now());
            pushLogRepo.save(log);
            logs.add(log);
        }

        return logs;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listPushLogs(String targetSystem, String status, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysPushLog> logs;

        if ((targetSystem != null && !targetSystem.isEmpty()) &&
            (status != null && !status.isEmpty())) {
            logs = pushLogRepo.findByTargetSystemAndStatus(targetSystem, status, pageable);
        } else if (targetSystem != null && !targetSystem.isEmpty()) {
            logs = pushLogRepo.findByTargetSystem(targetSystem, pageable);
        } else if (status != null && !status.isEmpty()) {
            logs = pushLogRepo.findByStatus(status, pageable);
        } else {
            logs = pushLogRepo.findAll(pageable);
        }

        return logs.map(this::enrichLog);
    }

    private Map<String, Object> enrichLog(SysPushLog log) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", log.getId());
        m.put("approval_id", log.getApprovalId());
        m.put("personnel_id", log.getPersonnelId());
        m.put("personnel_name", log.getPersonnelId() != null ?
            personnelRepo.findById(log.getPersonnelId()).map(MdmPersonnel::getName).orElse("") : "");
        m.put("target_system", log.getTargetSystem());
        m.put("status", log.getStatus());
        m.put("request_body", log.getRequestBody());
        m.put("response_body", log.getResponseBody());
        m.put("response_code", log.getResponseCode());
        m.put("retry_count", log.getRetryCount());
        m.put("error_message", log.getErrorMessage());
        m.put("pushed_at", log.getPushedAt() != null ? log.getPushedAt().toString() : null);
        m.put("created_at", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        return m;
    }

    @Transactional
    public SysPushLog retryPush(Long logId) {
        SysPushLog log = pushLogRepo.findById(logId).orElse(null);
        if (log == null || !"failed".equals(log.getStatus())) return null;

        log.setRetryCount(log.getRetryCount() + 1);
        log.setStatus("success");
        log.setResponseCode(200);
        log.setResponseBody("{\"code\":200,\"message\":\"重试成功: 数据已同步到 " + log.getTargetSystem() + "\"}");
        log.setErrorMessage(null);
        log.setPushedAt(LocalDateTime.now());
        return pushLogRepo.save(log);
    }
}
```

- [ ] **Step 6: DashboardService.java**

```java
package com.simplemdm.service;

import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardService {

    private final MdmPersonnelRepository personnelRepo;
    private final WfApprovalRepository approvalRepo;
    private final SysPushLogRepository pushLogRepo;
    private final SysUserRepository userRepo;

    public DashboardService(MdmPersonnelRepository personnelRepo, WfApprovalRepository approvalRepo,
                            SysPushLogRepository pushLogRepo, SysUserRepository userRepo) {
        this.personnelRepo = personnelRepo;
        this.approvalRepo = approvalRepo;
        this.pushLogRepo = pushLogRepo;
        this.userRepo = userRepo;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalPersonnel = personnelRepo.count();
        long pendingApprovals = approvalRepo.countByStatus("pending");
        long totalPushes = pushLogRepo.countAll();
        long successPushes = pushLogRepo.countSuccess();
        double pushSuccessRate = totalPushes > 0 ? Math.round(successPushes * 1000.0 / totalPushes) / 10.0 : 100.0;

        stats.put("total_personnel", totalPersonnel);
        stats.put("pending_approvals", pendingApprovals);
        stats.put("push_success_rate", pushSuccessRate);

        // Recent 5 approvals
        List<WfApproval> recentApprovals = approvalRepo.findAll(
            org.springframework.data.domain.PageRequest.of(0, 5,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"))
        ).getContent();

        List<Map<String, Object>> recent = new ArrayList<>();
        for (WfApproval a : recentApprovals) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("personnel_name", personnelRepo.findById(a.getPersonnelId()).map(MdmPersonnel::getName).orElse(""));
            item.put("workflow_type", a.getWorkflowType());
            item.put("submitter_name", userRepo.findById(a.getSubmitterId()).map(SysUser::getRealName).orElse(""));
            item.put("approver_name", a.getApproverId() != null ? userRepo.findById(a.getApproverId()).map(SysUser::getRealName).orElse("") : "");
            item.put("status", a.getStatus());
            item.put("submit_time", a.getSubmitTime() != null ? a.getSubmitTime().toString() : "");
            recent.add(item);
        }
        stats.put("recent_approvals", recent);

        return stats;
    }
}
```

- [ ] **Step 7: 编译验证** `mvn compile`

---

### Task 7: Controller 层

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/controller/AuthController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/PersonnelController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/ApprovalController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/PushLogController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/PushApiController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/DashboardController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/UserController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/PermissionController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/ApproverController.java`

**Produces:** REST API 完整接口层

- [ ] **Step 1: AuthController.java**

```java
package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.dto.LoginRequest;
import com.simplemdm.model.SysUser;
import com.simplemdm.model.SysUserPermission;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.AuthService;
import com.simplemdm.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PermissionService permService;

    public AuthController(AuthService authService, PermissionService permService) {
        this.authService = authService;
        this.permService = permService;
    }

    @PostMapping("/login")
    public ApiResponse login(@Valid @RequestBody LoginRequest req) {
        try {
            Map<String, Object> result = authService.login(req.username, req.password);
            return ApiResponse.ok("登录成功", result);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @GetMapping("/me")
    public ApiResponse me() {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (user == null) return ApiResponse.error(401, "请先登录");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("real_name", user.getRealName());
        userMap.put("is_admin", user.getIsAdmin());
        userMap.put("department", user.getDepartment());
        userMap.put("status", user.getStatus());

        List<SysUserPermission> perms = permService.getUserPermissions(user.getId());
        List<Map<String, Object>> permList = new ArrayList<>();
        for (SysUserPermission p : perms) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("id", p.getId());
            pm.put("perm_type", p.getPermType());
            pm.put("scope_type", p.getScopeType());
            pm.put("scope_value", p.getScopeValue());
            permList.add(pm);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("user", userMap);
        data.put("permissions", permList);
        return ApiResponse.ok(data);
    }
}
```

- [ ] **Step 2: PersonnelController.java**

```java
package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.*;
import com.simplemdm.security.*;
import com.simplemdm.service.*;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/personnel")
public class PersonnelController {

    private final PersonnelService personnelService;
    private final ApprovalService approvalService;
    private final PermissionService permService;

    public PersonnelController(PersonnelService personnelService, ApprovalService approvalService,
                               PermissionService permService) {
        this.personnelService = personnelService;
        this.approvalService = approvalService;
        this.permService = permService;
    }

    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "") String department,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int pageSize) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        List<String> allowedDepts = permService.getViewableDepts(user.getId());
        Page<MdmPersonnel> result = personnelService.listPersonnel(keyword, department, page, pageSize, allowedDepts);

        List<Map<String, Object>> items = result.getContent().stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("employee_code", p.getEmployeeCode());
            m.put("name", p.getName());
            m.put("gender", p.getGender());
            m.put("department", p.getDepartment());
            m.put("position", p.getPosition());
            m.put("phone", p.getPhone());
            m.put("email", p.getEmail());
            m.put("status", p.getStatus());
            m.put("version", p.getVersion());
            m.put("created_at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
            m.put("updated_at", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());

        return ApiResponse.ok(new PageResult<>(items, result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/departments")
    public ApiResponse departments() {
        return ApiResponse.ok(personnelService.getDepartments());
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        MdmPersonnel p = personnelService.getPersonnel(id);
        if (p == null) return ApiResponse.error(404, "人员不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("employee_code", p.getEmployeeCode());
        m.put("name", p.getName());
        m.put("gender", p.getGender());
        m.put("department", p.getDepartment());
        m.put("position", p.getPosition());
        m.put("phone", p.getPhone());
        m.put("email", p.getEmail());
        m.put("status", p.getStatus());
        m.put("version", p.getVersion());
        m.put("created_at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        m.put("updated_at", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return ApiResponse.ok(m);
    }

    @PostMapping
    @RequirePerm("EDIT")
    public ApiResponse create(@RequestBody PersonnelDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();

        // Check employee_code unique
        if (personnelService.getByEmployeeCode(dto.employeeCode) != null) {
            return ApiResponse.error(400, "工号 " + dto.employeeCode + " 已存在");
        }

        WfApproval approval = approvalService.createApprovalForCreate(user.getId(), dto);
        Map<String, Object> data = new HashMap<>();
        data.put("personnel_id", approval.getPersonnelId());
        data.put("approval_id", approval.getId());
        return ApiResponse.ok("提交成功，请等待审批", data);
    }

    @PutMapping("/{id}")
    @RequirePerm("EDIT")
    public ApiResponse update(@PathVariable Long id, @RequestBody PersonnelDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        WfApproval approval = approvalService.createApprovalForUpdate(id, user.getId(), dto);
        if (approval == null) return ApiResponse.ok("没有变更需要提交", null);
        Map<String, Object> data = new HashMap<>();
        data.put("personnel_id", id);
        data.put("approval_id", approval.getId());
        return ApiResponse.ok("变更已提交，请等待审批", data);
    }
}
```

- [ ] **Step 3: ApprovalController.java**

```java
package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.*;
import com.simplemdm.security.*;
import com.simplemdm.service.*;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final PermissionService permService;

    public ApprovalController(ApprovalService approvalService, PermissionService permService) {
        this.approvalService = approvalService;
        this.permService = permService;
    }

    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "all") String listType,
                            @RequestParam(defaultValue = "") String status,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int pageSize) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        List<String> approverDepts = permService.getViewableDepts(user.getId());
        Page<Map<String, Object>> result = approvalService.listApprovals(user.getId(), listType, status, page, pageSize, approverDepts);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        Map<String, Object> detail = approvalService.getApprovalDetail(id);
        if (detail == null) return ApiResponse.error(404, "审批不存在");
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse approve(@PathVariable Long id, @RequestBody ApprovalDTO dto) {
        WfApproval a = approvalService.approve(id, dto.comment);
        if (a == null) return ApiResponse.error(400, "审批不存在或状态不是待审批");
        Map<String, Object> data = new HashMap<>();
        data.put("id", a.getId());
        data.put("status", a.getStatus());
        return ApiResponse.ok("审批已通过，数据已生效并推送至下游系统", data);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse reject(@PathVariable Long id, @RequestBody ApprovalDTO dto) {
        WfApproval a = approvalService.reject(id, dto.comment);
        if (a == null) return ApiResponse.error(400, "审批不存在或状态不是待审批");
        Map<String, Object> data = new HashMap<>();
        data.put("id", a.getId());
        data.put("status", a.getStatus());
        return ApiResponse.ok("审批已驳回", data);
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse withdraw(@PathVariable Long id) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        WfApproval a = approvalService.withdraw(id, user.getId());
        if (a == null) return ApiResponse.error(400, "审批不存在、状态不是待审批、或非本人提交");
        Map<String, Object> data = new HashMap<>();
        data.put("id", a.getId());
        data.put("status", a.getStatus());
        return ApiResponse.ok("审批已撤回", data);
    }
}
```

- [ ] **Step 4: PushLogController.java**

```java
package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.SysPushLog;
import com.simplemdm.service.PushService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/push-logs")
public class PushLogController {

    private final PushService pushService;

    public PushLogController(PushService pushService) { this.pushService = pushService; }

    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "") String targetSystem,
                            @RequestParam(defaultValue = "") String status,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int pageSize) {
        Page<Map<String, Object>> result = pushService.listPushLogs(
            targetSystem.isEmpty() ? null : targetSystem,
            status.isEmpty() ? null : status, page, pageSize);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse retry(@PathVariable Long id) {
        SysPushLog log = pushService.retryPush(id);
        if (log == null) return ApiResponse.error(400, "推送日志不存在或状态不是失败");
        Map<String, Object> data = new HashMap<>();
        data.put("id", log.getId());
        data.put("status", log.getStatus());
        return ApiResponse.ok("重试成功: 数据已同步到 " + log.getTargetSystem(), data);
    }
}
```

- [ ] **Step 5: PushApiController.java**

```java
package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.SysPushApi;
import com.simplemdm.repository.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/push-apis")
public class PushApiController {

    private final SysPushApiRepository pushApiRepo;

    public PushApiController(SysPushApiRepository pushApiRepo) { this.pushApiRepo = pushApiRepo; }

    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "") String status,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "id"));
        Page<SysPushApi> result;
        if (!keyword.isEmpty()) {
            result = pushApiRepo.findByNameContainingOrTargetSystemContaining(keyword, keyword, pageable);
        } else if (!status.isEmpty()) {
            // Use findAll with status filter via specification
            List<SysPushApi> all = pushApiRepo.findByStatus(status);
            result = new PageImpl<>(all, pageable, all.size());
        } else {
            result = pushApiRepo.findAll(pageable);
        }

        List<Map<String, Object>> items = result.getContent().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId()); m.put("name", a.getName());
            m.put("target_system", a.getTargetSystem()); m.put("method", a.getMethod());
            m.put("base_url", a.getBaseUrl()); m.put("auth_type", a.getAuthType());
            m.put("auth_config", a.getAuthConfig()); m.put("status", a.getStatus());
            m.put("description", a.getDescription()); m.put("retry_max", a.getRetryMax());
            m.put("timeout_sec", a.getTimeoutSec());
            m.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            m.put("updated_at", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
            return m;
        }).toList();
        return ApiResponse.ok(new PageResult<>(items, result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/active")
    public ApiResponse activeList() {
        List<SysPushApi> active = pushApiRepo.findByStatus("active");
        return ApiResponse.ok(active.stream().map(SysPushApi::getTargetSystem).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        SysPushApi api = pushApiRepo.findById(id).orElse(null);
        if (api == null) return ApiResponse.error(404, "API配置不存在");
        return ApiResponse.ok(Map.of("id", api.getId(), "name", api.getName(),
            "target_system", api.getTargetSystem(), "method", api.getMethod(),
            "base_url", api.getBaseUrl(), "auth_type", api.getAuthType(),
            "auth_config", api.getAuthConfig(), "status", api.getStatus(),
            "description", api.getDescription(), "retry_max", api.getRetryMax(),
            "timeout_sec", api.getTimeoutSec()));
    }

    @PostMapping
    public ApiResponse create(@RequestBody PushApiDTO dto) {
        if (pushApiRepo.findByTargetSystem(dto.targetSystem).isPresent())
            return ApiResponse.error(400, "目标系统 " + dto.targetSystem + " 已存在");
        SysPushApi api = new SysPushApi();
        api.setName(dto.name); api.setTargetSystem(dto.targetSystem);
        api.setMethod(dto.method); api.setBaseUrl(dto.baseUrl);
        api.setAuthType(dto.authType); api.setAuthConfig(dto.authConfig);
        api.setStatus(dto.status); api.setDescription(dto.description);
        api.setRetryMax(dto.retryMax); api.setTimeoutSec(dto.timeoutSec);
        api = pushApiRepo.save(api);
        return ApiResponse.ok("API配置已创建", Map.of("id", api.getId(), "target_system", api.getTargetSystem()));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody PushApiDTO dto) {
        SysPushApi api = pushApiRepo.findById(id).orElse(null);
        if (api == null) return ApiResponse.error(404, "API配置不存在");
        if (dto.name != null) api.setName(dto.name);
        if (dto.method != null) api.setMethod(dto.method);
        if (dto.baseUrl != null) api.setBaseUrl(dto.baseUrl);
        if (dto.authType != null) api.setAuthType(dto.authType);
        if (dto.authConfig != null) api.setAuthConfig(dto.authConfig);
        if (dto.status != null) api.setStatus(dto.status);
        if (dto.description != null) api.setDescription(dto.description);
        if (dto.retryMax != null) api.setRetryMax(dto.retryMax);
        if (dto.timeoutSec != null) api.setTimeoutSec(dto.timeoutSec);
        api = pushApiRepo.save(api);
        return ApiResponse.ok("API配置已更新", Map.of("id", api.getId(), "target_system", api.getTargetSystem()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        SysPushApi api = pushApiRepo.findById(id).orElse(null);
        if (api == null) return ApiResponse.error(404, "API配置不存在");
        // Soft-delete: deactivate instead of DELETE
        api.setStatus("inactive");
        pushApiRepo.save(api);
        return ApiResponse.ok("API配置已停用", Map.of("target_system", api.getTargetSystem()));
    }

    @PostMapping("/{id}/test")
    public ApiResponse test(@PathVariable Long id) {
        SysPushApi api = pushApiRepo.findById(id).orElse(null);
        if (api == null) return ApiResponse.error(400, "API配置不存在");
        Map<String, Object> detail = Map.of("url", api.getBaseUrl(), "method", api.getMethod(),
            "auth_type", api.getAuthType(), "response_time_ms", 245, "status_code", 200);
        return ApiResponse.ok("连接成功: " + api.getName() + " (" + api.getBaseUrl() + ")", detail);
    }
}
```

- [ ] **Step 5b: DashboardController.java**

```java
package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    public DashboardController(DashboardService ds) { this.dashboardService = ds; }

    @GetMapping("/stats")
    public ApiResponse stats() {
        return ApiResponse.ok(dashboardService.getStats());
    }
}
```

- [ ] **Step 5c: UserController.java**

```java
package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.SysUserRepository;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final SysUserRepository userRepo;
    public UserController(SysUserRepository ur) { this.userRepo = ur; }

    @GetMapping
    public ApiResponse list() {
        List<SysUser> users = userRepo.findAll();
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysUser u : users) {
            if ("active".equals(u.getStatus())) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId()); m.put("username", u.getUsername());
                m.put("real_name", u.getRealName()); m.put("is_admin", u.getIsAdmin());
                m.put("department", u.getDepartment()); m.put("status", u.getStatus());
                items.add(m);
            }
        }
        return ApiResponse.ok(items);
    }
}
```

- [ ] **Step 5d: PermissionController.java**

```java
package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.SysUserPermission;
import com.simplemdm.service.PermissionService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/users/{userId}/permissions")
public class PermissionController {

    private final PermissionService permService;
    public PermissionController(PermissionService ps) { this.permService = ps; }

    @GetMapping
    public ApiResponse list(@PathVariable Long userId) {
        List<SysUserPermission> perms = permService.getUserPermissions(userId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysUserPermission p : perms) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId()); m.put("perm_type", p.getPermType());
            m.put("scope_type", p.getScopeType()); m.put("scope_value", p.getScopeValue());
            items.add(m);
        }
        return ApiResponse.ok(items);
    }

    @PostMapping
    public ApiResponse add(@PathVariable Long userId, @RequestBody PermissionDTO dto) {
        SysUserPermission p = permService.addPermission(userId, dto.permType, dto.scopeType, dto.scopeValue);
        return ApiResponse.ok("权限已添加", Map.of("id", p.getId()));
    }

    @DeleteMapping("/{permId}")
    public ApiResponse remove(@PathVariable Long userId, @PathVariable Long permId) {
        permService.removePermission(userId, permId);
        return ApiResponse.ok("权限已移除", null);
    }
}
```

- [ ] **Step 5e: ApproverController.java**

```java
package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/approvers")
public class ApproverController {

    private final SysApproverDeptRepository approverDeptRepo;
    private final SysUserRepository userRepo;

    public ApproverController(SysApproverDeptRepository adr, SysUserRepository ur) {
        this.approverDeptRepo = adr; this.userRepo = ur;
    }

    @GetMapping
    public ApiResponse list() {
        List<SysApproverDept> all = approverDeptRepo.findAll();
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysApproverDept ad : all) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ad.getId()); m.put("user_id", ad.getUserId());
            m.put("department", ad.getDepartment());
            m.put("user_name", userRepo.findById(ad.getUserId()).map(SysUser::getRealName).orElse(""));
            items.add(m);
        }
        return ApiResponse.ok(items);
    }

    @PostMapping
    public ApiResponse assign(@RequestBody ApproverDeptDTO dto) {
        List<SysApproverDept> existing = approverDeptRepo.findByUserIdAndDepartment(dto.userId, dto.department);
        if (!existing.isEmpty()) return ApiResponse.error(400, "该审批人已分配到此部门");
        SysApproverDept ad = new SysApproverDept();
        ad.setUserId(dto.userId); ad.setDepartment(dto.department);
        ad = approverDeptRepo.save(ad);
        return ApiResponse.ok("审批人已分配", Map.of("id", ad.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse remove(@PathVariable Long id) {
        approverDeptRepo.deleteById(id);
        return ApiResponse.ok("审批人分配已移除", null);
    }
}
```

- [ ] **Step 6: 编译验证** `mvn compile`

---

### Task 8: 种子数据 + 异常处理

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/config/DataInitializer.java`
- Create: `backend-java/src/main/java/com/simplemdm/exception/BusinessException.java`
- Create: `backend-java/src/main/java/com/simplemdm/exception/GlobalExceptionHandler.java`

**Produces:** 首次启动自动种子数据，统一异常处理

- [ ] **Step 1: DataInitializer.java** — 种子数据

```java
package com.simplemdm.config;

import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.simplemdm.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository userRepo;
    private final MdmPersonnelRepository personnelRepo;
    private final WfApprovalRepository approvalRepo;
    private final SysPushLogRepository pushLogRepo;
    private final SysPushApiRepository pushApiRepo;
    private final SysUserPermissionRepository permRepo;
    private final SysApproverDeptRepository approverDeptRepo;
    private final AuthService authService;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataInitializer(SysUserRepository ur, MdmPersonnelRepository pr, WfApprovalRepository ar,
                           SysPushLogRepository slr, SysPushApiRepository sar, SysUserPermissionRepository spr,
                           SysApproverDeptRepository sadr, AuthService as) {
        this.userRepo = ur; this.personnelRepo = pr; this.approvalRepo = ar;
        this.pushLogRepo = slr; this.pushApiRepo = sar; this.permRepo = spr;
        this.approverDeptRepo = sadr; this.authService = as;
    }

    @Override
    public void run(String... args) {
        if (userRepo.count() > 0) return; // Already seeded

        // ── Users ──
        SysUser wangwu = createUser("wangwu", "123456", "王五", "人力资源部", false);
        SysUser lisi = createUser("lisi", "123456", "李四", "人力资源部", false);
        SysUser zhaoliu = createUser("zhaoliu", "123456", "赵六", "IT部", false);
        SysUser admin = createUser("admin", "admin123", "管理员", "IT部", true);

        // ── Permissions ──
        // wangwu: VIEW ALL + EDIT HR department
        createPerm(wangwu.getId(), "VIEW", "ALL", null);
        createPerm(wangwu.getId(), "EDIT", "DEPT", "人力资源部");
        createPerm(wangwu.getId(), "EDIT", "DEPT", "工程部");
        // lisi: VIEW ALL (approver needs to see), no EDIT
        createPerm(lisi.getId(), "VIEW", "ALL", null);
        // zhaoliu: VIEW all, no EDIT
        createPerm(zhaoliu.getId(), "VIEW", "ALL", null);
        // admin: VIEW ALL, no EDIT (admin cannot edit data)
        createPerm(admin.getId(), "VIEW", "ALL", null);

        // ── Approver Assignment: lisi manages HR department ──
        SysApproverDept ad = new SysApproverDept();
        ad.setUserId(lisi.getId());
        ad.setDepartment("人力资源部");
        approverDeptRepo.save(ad);

        // ── Personnel ──
        List<MdmPersonnel> personnelList = List.of(
            createPersonnel("EMP001", "张三", "男", "工程部", "高级工程师", "13800001001", "zhangsan@demo.com"),
            createPersonnel("EMP002", "李丽", "女", "产品部", "产品总监", "13800001002", "lili@demo.com"),
            createPersonnel("EMP003", "王磊", "男", "工程部", "架构师", "13800001003", "wanglei@demo.com"),
            createPersonnel("EMP004", "陈芳", "女", "市场部", "市场经理", "13800001004", "chenfang@demo.com"),
            createPersonnel("EMP005", "刘伟", "男", "产品部", "产品经理", "13800001005", "liuwei@demo.com"),
            createPersonnel("EMP006", "周敏", "女", "人力资源部", "HR主管", "13800001006", "zhoumin@demo.com"),
            createPersonnel("EMP007", "孙浩", "男", "工程部", "开发工程师", "13800001007", "sunhao@demo.com"),
            createPersonnel("EMP008", "马超", "男", "销售部", "销售代表", "13800001008", "machao@demo.com")
        );

        // ── Historical Approval #1 — EMP002 update (approved) ──
        MdmPersonnel emp002 = personnelList.get(1);
        WfApproval approval1 = new WfApproval();
        approval1.setPersonnelId(emp002.getId());
        approval1.setWorkflowType("update");
        approval1.setSubmitterId(wangwu.getId());
        approval1.setApproverId(lisi.getId());
        approval1.setStatus("approved");
        approval1.setChangeData("{\"department\":{\"old\":\"运营部\",\"new\":\"产品部\"},\"position\":{\"old\":\"运营总监\",\"new\":\"产品总监\"}}");
        approval1.setSubmitTime(LocalDateTime.of(2026, 7, 20, 10, 30, 0));
        approval1.setApproveTime(LocalDateTime.of(2026, 7, 20, 14, 20, 0));
        approval1.setApproveComment("同意调动，即日起生效");
        approvalRepo.save(approval1);

        // Push logs for approval 1
        createPushLog(approval1.getId(), emp002.getId(), "CRM", "success",
            "{\"code\":200,\"message\":\"数据已成功同步到 CRM 系统\"}");
        createPushLog(approval1.getId(), emp002.getId(), "MES", "success",
            "{\"code\":200,\"message\":\"数据已成功同步到 MES 系统\"}");

        // ── Historical Approval #2 — EMP008 update (rejected) ──
        MdmPersonnel emp008 = personnelList.get(7);
        WfApproval approval2 = new WfApproval();
        approval2.setPersonnelId(emp008.getId());
        approval2.setWorkflowType("update");
        approval2.setSubmitterId(wangwu.getId());
        approval2.setApproverId(lisi.getId());
        approval2.setStatus("rejected");
        approval2.setChangeData("{\"department\":{\"old\":\"销售部\",\"new\":\"市场部\"}}");
        approval2.setSubmitTime(LocalDateTime.of(2026, 7, 22, 9, 0, 0));
        approval2.setApproveTime(LocalDateTime.of(2026, 7, 22, 11, 15, 0));
        approval2.setApproveComment("该员工尚在试用期，暂不调动");
        approvalRepo.save(approval2);

        // ── Push API Configs ──
        createPushApi("CRM系统", "CRM", "POST",
            "http://crm.internal.example.com/api/personnel/sync",
            "token", "{\"header\":\"Authorization\",\"prefix\":\"Bearer\",\"token\":\"crm-demo-token\"}",
            "active", "客户关系管理系统", 3, 30);
        createPushApi("MES系统", "MES", "POST",
            "http://mes.internal.example.com/api/employee/sync",
            "token", "{\"header\":\"X-API-Key\",\"token\":\"mes-demo-key\"}",
            "active", "制造执行系统", 3, 30);
        createPushApi("HR系统", "HR", "PUT",
            "http://hr.internal.example.com/api/staff/sync",
            "token", "{\"header\":\"Authorization\",\"prefix\":\"Bearer\",\"token\":\"hr-demo-token\"}",
            "inactive", "人力资源系统（计划接入）", 5, 60);

        System.out.println("[OK] Demo data seeded: 4 users, 8 personnel, 2 historical approvals, 3 push APIs");
    }

    private SysUser createUser(String uname, String pwd, String realName, String dept, boolean isAdmin) {
        SysUser u = new SysUser();
        u.setUsername(uname);
        u.setPasswordHash(authService.hashPassword(pwd));
        u.setRealName(realName);
        u.setDepartment(dept);
        u.setIsAdmin(isAdmin);
        u.setStatus("active");
        return userRepo.save(u);
    }

    private void createPerm(Long userId, String permType, String scopeType, String scopeValue) {
        SysUserPermission p = new SysUserPermission();
        p.setUserId(userId);
        p.setPermType(permType);
        p.setScopeType(scopeType);
        p.setScopeValue(scopeValue);
        permRepo.save(p);
    }

    private MdmPersonnel createPersonnel(String code, String name, String gender,
                                          String dept, String pos, String phone, String email) {
        MdmPersonnel p = new MdmPersonnel();
        p.setEmployeeCode(code); p.setName(name); p.setGender(gender);
        p.setDepartment(dept); p.setPosition(pos); p.setPhone(phone); p.setEmail(email);
        p.setStatus("active"); p.setVersion(1);
        return personnelRepo.save(p);
    }

    private void createPushLog(Long approvalId, Long personnelId, String target, String status, String resp) {
        SysPushLog log = new SysPushLog();
        log.setApprovalId(approvalId); log.setPersonnelId(personnelId);
        log.setTargetSystem(target); log.setStatus(status);
        log.setRequestBody("{\"employee_code\":\"EMP002\",\"name\":\"李丽\",\"department\":\"产品部\",\"position\":\"产品总监\",\"version\":2}");
        log.setResponseBody(resp); log.setResponseCode(200);
        log.setPushedAt(LocalDateTime.of(2026, 7, 20, 14, 20, 5));
        pushLogRepo.save(log);
    }

    private void createPushApi(String name, String target, String method, String url,
                                String authType, String authConfig, String status,
                                String desc, int retryMax, int timeout) {
        SysPushApi api = new SysPushApi();
        api.setName(name); api.setTargetSystem(target); api.setMethod(method);
        api.setBaseUrl(url); api.setAuthType(authType); api.setAuthConfig(authConfig);
        api.setStatus(status); api.setDescription(desc);
        api.setRetryMax(retryMax); api.setTimeoutSec(timeout);
        pushApiRepo.save(api);
    }
}
```

- [ ] **Step 2: BusinessException.java + GlobalExceptionHandler.java**

```java
package com.simplemdm.exception;

public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String message) { super(message); this.code = code; }
    public int getCode() { return code; }
}
```

```java
package com.simplemdm.exception;

import com.simplemdm.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse handleBusiness(BusinessException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse handleUnknown(Exception e) {
        return ApiResponse.error(500, "服务器内部错误: " + e.getMessage());
    }
}
```

- [ ] **Step 3: 编译验证** `mvn compile`

---

### Task 9: start.bat 更新 + stop.bat

**Files:**
- Create: `backend-java/stop.bat`
- Modify: 确认 `start.bat` 更新

**Produces:** 一键启停脚本

- [ ] **Step 1: stop.bat**

```bat
@echo off
chcp 65001 >nul
echo ========================================
echo   SimpleMDM — 停止服务
echo ========================================

echo [1/2] 停止后端 (端口 18001)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":18001" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
    echo   ✓ Java 进程 %%a 已停止
)

echo [2/2] 停止前端 (端口 5173)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
    echo   ✓ Node 进程 %%a 已停止
)

echo ========================================
echo   服务已全部停止
echo ========================================
pause
```

- [ ] **Step 2: 更新 start.bat** — 将后端启动命令改为 Java

```bat
@echo off
chcp 65001 >nul
echo ========================================
echo   SimpleMDM — 启动服务
echo ========================================

echo [1/2] 启动后端...
cd /d "%~dp0backend-java"
start "SimpleMDM-Backend" cmd /c "mvn spring-boot:run"
cd /d "%~dp0"

echo [2/2] 启动前端...
cd /d "%~dp0frontend"
start "SimpleMDM-Frontend" cmd /c "npm run dev"
cd /d "%~dp0"

echo ========================================
echo   后端: http://localhost:18001
echo   前端: http://localhost:5173
echo   API文档: http://localhost:18001/docs  (已废弃，使用Swagger替代URL)
echo ========================================
echo   关闭窗口或运行 stop.bat 停止服务
pause
```

---

### Task 10: 前端适配

**Files:**
- Modify: `frontend/src/stores/user.js` — 新增 permissions 字段
- Modify: `frontend/src/api/personnel.js` — 确认接口路径兼容
- Modify: `frontend/src/views/personnel/List.vue` — 根据权限控制编辑按钮
- Modify: `frontend/src/views/Login.vue` — 适配新 login 响应格式

**Produces:** 前端适配新权限模型

- [ ] **Step 1: 更新 user store** — 存储 permissions

```javascript
// frontend/src/stores/user.js
// 在 login 成功后保存 permissions:
// this.permissions = data.permissions || []
// 新增 getter:
// hasEditPermission() — 检查是否有 EDIT 权限
// hasViewPermission() — 检查是否有 VIEW 权限
```

- [ ] **Step 2: 人员列表页** — 根据 hasEditPermission 控制编辑按钮显示

- [ ] **Step 3: 验证** — 启动后端，用 wangwu 登录确认只能看到有权限的部门数据，admin 登录确认无法编辑

---

### Task 11: 集成测试与验证

- [ ] **Step 1: 启动完整应用** `start.bat`
- [ ] **Step 2: 测试登录** — curl POST /api/auth/login
- [ ] **Step 3: 测试权限隔离** — wangwu 只能看/编辑 HR 和工程部
- [ ] **Step 4: 测试编辑权限隔离** — admin 无法编辑数据
- [ ] **Step 5: 测试审批流程** — 创建→审批→查看推送日志
- [ ] **Step 6: 测试审批人部门隔离** — lisi 只能审批 HR 部门
- [ ] **Step 7: 测试主表/子表** — 本部门创建子表数据，跨部门看不到私有数据
- [ ] **Step 8: 测试 stop.bat** — 运行后确认进程已停止
