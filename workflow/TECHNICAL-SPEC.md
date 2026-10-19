# Java/JPro PostgreSQL conversion — technical design

Revision: PG-T1, 2026-10-19. Baseline: jpro-docker, f15b076.
Status: PG-T1 approved by owner for task planning on 2026-10-19; implementation approval remains separate.
Source: approved bounded scope in PRODUCT-SPEC.md, PG-01–PG-04.

## Scope and architecture

Retain Java 25, Gradle 9.1.0, JavaFX/JPro, repositories and services. Replace
SQLite persistence in production and tests. Fresh database; no record import.
Supabase Storage, deployment and hosted multi-user acceptance are separate work.
Coordinator owns this spec/plan. Under the owner-approved consolidated delivery
workflow, one implementer owns production, tests/fixtures/configuration and guides;
specification and security reviewers independently assess the completed candidate.

## Connections and configuration — PG-01, PG-04

Proposed dependencies: org.postgresql:postgresql:42.7.14 and Testcontainers
2.0.5 PostgreSQL/JUnit Jupiter modules. Remove sqlite-jdbc after conversion.
Versions are documented upstream; Java 25 compatibility still requires actual builds.
Use PostgreSQL 17 as proposed local/test major; confirm the target Supabase major
before final image pinning and hosted compatibility acceptance.

HOTSHOP_DB_URL is a credential-free jdbc:postgresql URL; HOTSHOP_DB_USER and
HOTSHOP_DB_PASSWORD are separate server environment values. Reject missing values,
non-PostgreSQL URLs and credentials embedded in URLs. Do not print configuration,
raw driver errors or credential-bearing exception chains in startup logs.
Hosted connections use verify-full and configured provider trust; isolated local
containers may disable TLS. Use JDBC connectTimeout=10 seconds and socketTimeout=30 seconds (approved correction2); close connections after
work. Retain DriverManager initially, avoiding an unnecessary pool dependency.
Connection capacity/load testing remains a hosted acceptance gate.

## Schema and provisioning — PG-01, PG-02, PG-03

Create a PostgreSQL-specific initial schema from the final state of all six SQLite
scripts. Preserve historical SQLite scripts without loading them at runtime.
Fresh initialization omits SQLite randomblob/hex conversation backfill: no old rows
exist to backfill. No startup seeding or reset; initial data is empty.

Keep textual UUIDs, enums and normalized usernames to preserve current bindings.
Use BYTEA for password salt/hash. Use BIGINT for epoch-millisecond timestamp columns,
message sequences and read counters: PostgreSQL INTEGER is too narrow for Java long.
Keep bounded price/order fields INTEGER. Preserve nullability, foreign keys, checks,
conversation uniqueness and all existing partial unique indexes. Preserve application
Clock and current millisecond precision, with no database-generated domain timestamps.

SQLite rowid supplies an insertion-order tiebreaker in cancellation-request and
meetup-rescheduling history queries. Preserve that existing behavior in PostgreSQL
with an internal BIGINT GENERATED ALWAYS AS IDENTITY history_order column in each
of those two tables, ordered after created_at. This is storage ordering only;
domain timestamps and message/read counters remain application-owned. Cover tied
application timestamps in regression tests. Initial-conversion discovery recorded
2026-10-19; no new user-facing behavior or legacy-data import.

Command contract: ./gradlew migrateDatabase, using operator configuration. Failed provisioning exits nonzero with the sanitised diagnostic "Database provisioning failed" and no raw driver exception or connection/credential values (PG019 acceptance contract; implementation pending). An explicit
provisioning entry point acquires a transaction advisory lock before creating/reading
schema history. Apply pending migrations and history atomically; rollback on failure.
Repeated setup is a no-op; reject newer schema versions. Keep scripts simple enough
for the existing SQL execution mechanism, or parse them deliberately before extending
SQL syntax; do not silently split function bodies on semicolons.

Normal startup only validates schema/version with read-only queries. Missing, older
or newer schema fails with sanitised guidance to run compatible provisioning. Never
create tables, migrate, reset or seed during ApplicationRuntime.open.

## Transaction semantics — PG-02

Retain one caller-owned connection per service transaction, rollback on any failure,
and close on exit. Service transaction isolation: SERIALIZABLE. Migration control uses READ_COMMITTED under its exclusive transaction advisory lock so waiting provisioners read the committed history after acquiring the lock (owner-approved PG018 stage). Preserve existing database
constraints as independent integrity protection. Do not automatically replay callbacks:
image operations and mutable objects make retries unsafe without a separate audit.
Serialization/deadlock failures return a sanitised recoverable storage error instructing
the user to retry. Concurrent username uniqueness violations must retain the existing
username-taken result through SQLSTATE 23505 and the specific constraint, not classify
all constraint errors as duplicate names. Test failures and unrelated SQL errors separately.

Acceptance requires separate-connection competing offer acceptance, booking and chat
sequence tests; at most one conflicting winner, no partial loser state. This does not
claim full hosted concurrency acceptance without the JPro/storage integration gate.

## Runtime interface and integration boundary — PG-01, PG-03

Keep open(Path, Clock) as environment-configured convenience; add explicit database
configuration injection for deterministic tests. Path now selects local image storage,
not the database. Replace startup migrate with version validation. Main must distinguish
database setup/connectivity failure from local folder/lock failure without leaking secrets.

Source evidence: Main owns an instance runtime; runtime owns its session and worker.
Runtime also takes an exclusive directory lock and runs image recovery. ManagedImages
scans unreferenced files and deletes them, which can race another runtime's upload.
Owner approved per-browser authentication/UI state and server-owned shared storage with
coordinated cleanup. Do not remove the lock or invent session-specific image directories
as part of JDBC conversion. Preserve the existing single-runtime safety until a separate
approved lifecycle/storage task replaces it. Hosted multi-user readiness stays blocked
on two-browser isolation, shared-image visibility and upload-versus-cleanup tests.

## Tests and verification — PG-01–PG-04

Use Testcontainers fixture-generated local credentials, never environment/hosted URLs.
One container per class may host a fresh database per test; disable persistent reuse.
Provision before service tests. Keep non-database tests independent of Docker.
Missing Docker is a setup failure, never skipped coverage or a SQLite fallback.
Replace direct SQLite accesses and sqlite_master checks with PostgreSQL equivalents.

Cover fresh/repeated/failed/concurrent provisioning; supported/older/newer/missing
schema startup; credential redaction; binary credentials; millisecond timestamps,
nullable values and long counters; username uniqueness; foreign keys and partial
indexes; transaction rollback; restart persistence; competing operations. Retain
service regression assertions and test independent fixtures/cleanup.

Required wrapper checks: test, checkstyleMain, checkstyleTest, check, build, shadowJar.
CI already runs these on ubuntu-latest; add explicit Docker readiness before container
tests. Under the owner-approved workflow amendment, test readiness and final
specification/coverage assessment are combined at the stable candidate, together
with final security review. Do not weaken valid tests.

## Executed checks and readiness

Source/schema/repository/CI inspection and Git baseline check completed. Docker CLI
located in the per-user installation. Client/server 29.8.2 are now reachable. PG-001 fixture verification passed: two smoke tests, plus rerun with unreachable application settings and container cleanup. No production conversion or full application verification is claimed.

Database design is owner-approved for planning. Target PostgreSQL major/image pin and
Docker readiness remain explicit acceptance prerequisites. JPro/storage is a separate
integration dependency, not evidence against independent PostgreSQL implementation.
PG-P1 execution was approved on 2026-10-19; no production worker
may start without approved plan assignments and working prerequisite contracts.
The owner-approved delivery override in PLAN.md supersedes intermediate review gates.

## Primary references checked

- https://jdbc.postgresql.org/download/ and https://jdbc.postgresql.org/ — JDBC version.
- https://jdbc.postgresql.org/documentation/ssl/ — certificate/hostname verification.
- https://www.postgresql.org/docs/16/transaction-iso.html — serializable failures.
- https://www.postgresql.org/docs/17/explicit-locking.html — advisory locking.
- https://java.testcontainers.org/quickstart/junit_5_quickstart/ — JUnit fixtures/version.

## Buyer AI search — AI-T1, 2026-10-20

Source:PRODUCT-SPEC.md AI-P1/AI-01..07 and AC-AI-01..05. Owner explicitly requested
contract+validator benchmark now on two sequential branches. Current implementation
baseline:aac5548. Contract/test scaffold checkpoint will be committed before runs.
PostgreSQL design above is unaffected. Live SoC/UI design remains provisional.

### First slice: pure proposal validation

New public final hotshop.service.AiSearchProposalValidator with private constructor:
public static ListingSearch validate(Map<?, ?> proposal).
Caller:future SoC response adapter; output:existing ListingSearch. No DB/session/env,
clock, filesystem, network, logging, UI, mutable global state or dependency additions.
Unknown keys and invalid values fail with the fixed exception contract in AI-P1;
never stringify untrusted values into diagnostics. No partial output or input mutation.
Use existing Category,Condition,ListingSort and ListingDetails.MAX_PRICE_CENTS.
Copy conditions into an immutable set; do not coerce types or truncate values.
The exact map/default/Unicode/numeric rules are normative in AI-P1, not duplicated here.

### Trust boundary and traceability

| Source criteria | Mechanism and check |
| --- | --- |
| AI-01,AC-AI-02 | Exact key/enum/type allowlists; reject SQL/action/identity/status fields. |
| AI-02..04,AC-AI-01..03 | Pure all-or-nothing conversion, safe error, integral range checks and Unicode code-point length after strip. |
| AI-05,AC-AI-04 | Defensive condition copy, immutable result and unchanged input collections. |
| AC-AI-05 | Identical common checkpoint, read-only evaluation suite and same Luna implementation model for both arms. |
| AI-06..07 | Deferred SoC adapter and preview UI; benchmark does not establish runtime LLM safety or end-to-end authorization. |

A literal title is data. The validator does not execute SQL/prompts or authorize
search access. Existing ListingService.searchListings remains the authorization
boundary in later integration, unchanged by this slice. No schema/data changes.

### Verification and benchmark protocol

Independent common setup author writes a behavior-free API stub and fixed JUnit5
suite before either run. Expected red must reach UnsupportedOperationException,
not compiler/setup failure. Shared setup time is excluded from measured arms.
Freeze contract, test content/hashes and source checkpoint, then create both branches
from that same checkpoint. Root stays in multiagent-search; singleagent-search uses
an isolated ignored worktree under tools/benchmark, with its own build outputs.
Run multi-agent first, single-agent second. Never transfer implementation code,
review findings, failure hints or results between arms. New workers inherit no chat.

Both implementation agents use Luna and identical assignment/allowed files. Each
owns only the validator; fixed test/spec edits are prohibited. They may inspect/run
those tests and self-verify. Multi-agent adds coordinator plus independent final
specification/coverage and security reviewers; single-agent has no development review.
The common evaluator scores both submitted candidates against the unchanged suite.
First-submission scores are primary. One correction pass per arm is a ceiling, not
a requirement or implicit authorization; post-review fixes need owner-approved plan.
Preserve initial outcomes even if a correction is separately authorised.

Checks for this small CPU-only slice:wrapper test filtered to
hotshop.service.AiSearchProposalValidatorTest, checkstyleMain and checkstyleTest.
Reset per-worktree build outputs consistently; Gradle dependency cache may remain warm.
No20-minute unrelated PostgreSQL/full-app suite is needed for this isolated benchmark;
full feature integration will retain normal repository completion gates later.

Metrics:full acceptance pass count/total; tagged security pass count/total; build/style
status; first-candidate and end-to-end workflow elapsed seconds; implementation,
verification and review phase times where separable; agent count, human interventions
and correction rounds. Record token/credit usage only if runtime exposes actual data,
otherwise N/A. Record order/cache/model limitations. No statistical performance claim
from one task and one run per workflow. Total preparation is reported separately.
No artificial defects, hand-picked reruns or unmeasured claims of agent improvement.

### Later integration and blockers

Future sequence:SoC client (reuse agreed seller-client boundary), JSON/response adapter,
async buyer preview/apply UI, then service/UI/security integration checks. Endpoint,
model, request budget shared with seller feature, timeout and quota policies require
SoC guide and owner agreement before that slice. No Supabase/demo automated tests.
