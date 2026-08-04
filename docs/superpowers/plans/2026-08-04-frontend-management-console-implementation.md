# SimpleMDM Frontend Management Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a role-aware Vue management console for authentication, system administration, department metadata verification/editing, and metadata approval.

**Architecture:** A modular Vue SPA uses typed API clients, a Pinia session store, route metadata, and Element Plus views. Backend business rules remain authoritative; the only new backend surface is a department-scoped metadata approval query/action controller delegating to the existing transactional application service.

**Tech Stack:** Vue 3.4, TypeScript 5.5, Vue Router, Pinia, Axios, Element Plus, Vitest, Vue Test Utils, Java 17, Spring Boot 3.3, Spring JDBC, JUnit 5, MockMvc.

## Global Constraints

- Use `sessionStorage`; closing the browser ends the frontend session.
- Render only menu entries authorized for the current role, while retaining backend authorization as the security boundary.
- `DEPT_EDITOR` must have both ACTIVE metadata verification and change-submission views.
- Metadata submission never changes ACTIVE definitions; approval is the only activation path.
- Approval center covers metadata tasks only, not record-data approval.
- Preserve the existing `ApiResponse<T>` envelope and surface `requestId` in errors.
- Keep desktop as the primary management target; login and read-only views remain usable on mobile.
- Docker-dependent tests remain optional; all non-container tests and frontend production build must pass.
- Preserve the user's uncommitted `backend/src/main/resources/application.yml` changes unless explicitly included by the user.

---

### Task 1: Frontend foundation, authentication, and application shell

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/main.ts`
- Replace: `frontend/src/App.vue`
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/api/types.ts`
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/router/menu.ts`
- Create: `frontend/src/layouts/AppLayout.vue`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/DashboardView.vue`
- Create: `frontend/src/views/ForbiddenView.vue`
- Create: `frontend/src/views/NotFoundView.vue`
- Create: `frontend/src/styles/main.css`
- Test: `frontend/src/api/http.spec.ts`
- Test: `frontend/src/stores/auth.spec.ts`
- Test: `frontend/src/router/router.spec.ts`
- Test: `frontend/src/views/LoginView.spec.ts`

**Interfaces:**
- Produces: `http`, `ApiEnvelope<T>`, `ApiError`, `useAuthStore()`, `Role`, `AppRouteMeta`, and authenticated router/layout used by every later task.
- Consumes: `POST /api/auth/login`, `POST /api/auth/logout`, and the existing login response `{ accessToken, user, roles, department }`.

- [ ] **Step 1: Install runtime dependencies** with `npm install vue-router@4 pinia@2 axios@1 element-plus@2 @element-plus/icons-vue@2`; verify `package-lock.json` records exact resolved versions.

- [ ] **Step 2: Write failing authentication-store tests** proving login persistence, refresh restoration, logout cleanup, and role checks.

```ts
it('restores the session from sessionStorage', () => {
  sessionStorage.setItem('mdm.session', JSON.stringify(sessionFixture))
  const store = useAuthStore()
  store.restore()
  expect(store.token).toBe('jwt')
  expect(store.hasRole('DEPT_EDITOR')).toBe(true)
})
```

- [ ] **Step 3: Write failing API-client tests** in `frontend/src/api/http.spec.ts` proving Bearer and `X-Request-Id` headers, envelope unwrapping, request ID retention, and 401 session cleanup. Use an injected Axios adapter rather than real network requests.

- [ ] **Step 4: Run** `npm test -- --run src/stores/auth.spec.ts src/api/http.spec.ts`; expect missing-module failures.

- [ ] **Step 5: Implement typed API and auth store.** Define:

```ts
export type Role = 'SUPER_ADMIN' | 'DEPT_EDITOR' | 'DEPT_APPROVER' | 'DEPT_VIEWER'
export interface ApiEnvelope<T> { code: number; message: string; data: T; requestId: string }
export interface Session { accessToken: string; user: LoginUser; roles: Role[]; department: DepartmentRef | null }
```

`http` must attach the session token, generate a request ID when absent, unwrap `data`, and throw `ApiError(status, message, requestId)`.

- [ ] **Step 6: Write failing router/menu tests** proving unauthenticated redirects, authenticated login redirects, forbidden role routes, and exact role menus including `DEPT_EDITOR` ACTIVE verification.

- [ ] **Step 7: Implement routes and menus.** Route meta uses `roles?: Role[]`; a global guard calls `auth.restore()` once and enforces authentication and roles. Do not rely on hidden menus alone.

- [ ] **Step 8: Write failing login component tests** for validation, disabled loading state, failed-login message with request ID, and successful redirect.

- [ ] **Step 9: Implement the modern login view and shell** using Element Plus. The shell contains sidebar, breadcrumb/header, department label, current user, and logout action. Replace the malformed placeholder text in `App.vue`.

- [ ] **Step 10: Run** `npm test -- --run` and `npm run build`; then commit:

```bash
git add frontend/package.json frontend/package-lock.json frontend/src
git commit -m "feat: add frontend authentication shell"
```

### Task 2: Department, user, and role administration

**Files:**
- Create: `frontend/src/api/system.ts`
- Create: `frontend/src/views/system/DepartmentListView.vue`
- Create: `frontend/src/views/system/UserListView.vue`
- Create: `frontend/src/views/system/RoleListView.vue`
- Create: `frontend/src/components/system/DepartmentDrawer.vue`
- Create: `frontend/src/components/system/UserDrawer.vue`
- Modify: `frontend/src/router/index.ts`
- Test: `frontend/src/api/system.spec.ts`
- Test: `frontend/src/views/system/SystemViews.spec.ts`

**Interfaces:**
- Consumes: Task 1 `http` and `Role`; backend `/api/department`, `/api/user`, and `/api/role` endpoints.
- Produces: `systemApi`, `Department`, `SystemUser`, and fixed-role administration routes for `SUPER_ADMIN`.

- [ ] **Step 1: Write failing typed-client tests** for department list/create/update/status, user list/create/update/status/roles, and role list. Assert exact URLs, methods, query parameters, and bodies.

- [ ] **Step 2: Run** `npm test -- --run src/api/system.spec.ts`; expect missing `systemApi`.

- [ ] **Step 3: Implement `systemApi`** with exact controller contracts. Status calls use `PATCH /api/{resource}/{id}/status?status=ACTIVE|DISABLED`; user roles use `PUT /api/user/{id}/roles`.

- [ ] **Step 4: Write failing component tests** proving lists render, create/edit drawers submit validated data, status changes require confirmation, user department/role assignment is sent, and 409 errors show request IDs.

- [ ] **Step 5: Implement the three administration views.** Roles are read-only. Drawers reset on close, prevent duplicate submit, and refresh list data only after successful mutation.

- [ ] **Step 6: Add `SUPER_ADMIN` routes and verify a department role cannot navigate to them.** Run `npm test -- --run src/views/system/SystemViews.spec.ts src/router/router.spec.ts`.

- [ ] **Step 7: Run all frontend tests/build**, then commit:

```bash
git add frontend/src/api frontend/src/views/system frontend/src/components/system frontend/src/router
git commit -m "feat: add system administration views"
```

### Task 3: Master-type templates and department assignment

**Files:**
- Create: `frontend/src/api/metadata.ts`
- Create: `frontend/src/views/metadata/MasterTypeListView.vue`
- Create: `frontend/src/components/metadata/MasterTypeDrawer.vue`
- Create: `frontend/src/components/metadata/DepartmentAssignmentDialog.vue`
- Modify: `frontend/src/router/index.ts`
- Test: `frontend/src/api/metadata.spec.ts`
- Test: `frontend/src/views/metadata/MasterTypeListView.spec.ts`

**Interfaces:**
- Consumes: `http`, `systemApi.listDepartments()`, `/api/master-type` GET/POST, and `/api/master-type/{masterTypeId}/departments/{departmentId}` PUT.
- Produces: `metadataApi.listMasterTypes/createMasterType/assignDepartment` and the super-admin template route.

- [ ] **Step 1: Write failing API tests** for exact master-type URLs and assignment path authority.

- [ ] **Step 2: Implement metadata types and template client:**

```ts
export interface MasterType { id: number; code: string; name: string; status: 'ACTIVE' | 'DISABLED' }
async function assignDepartment(masterTypeId: number, departmentId: number): Promise<void>
```

- [ ] **Step 3: Write failing view tests** for template list, validated creation, department selection, successful assignment refresh, and duplicate assignment 409.

- [ ] **Step 4: Implement the template list, create drawer, and assignment dialog.** Do not allow a request body to override path IDs.

- [ ] **Step 5: Run targeted/all frontend tests and build**, then commit:

```bash
git add frontend/src/api/metadata.ts frontend/src/views/metadata frontend/src/components/metadata frontend/src/router
git commit -m "feat: manage master type templates"
```

### Task 4: Department ACTIVE metadata verification and editor submissions

**Files:**
- Modify: `frontend/src/api/metadata.ts`
- Create: `frontend/src/views/metadata/DepartmentMetadataView.vue`
- Create: `frontend/src/components/metadata/ActiveMetadataPanel.vue`
- Create: `frontend/src/components/metadata/MetadataEditor.vue`
- Create: `frontend/src/components/metadata/FieldEditorDrawer.vue`
- Modify: `frontend/src/router/index.ts`
- Test: `frontend/src/api/metadata.spec.ts`
- Test: `frontend/src/views/metadata/DepartmentMetadataView.spec.ts`
- Test: `frontend/src/components/metadata/MetadataEditor.spec.ts`

**Interfaces:**
- Consumes: Task 3 `metadataApi`; GET/POST `/api/master-field/{masterTypeId}`, `/api/sub-type/{masterTypeId}`, and `/api/sub-field/{subTypeId}`.
- Produces: ACTIVE verification for editor/approver/viewer and editor-only submission returning `{ approvalTaskId: number }`.

- [ ] **Step 1: Write failing API tests** for all three ACTIVE reads and submissions. Define `FieldDefinition`, `SubType`, `FieldType`, and `ApprovalSubmission` to match backend JSON exactly.

- [ ] **Step 2: Implement typed metadata API methods**, preserving ordered arrays and sending owner IDs only through URL paths.

- [ ] **Step 3: Write failing ACTIVE-panel tests** proving editor, approver, and viewer can inspect the same read-only structure; assert there is no save button and that refresh reloads all three levels.

- [ ] **Step 4: Write failing editor tests** for code regex, required/name/type validation, select options, duplicate codes/orders, reordering, and submission task-ID feedback. Assert the editor begins with a deep copy and never mutates ACTIVE props.

- [ ] **Step 5: Implement the two-tab metadata view.** `Current active version` is always visible to department roles; `Submit changes` is rendered only for `DEPT_EDITOR`.

- [ ] **Step 6: Implement focused editors** for master fields, sub-types, and sub-fields. Submit the selected entity family independently and reload ACTIVE only when the user explicitly refreshes or after approval, never immediately after submission.

- [ ] **Step 7: Run targeted/all frontend tests and build**, then commit:

```bash
git add frontend/src/api/metadata.ts frontend/src/views/metadata frontend/src/components/metadata frontend/src/router
git commit -m "feat: add department metadata workspace"
```

### Task 5: Backend metadata approval query and action API

**Files:**
- Modify: `backend/src/main/java/com/example/mdm/metadata/MetadataApprovalRepository.java`
- Modify: `backend/src/main/java/com/example/mdm/metadata/JdbcMetadataApprovalRepository.java`
- Create: `backend/src/main/java/com/example/mdm/metadata/MetadataApprovalQueryService.java`
- Create: `backend/src/main/java/com/example/mdm/metadata/MetadataApprovalController.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/MetadataApprovalControllerTest.java`
- Test: `backend/src/test/java/com/example/mdm/metadata/MetadataApprovalControllerSecurityTest.java`

**Interfaces:**
- Consumes: `MetadataApprovalApplicationService.approve(long,String)` and `.reject(long,String)`, `AuthorizationService`, and approval task storage.
- Produces:
  - `GET /api/metadata-approval?status=PENDING`
  - `GET /api/metadata-approval/{taskId}`
  - `POST /api/metadata-approval/{taskId}/approve` with `{ "comment": string|null }`
  - `POST /api/metadata-approval/{taskId}/reject` with `{ "reason": string }`

- [ ] **Step 1: Write failing repository/query tests** for department-scoped list/detail summaries including task ID, entity kind/ID, status, snapshots, submitter/reviewer audit fields, and timestamps. Foreign department detail must be 403; absent ID must be 404.

- [ ] **Step 2: Add repository projections and SQL** where every list/detail query binds the authenticated department. Use an existence check to distinguish 403 from 404 without returning foreign snapshot contents.

- [ ] **Step 3: Write failing MockMvc route tests** for response envelopes, status filtering, detail, approve, reject reason validation, 404, and 409 propagation.

- [ ] **Step 4: Implement query service and controller:**

```java
@GetMapping
ApiResponse<List<ApprovalTaskView>> list(@RequestParam(defaultValue = "PENDING") String status, ...)

@PostMapping("/{taskId}/approve")
ApiResponse<Void> approve(@PathVariable long taskId, @Valid @RequestBody ApproveRequest body, ...)
```

The service derives department from `UserPrincipal` and requires `DEPT_APPROVER`; the controller never accepts department or reviewer IDs.

- [ ] **Step 5: Add security-enabled MockMvc tests** with the real JWT filter proving approver success, editor/viewer/anonymous denial, and cross-department 403 through the real query boundary.

- [ ] **Step 6: Run** `mvnw.cmd -Dtest=MetadataApprovalControllerTest,MetadataApprovalControllerSecurityTest test`, then all non-container backend tests and `git diff --check`.

- [ ] **Step 7: Commit:**

```bash
git add backend/src/main/java/com/example/mdm/metadata backend/src/test/java/com/example/mdm/metadata
git commit -m "feat: expose metadata approval API"
```

### Task 6: Frontend metadata approval center and final verification

**Files:**
- Create: `frontend/src/api/approval.ts`
- Create: `frontend/src/views/approval/ApprovalListView.vue`
- Create: `frontend/src/views/approval/ApprovalDetailView.vue`
- Create: `frontend/src/components/approval/SnapshotDiff.vue`
- Create: `frontend/src/components/approval/ApprovalActionBar.vue`
- Modify: `frontend/src/router/index.ts`
- Test: `frontend/src/api/approval.spec.ts`
- Test: `frontend/src/views/approval/ApprovalViews.spec.ts`
- Test: `frontend/src/components/approval/SnapshotDiff.spec.ts`

**Interfaces:**
- Consumes: Task 5 approval API and Task 1 auth/router/error handling.
- Produces: approver-only list/detail/diff/action UI; approved ACTIVE data is visible through Task 4 refresh.

- [ ] **Step 1: Write failing approval API tests** for list status query, detail, optional approve comment, mandatory reject reason, and 403/404/409 errors with request IDs.

- [ ] **Step 2: Implement `approvalApi` and safe snapshot types.** Treat snapshots as untrusted JSON: malformed envelopes render an error state and never execute HTML.

- [ ] **Step 3: Write failing diff tests** for additions, removals, modifications, unchanged rows, and ordered definitions. Match definitions by stable `code`; escape all displayed values.

- [ ] **Step 4: Implement `SnapshotDiff`** with green/red/yellow states and a raw-JSON fallback for unsupported schema versions.

- [ ] **Step 5: Write failing list/detail tests** proving only approvers see routes, filters refresh the list, approval accepts a comment, rejection requires a reason, actions disable while pending, and 409 refreshes task status.

- [ ] **Step 6: Implement approval views and action bar.** After successful approval, navigate back to the list and invalidate metadata ACTIVE caches; after rejection, refresh the task and list.

- [ ] **Step 7: Run the complete verification set:**

```powershell
cd frontend
npm test -- --run
npm run build
cd ..\backend
.\mvnw.cmd -q clean test '-Dtest=!ArchitectureSmokeTest'
cd ..
git diff --check
```

- [ ] **Step 8: Request independent code review** and resolve all Critical/Important findings. Re-run the full verification commands after the final fix.

- [ ] **Step 9: Commit:**

```bash
git add frontend backend/src/main/java/com/example/mdm/metadata backend/src/test/java/com/example/mdm/metadata
git commit -m "feat: add metadata approval console"
```
