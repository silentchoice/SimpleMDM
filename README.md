# SimpleMDM

SimpleMDM 是基于关系模型的通用主数据管理平台。对象类型、字段定义、主记录、子记录、整单审批变更和推送日志均使用关系表保存；部门和业务系统关联使用稳定外键。

## 技术栈

- Java 17、Spring Boot 3.3、Spring Data JPA
- Flyway、MySQL 8
- Vue 3、Pinia、Element Plus、Vitest、Vite

## 本地启动

后端默认端口为 `18001`，前端开发服务器按下述命令监听 `127.0.0.1:5173`。准备本机 MySQL 8 的 `simple_mdm` 数据库访问权限，并仅通过进程环境传入数据库密码和至少 32 字符的 JWT 密钥；不要把真实值写入配置文件、命令历史或文档。

macOS/Linux：

```bash
cd backend-java
printf 'MySQL password: '
read -rs SIMPLE_MDM_DB_PASSWORD && printf '\n'
export SIMPLE_MDM_DB_PASSWORD
printf 'JWT secret (at least 32 characters): '
read -rs SIMPLE_MDM_JWT_SECRET && printf '\n'
export SIMPLE_MDM_JWT_SECRET
printf 'Integration encryption key (base64-encoded AES key): '
read -rs SIMPLE_MDM_INTEGRATION_KEY && printf '\n'
export SIMPLE_MDM_INTEGRATION_KEY
export SPRING_PROFILES_ACTIVE=local
./mvnw spring-boot:run
```

PowerShell：

```powershell
cd backend-java
$dbPassword = Read-Host 'MySQL password' -AsSecureString
$jwtSecret = Read-Host 'JWT secret (at least 32 characters)' -AsSecureString
$env:SIMPLE_MDM_DB_PASSWORD = [Net.NetworkCredential]::new('', $dbPassword).Password
$env:SIMPLE_MDM_JWT_SECRET = [Net.NetworkCredential]::new('', $jwtSecret).Password
$integrationKey = Read-Host 'Integration encryption key (base64-encoded AES key)' -AsSecureString
$env:SIMPLE_MDM_INTEGRATION_KEY = [Net.NetworkCredential]::new('', $integrationKey).Password
$env:SPRING_PROFILES_ACTIVE = 'local'
.\mvnw.cmd spring-boot:run
```

Flyway 从空库依次执行 V1 至 V4；已有 V1/V2/V3 库只执行缺失的增量迁移。Hibernate 使用 `ddl-auto=validate`，不会在运行时建表。`local` profile 会开启演示 bootstrap；初始化器按稳定代码幂等写入，并只对已知 V1 演示元数据签名执行保留 ID 的原位升级。未知或存在冲突的业务结构会使启动失败，不会静默覆盖数据。

另开终端启动前端：

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1
```

登录使用系统编码 `DEFAULT`。`local` bootstrap 提供以下演示账户，并为其设置统一的本地初始口令；本文不记录口令值。初始化器不会覆盖已有密码哈希。演示账户和初始口令只用于隔离的本地环境，部署前必须关闭 bootstrap，并替换或禁用这些账户。

## 演示角色与数据范围

| 账户 | 部门/范围 | 主要能力 | 记录投影 |
|---|---|---|---|
| `admin` | 组织根及子树 | 系统管理、字段管理、查看、编辑、手动分发 | 所有可见部门 `FULL` |
| `hr_approver` | 人力资源，`SELF` | 查看、审批、手动分发 | 人力资源 `FULL` |
| `hr_editor` | 人力资源，`SELF` | 查看、提交整单变更、手动分发与失败重试 | 人力资源 `FULL` |
| `hr_viewer` | 人力资源，`SELF` | 只读 | 人力资源 `FULL` |
| `cross_viewer` | 其他部门，跨部门查看 | 跨部门只读；不能编辑、审批、分发或重试 | 本部门 `FULL`，其他部门 `SHARED` |

`FULL` 返回完整主字段和活动子记录的完整子字段；`SHARED` 仍返回当前主记录字段，但子表只返回活动子记录中元数据为活动且 `shared=true` 的字段。权限判断同时校验 JWT 中的系统、用户角色和部门范围；无权访问的记录按未找到处理，避免泄露跨系统或跨部门存在性。

## 整单审批

主记录与重复子表作为一个审批单提交。对启用审批的对象，编辑接口只写入 `PENDING` 草稿以及主/子字段差异，不会立即修改生效数据。审批人必须匹配同系统、对象类型和部门的有效审批分配。

批准时服务端锁定审批单，在单一数据库事务内复核审批能力、目标版本、字段元数据、唯一约束和子记录版本，然后原子应用全部主子变更、写入审批动作，并为匹配订阅写入自动分发事件。任一步失败都会整体回滚；已经处理、目标过期或并发冲突的审批返回 `409`。客户端提交的操作者、审批人或版本覆盖值不会替代 JWT 身份和服务端状态。

## 分发与重试

批准后的记录按活动订阅生成不可变完整快照。自动事件使用稳定的记录版本事件标识并由唯一约束防止重复入队；手动分发创建新的逻辑事件，失败重试则以 CAS 将同一日志行重新入队。重试保留原 `log id`、`event_id`、请求快照和初始触发审计，只更新最近重试操作者/原因/时间并重置有界传输尝试窗口。投递任务采用 at-least-once 语义；下游必须按不变的 `event_id` 幂等处理，因为网络边界无法保证 exactly-once。

日志列表按当前用户的记录可见范围过滤，只返回摘要和 `can_retry`；包含请求/响应快照的详情仅系统管理员可读。Endpoint 只接受生产策略允许的公共 HTTP(S) 地址，并执行 DNS/地址校验与连接固定；localhost、私网、保留地址和 URL userinfo 会被拒绝。Endpoint 支持 `NONE`、`BASIC`、`BEARER` 与 `API_KEY`：认证凭据通过 `SIMPLE_MDM_INTEGRATION_KEY` 进行 AES-GCM 加密后保存，列表和详情只暴露认证类型及是否已配置，绝不回显凭据或认证头。

## 完整验证

macOS/Linux：

```bash
cd backend-java
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw test

cd ../frontend
npm test -- --run
npm run build

cd ..
git diff --check
```

在没有 `/usr/libexec/java_home` 的平台，请先让 `java -version` 指向 Java 17，再执行相同的 Maven Wrapper 命令。PowerShell 使用 `./mvnw.cmd test`，其余 npm 与 Git 命令相同。

2026-08-01 final-fix 的自动化验收结果为 Maven 349 tests（0 failures/errors/skips）、Vitest 13 files / 52 tests 通过，且 Vite production build 成功。该轮没有可安全使用的本机数据库、JWT 与集成加密密钥环境，因此未执行 MySQL 重建或真实 HTTP 验收；自动化测试不能替代这两项证据。后续执行时必须仅通过进程环境传入凭据，不应把真实值保存到配置文件或文档。

## 目录

- `backend-java/`：关系化通用 MDM、系统与部门、RBAC、主子整单审批和集成分发服务
- `frontend/`：元数据驱动的通用 MDM、审批和分发工作台
- `docs/decisions/`：实现决策记录

数据库密码、JWT 密钥、token、endpoint 凭据、本地数据库文件和 `.superpowers/` 内部工作记录不得提交或发布。
