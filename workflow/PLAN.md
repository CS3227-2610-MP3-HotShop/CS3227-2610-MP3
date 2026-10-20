# PostgreSQL implementation plan

Revision PG-P1, 2026-10-19. Sources: [product scope](PRODUCT-SPEC.md) and
[PG-T1 technical design](TECHNICAL-SPEC.md), approved by owner for planning in this chat.
Baseline jpro-docker at f15b076. Owner approved execution of PG-001 through PG-010 on 2026-10-19;
Bounded local PostgreSQL conversion accepted2026-10-20 after final checks and both reviews; see existing feature summary current acceptance. Historical stage text below is retained.

## Owner-approved delivery workflow (2026-10-19)

This instruction supersedes earlier role, skill, client-configuration and plan wording
that requires tester handoffs or independent reviews before implementation.

Assign one implementer the entire approved implementation batch, including production
code, tests, fixtures, build configuration, associated guides and its task evidence.
The implementer may write and update tests against the approved requirements; it must
preserve valid assertions and never weaken checks to obtain a pass. Use test-first
evidence where practical and run relevant tests/styles during implementation.
Normal initial implementation includes bringing the approved behavior to green;
do not route every expected development failure through another agent.

Keep that implementer through the batch. No separate tester or intermediate review
handoffs by default. Prerequisites are working contracts and evidence, rather than
acceptance by an intermediate reviewer. Pause for unresolved requirements, scope
changes or a genuine blocker. Do not invent scope or relabel review corrections
as initial work.

After implementation, tests, documentation and required checks are complete, submit
one stable candidate for the final specification/coverage and security reviews.
Group findings from both into one report and, if needed, one explicitly approved
correction pass followed by focused re-review. There is one shared correction budget,
not one per reviewer. Stop and report unresolved findings after that pass.
The coordinator retains shared records, plan status and final acceptance ownership.

Existing completed entries, historical evidence and exhausted correction budgets
remain unchanged. This workflow change grants no extra PG-CONVERSION correction cycle.
The owner resumed PostgreSQL implementation on 2026-10-19; current execution status
is recorded below and in docs/InteractionRecord.md.

### Remaining PostgreSQL task routing

On owner resumption, the existing implementer carries PG-004 verification, PG-005
test work and PG-007 integration through one implementation batch. PG-005 test/fixture
ownership transfers to that implementer, including necessary runtime API scaffolding.
PG-007 additionally owns Database.java for removal of the legacy SQLite path.
PG-006's readiness assessment is deferred and combined with PG-008 at final review;
it is no longer a prerequisite for PG-007. PG-009 security review follows completion
of the same stable candidate and precedes any combined correction plan. PG-010
acceptance still requires both final reports and passing applicable checks.
No task is marked complete by this routing amendment. Existing approvals cover
the implementation scope; no deployment, hosted tests or Storage/JPro expansion.

## Historical evidence locations

Owner explicitly requested deletion of old per-task interaction logs on2026-10-20.
Filenames retained in immutable completed entries are historical references; final
approvals/checks are consolidated in logs/2026-10-19-feature-pg-conversion-sdd-summary.md.
Do not recreate removed logs. Current handoff: docs/InteractionRecord.md.

## Execution contract

Human scope/correction approver: repository owner (user in this chat). This is a
single database area; no separate frontend/backend ownership is invented.
Coordinator assigns the named roles after approval, respecting prerequisite evidence.
Each worker supplies a task log; reviewers return read-only findings. One writer per
shared file; build.gradle transfers serially from tester to implementer. Coordinator
alone updates plan statuses and shared records. Initial stages are Backlog.
Correction batch PG-CONVERSION: TLS cycle3/3 complete and independently verified under owner extension. Further unexpected corrections require explicit owner direction. Unexpected failed-loop edits
require concrete owner-approved correction plans; reviewer disagreements go to owner.
Completed entries are immutable; approved follow-up tasks preserve correction counts.

Tracking: local plan only; GitHub issue IDs pending. No remote publication requested.
Owner approved this explicitly local-only plan for execution; remote publication remains outside this authorisation.
planning does not claim remote issues have been created.

## External prerequisites and boundaries

- Docker engine is reachable; PG-001 fixture verification now passes. Docker remains required for downstream database tests.
- PostgreSQL 17 is the approved proposed local/test major; exact image pin is recorded
  during PG-001. Confirm Supabase major before hosted compatibility acceptance.
- Public Java declaration scaffolding may be necessary before PG-002 can run. If so,
  tester reports exact missing declarations; coordinator assigns a minimal, behavior-free
  scaffold handoff with reviewer visibility. Compiler failure is not behavioral red.
- JPro session/storage lifecycle integration is separately scoped: preserve current lock,
  do not claim hosted multi-user readiness, and do not delete lock protection in this plan.
- User owns approval; roles own assigned edits. Execution approval: explicit PG-P1 reply in this chat, 2026-10-19.

## Coverage

| Requirement | Implementation | Evidence/review |
| --- | --- | --- |
| PG-01 PostgreSQL everywhere | PG-004, PG-007 | PG-001–PG-003, PG-005–PG-006, PG-008 |
| PG-02 persistence semantics/atomicity | PG-004, PG-007 | PG-002–PG-003, PG-005–PG-006, PG-008 |
| PG-03 restart persistence/provisioning | PG-004, PG-007 | PG-002, PG-005, PG-008 |
| PG-04 credentials/test isolation | PG-001, PG-004, PG-007 | PG-002–PG-003, PG-008–PG-009 |
| Documentation and final acceptance | PG-007, PG-010 | PG-008–PG-009 |

Commands below are planned, not executed. Use Java 25 and Gradle 9.1.0 wrapper.
Targeted invocations use ./gradlew test --tests '<assigned class>' (gradlew.bat on Windows).
Playwright/browser verification belongs to separately approved JPro integration;
PostgreSQL integration tests establish database behavior, not browser/session correctness.

### PG-001 — Prepare isolated PostgreSQL test infrastructure

- **Task ID:** PG-001
- **Name:** Prepare isolated PostgreSQL test infrastructure
- **Task dependencies:** None
- **Specification references:** PG-T1 Tests and verification; PG-01, PG-04
- **Area / execution role / allowed files:** Testing / hotshop_testing; build.gradle (test dependencies only), src/test/java/hotshop/support/ (new), src/test/resources/, .github/workflows/gradle.yml
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. Docker readiness is required for container acceptance; PostgreSQL 17 patch/image digest must be recorded before running tests.

**Description**

Add Testcontainers 2.0.5 test modules and PostgreSQL JDBC test runtime; create fixture-owned local connections with fresh databases and teardown. Add CI Docker readiness. No production behavior. Infrastructure precedes behavior tests because container execution requires a working local harness.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Run a container-only smoke test; prove two fresh databases do not share records and external environment URLs are ignored.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- Fixture starts real PostgreSQL, isolates state, cleans up, and fails clearly without Docker; dependency resolution works with Java 25. No hosted target accepted.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-19. Tester reran unchanged smoke tests: 2 passed, zero failures/errors/skips; sentinel HOTSHOP_DB_* run passed and no fixture containers remained. Java25 compilation/dependency resolution and checkstyleTest passed. Image digest and actual commands: logs/2026-10-19-pg-001-test-infrastructure.md. Coordinator inspected test XML; correction count 0/2.

### PG-002 — Specify database configuration, schema and provisioning tests

- **Task ID:** PG-002
- **Name:** Specify database configuration, schema and provisioning tests
- **Task dependencies:** PG-001
- **Specification references:** PG-T1 Connections and configuration; Schema and provisioning; PG-01–PG-04
- **Area / execution role / allowed files:** Testing / hotshop_testing; src/test/java/hotshop/database/, src/test/resources/, tester-owned fixture files
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. Requires runnable container fixture; missing public contracts need a minimal declaration handoff, not compiler errors counted as red.

**Description**

Test configuration validation/redaction, transaction rollback, supported schema checks, BIGINT and BYTEA round trips, constraints, fresh/repeated/failed/concurrent migrations. Use behavior-driven tests against PostgreSQL. Prerequisites PG-001 must deliver their accepted evidence before dispatch.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Run focused database tests. Record per-criterion meaningful behavioral red; already-correct behavior needs passing evidence and implementation-path review. If declarations are missing, report scaffold needed before red.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- Tests cover invalid/missing configuration, integer boundaries, nulls, binary bytes, timestamps, migration rollback/version checks and locks. Setup errors remain unverified.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-19, test-authoring/baseline phase only. Configuration/schema/transaction tests and evidence mapping delivered; approved false-positive correction now produces expected red (42P01 expected versus08004 actual). Compilation and test style passed. Downstream schema/rollback assertions remain unverified and must be assessed by PG-003; no production readiness implied. Logs: logs/2026-10-19-pg-002-baseline-tests.md and logs/2026-10-19-pg-002-correction-1-verification.md. Cycle1/2 awaiting independent assessment.


## Approved staged correction2 follow-up tasks

Owner explicitly approved PG-011–PG-014 and correction2 in this chat,2026-10-19.
Shared batch PG-CONVERSION cycle2/2 in progress; no budget reset.
These tasks precede unresolved PG-003/PG-004; completed PG-001/PG-002 remain immutable.

### PG-011 — Add staged connection and missing coverage tests

- **Task ID:** PG-011
- **Name:** Add staged connection and missing coverage tests
- **Task dependencies:** PG-002
- **Specification references:** PG-T1, PG-01–PG-04, correction-plan-2, PG-003 findings R1–R3
- **Area / execution role / allowed files:** Testing / hotshop_testing; src/test/java/hotshop/database/, src/test/resources/, tester-owned fixtures
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local-only PG-P1 amendment; approved correction2
- **Readiness / authorisation:** Included in explicit staged-correction approval; dispatch only after dependency completion. Cycle2/2.

**Description**

Add a direct SELECT through Database independent of migrations; positive synthetic hosted verify-full parsing without network; direct redacted toString baseline independent of loader. Add valid/nonconflicting/conflicting cases for all missing uniqueness invariants R2. Preserve existing assertions. Establish fixed JDBC timeout policy10s connect/30s socket without new env API. Dependency PG-002 must supply accepted evidence before dispatch.

**Steps**

1. Read approved scope, evidence and correction budget; preserve others' edits.
2. Run focused config/direct connection tests and checkstyleTest. Record meaningful direct red, already-satisfied redaction passing evidence, and unreached schema cases separately.
3. Supply actual snapshot/check evidence and own task log; coordinator owns statuses.

**Acceptance criteria**

- Direct connection/configuration behavior has runnable independent evidence; R2 invariants mapped to tests; no hosted connection or claim of reached schema baseline.

**Completed:**

Yes — 2026-10-19. Tester delivered direct connection/config red and passing direct redaction evidence:19 cases18 failed1 passed, compile/checkstyleTest passed. Eight additional invariant cases compiled, schema execution unverified. Hashes/evidence in PG011 worker log; cycle2/2, readiness review separate.

### PG-012 — Review staged configuration and connection readiness

- **Task ID:** PG-012
- **Name:** Review staged configuration and connection readiness
- **Task dependencies:** PG-011
- **Specification references:** PG-T1, PG-01–PG-04, correction-plan-2, PG-003 findings R1–R3
- **Area / execution role / allowed files:** Review / hotshop_specification_reviewer; Read-only repository; return report to coordinator
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local-only PG-P1 amendment; approved correction2
- **Readiness / authorisation:** Included in explicit staged-correction approval; dispatch only after dependency completion. Cycle2/2.

**Description**

Review only configuration/connection readiness and deferred invariant coverage mapping. Independently confirm direct contract red and fixture readiness; do not approve schema, validator, migrations or transaction isolation here. Dependency PG-011 must supply accepted evidence before dispatch.

**Steps**

1. Read approved scope, evidence and correction budget; preserve others' edits.
2. Inspect stable snapshot and run focused permitted checks; verify correction2 expectations and return findings.
3. Supply actual snapshot/check evidence and own task log; coordinator owns statuses.

**Acceptance criteria**

- No unresolved readiness findings for PG-013 narrow slice; all broader limits explicit.

**Completed:**

Yes — 2026-10-19. Independent reviewer accepted narrow config/connection readiness:21 focused cases18 expected failures3 passes, no Docker failure, compile/style UP-TO-DATE. Direct redaction and two fixture cases pass; direct connection red independently reproduced. Broader schema remains blocked; cycle2/2.


## Owner-authorised TLS correction cycle3

Explicit extension approved2026-10-19 solely for PG015–PG017. Batch PG-CONVERSION
cycle3/3 in progress; further unexpected corrections require owner direction.

### PG-015 — Specify TLS query-key regressions

- **Task ID:** PG-015
- **Name:** Specify TLS query-key regressions
- **Task dependencies:** PG-012
- **Specification references:** PG-T1 PG-04, logs/2026-10-19-pg-013-tls-direction-plan.md
- **Area / execution role / allowed files:** Testing / hotshop_testing; src/test/java/hotshop/database/DatabaseConfigTest.java; own log
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local-only PG-P1 amendment, explicitly authorised TLS cycle3
- **Readiness / authorisation:** Approved in owner's cycle3 reply; dispatch after dependency acceptance. No extra scope/budget inferred.

**Description**

Add parser-only uppercase SSLMODE and encoded %73slmode regressions requiring rejection. Preserve canonical hosted verify-full positive and existing tests. Synthetic values only, no network. Prerequisite PG-012 must supply accepted evidence before dispatch.

**Steps**

1. Read scope, hashes and correction state; preserve others' edits.
2. Run focused configuration tests and checkstyleTest. Record red for both bypass forms and stable canonical positive.
3. Report exact check results and snapshot; no autonomous failed-loop fixes beyond approval.

**Acceptance criteria**

- Both bypasses have meaningful rejection-test failures before production fix; canonical positive preserved; no hosted connection.

**Completed:**

Yes — 2026-10-19. Parser-only regression run20 cases2 expected failures18 passes; both noncanonical keys fail rejection expectations, canonical hosted positive passes. Test compilation/checkstyleTest passed. Test SHAF9FAC5763520845E3C1F716B9F30061C025110A68A0763F57AD9FEB27A4825DE; config production unchanged. Cycle3/3.

### PG-016 — Review TLS regression readiness

- **Task ID:** PG-016
- **Name:** Review TLS regression readiness
- **Task dependencies:** PG-015
- **Specification references:** PG-T1 PG-04, logs/2026-10-19-pg-013-tls-direction-plan.md
- **Area / execution role / allowed files:** Review / hotshop_specification_reviewer; Read-only repository; returned report
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local-only PG-P1 amendment, explicitly authorised TLS cycle3
- **Readiness / authorisation:** Approved in owner's cycle3 reply; dispatch after dependency acceptance. No extra scope/budget inferred.

**Description**

Independently review raw/decoded-key semantics, rejection expectations and baseline before production correction. Prerequisite PG-015 must supply accepted evidence before dispatch.

**Steps**

1. Read scope, hashes and correction state; preserve others' edits.
2. Inspect hashes and run focused permitted checks against current candidate.
3. Report exact check results and snapshot; no autonomous failed-loop fixes beyond approval.

**Acceptance criteria**

- Narrow TLS-fix readiness independently accepted, with no broadened schema acceptance.

**Completed:**

Yes — 2026-10-19. Reviewer independently reproduced20 config tests2 expected failures18 passes; JDBC parseURL confirms canonical TLS accepted, uppercase/encoded ignored without network. Compilation/style UP-TO-DATE. Narrow raw-key fix readiness accepted; cycle3/3.

### PG-017 — Reject noncanonical TLS option names

- **Task ID:** PG-017
- **Name:** Reject noncanonical TLS option names
- **Task dependencies:** PG-016
- **Specification references:** PG-T1 PG-04, logs/2026-10-19-pg-013-tls-direction-plan.md
- **Area / execution role / allowed files:** Backend / hotshop_implementer; src/main/java/hotshop/database/DatabaseConfig.java; own log
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local-only PG-P1 amendment, explicitly authorised TLS cycle3
- **Readiness / authorisation:** Approved in owner's cycle3 reply; dispatch after dependency acceptance. No extra scope/budget inferred.

**Description**

Reject noncanonical raw sslmode keys before accepting hosted TLS. Preserve canonical verification, separate credentials, fixed timeouts, local fixtures and existing tests. No schema/runtime/other production changes. Prerequisite PG-016 must supply accepted evidence before dispatch.

**Steps**

1. Read scope, hashes and correction state; preserve others' edits.
2. Run focused configuration/connection tests and both style checks. Independent confirmation follows in PG014.
3. Report exact check results and snapshot; no autonomous failed-loop fixes beyond approval.

**Acceptance criteria**

- Uppercase and encoded sslmode regressions pass alongside canonical positive; no test edits; unrelated contracts unchanged.

**Completed:**

Yes — 2026-10-19. Implementer reports20 configuration plus1 connection tests all pass, both styles passed, tests/Database unchanged; one-line raw TLS-key guard. Config SHA53716BDA62875C862DDBD4487283E80529A3C947840524B3D6A7C99FCBAF81E6. Cycle3/3; PG014 independent confirmation follows.


### PG-013 — Implement configuration and PostgreSQL connection slice

- **Task ID:** PG-013
- **Name:** Implement configuration and PostgreSQL connection slice
- **Task dependencies:** PG-012, PG-017
- **Specification references:** PG-T1, PG-01–PG-04, correction-plan-2, PG-003 findings R1–R3
- **Area / execution role / allowed files:** Backend / hotshop_implementer; src/main/java/hotshop/database/DatabaseConfig.java and Database.java connection/config fields/path only
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local-only PG-P1 amendment; approved correction2
- **Readiness / authorisation:** Included in explicit staged-correction approval; dispatch only after dependency completion. Cycle2/2.

**Description**

Implement parsing/validation/redaction and credential-aware PostgreSQL connections. Remove PRAGMAs only on PostgreSQL path, keep legacy Path compatibility temporarily. Driver properties connectTimeout10/socketTimeout30. No schema, migrations, validator, transaction isolation, runtime or service changes. Dependency PG-012 must supply accepted evidence before dispatch.

**Steps**

1. Read approved scope, evidence and correction budget; preserve others' edits.
2. Run staged config/direct connection tests and checkstyleMain/checkstyleTest; preserve tester-owned tests. Unexpected failed-loop edits need owner direction under exhausted budget.
3. Supply actual snapshot/check evidence and own task log; coordinator owns statuses.

**Acceptance criteria**

- All narrow tests pass; credentials separate, hosted URL parsing valid under verify-full, bounded timeouts supplied; broader behavior unchanged.

**Completed:**

Yes — 2026-10-19. Narrow configuration/credential-aware connection implementation passes21 focused cases and both styles after approved PG017 fix. Separate credentials/timeout properties present; raw TLS guard resolves identified mismatch. No migration/schema/validator/isolation/runtime acceptance. Independent downstream assessment follows PG014; cycle3/3.

### PG-014 — Rebaseline downstream tests and reassess readiness

- **Task ID:** PG-014
- **Name:** Rebaseline downstream tests and reassess readiness
- **Task dependencies:** PG-013
- **Specification references:** PG-T1, PG-01–PG-04, correction-plan-2, PG-003 findings R1–R3
- **Area / execution role / allowed files:** Testing then review / hotshop_testing then hotshop_specification_reviewer; No code edits; own task logs and read-only review returned to coordinator
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local-only PG-P1 amendment; approved correction2
- **Readiness / authorisation:** Included in explicit staged-correction approval; dispatch only after dependency completion. Cycle2/2.

**Description**

Tester reruns reached config/connection and full downstream tests unchanged; reviewer then assesses actual newly reached SQL-stage failures. Do not infer schema readiness from prior authentication failures. Dependency PG-013 must supply accepted evidence before dispatch.

**Steps**

1. Read approved scope, evidence and correction budget; preserve others' edits.
2. Run focused/database tests and relevant style checks; record counts, SQL-stage evidence and hashes. Transfer Gradle ownership before independent reviewer checks.
3. Supply actual snapshot/check evidence and own task log; coordinator owns statuses.

**Acceptance criteria**

- Config/connection results and downstream baseline independently mapped; broader readiness explicitly accepted or blocked with exact reasons. No full acceptance from unexecuted assertions.

**Completed:**

Yes — 2026-10-19, baseline/review phase only. Tester49cases25pass24fail; independent reviewer27cases23pass4expectedfail. Narrow TLS/config/connection accepted; rollback/closure pass; reached isolation/atomicity/validator/schema failures mapped. Full PG004 remains not ready; bounded initial schema stage proposed in logs/2026-10-19-pg-next-schema-stage-plan.md. Cycle3/3 TLS verified, no extra correction authority.



## Owner-approved initial schema implementation stage

Owner approved PG018–PG020 on2026-10-19. This is initial green/baseline/review work,
not a further correction-budget extension. Existing unexpected-correction gates remain.

### PG-018 — Implement reached PostgreSQL storage-engine behavior

- **Task ID:** PG-018
- **Name:** Implement reached PostgreSQL storage-engine behavior
- **Task dependencies:** PG-014
- **Specification references:** PG-T1 PG-01–PG-04, logs/2026-10-19-pg-next-schema-stage-plan.md, PG014 reached review
- **Area / execution role / allowed files:** Backend / hotshop_implementer; Database.java; new src/main/resources/db/postgresql/001_initial.sql; build.gradle production JDBC dependency; own log
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local PG-P1 amendment; explicit schema-stage approval
- **Readiness / authorisation:** Approved; dispatch only after PG-014 accepted. Correction history3/3, no extra authority.

**Description**

Implement fresh final-state PostgreSQL schema preserving all six-script invariants, BYTEA and BIGINT, no historical backfill. Atomic pending batch under transaction advisory lock; migration control uses READ_COMMITTED after lock acquisition, service transactions SERIALIZABLE. Read-only version validation. Preserve verified rollback/closure and temporary legacy Path behavior; no command/runtime/services/storage. Dependency PG-014 must deliver accepted evidence first.

**Steps**

1. Re-read scope/evidence; preserve others' edits and tests ownership.
2. Run reached database cases and both styles, then all new PostgreSQL cases; report actual initial-green outcomes. No unexpected failed-loop corrections without owner direction.
3. Return checks/hashes/log and limitations; coordinator owns status.

**Acceptance criteria**

- Reached behaviors pass; remaining schema/width/binary/invariant assertions are executable and outcomes recorded, without claiming unperformed acceptance.

**Completed:**

Yes — 2026-10-19. Implementer recovered original Gradle sessions after usage interruption: focused8 reached tests plus both styles exit0, then all49 PostgreSQL cases exit0 BUILD SUCCESSFUL. Coordinator inspected five XML suites:49 tests0failures/errors/skips. Source/SQL persisted, no retry edits. Independent PG020 acceptance remains separate; correction history3/3 unchanged.

### PG-019 — Establish remaining schema and provisioning-command evidence

- **Task ID:** PG-019
- **Name:** Establish remaining schema and provisioning-command evidence
- **Task dependencies:** PG-018
- **Specification references:** PG-T1 PG-01–PG-04, logs/2026-10-19-pg-next-schema-stage-plan.md, PG014 reached review
- **Area / execution role / allowed files:** Testing / hotshop_testing; src/test/java/hotshop/database/; test resources/fixtures; own log
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local PG-P1 amendment; explicit schema-stage approval
- **Readiness / authorisation:** Approved; dispatch only after PG-018 accepted. Correction history3/3, no extra authority.

**Description**

Rerun unchanged PostgreSQL group, map reached values/invariants/newer version and passing implementation paths. Add executable migrateDatabase command acceptance against disposable local PostgreSQL: success, idempotence/data preservation, safe nonzero failure. Use copied actual build/production context in temp to avoid recursive project lock; do not fake task registration. No production edits. Dependency PG-018 must deliver accepted evidence first.

**Steps**

1. Re-read scope/evidence; preserve others' edits and tests ownership.
2. Run original group first, then command baseline and test style. Distinguish direct missing-command red from infrastructure failures; preserve credentials and no hosted targets.
3. Return checks/hashes/log and limitations; coordinator owns status.

**Acceptance criteria**

- All schema criteria mapped to actual results; meaningful command baseline produced; no manufactured failure for already-satisfied behavior.

**Completed:**

Yes — 2026-10-19. Tester forced fresh49 PostgreSQL cases allpass; two executable command tests show direct missing migrateDatabase task red, confirmed by actual copied-build probe. Compilation/checkstyleTest passed, no fixtures remain. Negative command diagnostics/redaction unreached until command implementation, explicitly not accepted as passing. CLI test SHA9A7826E1F2C7798A0A4C15FC1119633B70A20A117D28C4FCF2BD0446C079B7D8. Independent PG020 review pending; correction history3/3 unchanged.

### PG-020 — Review remaining schema and command readiness

- **Task ID:** PG-020
- **Name:** Review remaining schema and command readiness
- **Task dependencies:** PG-019
- **Specification references:** PG-T1 PG-01–PG-04, logs/2026-10-19-pg-next-schema-stage-plan.md, PG014 reached review
- **Area / execution role / allowed files:** Review / hotshop_specification_reviewer; Read-only repository; report returned to coordinator
- **Human approver:** Repository owner in this chat
- **Tracking / contract:** Local PG-P1 amendment; explicit schema-stage approval
- **Readiness / authorisation:** Approved; dispatch only after PG-019 accepted. Correction history3/3, no extra authority.

**Description**

Independently assess initial schema implementation/passing paths, newly reached evidence, newer versions, all invariants and executable command coverage/red. Decide readiness for original PG003/PG004 remaining work with exact limits. Dependency PG-019 must deliver accepted evidence first.

**Steps**

1. Re-read scope/evidence; preserve others' edits and tests ownership.
2. Run permitted focused checks and inspect stable test/production snapshot. No file edits or acceptance from unreached assertions.
3. Return checks/hashes/log and limitations; coordinator owns status.

**Acceptance criteria**

- No unresolved applicable readiness findings before broad task release; record failed/unverified criteria if any. Existing original tasks remain gated.

**Completed:**

Yes — 2026-10-19. Independent reviewer51 cases49pass2 expected CLI-red, no errors/skips; all schema/value/version/invariant/rollback/isolation/concurrency paths green and source-reviewed. Actual task-help confirms missing command. Narrow remaining CLI initial-green readiness accepted; compile/styles UP-TO-DATE, no fixtures remain. No runtime/full-feature acceptance; correction history3/3.


### PG-003 — Review database test readiness

- **Task ID:** PG-003
- **Name:** Review database test readiness
- **Task dependencies:** PG-002, PG-014, PG-020
- **Specification references:** PG-T1 All database contracts; PG-01–PG-04
- **Area / execution role / allowed files:** Review / hotshop_specification_reviewer; Read-only repository; report returned to coordinator
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. Requires PG-002 evidence and executable test snapshot.

**Description**

Independently review coverage, baseline evidence, fixture safety and intended public contracts. Resolve any scaffold gap through explicit assignment before production green. Prerequisites PG-002 must deliver their accepted evidence before dispatch.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Run relevant tests where infrastructure permits; return criterion-level readiness findings.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- All database behavior has meaningful red or independently confirmed already-satisfied evidence; no unresolved readiness findings.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-19. PG020 independent review confirms49 PostgreSQL criteria cases pass with actual implementation-path evidence, plus2 meaningful missing-command red cases. Prior R1–R3 readiness blockers resolved through authorised staged tasks; release PG004 remaining CLI green only. Command success/redaction acceptance remains pending implementation; runtime/feature acceptance not claimed. Correction history3/3.

### PG-004 — Implement PostgreSQL database and provisioning

- **Task ID:** PG-004
- **Name:** Implement PostgreSQL database and provisioning
- **Task dependencies:** PG-003
- **Specification references:** PG-T1 Connections and configuration; Schema and provisioning; Transaction semantics; PG-01–PG-04
- **Area / execution role / allowed files:** Backend / hotshop_implementer; src/main/java/hotshop/database/, src/main/resources/db/postgresql/ (new), build.gradle (production dependency and migrateDatabase task only)
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. Production begins only after approved test readiness; exclusive build.gradle writer transfers from tester to implementer.

**Description**

Implement configuration, PostgreSQL connections, serializable transactions, fresh schema and locked atomic provisioning. Preserve historical SQLite resources. Implement read-only schema validation and sanitised errors. Prerequisites PG-003 must deliver their accepted evidence before dispatch.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Run focused database tests plus checkstyleMain; inspect migration SQL for full six-script final-state parity.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- Database tests pass; no SQLite runtime fallback; BIGINT/BYTEA mappings and invariants preserved; repeated setup preserves records; startup validation performs no DDL.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-20. PostgreSQL-only engine/config/schema/provisioning accepted; final746-case suite and required checks pass, final reviews clear. Evidence: existing feature-pg-conversion-sdd-summary current acceptance.

### PG-005 — Specify runtime and marketplace regression tests

- **Task ID:** PG-005
- **Name:** Specify runtime and marketplace regression tests
- **Task dependencies:** PG-004
- **Specification references:** PG-T1 Runtime interface; Transaction semantics; Tests and verification; PG-01–PG-04
- **Area / execution role / allowed files:** Testing / hotshop_testing; src/test/java/, src/test/resources/, tester-owned fixture files
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. Requires functioning PostgreSQL database contract from PG-004.

**Description**

Convert database-backed service/runtime fixtures and direct SQLite queries; preserve valid assertions. Add restart, startup failure/redaction, duplicate username and two-connection acceptance/booking/chat race tests. Prerequisites PG-004 must deliver their accepted evidence before dispatch.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Run focused runtime/service tests; document per-criterion red or independently reviewable baseline evidence, including fixture independence.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- Tests exercise actual PostgreSQL, deterministic clocks, persisted restart state, supported schema checks, constraint-specific username errors and atomic competing operations.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-20. Runtime/service/UI fixtures converted to disposable PostgreSQL; valid assertions retained, concurrency/history/error/restart coverage accepted in final review. Evidence: existing feature-pg-conversion-sdd-summary current acceptance.

### PG-006 — Review runtime and regression test readiness

- **Task ID:** PG-006
- **Name:** Review runtime and regression test readiness
- **Task dependencies:** PG-005
- **Specification references:** PG-T1 Runtime interface; Transaction semantics; PG-01–PG-04
- **Area / execution role / allowed files:** Review / hotshop_specification_reviewer; Read-only repository; report returned to coordinator
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. Requires PG-005 snapshot and baseline report.

**Description**

Review service parity, concurrency assertions, failure partitions, time boundaries and retained directory-lock safety. Prerequisites PG-005 must deliver their accepted evidence before dispatch.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Run focused checks and compare tests with source behaviors and approved design.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- No unresolved readiness findings; observed red is behavioral, not an incomplete harness. Hosted browser coverage is explicitly excluded.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-20. Completed jointly with PG008 under owner amendment; Luna final readiness/coverage review has no unresolved findings. Evidence: existing feature-pg-conversion-sdd-summary current acceptance.

### PG-007 — Integrate PostgreSQL with runtime and services

- **Task ID:** PG-007
- **Name:** Integrate PostgreSQL with runtime and services
- **Task dependencies:** PG-006
- **Specification references:** PG-T1 Runtime interface; Transaction semantics; PG-01–PG-04
- **Area / execution role / allowed files:** Backend / hotshop_implementer; src/main/java/hotshop/ApplicationRuntime.java, Main.java, repository/, service/ (affected persistence/error paths only), build.gradle (remove SQLite dependency), docs/UserGuide.md, docs/DeveloperGuide.md (setup/design portions)
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. Requires PG-006 readiness; do not remove directory lock or change storage/session architecture.

**Description**

Inject database configuration, replace startup migration with validation, audit repository SQL and map specific duplicate-name SQLSTATE. Provide retry guidance for serialization/deadlock failures without callback replay. Update startup feedback and PostgreSQL setup documentation. Prerequisites PG-006 must deliver their accepted evidence before dispatch.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Run focused regressions, then ./gradlew test checkstyleMain checkstyleTest check build shadowJar. Never change tester-owned tests.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- Required checks pass; application/tests use PostgreSQL exclusively; records survive restart; errors redact secrets; current image/lock safety preserved. Documentation states hosted integration limits.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-20. Runtime/services use PostgreSQL without SQLite fallback; guides updated, full746 tests/checks/build/package accepted, no SQLite classes in final JAR. Evidence: existing feature-pg-conversion-sdd-summary current acceptance.

### PG-008 — Review stable implementation and completion documentation

- **Task ID:** PG-008
- **Name:** Review stable implementation and completion documentation
- **Task dependencies:** PG-007
- **Specification references:** PG-T1 All PG-T1 sections; PG-01–PG-04; repository documentation gates
- **Area / execution role / allowed files:** Review / hotshop_specification_reviewer; Read-only repository; report returned to coordinator
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. Coordinator must prepare current AGENTS.md, feature SDD summary and decision handoff from worker evidence before review.

**Description**

Review final implementation, all criteria, UserGuide, DeveloperGuide, AGENTS and SDD summary. Verify logs and final diff exclude unrelated edits. Prerequisites PG-007 must deliver their accepted evidence before dispatch.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Run necessary independent checks against stable snapshot; report coverage gaps and hosted limitations.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- All in-scope criteria and final documentation independently accepted; no unresolved reviewer disagreements; exact verification provenance recorded.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-20. Luna independent source/test/documentation review clear; coordinator consolidates final records after review per owner reporting rule. Evidence: existing feature-pg-conversion-sdd-summary current acceptance.

### PG-009 — Review security of the stable PostgreSQL candidate

- **Task ID:** PG-009
- **Name:** Review security of the stable PostgreSQL candidate
- **Task dependencies:** PG-008
- **Specification references:** PG-T1 Configuration, provisioning, transaction and fixture security; PG-04
- **Area / execution role / allowed files:** Review / hotshop_security_reviewer; Read-only repository; report returned to coordinator
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. Stable final snapshot required; later edits invalidate affected review.

**Description**

Check secrets/redaction, SQL binding, connection TLS, fixture destinations, startup/provisioning authority and preserved authorization. Do not read credentials into logs. Prerequisites PG-008 must deliver their accepted evidence before dispatch.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Review code/config and run targeted permitted negative checks; return actionable findings.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- No unresolved feature-relevant security findings; no hosted test writes or secrets exposed; reviewed snapshot identified.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-20. Luna final security inspection clear; no confirmed in-scope findings; hosted/provider/JPro limits explicitly retained. Evidence: existing feature-pg-conversion-sdd-summary current acceptance.

### PG-010 — Record bounded database acceptance and handoff

- **Task ID:** PG-010
- **Name:** Record bounded database acceptance and handoff
- **Task dependencies:** PG-009
- **Specification references:** PG-T1 All PG-T1 sections; PG-01–PG-04; documentation/logging obligations
- **Area / execution role / allowed files:** Coordinator; AGENTS.md, docs/DeveloperGuide.md (workflow), docs/InteractionRecord.md, workflow/PLAN.md, logs/, logs/2026-10-19-feature-pg-conversion-sdd-summary.md
- **Human approver:** Repository owner, user in this chat
- **Tracking / contract:** Local only, remote linkage pending; PG-T1 / PG-P1
- **Readiness / authorisation:** Backlog; included in owner-approved PG-P1 execution scope. All worker evidence and reviews required; hosted parity cannot be asserted without confirmed target major.

**Description**

Consolidate evidence, verify requirements/reviews/logs, update completion only for satisfied tasks, preserve completed entries. Record Docker, target-major and JPro/Storage acceptance limitations. Prerequisites PG-009 must deliver their accepted evidence before dispatch.

**Steps**

1. Re-read assigned scope and evidence; preserve other agents' edits.
2. Inspect final diff and documentation links; report actual commands/results, correction count and remaining integration work.
3. Record actual outcomes and task log; unexpected failures requiring edits follow the correction gate.

**Acceptance criteria**

- Database conversion accepted only with required passing checks/reviews. Hosted deployment/multi-user readiness explicitly remains separate; no commit/push/deployment implied.
- Report exact snapshot, checks and limitations; do not count attempted or unverified work as complete.

**Completed:**

Yes — 2026-10-20. Bounded local acceptance, consolidated approvals/evidence and current handoff recorded; no deployment/hosted acceptance or publication claimed. Evidence: existing feature-pg-conversion-sdd-summary current acceptance.



## Owner-approved correction limit (2026-10-19)

Use one initial implementation pass and required initial reviews, then at most one
shared correction pass and re-review per task/batch. Group findings into that pass.
Corrections still require an explicitly approved plan. If findings remain, stop
and report them; no further loop without explicit owner direction. Required initial
readiness, coverage and security checkpoints remain. Avoid repeating unchanged checks.
This owner instruction supersedes older two-cycle wording in saved skills and client
configurations. Preserve historical counts: PG-CONVERSION remains exhausted at 3/3.
Execution was resumed by the owner; no new correction budget is granted.

## Current execution status — owner resumed 2026-10-19

The owner lifted the pause. Existing implementer is executing PG004/PG005/PG007 as one batch under the delivery override above. No tasks newly marked complete; final PG008/PG009 reviews and PG010 acceptance remain pending. Historical correction exhaustion unchanged.


Associated PG007 documentation ownership includes linked docs/userGuide/GettingStarted.md, docs/developerGuide/DevelopmentWorkflow.md and docs/developerGuide/ArchitectureOverview.md for precise PostgreSQL setup/reset/architecture corrections. Historical diagrams need not be regenerated; scope remains the database conversion.

## AI search benchmark — BENCH-P1, 2026-10-20

Owner requested immediate sequential two-branch execution, multiagent-search first,
singleagent-search second. Source:AI-P1/AI-T1 appended to product/technical specs.
Human approver:repository owner. New batch AI-SEARCH-BENCH (not PG-CONVERSION).
No deployment/push requested. Deadline:21October2026,14:00Asia/Singapore.

| Task | Dependency | Ownership and deliverable | Acceptance | Completed |
| --- | --- | --- | --- | --- |
| BENCH-SETUP | None | Independent Luna common-setup author owns validator declaration stub and fixed AiSearchProposalValidatorTest; root owns specs/Git/checkpoint. | Runnable79-case behavioral red at stub; both styles passed;45 security-tagged cases; common checkpoint being frozen. | Yes2026-10-20 |
| BENCH-MULTI | BENCH-SETUP-FIX | Luna implementer owns validator only; coordinator plus final Luna specification/security reviewers. | Candidate f2d07fd;76/76 plus42/42 security, stylespass; finalreviews clear of implementation defects,1 sharedtest coverage note;320s observed. | Yes2026-10-20 |
| BENCH-SINGLE | BENCH-SETUP-FIX; after BENCH-MULTI for scheduling only | Fresh Luna implementer owns validator only in isolated singleagent-search worktree. No multi-arm findings supplied. | Candidate0c626fa;76/76 plus42/42 security, stylespass; fixedtests unchanged;329s observed. | Yes2026-10-20 |
| BENCH-RESULTS | BENCH-MULTI,BENCH-SINGLE | Coordinator evaluates fixed reports/hashes, consolidates one result table here. | Fixedtest blob matches botharms; XML/style/security-tag counts independently inspected; results/limits consolidated below and benchmark-results.csv. | Yes2026-10-20 |

Mandatory protocol and exact acceptance partitions are in AI-P1/AI-T1. Fixed tests
are common evaluation setup, not a benefit credited to either workflow. Record
preparation separately, same implementation model, sequential order/cache effects.
Worker reports are concise messages; no interaction/task Markdown files. Broader AI
integration remains pending and is not a prerequisite for this early benchmark.

### BENCH-SETUP-FIX — Approved common-oracle correction

Dependencies:BENCH-SETUP. Status:owner-approved correction1/1 verified; Completed:Yes2026-10-20. All76 cases reach stub red,42 security-tagged; both styles pass.
Owner:existing common setup author, test file only. Root owns Git/spec/status.
Failure:e79 pilot76pass3fail because invalid title/category/sort fixtures include
null, contradicting AI-P1 optional-null defaults and the positive null-default test.
Proposed edit:remove exactly those3 null fixture rows; keep all remaining assertions.
Freeze76 common cases (security subset expected42), reset both arms to corrected
common stub checkpoint, use fresh no-history workers; retain/exclude pilot evidence
from paired timing. This is shared setup correction1/1, not a production defect.
Verification:76 behavioral-red cases at stub, compile/styles successful, fixed test
blob identical on both branches. No SoC/DB/network or new reporting Markdown files.

## AI search benchmark results — completed 2026-10-20

Local candidates only; no push/deployment. Common corrected checkpoint:
b87d2151e8af78f020a4ffe9572e59f897c0ba38, based on application aac5548.
Frozen evaluation test Gitblob:18fdef917ae5bd685526365ec27fd86e94ee3b54 on both arms.
Implementation model:both gpt-6-luna, fresh workers, no chat inheritance or arm-result
sharing. Multi adds this coordinator plus two independent Luna final reviewers.
Coordinator Sol preference is configured; actual running model identity and usage
were not exposed, so model identity/credits are not inferred from that setting.

| Metric | Multi-agent | Single-agent |
| --- | --- | --- |
| Branch | multiagent-search | singleagent-search |
| Candidate commit | f2d07fd20fe157dd016c14f1a3b4bcac00fbdddb | 0c626fac397a8b82531a0933c0ed3889f6a55774 |
| Full fixed acceptance suite, including security |76/76,0 errors/skips |76/76,0 errors/skips |
| Independently executed security-tagged subset |42/42,0 errors/skips |42/42,0 errors/skips |
| Main/test Checkstyle |Pass,0 report errors |Pass,0 report errors |
| Worker implementation/self-check time |186s (3m06s) |270s (4m30s) |
| Observed dispatch-to-completion workflow time |320s (5m20s) |329s (5m29s) |
| Observed dispatch-to-first-candidate report |223s |329s |
| Additional observed review/orchestration interval |97s |Not part of single workflow |
| Reviewer-reported source review time |17s specification +10s security |None |
| Development specialists |3 plus coordinator |1 |
| Initial production-test failures before self-fix |0 |2 cases, null condition elements |
| Initial style failures before self-fix |0 |1 overlong line |
| Post-submission correction passes |0 |0 |
| Human development interventions during measured arm |0 |0 |
| Final production-file lines (descriptive, not quality score) |125 |119 |
| Tokens / credits |N/A:not exposed |N/A:not exposed |

Timing uses clock-tool UTC consistently. Multi dispatch18:08:37, first recorded
candidate18:12:20, final review completion recorded18:13:57 UTC on2026-10-19.
Worker reported18:08:55–18:12:01 UTC. Specification review18:12:56–18:13:13;
security review18:13:19–18:13:29. Single dispatch18:14:22, final report recorded
18:19:51 UTC; worker reported18:14:46–18:19:16. These are20October02:xx in
Singapore, not a disagreement with the local date. Observed workflow time includes
dispatch/report transport, orchestration and environment/tool approval delays;
worker time is separately self-reported. Final Git commits and common evaluator
security-tag runs occurred afterward and are excluded equally from measured arms.

Common evaluator actually inspected full76-case XML and both Checkstyle XML reports
for each worktree, confirmed unchanged frozen-test Gitblob, and ran the same frozen
security-tag subset independently:multi BUILD SUCCESSFUL3s, single2s,42 cases each.
An ignored tools/benchmark/security-only.init.gradle selected existing @Tag("security")
cases and wrote separate test-results/benchmark-security output; no assertions/source
were changed. Primary reports remain in build/test-results/test. Results CSV is
workflow/benchmark-results.csv. No full unrelated PostgreSQL suite or live SoC call
was run for this isolated pure-Java slice.

### Findings and limitations

- Final correctness/security scores tied. The9s observed workflow difference is
  not evidence of a general speed advantage; one task, one pair, fixed run order
  and unequal per-project warm cache state limit timing inference. Both ran clean,
  sharing the warm dependency cache; project-directory caches were not matched.
- Reviews found no production defect/security issue. Specification reviewer found
  one minor shared-test limitation:input-list nonmutation comparison uses the same
  list reference, so that assertion is tautological. Source inspection showed no
  mutation; output isolation/immutability assertions are meaningful. Fixed tests
  were not changed mid-comparison. Do not claim complete mutation/security coverage.
- Initial single-agent tests caught/fixed two null-condition cases and one style
  issue before submission. These are self-check iterations, not reviewer fixes.
  Multi's faster implementation happened before reviews, so it cannot be credited
  to those reviews. The review benefit observed was test-quality feedback only.
- Shared setup used one independent setup worker. An excluded pilot exposed three
  contradictory null fixtures. Owner approved one common setup correction (1/1);
  fixture rows were removed while valid null-default assertions stayed. Pilot
  worker186s and preparation/approval waits are excluded, preserved transparently
  here; total preparation time was not instrumented. Pilot code is in a local stash.
- No implementation hints, code or arm-specific review findings were sent to the
  single worker. Worktrees are separate snapshots on a shared filesystem; access
  separation relied on assignment instructions, not an enforced security sandbox.
- No exact cost comparison:agent counts are a resource proxy, not token/credit totals.
  One coordinator status query to single worker supplied no implementation hint;
  environment sandbox Java-access retries occurred in both runs. Human development
  interventions count requirement/fix decisions during measured arms, not automatic
  operational approvals; shared setup had one explicit human correction approval.
- This is a validator benchmark within a proposed buyer AI-search feature, not a
  completed runtime AI feature or LLM jailbreak study. Raw JSON/provider/SoC quotas,
  UI preview/apply and downstream authorization integration remain deferred.

### Reproduce locally

Multi root:E:/CS3227-2610-MP3 (branch multiagent-search).
Single root:E:/CS3227-2610-MP3/tools/benchmark/singleagent-search (branch singleagent-search).
In either assigned root with Java25, run wrapper test filtered to
hotshop.service.AiSearchProposalValidatorTest, then checkstyleMain and checkstyleTest.
For tagged scoring, use the common init script with --init-script and the same test
filter; verify42 tests in build/test-results/benchmark-security. For both primary
runs, the full76-case suite was fixed before implementation and visible/read-only
to implementation agents. It was not a hidden held-out suite, limiting generalisation.

Exact primary verification commands in each worktree:

```powershell
.\gradlew.bat test --tests hotshop.service.AiSearchProposalValidatorTest --console=plain
.\gradlew.bat checkstyleMain checkstyleTest --console=plain
```

The ignored security scoring script can be recreated with the following content.
Save it outside application source and pass its absolute path using `--init-script`
to the same filtered test command. This selects existing tests without changing them.

```groovy
gradle.projectsEvaluated {
    gradle.rootProject.allprojects { project ->
        project.tasks.withType(org.gradle.api.tasks.testing.Test).configureEach { task ->
            task.useJUnitPlatform { options -> options.includeTags('security') }
            task.reports.junitXml.outputLocation.set(
                project.layout.buildDirectory.dir('test-results/benchmark-security'))
            task.reports.html.required.set(false)
        }
    }
}
```

## SoC connection benchmark — BENCH-P2 draft, 2026-10-20

Source: product second-benchmark scope and AI-T2. Owner requested the larger task
and manually monitored credit windows. Preparation is authorised; measured work
awaits the provider contract and a user-reported starting credit reading.
Task 1 entries and results remain historical and unchanged.

Proposed sequence, not completed tasks:

1. Resolve provider/shared-client inputs; finalise product/design acceptance and
   create explicit execution tasks with one shared starting checkpoint and oracle.
2. Prepare new branches from the same selected Task 1 validator implementation;
   freeze common tests and configuration scaffolding before credit measurement.
3. User records starting credits and says ready; run only the first workflow.
   Pause after its implementation and applicable final reviews, before aggregation.
4. User records ending credits, then a new baseline and ready signal for workflow
   two. Do not start workflow two automatically. Use fresh Luna specialists, no
   history or access to the other run's findings, same model and requirements.
5. After the second ending reading, evaluate common checks and consolidate results
   once. Record manual credit deltas and their dashboard precision/lag; separate
   preparation, common evaluation and live smoke checks from both measured windows.

Metrics: acceptance/security tests, elapsed time, implementation self-corrections,
review findings, human interventions, specialist count and manually reported credit
delta. Avoid unrelated Codex activity during each credit window; report background
usage or delayed dashboard updates as confounders. One pair remains a case study.
No measured agent dispatched, new benchmark branch created or live SoC call made.

Preparation update: fetched origin/llm-feature at 7d028e4. Existing configuration,
request encoding and seller quota code can inform the common starting point;
transport/parser remain seller TASK-007/008, creating an ownership overlap. Owner
has been asked whether to coordinate shared transport ownership or wait for that
work and benchmark only its buyer adapter. Endpoint/model supplied; provider-wide
quota remains unknown. Preserve PostgreSQL and completed benchmark branches.

Owner decision, 2026-10-20: keep seller transport/parser work separate; benchmark
the buyer adapter only after it is ready. No transport implementation is assigned
here. Next prerequisite is the seller's ready commit/interface, followed by review
of selected reusable components and one common pinned baseline. Do not start a
measured run or ask for starting credits while this dependency remains unresolved.
Retain manual credit checkpoints, fresh agents and identical transport in both arms.

Superseding owner clarification, 2026-10-20: proceed with independent transport,
parser and buyer adapter implementation for the benchmark; overlap can be reconciled
later. Remove the wait-for-seller prerequisite from the proposed sequence above.
Next authorised preparation: finalise the bounded connection/adapter contract and
fixed offline tests, then create two new branches from one common checkpoint.
Neither measured arm starts before its manual credit baseline/ready signal.
Seller reconciliation, live smoke checks and common evaluation stay outside runs.

Credit checkpoint: owner reported 46% before common setup was frozen. Preserve as
a preliminary raw dashboard reading; request a fresh reading at actual dispatch.
Run order remains multi-agent first, single-agent second. Do not interpret percent
as consumed versus remaining or calculate a credit delta without dashboard meaning.

### SOC-SETUP — Freeze common declarations and offline evaluation

- Task ID: SOC-SETUP. Dependencies: None. Specification: AI-T2-C1.
- Owner: one Luna testing specialist; approver: repository owner through current
  request to proceed. Coordinator owns plan/specs/Git; worker owns new SocSearchClient
  declaration stub, focused tests/resources and Gson-only build dependency addition.
- Scope: common setup only, no transport/parser/adapter production behaviour.
  Preserve PostgreSQL, Task 1 validator/tests, uncommitted documentation and seller
  branch. No logs, live provider calls, credentials or hosted database access.
- Steps: prepare runnable stub/API and isolated local HTTPS fixture; write fixed
  acceptance/security tests; run focused test and styles; distinguish behavioural
  red from setup/compiler failures. Report test counts and fixture limitations.
- Acceptance: compile/style checks pass and specified missing behaviour reaches
  meaningful runtime assertions; no implementation behavior supplied by stub.
- Completed: Yes — 2026-10-20. Author ran 73 cases: 60 UnsupportedOperationException
  and 11 assertion failures for missing stub behaviour; 2 independent HTTPS fixture
  self-checks pass. Both styles pass, coordinator inspected XML with zero style
  errors. No production connection behaviour supplied. Outside measured windows.

### SOC-READY — Review the common test snapshot

- Task ID: SOC-READY. Dependency: SOC-SETUP. Spec: AI-T2-C1.
- Owner: independent Luna specification reviewer, read-only new contract/tests and
  fixture evidence; human approver: repository owner under current benchmark request.
- Steps: check fixture assertions, positive/negative partitions and actual red causes;
  report blockers separately from coverage limitations. No production implementation.
- Acceptance: no contradictory required assertions or invalid fixture paths; common
  baseline and source hashes recorded by coordinator. No extra logs.
- Completed: Yes — 2026-10-20. Luna read-only review found no invalid fixture or
  contradictory assertion and explicitly cleared representative oracle readiness.
  Reviewer initially misclassified missing exact request-byte edge as a blocker,
  then corrected that classification against the actual acceptance criterion.
  Exact 8192-byte request edge and network-disconnect mapping remain coverage gaps;
  no tests changed after submission/review. Outside both measured windows.

### SOC-MULTI — Implement the connection slice with final independent reviews

- Task ID: SOC-MULTI. Dependencies: SOC-SETUP, SOC-READY and first user credit/ready
  checkpoint. Spec: AI-T2-C1; branch multiagent-soc-search. Human approver: owner.
- Owner: fresh Luna implementer owns new client implementation/helper files only;
  fixed tests/dependencies and existing validator are read-only. Coordinator owns
  Git/metrics; final Luna specification and security reviewers inspect stable code.
- Steps: implement all specified transport/parser/adapter behaviour, run common
  focused tests/styles and required checks, submit candidate; independent reviewers
  inspect the final candidate once. No interleaved reviews or test weakening.
- Acceptance: frozen common tests and styles pass; final findings recorded. One
  explicitly approved post-review correction cycle maximum; preserve any failed
  initial candidate/metrics rather than silently rerunning or resetting the clock.
- Completed: No. Dispatched 2026-10-20, clock start 2026-10-19 18:56:00 UTC;
  owner starting raw readings session 36%, weekly 43%. Meaning used/remaining not
  explicitly stated. Common source checkpoint f5ec63b, fresh Luna implementer.
  Focused client+validator tests/styles in measured window; broader database/build
  evaluator checks outside both arms. Initial measured review end 19:02:38 UTC,
  398 seconds from dispatch. First candidate observed 19:01:22 (322 seconds).
  Final 149 focused tests and both styles pass, fixed oracle unchanged. Initial
  self-check: 1 close-order failure and 79 style violations, fixed before submission.
  Both final reviewers confirm body read is not canceled/closed after headers on
  timeout/close, and body I/O disconnect maps INVALID_RESPONSE instead of NETWORK.
  Acceptance failed despite tests passing. No correction authorised or performed;
  cycle 0/1. Stop for ending readings before single-arm dispatch or any fixes.

### SOC-SINGLE — Implement the same slice with one agent

- Task ID: SOC-SINGLE. Dependencies: SOC-SETUP, SOC-READY; scheduling after SOC-MULTI,
  its ending reading and separate second user credit/ready checkpoint. Spec AI-T2-C1.
- Owner: fresh Luna implementer, same code ownership/commands as SOC-MULTI; branch
  singleagent-soc-search from the identical common checkpoint. Human approver: owner.
- Steps: independently implement and self-verify the same fixed contract without
  access to multi findings/code. No independent development reviewers in this arm.
- Acceptance: common tests/styles pass and iterations recorded; same correction
  policy and model, usage recorded only from actual user readings/runtime evidence.
- Completed: No. Owner supplied updated starting readings session 28%, weekly42%;
  dispatched fresh no-history Luna single agent at 2026-10-19 19:05:24 UTC from
  unchanged f5ec63b in tools/benchmark/singleagent-soc-search. No multi findings/code
  supplied. Root orchestration only; no independent development review for this arm.
  Initial stable submission observed 19:09:55 UTC:271seconds (4m31s), 149 focused
  tests and both styles pass, XML independently inspected; fixed testblob unchanged.
  Worker-reported interval19:05:58–19:09:37UTC:219seconds. Initial self-check2compile
  diagnostics,1promptliteral testfailure,9styleviolations; fixed before submission.
  Final productionblob02d08dceeddd7113796e7da6e54e07df07c82965. No review fixes.
  Await ending readings before common evaluation or correction; single self-test
  success is not independent contract/security acceptance.

### SOC-RESULTS — Score and consolidate once

- Task ID: SOC-RESULTS. Dependencies: SOC-MULTI, SOC-SINGLE and both ending readings.
- Owner: coordinator; human approver: owner. Allowed files: existing guides,
  instructions, workflow specs/plan/results CSV and current handoff.
- Steps: inspect fixed-suite hashes/XML/style reports; run identical common scoring
  outside measured windows; record timing phases, raw credit readings, manual deltas
  only when dashboard semantics are known, interventions/findings and limitations.
- Acceptance: reproducible commits/checks, transparent costs and test limitations;
  do not claim provider quota compliance, live quality or general workflow advantage.
- Completed: Yes — 2026-10-20. Both initial candidates, fixed hashes, focused/style
  reports,100 common security cases, source findings and manual credit observations
  consolidated below and in benchmark-results.csv. Shared encoding oracle conflict
  is unscorable; attributable counts4/1. Broad regressions were interrupted after
  approximately14minutes, not passed; standalone packaging passes on both. Neither
  implementation accepted and no correction performed. No push/deployment/merge.

Common frozen source hashes (Git blobs): SocSearchClientTest
`b67b9e8f24eef1067b8caa22bf039c2ff6f7b830`; ControlledHttpsServer
`f17ee62da31262a0217af84c7faa43aea4817916`. Both arms will inherit these unchanged.
Shared setup is complete; multi measured implementation awaits fresh credit/ready
signal. Original 46% is preliminary, not the measured baseline. No live SoC call.

### SOC-MULTI initial result and manual usage readings

Candidate saved as a47df937694c854b25641b8df383c918a907c4a7. Measured workflow
398 seconds; first candidate observed at 322 seconds, remaining review/orchestration
76 seconds. Final 149 tests and both styles pass, but both independent reviewers
confirm two contract defects: body work survives timeout/close after headers, and
body network errors map to INVALID_RESPONSE. Initial review acceptance therefore
fails. No post-review edits; correction count 0/1. These results remain part of the
case study even if a later correction is approved.

Owner manual readings: 5-hour session 36% to 30%; weekly 43% to 42%. Observed
decreases are 6 and 1 percentage points respectively. Display direction (remaining
or used) was not explicitly confirmed; no absolute credit/token conversion or
fine-grained cost claim. Dashboard rounding, lag and coordinator wrap-up can affect
the deltas. Common setup (earlier preliminary 46%) is excluded. Single has not run.
Recommend initial single run before correcting either candidate; await separate
user ready signal, using 30%/42% only if still current at that checkpoint.

Single starting checkpoint supersedes 30%/42%: owner reported 28%/42% and the run
started. Between-window dashboard change is session30% to28%, weekly42% unchanged;
record separately rather than attributing it to either measured arm. Dashboard lag
and coordinator wrap-up can contribute; no causal allocation to specific actions.

## SoC connection paired benchmark — initial results, 2026-10-20

The owner explicitly revised multi's ending reading from30%/42% to28%/42% after
single finished, judging the latter more accurate. Use that selected endpoint below;
retain the original snapshot above. It incorporates the previously separate30-to28
session interval into multi's manual usage window, without changing its398-second
workflow clock. Single ending is25%/41%. These are raw dashboard observations, not
exact tokens or credits; remaining/used wording and dashboard lag were not verified.

| Metric | Multi-agent | Single-agent |
| --- | --- | --- |
| Branch | multiagent-soc-search | singleagent-soc-search |
| Initial candidate | a47df937694c854b25641b8df383c918a907c4a7 | 560ffcb73516e51e030d204f8104a651028ba4e1 |
| Same common starting checkpoint | f5ec63b99fbb81148cd3ad3206e2508df9974a90 | f5ec63b99fbb81148cd3ad3206e2508df9974a90 |
| Focused acceptance/validator tests |149/149 |149/149 |
| Separately executed frozen security-tag subset |100/100 |100/100 |
| Main/test Checkstyle |Pass |Pass |
| Observed initial workflow time |398s (6m38s) |271s (4m31s) |
| Development specialists |3 plus coordinator |1 |
| User development interventions during run |0 |0 |
| Initial compile diagnostics |Not reported |2 |
| Initial test failures before self-fix |1 close-order case |1 literal-input case |
| Initial Checkstyle violations before self-fix |79 |9 |
| Post-review correction passes |0 |0 |
| Common source-contract acceptance |FAIL |FAIL |
| Attributable common source findings |4 |1 |
| Shared test/spec-conflicted criterion |1 unscored |1 unscored |
| 5-hour dashboard start to selected end |36% to28% |28% to25% |
| Observed session percentage-point decrease |8 |3 |
| Weekly dashboard start to end |43% to42% |42% to41% |
| Observed weekly percentage-point decrease |1 |1 |
| Exact tokens / credits |N/A |N/A |

### Common findings, with both initial candidates unchanged

Common evaluator independently inspected both candidates against AI-T2-C1 without
the earlier review findings. Source inspection, not new execution, established:

- Multi: request prompt invents category TOYS and omits existing SPORTS (client
  lines36–41); body read I/O errors become INVALID_RESPONSE instead of NETWORK
  (157–161); after headers, body reader/stream is not canceled by timeout or close
  (133–163,320–325); only first Content-Encoding value is checked, permitting an
  unsupported additional value (292–295).
- Single: a short valid deadline can finish before sendAsync dispatch, while the
  HTTP future is still null, allowing a request to start after timeout (140–150).
- The deadline race is a source-inferred possible ordering, not experimentally
  reproduced. Finding counts do not imply equal severity.
- Multi's in-window final reviewers found the body cancellation and error-mapping
  defects (2 of its4 attributable findings), stopping acceptance despite green tests. Other findings and
  all single findings came from the common post-run assessment. No code was fixed
  following any review, and no common tests were weakened or changed mid-run.

Shared encoding criterion is UNSCORABLE: both concatenate buyer text into input
rather than the specified inner JSON encoding (multi107–109, single123–125), but
SocSearchClientTest216–225 requires the raw hostile buyer string to remain a literal
substring of the parsed input. Quotes/backslashes would be escaped by compliant
inner encoding, contradicting that assertion. Initial readiness and final reviews
missed this oracle conflict. Root audit and evaluator clarification exclude it from
workflow-attributed defect counts (initial provisional5/2 becomes4/1), while
preserving evidence of both formal deviations. This is a shared setup defect;
it compromises comparison for that criterion, not proof of a successful injection.
Single reported its initial test failure on literal preservation, so tests may have
steered its implementation. Do not blame either arm for satisfying this conflict.
No tests corrected or benchmark rerun; any correction requires explicit owner plan.

### Verification and interpretation

Coordinator inspected both focused XML reports (73 client +76 validator cases) and
styles:zero failures/errors/skips/style errors. Frozen SocSearchClientTest blob
b67b9e8f24eef1067b8caa22bf039c2ff6f7b830 matches both snapshots. Independent common
tagged execution used the same ignored security-only init script and both class
filters:58 client +42 validator security cases each, exit0 on both. That100 is a
subset of149, not100 additional independent tests. Local HTTPS fixture checks prove
fixture operation, not live SoC interoperability. Exact request-byte edge and
disconnect mapping remain known test gaps; passing tests do not prove full contract.

Additional `check build shadowJar` runs were interrupted after approximately14minutes
for both candidates, outside measured windows. No aggregate full regression/build
pass is claimed. The complete application suite was active in isolated PostgreSQL
provisioning; this interruption is not an observed assertion failure or proof of
an application defect. Docker CLI lookup failed earlier, but test-worker diagnostics
confirmed Testcontainers PostgreSQL reachable. Both owned workers stopped after
interrupt. Standalone `shadowJar` then passed on both (UP-TO-DATE); both resulting
JARs contain SocSearchClient, Gson and PostgreSQL driver, with zero org/sqlite entries.

Only one paired run:single was second; dependency/project caches and operational
process reminders were not perfectly matched. Coordinator prompted multi to finish
style work after premature handoffs; single received the same completion policy in
its initial briefing. Neither received the other arm's code or defect-specific hints.
Common setup, readiness, post-run evaluation and result bookkeeping are separate
from measured development time. Revised multi manual credits include extra wrap-up;
display rounding/lag prevents exact usage ratios or a general cost claim. One
independent common evaluator is shared scoring, not a single-arm development reviewer.
Separate worktrees share a filesystem; instruction isolation is not a security sandbox.

This case observed a shorter initial single run and a smaller selected session
percentage drop. Multi's extra reviews exposed defects before acceptance, but did
not fix them or produce an accepted candidate. Both workflows missed contract
requirements; neither runtime feature is ready to use. No live SoC calls, hosted
database tests, deployment, seller-branch reconciliation or correction pass occurred.
Machine-readable observations are in workflow/benchmark-results.csv.

Benchmark results are complete; SOC-MULTI/SOC-SINGLE remain unaccepted implementation
tasks because of confirmed contract defects. No automatic correction or rerun is
authorised. Any next repair must address the shared oracle conflict explicitly and
preserve these initial snapshots/results. Full marketplace regression remains an
uncompleted verification gate for actual feature acceptance.

Post-run common evaluation and result preparation were observed from19:11:28 to
19:29:58UTC (20October03:11:28–03:29:58SGT),18m30s, including the interrupted broad
regressions. This shared overhead is separate from both development windows; its
credit delta and total pre-run preparation time were not measured. Final result
commit bookkeeping follows that endpoint.

## SOC-FIX-1 — Approved connection readiness correction, 2026-10-20

- Authority: owner approved the proposed single-candidate repair, regression checks
  and one controlled live smoke call in chat ("ok can you do run that for me").
- Baseline: single candidate 560ffcb; corrections live on codex/soc-client-readiness
  based on current consolidated records. Original benchmark commits, branches,
  fixed suite and results CSV remain historical and unchanged.
- Specification: AI-T2-C1. Encode buyer text as a separate JSON data object inside
  input, retaining its decoded text exactly; replace only the contradictory raw
  substring assertion. Preserve all valid assertions. No UI/quota/deployment scope.
- Owner: one Luna implementer, production client/tests/fixtures and relevant guides;
  coordinator owns specs/plan/AGENTS/current handoff. No new task logs.
- Steps: adopt single client; reproduce/fix deadline/dispatch cancellation race;
  test inner JSON round-trip and request byte boundaries/network failure mapping;
  run focused tests/styles, full test/check/build/shadowJar against disposable local
  PostgreSQL only; prepare safe opt-in smoke runner, use a locally available SoC
  credential for at most one request, never print secrets or raw provider content.
- Acceptance: approved defects resolved with executable coverage; final independent
  specification/coverage and security reviews; actual full-check results and live
  compatibility status recorded separately. Missing credentials block only live check.
- Correction count: single-candidate cycle 1/1 authorised and consumed; no extra
  correction allowance. Group remaining findings and report rather than looping.
- Completed: No. Approved encoding and timeout-registration fixes implemented.
  Final focused tests: 76 client + 76 validator, zero failures/errors/skips; styles
  pass. Full application test task completed in the 19m44s invocation, whose later
  Checkstyle failure was an unused test import, subsequently fixed. Exact full test
  count was not preserved. Additional race/disconnect regressions passed focused
  checks afterward; production code unchanged. Remaining `check build shadowJar
  -x test` passed; no final full-suite rerun claimed. `git diff --check` passed.
  Both final reviewers independently found one unresolved close/scheduler race:
  `timer.schedule` outside the guarded block can throw RejectedExecutionException
  if close shuts down the timer between the final closed check and scheduling.
  No reproducer or further fix was performed. Optional smoke runner retained as
  intent-to-add despite the tools ignore rule. One live synthetic call using local
  .env.local credentials returned INVALID_RESPONSE; raw response/status not retained
  by the safe runner, so cause and provider compatibility remain unverified. No
  retry, hosted database action or credential output. UI benchmark remains gated.

### Proposed next direction — not authorised

One additional bounded correction pass would cover a deterministic close-versus-
schedule regression and lifecycle fix, plus one additional diagnostic SoC request
that retains only HTTP status and structural envelope metadata (never raw content,
headers/tokens or buyer data) to identify the INVALID_RESPONSE cause. Preserve safe
failure behavior and document any provider-contract change for owner approval before
implementation. Then run affected tests/styles/packaging and focused final reviews.
This exceeds the current 1/1 allowance and requires explicit owner extension.

## SOC-CHAT-1 — owner-directed provider format correction, 2026-10-20

- Authority: owner supplied Chat Completions example, then requested "can we use
  this ... format". Explicit additional direction covers provider format only;
  SOC-FIX-1 historical 1/1 exhaustion and outstanding close/scheduler race remain.
- Spec: AI-T2-C2; dependencies: existing client/validator and supplied API example.
- Owner: same Luna implementer owns client/tests/fixtures/smoke runner/DeveloperGuide;
  coordinator owns shared spec/PLAN/AGENTS/handoff and secret-safe local env update.
- Steps: use API base URI + /chat/completions, messages/reasoning_effort request,
  choices message parsing and strict safe rejection; update synthetic fixtures and
  assertions for changed contract while preserving applicable security/boundary tests.
  Run focused tests/styles and packaging; existing DB/application code unchanged,
  no repeat of unchanged full PostgreSQL suite. Final independent focused reviews.
  Coordinator updates nonsecret local base/model only and performs one live smoke.
- Acceptance: documented wire contract covered and verified, live result recorded;
  separately disclose the existing lifecycle blocker. Preserve original benchmarks,
  branches, results CSV and other presentation work. No UI/deployment/seller merge.
- Completed: Yes — 2026-10-20 for the provider-format correction only. Focused
  client82 + validator76 =158 tests pass with zero failures/errors/skips, styles
  pass, `check build shadowJar -x test` and Java25 smoke compilation pass. Initial
  refusal-field regression failed then passed after correction within this directed
  work. No unchanged full PostgreSQL suite repeated. Final independent specification/
  coverage and security reviews pass the format change with no new finding; client
  SHA256 C6889F1524FEDF30ADDA819A73F8A58E10E67BCE42E90218B3C3E2E002CA1731.
  Coordinator's one live synthetic SoC call using updated local /v1 base and
  qwen3.5:9b returned `proposal accepted`; key line preserved and no request, secret
  or provider content displayed. This proves one successful client/provider exchange,
  not model quality, quota compliance or full feature readiness. Historical benchmark
  commits/results unchanged. Prior close/scheduler race remains unresolved; no
  additional review-fix loop or extension for that lifecycle finding was authorised.

## BENCH-P3 — explicit AI-search UI, approved 2026-10-20

Source AI-P3/AI-T3. Owner approved benchmark implementation in chat on2026-10-20.
Owner confirmed one existing query field plus a button labelled exactly "AI Search";
shared acceptance tests must assert that visible text, not "Search with AI".
Pre-setup readings:5-hour37%/weekly27%; this is not the measured arm baseline.
Common setup authorised; measured ready/credit checkpoints remain required after
freezing. UI-SETUP in progress on codex/ai-search-ui-setup. Lifecycle foundation
accepted under SOC-LIFECYCLE-1; do not repeat or expand its consumed correction.

| Task | Dependencies / ownership | Acceptance / Completed |
| --- | --- | --- |
| UI-SETUP | SOC-CHAT-1 and SOC-LIFECYCLE-1 accepted. Fresh Luna implementer owns minimal MarketplaceUi provider seam and frozen UI tests/fixtures; coordinator owns shared records/Git. | Completed: Yes,2026-10-20. Final21 UI cases:3 baseline pass/18 expected missing-button assertion failures,0 errors/skips,31.039s; root XML/hash verified. Test style/diff pass; unchanged159 client,2 legacy UI/main-style/build-packaging evidence reused. Both focused final reviews accept corrected setup with disclosed common SOURCE checks below. No measured UI implemented; setup correction1/1 complete. |
| UI-FREEZE | UI-SETUP. Coordinator freezes commit, test/production hashes and identical branches/workspaces; arm scope excludes common tests/client/seam. | Completed: Yes,2026-10-20. Source/test checkpoint3bb5d1960a2997f3c7bc3a8b00d43be0ad8db9c8. Created clean codex/multiagent-ai-search-ui and codex/singleagent-ai-search-ui in matching tools/benchmark workspaces; verified same HEAD/test blob be4e072d0de179fe17988de6162e5559b3e5ff45. Only shared status/checklist metadata follows that checkpoint and is advanced identically before dispatch. No secrets, presentation drafts or old results included. Actual common final revision is pinned in each dispatch brief. |
| UI-MULTI | UI-FREEZE + owner fresh credit/ready. One fresh Luna implementer owns complete UI/Main/tests/guides batch; final independent Luna spec/security reviews only. | AI-UI-01..06 fixed checks/styles and relevant packaging; stable candidate saved; no post-submission fixes unless explicit grouped approval. Completed: No. |
| UI-SINGLE | UI-FREEZE; scheduled after UI-MULTI ending reading + own fresh ready. Fresh Luna implementer same model/role/scope, self-verification only. | Same requirements/checks; no other arm findings/code supplied. Save candidate and stop for ending reading. Completed: No. |
| UI-SCORE | Both saved candidates/readings. Independent common evaluator plus coordinator. | Same acceptance/source review for both, including stale-session and no-AI-on-manual paths; times, test counts, findings, interventions, raw coarse usage readings and limits consolidated in existing PLAN/CSV. Completed: No. |

Metrics: elapsed dispatch-to-candidate and workflow completion; acceptance/security
checks; source findings with severity and proven/inferred status; review overhead;
post-submission corrections; human interventions; manually recorded session/weekly
percentage readings. No exact token/cost estimates. Setup/common evaluation excluded
from measured windows and disclosed. Sequential multi then single as before unless
owner changes order. One implementation pass plus final reviews; at most one shared,
explicitly approved correction pass, no silent reset of historical budgets. Preserve
first-pass results even if later repaired; no artificial failures or weakened tests.

### UI-SETUP initial candidate and approved correction

Candidate seam/test hashes: MarketplaceUi SHA256
2ED59E588402F72E804823687E23369ACAE55D4168FF6652E5554B59AABE61E7;
AiSearchUiBenchmarkTest SHA256
6871DDEF43183CA63D045C236E29D18B1193EC52ECB017C9B3ABF22D6DFDEC2C.
Root inspected UI XML:15 tests,3pass/12expected missing-ai-search assertion failures,
0errors/skips,22.459seconds. Worker reports existing client159green, selected legacy
UI2green, both styles and check/build/shadowJar -x test pass. No feature/UI/Main code
implemented beyond the constructor seam; no live/provider/hosted calls. Setup source
and tests remain unfrozen; no measured arm started. Pre-setup37%/27% is not reused.

Independent specification review found three required coverage gaps:
AI-UI-01 single query surface, AI-UI-03 pending/loading indication and AI-UI-05
edit-only invalidation (current test combines edits with manual submit, masking
which action invalidates). Security review found no confirmed security defect;
its provisional claim of an already-visible AI button was withdrawn because tests
assert its absence on the initial baseline. Root also found malformed developer
commands (leading extra backslash) and stale setup approval/test-command pointer.

Owner approved in chat on2026-10-20. UI-SETUP correction cycle1/1, tests/docs only:
1. Assert only one editable query surface, reusing search-query, excluding existing
   minimum/maximum price inputs; do not forbid read-only preview fields or impose layout.
2. Assert observable pending/loading feedback before resolving controlled future,
   without requiring specific copy or a specific progress-control implementation.
3. Split query-edit-only, filter-edit-only and manual-submit invalidation; include
   stale preview as well as pending completion, with no unrelated invalidating action
   that could hide a defect. Preserve all valid existing assertions.
4. Correct wrapper commands to dot-backslash gradlew.bat, explicit UI invocation
   and approved-setup/pending-measured wording in existing DeveloperGuide.
5. Rerun UI expected-red suite/styles and affected existing baseline checks, then
   focused re-review of grouped findings before freezing. No production seam/client
   or UI implementation changes. No extra loop beyond1/1 without explicit direction.

Authority: explicit owner reply "approved". UI-SETUP Completed:Yes; correction1/1
complete with focused specification and security re-review acceptance. Final test
SHA256 FA8791E97D6EC34F5342B785AAA23B813801936A7C1FDD0808343DD2956AC05D;
seam hash unchanged. Preserve initial candidate evidence; no extra repair loop.

Frozen common SOURCE assessment for BOTH saved arm candidates additionally requires:
Main constructs optional client from the three process environment settings, passes
the borrowed provider and closes the owned client on stop; missing/invalid config
keeps app/manual search working and supplies visible fixed safe feedback with AI
disabled. The fixed UI test verifies disabled/manual behavior but not that actual
configuration feedback path. This is an explicit coverage limit, not waiver of
AI-UI-06; both reviewers accept this equal verification allocation before freeze.
Do not infer feedback correctness merely from a disabled button. Record source/path
and visible-feedback evidence for each arm in UI-SCORE.

Shared fixture file: src/test/java/hotshop/ui/AiSearchUiBenchmarkTest.java
with reusable local fake-provider helpers. Common setup check command is
`./gradlew test --tests hotshop.llm.SocSearchClientTest --tests
hotshop.service.AiSearchProposalValidatorTest checkstyleMain checkstyleTest`.
Measured fixed UI command is `./gradlew test --tests
hotshop.ui.AiSearchUiBenchmarkTest checkstyleMain checkstyleTest`; add exact selected
legacy search tests to the frozen packet after fixture inspection, plus required
build/packaging commands. Full regression verification is reported separately from
focused timing where its unchanged database work dominates. Setup/UI tests and
commands must be reviewed/frozen before either measured dispatch.
Measured focused command is the UI21 suite plus both styles as above; then
`./gradlew check build shadowJar -x test` verifies remaining aggregate/packaging
tasks without repeating the full marketplace database suite. Selected unchanged
legacy search regression evidence is common setup only, not silently counted as
measured new cases. No full-regression success follows from these focused runs.
All Java checks use wrapper Java25/Gradle9.1. Common preparation and UI timing do not
use hosted data, live SoC, deployment, secrets or the seller branch.

## SOC-LIFECYCLE-1 — explicit close-race extension, 2026-10-20

- Authority: owner explicitly requested "ok lets fix that race" after the exhausted
  allowance was disclosed. This grants one additional bounded lifecycle repair pass
  and final focused reviews only; historical SOC-FIX-1 1/1 remains recorded. Total
  authorised readiness repair passes now2, this extension is pass2/2; no reset.
- Dependencies: accepted SOC-CHAT-1 format; unresolved finding from both SOC-FIX-1
  reviewers at timer.schedule outside try. AI-T2-C2 protocol stays unchanged.
- Owner: same Luna implementer owns client, relevant tests/fixtures and existing
  DeveloperGuide note. Coordinator owns PLAN/spec/AGENTS/handoff. No UI setup,
  benchmark branches, live calls, credentials, unrelated refactors or new logs.
- Steps: reproduce close between final open check and deadline registration with a
  deterministic gate, then coordinate registration/shutdown and/or safely resolve
  scheduler rejection as CLOSED without synchronous exception, leaked call/timer,
  transport or stream. Preserve normal timeout, concurrency and idempotent close.
  Avoid waiting on network or user callbacks while holding a lifecycle lock.
- Acceptance: meaningful behavioral red then green regression; existing focused
  client+validator tests and styles pass, packaging/build checks pass; final spec/
  coverage and security reviews against stable candidate with no unresolved finding
  in this scope. No repeat unchanged full PostgreSQL suite and no live SoC needed.
- Completed: Yes — 2026-10-20. Deterministic close-during-schedule regression
  failed with ExecutionException caused by RejectedExecutionException before fix,
  then passed after guarding scheduler rejection and completing CLOSED. Regression
  checks no HTTP dispatch, terminated scheduler and empty timer queue. Final83
  client +76 validator=159 focused tests pass with zero failures/errors/skips;
  both styles and `check build shadowJar -x test` pass. No unchanged full DB suite
  repeated and no live calls in this pass. Final specification/coverage and security
  reviews confirm this race resolved with no remaining confirmed contract finding.
  A source-inferred transient sendAsync invocation after a raced finish was reviewed:
  setHttp/finish cancel its future and a late body is closed; both reviewers withdrew
  their provisional failure classification because the contract requires cleanup,
  not a stronger guarantee of zero transient invocations. No code changes followed
  those reviews. Client SHA256
  48BD0F0BB8E0F3E216AA814F2DAA5B062B5CD7E9DF1BEE9E615EA221C76A60F4.
  Extension pass2/2 consumed; UI benchmark detail/setup/execution remains pending.
