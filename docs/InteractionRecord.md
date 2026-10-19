# Current project handoff

## Active benchmark — AI search contract and validator

Owner authorised immediate sequential multiagent-search then singleagent-search on
2026-10-20. multiagent-search was created from aac5548 before contract edits.
AI-P1/AI-T1 and BENCH-P1 are in existing workflow specs/plan. Independent common
setup is writing the fixed evaluation suite and behavior-free stub; measured arms
have not started. Same frozen checkpoint/tests and Luna implementation model for
both; no SoC/database/UI calls. Deadline:21October2026,14:00Singapore. Workers
return messages only; results will be consolidated once, not per-agent logs.

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
