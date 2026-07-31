# SimpleMDM 全关系化通用主数据重建设计

日期：2026-07-31  
目标分支：`feat/dynamic-master-sub-fields`

## 1. 背景与目标

当前系统没有独立部门实体。用户、人员主数据、字段定义、权限和审批配置均重复保存部门名称字符串，无法使用外键保证部门存在，导致部门可能为空、名称不一致以及跨表关联失效。

本次重建目标：

- 建立独立的系统与部门树模型。
- 所有部门关系使用非空 `department_id` 外键。
- 保留多业务系统隔离能力。
- 将人员模型提升为可配置的通用 MDM 对象模型。
- 将 JSON 动态字段改为全关系化、类型化字段值。
- 使用 Flyway 管理数据库结构，禁止依赖 Hibernate 自动演进生产结构。
- 清空并重建本地 `simple_mdm`，不迁移现有演示数据。
- 仅保留 Java 后端和前端；旧 Python 后端已删除。

## 2. 总体架构

系统分为五个边界：

1. 系统与组织：业务系统、部门树、用户。
2. 身份与授权：角色、权限、部门访问范围。
3. MDM 元数据：对象类型、主字段、子类型、子字段。
4. MDM 数据：主记录、主字段值、子记录、子字段值。
5. 流程与集成：审批单、审批记录、推送配置与日志。

业务表不得保存部门名称作为关联键，也不得继续保存散落的 `system_code`。名称和编码只用于展示或业务唯一标识，跨表关系统一使用主键 ID。

## 3. 系统与组织模型

### 3.1 `sys_system`

字段：

- `id`
- `code`
- `name`
- `status`
- `created_at`
- `updated_at`
- `version`

约束：

- `code` 全局唯一且创建后不可修改。
- 禁用系统后禁止登录和业务写入。

### 3.2 `sys_department`

字段：

- `id`
- `system_id`
- `parent_id`
- `code`
- `name`
- `level`
- `path`
- `sort_order`
- `status`
- `created_at`
- `updated_at`
- `version`

约束：

- `system_id` 非空，引用 `sys_system`。
- `parent_id` 可空，根部门为空；非根部门必须引用同一系统中的部门。
- `(system_id, code)` 唯一。
- `(system_id, parent_id, name)` 唯一。
- `path` 保存由部门 ID 组成的规范化路径，用于子树查询；移动部门时在事务中更新整个子树。
- 禁止形成循环。
- 部门存在用户、权限范围或 MDM 记录时不得硬删除；应先迁移关联数据，再停用或删除。

### 3.3 `sys_user`

字段：

- `id`
- `system_id`
- `department_id`
- `username`
- `password_hash`
- `real_name`
- `email`
- `mobile`
- `status`
- `is_system_admin`
- `failed_login_count`
- `locked_until`
- `last_login_at`
- `password_changed_at`
- `created_at`
- `created_by`
- `updated_at`
- `updated_by`
- `version`
- `deleted_at`

约束：

- `(system_id, username)` 唯一。
- `system_id` 和 `department_id` 非空。
- 用户与部门必须属于同一系统。
- 密码只保存 BCrypt 或 Argon2 摘要。
- 禁用或软删除用户不能登录，也不能新增为审批负责人。
- 一个用户只有一个主属部门；额外部门访问通过授权范围表表达。

## 4. 身份与授权模型

表：

- `sys_role`
- `sys_permission`
- `sys_user_role`
- `sys_role_permission`
- `sys_user_department_scope`

`sys_user_department_scope` 字段包括：

- `user_id`
- `department_id`
- `scope_mode`：`SELF` 或 `SUBTREE`
- `can_view`
- `can_edit`

规则：

- 角色表达动作权限，例如 `MDM_RECORD_VIEW`、`MDM_RECORD_EDIT`、`MDM_FIELD_MANAGE`、`APPROVAL_REVIEW`。
- 部门范围表达这些动作可作用的数据范围。
- `is_system_admin=true` 只绕过当前系统内的角色与部门范围检查，不能跨系统。
- 用户主属部门默认具备的权限由角色决定，不因属于该部门就自动获得编辑权。
- `(user_id, department_id, scope_mode)` 唯一。

## 5. MDM 元数据模型

### 5.1 `mdm_object_type`

描述一种主数据对象，例如人员、客户、供应商或产品。

主要字段：

- `id`
- `system_id`
- `code`
- `name`
- `status`
- `department_scoped`
- `approval_required`
- 审计字段与 `version`

约束：`(system_id, code)` 唯一。

### 5.2 `mdm_field_definition`

主要字段：

- `id`
- `object_type_id`
- `field_key`
- `field_name`
- `data_type`
- `required`
- `unique_value`
- `searchable`
- `shared`
- `max_length`
- `precision_value`
- `scale_value`
- `reference_object_type_id`
- `default_value`
- `validation_rule`
- `sort_order`
- `status`
- 审计字段与 `version`

规则：

- `(object_type_id, field_key)` 唯一。
- `data_type` 支持 `STRING`、`TEXT`、`INTEGER`、`DECIMAL`、`BOOLEAN`、`DATE`、`DATETIME`、`REFERENCE`。
- 字段定义决定值表中允许使用的类型列。
- 存在数据的字段不能直接硬删除；先停用，或执行显式的数据清理事务。
- `shared` 仅在对象启用部门隔离时控制跨部门读取。

### 5.3 子类型与子字段

表：

- `mdm_child_type`
- `mdm_child_field_definition`

约束：

- `(object_type_id, code)` 唯一。
- `(child_type_id, field_key)` 唯一。
- 子字段使用与主字段相同的数据类型、校验与生命周期规则。

## 6. MDM 数据模型

### 6.1 `mdm_record`

字段：

- `id`
- `system_id`
- `object_type_id`
- `department_id`
- `record_code`
- `status`
- `approval_status`
- `created_at`
- `created_by`
- `updated_at`
- `updated_by`
- `version`
- `deleted_at`

约束：

- `system_id`、`object_type_id` 和 `department_id` 非空。
- 三者必须属于一致的系统上下文。
- `(object_type_id, record_code)` 在未删除记录中唯一。
- 所有更新使用乐观锁。

### 6.2 `mdm_record_value`

字段：

- `id`
- `record_id`
- `field_definition_id`
- `string_value`
- `text_value`
- `integer_value`
- `decimal_value`
- `boolean_value`
- `date_value`
- `datetime_value`
- `reference_record_id`
- 审计字段

约束：

- `(record_id, field_definition_id)` 唯一。
- 一行只能有一个类型值列非空。
- 实际非空类型列必须与字段定义的 `data_type` 一致。
- 必填、长度、精度、引用对象类型和业务唯一性由服务层验证，并由可实现的数据库约束与索引加强。
- `REFERENCE` 必须引用同一系统中类型匹配且未删除的记录。

### 6.3 子记录和值

表：

- `mdm_child_record`
- `mdm_child_record_value`

`mdm_child_record` 必须引用主记录和子类型，其系统与部门从主记录继承，不允许独立漂移。子值表使用与主值表相同的类型化列和单值约束。

## 7. 审批与集成

审批表：

- `wf_approval_request`
- `wf_approval_change`
- `wf_approval_action`
- `sys_approver_assignment`

规则：

- 审批申请引用对象类型、目标记录、发起人和目标部门。
- 每个字段变更在 `wf_approval_change` 中保存字段定义、旧类型值和新类型值，不保存整段 JSON。
- 审批动作保存处理人、动作、意见和时间。
- 审批通过时校验记录版本；版本冲突则拒绝覆盖并要求重新提交。
- 审批负责人可按系统、对象类型和部门配置。

集成表：

- `sys_push_endpoint`
- `sys_push_subscription`
- `sys_push_log`

推送日志保存稳定 ID、状态、重试次数和必要的请求快照。敏感认证信息必须加密或通过环境变量提供。

## 8. 数据流与 API

读取记录：

1. 从认证上下文确定 `system_id` 和用户。
2. 校验对象动作权限。
3. 展开用户可访问的部门范围。
4. 查询主记录。
5. 批量查询字段定义和值，避免逐记录 N+1 查询。
6. 根据部门与 `shared` 规则投影字段。
7. 组装保持 `snake_case` 的 API 响应。

写入记录：

1. 校验系统、对象类型、部门和编辑范围。
2. 校验字段定义、数据类型、必填、唯一性与引用关系。
3. 在单一事务中写入记录和值。
4. 对需要审批的对象创建审批申请，不直接覆盖已生效数据。
5. 提交后生成推送事件。

前端可以继续使用对象形式的 `data` 载荷；后端负责将 API 对象拆分为关系化值行。数据库内部不再保存 JSON 动态业务字段。

## 9. 删除与一致性策略

- 用户和 MDM 记录默认软删除。
- 系统、部门、角色、对象类型和字段定义优先停用。
- 元数据存在关联数据时禁止直接硬删除。
- 子记录随主记录软删除；显式永久清理必须在事务中按外键顺序执行。
- 外键默认使用 `RESTRICT`，仅纯关联表使用 `CASCADE`。
- 名称修改不影响关系。
- 禁止将关键外键设为 `NULL` 来绕过删除冲突。

## 10. 数据库重建与配置

- 使用 Flyway 创建完整初始迁移。
- `spring.jpa.hibernate.ddl-auto=validate`。
- 数据库密码从 `SIMPLE_MDM_DB_PASSWORD` 注入。
- JWT 密钥从 `SIMPLE_MDM_JWT_SECRET` 注入。
- 本地密码 `01270127` 不写入代码、迁移、文档或 Git 历史。

初始数据包括：

- 一个默认业务系统。
- 一棵示例部门树。
- 一个系统管理员。
- 基础角色与权限。
- “人员”对象类型、基础主字段和示例子类型。

正式清库顺序：

1. 完成并测试初始迁移。
2. 停止 Java 后端和任何写入数据库的进程。
3. 删除并重建本地 `simple_mdm` 数据库。
4. 使用 Flyway 执行初始迁移。
5. 启动应用并写入受控初始数据。
6. 验证表、外键、唯一约束、非空约束、部门树和登录。

本次明确不备份或迁移现有 `simple_mdm` 数据。

## 11. 测试与验收

后端自动化测试覆盖：

- 跨系统隔离。
- 部门树循环与跨系统父子关系拒绝。
- 用户部门非空及系统一致性。
- RBAC 与额外部门范围。
- 字段类型、必填、唯一值和引用校验。
- 主子记录事务。
- 跨部门共享字段投影。
- 审批版本冲突。
- 元数据删除保护。
- Flyway 从空库完整迁移。

前端测试覆盖：

- 登录用户与部门上下文。
- 部门树选择。
- 通用对象和字段渲染。
- 类型化字段录入与错误提示。
- 跨部门只读和共享投影。
- 审批差异展示。

最终验收：

- 后端全量测试通过。
- 前端全量测试和生产构建通过。
- 空库迁移成功。
- 所有用户和 MDM 记录的部门均非空且外键有效。
- 数据库中不存在旧 JSON 业务字段列。
- 公开发布内容只包含 `frontend/` 和 `backend-java/`，不包含旧 Python 后端或本地敏感配置。

## 12. 范围边界

本轮不包含：

- 从旧 JSON 数据迁移业务记录。
- 跨数据库或跨租户分库。
- 可视化流程设计器。
- 字段公式引擎。
- 搜索引擎或数据仓库同步。
- 用户自助注册和第三方单点登录。

这些能力可以在新关系模型稳定后单独设计。
