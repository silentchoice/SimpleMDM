# MDM 最小可行产品实施计划

> **面向执行代理：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，逐任务实施本计划。所有步骤使用复选框跟踪。

**目标：** 交付一个可通过 Docker Compose 启动的 MDM 系统，包含权限、部门、动态元数据、主子数据、审批、编辑锁及定时 HTTP 同步。

**架构：** 使用前后端分离的模块化单体。后端按领域包划分并以 MySQL 为事实来源、Redis 为分布式协调设施；前端以类型化 API 客户端驱动 Element Plus 页面。

**技术栈：** Java 17、Spring Boot 3.3、MyBatis-Plus、Flyway、MySQL 8.0、Redis、JUnit 5、Testcontainers、Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Vitest、Docker Compose。

## 全局约束

- 数据库仅支持 MySQL 8.0。
- 角色固定为 `SUPER_ADMIN`、`DEPT_APPROVER`、`DEPT_EDITOR`、`DEPT_VIEWER`。
- API 响应固定为 `{ code, message, data, requestId }`。
- 待审批内容不得覆盖已生效数据；审批通过后才原子应用。
- 跨部门子表字段必须在后端按 `share_config=true` 过滤。
- 编辑锁有效期为 30 分钟。
- 同步仅支持 REST + API Key/Basic Auth；重试间隔为 1、2、5、60、60 分钟，最多五次。
- 凭据和初始管理员信息仅通过环境变量提供，不提交明文秘密。
- RabbitMQ、Kafka、Elasticsearch、邮件、OAuth2、增量同步和共享模板不在本期范围。

---

### 任务 1：建立可运行的工程骨架与数据库基线

**文件：**
- 创建：`backend/pom.xml`
- 创建：`backend/src/main/java/com/example/mdm/MdmApplication.java`
- 创建：`backend/src/main/resources/application.yml`
- 创建：`backend/src/main/resources/db/migration/V1__baseline.sql`
- 创建：`backend/src/test/java/com/example/mdm/ArchitectureSmokeTest.java`
- 创建：`frontend/package.json`、`frontend/vite.config.ts`、`frontend/tsconfig.json`
- 创建：`frontend/src/main.ts`、`frontend/src/App.vue`
- 创建：`frontend/src/App.spec.ts`
- 创建：`docker-compose.yml`、`.env.example`、`.gitignore`

**接口：**
- 产出：可启动的 Spring Boot 应用、Flyway 数据库基线和 Vue 应用。
- 数据表：系统、元数据、正式记录、草稿、历史、审批、编辑锁、同步配置、日志和重试表；外键引用顺序必须可由 Flyway 一次执行成功。

- [ ] **步骤 1：先写失败的后端冒烟测试**

```java
@SpringBootTest
class ArchitectureSmokeTest {
  @Test void applicationContextLoads() {}
}
```

- [ ] **步骤 2：运行 `cd backend && ./mvnw -Dtest=ArchitectureSmokeTest test`，确认因工程入口或配置不存在而失败。**
- [ ] **步骤 3：创建 Maven、应用入口、配置和 V1 迁移；JSON 列使用 MySQL `JSON`，所有状态列使用 `VARCHAR(32)`。**
- [ ] **步骤 4：运行后端测试，确认 Testcontainers MySQL 完成迁移且上下文启动成功。**
- [ ] **步骤 5：先写前端测试，断言根组件显示“主数据管理系统”；运行 `npm test -- --run` 确认失败。**
- [ ] **步骤 6：创建最小 Vue 入口使测试通过，再运行 `npm run build`。**
- [ ] **步骤 7：添加 Compose 服务和健康检查，运行 `docker compose config`。**
- [ ] **步骤 8：提交：`git commit -m "chore: bootstrap MDM applications and database"`。**

### 任务 2：统一响应、错误处理与 JWT 安全基础

**文件：**
- 创建：`backend/src/main/java/com/example/mdm/common/api/ApiResponse.java`
- 创建：`backend/src/main/java/com/example/mdm/common/error/BusinessException.java`
- 创建：`backend/src/main/java/com/example/mdm/common/error/GlobalExceptionHandler.java`
- 创建：`backend/src/main/java/com/example/mdm/auth/JwtService.java`
- 创建：`backend/src/main/java/com/example/mdm/auth/SecurityConfig.java`
- 创建：`backend/src/main/java/com/example/mdm/auth/AuthController.java`
- 测试：对应 `common` 与 `auth` 包下测试。

**接口：**
- 产出：`JwtService.issue(UserPrincipal)`、`JwtService.parse(String)`、`POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/menu`。
- 登录成功返回访问令牌、用户、角色及部门；失败固定返回 401，且不得泄露用户是否存在。

- [ ] **步骤 1：写 `ApiResponseTest` 和 `AuthControllerTest`，分别断言响应结构、有效登录、错误密码、缺失令牌和 `requestId`。**
- [ ] **步骤 2：运行定向测试，确认因类型和端点不存在而失败。**
- [ ] **步骤 3：实现统一响应、异常映射、请求 ID 过滤器、BCrypt 密码校验和 JWT 过滤器。**
- [ ] **步骤 4：运行定向测试及全量后端测试，确认全部通过且日志不含密码或令牌。**
- [ ] **步骤 5：提交：`git commit -m "feat: add JWT authentication foundation"`。**

### 任务 3：部门、用户、角色与服务层授权

**文件：**
- 创建：`backend/src/main/java/com/example/mdm/system/{controller,service,repository,model,dto}/...`
- 创建：`backend/src/main/java/com/example/mdm/auth/AuthorizationService.java`
- 创建：`backend/src/main/java/com/example/mdm/system/AdminInitializer.java`
- 测试：`DepartmentServiceTest.java`、`UserServiceTest.java`、`AuthorizationServiceTest.java`、`AdminInitializerTest.java`。

**接口：**
- 产出：部门 CRUD、用户创建/修改/禁用/列表、角色列表与角色分配。
- `AuthorizationService.requireRole(RoleCode...)` 和 `requireDepartment(long)` 作为后续领域的唯一授权入口。

- [ ] **步骤 1：写失败测试，覆盖超级管理员操作、普通用户禁止管理、审批人员仅本部门审批、编辑人员仅本部门修改及环境变量初始化管理员。**
- [ ] **步骤 2：运行定向测试并确认失败原因是服务行为尚未实现。**
- [ ] **步骤 3：实现模型、Mapper、服务和控制器；初始化器在缺少环境变量时不创建弱口令账户。**
- [ ] **步骤 4：运行定向和全量测试。**
- [ ] **步骤 5：提交：`git commit -m "feat: add departments users and role authorization"`。**

### 任务 4：元数据定义与动态字段校验

**文件：**
- 创建：`backend/src/main/java/com/example/mdm/metadata/...`
- 创建：`backend/src/main/java/com/example/mdm/metadata/FieldValueValidator.java`
- 测试：`MetadataServiceTest.java`、`FieldValueValidatorTest.java`、`MetadataControllerTest.java`。

**接口：**
- 产出：主类型、部门分配、主字段、子类型及子字段接口。
- `FieldValueValidator.validate(List<FieldDefinition>, Map<String,Object>)` 对未知字段、必填、文本、数字、日期、日期时间、选择、单选、复选和开关进行校验。

- [ ] **步骤 1：写失败测试，覆盖部门只能分配一种主类型、字段编码唯一、非法字段类型、缺失必填项、错误数值类型及非法选项。**
- [ ] **步骤 2：运行测试确认红灯。**
- [ ] **步骤 3：实现最小模型、Mapper、事务服务、控制器和校验器。字段结构变更生成审批快照，不立即修改生效定义。**
- [ ] **步骤 4：运行定向和全量测试。**
- [ ] **步骤 5：提交：`git commit -m "feat: add metadata definitions and validation"`。**

### 任务 5：主子数据草稿、可见性和编辑锁

**文件：**
- 创建：`backend/src/main/java/com/example/mdm/record/...`
- 创建：`backend/src/main/java/com/example/mdm/record/RecordVisibilityService.java`
- 创建：`backend/src/main/java/com/example/mdm/record/EditLockService.java`
- 测试：`RecordServiceTest.java`、`RecordVisibilityServiceTest.java`、`EditLockServiceTest.java`、`RecordControllerTest.java`。

**接口：**
- 产出：主/子记录草稿 CRUD、详情、列表、提交入口，以及锁获取、续期和释放接口。
- `RecordVisibilityService.filterSubFields(SubRecord, viewerDeptId)` 返回本部门完整字段或跨部门共享字段。

- [ ] **步骤 1：写失败测试，覆盖草稿不影响正式数据、动态字段校验、逻辑删除、乐观锁冲突、30 分钟锁过期和跨部门字段过滤。**
- [ ] **步骤 2：运行测试确认红灯及预期失败信息。**
- [ ] **步骤 3：实现草稿与正式数据分离、Redis 锁及 MySQL 锁审计；控制器只返回过滤后的 DTO。**
- [ ] **步骤 4：运行定向测试、全量测试和迁移测试。**
- [ ] **步骤 5：提交：`git commit -m "feat: add record drafts visibility and edit locks"`。**

### 任务 6：审批事务、版本历史和差异

**文件：**
- 创建：`backend/src/main/java/com/example/mdm/approval/...`
- 创建：`backend/src/main/java/com/example/mdm/approval/SnapshotDiffService.java`
- 测试：`ApprovalServiceTest.java`、`SnapshotDiffServiceTest.java`、`ApprovalControllerTest.java`。

**接口：**
- 产出：待审批、已提交、详情、通过和拒绝接口。
- `ApprovalService.approve(taskId, actor, comment)` 在单事务中校验部门、锁定任务、应用快照、写历史并更新状态。
- `ApprovalService.reject(taskId, actor, reason)` 要求非空原因且不得改变正式数据。

- [ ] **步骤 1：写失败测试，覆盖重复审批、跨部门审批、拒绝原因、审批原子性、版本递增、历史记录及快照差异。**
- [ ] **步骤 2：运行测试确认红灯。**
- [ ] **步骤 3：实现审批模型、行锁查询、事务服务、差异服务和控制器；通过或拒绝后释放编辑锁。**
- [ ] **步骤 4：运行定向及全量后端测试。**
- [ ] **步骤 5：提交：`git commit -m "feat: add transactional approval workflow"`。**

### 任务 7：定时 REST 同步、凭据加密和重试

**文件：**
- 创建：`backend/src/main/java/com/example/mdm/sync/...`
- 创建：`backend/src/main/java/com/example/mdm/sync/CredentialCipher.java`
- 创建：`backend/src/main/java/com/example/mdm/sync/DynamicSyncScheduler.java`
- 创建：`backend/src/main/java/com/example/mdm/sync/SyncExecutor.java`
- 创建：`backend/src/main/java/com/example/mdm/sync/RetryPolicy.java`
- 测试：`CredentialCipherTest.java`、`DynamicSyncSchedulerTest.java`、`SyncExecutorTest.java`、`RetryPolicyTest.java`、`SyncControllerTest.java`。

**接口：**
- 产出：同步配置 CRUD/提交审批/手动触发、日志列表、重试列表和停止接口。
- `RetryPolicy.nextDelay(attempt)` 对第 1 至 5 次返回 `1m,2m,5m,60m,60m`，超过五次返回空。
- `DynamicSyncScheduler.refresh(configId)` 仅为 `ACTIVE` 配置注册合法 cron。

- [ ] **步骤 1：写失败测试，覆盖凭据加解密与隐藏、API Key/Basic Auth 请求、只同步已审批数据、cron 注册替换注销、Redis 互斥和五次重试状态机。**
- [ ] **步骤 2：运行测试确认红灯。**
- [ ] **步骤 3：实现 AES-GCM 凭据加密、基于 Spring `TaskScheduler` 的动态任务、Java HTTP 客户端、同步快照、日志与重试扫描器。**
- [ ] **步骤 4：运行 WireMock/Testcontainers 集成测试和全量后端测试。**
- [ ] **步骤 5：提交：`git commit -m "feat: add scheduled REST synchronization"`。**

### 任务 8：Vue 应用框架、动态表单和核心页面

**文件：**
- 创建：`frontend/src/router/index.ts`、`frontend/src/stores/auth.ts`、`frontend/src/api/client.ts`
- 创建：`frontend/src/layouts/MainLayout.vue`
- 创建：`frontend/src/components/DynamicForm.vue`、`frontend/src/components/SnapshotDiff.vue`
- 创建：`frontend/src/views/{Login,System,Metadata,Records,Approvals,SyncConfigs,SyncLogs,SyncRetries}.vue`
- 测试：对应组件和视图的 `*.spec.ts`。

**接口：**
- 产出：类型化 API 客户端、路由守卫、角色菜单、动态表单、审批差异和同步管理页面。
- `DynamicForm` 接收 `fields`、`modelValue` 和 `readonly`，只渲染允许列表中的组件。

- [ ] **步骤 1：写失败测试，覆盖登录跳转、401 清理会话、角色菜单、动态字段组件映射、表单校验、跨部门只读展示、审批差异和 cron 错误提示。**
- [ ] **步骤 2：运行 `npm test -- --run` 确认红灯。**
- [ ] **步骤 3：实现 API 客户端、状态、路由、布局、组件和页面，以后端 DTO 为唯一字段来源。**
- [ ] **步骤 4：运行 `npm test -- --run` 和 `npm run build`。**
- [ ] **步骤 5：提交：`git commit -m "feat: add MDM frontend workflows"`。**

### 任务 9：全栈验收、容器化与运行文档

**文件：**
- 创建：`backend/src/test/java/com/example/mdm/e2e/ApprovedRecordSyncTest.java`
- 创建：`backend/Dockerfile`、`frontend/Dockerfile`、`frontend/nginx.conf`
- 修改：`docker-compose.yml`、`.env.example`
- 创建：`README.md`

**接口：**
- 产出：可复现的完整启动与验证流程。

- [ ] **步骤 1：写失败的全链路测试：创建草稿、提交、审批、触发调度、HTTP 目标成功、查询成功日志。**
- [ ] **步骤 2：增加失败链路：目标持续失败，时间推进后验证五次重试及最终失败。**
- [ ] **步骤 3：运行测试确认两条链路在缺少装配时失败，再补齐装配和事务边界。**
- [ ] **步骤 4：运行 `backend/mvnw test`、`frontend npm test -- --run`、`frontend npm run build` 和 `docker compose config`。**
- [ ] **步骤 5：启动 Compose，检查 MySQL、Redis、后端 `/actuator/health` 和前端健康状态；执行一次浏览器主流程。**
- [ ] **步骤 6：在 README 写明环境变量、本地启动、Compose 启动、测试命令、初始管理员创建和定时同步配置方法。**
- [ ] **步骤 7：运行 `git diff --check` 和 `git status --short`，确认没有秘密、构建产物或无关文件。**
- [ ] **步骤 8：提交：`git commit -m "docs: finalize deployment and verification"`。**
