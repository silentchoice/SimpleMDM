# Codex Resume Handoff

## Repository

- Remote: `https://github.com/silentchoice/SimpleMDM.git`
- Branch: `feat/dynamic-master-sub-fields`
- Worktree used on the original computer: `C:\Users\qc\simple-mdm\.worktrees\dynamic-master-sub-fields`
- Last completed commit before the paused work: `4663ef785b01f86846e29c40ff340c9d16f657e4`

## Current status

- Tasks 1-9 are complete and independently reviewed.
- Task 10 is paused during fix round 3.
- Task 10 round 2 is committed at `4663ef7`.
- Round 3 was adding real HTTP security and tenant contract tests plus tighter integration-log snapshot authorization.
- Do not reset or discard the paused changes.
- Tasks 11 and 12 have not started.

Paused working changes at handoff:

- Modified: `backend-java/src/test/java/com/simplemdm/controller/IntegrationControllerTest.java`
- Added: `backend-java/src/test/java/com/simplemdm/controller/HttpSecurityContractTest.java`

## Resume instructions

On another computer:

```powershell
git clone https://github.com/silentchoice/SimpleMDM.git
cd SimpleMDM
git fetch --all
git switch feat/dynamic-master-sub-fields
git status --short
```

Then tell Codex:

> Continue the SimpleMDM relational rebuild from `codex/RESUME.md`. Resume Task 10 fix round 3. Inspect the existing WIP first; do not reset or discard it. Finish Task 10 review, then execute Tasks 11-12 according to the approved plan without asking for routine choices.

## Authoritative project documents

- Plan: `docs/superpowers/plans/2026-07-31-relational-generic-mdm-rebuild.md`
- Specification: `docs/superpowers/specs/2026-07-31-relational-generic-mdm-redesign.md`
- Decision record: `docs/decisions/2026-07-31-relational-mdm-implementation-decisions.md`
- Local-only progress ledger: `.superpowers/sdd/2026-07-31-relational-generic-mdm-rebuild/progress.md`

The `.superpowers` directory is intentionally ignored and will not transfer through Git. The tracked plan, specification, decision record, and this handoff file are sufficient to resume.

## Remaining work

### Task 10 fix round 3

- Complete real HTTP tests for unauthenticated `401` and unauthorized `403` behavior.
- Verify current-system list isolation and cross-system approval detail `404`.
- Verify typed approval-change JSON and approve HTTP identity semantics.
- Restrict integration log `request_snapshot` to an explicitly authorized detail endpoint; ordinary lists must not expose it.
- Strengthen endpoint URL parsing to require HTTP(S) and a non-empty host.
- Run the complete backend and frontend suites, production build, diff check, and independent review.

### Task 11

- Destructively rebuild only the authorized local `simple-mdm` MySQL database using Flyway.
- Database password was supplied out of band; never commit it or write it into this file.
- Verify schema, bootstrap data, non-null department relationships, APIs, backend tests, frontend tests, and production build.

### Task 12

- Complete the implementation decision record.
- Perform final review and verification.
- Synchronize only the Java backend, frontend, README, and approved tracked documentation to the publication branch/remote.
- Never publish Python backend history, secrets, or `.superpowers` internal files.

## Default execution policy

- Follow the approved plan continuously.
- Do not ask for routine technical choices; use the preferred safe default.
- Preserve unrelated or paused work.
- Require evidence from tests and review before declaring a task complete.
