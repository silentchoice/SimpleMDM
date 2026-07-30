# SimpleMDM 开发会话纪要

日期：2026-07-30  
工作分支：`feat/dynamic-master-sub-fields`

## 会话目标

本次会话围绕 SimpleMDM 动态主子表功能的联调、问题定位和下一阶段设计展开，并准备将安全清理后的代码与会话决策发布到 GitHub。

## 已定位的问题

### 新增子表字段时报 `system_field` 无默认值

原因是前端和后端混用了不同工作树：

- 数据库由动态字段分支创建，包含非空 `system_field`。
- 当时运行的后端来自 `master`，旧实体没有映射该字段。
- 旧后端 INSERT 未包含 `system_field`，MySQL 严格模式拒绝写入。

结论：前后端都应从
`simple-mdm/.worktrees/dynamic-master-sub-fields`
启动。

### 新增或更新主数据时报 `ownerDept` 为空

前端发送 snake_case 的 `owner_dept`，后端 DTO 使用 camelCase 的 `ownerDept`，动态工作树缺少 Jackson snake_case 配置。

处理：

- 增加 `spring.jackson.property-naming-strategy=SNAKE_CASE`。
- 增加 `DynamicPersonnelDTOJsonTest` 回归测试。
- 测试先复现 `ownerDept=null`，配置后通过。

### 主数据提交时报 `未知字段: base_salary`

动态字段存储在 JSON 中，不会创建 `base_salary` 物理列。

当时的数据库状态为：

- `base_salary` 只属于工程部和人力资源部的 `sub/salary`。
- 主表 `master/basic` 没有该字段。
- 后端重启时 `ddl-auto=create` 重建了字段定义表，但浏览器仍保留重启前的字段定义。

处理方向：

- 改用 `ddl-auto=update`，避免普通重启清除用户字段。
- 强制刷新浏览器，使前后端字段定义一致。

## 已确认的新需求

### 主子表字段标识隔离

- 同一系统内，主表和所有部门子表的 `field_key` 全局唯一。
- 主表字段不能与子表字段重名。
- 不同部门子表字段也不能重名。
- 不同系统允许使用相同字段标识。

### 子表字段共享

- “是否共享”配置在子表字段定义上。
- 主表字段没有共享开关。
- 删除原有每条子表记录的可见性配置。
- 查看本部门时可见全部子表字段。
- 查看其他部门时只返回 `shared=true` 的字段和值。
- 过滤必须在后端完成，不能把完整数据发到前端后再隐藏。

### 字段删除

- 主表字段不可删除。
- 系统字段不可删除。
- 主管理员不能删除字段。
- 只有部门管理员可以删除本部门非系统子表字段。
- 删除字段时永久清理本部门相关子表历史 JSON 中的对应键。
- 字段定义删除和历史数据清理必须处于同一事务，失败时整体回滚。

### 演示数据重建

- 主表、各部门子表使用完全不同的字段标识。
- 清理并重建人员主数据、子表数据、字段定义、审批记录和相关演示推送日志。
- 保留用户、权限及审批人配置。
- 普通重启不能再次清除用户创建的数据。

### 部门主数据页面

- 部门是必选上下文，不提供“全部部门”。
- 默认选择当前用户所属部门。
- 下拉只显示用户拥有 `VIEW` 权限的部门。
- 选中部门写入 URL，刷新和详情返回时保留。
- 当前所属部门可新增和编辑。
- 切换其他部门后只有查看权限。
- 所有授权部门的主表字段和值完整可见。
- 其他部门子表只显示共享字段和值。
- 后端列表和详情接口均需独立执行部门权限校验。

## 设计与实施文档

- `docs/superpowers/specs/2026-07-30-field-isolation-sharing-deletion-design.md`
- `docs/superpowers/specs/2026-07-30-department-master-data-navigation-design.md`
- `docs/superpowers/plans/2026-07-30-field-governance-and-department-navigation.md`

相关提交：

- `353886b`：字段隔离、共享与删除设计。
- `fe198d3`：部门主数据选择与可见性设计。
- `a7ede54`：综合实施计划。

## 当前状态

- 设计已由用户确认。
- 实施计划已完成，共九个可独立测试和提交的任务。
- 功能实现尚未开始。
- 发布到 GitHub 前必须移除真实数据库密码和固定 JWT 密钥。

## 安全发布约定

- 本地数据库密码由 `SIMPLE_MDM_DB_PASSWORD` 环境变量提供。
- JWT 密钥由 `SIMPLE_MDM_JWT_SECRET` 环境变量提供。
- GitHub 发布分支使用干净的单一快照，不包含曾经提交过本地密码的旧 Git 历史。
- 演示账号密码属于公开测试数据，可以保留，但不得用于真实环境。

## 续作完成记录

掉线后已从综合实施计划继续完成全部九项任务。

### 已完成能力

- 同一系统内动态字段标识全局唯一。
- 字段定义创建、更新、删除采用事务化服务。
- 跨部门子表只返回字段定义中 `shared=true` 的字段和值。
- 人员列表必须选择具体部门，并将部门写入 URL。
- 跨部门列表只读；新增、主表编辑和子表写操作仅限本人所属部门。
- 详情页通过 `from_department` 返回原部门列表。
- 删除子字段会同步清理相关历史 JSON 键。
- 记录级 `visibility` 已从前端 UI 和提交载荷中移除。
- 业务异常的 HTTP 状态会跟随 `BusinessException.code`，403 不再被固定映射成 400。

### 主要提交

- `cedc8f4`：系统内字段键唯一约束。
- `9f01ce2`：字段治理事务服务。
- `b323732`：字段级共享投影。
- `2bc142b`：按所选部门授权主数据。
- `fcc03ce`：刷新隔离的动态字段演示数据。
- `3479763`：共享与可删除子字段管理界面。
- `37005c2`：URL 驱动的必选部门上下文。
- `d674ae8`：安全的详情返回与跨部门只读。
- `68005d3`：保留业务异常 HTTP 状态。
- `9b29406`：完整验收记录。

### 验收结果

- Java 后端：47 个测试全部通过。
- 前端：19 个测试全部通过。
- Vite 生产构建成功。
- 数据库重复字段键分组数：0。
- 主表错误共享字段数：0。
- 重置后保留 4 个用户、7 条权限、1 条审批人分配。
- 重复主/子表字段键：HTTP 400，并返回冲突来源。
- 部门管理员删除本部门子字段：HTTP 200，字段定义和历史 JSON 残留均为 0。
- 删除主字段：HTTP 403。
- 主管理员删除子字段：HTTP 403，消息为“主管理员无字段删除权限”。

详细证据：

- `docs/superpowers/verification/2026-07-30-field-governance-acceptance.md`

### 数据库与运行状态

- 已执行一次 `app.demo.reset=true` 的受控演示数据重置。
- 当前提交配置为 `app.demo.reset=false`。
- 会话结束时 Java 后端以 `reset=false` 运行在端口 `18001`。
- `reset=false` 启动器仍会补回缺失的演示字段和对应演示 JSON；若需要验证永久删除，应在删除后、再次重启前检查。

### GitHub 发布

当前完整开发分支：

- `feat/dynamic-master-sub-fields`
- 最新验收提交：`9b29406`

为避免推送旧 Python 后端历史，已从 GitHub Java-only 基线创建选择性发布分支，只同步：

- `frontend/`
- `backend-java/`

GitHub 发布结果：

- 远程仓库：`https://github.com/silentchoice/SimpleMDM.git`
- 分支：`publish/dynamic-master-sub-fields`
- 发布提交：`1d8eb8e`
- PR 入口：`https://github.com/silentchoice/SimpleMDM/pull/new/publish/dynamic-master-sub-fields`

发布副本再次验证：

- Java 后端 47/47。
- 前端 19/19。
- 生产构建成功。
- 未包含 Python `backend/`、本地数据库密码或 JWT 密钥。

## 下次唤醒

唤醒词：`SimpleMDM`

收到唤醒词后：

1. 读取本文件和最终验收记录。
2. 检查 `feat/dynamic-master-sub-fields` 与 `publish/dynamic-master-sub-fields` 的 Git 状态。
3. 根据用户指令继续创建 PR、合并发布分支，或处理评审意见。
4. 不要把旧 Python 后端或本地敏感配置推送到 GitHub。