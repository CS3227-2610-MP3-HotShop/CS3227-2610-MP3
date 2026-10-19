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
