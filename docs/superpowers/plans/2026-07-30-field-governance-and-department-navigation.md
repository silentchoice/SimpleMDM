# Field Governance and Department Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce system-wide field-key isolation, field-level sub-table sharing, transactional sub-field deletion, refreshed demo data, and a mandatory department-scoped master-data UI.

**Architecture:** Move field creation, sharing, and deletion rules into a transactional `FieldDefinitionService`, backed by a database unique constraint on `(system_code, field_key)`. Keep master data globally visible within authorized departments, filter sub-table definitions and JSON values in the backend according to field-level sharing, and make the selected department an explicit URL-backed frontend context.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, MySQL 8, JUnit 5, Mockito, Vue 3, Pinia, Vue Router 4, Element Plus, Vitest.

## Global Constraints

- Within one `system_code`, every `field_key` is unique across the master table, every sub-table group, and every department.
- Different systems may reuse the same `field_key`.
- `shared` exists only as a meaningful option for `table_type=sub`; master fields always use `shared=false`.
- Record-level `visibility` is no longer read, written, displayed, or submitted.
- Master fields and system fields cannot be deleted.
- A main administrator (`is_admin=true`) cannot delete fields.
- Only a non-admin user with `EDIT` permission for their own department may delete that department's non-system sub-table field.
- Field deletion permanently removes the matching key from all affected historical sub-table JSON records in the same transaction.
- The department master-data list always has one selected department and never offers “all departments”.
- Users may edit only their own department; other viewable departments are read-only.
- Master fields and values are visible for every authorized department; other departments expose only `shared=true` sub-fields and their values.
- Preserve the unrelated untracked snake-case configuration and regression test already present in the worktree; include them only in the task that formalizes configuration.

---

## File Map

**Backend domain and persistence**

- `backend-java/src/main/java/com/simplemdm/model/MdmFieldDefinition.java`: add `shared` and the system-wide unique table constraint.
- `backend-java/src/main/java/com/simplemdm/repository/MdmFieldDefinitionRepository.java`: global key lookup and scoped definition queries.
- `backend-java/src/main/java/com/simplemdm/repository/MdmPersonnelSubRepository.java`: load records affected by deletion and personnel detail.
- `backend-java/src/main/java/com/simplemdm/service/FieldDefinitionService.java`: create/update/delete rules and transactional JSON cleanup.
- `backend-java/src/main/java/com/simplemdm/service/DynamicFieldService.java`: validate against the new field lookup contract and expose visible definitions.
- `backend-java/src/main/java/com/simplemdm/service/PersonnelSubService.java`: field-level sharing and JSON projection.
- `backend-java/src/main/java/com/simplemdm/service/PersonnelService.java`: mandatory department authorization and own-department mutations.
- `backend-java/src/main/java/com/simplemdm/controller/FieldDefinitionController.java`: delegate field mutations to the service.
- `backend-java/src/main/java/com/simplemdm/controller/PersonnelController.java`: require department queries and authorize detail access.
- `backend-java/src/main/java/com/simplemdm/dto/PersonnelSubDTO.java`: remove record-level visibility.
- `backend-java/src/main/java/com/simplemdm/config/DataInitializer.java`: deterministic business-data reset and non-overlapping demo fields.
- `backend-java/src/main/resources/application.properties`: snake-case binding and persistent schema update settings.
- `backend-java/src/main/resources/application-default.properties`: remove after consolidating its setting.

**Frontend**

- `frontend/src/api/deptFields.js`: delete-field request.
- `frontend/src/api/personnel.js`: mandatory department query contract.
- `frontend/src/views/dept-fields/Manager.vue`: sub-field sharing toggle and authorized delete UI.
- `frontend/src/views/personnel/List.vue`: mandatory URL-backed department context.
- `frontend/src/views/personnel/Form.vue`: own-department editing, source-department navigation, and removal of record visibility.
- `frontend/src/stores/user.js`: explicit own-department edit helpers that do not grant edit access to main administrators.

**Tests**

- `backend-java/src/test/java/com/simplemdm/repository/MdmFieldDefinitionRepositoryTest.java`
- `backend-java/src/test/java/com/simplemdm/service/FieldDefinitionServiceTest.java`
- `backend-java/src/test/java/com/simplemdm/service/PersonnelSubServiceTest.java`
- `backend-java/src/test/java/com/simplemdm/controller/PersonnelControllerTest.java`
- `backend-java/src/test/java/com/simplemdm/config/DataInitializerTest.java`
- `backend-java/src/test/java/com/simplemdm/dto/DynamicPersonnelDTOJsonTest.java`
- `frontend/src/views/dept-fields/Manager.spec.js`
- `frontend/src/views/personnel/List.spec.js`
- `frontend/src/views/personnel/Form.spec.js`

---

### Task 1: Consolidate Configuration and Enforce Field-Key Persistence Constraints

**Files:**
- Modify: `backend-java/src/main/resources/application.properties`
- Delete: `backend-java/src/main/resources/application-default.properties`
- Modify: `backend-java/src/main/java/com/simplemdm/model/MdmFieldDefinition.java`
- Modify: `backend-java/src/main/java/com/simplemdm/repository/MdmFieldDefinitionRepository.java`
- Create: `backend-java/src/test/java/com/simplemdm/repository/MdmFieldDefinitionRepositoryTest.java`
- Keep: `backend-java/src/test/java/com/simplemdm/dto/DynamicPersonnelDTOJsonTest.java`

**Interfaces:**
- Produces: `Optional<MdmFieldDefinition> findBySystemCodeAndFieldKey(String systemCode, String fieldKey)`.
- Produces: database uniqueness for `(system_code, field_key)`.
- Produces: `Boolean shared` with getter/setter on `MdmFieldDefinition`.

- [ ] **Step 1: Write repository tests for uniqueness and cross-system reuse**

Create a `@DataJpaTest` using H2:

```java
@DataJpaTest
class MdmFieldDefinitionRepositoryTest {
    @Autowired MdmFieldDefinitionRepository repository;

    @Test
    void rejectsSameFieldKeyAcrossMasterAndSubWithinOneSystem() {
        repository.saveAndFlush(field("HR", "ALL", "master", "basic", "employee_code"));
        assertThrows(DataIntegrityViolationException.class, () ->
            repository.saveAndFlush(field("HR", "工程部", "sub", "project", "employee_code")));
    }

    @Test
    void permitsSameFieldKeyInDifferentSystems() {
        repository.saveAndFlush(field("HR", "ALL", "master", "basic", "employee_code"));
        repository.saveAndFlush(field("FIN", "ALL", "master", "basic", "employee_code"));
        assertEquals(2, repository.count());
    }
}
```

The local `field(...)` fixture must set every non-null entity property, including `shared=false` and `systemField=false`.

- [ ] **Step 2: Run the repository test and verify RED**

Run:

```powershell
cd backend-java
.\mvnw.cmd -Dtest=MdmFieldDefinitionRepositoryTest test
```

Expected: the first test fails because the current table has no `(system_code, field_key)` unique constraint.

- [ ] **Step 3: Add the entity constraint, shared field, and repository lookup**

Use:

```java
@Table(
    name = "mdm_field_definition",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_field_definition_system_key",
        columnNames = {"system_code", "field_key"}
    )
)
```

Add:

```java
@Column(nullable = false)
private Boolean shared = false;

public Boolean getShared() { return shared; }
public void setShared(Boolean shared) { this.shared = shared; }
```

Add to the repository:

```java
Optional<MdmFieldDefinition> findBySystemCodeAndFieldKey(String systemCode, String fieldKey);
```

- [ ] **Step 4: Consolidate runtime properties**

Make `application.properties` contain exactly the two overrides needed by the confirmed design:

```properties
spring.jackson.property-naming-strategy=SNAKE_CASE
spring.jpa.hibernate.ddl-auto=update
```

Delete `application-default.properties` so the effective setting is not profile-dependent. Preserve `DynamicPersonnelDTOJsonTest`.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=MdmFieldDefinitionRepositoryTest,DynamicPersonnelDTOJsonTest test
```

Expected: both repository tests and the snake-case JSON test pass.

- [ ] **Step 6: Commit**

```powershell
git add backend-java/src/main/resources/application.properties backend-java/src/main/resources/application-default.properties backend-java/src/main/java/com/simplemdm/model/MdmFieldDefinition.java backend-java/src/main/java/com/simplemdm/repository/MdmFieldDefinitionRepository.java backend-java/src/test/java/com/simplemdm/repository/MdmFieldDefinitionRepositoryTest.java backend-java/src/test/java/com/simplemdm/dto/DynamicPersonnelDTOJsonTest.java
git commit -m "feat: enforce system-wide dynamic field keys"
```

---

### Task 2: Add Transactional Field Creation, Sharing, and Deletion Service

**Files:**
- Create: `backend-java/src/main/java/com/simplemdm/service/FieldDefinitionService.java`
- Modify: `backend-java/src/main/java/com/simplemdm/controller/FieldDefinitionController.java`
- Modify: `backend-java/src/main/java/com/simplemdm/repository/MdmPersonnelSubRepository.java`
- Create: `backend-java/src/test/java/com/simplemdm/service/FieldDefinitionServiceTest.java`
- Modify: `backend-java/src/test/java/com/simplemdm/controller/FieldDefinitionControllerTest.java`

**Interfaces:**
- Consumes: `findBySystemCodeAndFieldKey(...)` from Task 1.
- Produces: `MdmFieldDefinition create(Map<String,Object> body, SysUser user, String systemCode)`.
- Produces: `MdmFieldDefinition update(Long id, Map<String,Object> body, SysUser user)`.
- Produces: `void deleteSubField(Long id, SysUser user)`.

- [ ] **Step 1: Write failing service tests for global conflict and shared rules**

Test exact behavior:

```java
@Test
void createReportsExistingMasterFieldWhenSubKeyConflicts() {
    MdmFieldDefinition conflict = field("ALL", "master", "basic", "employee_code");
    when(fields.findBySystemCodeAndFieldKey("HR", "employee_code"))
        .thenReturn(Optional.of(conflict));

    BusinessException error = assertThrows(BusinessException.class,
        () -> service.create(subBody("employee_code", true), departmentAdmin(), "HR"));

    assertEquals(400, error.getCode());
    assertEquals("字段标识 employee_code 已被主表使用", error.getMessage());
}

@Test
void createRejectsSharedMasterField() {
    BusinessException error = assertThrows(BusinessException.class,
        () -> service.create(masterBody("new_master_key", true), departmentAdmin(), "HR"));
    assertEquals("主表字段不能设置共享", error.getMessage());
}
```

- [ ] **Step 2: Write failing deletion tests**

Cover all permission branches and data cleanup:

```java
@Test
void deleteSubFieldRemovesDefinitionAndHistoricalJsonValues() {
    MdmFieldDefinition definition =
        field("工程部", "sub", "salary", "engineering_base_pay");
    MdmPersonnelSub first = record("{\"engineering_base_pay\":25000,\"engineering_bonus\":5000}");
    MdmPersonnelSub second = record("{\"engineering_base_pay\":30000}");
    when(fields.findById(9L)).thenReturn(Optional.of(definition));
    when(permissions.getEditableDepts(7L)).thenReturn(List.of("工程部"));
    when(records.findByOwnerDeptAndSubType("工程部", "salary"))
        .thenReturn(List.of(first, second));

    service.deleteSubField(9L, departmentAdmin());

    assertEquals("{\"engineering_bonus\":5000}", first.getDataJson());
    assertEquals("{}", second.getDataJson());
    verify(records).saveAll(List.of(first, second));
    verify(fields).delete(definition);
}
```

Separate tests must assert the exact messages for master fields, system fields, `isAdmin=true`, another department, and no own-department `EDIT` permission. Add a malformed JSON test that expects an exception before `fields.delete(...)`.

- [ ] **Step 3: Run service tests and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=FieldDefinitionServiceTest test
```

Expected: compilation fails because `FieldDefinitionService` does not exist.

- [ ] **Step 4: Implement the service**

Annotate deletion with `@Transactional`. Use injected `ObjectMapper`; do not construct a private mapper. Permission order must be deterministic:

```java
if ("master".equals(definition.getTableType()))
    throw new BusinessException(403, "主表字段不可删除");
if (Boolean.TRUE.equals(definition.getSystemField()))
    throw new BusinessException(403, "系统字段不可删除");
if (Boolean.TRUE.equals(user.getIsAdmin()))
    throw new BusinessException(403, "主管理员无字段删除权限");
if (!Objects.equals(user.getDepartment(), definition.getDepartment()))
    throw new BusinessException(403, "只能删除本部门子表字段");
List<String> editable = permissionService.getEditableDepts(user.getId());
if (editable == null || !editable.contains(user.getDepartment()))
    throw new BusinessException(403, "无本部门字段删除权限");
```

For cleanup, parse each record to `LinkedHashMap<String,Object>`, remove the key, serialize it, call `saveAll`, then delete the definition. A parse error must throw `BusinessException(500, "历史子表数据无法清理")`.

- [ ] **Step 5: Delegate controller mutations**

Inject `FieldDefinitionService` into `FieldDefinitionController`. Keep list/read mapping in the controller for now. Replace create/update/delete bodies with service calls and response mapping:

```java
@DeleteMapping("/{id}")
public ApiResponse delete(@PathVariable Long id) {
    fieldDefinitionService.deleteSubField(id, JwtInterceptor.CURRENT_USER.get());
    return ApiResponse.ok("字段及历史数据已删除", null);
}
```

Update controller tests to mock the service and verify delegation and response codes; business-rule tests remain in the service test.

- [ ] **Step 6: Run focused backend tests**

```powershell
.\mvnw.cmd -Dtest=FieldDefinitionServiceTest,FieldDefinitionControllerTest test
```

Expected: all tests pass with zero failures.

- [ ] **Step 7: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/service/FieldDefinitionService.java backend-java/src/main/java/com/simplemdm/controller/FieldDefinitionController.java backend-java/src/main/java/com/simplemdm/repository/MdmPersonnelSubRepository.java backend-java/src/test/java/com/simplemdm/service/FieldDefinitionServiceTest.java backend-java/src/test/java/com/simplemdm/controller/FieldDefinitionControllerTest.java
git commit -m "feat: add governed dynamic field deletion"
```

---

### Task 3: Replace Record Visibility with Field-Level Sub-Table Sharing

**Files:**
- Modify: `backend-java/src/main/java/com/simplemdm/dto/PersonnelSubDTO.java`
- Modify: `backend-java/src/main/java/com/simplemdm/service/DynamicFieldService.java`
- Modify: `backend-java/src/main/java/com/simplemdm/service/PersonnelSubService.java`
- Modify: `backend-java/src/test/java/com/simplemdm/service/DynamicFieldServiceTest.java`
- Modify: `backend-java/src/test/java/com/simplemdm/service/PersonnelSubServiceTest.java`

**Interfaces:**
- Produces: `List<MdmFieldDefinition> visibleSubDefinitions(String systemCode, String ownerDepartment, String viewerDepartment, String subType)`.
- Produces: sub-record maps whose `data` contains only keys visible to the viewer.

- [ ] **Step 1: Write failing visibility projection tests**

Extend `PersonnelSubServiceTest`:

```java
@Test
void anotherDepartmentReceivesOnlySharedSubFields() {
    MdmPersonnel parent = parent();
    MdmPersonnelSub record = record(
        "{\"engineering_project_name\":\"工厂平台\",\"engineering_internal_cost\":9000}");
    editor.setDepartment("人力资源部");
    when(personnelRepository.findById(1L)).thenReturn(Optional.of(parent));
    when(subRepository.findByPersonnelId(1L)).thenReturn(List.of(record));
    when(fields.visibleSubDefinitions("HR", "工程部", "人力资源部", "project"))
        .thenReturn(List.of(field("engineering_project_name", true)));

    List<Map<String,Object>> result = service.list(1L, editor);

    assertEquals(Map.of("engineering_project_name", "工厂平台"), result.get(0).get("data"));
    assertFalse(result.get(0).containsKey("visibility"));
}
```

Add tests for owner department receiving all fields and a foreign viewer receiving no record/group when no shared fields exist.

- [ ] **Step 2: Run tests and verify RED**

```powershell
.\mvnw.cmd -Dtest=PersonnelSubServiceTest,DynamicFieldServiceTest test
```

Expected: tests fail because list still filters by record visibility and returns the full JSON.

- [ ] **Step 3: Implement visible definition lookup**

In `DynamicFieldService`:

```java
public List<MdmFieldDefinition> visibleSubDefinitions(
        String systemCode, String ownerDepartment, String viewerDepartment, String subType) {
    List<MdmFieldDefinition> definitions =
        fieldRepository.findBySystemCodeAndDepartmentAndTableTypeAndSubTypeOrderBySortOrderAsc(
            systemCode, ownerDepartment, "sub", subType);
    if (Objects.equals(ownerDepartment, viewerDepartment)) return definitions;
    return definitions.stream().filter(f -> Boolean.TRUE.equals(f.getShared())).toList();
}
```

- [ ] **Step 4: Project record JSON and remove visibility DTO behavior**

In `PersonnelSubService.list`, stop filtering on `record.visibility`. For each record, compute visible definitions, skip it when definitions are empty for a foreign viewer, and retain only visible keys in insertion order.

Remove `visibility` from `PersonnelSubDTO`, stop setting/updating it, and omit it from `toMap`. The physical entity column remains untouched.

- [ ] **Step 5: Run focused tests**

```powershell
.\mvnw.cmd -Dtest=PersonnelSubServiceTest,DynamicFieldServiceTest test
```

Expected: owner, foreign-viewer, and empty-shared-group tests pass.

- [ ] **Step 6: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/dto/PersonnelSubDTO.java backend-java/src/main/java/com/simplemdm/service/DynamicFieldService.java backend-java/src/main/java/com/simplemdm/service/PersonnelSubService.java backend-java/src/test/java/com/simplemdm/service/DynamicFieldServiceTest.java backend-java/src/test/java/com/simplemdm/service/PersonnelSubServiceTest.java
git commit -m "feat: share sub data by field definition"
```

---

### Task 4: Require Department-Scoped Master Data and Secure Detail Access

**Files:**
- Modify: `backend-java/src/main/java/com/simplemdm/service/PermissionService.java`
- Modify: `backend-java/src/main/java/com/simplemdm/service/PersonnelService.java`
- Modify: `backend-java/src/main/java/com/simplemdm/controller/PersonnelController.java`
- Create: `backend-java/src/test/java/com/simplemdm/controller/PersonnelControllerTest.java`
- Modify: `backend-java/src/test/java/com/simplemdm/service/DynamicPersonnelServiceTest.java`

**Interfaces:**
- Produces: `List<String> getConcreteViewableDepts(Long userId, String systemCode)`.
- Produces: `MdmPersonnel requireViewablePersonnel(Long id, SysUser user)`.
- Changes: list endpoint requires nonblank `department`.

- [ ] **Step 1: Write failing controller authorization tests**

Use mocked services and `JwtInterceptor.CURRENT_USER`:

```java
@Test
void listRejectsMissingDepartment() {
    ApiResponse response = controller.list("", "", 1, 10);
    assertEquals(400, response.getCode());
    assertEquals("必须选择部门", response.getMessage());
    verify(personnelService, never()).listPersonnel(any(), any(), anyInt(), anyInt(), any(), any());
}

@Test
void listRejectsDepartmentWithoutViewPermission() {
    when(permissionService.getConcreteViewableDepts(7L, "HR"))
        .thenReturn(List.of("工程部", "产品部"));
    ApiResponse response = controller.list("", "市场部", 1, 10);
    assertEquals(403, response.getCode());
}

@Test
void detailRejectsDirectIdAccessToHiddenDepartment() {
    when(personnelService.getPersonnel(9L)).thenReturn(personnel("市场部"));
    when(permissionService.getConcreteViewableDepts(7L, "HR"))
        .thenReturn(List.of("工程部"));
    ApiResponse response = controller.get(9L);
    assertEquals(403, response.getCode());
}
```

- [ ] **Step 2: Run test and verify RED**

```powershell
.\mvnw.cmd -Dtest=PersonnelControllerTest test
```

Expected: tests fail because empty department currently means all and detail has no permission check.

- [ ] **Step 3: Add concrete viewable department resolution**

`VIEW ALL` currently returns `null`. Convert it to concrete departments using the current system's configured/personnel departments:

```java
public List<String> getConcreteViewableDepts(Long userId, String systemCode) {
    List<String> scoped = getViewableDepts(userId);
    if (scoped != null) return scoped.stream().distinct().sorted().toList();
    LinkedHashSet<String> all = new LinkedHashSet<>(
        fieldRepository.findDistinctDepartmentsBySystemCode(systemCode));
    all.remove("ALL");
    all.addAll(personnelRepository.findDistinctOwnerDepartmentsBySystemCode(systemCode));
    return all.stream().sorted().toList();
}
```

Inject the two repositories needed by `PermissionService`, and add `findDistinctOwnerDepartmentsBySystemCode`.

- [ ] **Step 4: Enforce list/detail and own-department mutations**

In `PersonnelController`:

- Return 400 for blank department.
- Return 403 unless department is in `getConcreteViewableDepts`.
- Always pass exactly the selected department to `PersonnelService`.
- On detail, load the record, then authorize its real `ownerDept`.
- On create/update, require both DTO department and existing department to equal `user.department`.

Use exact message `"只能维护所属部门主数据"` for cross-department mutations.

- [ ] **Step 5: Run focused controller and service tests**

```powershell
.\mvnw.cmd -Dtest=PersonnelControllerTest,DynamicPersonnelServiceTest test
```

Expected: mandatory department, direct-ID denial, and own-department mutation tests pass.

- [ ] **Step 6: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/service/PermissionService.java backend-java/src/main/java/com/simplemdm/service/PersonnelService.java backend-java/src/main/java/com/simplemdm/controller/PersonnelController.java backend-java/src/main/java/com/simplemdm/repository/MdmPersonnelRepository.java backend-java/src/test/java/com/simplemdm/controller/PersonnelControllerTest.java backend-java/src/test/java/com/simplemdm/service/DynamicPersonnelServiceTest.java
git commit -m "feat: scope master data by selected department"
```

---

### Task 5: Rebuild Deterministic Demo Business Data with Unique Keys

**Files:**
- Modify: `backend-java/src/main/java/com/simplemdm/config/DataInitializer.java`
- Create: `backend-java/src/test/java/com/simplemdm/config/DataInitializerTest.java`

**Interfaces:**
- Produces: repeatable `resetBusinessDemoData()` invoked during initialization.
- Produces: demo definitions with no duplicate `(system_code, field_key)`.

- [ ] **Step 1: Write failing demo-data consistency test**

Extract the demo field definitions into a package-visible immutable specification method and test literals:

```java
@Test
void demoFieldKeysAreUniqueWithinSystem() {
    List<DataInitializer.DemoField> fields = DataInitializer.demoFields();
    long unique = fields.stream()
        .map(field -> field.systemCode() + ":" + field.fieldKey())
        .distinct()
        .count();
    assertEquals(fields.size(), unique);
}

@Test
void onlySubFieldsCanBeShared() {
    assertTrue(DataInitializer.demoFields().stream()
        .filter(DataInitializer.DemoField::shared)
        .allMatch(field -> "sub".equals(field.tableType())));
}
```

- [ ] **Step 2: Run test and verify RED**

```powershell
.\mvnw.cmd -Dtest=DataInitializerTest test
```

Expected: compilation fails because `DemoField` and `demoFields()` do not exist.

- [ ] **Step 3: Define non-overlapping demo fields**

Use these keys:

```text
master/basic:
employee_code, employee_name, gender, job_title, mobile_phone, work_email

工程部/project:
engineering_project_name, engineering_project_role, engineering_allocation_rate

工程部/payroll:
engineering_base_pay, engineering_performance_bonus

产品部/roadmap:
product_quarter_target, product_delivery_rate

人力资源部/contract:
hr_contract_type, hr_contract_term, hr_contract_expiry_date

市场部/campaign:
marketing_campaign_name, marketing_budget

销售部/target:
sales_quarter_amount, sales_collection_rate
```

Mark only selected non-sensitive fields shared, for example project name/role, product target/delivery rate, contract type, campaign name, and sales amount. Payroll fields remain non-shared.

- [ ] **Step 4: Implement deterministic business reset**

Do not delete users or permissions. Before reseeding, delete in dependency order:

```java
pushLogRepo.deleteAllInBatch();
approvalRepo.deleteAllInBatch();
personnelSubRepo.deleteAllInBatch();
personnelRepo.deleteAllInBatch();
fieldDefRepo.deleteAllInBatch();
```

Replace the current `if (userRepo.count() > 0) return` with two phases:

1. Seed accounts and permissions only when users are absent.
2. Reset and seed business demo data when the demo schema version constant differs from a persisted marker or when explicitly enabled for this development branch.

For this branch, use a property `app.demo.reset=true` once to satisfy the requested reset, then set it to `false` after the first verified run so ordinary restarts preserve user-created fields. Do not restore `ddl-auto=create`.

- [ ] **Step 5: Run initializer tests**

```powershell
.\mvnw.cmd -Dtest=DataInitializerTest test
```

Expected: key uniqueness and sharing-scope tests pass.

- [ ] **Step 6: Commit**

```powershell
git add backend-java/src/main/java/com/simplemdm/config/DataInitializer.java backend-java/src/test/java/com/simplemdm/config/DataInitializerTest.java backend-java/src/main/resources/application.yml
git commit -m "feat: refresh isolated dynamic field demo data"
```

---

### Task 6: Add Sub-Field Sharing and Authorized Deletion to Field Manager

**Files:**
- Modify: `frontend/src/api/deptFields.js`
- Modify: `frontend/src/stores/user.js`
- Modify: `frontend/src/views/dept-fields/Manager.vue`
- Create: `frontend/src/views/dept-fields/Manager.spec.js`

**Interfaces:**
- Produces: `deleteFieldDef(id)` API helper.
- Produces: `canManageOwnDepartment` store computed.
- Consumes: field response property `shared`.

- [ ] **Step 1: Write failing component tests**

Mount with mocked API modules and Pinia. Assert:

```javascript
it('shows shared only for sub fields and never shows delete for master fields', async () => {
  const wrapper = mountManager({ activeTab: 'sub', departmentAdmin: true })
  expect(wrapper.text()).toContain('是否共享')
  expect(wrapper.text()).toContain('删除')
  await wrapper.vm.setActiveTabForTest('master')
  expect(wrapper.text()).not.toContain('是否共享')
  expect(wrapper.text()).not.toContain('删除')
})

it('hides delete from main administrators', () => {
  const wrapper = mountManager({ isAdmin: true, departmentAdmin: false })
  expect(wrapper.text()).not.toContain('删除')
})
```

The delete confirmation test must assert the literal warning:

```text
删除后，该字段定义以及所有历史子表记录中的对应数据都会永久清除。
```

- [ ] **Step 2: Run test and verify RED**

```powershell
cd frontend
npm test -- src/views/dept-fields/Manager.spec.js
```

Expected: tests fail because no shared column/toggle or delete action exists.

- [ ] **Step 3: Implement API and permission helper**

Add:

```javascript
export function deleteFieldDef(id) {
  return request.delete(`/dept-fields/${id}`)
}
```

In the store, do not let `isAdmin` imply edit rights:

```javascript
const canManageOwnDepartment = computed(() =>
  !isAdmin.value && permissions.value.some(permission =>
    permission.perm_type === 'EDIT' &&
    permission.scope_value === user.value?.department
  )
)
```

- [ ] **Step 4: Implement manager UI**

- Add `shared: false` to `dialogForm`.
- Show the switch only when `activeTab === 'sub'`.
- Send `shared` on sub create/update.
- Add a shared-status column only for sub fields.
- Show delete only for non-system sub fields when `canManageOwnDepartment`.
- Use `ElMessageBox.confirm`, call `deleteFieldDef`, close/reset state, and reload fields.

- [ ] **Step 5: Run component test and frontend suite**

```powershell
npm test -- src/views/dept-fields/Manager.spec.js
npm test
```

Expected: all field-manager and existing frontend tests pass.

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/api/deptFields.js frontend/src/stores/user.js frontend/src/views/dept-fields/Manager.vue frontend/src/views/dept-fields/Manager.spec.js
git commit -m "feat: manage shared and deletable sub fields"
```

---

### Task 7: Make Department a Mandatory URL-Backed List Context

**Files:**
- Modify: `frontend/src/views/personnel/List.vue`
- Modify: `frontend/src/api/personnel.js`
- Create: `frontend/src/views/personnel/List.spec.js`

**Interfaces:**
- Consumes: `/personnel/departments` returns only concrete viewable departments.
- Produces: route query `department`.
- Produces: detail links with `from_department`.

- [ ] **Step 1: Write failing list tests**

Cover the observable behaviors:

```javascript
it('defaults to own department and never requests all departments', async () => {
  getDepartments.mockResolvedValue({ data: ['工程部', '产品部'] })
  mountList({ userDepartment: '工程部', routeQuery: {} })
  await flushPromises()
  expect(listPersonnel).toHaveBeenCalledWith(expect.objectContaining({
    department: '工程部'
  }))
  expect(wrapper.text()).not.toContain('全部')
})

it('switching to another department hides create and edit', async () => {
  const wrapper = mountList({ userDepartment: '工程部' })
  await selectDepartment(wrapper, '产品部')
  expect(wrapper.text()).not.toContain('新增')
  expect(wrapper.text()).not.toContain('编辑')
  expect(router.replace).toHaveBeenCalledWith({
    query: expect.objectContaining({ department: '产品部' })
  })
})
```

Add a deferred-promise test in which the first department response arrives after the second; assert the table still contains only the second response.

- [ ] **Step 2: Run test and verify RED**

```powershell
npm test -- src/views/personnel/List.spec.js
```

Expected: tests fail because department is clearable, route state is absent, and stale requests are unguarded.

- [ ] **Step 3: Implement mandatory department selection**

Use `useRoute`/`useRouter`. Determine selection in this exact order:

```javascript
const routeDepartment = String(route.query.department || '')
const ownDepartment = userStore.user?.department || ''
const selected = departments.value.includes(routeDepartment)
  ? routeDepartment
  : departments.value.includes(ownDepartment)
    ? ownDepartment
    : departments.value[0] || ''
```

Remove `clearable`, remove the “全部” placeholder, and do not call `listPersonnel` when selected is empty.

- [ ] **Step 4: Add stale-request protection and navigation context**

Use an incrementing sequence:

```javascript
let requestSequence = 0
async function fetchData() {
  const sequence = ++requestSequence
  tableData.value = []
  const response = await listPersonnel({ ...query, department: selectedDepartment.value })
  if (sequence !== requestSequence) return
  tableData.value = response.data.items || []
}
```

Build view/edit links with:

```javascript
{
  path: `/personnel/${row.id}`,
  query: { from_department: selectedDepartment.value }
}
```

Show create/edit only when `selectedDepartment === userStore.user.department`.

- [ ] **Step 5: Run list test and frontend suite**

```powershell
npm test -- src/views/personnel/List.spec.js
npm test
```

Expected: defaulting, URL persistence, read-only switching, and stale-response tests pass.

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/views/personnel/List.vue frontend/src/api/personnel.js frontend/src/views/personnel/List.spec.js
git commit -m "feat: add mandatory department master-data context"
```

---

### Task 8: Align Detail Navigation and Remove Record Visibility UI

**Files:**
- Modify: `frontend/src/views/personnel/Form.vue`
- Create: `frontend/src/views/personnel/Form.spec.js`
- Modify: `frontend/src/api/personnelSub.js` if it still sends visibility.

**Interfaces:**
- Consumes: `from_department` route query from Task 7.
- Consumes: backend-projected sub definitions and data from Task 3.
- Produces: no record-level `visibility` payload.

- [ ] **Step 1: Write failing form tests**

Tests must assert:

```javascript
it('returns to the source department', async () => {
  const wrapper = mountForm({
    route: { params: { id: '12' }, query: { from_department: '产品部' } }
  })
  await wrapper.get('[data-test="back"]').trigger('click')
  expect(router.push).toHaveBeenCalledWith({
    path: '/personnel',
    query: { department: '产品部' }
  })
})

it('does not show or submit record visibility', async () => {
  const wrapper = mountForm({ ownerDepartment: '工程部', mode: 'edit' })
  expect(wrapper.text()).not.toContain('可见性')
  await saveSubRecord(wrapper)
  expect(updateSub).toHaveBeenCalledWith(
    expect.anything(),
    expect.anything(),
    expect.not.objectContaining({ visibility: expect.anything() })
  )
})
```

Add a foreign-department test asserting no master edit button and no sub add/edit buttons.

- [ ] **Step 2: Run test and verify RED**

```powershell
npm test -- src/views/personnel/Form.spec.js
```

Expected: tests fail because the page uses `router.back()`, displays visibility, and submits it.

- [ ] **Step 3: Implement source-department navigation and strict own-department editing**

Replace generic back calls with:

```javascript
function returnToList() {
  const source = String(route.query.from_department || userStore.user?.department || '')
  router.push({ path: '/personnel', query: source ? { department: source } : {} })
}
```

Define:

```javascript
const isOwnDepartment = computed(
  () => form.owner_dept === userStore.user?.department
)
```

Use it for all master and sub mutation controls. Do not infer editability from `VIEW ALL` or `isAdmin`.

- [ ] **Step 4: Remove visibility UI and payload**

Delete:

- visibility column.
- visibility selector.
- visibility label/type helpers.
- `subForm.visibility`.
- `visibility` in create/update payload.

Render only definitions and values returned by the backend. Empty foreign groups are naturally absent because Task 3 omits them.

- [ ] **Step 5: Run frontend tests and production build**

```powershell
npm test
npm run build
```

Expected: all Vitest tests pass and Vite build exits with code 0.

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/views/personnel/Form.vue frontend/src/views/personnel/Form.spec.js frontend/src/api/personnelSub.js
git commit -m "feat: secure department detail navigation"
```

---

### Task 9: Reset the Development Database and Run End-to-End Verification

**Files:**
- Modify only if verification exposes a defect in files owned by Tasks 1–8.
- Create: `docs/superpowers/verification/2026-07-30-field-governance-acceptance.md`

**Interfaces:**
- Consumes all prior task outputs.
- Produces fresh verification evidence and an acceptance record.

- [ ] **Step 1: Run all automated backend tests**

```powershell
cd backend-java
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`, zero failures, zero errors.

- [ ] **Step 2: Run all automated frontend tests and build**

```powershell
cd ..\frontend
npm test
npm run build
```

Expected: Vitest reports zero failures and Vite build exits 0.

- [ ] **Step 3: Perform the one-time demo reset**

Stop the backend. Set `app.demo.reset=true`, start the backend once, and confirm initialization completes. Stop it, set `app.demo.reset=false`, then restart. This explicitly authorizes deletion of the requested business demo data only:

- `mdm_personnel`
- `mdm_personnel_sub`
- `mdm_field_definition`
- `wf_approval`
- associated demo push logs

Do not delete `sys_user`, `sys_user_permission`, or approver assignments.

- [ ] **Step 4: Verify database invariants with read-only SQL**

Run:

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

Expected: both queries return zero rows.

- [ ] **Step 5: Verify APIs with department admin and viewer accounts**

Using `wangwu/123456`:

- List own department and confirm create/edit controls are authorized.
- List another viewable department and confirm master data is returned.
- Open another department detail and confirm only shared sub-fields appear.
- Create a duplicate key across master/sub and confirm HTTP 400 with conflict source.
- Delete an own-department sub-field and confirm its JSON key is absent from every affected record.
- Attempt to delete a master field and confirm HTTP 403.

Using `admin/admin123`:

- Attempt sub-field deletion and confirm HTTP 403, `"主管理员无字段删除权限"`.

- [ ] **Step 6: Record acceptance evidence**

Write the exact commands, exit codes, test counts, API statuses, and SQL row counts to:

```text
docs/superpowers/verification/2026-07-30-field-governance-acceptance.md
```

Do not write “passes” without the command output evidence.

- [ ] **Step 7: Commit verification evidence**

```powershell
git add docs/superpowers/verification/2026-07-30-field-governance-acceptance.md
git commit -m "test: verify field governance acceptance"
```

