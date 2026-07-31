# Relational Generic MDM Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SimpleMDM's department-name and JSON-based personnel schema with a Flyway-managed, multi-system, department-safe, fully relational generic MDM platform.

**Architecture:** Build from the database outward: install a repeatable empty-schema migration, map focused JPA aggregates, add system-aware RBAC, implement generic metadata and typed values, then adapt approval, integration, APIs, and Vue views. Keep the existing snake_case HTTP contract where practical while removing personnel-specific persistence.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, Flyway, MySQL 8, H2 tests, JUnit 5, Mockito, Vue 3, Pinia, Element Plus, Vitest, Vite 6.

## Global Constraints

- All department relationships use non-null `department_id` foreign keys; department names are never relationship keys.
- All business-system relationships use `system_id`; `system_code` is not duplicated in business tables.
- Dynamic values are relational typed rows, not JSON database columns.
- HTTP payloads remain snake_case.
- `spring.jpa.hibernate.ddl-auto=validate`.
- Database credentials come from `SIMPLE_MDM_DB_PASSWORD`; JWT secrets come from `SIMPLE_MDM_JWT_SECRET`.
- Never commit the local password `01270127`, local JWT values, `.superpowers/`, or database files.
- The rebuild intentionally does not migrate old `simple_mdm` records.
- Public GitHub publication contains only `frontend/` and `backend-java/`.

---

## File Structure

New backend packages separate responsibilities:

- `model/system/`: system, department, user, role, permission, scope entities.
- `model/mdm/`: object metadata, records, typed values, child aggregates.
- `model/workflow/`: approval request, field changes, actions, assignments.
- `model/integration/`: endpoints, subscriptions, delivery logs.
- `repository/...`: one repository per aggregate root or lookup entity.
- `service/system/`: department tree and authorization services.
- `service/mdm/`: metadata validation, typed value conversion, record transactions.
- `service/workflow/`: approval lifecycle.
- `service/integration/`: outbound subscription and delivery.
- `controller/`: generic systems, departments, metadata, records, workflow APIs.
- `db/migration/`: Flyway SQL only; no runtime DDL.

Frontend additions:

- `api/systems.js`, `api/departments.js`, `api/mdm.js`: generic endpoints.
- `stores/context.js`: selected system, object type, and department.
- `views/mdm/`: generic list/form and metadata manager.
- `components/mdm/`: typed value input and display.

Old `MdmPersonnel*`, `Personnel*`, and `deptFields` files are removed only after their generic replacements pass tests.

---

### Task 1: Flyway Empty-Schema Baseline

**Files:**
- Modify: `backend-java/pom.xml`
- Modify: `backend-java/src/main/resources/application.yml`
- Modify: `backend-java/src/main/resources/application.properties`
- Create: `backend-java/src/main/resources/db/migration/V1__relational_generic_mdm.sql`
- Create: `backend-java/src/test/java/com/simplemdm/migration/FlywayMigrationTest.java`

**Interfaces:**
- Consumes: MySQL/H2 `DataSource`.
- Produces: schema tables and foreign keys named in the approved design; `FlywayMigrationTest` proves migration from an empty database.

- [ ] **Step 1: Write the failing empty-schema migration test**

```java
@Test
void migratesEmptyDatabaseAndCreatesRequiredForeignKeys() throws Exception {
    Flyway flyway = Flyway.configure()
        .dataSource("jdbc:h2:mem:migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
        .locations("classpath:db/migration")
        .load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
    try (Connection c = flyway.getConfiguration().getDataSource().getConnection()) {
        assertThat(tableNames(c)).contains(
            "SYS_SYSTEM", "SYS_DEPARTMENT", "SYS_USER",
            "MDM_OBJECT_TYPE", "MDM_RECORD", "MDM_RECORD_VALUE",
            "MDM_CHILD_TYPE", "MDM_CHILD_RECORD", "MDM_CHILD_RECORD_VALUE");
        assertThat(importedKeys(c, "SYS_USER"))
            .containsEntry("DEPARTMENT_ID", "SYS_DEPARTMENT")
            .containsEntry("SYSTEM_ID", "SYS_SYSTEM");
    }
}
```

- [ ] **Step 2: Run the test and verify the missing migration failure**

Run: `cd backend-java; .\mvnw.cmd -Dtest=FlywayMigrationTest test`  
Expected: FAIL because Flyway is absent or `V1__relational_generic_mdm.sql` does not exist.

- [ ] **Step 3: Add Flyway and the complete V1 schema**

Add `org.flywaydb:flyway-core` and `org.flywaydb:flyway-mysql`. Configure:

```yaml
spring:
  datasource:
    password: ${SIMPLE_MDM_DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

The migration creates every approved system, RBAC, metadata, record, child, workflow, and integration table. Use `RESTRICT` for aggregate references, `CASCADE` only for pure join/value rows, and checks such as:

```sql
CONSTRAINT uk_department_code UNIQUE (system_id, code),
CONSTRAINT uk_record_field UNIQUE (record_id, field_definition_id),
CONSTRAINT ck_record_value_one_type CHECK (
  (string_value IS NOT NULL) + (text_value IS NOT NULL) +
  (integer_value IS NOT NULL) + (decimal_value IS NOT NULL) +
  (boolean_value IS NOT NULL) + (date_value IS NOT NULL) +
  (datetime_value IS NOT NULL) + (reference_record_id IS NOT NULL) <= 1
)
```

- [ ] **Step 4: Run the migration test**

Run: `cd backend-java; .\mvnw.cmd -Dtest=FlywayMigrationTest test`  
Expected: PASS, one migration executed and required foreign keys found.

- [ ] **Step 5: Commit**

```powershell
git add backend-java/pom.xml backend-java/src/main/resources backend-java/src/test/java/com/simplemdm/migration
git commit -m "feat: add relational MDM Flyway baseline"
```

---

### Task 2: System, Department Tree, and User Identity

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/model/system/SystemEntity.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/system/Department.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/system/User.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/system/SystemRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/system/DepartmentRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/system/UserRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/system/DepartmentService.java`
- Test: `backend-java/src/test/java/com/simplemdm/service/system/DepartmentServiceTest.java`
- Test: `backend-java/src/test/java/com/simplemdm/repository/system/UserRepositoryTest.java`

**Interfaces:**
- Produces: `DepartmentService.create(Long systemId, Long parentId, String code, String name)` and `move(Long departmentId, Long newParentId)`.
- Produces: repositories using stable IDs and `findBySystemIdAndUsername`.

- [ ] **Step 1: Write failing department and user tests**

```java
@Test
void rejectsDepartmentCycleAndCrossSystemParent() {
    assertThatThrownBy(() -> service.move(rootId, childId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("閮ㄩ棬灞傜骇涓嶈兘褰㈡垚寰幆");
    assertThatThrownBy(() -> service.create(systemA, departmentInB, "OPS", "杩愯惀閮?))
        .hasMessageContaining("鐖堕儴闂ㄤ笉灞炰簬褰撳墠绯荤粺");
}

@Test
void userRequiresDepartmentFromSameSystem() {
    User user = User.create(systemA, departmentInB, "alice", hash, "Alice");
    assertThatThrownBy(() -> repository.saveAndFlush(user))
        .isInstanceOf(DataIntegrityViolationException.class);
}
```

- [ ] **Step 2: Run focused tests**

Run: `cd backend-java; .\mvnw.cmd -Dtest=DepartmentServiceTest,UserRepositoryTest test`  
Expected: FAIL because the new model and service do not exist.

- [ ] **Step 3: Implement system, department, and user aggregates**

Use `@ManyToOne(fetch = LAZY, optional = false)` for system and department. `DepartmentService` loads both system contexts before create/move, checks ancestry from `path`, and updates descendant paths in one `@Transactional` method. `User` stores only `department_id`, never a department-name shadow field.

- [ ] **Step 4: Run focused tests**

Run: `cd backend-java; .\mvnw.cmd -Dtest=DepartmentServiceTest,UserRepositoryTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/model/system backend-java/src/main/java/com/simplemdm/repository/system backend-java/src/main/java/com/simplemdm/service/system backend-java/src/test/java/com/simplemdm
git commit -m "feat: model systems departments and users"
```

---

### Task 3: RBAC and Department Scopes

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/model/system/Role.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/system/Permission.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/system/UserRole.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/system/RolePermission.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/system/UserDepartmentScope.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/system/AuthorizationService.java`
- Modify: `backend-java/src/main/java/com/simplemdm/security/JwtInterceptor.java`
- Modify: `backend-java/src/main/java/com/simplemdm/security/PermissionAspect.java`
- Test: `backend-java/src/test/java/com/simplemdm/service/system/AuthorizationServiceTest.java`

**Interfaces:**
- Consumes: authenticated `userId`, permission code, target `departmentId`.
- Produces: `boolean can(Long userId, String permissionCode, Long departmentId)` and `Set<Long> viewableDepartmentIds(Long userId)`.

- [ ] **Step 1: Write failing authorization tests**

```java
@Test
void combinesRoleActionWithSelfOrSubtreeScope() {
    assertThat(auth.can(editorId, "MDM_RECORD_EDIT", ownDepartmentId)).isTrue();
    assertThat(auth.can(editorId, "MDM_RECORD_EDIT", childDepartmentId)).isTrue();
    assertThat(auth.can(editorId, "MDM_RECORD_EDIT", unrelatedDepartmentId)).isFalse();
}

@Test
void systemAdminNeverCrossesSystemBoundary() {
    assertThat(auth.can(adminA, "MDM_RECORD_EDIT", departmentInB)).isFalse();
}
```

- [ ] **Step 2: Verify red**

Run: `cd backend-java; .\mvnw.cmd -Dtest=AuthorizationServiceTest test`  
Expected: FAIL because RBAC services do not exist.

- [ ] **Step 3: Implement RBAC and system-aware JWT context**

JWT claims contain `user_id` and `system_id`, not department names. `AuthorizationService` first verifies target department system, then action permission, then `SELF`/`SUBTREE` path membership. System admins bypass action/scope checks only after system equality passes.

- [ ] **Step 4: Verify green**

Run: `cd backend-java; .\mvnw.cmd -Dtest=AuthorizationServiceTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/model/system backend-java/src/main/java/com/simplemdm/service/system backend-java/src/main/java/com/simplemdm/security backend-java/src/test/java/com/simplemdm/service/system
git commit -m "feat: add system-aware RBAC scopes"
```

---

### Task 4: Generic Metadata and Typed Value Contract

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/FieldDataType.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/ObjectType.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/FieldDefinition.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/ChildType.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/ChildFieldDefinition.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/TypedValue.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/mdm/TypedValueConverter.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/mdm/MetadataService.java`
- Test: `backend-java/src/test/java/com/simplemdm/service/mdm/TypedValueConverterTest.java`
- Test: `backend-java/src/test/java/com/simplemdm/service/mdm/MetadataServiceTest.java`

**Interfaces:**
- Produces: `TypedValue convert(FieldDefinition field, Object rawValue)`.
- Produces: `FieldDefinition createField(Long objectTypeId, CreateFieldCommand command)`.

- [ ] **Step 1: Write failing typed-value and metadata tests**

```java
@Test
void convertsOnlyTheDeclaredType() {
    TypedValue value = converter.convert(decimalField, "123.45");
    assertThat(value.decimalValue()).isEqualByComparingTo("123.45");
    assertThat(value.nonNullValueCount()).isEqualTo(1);
}

@Test
void rejectsDuplicateFieldKeyWithinObject() {
    service.createField(personTypeId, command("employee_code", STRING));
    assertThatThrownBy(() -> service.createField(personTypeId, command("employee_code", STRING)))
        .hasMessageContaining("瀛楁鏍囪瘑宸插瓨鍦?);
}
```

- [ ] **Step 2: Verify red**

Run: `cd backend-java; .\mvnw.cmd -Dtest=TypedValueConverterTest,MetadataServiceTest test`  
Expected: FAIL.

- [ ] **Step 3: Implement metadata and typed conversion**

Use an immutable `TypedValue` record with exactly the migration's type columns. Conversion rejects blank required values, invalid date/decimal/boolean representations, precision overflow, and references whose object/system context does not match.

- [ ] **Step 4: Verify green**

Run: `cd backend-java; .\mvnw.cmd -Dtest=TypedValueConverterTest,MetadataServiceTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/model/mdm backend-java/src/main/java/com/simplemdm/service/mdm backend-java/src/test/java/com/simplemdm/service/mdm
git commit -m "feat: define generic MDM metadata and typed values"
```

---

### Task 5: Transactional Main and Child Records

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/MdmRecord.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/RecordValue.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/ChildRecord.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/mdm/ChildRecordValue.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/mdm/MdmRecordRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/mdm/RecordValueRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/repository/mdm/ChildRecordRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/mdm/RecordService.java`
- Test: `backend-java/src/test/java/com/simplemdm/service/mdm/RecordServiceTest.java`

**Interfaces:**
- Consumes: `CreateRecordCommand(systemId, objectTypeId, departmentId, recordCode, Map<String,Object> data)`.
- Produces: `RecordView create(CreateRecordCommand)` and `RecordView update(Long id, long version, Map<String,Object>)`.

- [ ] **Step 1: Write failing transaction tests**

```java
@Test
void persistsRecordAndOneTypedRowPerFieldAtomically() {
    RecordView created = service.create(validPersonCommand());
    assertThat(valueRepository.findByRecordId(created.id())).hasSize(3);
    assertThat(valueRepository.findByRecordId(created.id()))
        .allSatisfy(v -> assertThat(v.nonNullValueCount()).isEqualTo(1));
}

@Test
void rejectsDepartmentFromAnotherSystemWithoutPartialRows() {
    assertThatThrownBy(() -> service.create(crossSystemCommand()))
        .hasMessageContaining("閮ㄩ棬涓嶅睘浜庡綋鍓嶇郴缁?);
    assertThat(recordRepository.count()).isZero();
    assertThat(valueRepository.count()).isZero();
}
```

- [ ] **Step 2: Verify red**

Run: `cd backend-java; .\mvnw.cmd -Dtest=RecordServiceTest test`  
Expected: FAIL.

- [ ] **Step 3: Implement aggregate transactions**

`RecordService` checks authorization, metadata, department/system consistency, required and unique fields before saving. It writes the record and value rows in one transaction. Child records derive system and department from their parent and cannot override either.

- [ ] **Step 4: Verify green**

Run: `cd backend-java; .\mvnw.cmd -Dtest=RecordServiceTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/model/mdm backend-java/src/main/java/com/simplemdm/repository/mdm backend-java/src/main/java/com/simplemdm/service/mdm backend-java/src/test/java/com/simplemdm/service/mdm/RecordServiceTest.java
git commit -m "feat: persist relational master and child records"
```

---

### Task 6: Generic Metadata and Record HTTP APIs

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/controller/SystemController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/DepartmentController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/MdmMetadataController.java`
- Create: `backend-java/src/main/java/com/simplemdm/controller/MdmRecordController.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/mdm/CreateRecordRequest.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/mdm/RecordResponse.java`
- Test: `backend-java/src/test/java/com/simplemdm/controller/MdmRecordControllerTest.java`
- Test: `backend-java/src/test/java/com/simplemdm/dto/mdm/RecordResponseJsonTest.java`

**Interfaces:**
- Produces:
  - `GET /api/systems`
  - `GET /api/departments/tree`
  - `GET /api/mdm/object-types`
  - `GET/POST/PUT /api/mdm/object-types/{objectCode}/records`
  - `GET/POST/PUT /api/mdm/records/{recordId}/children/{childCode}`

- [ ] **Step 1: Write failing API contract tests**

```java
mockMvc.perform(post("/api/mdm/object-types/person/records")
    .contentType(APPLICATION_JSON)
    .content("""
      {"department_id":10,"record_code":"EMP001",
       "data":{"employee_name":"寮犱笁","hire_date":"2026-07-31"}}
      """))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.department_id").value(10))
    .andExpect(jsonPath("$.data.data.employee_name").value("寮犱笁"));
```

- [ ] **Step 2: Verify red**

Run: `cd backend-java; .\mvnw.cmd -Dtest=MdmRecordControllerTest,RecordResponseJsonTest test`  
Expected: FAIL because generic endpoints do not exist.

- [ ] **Step 3: Implement snake_case endpoints and batch assembly**

Controllers take system context from JWT, never from an untrusted request field. Record reads load definitions and values in batches, apply department/shared projection, and return `{id, object_type, department_id, record_code, status, version, data}`.

- [ ] **Step 4: Verify green**

Run: `cd backend-java; .\mvnw.cmd -Dtest=MdmRecordControllerTest,RecordResponseJsonTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/controller backend-java/src/main/java/com/simplemdm/dto/mdm backend-java/src/test/java/com/simplemdm/controller backend-java/src/test/java/com/simplemdm/dto/mdm
git commit -m "feat: expose generic MDM APIs"
```

---

### Task 7: Relational Approval and Integration

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/model/workflow/ApprovalRequest.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/workflow/ApprovalChange.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/workflow/ApprovalAction.java`
- Create: `backend-java/src/main/java/com/simplemdm/model/workflow/ApproverAssignment.java`
- Replace: `backend-java/src/main/java/com/simplemdm/service/ApprovalService.java`
- Replace: `backend-java/src/main/java/com/simplemdm/service/PushService.java`
- Test: `backend-java/src/test/java/com/simplemdm/service/workflow/ApprovalServiceTest.java`
- Test: `backend-java/src/test/java/com/simplemdm/service/integration/PushServiceTest.java`

**Interfaces:**
- Produces: `Long submit(UpdateRecordCommand, Long applicantId)`.
- Produces: `RecordView approve(Long requestId, Long approverId, long expectedRecordVersion)`.
- Produces: `void publishRecordChanged(Long recordId)`.

- [ ] **Step 1: Write failing workflow tests**

```java
@Test
void approvalStoresRelationalFieldChangesAndRejectsStaleVersion() {
    Long requestId = service.submit(changeSalaryCommand(), applicantId);
    assertThat(changeRepository.findByRequestId(requestId))
        .extracting(ApprovalChange::getFieldDefinitionId)
        .containsExactly(salaryFieldId);
    recordRepository.incrementVersion(recordId);
    assertThatThrownBy(() -> service.approve(requestId, approverId, originalVersion))
        .hasMessageContaining("涓绘暟鎹凡鍙戠敓鍙樺寲");
}
```

- [ ] **Step 2: Verify red**

Run: `cd backend-java; .\mvnw.cmd -Dtest=ApprovalServiceTest,PushServiceTest test`  
Expected: FAIL.

- [ ] **Step 3: Implement relational approval and subscriptions**

Store one `ApprovalChange` per changed field using old/new typed columns. Approval verifies assignment, permission, target system, and optimistic version before invoking `RecordService`. Push subscriptions bind to system/object type and logs reference stable IDs while storing only a bounded request snapshot.

- [ ] **Step 4: Verify green**

Run: `cd backend-java; .\mvnw.cmd -Dtest=ApprovalServiceTest,PushServiceTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/model/workflow backend-java/src/main/java/com/simplemdm/service backend-java/src/test/java/com/simplemdm/service
git commit -m "feat: rebuild approval and integration flows"
```

---

### Task 8: Controlled Bootstrap Data

**Files:**
- Replace: `backend-java/src/main/java/com/simplemdm/config/DataInitializer.java`
- Modify: `backend-java/src/main/resources/application.yml`
- Test: `backend-java/src/test/java/com/simplemdm/config/DataInitializerTest.java`

**Interfaces:**
- Produces: idempotent initial system, department tree, administrator, RBAC, person object, fields, and child type.
- Consumes: `app.bootstrap.enabled`, default `true` only for local/demo profile.

- [ ] **Step 1: Write failing idempotency and invariants test**

```java
@Test
void bootstrapIsIdempotentAndCreatesNoNullDepartments() {
    initializer.run();
    initializer.run();
    assertThat(systemRepository.count()).isEqualTo(1);
    assertThat(userRepository.findAll()).allMatch(u -> u.getDepartment() != null);
    assertThat(recordRepository.findAll()).allMatch(r -> r.getDepartment() != null);
    assertThat(objectTypeRepository.findBySystemIdAndCode(systemId, "person")).isPresent();
}
```

- [ ] **Step 2: Verify red**

Run: `cd backend-java; .\mvnw.cmd -Dtest=DataInitializerTest test`  
Expected: FAIL against the old personnel/JSON initializer.

- [ ] **Step 3: Implement repository-driven bootstrap**

Create by stable codes, not fixed IDs. Seed password `123456` only for documented demo accounts and hash it with BCrypt. Do not reset or delete existing data on application restart.

- [ ] **Step 4: Verify green**

Run: `cd backend-java; .\mvnw.cmd -Dtest=DataInitializerTest test`  
Expected: PASS twice in the same database.

- [ ] **Step 5: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/config/DataInitializer.java backend-java/src/main/resources/application.yml backend-java/src/test/java/com/simplemdm/config/DataInitializerTest.java
git commit -m "feat: seed relational MDM bootstrap data"
```

---

### Task 9: Generic Vue MDM Workspace

**Files:**
- Create: `frontend/src/api/systems.js`
- Create: `frontend/src/api/departments.js`
- Create: `frontend/src/api/mdm.js`
- Create: `frontend/src/stores/context.js`
- Create: `frontend/src/components/mdm/TypedFieldInput.vue`
- Create: `frontend/src/components/mdm/TypedFieldValue.vue`
- Create: `frontend/src/views/mdm/List.vue`
- Create: `frontend/src/views/mdm/Form.vue`
- Create: `frontend/src/views/mdm/MetadataManager.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/layout/MainLayout.vue`
- Test: `frontend/src/stores/context.spec.js`
- Test: `frontend/src/views/mdm/List.spec.js`
- Test: `frontend/src/views/mdm/Form.spec.js`

**Interfaces:**
- Consumes generic APIs from Task 6.
- Produces URL context `?system=<code>&object=<code>&department=<id>` and snake_case record payloads.

- [ ] **Step 1: Write failing context and view tests**

```javascript
it('defaults to the user system and primary department', async () => {
  const store = useContextStore()
  await store.initialize({ system_id: 1, department_id: 10 })
  expect(store.systemId).toBe(1)
  expect(store.departmentId).toBe(10)
})

it('never sends a department name as a relationship key', async () => {
  await wrapper.get('[data-test="save"]').trigger('click')
  expect(createRecord).toHaveBeenCalledWith('person', expect.objectContaining({
    department_id: 10
  }))
  expect(createRecord.mock.calls[0][1]).not.toHaveProperty('owner_dept')
})
```

- [ ] **Step 2: Verify red**

Run: `cd frontend; npm test -- context.spec.js List.spec.js Form.spec.js`  
Expected: FAIL because generic store/views do not exist.

- [ ] **Step 3: Implement generic UI**

Render inputs from `data_type`; keep selected department ID in router query; show department names only as labels. Hide mutations when `can_edit` is false. Use metadata definitions to serialize dates, decimals, booleans, and references without JSON persistence assumptions.

- [ ] **Step 4: Verify green and build**

Run: `cd frontend; npm test; npm run build`  
Expected: all tests PASS and production build exits 0.

- [ ] **Step 5: Commit**

```powershell
git add frontend/src
git commit -m "feat: add generic relational MDM workspace"
```

---

### Task 10: Remove Personnel-Specific Java and Frontend Paths

**Files:**
- Delete: `backend-java/src/main/java/com/simplemdm/model/MdmPersonnel.java`
- Delete: `backend-java/src/main/java/com/simplemdm/model/MdmPersonnelSub.java`
- Delete: `backend-java/src/main/java/com/simplemdm/model/MdmFieldDefinition.java`
- Delete: matching old personnel repositories, services, controllers, DTOs, and obsolete tests.
- Delete: `frontend/src/views/personnel/`
- Delete: `frontend/src/views/dept-fields/`
- Delete: `frontend/src/api/personnel.js`
- Delete: `frontend/src/api/personnelSub.js`
- Delete: `frontend/src/api/deptFields.js`
- Modify: `README.md`

**Interfaces:**
- Consumes: generic replacements from Tasks 4鈥?.
- Produces: no runtime reference to old personnel entities, `owner_dept`, or JSON dynamic columns.

- [ ] **Step 1: Add an architecture guard test**

```java
@Test
void noLegacyPersonnelPersistenceClassesRemain() {
    assertThat(classNamesUnder("com.simplemdm"))
        .noneMatch(name -> name.matches(".*(MdmPersonnel|PersonnelSubService|DynamicFieldService).*"));
}
```

- [ ] **Step 2: Run the guard test and verify red**

Run: `cd backend-java; .\mvnw.cmd -Dtest=ArchitectureGuardTest test`  
Expected: FAIL listing legacy classes.

- [ ] **Step 3: Delete legacy paths and update documentation**

Remove only files replaced by the generic APIs/UI. Update README startup instructions to require `SIMPLE_MDM_DB_PASSWORD` and `SIMPLE_MDM_JWT_SECRET`, document Flyway, and remove Python/JSON/personnel-specific architecture references.

- [ ] **Step 4: Run all non-database verification**

Run: `cd backend-java; .\mvnw.cmd test`  
Run: `cd frontend; npm test; npm run build`  
Expected: all tests PASS and build exits 0.

- [ ] **Step 5: Commit**

```powershell
git add backend-java frontend README.md
git commit -m "refactor: remove legacy personnel persistence"
```

---

### Task 11: Destructive Local Database Rebuild and Acceptance

**Files:**
- Create: `docs/superpowers/verification/2026-07-31-relational-mdm-rebuild-acceptance.md`
- Modify only if verification exposes a tested defect: files owned by Tasks 1鈥?0.

**Interfaces:**
- Consumes: local MySQL `127.0.0.1:3306`, database `simple_mdm`, password supplied through a process-scoped environment variable.
- Produces: clean migrated database and recorded acceptance evidence.

- [ ] **Step 1: Verify no application process is writing**

Run:

```powershell
Get-NetTCPConnection -LocalPort 18001,5173 -ErrorAction SilentlyContinue |
  Select-Object LocalPort,State,OwningProcess
```

Expected: no listening backend or frontend process. Stop only the exact owning processes if present.

- [ ] **Step 2: Run the destructive rebuild authorized by the user**

Prompt for a process-scoped `MYSQL_PWD` value without printing or persisting it:

```powershell
$env:MYSQL_PWD = Read-Host 'Local MySQL root password'
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u root -e `
  "DROP DATABASE IF EXISTS simple_mdm; CREATE DATABASE simple_mdm CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
Remove-Item Env:\MYSQL_PWD
```

Expected: exit code 0. This is performed only now, after the migration and tests exist.

- [ ] **Step 3: Start the Java application with environment-only secrets**

```powershell
$env:SIMPLE_MDM_DB_PASSWORD = Read-Host 'Local MySQL application password'
$secretBytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
$env:SIMPLE_MDM_JWT_SECRET = [Convert]::ToBase64String($secretBytes)
cd backend-java
.\mvnw.cmd spring-boot:run
```

Expected log: Flyway applies V1, Hibernate validation passes, application listens on `18001`, and bootstrap completes once.

- [ ] **Step 4: Verify database invariants**

Run read-only SQL proving:

```sql
SELECT COUNT(*) FROM sys_user WHERE department_id IS NULL;
SELECT COUNT(*) FROM mdm_record WHERE department_id IS NULL;
SELECT COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='simple_mdm'
  AND COLUMN_NAME IN ('owner_dept', 'data_json', 'system_code');
SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;
```

Expected results: `0`, `0`, `0`, and `1`.

- [ ] **Step 5: Run final automated verification**

Run:

```powershell
cd backend-java
.\mvnw.cmd test
cd ..\frontend
npm test
npm run build
```

Expected: backend tests PASS, frontend tests PASS, production build exits 0.

- [ ] **Step 6: Record exact evidence and commit**

The acceptance document records command, exit code, test counts, migration version, invariant query results, and service ports. It must not include passwords, JWT secrets, or authentication headers.

```powershell
git add docs/superpowers/verification/2026-07-31-relational-mdm-rebuild-acceptance.md
git commit -m "test: verify relational MDM rebuild"
```

---

### Task 12: Java-Only Publication Sync

**Files:**
- Synchronize only: `backend-java/`, `frontend/`, `README.md`
- Exclude: `.superpowers/`, `.worktrees/`, `backend/`, local configuration secrets, database files, internal session documents.

**Interfaces:**
- Consumes: verified feature branch output.
- Produces: a clean Java-only publication commit derived from `github-java-only`.

- [ ] **Step 1: Verify the feature branch**

Run: `git status --short; git diff --check; git log -1 --oneline`  
Expected: only ignored/untracked visual-companion state, no code changes.

- [ ] **Step 2: Update the isolated publication worktree**

Copy or cherry-pick only Java/frontend-safe changes into `C:\tmp\simple-mdm-java-publish`. Verify:

```powershell
git status --short
git diff --check
git ls-files backend
```

Expected: `git ls-files backend` returns no Python path.

- [ ] **Step 3: Re-run publication verification**

Run: `cd backend-java; .\mvnw.cmd test`  
Run: `cd ..\frontend; npm test; npm run build`  
Expected: all verification passes.

- [ ] **Step 4: Commit and push the existing publication branch**

```powershell
git add backend-java frontend README.md
git commit -m "feat: rebuild SimpleMDM on relational metadata"
git push origin publish/dynamic-master-sub-fields
```

Expected: push succeeds without exposing Python history or secrets.


