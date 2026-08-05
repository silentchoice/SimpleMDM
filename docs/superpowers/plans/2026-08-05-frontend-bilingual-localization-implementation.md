# SimpleMDM Frontend Bilingual Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Localize the existing SimpleMDM frontend in Simplified Chinese and English, defaulting to Chinese with a persistent runtime language switch.

**Architecture:** `vue-i18n` owns application messages and locale persistence. `App.vue` binds the same reactive locale to Element Plus through `el-config-provider`; feature components consume semantic translation keys while backend messages and request IDs remain verbatim.

**Tech Stack:** Vue 3.4, TypeScript 5.5, vue-i18n 10, Element Plus 2, Vitest, Vue Test Utils.

## Global Constraints

- Default locale is exactly `zh-CN`; the only other supported locale is `en-US`.
- Persist locale under `localStorage['mdm.locale']`; do not change authentication `sessionStorage` behavior.
- Preserve backend-provided error messages verbatim and preserve `requestId` formatting.
- Do not modify Redis, backend code, Dashboard functionality, API contracts, routes, permissions, or business workflows.
- Every existing frontend test and the production build must pass.

---

### Task 1: Localization runtime and language controls

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/src/i18n/index.ts`
- Create: `frontend/src/i18n/messages.ts`
- Create: `frontend/src/i18n/i18n.spec.ts`
- Create: `frontend/src/components/LanguageSwitcher.vue`
- Create: `frontend/src/components/LanguageSwitcher.spec.ts`
- Modify: `frontend/src/main.ts`
- Replace: `frontend/src/App.vue`
- Modify: `frontend/src/views/LoginView.vue`
- Modify: `frontend/src/views/LoginView.spec.ts`
- Modify: `frontend/src/layouts/AppLayout.vue`
- Modify: `frontend/src/layouts/AppLayout.spec.ts`

**Interfaces:**
- Produces: `SupportedLocale`, `SUPPORTED_LOCALES`, `i18n`, `setLocale(locale)`, `currentElementLocale`, and `<LanguageSwitcher />`.
- Consumes: `localStorage`, Vue app installation, Element Plus `zhCn` and `en` locale objects.

- [ ] **Step 1: Install the runtime dependency**

Run:

```powershell
cd frontend
npm install vue-i18n@10
```

Verify `package-lock.json` records the resolved package and no unrelated dependency changes.

- [ ] **Step 2: Write failing runtime tests**

Create tests that clear `localStorage` before each case and assert:

```ts
expect(resolveInitialLocale()).toBe('zh-CN')
localStorage.setItem('mdm.locale', 'en-US')
expect(resolveInitialLocale()).toBe('en-US')
localStorage.setItem('mdm.locale', 'invalid')
expect(resolveInitialLocale()).toBe('zh-CN')
setLocale('en-US')
expect(localStorage.getItem('mdm.locale')).toBe('en-US')
```

Run `npm test -- --run src/i18n/i18n.spec.ts`; expect missing-module failures.

- [ ] **Step 3: Implement the focused i18n runtime**

Use these exact public types and storage rules:

```ts
export type SupportedLocale = 'zh-CN' | 'en-US'
export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export const LOCALE_STORAGE_KEY = 'mdm.locale'

export function resolveInitialLocale(): SupportedLocale {
  const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
  return stored === 'en-US' || stored === 'zh-CN' ? stored : 'zh-CN'
}

export function setLocale(locale: SupportedLocale): void {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_STORAGE_KEY, locale)
}
```

Export `currentElementLocale` as a computed value selecting Element Plus `zhCn` for `zh-CN` and `en` for `en-US`. Install `i18n` in `main.ts`. Wrap the router view in `App.vue`:

```vue
<el-config-provider :locale="currentElementLocale">
  <router-view />
</el-config-provider>
```

Run the runtime test; expect PASS.

- [ ] **Step 4: Write failing language-switch tests**

Assert the switcher initially exposes `English` under Chinese locale, clicking it changes representative text to English, persists `en-US`, and then exposes `中文`. Add login/layout tests proving both locations render the control and update their text without navigation.

Run:

```powershell
npm test -- --run src/components/LanguageSwitcher.spec.ts src/views/LoginView.spec.ts src/layouts/AppLayout.spec.ts
```

Expect failures because the component and translation keys do not exist.

- [ ] **Step 5: Implement controls and core dictionaries**

Create a button-based switcher with `data-testid="language-switcher"`, accessible label from `common.switchLanguage`, and exact visible alternatives `English` / `中文`. Add these core keys in both locales:

```ts
// zh-CN values
common: { switchLanguage: '切换语言', requestId: '请求 ID：{id}' }
auth: { console: '管理控制台', username: '用户名', password: '密码', signIn: '登录', signingIn: '登录中…', required: '请输入用户名和密码', localLogout: '已在本地退出，无法确认服务器退出状态。' }
layout: { menu: '菜单', global: '全局', signOut: '退出登录', mainNavigation: '主导航' }

// en-US values
common: { switchLanguage: 'Switch language', requestId: 'Request ID: {id}' }
auth: { console: 'Management Console', username: 'Username', password: 'Password', signIn: 'Sign in', signingIn: 'Signing in…', required: 'Username and password are required', localLogout: 'Signed out locally. Server sign-out could not be confirmed.' }
layout: { menu: 'Menu', global: 'Global', signOut: 'Sign out', mainNavigation: 'Main navigation' }
```

Use `t()` for frontend validation while retaining backend `apiError.message`; append request IDs with `t('common.requestId', { id })`.

- [ ] **Step 6: Verify and commit Task 1**

Run targeted tests, `npm test -- --run`, `npm run build`, and `git diff --check`. Commit:

```powershell
git add frontend/package.json frontend/package-lock.json frontend/src/i18n frontend/src/components/LanguageSwitcher.vue frontend/src/components/LanguageSwitcher.spec.ts frontend/src/main.ts frontend/src/App.vue frontend/src/views/LoginView.vue frontend/src/views/LoginView.spec.ts frontend/src/layouts/AppLayout.vue frontend/src/layouts/AppLayout.spec.ts
git commit -m "feat: add bilingual frontend runtime"
```

### Task 2: Navigation and system-administration localization

**Files:**
- Modify: `frontend/src/i18n/messages.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/router/menu.ts`
- Modify: `frontend/src/router/router.spec.ts`
- Modify: `frontend/src/views/DashboardView.vue`
- Modify: `frontend/src/views/ForbiddenView.vue`
- Modify: `frontend/src/views/NotFoundView.vue`
- Modify: `frontend/src/views/system/DepartmentListView.vue`
- Modify: `frontend/src/views/system/UserListView.vue`
- Modify: `frontend/src/views/system/RoleListView.vue`
- Modify: `frontend/src/components/system/DepartmentDrawer.vue`
- Modify: `frontend/src/components/system/UserDrawer.vue`
- Modify: `frontend/src/views/system/SystemViews.spec.ts`

**Interfaces:**
- Consumes: Task 1 `i18n` and `t()`.
- Produces: route title keys, menu label keys, and fully localized system-administration UI.

- [ ] **Step 1: Write failing navigation/system tests**

Update menu expectations to assert Chinese defaults (`仪表盘`, `当前元数据`, `提交变更`, `审批中心`, `主数据类型模板`, `用户管理`, `部门管理`, `角色管理`) and an English switch restores the existing English labels. Add system view assertions for translated headings, actions, confirmations, empty/error states, validation text, and status labels.

Run `npm test -- --run src/router/router.spec.ts src/views/system/SystemViews.spec.ts`; expect current English strings.

- [ ] **Step 2: Replace route/menu display strings with keys**

Store route metadata as `titleKey` and menu definitions as `labelKey`; resolve them reactively at render time so switching locale does not require rebuilding the router. Preserve all paths, names, and role arrays exactly.

Add route keys under `routes.*` and menu keys under `menu.*`. `AppLayout` resolves `route.meta.titleKey` through `t()`.

- [ ] **Step 3: Localize static and system views**

Add complete `dashboard`, `errors`, and `system` dictionaries. Translate every frontend-owned heading, column, button, form label, confirmation, validation, loading, status, empty-state, and success/error wrapper. Preserve usernames, department names, role codes, backend messages, and request IDs as data.

Use shared exact status translations:

```ts
status: {
  ACTIVE: '启用', DISABLED: '停用', PENDING: '待审批', APPROVED: '已批准', REJECTED: '已拒绝'
}
```

and English equivalents `Active`, `Disabled`, `Pending`, `Approved`, `Rejected`.

- [ ] **Step 4: Verify and commit Task 2**

Run targeted tests, full frontend tests/build, and diff check. Commit:

```powershell
git add frontend/src/i18n/messages.ts frontend/src/router frontend/src/views/DashboardView.vue frontend/src/views/ForbiddenView.vue frontend/src/views/NotFoundView.vue frontend/src/views/system frontend/src/components/system
git commit -m "feat: localize navigation and system administration"
```

### Task 3: Metadata and approval localization with final verification

**Files:**
- Modify: `frontend/src/i18n/messages.ts`
- Modify: `frontend/src/views/metadata/DepartmentMetadataView.vue`
- Modify: `frontend/src/views/metadata/MasterTypeListView.vue`
- Modify: `frontend/src/components/metadata/ActiveMetadataPanel.vue`
- Modify: `frontend/src/components/metadata/DepartmentAssignmentDialog.vue`
- Modify: `frontend/src/components/metadata/FieldEditorDrawer.vue`
- Modify: `frontend/src/components/metadata/MasterTypeDrawer.vue`
- Modify: `frontend/src/components/metadata/MetadataEditor.vue`
- Modify: `frontend/src/views/approval/ApprovalListView.vue`
- Modify: `frontend/src/views/approval/ApprovalDetailView.vue`
- Modify: `frontend/src/components/approval/ApprovalActionBar.vue`
- Modify: `frontend/src/components/approval/SnapshotDiff.vue`
- Modify: corresponding metadata and approval `*.spec.ts` files

**Interfaces:**
- Consumes: Task 1 runtime and Task 2 shared status/action keys.
- Produces: fully localized metadata and approval workflows with unchanged payloads and safety behavior.

- [ ] **Step 1: Write failing metadata localization tests**

Assert Chinese defaults and English switching for ACTIVE/submit tabs, refresh, master fields, sub-types, sub-fields, add/edit/remove/reorder, assignment, validation, approval-task feedback, malformed-assignment errors, and request-ID formatting. Ensure tests continue asserting that submission payloads and owner IDs are unchanged.

Run metadata component/view tests; expect current English strings.

- [ ] **Step 2: Localize metadata views and components**

Add `metadata.*` translations and replace every frontend-owned literal. Keep field codes, field types, JSON values, API messages, request IDs, IDs, and user-entered names unchanged. Translate labels for field types, statuses, tabs, and actions only at display time; never translate request payload enum values.

- [ ] **Step 3: Write failing approval localization tests**

Assert Chinese defaults and English switching for list filters, audit labels, detail actions, rejection validation, diff states (`新增`, `删除`, `修改`, `未变化`), malformed snapshot, unsupported schema fallback, pending state, and 409 refresh errors. Retain existing XSS, ordering, task-binding, and concurrency assertions.

Run approval tests; expect current English strings.

- [ ] **Step 4: Localize approval views and safe diff presentation**

Add `approval.*` translations and replace labels only. `SnapshotDiff` continues using text interpolation and the existing runtime validators; raw JSON data and user values are never passed through message lookup or HTML rendering.

- [ ] **Step 5: Run final verification and commit**

Run:

```powershell
cd frontend
npm test -- --run
npm run build
cd ..
git diff --check
```

Manually scan Vue templates for remaining user-visible English literals while excluding HTML attributes, role/API enum values, test fixtures, and backend messages. Commit:

```powershell
git add frontend/src/i18n/messages.ts frontend/src/views/metadata frontend/src/components/metadata frontend/src/views/approval frontend/src/components/approval
git commit -m "feat: localize metadata and approval workflows"
```

Request independent code review. Resolve every Critical/Important finding, rerun the final verification commands, and confirm no backend or Redis files changed.
