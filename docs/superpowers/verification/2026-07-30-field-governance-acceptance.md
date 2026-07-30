# Field Governance and Department Navigation Acceptance

Date: 2026-07-30  
Branch: `feat/dynamic-master-sub-fields`

## Automated verification

### Backend

Command:

```powershell
cd backend-java
.\mvnw.cmd test
```

Exit code: `0`

Result:

```text
Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The final count includes `GlobalExceptionHandlerTest` (3 cases), added after
acceptance exposed that `BusinessException(403, ...)` was being returned with
HTTP 400. The focused red run reported 2 failures (`expected 403/404, was 400`);
after the fix the focused run reported 3 tests and 0 failures.

### Frontend

Commands:

```powershell
cd frontend
npm test
npm run build
```

Exit codes: `0`, `0`

Results:

```text
Test Files  6 passed (6)
Tests       19 passed (19)
✓ 1688 modules transformed.
✓ built in 4.27s
```

Vite reported only dependency/chunk-size warnings; it emitted the production
bundle and exited successfully.

## One-time demo reset

The reset instance was started with:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--app.demo.reset=true
```

The database password was injected into that process from the existing local
development configuration; it was not written to this branch or this record.
A temporary local JWT secret of sufficient HS256 length was also injected.

Startup evidence:

```text
Started SimpleMdmApplication in 2.972 seconds
[OK] Demo data seeded: 4 users, 8 personnel, 2 historical approvals, 3 push APIs
```

The reset Java process was then stopped. The acceptance instance was restarted
with the committed configuration:

```text
app.demo.reset=false
server port 18001 listening
```

Two initial reset starts exited before `DataInitializer` ran: the first used the
placeholder database password and received MySQL 1045; the second connected to
MySQL but rejected the placeholder JWT key as 184 bits. Neither failed start
executed the demo reset.

## Database invariants

Commands:

```sql
SELECT system_code, field_key, COUNT(*) AS duplicate_count
FROM mdm_field_definition
GROUP BY system_code, field_key
HAVING COUNT(*) > 1;

SELECT table_type, COUNT(*) AS invalid_shared_master_count
FROM mdm_field_definition
WHERE table_type = 'master' AND shared = 1
GROUP BY table_type;
```

Exit code: `0`

Results:

```text
duplicate_group_count: 0
invalid_shared_master_count: 0
personnel_count: 8
user_count: 4
permission_count: 7
approver_assignment_count: 1
```

This confirms the reset retained users, permissions, and approver assignments.

## API acceptance

Base URL: `http://127.0.0.1:18001/api`

### Department navigation and projection

Using `wangwu/123456`:

```text
POST /auth/login                                      HTTP 200
GET  /personnel/departments                           HTTP 200, 5 concrete departments
GET  /personnel?department=<own department>           HTTP 200, 1 record
GET  /personnel?department=<another department>       HTTP 200, 2 records
GET  /personnel/13                                    HTTP 200, other-department record
GET  /personnel/13/sub                                HTTP 200, 1 projected sub record
```

The returned cross-department `roadmap` data contained
`product_quarter_target` and `product_delivery_rate`. The database definitions
for both keys had `shared=1`; no non-shared key was returned.

The login response carried the department-scoped EDIT permission used by the UI
to show create/edit controls only when the selected department equals the
user's own department. The component tests independently assert those controls
are absent after switching to another department.

### Field governance

Using `wangwu/123456`:

```text
POST   /dept-fields
body field_key=employee_code, table_type=sub
HTTP 400 / API code 400
message: 字段标识 employee_code 已被主表使用

DELETE /dept-fields/59
HTTP 200
```

Immediate read-only SQL after deletion:

```text
field definitions with field_key=hr_contract_type: 0
sub records whose JSON contains $.hr_contract_type: 0
```

Forbidden deletion checks after fixing the global status mapping:

```text
wangwu DELETE a master field
HTTP 403 / API code 403
message: 主表字段不可删除

admin DELETE a sub field
HTTP 403 / API code 403
message: 主管理员无字段删除权限
```

## Acceptance outcome

All automated suites, production build, database invariants, department-scoped
read APIs, shared sub-field projection, global key conflict, destructive field
cleanup, and deletion authorization checks produced the expected evidence.

Operational note: with `app.demo.reset=false`, `DataInitializer` still restores
missing demo definitions/data on a later application restart. For that reason,
the destructive cleanup assertion above was captured immediately after the
delete request on the final running acceptance instance, without another
restart.
