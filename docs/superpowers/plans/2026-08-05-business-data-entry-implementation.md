# SimpleMDM Business Data Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add department-scoped master/sub-record entry with drafts, generated codes, Redis edit locks, approval-based activation, three-version history, shared-field visibility, a populated dashboard, and modular bilingual table interfaces.

**Architecture:** A new backend `record` module owns record persistence, draft state, dynamic validation, code allocation, visibility, locking, and activation. Existing `approval_tasks` gains a record task family while retaining metadata behavior; the Vue console consumes typed record/dashboard APIs and renders separate dashboard, business-data, metadata, and approval modules.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring JDBC, Spring Security, Spring Data Redis, Flyway/MySQL 8, JUnit 5, MockMvc, Vue 3.4, TypeScript 5.5, Pinia 2, Vue Router 4, Element Plus 2, vue-i18n 10, Vitest.

## Global Constraints

- New, update, and logical-delete changes use `DRAFT → PENDING → APPROVED | REJECTED`; only approval mutates formal records.
- One master record and all child rows form one draft, one approval task, and one activation transaction.
- Retain exactly the latest three activated history snapshots per master record.
- Redis edit locks expire after 30 minutes; database version checks remain authoritative.
- Own-department users see complete data; cross-department readers receive shared fields only, filtered by the backend.
- Generated code templates support fixed text, `{yyyyMMdd}`, and one zero-padded sequence segment such as `{0001}`.
- Keep metadata approval behavior, API envelopes, request IDs, authentication storage, route permissions, and backend error messages unchanged.
- All new frontend-owned text exists in both `zh-CN` and `en-US`; default locale remains `zh-CN`.
- Do not implement multi-level workflow, physical deletion, history rollback, imports/exports, or sync changes.
- Use TDD for every behavior, request an independent review after every task, and preserve unrelated user changes.

---

### Task 1: Record schema evolution and generated-code rules

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__record_entry_workflow.sql`
- Create: `backend/src/main/java/com/example/mdm/record/RecordAction.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordStatus.java`
- Create: `backend/src/main/java/com/example/mdm/record/CodeRule.java`
- Create: `backend/src/main/java/com/example/mdm/record/CodeRuleParser.java`
- Create: `backend/src/main/java/com/example/mdm/record/CodeSequenceRepository.java`
- Create: `backend/src/main/java/com/example/mdm/record/JdbcCodeSequenceRepository.java`
- Create: `backend/src/main/java/com/example/mdm/record/CodeRuleService.java`
- Create: `backend/src/main/java/com/example/mdm/record/CodeRuleController.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordWorkflowMigrationTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/CodeRuleParserTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/CodeRuleServiceTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/CodeRuleControllerSecurityTest.java`

**Interfaces:**
- Produces: `CodeRule(long masterTypeId, String pattern, int sequenceWidth)`, `CodeRuleParser.parse(String)`, `CodeRuleService.save(long,String)`, and `CodeRuleService.allocate(long, LocalDate)`.
- Consumes: existing `AuthorizationService`, `UserPrincipal`, `ApiResponse<T>`, and master-type storage.

- [ ] **Step 1: Write failing migration tests** asserting V6 adds `record_action`, `base_version`, `delete_reason`, and `approval_task_id` to master drafts; `row_order` to child drafts/formal rows; a unique active-draft constraint; `master_type_code_rules`; and `code_sequences` keyed by `(master_type_id, sequence_date)`.

```java
assertThat(columns("master_record_drafts")).contains("record_action", "base_version", "delete_reason", "approval_task_id");
assertThat(uniqueIndex("code_sequences")).containsExactly("master_type_id", "sequence_date");
```

- [ ] **Step 2: Run the migration test and confirm RED.**

```powershell
cd backend
.\mvnw.cmd -Dtest=RecordWorkflowMigrationTest test
```

Expected: FAIL because V6 and the new columns/tables do not exist.

- [ ] **Step 3: Add V6 migration.** Preserve existing data, use `ALTER TABLE`, add foreign keys to `master_types` and `approval_tasks`, add a unique `(department_id, master_type_id, record_code)` formal-record key, and store sequence dates as `DATE`.

- [ ] **Step 4: Write failing parser/service tests** for `CUS-{yyyyMMdd}-{0001}`, rejection of multiple sequences/unknown variables, preview output, independent daily sequences, and gaps remaining after allocation.

```java
assertThat(parser.parse("CUS-{yyyyMMdd}-{0001}").render(LocalDate.of(2026, 8, 5), 7))
    .isEqualTo("CUS-20260805-0007");
assertThatThrownBy(() -> parser.parse("{department}-{0001}"))
    .isInstanceOf(BusinessException.class);
```

- [ ] **Step 5: Implement parser, transactional allocator, and SUPER_ADMIN rule controller.** Expose `GET/PUT /api/master-type/{masterTypeId}/code-rule`; PUT body is `{ "pattern": "CUS-{yyyyMMdd}-{0001}" }` and response includes a deterministic preview using the request date supplied only in tests.

- [ ] **Step 6: Run focused backend tests and commit.**

```powershell
.\mvnw.cmd -Dtest=RecordWorkflowMigrationTest,CodeRuleParserTest,CodeRuleServiceTest,CodeRuleControllerSecurityTest test
cd ..
git diff --check
git add backend/src/main/resources/db/migration/V6__record_entry_workflow.sql backend/src/main/java/com/example/mdm/record backend/src/test/java/com/example/mdm/record
git commit -m "feat: add record workflow schema and code rules"
```

### Task 2: Record drafts, dynamic validation, and three-version persistence

**Files:**
- Create: `backend/src/main/java/com/example/mdm/record/RecordDraft.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordDraftCommand.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordView.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordRepository.java`
- Create: `backend/src/main/java/com/example/mdm/record/JdbcRecordRepository.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordDraftService.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordSnapshotCodec.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordDraftServiceTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/JdbcRecordRepositoryTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordSnapshotCodecTest.java`

**Interfaces:**
- Consumes: Task 1 `CodeRuleService.allocate`; existing `MetadataRepository`, `FieldValueValidator`, and authenticated department/user.
- Produces: `RecordDraftService.create`, `.update`, `.copyRejected`, `.logicalDelete`, `.getDraft`; `RecordRepository.activate` and `.retainLatestHistory(recordId, 3)`.

- [ ] **Step 1: Write failing service tests** for new draft code allocation, complete master/sub validation, immutable path IDs, repeated draft saves, delete reason requirement, rejected-draft copying, and prevention of edits to `PENDING` drafts.

```java
var command = new RecordDraftCommand(null, 9L, 0L, RecordAction.CREATE,
    Map.of("name", "North Supplier"), List.of(new ChildRows(31L, List.of(Map.of("contact", "Li")))), null);
assertThat(service.create(command).recordCode()).isEqualTo("CUS-20260805-0001");
```

- [ ] **Step 2: Run focused tests and confirm RED.**

```powershell
cd backend
.\mvnw.cmd -Dtest=RecordDraftServiceTest,JdbcRecordRepositoryTest,RecordSnapshotCodecTest test
```

- [ ] **Step 3: Implement focused domain records and snapshot codec.** Use Jackson tree/model conversion, never Java native serialization; snapshot schema is `{schemaVersion, departmentId, masterTypeId, recordId, recordCode, action, baseVersion, masterValues, children}` with children ordered by subtype order then row order.

- [ ] **Step 4: Implement JDBC draft/formal/history repository.** Replace child draft rows as one transaction on draft save, bind JSON as strings accepted by MySQL JSON columns, use `SELECT ... FOR UPDATE` during activation, and delete history with a ranked-ID query so only newest three remain.

- [ ] **Step 5: Implement draft service.** Resolve department/actor from `UserPrincipal`; load ACTIVE department metadata; call `FieldValueValidator` separately for master and every child row; reject unknown/inactive subtypes and cross-department record IDs.

- [ ] **Step 6: Run focused tests and commit.**

```powershell
.\mvnw.cmd -Dtest=RecordDraftServiceTest,JdbcRecordRepositoryTest,RecordSnapshotCodecTest test
cd ..
git diff --check
git add backend/src/main/java/com/example/mdm/record backend/src/test/java/com/example/mdm/record
git commit -m "feat: add validated master and child record drafts"
```

### Task 3: Redis edit-lock lifecycle with version fallback

**Files:**
- Create: `backend/src/main/java/com/example/mdm/record/EditLock.java`
- Create: `backend/src/main/java/com/example/mdm/record/EditLockStore.java`
- Create: `backend/src/main/java/com/example/mdm/record/RedisEditLockStore.java`
- Create: `backend/src/main/java/com/example/mdm/record/EditLockService.java`
- Create: `backend/src/main/java/com/example/mdm/record/EditLockController.java`
- Test: `backend/src/test/java/com/example/mdm/record/EditLockServiceTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/EditLockControllerTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/RedisEditLockStoreTest.java`

**Interfaces:**
- Produces: `acquire(recordId) -> EditLock`, `renew(recordId, token) -> EditLock`, and `release(recordId, token)` at `/api/master-record/{recordId}/lock`.
- Consumes: authenticated department/user, `StringRedisTemplate`, record ownership lookup, and `Duration.ofMinutes(30)`.

- [ ] **Step 1: Write failing lock tests** for first acquisition, same-owner idempotent acquisition, competing-owner 409 with holder/expiry, token-protected renewal/release, 30-minute TTL, department ownership checks, and expired lock replacement.

```java
assertThat(service.acquire(42L).expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(30)));
assertThatThrownBy(() -> otherUser.acquire(42L)).isInstanceOf(BusinessException.class);
```

- [ ] **Step 2: Run tests and confirm RED.**

```powershell
cd backend
.\mvnw.cmd -Dtest=EditLockServiceTest,EditLockControllerTest,RedisEditLockStoreTest test
```

- [ ] **Step 3: Implement atomic Redis operations.** Use one Redis key per record and Lua compare-and-expire/delete scripts; store user ID, display name, department ID, random 256-bit token, and expiry. Never accept user/department identity from request bodies.

- [ ] **Step 4: Implement controller contracts.** `POST` acquires, `PUT` renews with `{token}`, and `DELETE` releases with `{token}`. Return conflict data through the established error envelope without exposing the lock token to other users.

- [ ] **Step 5: Verify and commit.**

```powershell
.\mvnw.cmd -Dtest=EditLockServiceTest,EditLockControllerTest,RedisEditLockStoreTest test
cd ..
git diff --check
git add backend/src/main/java/com/example/mdm/record backend/src/test/java/com/example/mdm/record
git commit -m "feat: add record edit locks"
```

### Task 4: Record submission and approval activation

**Files:**
- Create: `backend/src/main/java/com/example/mdm/record/RecordApprovalRepository.java`
- Create: `backend/src/main/java/com/example/mdm/record/JdbcRecordApprovalRepository.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordApprovalService.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordApprovalController.java`
- Modify: `backend/src/main/java/com/example/mdm/metadata/MetadataApprovalQueryService.java`
- Modify: `backend/src/main/java/com/example/mdm/metadata/MetadataApprovalController.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordApprovalServiceTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordApprovalControllerTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordApprovalSecurityTest.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/MetadataApprovalControllerTest.java`

**Interfaces:**
- Consumes: Task 2 draft/repository/snapshot APIs and Task 3 lock release; existing `approval_tasks` audit columns.
- Produces: submit/approve/reject operations plus a typed task discriminator `METADATA | RECORD` for the combined approval query.

- [ ] **Step 1: Write failing application-service tests** for atomic submit, self-approval denial, department/role denial, CREATE/UPDATE/DELETE activation, base-version conflict, history insertion and three-version trimming, rejected draft immutability, duplicate actions, and lock release after transaction completion.

```java
service.approve(taskId, "looks good");
assertThat(repository.findFormal(recordId).version()).isEqualTo(4L);
assertThat(repository.history(recordId)).extracting(History::version).containsExactly(3L, 2L, 1L);
```

- [ ] **Step 2: Run record approval tests and confirm RED.**

```powershell
cd backend
.\mvnw.cmd -Dtest=RecordApprovalServiceTest,RecordApprovalControllerTest,RecordApprovalSecurityTest test
```

- [ ] **Step 3: Implement transactional submit and activation.** Bind task `entity_type='RECORD'` and `entity_id=draftId`; lock task/draft/formal record rows; verify snapshot IDs against the task; apply all child changes by desired-state replacement; mark formal delete without physical removal; register edit-lock release using transaction synchronization after commit.

- [ ] **Step 4: Expose record actions and combine approval queries.** Add `POST /api/master-record-draft/{draftId}/submit`, `/api/record-approval/{taskId}/approve`, and `/reject`; extend existing list filtering with optional `taskType=METADATA|RECORD` while leaving no-filter metadata clients compatible.

- [ ] **Step 5: Run record and metadata regression tests, then commit.**

```powershell
.\mvnw.cmd -Dtest=RecordApprovalServiceTest,RecordApprovalControllerTest,RecordApprovalSecurityTest,MetadataApprovalControllerTest,MetadataApprovalApplicationServiceTest test
cd ..
git diff --check
git add backend/src/main/java/com/example/mdm backend/src/test/java/com/example/mdm
git commit -m "feat: activate business records through approval"
```

### Task 5: Record query APIs, shared visibility, and dashboard summaries

**Files:**
- Create: `backend/src/main/java/com/example/mdm/record/RecordVisibilityService.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordQueryService.java`
- Create: `backend/src/main/java/com/example/mdm/record/RecordController.java`
- Create: `backend/src/main/java/com/example/mdm/record/DashboardService.java`
- Create: `backend/src/main/java/com/example/mdm/record/DashboardController.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordVisibilityServiceTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordControllerTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordControllerSecurityTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/DashboardControllerTest.java`

**Interfaces:**
- Produces: paged formal-record list/detail/history, draft CRUD/detail/copy/delete request endpoints, and `/api/dashboard/summary`.
- Consumes: Tasks 1–4 services and existing role/department metadata.

- [ ] **Step 1: Write failing visibility tests** proving own-department full values, cross-department shared master fields only, omission of child rows without shared values, partial child-row filtering, deleted-record default exclusion, and no leakage through filters/counts.

- [ ] **Step 2: Write failing controller tests** for pagination/filter contracts, immutable path ownership, draft create/update/copy, logical-delete reason, history limited to three, viewer mutation denial, and request-ID envelopes.

```java
mockMvc.perform(get("/api/master-record").param("masterTypeId", "9").param("page", "0").param("size", "20"))
    .andExpect(jsonPath("$.data.content[0].recordCode").value("CUS-20260805-0001"));
```

- [ ] **Step 3: Write dashboard tests** asserting role-scoped `formalCount`, `myDraftCount`, `pendingApprovalCount`, `activatedThisMonth`, and recent tasks; SUPER_ADMIN receives global administrative totals but no fabricated department draft access.

- [ ] **Step 4: Run tests and confirm RED.**

```powershell
cd backend
.\mvnw.cmd -Dtest=RecordVisibilityServiceTest,RecordControllerTest,RecordControllerSecurityTest,DashboardControllerTest test
```

- [ ] **Step 5: Implement query/visibility/controller services.** Apply visibility before DTO serialization, whitelist sortable columns, cap page size at 100, and derive department/user from security context. Expose `/api/master-record`, `/api/master-record/{id}`, `/history`, `/api/master-record-draft`, and dashboard summary.

- [ ] **Step 6: Run all non-container backend tests and commit.**

```powershell
.\mvnw.cmd test
cd ..
git diff --check
git add backend/src/main/java/com/example/mdm/record backend/src/test/java/com/example/mdm/record
git commit -m "feat: expose visible records and dashboard metrics"
```

### Task 6: Frontend record API, routes, dictionaries, and metadata controls

**Files:**
- Create: `frontend/src/api/records.ts`
- Create: `frontend/src/api/dashboard.ts`
- Test: `frontend/src/api/records.spec.ts`
- Test: `frontend/src/api/dashboard.spec.ts`
- Modify: `frontend/src/api/approval.ts`
- Modify: `frontend/src/api/approval.spec.ts`
- Modify: `frontend/src/api/metadata.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/router/menu.ts`
- Modify: `frontend/src/router/router.spec.ts`
- Modify: `frontend/src/i18n/messages.ts`
- Modify: `frontend/src/components/metadata/FieldEditorDrawer.vue`
- Modify: `frontend/src/components/metadata/MetadataEditor.vue`
- Modify: `frontend/src/components/metadata/MetadataEditor.spec.ts`
- Modify: `frontend/src/views/metadata/MasterTypeListView.vue`
- Modify: `frontend/src/views/metadata/MasterTypeListView.spec.ts`

**Interfaces:**
- Produces: typed record/draft/lock/history/dashboard clients, business-data routes, bilingual message keys, shared-field control, and code-rule administration.
- Consumes: Task 5 API contracts and existing `http`, `Role`, `i18n`, metadata components.

- [ ] **Step 1: Write failing typed-client tests** for list/detail/history, draft create/update/copy/delete/submit, acquire/renew/release lock, dashboard summary, approval `taskType`, and code-rule GET/PUT. Assert exact URLs, methods, bodies, and path-ID authority.

- [ ] **Step 2: Run API tests and confirm RED.**

```powershell
cd frontend
npm test -- --run src/api/records.spec.ts src/api/dashboard.spec.ts src/api/approval.spec.ts src/api/metadata.spec.ts
```

- [ ] **Step 3: Implement TypeScript contracts and clients.** Define `RecordSummary`, `RecordDetail`, `RecordDraft`, `ChildRowDraft`, `EditLock`, `HistorySnapshot`, `Paged<T>`, and `DashboardSummary`; preserve raw dynamic values as `Record<string, unknown>`.

- [ ] **Step 4: Write failing router/i18n/metadata tests** for editor business-data menu, viewer read-only route, approver approval route, bilingual labels, child-field data type column, shared switch default false, and code-rule preview/save.

- [ ] **Step 5: Implement routes/messages and metadata controls.** Add `/records`, `/records/:recordId`, `/records/drafts/:draftId`; display type/shared columns for both field families; add code-rule action to master-type templates without changing enum payloads.

- [ ] **Step 6: Verify and commit.**

```powershell
npm test -- --run src/api/records.spec.ts src/api/dashboard.spec.ts src/api/approval.spec.ts src/router/router.spec.ts src/components/metadata/MetadataEditor.spec.ts src/views/metadata/MasterTypeListView.spec.ts
npm run build
cd ..
git diff --check
git add frontend/src/api frontend/src/router frontend/src/i18n/messages.ts frontend/src/components/metadata frontend/src/views/metadata
git commit -m "feat: add record clients and metadata controls"
```

### Task 7: Modular business-data tables and dynamic master/child editor

**Files:**
- Create: `frontend/src/views/records/RecordListView.vue`
- Create: `frontend/src/views/records/RecordDetailView.vue`
- Create: `frontend/src/views/records/RecordEditorView.vue`
- Create: `frontend/src/components/records/RecordFilters.vue`
- Create: `frontend/src/components/records/DynamicMasterForm.vue`
- Create: `frontend/src/components/records/DynamicChildTable.vue`
- Create: `frontend/src/components/records/RecordStatusTag.vue`
- Create: `frontend/src/components/records/RecordHistoryTable.vue`
- Test: `frontend/src/views/records/RecordViews.spec.ts`
- Test: `frontend/src/components/records/DynamicRecordEditor.spec.ts`
- Test: `frontend/src/components/records/RecordHistoryTable.spec.ts`
- Modify: `frontend/src/styles/main.css`

**Interfaces:**
- Consumes: Task 6 clients, routes, dictionaries, ACTIVE metadata API, and auth roles.
- Produces: record table/search/pagination, dynamic master/sub editors, draft actions, delete flow, edit-lock lifecycle, detail/diff/history tabs.

- [ ] **Step 1: Write failing list/detail tests** for table columns, filters, pagination, deleted toggle, role-based actions, loading/empty/error states, request IDs, current/draft/history tabs, and maximum three rendered versions.

- [ ] **Step 2: Write failing dynamic-editor tests** for every field type, required/select validation, unknown values, child subtype tabs, add/delete/reorder rows, unchanged payload enums, generated-code read-only behavior, repeated save, submit freeze, and delete reason.

- [ ] **Step 3: Write failing lock tests** using fake timers: acquire on edit, renew before expiry, read-only conflict with holder/expiry, release on cancel/submit/unmount, and version-conflict refresh guidance.

- [ ] **Step 4: Run record UI tests and confirm RED.**

```powershell
cd frontend
npm test -- --run src/views/records/RecordViews.spec.ts src/components/records/DynamicRecordEditor.spec.ts src/components/records/RecordHistoryTable.spec.ts
```

- [ ] **Step 5: Implement focused components.** Keep list and editor separate; build Element Plus controls from `FieldType`; keep child values keyed by stable client row IDs; deep-copy API data; never pass user values through `t()` or `v-html`.

- [ ] **Step 6: Implement lock lifecycle and safe navigation.** Store the returned token only in component memory, renew at a safe interval shorter than TTL, release best-effort, and warn before abandoning dirty drafts.

- [ ] **Step 7: Verify and commit.**

```powershell
npm test -- --run src/views/records/RecordViews.spec.ts src/components/records/DynamicRecordEditor.spec.ts src/components/records/RecordHistoryTable.spec.ts
npm run build
cd ..
git diff --check
git add frontend/src/views/records frontend/src/components/records frontend/src/styles/main.css
git commit -m "feat: add dynamic business data workspace"
```

### Task 8: Combined approval center and populated dashboard

**Files:**
- Modify: `frontend/src/views/DashboardView.vue`
- Create: `frontend/src/views/DashboardView.spec.ts`
- Create: `frontend/src/components/dashboard/SummaryMetrics.vue`
- Create: `frontend/src/components/dashboard/RecentTasksTable.vue`
- Modify: `frontend/src/views/approval/ApprovalListView.vue`
- Modify: `frontend/src/views/approval/ApprovalDetailView.vue`
- Modify: `frontend/src/views/approval/ApprovalViews.spec.ts`
- Create: `frontend/src/components/approval/RecordSnapshotDiff.vue`
- Create: `frontend/src/components/approval/RecordSnapshotDiff.spec.ts`
- Modify: `frontend/src/components/approval/ApprovalActionBar.vue`
- Modify: `frontend/src/i18n/messages.ts`
- Modify: `frontend/src/styles/main.css`

**Interfaces:**
- Consumes: Tasks 4–7 dashboard, approval, snapshot, and route contracts.
- Produces: role-aware dashboard, metadata/record approval tabs, horizontal single-row audit flow, and safe master/child table diffs.

- [ ] **Step 1: Write failing dashboard tests** for the four metrics, recent-task rows, role-scoped shortcuts, loading/empty/error states, request IDs, and live Chinese/English switching.

- [ ] **Step 2: Write failing approval tests** for task type tabs, preserved metadata flow, record list/detail, horizontal audit row, master-field diff, child-row add/change/delete/reorder, raw-value safety, self-approval error, 409 refresh, and bilingual labels.

- [ ] **Step 3: Run tests and confirm RED.**

```powershell
cd frontend
npm test -- --run src/views/DashboardView.spec.ts src/views/approval/ApprovalViews.spec.ts src/components/approval/RecordSnapshotDiff.spec.ts src/components/approval/SnapshotDiff.spec.ts
```

- [ ] **Step 4: Implement dashboard as an independent overview module.** Fetch one summary endpoint, render metric cards and a compact recent-task table, and route shortcuts rather than embedding editor/approval forms.

- [ ] **Step 5: Implement combined approval UI.** Retain existing metadata `SnapshotDiff`; dispatch record tasks to `RecordSnapshotDiff`; render audit events in one horizontal row; use text interpolation only and validate snapshot schema/task binding before diffing.

- [ ] **Step 6: Run focused and full frontend verification, then commit.**

```powershell
npm test -- --run src/views/DashboardView.spec.ts src/views/approval/ApprovalViews.spec.ts src/components/approval/RecordSnapshotDiff.spec.ts src/components/approval/SnapshotDiff.spec.ts
npm test -- --run
npm run build
cd ..
git diff --check
git add frontend/src/views/DashboardView.vue frontend/src/views/DashboardView.spec.ts frontend/src/components/dashboard frontend/src/views/approval frontend/src/components/approval frontend/src/i18n/messages.ts frontend/src/styles/main.css
git commit -m "feat: add record approvals and dashboard insights"
```

### Task 9: End-to-end hardening and final acceptance

**Files:**
- Test: `backend/src/test/java/com/example/mdm/record/RecordWorkflowIntegrationTest.java`
- Test: `backend/src/test/java/com/example/mdm/record/RecordWorkflowSecurityIntegrationTest.java`
- Create: `.superpowers/sdd/2026-08-05-business-data-entry-implementation/final-report.md` (workspace ledger; do not commit unless repository policy tracks `.superpowers`)

**Interfaces:**
- Consumes: all earlier task contracts.
- Produces: verified editor-to-approver workflow and regression evidence.

- [ ] **Step 1: Write integration scenarios** for editor creating a master record with multiple child rows, submit, approver approve, formal data visibility, cross-department shared filtering, update/delete approval, lock conflict, version conflict, self-approval denial, and history trimming after four approved changes.

- [ ] **Step 2: Run integration tests and require them to pass before continuing.**

```powershell
cd backend
.\mvnw.cmd -Dtest=RecordWorkflowIntegrationTest,RecordWorkflowSecurityIntegrationTest test
```

Expected: PASS. A failure returns ownership to the task that defines the failing contract; add the regression assertion to that task's named test file before changing its named production file.

- [ ] **Step 3: Run complete automated verification.**

```powershell
cd backend
.\mvnw.cmd test
cd ..\frontend
npm test -- --run
npm run build
cd ..
git diff --check
```

- [ ] **Step 4: Perform real-service smoke verification** with MySQL and Redis running: log in as editor, save a master/child draft, verify lock conflict from a second editor session, submit, approve as approver, read as viewer, and confirm a foreign department receives only shared fields. Record request IDs and outcomes without recording tokens or passwords.

- [ ] **Step 5: Scan scope and safety.** Confirm no `v-html` handles record values, no request body controls actor/department/path IDs, no physical delete endpoint exists, metadata approval regressions pass, history count is at most three, and `git status --short` contains no unintended files.

- [ ] **Step 6: Commit integration coverage and request independent final review.**

```powershell
git add backend/src/test/java/com/example/mdm/record
git commit -m "test: verify business data workflow end to end"
```

Resolve every Critical and Important review finding with a focused failing test, rerun Step 3, and document final commits/test counts in the workspace report.
