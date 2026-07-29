# SimpleMDM Fully Dynamic Master and Sub Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make field definitions the single source of truth for arbitrary master and sub-table fields while preserving department permissions, approval, system isolation, and stable historical data.

**Architecture:** `mdm_personnel` retains system metadata and stores business values in `data_json`; `mdm_personnel_sub` keeps its current JSON storage. Both paths use immutable `field_key` definitions and a shared backend validator plus shared Vue dynamic-field components.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, Jackson, MySQL 8.0, JUnit 5/Mockito, Vue 3, Element Plus, Vite, Vitest

## Global Constraints

- Do not execute physical `DELETE` operations.
- Database data may be rebuilt; no online migration or preservation of current demo rows is required.
- `field_key` is immutable and is the JSON/API/approval identity; `field_name` is mutable display text.
- `owner_dept` remains a required system column and cannot be deleted or repurposed.
- Master updates continue through the existing approval flow.
- Sub-record create/update remains direct-save in this change; sub approval is a later feature.
- Field and record access must remain isolated by `system_code`.
- Backend validation is authoritative; frontend validation is for immediate feedback.

---

## File Structure

### Backend

- `model/MdmPersonnel.java`: system metadata plus master `dataJson`.
- `model/MdmFieldDefinition.java`: stable key, display metadata, options, and system-field flag.
- `dto/DynamicPersonnelDTO.java`: master create/update request contract.
- `dto/PersonnelSubDTO.java`: sub request contract using a typed `data` map.
- `service/DynamicFieldService.java`: loads definitions, validates/coerces maps, and labels diffs.
- `service/PersonnelService.java`: persistence, search, diff, and approval application for dynamic master data.
- `service/ApprovalService.java`: creates/applies dynamic master approvals.
- `controller/PersonnelController.java`: dynamic master HTTP contract.
- `controller/PersonnelSubController.java`: validated dynamic sub records.
- `controller/FieldDefinitionController.java`: immutable key and type-change safety.
- `repository/MdmPersonnelRepository.java`: `ownerDept`, `systemCode`, and JSON-text search.
- `repository/MdmFieldDefinitionRepository.java`: scoped key uniqueness and ordered definition lookup.
- `config/DataInitializer.java`: rebuilt dynamic seed definitions and JSON records.
- `src/test/...`: focused unit tests for definitions, validation, personnel, approval, and sub records.

### Frontend

- `components/DynamicFieldInput.vue`: one field input in edit mode.
- `components/DynamicFieldValue.vue`: one formatted value in display/list mode.
- `utils/dynamicFields.js`: default values, Element Plus rules, and normalized payloads.
- `views/personnel/Form.vue`: master and sub forms driven only by definitions.
- `views/personnel/List.vue`: dynamic master columns.
- `views/dept-fields/Manager.vue`: stable key, options, and system-field restrictions.
- `components/ChangeDiff.vue`: resolves stable keys to display names.
- `src/**/*.spec.js`: Vitest component/helper tests.

---

### Task 1: Establish the Dynamic Field Definition Contract

**Files:**
- Modify: `backend-java/pom.xml`
- Modify: `backend-java/src/main/java/com/simplemdm/model/MdmFieldDefinition.java`
- Modify: `backend-java/src/main/java/com/simplemdm/repository/MdmFieldDefinitionRepository.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/DynamicFieldService.java`
- Modify: `backend-java/src/main/java/com/simplemdm/controller/FieldDefinitionController.java`
- Create: `backend-java/src/test/java/com/simplemdm/service/DynamicFieldServiceTest.java`
- Create: `backend-java/src/test/java/com/simplemdm/controller/FieldDefinitionControllerTest.java`

**Interfaces:**
- Produces: `ValidationResult DynamicFieldService.validate(String systemCode, String department, String tableType, String subType, Map<String,Object> data)`
- Produces: `Map<String,Object> DynamicFieldService.computeDiff(Map<String,Object> oldData, Map<String,Object> newData)`
- Produces: field definitions containing `field_key`, `field_name`, `field_type`, `required`, `sort_order`, `options`, and `system_field`

- [ ] **Step 1: Add an isolated test database dependency**

Add H2 test scope to `pom.xml`:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write failing validator tests**

Create `DynamicFieldServiceTest` with Mockito-backed repository definitions for:

```java
@Test void rejectsUnknownFieldKey()
@Test void rejectsMissingRequiredValue()
@Test void coercesNumberAndAcceptsSelectOption()
@Test void rejectsInvalidDateAndUnknownSelectOption()
@Test void computesDiffUsingStableFieldKeys()
```

Use definitions including:

```java
field("employee_code", "工号", "string", true, null);
field("age", "年龄", "number", false, null);
field("hire_date", "入职日期", "date", false, null);
field("level", "职级", "select", false, "[\"P1\",\"P2\"]");
```

Assert errors identify the exact key, for example `未知字段: extra` and `字段 hire_date 必须是日期 yyyy-MM-dd`.

- [ ] **Step 3: Run the validator tests and verify failure**

Run:

```powershell
cd backend-java
.\mvnw.cmd -Dtest=DynamicFieldServiceTest test
```

Expected: FAIL because `DynamicFieldService` and new definition properties do not exist.

- [ ] **Step 4: Extend the field-definition entity and repository**

Add:

```java
@Column(name = "field_key", length = 64, nullable = false)
private String fieldKey;

@Column(name = "options_json", columnDefinition = "TEXT")
private String optionsJson;

@Column(name = "system_field", nullable = false)
private Boolean systemField = false;
```

Add repository methods:

```java
boolean existsBySystemCodeAndDepartmentAndTableTypeAndSubTypeAndFieldKey(
    String systemCode, String department, String tableType, String subType, String fieldKey);

List<MdmFieldDefinition> findBySystemCodeAndTableTypeOrderBySubTypeAscSortOrderAsc(
    String systemCode, String tableType);

List<MdmFieldDefinition> findBySystemCodeAndDepartmentAndTableTypeAndSubTypeOrderBySortOrderAsc(
    String systemCode, String department, String tableType, String subType);
```

- [ ] **Step 5: Implement the shared validator**

Create `DynamicFieldService` with:

```java
public record ValidationResult(Map<String, Object> data) {}

public ValidationResult validate(
    String systemCode,
    String department,
    String tableType,
    String subType,
    Map<String, Object> input)

public Map<String, Object> computeDiff(
    Map<String, Object> oldData,
    Map<String, Object> newData)
```

Validation rules:

- Load master definitions by `systemCode + tableType=master`.
- Load sub definitions by `systemCode + department + tableType=sub + subType`.
- Reject undefined keys.
- Treat `null` and blank string as absent for required checks.
- Coerce numbers to `BigDecimal`.
- Parse dates with `LocalDate.parse`.
- Require select/radio values to occur in `options_json`.
- Return a new `LinkedHashMap` ordered by definition sort order.
- Do not include `owner_dept` in the returned business map.

- [ ] **Step 6: Make field creation enforce stable keys**

Update `FieldDefinitionController.create` to require:

```json
{
  "field_key": "employee_code",
  "field_name": "工号",
  "field_type": "string",
  "required": true,
  "sort_order": 1,
  "options": [],
  "table_type": "master",
  "sub_type": "basic"
}
```

Validate `field_key` against `^[a-z][a-z0-9_]{1,63}$`, enforce scoped uniqueness, set the current permitted `system_code`, and return all new properties in `toMap`.

- [ ] **Step 7: Write and run controller tests**

Test:

```java
@Test void createRejectsDuplicateFieldKey()
@Test void updateDoesNotChangeFieldKey()
@Test void updateRejectsTypeChangeWhenExistingDataIsIncompatible()
@Test void systemOwnerDepartmentFieldCannotBeDeleted()
```

Run:

```powershell
.\mvnw.cmd -Dtest=FieldDefinitionControllerTest test
```

Expected: PASS.

- [ ] **Step 8: Run Task 1 tests and commit**

```powershell
.\mvnw.cmd -Dtest=DynamicFieldServiceTest,FieldDefinitionControllerTest test
git add backend-java
git commit -m "feat: define stable dynamic field contract"
```

---

### Task 2: Replace Fixed Master Columns with JSON Business Data

**Files:**
- Modify: `backend-java/src/main/java/com/simplemdm/model/MdmPersonnel.java`
- Create: `backend-java/src/main/java/com/simplemdm/dto/DynamicPersonnelDTO.java`
- Delete: `backend-java/src/main/java/com/simplemdm/dto/PersonnelDTO.java`
- Modify: `backend-java/src/main/java/com/simplemdm/repository/MdmPersonnelRepository.java`
- Modify: `backend-java/src/main/java/com/simplemdm/service/PersonnelService.java`
- Modify: `backend-java/src/main/java/com/simplemdm/controller/PersonnelController.java`
- Create: `backend-java/src/test/java/com/simplemdm/service/PersonnelServiceTest.java`
- Create: `backend-java/src/test/java/com/simplemdm/controller/PersonnelControllerTest.java`

**Interfaces:**
- Consumes: `DynamicFieldService.validate(...)`
- Produces: `DynamicPersonnelDTO { String ownerDept; Map<String,Object> data; Integer version; }`
- Produces: personnel responses with `owner_dept` and `data`

- [ ] **Step 1: Write failing model/service tests**

Test:

```java
@Test void createsPendingPersonnelWithValidatedJsonData()
@Test void computesDiffForOwnerDepartmentAndBusinessFields()
@Test void appliesApprovedChangesToJsonAndOwnerDepartment()
@Test void filtersBySystemAndAllowedOwnerDepartments()
```

The expected stored entity has:

```java
assertEquals("工程部", saved.getOwnerDept());
assertEquals("""
    {"employee_code":"EMP001","name":"张三"}
    """.replaceAll("\\s+", ""), saved.getDataJson());
```

- [ ] **Step 2: Run service tests and verify failure**

```powershell
.\mvnw.cmd -Dtest=PersonnelServiceTest test
```

Expected: FAIL because `ownerDept` and `dataJson` do not exist.

- [ ] **Step 3: Replace fixed business properties in `MdmPersonnel`**

Remove the seven fixed business columns and add:

```java
@Column(name = "owner_dept", length = 128, nullable = false)
private String ownerDept;

@Column(name = "data_json", columnDefinition = "LONGTEXT", nullable = false)
private String dataJson = "{}";
```

Keep `id`, `systemCode`, `status`, `version`, and audit timestamps.

- [ ] **Step 4: Introduce the dynamic request DTO**

```java
public class DynamicPersonnelDTO {
    @NotBlank
    public String ownerDept;

    @NotNull
    public Map<String, Object> data;

    public Integer version;
}
```

- [ ] **Step 5: Replace repository queries**

Provide:

```java
@Query("""
    SELECT p FROM MdmPersonnel p
    WHERE (:keyword IS NULL OR LOWER(p.dataJson) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:department IS NULL OR p.ownerDept = :department)
      AND p.ownerDept IN :allowedDepts
      AND (:systemCode IS NULL OR p.systemCode = :systemCode)
    """)
Page<MdmPersonnel> search(...);

@Query("SELECT DISTINCT p.ownerDept FROM MdmPersonnel p ORDER BY p.ownerDept")
List<String> findDistinctOwnerDepartments();
```

Use a second query without `ownerDept IN` for users with `ALL` scope.

- [ ] **Step 6: Implement dynamic persistence and response mapping**

`PersonnelService` must:

- Inject the Spring-managed `ObjectMapper` rather than instantiate one.
- Validate request data through `DynamicFieldService`.
- Serialize/deserialize `data_json`.
- Compare business maps plus `owner_dept`.
- Apply approved changes by stable key.
- Map entities to:

```java
Map.of(
    "id", p.getId(),
    "system_code", p.getSystemCode(),
    "owner_dept", p.getOwnerDept(),
    "data", readData(p),
    "status", p.getStatus(),
    "version", p.getVersion()
)
```

- [ ] **Step 7: Update the controller contract**

Replace `PersonnelDTO` request bodies with `DynamicPersonnelDTO`. Remove fixed-field uniqueness logic; uniqueness is not implicit for arbitrary fields. Return the response shape defined above for list and detail.

- [ ] **Step 8: Write controller contract tests**

Using `@WebMvcTest`, assert:

```java
jsonPath("$.data.items[0].owner_dept").value("工程部");
jsonPath("$.data.items[0].data.employee_code").value("EMP001");
jsonPath("$.data.items[0].employee_code").doesNotExist();
```

Also assert a validation error for missing `owner_dept`.

- [ ] **Step 9: Run and commit**

```powershell
.\mvnw.cmd -Dtest=PersonnelServiceTest,PersonnelControllerTest test
git add backend-java
git commit -m "feat: store master business fields as json"
```

---

### Task 3: Adapt the Approval Flow to Stable Dynamic Keys

**Files:**
- Modify: `backend-java/src/main/java/com/simplemdm/service/ApprovalService.java`
- Modify: `backend-java/src/main/java/com/simplemdm/service/PushService.java`
- Modify: `backend-java/src/main/java/com/simplemdm/config/DataInitializer.java`
- Create: `backend-java/src/test/java/com/simplemdm/service/ApprovalServiceTest.java`

**Interfaces:**
- Consumes: `DynamicPersonnelDTO`
- Consumes: `PersonnelService.computeDiff(...)` and `PersonnelService.applyChanges(...)`
- Produces: approval `change_data` keyed by `field_key`, with `owner_dept` reserved for the system column

- [ ] **Step 1: Write failing approval tests**

Test:

```java
@Test void createApprovalStoresAllDynamicValuesAsStableKeyDiff()
@Test void updateApprovalStoresOnlyChangedDynamicValues()
@Test void approvalAppliesDynamicValuesAndOwnerDepartment()
@Test void rejectionRestoresActiveStatusWithoutChangingJson()
@Test void approvalDetailUsesConfiguredNameFieldForPersonnelName()
```

Expected change JSON:

```json
{
  "owner_dept": {"old": null, "new": "工程部"},
  "employee_code": {"old": null, "new": "EMP001"},
  "name": {"old": null, "new": "张三"}
}
```

For create workflows all old values are `null`; for update workflows omit
unchanged values and populate both old and new values.

- [ ] **Step 2: Run tests and verify failure**

```powershell
.\mvnw.cmd -Dtest=ApprovalServiceTest test
```

- [ ] **Step 3: Replace reflective fixed-DTO approval creation**

Use the validated request map directly. Resolve `personnel_name` from dynamic data using `field_key=name`; if absent, fall back to the first nonblank string field, then `#<id>`.

- [ ] **Step 4: Apply changes without a fixed-field switch**

`PersonnelService.applyChanges` must:

- Deserialize current `data_json`.
- Apply every diff key except `owner_dept` into the data map.
- Apply `owner_dept` to the system column.
- Revalidate the final state before saving.
- Mark the record active.

- [ ] **Step 5: Update push payload generation**

Change `PushService` to send:

```json
{
  "id": 1,
  "system_code": "HR",
  "owner_dept": "工程部",
  "data": {
    "employee_code": "EMP001",
    "name": "张三"
  },
  "version": 2
}
```

Remove all calls to deleted fixed getters.

- [ ] **Step 6: Run approval and push tests**

```powershell
.\mvnw.cmd -Dtest=ApprovalServiceTest,PersonnelServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend-java
git commit -m "feat: approve dynamic master field changes"
```

---

### Task 4: Validate Fully Dynamic Sub-Table Records

**Files:**
- Modify: `backend-java/src/main/java/com/simplemdm/dto/PersonnelSubDTO.java`
- Modify: `backend-java/src/main/java/com/simplemdm/controller/PersonnelSubController.java`
- Create: `backend-java/src/main/java/com/simplemdm/service/PersonnelSubService.java`
- Create: `backend-java/src/test/java/com/simplemdm/service/PersonnelSubServiceTest.java`
- Create: `backend-java/src/test/java/com/simplemdm/controller/PersonnelSubControllerTest.java`

**Interfaces:**
- Consumes: `DynamicFieldService.validate(systemCode, ownerDept, "sub", subType, data)`
- Produces: sub requests and responses using `data: Map<String,Object>`, not client-built JSON strings

- [ ] **Step 1: Write failing sub-service tests**

Test:

```java
@Test void createsMultipleRecordsForTheSameDefinedSubType()
@Test void rejectsUndefinedSubType()
@Test void rejectsUnknownOrMissingRequiredSubField()
@Test void updateCannotMoveRecordToAnotherSubType()
@Test void ownerDepartmentEditorCanUpdateAndOtherDepartmentCannot()
@Test void crossDepartmentViewerOnlySeesSharedRows()
```

- [ ] **Step 2: Run tests and verify failure**

```powershell
.\mvnw.cmd -Dtest=PersonnelSubServiceTest test
```

- [ ] **Step 3: Replace the sub DTO contract**

```java
public class PersonnelSubDTO {
    @NotBlank public String subType;
    @NotNull public Map<String, Object> data;
    public String visibility;
    public Integer version;
}
```

Do not accept `ownerDept` from the client.

- [ ] **Step 4: Extract `PersonnelSubService`**

Move repository, permission, visibility, serialization, version, and field validation logic out of the controller. Creation derives `systemCode` and `ownerDept` from the parent. Update keeps the existing `subType` immutable.

- [ ] **Step 5: Return parsed data maps**

Sub responses use:

```json
{
  "id": 11,
  "personnel_id": 1,
  "sub_type": "project",
  "owner_dept": "工程部",
  "data": {"project_name": "智能工厂平台"},
  "visibility": "private",
  "version": 1
}
```

- [ ] **Step 6: Add controller contract tests and run**

Assert parsed `data`, absence of `data_json`, validation errors, and permission errors.

```powershell
.\mvnw.cmd -Dtest=PersonnelSubServiceTest,PersonnelSubControllerTest test
```

- [ ] **Step 7: Commit**

```powershell
git add backend-java
git commit -m "feat: validate dynamic sub-table records"
```

---

### Task 5: Build Shared Vue Dynamic Field Components

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/src/utils/dynamicFields.js`
- Create: `frontend/src/utils/dynamicFields.spec.js`
- Create: `frontend/src/components/DynamicFieldInput.vue`
- Create: `frontend/src/components/DynamicFieldInput.spec.js`
- Create: `frontend/src/components/DynamicFieldValue.vue`

**Interfaces:**
- Produces: `buildInitialData(definitions, source)`
- Produces: `buildRules(definitions)`
- Produces: `normalizePayload(definitions, data)`
- Produces: `<DynamicFieldInput :definition="definition" v-model="value" />`
- Produces: `<DynamicFieldValue :definition="definition" :value="value" />`

- [ ] **Step 1: Add frontend test tooling**

Add scripts/dependencies:

```json
{
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "devDependencies": {
    "@vue/test-utils": "^2.4.6",
    "jsdom": "^26.0.0",
    "vitest": "^3.2.0"
  }
}
```

Run `npm install` to update the lockfile.

- [ ] **Step 2: Write failing helper tests**

Cover:

```javascript
it('uses field_key rather than field_name')
it('preserves existing zero and false values')
it('builds required number/date/select rules')
it('drops unknown keys from the payload')
it('keeps definition sort order')
```

- [ ] **Step 3: Run tests and verify failure**

```powershell
npm test -- src/utils/dynamicFields.spec.js
```

- [ ] **Step 4: Implement field helpers**

Definitions use:

```javascript
{
  field_key: 'level',
  field_name: '职级',
  field_type: 'select',
  required: true,
  sort_order: 4,
  options: ['P1', 'P2']
}
```

Normalize numeric values to numbers when nonblank and leave dates as `yyyy-MM-dd`.

- [ ] **Step 5: Write failing component tests**

Assert string, number, date, select, and radio definitions render the corresponding Element Plus controls and emit `update:modelValue`.

- [ ] **Step 6: Implement input and display components**

`DynamicFieldInput` selects the control only from `field_type`. `DynamicFieldValue` formats blank as `-`, dates as the stored ISO date, and select/radio values as their option label.

- [ ] **Step 7: Run and commit**

```powershell
npm test
npm run build
git add frontend/package.json frontend/package-lock.json frontend/src/utils frontend/src/components
git commit -m "feat: add shared dynamic field components"
```

---

### Task 6: Convert Field Management, Master Form, and List to Definitions

**Files:**
- Modify: `frontend/src/views/dept-fields/Manager.vue`
- Modify: `frontend/src/views/personnel/Form.vue`
- Modify: `frontend/src/views/personnel/List.vue`
- Modify: `frontend/src/components/ChangeDiff.vue`
- Modify: `frontend/src/api/personnel.js`
- Modify: `frontend/src/api/personnelSub.js`
- Create: `frontend/src/views/personnel/Form.spec.js`
- Create: `frontend/src/views/personnel/List.spec.js`

**Interfaces:**
- Consumes: dynamic components and helpers from Task 5
- Consumes: backend `owner_dept + data` response contracts

- [ ] **Step 1: Write failing form tests**

Mock APIs and assert:

```javascript
it('renders every master definition in sort order')
it('loads existing values by field_key')
it('submits owner_dept separately from data')
it('renders an empty sub group and can add multiple records')
it('submits parsed sub data rather than data_json')
it('does not render stale hardcoded master fields')
```

- [ ] **Step 2: Write failing list tests**

Assert the list creates one column per active master definition, reads `row.data[field_key]`, and always retains status/actions columns.

- [ ] **Step 3: Run tests and verify failure**

```powershell
npm test -- src/views/personnel/Form.spec.js src/views/personnel/List.spec.js
```

- [ ] **Step 4: Update field-definition management**

Add:

- Required `field_key` input for new fields.
- Disabled `field_key` when editing.
- Comma-separated or row-based options editor for select/radio.
- Read-only badge and disabled destructive/type controls for `system_field`.
- Scoped duplicate-key error display from the backend.

- [ ] **Step 5: Replace hardcoded master form fields**

Load master definitions first, then render:

```vue
<el-form-item
  v-for="fd in masterFieldDefs"
  :key="fd.field_key"
  :label="fd.field_name"
  :prop="`data.${fd.field_key}`"
>
  <DynamicFieldInput
    :definition="fd"
    v-model="form.data[fd.field_key]"
  />
</el-form-item>
```

Render `owner_dept` as the protected system field and submit:

```javascript
{
  owner_dept: form.owner_dept,
  data: normalizePayload(masterFieldDefs.value, form.data),
  version: form.version
}
```

- [ ] **Step 6: Convert sub forms and tables to stable keys**

Remove `JSON.parse(row.data_json)`. Read/write `row.data[fd.field_key]`; allow repeated records in each field group.

- [ ] **Step 7: Convert the list to dynamic columns**

Fetch master definitions and personnel in parallel. Render columns by sorted definitions and read values through `DynamicFieldValue`.

- [ ] **Step 8: Resolve diff labels at display time**

Update `ChangeDiff` to accept `definitions`. Map stable keys to `field_name`, with a fixed label for `owner_dept`. Fall back to the raw key for historical definitions no longer available.

- [ ] **Step 9: Run and commit**

```powershell
npm test
npm run build
git add frontend
git commit -m "feat: drive master and sub screens from field definitions"
```

---

### Task 7: Rebuild Dynamic Seed Data and Database Schema

**Files:**
- Modify: `backend-java/src/main/resources/application.yml`
- Modify: `backend-java/src/main/java/com/simplemdm/config/DataInitializer.java`
- Create: `backend-java/src/test/java/com/simplemdm/config/DataInitializerTest.java`
- Modify: `README.md`

**Interfaces:**
- Produces: a clean schema and matching seed definitions/records at every demo start

- [ ] **Step 1: Write failing seed consistency test**

Load the initializer against H2 and assert:

```java
@Test void everyMasterJsonKeyHasAMasterDefinition()
@Test void everySubJsonKeyHasAScopedSubDefinition()
@Test void ownerDepartmentSystemDefinitionExists()
@Test void fieldKeysAreUniqueWithinScope()
@Test void multipleSubRecordsCanExistForOneGroup()
```

- [ ] **Step 2: Run and verify failure**

```powershell
.\mvnw.cmd -Dtest=DataInitializerTest test
```

- [ ] **Step 3: Configure demo schema rebuild**

Set:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create
```

Document that restarting the backend rebuilds demo data.

- [ ] **Step 4: Replace fixed personnel seed construction**

Seed master definitions with stable keys including:

```text
owner_dept, employee_code, name, gender, position, phone, email
```

Mark `owner_dept` as `system_field=true`. Store all other values in `data_json`.

- [ ] **Step 5: Replace sub JSON keys with stable keys**

For example:

```json
{"project_name":"智能工厂平台","project_role":"后端负责人","allocation":"80%"}
```

Ensure each key exists in the exact `systemCode + department + subType` definition scope.

- [ ] **Step 6: Update historical approval and push seeds**

Approval diff keys use `field_key`; push logs use the new nested `data` payload.

- [ ] **Step 7: Run and commit**

```powershell
.\mvnw.cmd -Dtest=DataInitializerTest test
git add backend-java README.md
git commit -m "feat: seed fully dynamic master and sub data"
```

---

### Task 8: Full Regression and Acceptance Verification

**Files:**
- Modify only if a failing test identifies a defect in files already listed above.

**Interfaces:**
- Verifies the complete feature; introduces no new product behavior.

- [ ] **Step 1: Run all backend tests**

```powershell
cd backend-java
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`, zero failed tests.

- [ ] **Step 2: Run all frontend tests and production build**

```powershell
cd ..\frontend
npm test
npm run build
```

Expected: all Vitest suites pass and Vite completes without errors.

- [ ] **Step 3: Start the rebuilt application**

```powershell
cd ..
.\start.bat
```

Verify backend port `18001` and frontend port `5173`.

- [ ] **Step 4: Execute the acceptance path**

Using `wangwu`:

1. Create a master field `emergency_contact` / “紧急联系人”.
2. Create a personnel record with that value.
3. Approve it as `lisi`.
4. Confirm detail and list show the new column/value.
5. Rename the display label to “应急联系人” and confirm the value remains.
6. Create sub group `certificate` with `certificate_name` and `expiry_date`.
7. Add two certificate rows to one personnel record.
8. Confirm both rows render and can be edited.

- [ ] **Step 5: Verify permissions and isolation**

- `admin`: may view but cannot edit data.
- `wangwu`: may edit only departments granted by `EDIT`.
- A cross-department viewer sees only shared sub rows.
- Definitions and records for another `system_code` do not appear under HR.

- [ ] **Step 6: Verify invalid inputs**

Confirm HTTP 400 responses for:

- Unknown master key.
- Missing required master field.
- Invalid number/date/select value.
- Duplicate field key.
- Undefined sub type.
- Unknown sub key.
- Incompatible field-type change.

- [ ] **Step 7: Inspect repository state**

```powershell
git status --short
git log -10 --oneline
```

Expected: only intentional tracked changes; no generated `target`, `dist`, database, or log artifacts.

- [ ] **Step 8: Commit any verification-only corrections**

Only if Step 1–7 exposed a defect:

```powershell
git add <exact corrected files>
git commit -m "fix: complete dynamic field acceptance"
```
