# SimpleMDM Frontend Management Console Design

## Goal

Deliver a modern, role-aware SimpleMDM web console covering authentication, system administration, department metadata editing and verification, and department metadata approval.

## Scope

The implementation includes:

- Login, logout, JWT session restoration, and authorization failure handling.
- A role-aware application shell and navigation.
- Department, user, and fixed-role administration.
- Super-admin master-type template creation and department assignment.
- Department ACTIVE metadata verification and metadata change submission.
- Department metadata approval task listing, detail, approval, and rejection.
- The missing backend approval task HTTP API required by the frontend.

Record-data approval is explicitly out of scope. The approval center handles metadata changes only.

## Technology

- Vue 3 and TypeScript.
- Vue Router for routes and guards.
- Pinia for authentication and shared page state.
- Axios for API access.
- Element Plus for forms, tables, drawers, dialogs, feedback, and layout primitives.
- Vitest and Vue Test Utils for frontend tests.

## Frontend Architecture

The frontend is split into focused modules:

- `auth`: login, logout, token/session handling, user, roles, and department.
- `router`: public/private routes, role metadata, login guards, forbidden page, and not-found page.
- `layout`: sidebar, header, user menu, breadcrumb, and main content shell.
- `system`: department, user, and role administration.
- `metadata`: master-type templates, department assignment, ACTIVE metadata verification, and change submission.
- `approval`: pending metadata tasks, snapshot differences, approval, and rejection.
- `api`: typed clients, Bearer token injection, request ID propagation, response unwrapping, and error mapping.
- `stores`: Pinia stores with explicit persistence boundaries.

Business rules remain in backend services. Views coordinate typed API calls and present state; they do not duplicate authorization or approval rules.

## Authentication and Session

Login calls `POST /api/auth/login`. On success, the frontend stores the access token, user, roles, and department in Pinia and `sessionStorage`. Refreshing the page restores that session; closing the browser removes it.

Every protected API request sends `Authorization: Bearer <token>` and a generated `X-Request-Id`. A 401 response clears the session and redirects to login. Logout calls `POST /api/auth/logout` before clearing local state; local state is still cleared when the logout request fails.

The configured backend JWT expiration remains authoritative. No refresh-token behavior is invented because the backend does not expose refresh tokens.

## Role Navigation and Access

Only authorized menu entries are rendered.

- `SUPER_ADMIN`: dashboard, departments, users, roles, and master-type templates/assignments.
- `DEPT_EDITOR`: dashboard and department metadata. The metadata module contains both ACTIVE verification and change submission.
- `DEPT_APPROVER`: dashboard, metadata approval center, and read-only ACTIVE metadata.
- `DEPT_VIEWER`: dashboard and read-only ACTIVE metadata.

Routes also declare role requirements. Hidden menus are not the security boundary: guards prevent accidental navigation, and backend authorization remains definitive.

## Page Design

### Login

The login page uses a light blue-gray background and a centered white card with SimpleMDM branding, username and password inputs, validation feedback, and a primary login button. It is usable on desktop and mobile.

### Application Shell

The desktop-first shell uses a dark blue-gray sidebar, white header, and light-gray content background. The header shows breadcrumb context, department where applicable, and the current-user/logout menu.

### System Administration

- Departments: list, create, edit, activate, and deactivate.
- Users: list, create, edit, activate/deactivate, assign department, and assign fixed roles.
- Roles: read-only list of the fixed backend roles.
- Master types: list templates, create templates, and assign templates to departments.

Lists use a title/action row, compact filters, Element Plus tables, and drawers for create/edit operations. Destructive or state-changing actions require confirmation.

### Department Metadata

The module has two explicit tabs:

1. `Current active version`: read-only ACTIVE master fields, sub-types, and sub-fields. This is available to editors, approvers, and viewers so they can verify approved data.
2. `Submit changes`: available only to `DEPT_EDITOR`. It starts from the current ACTIVE structure and supports field/sub-type creation, removal, ordering, required flags, field types, and selection options.

Submission returns and displays the approval task ID. It never changes or visually replaces the ACTIVE tab. After an approval, reopening or refreshing ACTIVE data shows the approved version.

### Metadata Approval

The approval center is available only to `DEPT_APPROVER`. It lists the current department's metadata tasks and provides a detail view with before/after snapshots. Differences use green for additions, red for removals, and yellow for modifications.

Approval accepts an optional comment. Rejection requires a nonblank reason. Completed or stale tasks surface a 409 response and refresh their displayed status. Cross-department tasks are rejected by the backend with 403.

## Backend Approval API

Add a focused metadata approval controller and repository queries that expose:

- A current-department task list, with status filtering.
- Task detail containing safe snapshot data and audit metadata.
- Approve action delegating to `MetadataApprovalApplicationService.approve`.
- Reject action delegating to `MetadataApprovalApplicationService.reject`.

Department and operator identity always come from `UserPrincipal`; request payloads cannot select them. List/detail access requires `DEPT_APPROVER` and the same department. Existing transactional locking, fingerprint validation, and atomic application remain in the application service.

## API and Error Handling

The API client unwraps the existing `ApiResponse<T>` envelope and retains `requestId` for support diagnostics.

- 400: display field-level validation where possible and a business notification.
- 401: clear the session and navigate to login.
- 403: navigate to the forbidden page or show an action-level denial.
- 404: show a not-found state.
- 409: explain duplicate assignment, pending-task conflict, stale snapshot, or completed-task state.
- Unexpected failures: show a generic error containing the request ID.

Loading states prevent duplicate submissions. Mutating actions are disabled while requests are in flight.

## Responsive Behavior

Desktop is the primary management target. Mobile supports login, navigation, dashboard, ACTIVE metadata viewing, and basic approval inspection. Dense editing tables may scroll horizontally; advanced metadata editing is not optimized for small screens.

## Testing

Frontend coverage includes:

- Login success/failure, session restoration, logout, and 401 cleanup.
- Role menus and route guards.
- API envelope/error handling and request ID propagation.
- Department, user, role, and master-type administration requests.
- Editor ACTIVE verification and change submission without ACTIVE mutation.
- Approval list/detail, approve/reject, 403, and 409 behavior.
- Production TypeScript and Vite build.

Backend coverage includes:

- Approval list/detail Controller routes and response envelopes.
- Real security-filter coverage for approver role and department isolation.
- Approval/rejection delegation and existing transaction regressions.

## Delivery Order

1. Frontend foundation, API client, authentication, routes, and application shell.
2. System administration pages.
3. Master-type and department metadata pages, including editor ACTIVE verification.
4. Backend metadata approval HTTP API and frontend approval center.
5. Cross-module verification, frontend tests/build, backend tests, and review.

## Non-Goals

- Refresh tokens or long-lived browser sessions.
- Dynamic role creation.
- Record-data approval.
- A generic low-code CRUD engine.
- Full mobile optimization for metadata editing.
