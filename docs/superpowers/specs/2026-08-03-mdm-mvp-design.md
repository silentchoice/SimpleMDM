# MDM MVP Design

**Date:** 2026-08-03

## Goal

Build a runnable master-data-management MVP covering authentication and RBAC, departments, metadata-driven master/sub records, approval, cross-department field filtering, edit locks, and scheduled HTTP synchronization.

## Scope

The first release includes Vue 3 + TypeScript + Vite + Element Plus, Java 17 + Spring Boot 3.x + MyBatis-Plus, MySQL 8.0, Redis, JWT authentication, Docker Compose, REST synchronization with API Key or Basic Auth, cron scheduling, execution logs, and retry management.

RabbitMQ, Kafka, Elasticsearch, email, OAuth2, incremental-sync cursors, and reusable sharing templates are explicitly deferred.

## Architecture

Use a modular monolith. The backend is one Spring Boot deployment split into `auth`, `system`, `metadata`, `record`, `approval`, `sync`, and `common` packages with explicit service interfaces. The frontend is a separate Vue application. MySQL is the source of truth; Redis provides edit locks and synchronization mutual exclusion.

Repository layout:

```text
codex/
├─ backend/
├─ frontend/
├─ deploy/
├─ docs/
└─ docker-compose.yml
```

## Modules

- `auth`: login, logout, JWT parsing, current-user context, menu permissions.
- `system`: departments, users, four fixed roles, role assignment, enable/disable operations.
- `metadata`: master types, department assignment, master fields, sub types, sub fields, and field-schema validation.
- `record`: drafts, approved records, history, edit locks, dynamic-value validation, and visibility filtering.
- `approval`: immutable before/after snapshots, submission, approval, rejection, and atomic application of approved changes.
- `sync`: approved synchronization configurations, manual execution, dynamic cron registration, HTTP delivery, logs, retries, and stop controls.
- `common`: response envelope, exception mapping, error codes, auditing, security helpers, and JSON utilities.

## Authorization

The fixed roles are `SUPER_ADMIN`, `DEPT_APPROVER`, `DEPT_EDITOR`, and `DEPT_VIEWER`. Authorization combines role and department checks at the service boundary; frontend menu filtering is presentation only.

Editors modify data and sync configurations belonging to their department. Approvers act only on submissions from their department. Super administrators manage system configuration and may inspect or stop work across departments. Cross-department master records expose all approved fields; cross-department sub records expose only fields whose effective `share_config` value is true. Filtering occurs on the backend before serialization.

## Metadata and Records

Business records use fixed indexed columns plus JSON extension fields. Metadata defines field code, label, type, required flag, options, order, and the sub-field default sharing flag. The backend validates unknown fields, required values, field types, and select-like option membership.

Pending changes never overwrite approved data. Editors save drafts and submit immutable before/after snapshots. Approval atomically applies the snapshot, increments the version, writes history, and releases the lock. Rejection retains the draft and rejection reason while leaving the approved version unchanged. Only approved, non-deleted data is eligible for synchronization.

Edit locks expire after 30 minutes. Redis is authoritative for active lock ownership; MySQL records lock audit data. Conflicting locks or optimistic-version mismatches return HTTP 409.

## Synchronization

Synchronization configurations support manual or cron schedules, `FULL`, `BATCH`, or `SINGLE` selection, REST delivery, and API Key or Basic Auth. Credentials are encrypted at rest using an environment-provided application key and are never returned by read APIs.

Only approved `ACTIVE` configurations are scheduled. The scheduler registers and replaces cron tasks when configuration state changes. A Redis lock prevents concurrent execution of the same configuration. Each run creates a stable snapshot containing approved master records and their visible associated sub records.

Failed HTTP delivery enters a persistent retry queue. Retry delays are 1 minute, 2 minutes, 5 minutes, 60 minutes, and 60 minutes, for at most five retry attempts. Success, terminal failure, and operator stop are persisted. Department editors may stop retries for their own department; super administrators may stop any retry.

## API and Errors

Retain the source design's `/api/auth`, `/api/user`, `/api/role`, `/api/master-type`, `/api/master-field`, `/api/sub-type`, `/api/sub-field`, `/api/master-record`, `/api/sub-record`, `/api/approval`, `/api/sync-config`, `/api/sync-log`, and `/api/sync-retry` families. Add department, edit-lock, field-schema, and cron-validation endpoints.

Responses use `{ code, message, data, requestId }`. Invalid input returns 400, missing authentication 401, forbidden access 403, missing resources 404, conflicts 409, and unexpected server errors 500. Logs include `requestId`, actor, department, operation, and business identifier without secrets.

## Frontend

The initial UI contains login, system management, metadata configuration, master-data list and dynamic forms, approval center with snapshot differences, synchronization configuration, synchronization logs, and retry queue. Dynamic components are selected from an allowlisted field-type map. The client displays backend validation errors but does not duplicate authorization decisions.

## Deployment

Docker Compose starts MySQL 8.0, Redis, backend, and frontend with health checks. Local Maven and npm startup remain supported. Initial administrator credentials and the credential-encryption key come from environment variables; no default plaintext password is committed.

## Testing and Acceptance

Backend tests use JUnit 5, Spring Boot Test, and Testcontainers for MySQL and Redis. Frontend tests use Vitest and Vue Test Utils. Tests cover role/department authorization, metadata validation, approval transactions, history, locks, cross-department filtering, cron lifecycle, HTTP authentication, retry transitions, and secret masking.

An integration test proves the primary flow: create draft, submit, approve, trigger scheduled synchronization, receive a successful target response, and query the resulting log. A failure-path test proves five retries and terminal failure. Docker Compose health checks must pass, backend and frontend test suites must pass, and production builds must complete.

## Delivery Order

Implement a vertical foundation first (project setup, database migration, authentication, and system entities), then metadata, records and visibility, approval and locks, synchronization, frontend workflows, and full-stack verification. Every behavioral change follows red-green-refactor testing.
