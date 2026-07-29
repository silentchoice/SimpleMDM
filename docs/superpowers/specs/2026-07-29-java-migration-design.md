# SimpleMDM V2 — Java 重构 + 功能完善设计

## 概述

Python FastAPI → Java Spring Boot 3 迁移，同时落实 2026-07-28 会议确认的 4 项功能需求。

---

## 一、技术栈

| 层 | 技术 |
|---|---|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.x |
| ORM | Spring Data JPA + Hibernate |
| 数据库 | MySQL 8.0 (127.0.0.1:3306, root/01270127, 库名 simple_mdm) |
| 鉴权 | jjwt + BCryptPasswordEncoder |
| 构建 | Maven |
| 前端 | Vue 3 + Element Plus (不变，需适配新 API) |

---

## 二、数据模型

### 2.1 用户与权限

```
sys_user                   — 用户账号
sys_user_permission        — 用户权限项 (查看范围/编辑范围)
sys_approver_dept          — 审批人→部门分配
```

**sys_user**: id, username, password_hash, real_name, department, status, is_admin(boolean), created_at

**sys_user_permission**: id, user_id, perm_type(VIEW/EDIT), scope_type(DEPT/POSITION/ALL), scope_value, created_at
- 例: user=wangwu, perm=VIEW, scope=DEPT, value=工程部 → 王五可以查看工程部数据
- 例: user=wangwu, perm=EDIT, scope=DEPT, value=工程部 → 王五可以编辑工程部数据
- VIEW 和 EDIT 必须分别授予
- scope=ALL 表示全部可见或全部可编辑

**sys_approver_dept**: id, user_id, department
- 管理员为审批人分配管辖部门
- 多对多: 一个审批人可管多个部门,一个部门可有多个审批人
- 审批人登录后只看管辖部门的待审流程

**关键规则**:
- 管理员 (is_admin=true): 可管理用户/权限/审批人分配/推送API,但**不能编辑数据**
- 编辑权限: 必须显式授予 EDIT scope
- 查看权限: 必须显式授予 VIEW scope

### 2.2 主数据 — 主表/子表

```
mdm_personnel              — 主表 (跨部门共享字段)
mdm_personnel_sub          — 子表 (部门隔离字段, 审批共享)
```

**mdm_personnel (主表)**: id, employee_code, name, gender, department, position, phone, email, status, version, created_at, updated_at
- 所有有 VIEW 权限的用户均可查看（在 scope 范围内）
- 字段是所有部门共识的通用字段

**mdm_personnel_sub (子表)**: id, personnel_id (FK→主表), sub_type(varchar 标识子表类型), data_json(JSON), owner_dept, visibility(varchar), version, created_at, updated_at
- `sub_type`: 标识字段组，如 "salary" / "project" / "sales_target"
- `data_json`: 存储字段名和值，如 `{"base_salary": "15000", "bonus": "3000"}`
- `owner_dept`: 所属部门，只有该部门可编辑
- `visibility`: private(仅本部门) / pending_share(审批中) / shared(审批通过,关联部门可见)
- 子表数据变更要走审批流程

**数据可见性规则**:
| 条件 | 主表 | 子表 |
|---|---|---|
| 同部门用户 | 可见（需VIEW权限） | 可见所有状态 |
| 跨部门用户 | 可见（需VIEW权限） | 仅 `visibility=shared` 可见 |
| 无权限用户 | 不可见 | 不可见 |

### 2.3 审批流程

```
wf_approval                — 审批记录
wf_approval_approver       — 审批人分配表
```

**wf_approval**: id, personnel_id, sub_id (可空,子表变更时填), workflow_type(create/update/sub_update), submitter_id, approver_id, status(pending/approved/rejected/withdrawn), change_data(JSON), submit_time, approve_time, approve_comment

**变更**:
- 创建人员: 提交主表字段 → 本部门审批人审批
- 编辑主表: 提交变更 diff → 本部门审批人审批
- 编辑子表: 提交变更 diff → 本部门审批人审批，通过后 visibility 更新

---

## 三、API 设计

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | 登录 |
| GET | /api/auth/me | 当前用户信息+权限 |
| GET/POST/PUT | /api/personnel | 人员主表 CRUD |
| GET/POST/PUT | /api/personnel/{id}/sub | 人员子表 CRUD |
| GET/POST | /api/approvals | 审批列表 / 提交审批 |
| PUT | /api/approvals/{id} | 审批操作(通过/驳回) |
| GET | /api/push-logs | 推送日志 |
| GET/POST/PUT | /api/push-apis | 推送API管理 |
| GET | /api/dashboard | 仪表盘 |
| GET/POST/PUT | /api/users | 用户管理（管理员） |
| GET/POST/DELETE | /api/users/{id}/permissions | 用户权限管理 |
| GET/POST/DELETE | /api/approvers | 审批人分配管理 |
| GET/POST/PUT | /api/dept-fields | 子表字段组定义 |

---

## 四、数据库策略

- 启动时: `CREATE DATABASE IF NOT EXISTS simple_mdm`
- `ddl-auto: update` 自动建表
- 首次启动种子数据: 4用户 + 权限配置 + 8人员主表 + 2子表记录 + 2审批 + 3推送API + 审批人分配
- **禁止所有 DELETE 语句** — 数据删除改为 UPDATE status=inactive
- 如需物理删除, 先征求用户审核

---

## 五、项目结构

```
backend-java/
├── pom.xml
└── src/main/java/com/simplemdm/
    ├── SimpleMdmApplication.java
    ├── config/              # CORS, JWT拦截器, 拦截器注册
    ├── controller/          # REST 接口 9 个
    ├── model/               # JPA Entity 7 个
    ├── dto/                 # 请求/响应 DTO
    ├── repository/          # JPA Repository
    ├── service/             # 业务逻辑
    ├── security/            # JWT工具 + @RequirePerm注解
    └── seed/                # 种子数据
```

---

## 六、启动/停止

- `start.bat`: 启动 backend-java (8081) + frontend (5173)
- `stop.bat`: 按端口 8081/5173 查找并 kill 进程
