# SimpleMDM — 主数据管理平台 Demo

SimpleMDM 是一个基于 Java 和 Vue 的轻量级主数据管理示例，覆盖人员主数据、动态主子表字段、部门权限、审批和下游推送流程。

## 技术栈

- 后端：Java 17、Spring Boot 3.3、Spring Data JPA
- 数据库：MySQL 8
- 前端：Vue 3、Element Plus、Vite
- 认证：JWT

## 项目结构

```text
simple-mdm/
├── backend-java/     # Spring Boot 后端
├── frontend/         # Vue 3 前端
└── docs/             # 设计、实施计划与开发会话纪要
```

仓库不包含旧版 Python/FastAPI 后端。

## 环境要求

- JDK 17+
- MySQL 8+
- Node.js 18+

## 配置

后端默认连接本机 MySQL 的 `simple_mdm` 数据库。启动前请设置环境变量：

```powershell
$env:SIMPLE_MDM_DB_PASSWORD = '你的数据库密码'
$env:SIMPLE_MDM_JWT_SECRET = '至少 32 字节的随机密钥'
```

不要把真实密码或生产 JWT 密钥提交到仓库。

## 启动后端

```powershell
cd backend-java
.\mvnw.cmd spring-boot:run
```

后端地址：

```text
http://localhost:18001
```

## 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端地址：

```text
http://localhost:5173
```

## 测试

后端：

```powershell
cd backend-java
.\mvnw.cmd test
```

前端：

```powershell
cd frontend
npm test
npm run build
```

## 演示账号

以下账号仅用于本地演示：

| 用户名 | 密码 | 用途 |
|---|---|---|
| `wangwu` | `123456` | 部门主数据维护 |
| `lisi` | `123456` | 审批 |
| `zhaoliu` | `123456` | 只读查看 |
| `admin` | `admin123` | 主管理员 |

请勿在真实环境中使用这些凭据。

## 开发资料

- 字段隔离、共享和删除设计：`docs/superpowers/specs/2026-07-30-field-isolation-sharing-deletion-design.md`
- 部门主数据导航设计：`docs/superpowers/specs/2026-07-30-department-master-data-navigation-design.md`
- 综合实施计划：`docs/superpowers/plans/2026-07-30-field-governance-and-department-navigation.md`
- 会话纪要：`docs/sessions/2026-07-30-simplemdm-development-session.md`
