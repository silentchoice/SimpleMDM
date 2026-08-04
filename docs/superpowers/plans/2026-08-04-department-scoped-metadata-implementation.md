# Department-Scoped Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver department-isolated metadata whose field and sub-type changes are submitted by department editors and become active only after approval by the same department.

**Architecture:** Global `master_types` remain super-admin templates. Department assignments and all field/sub-type definitions are department-scoped; editors create immutable approval snapshots, while an approval application service atomically replaces the active department schema. Existing uncommitted metadata prototypes must be reshaped to these boundaries rather than committed as direct-write APIs.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring JDBC, MySQL 8, Flyway, Jackson, Spring Security, JUnit 5, Mockito.

## Global Constraints

- Every department has at most one active master-type assignment; one template may be assigned to many departments.
- `DEPT_EDITOR` submits only its own department changes; `DEPT_APPROVER` approves only its own department tasks.
- Submission never changes `ACTIVE` definitions; approval applies the snapshot in one transaction.
- Preserve `/api/master-type`, `/api/master-field`, `/api/sub-type`, and `/api/sub-field`.
- Return 400 for invalid structure, 403 for role/department violations, 404 for missing resources, and 409 for duplicate/state conflicts.
- Docker/Testcontainers may be skipped per user direction, but all non-container tests must pass.

---

### Task 1: Department-scoped schema and assignment constraint

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__department_scoped_metadata.sql`
- Modify: `backend/src/main/java/com/example/mdm/metadata/MetadataRepository.java`
- Modify: `backend/src/main/java/com/example/mdm/metadata/JdbcMetadataRepository.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/MetadataRepositoryContractTest.java`

**Interfaces:**
- Produces: `MetadataRepository.requireAssignment(long departmentId, long masterTypeId)` and department-aware field queries.

- [x] **Step 1: Write a failing repository contract test** that asserts a second active assignment for one department maps to `BusinessException` status 409 and that two departments using one template return separate field lists.
- [x] **Step 2: Run** `mvnw.cmd -Dtest=MetadataRepositoryContractTest test`; expect failure because department-scoped methods and V3 do not exist.
- [x] **Step 3: Add V3 migration** with `department_id` foreign keys on `master_fields`, `sub_types`, and `sub_fields`; replace unique keys with `(department_id, owner_id, code)` and add a unique department assignment key.
- [x] **Step 4: Change repository signatures** to `findMasterFields(long departmentId, long masterTypeId)`, `findSubTypes(long departmentId, long masterTypeId)`, and `findSubFields(long departmentId, long subTypeId)`; every SQL statement must bind `department_id`.
- [x] **Step 5: Translate** `DuplicateKeyException`/`DataIntegrityViolationException` into `BusinessException(HttpStatus.CONFLICT, "Metadata conflict")` while retaining database unique constraints for concurrency safety.
- [x] **Step 6: Run the contract and all non-container tests**, then commit with `feat: scope metadata storage by department`.

### Task 2: Field structure and value validation

**Files:**
- Modify: `backend/src/main/java/com/example/mdm/metadata/FieldDefinition.java`
- Create: `backend/src/main/java/com/example/mdm/metadata/FieldStructureValidator.java`
- Modify: `backend/src/main/java/com/example/mdm/metadata/FieldValueValidator.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/FieldStructureValidatorTest.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/FieldValueValidatorTest.java`

**Interfaces:**
- Produces: `void FieldStructureValidator.validate(FieldDefinition definition)` and `void FieldValueValidator.validate(List<FieldDefinition>, Map<String,Object>)`.

- [x] **Step 1: Add failing table-driven tests** for code regex `[A-Za-z][A-Za-z0-9_]{0,63}`, empty/duplicate selection options, options on non-selection fields, unknown values, required values, dates, numbers, switches, and multi-select values.
- [x] **Step 2: Run the two validator tests** and confirm each new branch fails for the named missing behavior.
- [x] **Step 3: Implement minimal validation**; normalize no business value silently, and return `BusinessException.badRequest(...)` with the offending field code.
- [x] **Step 4: Re-run validator and all non-container tests**, then commit with `feat: validate department metadata structures`.

### Task 3: Editor submission and immutable approval snapshots

**Files:**
- Create: `backend/src/main/java/com/example/mdm/metadata/MetadataChangeRequest.java`
- Create: `backend/src/main/java/com/example/mdm/metadata/MetadataApprovalRepository.java`
- Create: `backend/src/main/java/com/example/mdm/metadata/JdbcMetadataApprovalRepository.java`
- Modify: `backend/src/main/java/com/example/mdm/metadata/MetadataService.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/MetadataServiceTest.java`

**Interfaces:**
- Produces: `long submitMasterFields(List<FieldDefinition> fields)`, `long submitSubTypes(List<SubType> types)`, and `long submitSubFields(long subTypeId, List<FieldDefinition> fields)`.

- [x] **Step 1: Write failing tests** proving `DEPT_EDITOR` submission derives department from `UserPrincipal`, rejects another department/template, stores literal before/after JSON snapshots, and leaves active repository results unchanged.
- [x] **Step 2: Run** `mvnw.cmd -Dtest=MetadataServiceTest test`; expect failures from current direct-write methods.
- [x] **Step 3: Remove direct ACTIVE field/sub-type writes from `MetadataService`**. Require `Role.DEPT_EDITOR`, call `requireDepartment(principal.department().id())`, validate the department assignment, and persist `approval_tasks` with `status='PENDING'`, `submitted_by`, and the editor department.
- [x] **Step 4: Serialize snapshots with Jackson DTOs** containing explicit schema version, department ID, template ID, entity kind, and ordered definitions; never serialize security principals or credentials.
- [x] **Step 5: Run service and all non-container tests**, then commit with `feat: submit department metadata for approval`.

### Task 4: Compatible metadata API families

**Files:**
- Replace: `backend/src/main/java/com/example/mdm/metadata/MetadataController.java`
- Create: `backend/src/main/java/com/example/mdm/metadata/MasterTypeController.java`
- Create: `backend/src/main/java/com/example/mdm/metadata/MasterFieldController.java`
- Create: `backend/src/main/java/com/example/mdm/metadata/SubTypeController.java`
- Create: `backend/src/main/java/com/example/mdm/metadata/SubFieldController.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/MetadataControllerTest.java`

**Interfaces:**
- Consumes: Task 3 submission methods and department-aware Task 1 reads.
- Produces: `/api/master-type`, `/api/master-field`, `/api/sub-type`, `/api/sub-field` endpoints using `ApiResponse`.

- [x] **Step 1: Write failing MockMvc tests** for exact paths, request IDs, editor submission, viewer reads, cross-department 403, malformed schema 400, missing entity 404, and duplicate 409.
- [x] **Step 2: Run** `mvnw.cmd -Dtest=MetadataControllerTest test`; expect 404/missing-controller failures.
- [x] **Step 3: Implement focused controllers**: master-type creation/assignment calls super-admin services; structure POST endpoints submit approval tasks; GET endpoints return only current department `ACTIVE` definitions.
- [x] **Step 4: Run controller and all non-container tests**, then commit with `feat: expose department metadata APIs`.

### Task 5: Department approval applies metadata atomically

**Files:**
- Create: `backend/src/main/java/com/example/mdm/metadata/MetadataApprovalApplicationService.java`
- Modify: `backend/src/main/java/com/example/mdm/metadata/MetadataApprovalRepository.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/MetadataApprovalApplicationServiceTest.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/DepartmentMetadataIsolationTest.java`

**Interfaces:**
- Produces: `void approve(long taskId, String comment)` and `void reject(long taskId, String reason)` for later delegation from the general approval module.

- [x] **Step 1: Write failing tests** proving same-department approver access, cross-department rejection, repeat approval 409, rejection without active changes, and transactional application of ordered snapshots.
- [x] **Step 2: Add an isolation test** where departments A and B share a template, A approves a new field, and B's active schema remains byte-for-byte unchanged.
- [x] **Step 3: Run the tests** and confirm failure because no application service exists.
- [x] **Step 4: Implement `@Transactional` approval**: require `DEPT_APPROVER`, lock the task with `SELECT ... FOR UPDATE`, validate department/status/schema version, replace only that department's definitions, and update task status atomically.
- [x] **Step 5: Implement rejection** requiring a nonblank reason without touching active definitions.
- [x] **Step 6: Run all non-container backend tests, frontend tests, frontend build, and `git diff --check`**. Request code review and resolve all Critical/Important findings.
- [x] **Step 7: Commit** with `feat: apply approved department metadata changes`.
