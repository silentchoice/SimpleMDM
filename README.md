# SimpleMDM

SimpleMDM 是基于关系模型的通用主数据管理平台。对象类型、字段定义、主记录、子记录、审批变更和推送日志均使用关系表保存；部门和业务系统关联使用稳定外键。

## 技术栈

- Java 17、Spring Boot 3.3、Spring Data JPA
- Flyway、MySQL 8
- Vue 3、Pinia、Element Plus、Vite

## 本地启动

准备 MySQL 8，并设置 `SIMPLE_MDM_DB_PASSWORD` 与至少 32 字符的 `SIMPLE_MDM_JWT_SECRET`。

```powershell
cd backend-java
.\mvnw.cmd spring-boot:run
```

Flyway 从空库执行 `db/migration/V1__relational_generic_mdm.sql`；Hibernate 使用 `ddl-auto=validate`，不会在运行时建表。仅本地需要演示数据时设置 `APP_BOOTSTRAP_ENABLED=true` 鎴?`SPRING_PROFILES_ACTIVE=demo`，初始化器按稳定代码幂等写入。

```powershell
cd frontend
npm install
npm run dev
```

## 验证

```powershell
cd backend-java
.\mvnw.cmd test

cd ..\frontend
npm test
npm run build
```

## 目录

- `backend-java/`：关系化通用 MDM、系统与部门、RBAC、审批和集成服务
- `frontend/`：元数据驱动的通用 MDM 工作台

数据库密码、JWT 密钥、本地数据库文件和内部工作记录不得提交到 Git。
