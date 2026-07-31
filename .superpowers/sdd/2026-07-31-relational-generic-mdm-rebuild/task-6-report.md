# Task 6 Report: Generic Metadata and Record HTTP APIs

## Status

Implemented Task 6 API adapters and focused contract tests.

## Implementation

- Added JWT-scoped system, department-tree, object-type metadata, and generic MDM record controllers.
- Record creation and main-record updates resolve the object type within the authenticated user's system and delegate to `RecordService`, which owns authenticated actor lookup and persisted-department authorization.
- Main-record reads restrict records to `AuthorizationService.viewableDepartmentIds`, load field definitions once and record values with `findByRecordIdIn`, then project typed values into the snake_case record response shape.
- Added snake_case `CreateRecordRequest` and `RecordResponse` DTOs.
- Child GET/POST endpoints validate parent system/type context; POST delegates to the existing child creation API. Child PUT is mapped and checks JWT/system/department edit access before returning 501 because the prior `RecordService` interface supplies no child-update method.

## Verification

- RED: `./mvnw.cmd -Dtest=MdmRecordControllerTest,RecordResponseJsonTest test` failed at test compilation because `MdmRecordController`, `RecordResponse`, and the scoped object-type finder did not exist.
- GREEN: the same focused command passed: 2 tests, 0 failures, 0 errors, 0 skipped.
- `git diff --check` passed.

## Concerns

- The inherited Task 5 service contract has no child-record update operation. The required child PUT route is present, authenticated, and explicit about this 501 limitation rather than accepting an ambiguous write.
- Maven continues to warn about the pre-existing malformed user-level settings XML and the test JVM emits its pre-existing bootstrap-classpath sharing warning; neither affected the focused suite.