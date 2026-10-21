# Java/JPro PostgreSQL conversion

Revision: 2026-10-19 draft, baseline jpro-docker at f15b076.
This is a bounded change to the existing application, not a renewed Next.js migration.
Bounded conversion scope approved by the owner on 2026-10-19 for technical design. Technical design and implementation readiness remain separate gates.

## Product description and users

Retain the JavaFX/JPro marketplace and its existing buyer/seller workflows while
moving application records to shared PostgreSQL. The owner selected Render,
Supabase PostgreSQL and Supabase Storage in this conversation. This task concerns
the database portion; it does not establish that hosted multi-user operation works.

## Requirements and acceptance

| ID | Requirement | Status | Intended acceptance evidence |
| --- | --- | --- | --- |
| PG-01 | Use PostgreSQL for hosted use, local development and database tests; no SQLite fallback. | Explicitly approved in user reply, 2026-10-19 | Application and integration tests use PostgreSQL; missing configuration fails clearly rather than creating SQLite. |
| PG-02 | Preserve existing marketplace persistence semantics, including binary password material, timestamp precision, ownership references and transaction rollback. | Approved 2026-10-19 | PostgreSQL-backed regression tests exercise relevant existing services, constraints and rollback. |
| PG-03 | Persist records across application restarts without destructive startup reset. | Approved 2026-10-19 | Restart against the same disposable database preserves written records. |
| PG-04 | Keep database credentials server-side and use only isolated disposable local databases for automated tests. | Standing safety constraint | Configuration/error checks do not expose secrets; test setup refuses hosted destinations. |

## UI

No marketplace screen redesign is proposed. Database startup failure feedback
must reflect the configured database rather than claim a SQLite directory issue.
Exact messages remain design work. User/session isolation is an integration
dependency to inspect, not something proved by changing database engines.

## Scope and exclusions

Approved scope: connection configuration, JDBC dependency, schema compatibility,
repository SQL, runtime integration, PostgreSQL-backed tests and setup documentation.
Preserve Java 25 and Gradle 9.1.0. Keep tester, implementer and reviewer ownership.
Supabase Storage implementation, deployment, Git publication and unrelated UI work
are excluded from this database task. Existing files and staged changes are preserved.

## Open decisions and readiness

1. Resolved 2026-10-19: create and upgrade the schema through a separate setup command before deployment (explicit user choice). Normal application startup does not apply schema migrations.
2. Resolved 2026-10-19: start with fresh PostgreSQL data; no SQLite data import is required (explicit user choice).
3. Resolved 2026-10-19: automated database tests use disposable local Docker PostgreSQL containers (explicit user choice). Docker is a test prerequisite; no hosted test target or SQLite fallback.
4. Specify safe concurrent transaction/migration behavior and the interface with JPro startup.

PG-01 through PG-04, PG-T1 and PG-P1 execution/staged follow-ups are approved. Narrow configuration/connection/TLS work is independently accepted; full schema/runtime readiness remains gated. Docker fixtures and real PostgreSQL tests have run; full application/build/deployment acceptance has not. Current handoff and evidence are in workflow/PLAN.md and the feature SDD summary.
