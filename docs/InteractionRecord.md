# Current project handoff

## PostgreSQL and LLM merge follow-up — 2026-10-21

Owner approved combined runtime overloads, shared-account seeding instead of SQLite
copies, and migration of every test runtime opening to openTestRuntime. All direct
test-source ApplicationRuntime.open calls are now confined to PostgresTestSupport.
Tests retain clocks, owners and synthetic failure config; the live adapter owns a
disposable fixture. Two pending requests use distinct accounts in the shutdown test.
Storage snapshots cover PostgreSQL rows/sequences and local files.
Final read-only test review clear; decisions/results consolidated in the existing
PostgreSQL feature summary.
compileTestJava, checkstyleMain and checkstyleTest pass. Wrapper test with both style
tasks and check build --continue failed: 684 executed, 60 failures with Docker
unavailable. Docker command is absent from PATH; Testcontainers finds no valid
environment. Assembly completed, but check/build did not pass. No live LLM run.
Remaining document/build merge entries are outside this change; edits are unstaged.
Developer Guide cleanup removes work-history-only sections and audit paragraphs,
including old check results, exercise/handoff reports and machine-specific setup
history. Standing workflow and technical guidance remain; its merge markers are
intentionally retained pending further review. Documentation-only diff reviewed;
no application checks rerun.

## PostgreSQL conversion — accepted locally, 2026-10-20

Workspace E:/CS3227-2610-MP3; branch postgres-migration, based on f15b076 plus reviewed
conversion. PostgreSQL-only runtime/tests, separate migrateDatabase provisioning,
read-only startup validation and updated guides are complete. Final746 tests have
zero failures/errors/skips; wrapper test/styles/check/build/shadowJar exit0 with
completed outputs UP-TO-DATE. JAR contains PostgreSQL and no SQLite classes.
Final Luna specification/coverage and security reviews clear; no test reruns by
reviewers. Earlier one listing-setup failure did not recur; cause unconfirmed.
Historical correction budget3/3 exhausted; no additional cycle granted.

## Supabase connection — preparation in progress

Owner reports PostgreSQL17.11, matching local test major17 (different patch).
Downloaded public CA certificate copied and SHA256 verified at
C:/Users/jonat/.postgresql/prod-supabase.cer; original download preserved.
JDBC URL needs sslmode=verify-full and sslrootcert pointing to that file.
Data API disabling recommended for dedicated JDBC-only project; owner has not
confirmed disabling it. No hosted connection, provisioning or trust-chain check run.
Next: configure local environment privately, provision and manually verify persistence.
Automated tests remain on disposable Docker databases; Storage/JPro deployment separate.

## Minimal records and workflow

One implementer owns approved code/tests/guides; reviews happen at the final candidate.
Future batches allow one approved correction pass. New specialists use Luna with
compact briefs/no history; Sol coordinator preference saved, live model not inferred.
Workers return messages, not Markdown logs. Coordinator keeps this short handoff and
consolidates final approvals/results once in the existing feature summary after reviews.
Owner explicitly requested deletion of old logs/histories on2026-10-20; no archive kept.
Final acceptance: [existing summary](../logs/2026-10-19-feature-pg-conversion-sdd-summary.md).
Owner authorised commit and push to new branch postgres-migration on2026-10-20.
Publication is in progress. web/.env.local is untracked, unread and excluded;
.gitignore now excludes .env.local files. No deployment authorised or performed.
