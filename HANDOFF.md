# SimpleMDM 脱敏交接说明

## 当前状态

- 发布分支：`publish/feat-dynamic-master-sub-fields`
- 开发分支：`feat/dynamic-master-sub-fields`
- Java 17 后端、Vue/Vite 前端、Flyway/MySQL 关系化 MDM 已完成主要重构。
- 分发支持 NONE、BASIC、BEARER、API_KEY，凭据使用进程环境密钥加密。
- 分发队列支持 PENDING、RUNNING、SUCCESS、FAILED、CANCELLED，支持取消、重试、Cron 和时区。
- 元数据支持对象、主字段、子表、子字段管理及子字段共享开关。

## 本地启动

不要把密码或 JWT 写入文件。启动前通过当前机器的进程环境变量提供：

```bash
export SIMPLE_MDM_DB_PASSWORD='本机数据库密码'
export SIMPLE_MDM_JWT_SECRET='本机 JWT 密钥'
export SIMPLE_MDM_INTEGRATION_KEY='Base64 编码的 AES-256 密钥'
cd backend-java
./mvnw spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

## 演示账号

本地 bootstrap 提供 `admin`、`hr_approver`、`hr_editor`、`hr_viewer`、`cross_viewer`。初始口令不记录在仓库中，请在本地安全获取或重置。

## 验证

- Java 17 Maven 全量测试已通过。
- 前端 Vitest 与 Vite 构建已通过。
- 本地 MySQL 使用 Flyway 迁移；不要在未确认目标库的情况下执行清库。
- 分发 URL 默认只允许公网 HTTP(S)，回环和私有网段会被 SSRF 防护拒绝。

## 发布安全

本分支为无历史发布快照，不包含 `.superpowers`、会话原文、Python 历史路径、数据库密码、JWT 或认证 token。任何新凭据只能通过本地环境变量提供。
