# SimpleMDM Frontend Bilingual Localization Design

## Goal

Provide complete Simplified Chinese and English localization for the existing management console without changing backend behavior, Redis configuration, Dashboard functionality, routes, permissions, or business workflows.

## Scope

- Default the interface to Simplified Chinese (`zh-CN`).
- Add a persistent Chinese/English switch in the authenticated application header and on the login page.
- Store only the language preference in `localStorage`; authentication continues to use `sessionStorage`.
- Localize all user-visible frontend-owned text in login, navigation, layout, system administration, metadata management, and metadata approval views and components.
- Switch the Element Plus locale together with the application locale.
- Preserve backend-provided business-error messages verbatim and continue displaying their request IDs.
- Keep the existing empty Dashboard behavior unchanged.

## Out of Scope

- Redis availability, token-revocation behavior, and backend configuration.
- New Dashboard cards, statistics, health checks, or shortcuts.
- Backend message localization.
- Route-path changes, API changes, permission changes, or data-model changes.

## Architecture

Add `vue-i18n` as the localization runtime. A focused i18n module owns:

- the supported locale type (`zh-CN | en-US`);
- locale restoration from `localStorage`;
- the default locale (`zh-CN`);
- the Chinese and English message dictionaries;
- a setter that updates both the reactive locale and persisted preference.

The root application installs the i18n plugin. Element Plus receives the locale object selected from the same reactive locale, ensuring tables, pagination, dialogs, and validation widgets use the same language as application text.

Components use stable semantic translation keys rather than inline bilingual conditionals. Repeated actions and status labels live in shared namespaces; page-specific copy remains grouped by feature.

## Interaction Design

The language switch is visible before and after authentication:

- Login page: compact language control near the login card heading.
- Authenticated shell: header control beside the current-user/logout area.

Switching language updates the visible page immediately without navigation or reload. The selected language survives browser restarts. Missing or invalid stored values fall back to Simplified Chinese.

## Error Handling

Frontend validation, empty-state, confirmation, and loading messages are translated. Backend response messages are displayed exactly as received because the backend is authoritative and currently emits one language. Request IDs remain appended in the existing format so support diagnostics are not lost.

## Testing

Tests will prove:

- Simplified Chinese is the default when no valid preference exists.
- A saved English preference is restored.
- Switching language updates representative login, menu, system, metadata, and approval text and persists the choice.
- Element Plus receives the matching locale.
- Backend error messages and request IDs remain unchanged.
- Existing role, routing, API, and business-flow tests continue to pass.

The final verification set is the full frontend test suite and production build. No backend changes or backend test rerun are required for this frontend-only change.
