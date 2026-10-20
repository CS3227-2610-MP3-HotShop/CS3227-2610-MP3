# LLM Description Implementation Plan

Source: [technical specification](LLM_FEATURE_TECHNICAL_SPEC.md), sections 1–5;
requirements: [product specification](LLM_FEATURE_PRODUCT_SPEC.md), FR-001–006,
NFR-001–007, TC-01–12 and UC-001–003. Created 2026-10-20.

The plan itself authorises no execution or deployment. On 2026-10-20 the user
explicitly selected TASK-001–004 and confirmed configuration readiness; see their
execution records below. Subsequent selections completed TASK-005–009; the user
has now selected TASK-010–011 and confirmed this plan. TASK-012 onward remains
unauthorised. Initial source
inspection found the JavaFX editor, single service worker/session and runtime lock,
but no feature implementation, JSON dependency or `llmLiveTest` task. Inspection
is not passing runtime evidence. No SQL migration is planned; generation never
saves a listing.

## Execution rules and current blockers

| Gate | Current state / affected work | Resolution |
| --- | --- | --- |
| G-01: implementation scope | Resolved 2026-10-20: TASK-001–009 completed; user selected TASK-010–011 and confirmed this plan. | Design contracts govern the selected scope; TASK-012 onward remains unauthorised. |
| G-02: deferred tests | Resolved 2026-10-20: user explicitly confirmed endpoints and API key are ready. No secrets inspected. | Offline verification authorised; live calls still require separate explicit authorisation. |
| G-03: provider tools | Provider-side tools cannot be confirmed disabled. Blocks full end-to-end no-tool security acceptance in TASK-015/017, not independent local-boundary implementation after G-01/02. | Obtain provider capability assurance/configuration evidence, or explicit product-spec revision accepting a narrower guarantee. Prompt instructions or passing samples alone do not close this gate. |
| G-04: hosted identity | Independent concurrent JPro client identity/session isolation is not established in current code. Multi-user hosted acceptance in TASK-017 remains blocked. | Separately approved lifecycle/session integration review/work; do not add an authentication rewrite to this plan. Local/isolated runtime acceptance can be recorded separately. |

Single server, in-memory per-account admission shared across service instances,
quota reset on process restart are approved. No cross-replica quota store or new
REST application endpoint is planned. Docker/Render packaging and deployment are
later work, excluded from the local feature implementation tasks below. Keep
their secret-handling and identity prerequisites in the handoff; no release claim.

**Ownership:** coordinator owns plan status and shared records. Testing specialist
writes tests before the responsible implementation specialist writes behaviour.
Application specialist owns service/config/transport; JavaFX specialist owns editor
and UI lifecycle. Assign one writer per shared file (`build.gradle`,
`ApplicationRuntime`, `Main`, `MarketplaceUi`); serialize dependent edits. Reviewer
performs independent substantial-change review; final security review uses a stable
candidate. Workers do not revert others' changes or mark their own tasks complete.

**TDD protocol used by every behaviour task below:** testing owner derives partitions,
writes runnable tests, then runs the named focused command and records expected
behavioural failures **before** production behaviour is implemented. Minimal typed
stubs may enable compilation, but compile errors/setup failures are not red evidence.
Only then implement the smallest passing change, refactor changed code as needed,
and rerun the focused suite and relevant regressions. Keep each unrelated negative
condition separate. Use deterministic clocks/schedulers/gates, not test-order
dependencies or network sleeps. If a test unexpectedly passes, inspect the existing
path and record evidence rather than manufacturing a failure. Retain tests intact
unless their specification is demonstrably incorrect. No task is complete without
its own acceptance evidence/date in the task log; partial test drafting remains No.

**Commands:** Windows uses `.\gradlew.bat`; POSIX uses `./gradlew`. All commands
below are planned and subject to G-02. `--tests` filters select proposed JUnit
classes, not current existing suites. `llmLiveTest` is created in TASK-002 and
requires separate explicit live-run authorisation. No live calls through ordinary
`test`, `check` or `build`. Use synthetic secrets and controlled clients for all
ordinary tests. Baseline/check/build failures outside this feature are reported
separately; do not weaken tests or Checkstyle.

## Ordered tasks

### TASK-001: Establish authorised scope and execution readiness

Execution record (2026-10-20): user selected TASK-001–004 and confirmed this exact
plan and configuration readiness. Selection accepts the specified Gson 2.14.0
(subject to availability/advisory verification), typed outcomes, literal UTF-8
configuration syntax, Unicode word-count rule and shared server quota ownership.
No permission for live calls is inferred. All selected prerequisites are within
TASK-001–004, with no cycles or missing references; all four were initially No.
Coordinator owns plan/build integration and records; testing specialist owns
infrastructure/corpus/configuration tests; application specialist owns configuration
implementation after verified red; independent reviewer owns final code review.
Build edits have a single assigned writer. No runtime/UI/quota implementation is
authorised in this batch. Provider-tool and hosted-identity gates remain open.

**Description:** Coordinator records selected task scope, approved design contracts,
owners, configuration readiness and verification prerequisites; no secret values.
**Source:** Technical sections 1.1, 3.3, 5.2; NFR-006/007; G-01/02.
**Dependencies:** None.
**Completed:** Yes — 2026-10-20. Scope/contracts/owners and readiness recorded;
Java 25.0.2 / Wrapper 9.1.0 baseline: 688 tests, both Checkstyle tasks passed.
Evidence: [TASK-001 log](../logs/2026-10-20-llm-task-001-readiness.md).

**Steps**

1. Confirm task selection and acceptance of design mechanisms, including Gson,
   typed outcomes, config syntax, word counting and server quota ownership.
2. Record user's endpoint/token readiness indication; distinguish readiness from
   separate permission for live calls. Do not inspect real `.env` contents here.
3. Once allowed, run `.\gradlew.bat test checkstyleMain checkstyleTest` as baseline;
   record Java 25/Wrapper 9.1.0, actual outcomes and unrelated failures.

**Acceptance criteria**

- [x] Approved scope, owners, contracts and G-02 resolution recorded without secrets.
- [x] Baseline has actual results or diagnosed blockers; no feature completion claimed.

### TASK-002: Prepare minimal build and deterministic test infrastructure

**Description:** Testing owner and build writer prepare runnable test seams without
implementing generation behaviour; affect `build.gradle` and test resources/helpers.
**Source:** Technical 1.1, 2.2, 5.2; NFR-006/007.
**Dependencies:** TASK-001 — execution scope and test readiness.
**Completed:** Yes — 2026-10-20. Eight infrastructure tests and Checkstyle passed;
Gson 2.14.0 resolved and strict streaming verified on Java 25. Live task/tag
isolation inspected without provider calls. Evidence:
[TASK-002 log](../logs/2026-10-20-task-002-llm-test-infrastructure.md).

**Steps**

1. Verify the proposed Gson version's official availability/advisories and Java
   compatibility; add only the approved JSON module. Escalate material replacement
   to design review; do not silently substitute an incompatible version.
2. Add `llm-live` tag exclusion and explicit `llmLiveTest` task; prevent it joining
   ordinary verification automatically. Prepare controlled HTTPS/test CA, capturing
   client, fake monotonic clock/scheduler and FX completion gates.
3. Add minimal typed interfaces/stubs sufficient for later behavioural red tests;
   no success behaviour or tests that merely assert a stub throws.
4. Verify fixtures independently using `.\gradlew.bat test --tests '*LlmTestInfrastructureTest'`
   and inspect Gradle task configuration without contacting a provider.

**Acceptance criteria**

- [x] Infrastructure tests run successfully; transport fixtures use synthetic tokens.
- [x] Live task/tag separation verified; no real network generation occurs.
- [x] Baseline dependencies preserved; compatibility evidence and seams recorded.

### TASK-003: Write the live security corpus before generation implementation

**Description:** Testing owner defines tagged live tests/fixtures and semantic
evaluation rubric without running them or enabling provider credentials in ordinary tests.
**Source:** Technical 3.1, 5.2; FR-004; TC-05/06/11; UC-002.
**Dependencies:** TASK-002 — test seams and isolated live task.
**Completed:** Yes — 2026-10-20. Tagged 26-case corpus compiles, judging rubric
reviewed, ten offline infrastructure/corpus tests and Checkstyle pass. Live
exclusion verified; no live evaluation run. Evidence:
[TASK-003 log](../logs/2026-10-20-task-003-llm-security-corpus.md).

**Steps**

1. Write `LlmLiveSecurityTest` and corpus under `src/test/resources/hotshop/llm/`:
   ordinary items/conditions, role spoofing, disclosure requests, encoded/Unicode/
   multilingual injections and tool/action requests within the 120-code-point title limit.
2. Define expected grounded text or safe failure; attack compliance/fabricated facts/
   refusal inserted as description fail evaluation. Use deterministic boundary
   assertions plus a documented human rubric; a second LLM is not the sole judge.
3. Compile using `.\gradlew.bat testClasses`; verify tag exclusion. Record corpus
   and rubric review without claiming a live red run. This task writes evaluations,
   not passing model evidence; live execution belongs to TASK-014.

**Acceptance criteria**

- [x] Runnable tagged corpus and explicit judging rules cover TC-05/06/11; compile succeeds.
- [x] No live calls; evaluation remains unverified and cannot authorise implementation alone.

### TASK-004: Implement private configuration loading through TDD

**Description:** Testing/application owners add `LlmConfiguration` with safe unavailable
state and root working-directory `.env` syntax; no actual credentials changed.
**Source:** Technical 1.2, 3.2; FR-003; NFR-002/004; TC-04/07/09.
**Dependencies:** TASK-003 — prewritten evaluation corpus and infrastructure.
**Completed:** Yes — 2026-10-20. Configuration red/green evidence includes 69
passing configuration tests and 38 account regressions; private immutable settings
and manual persistence verified. Independent review has no unresolved material
findings. Final check/build/shadowJar pass with 767 tests. Evidence:
[red log](../logs/2026-10-20-llm-task-004-configuration-red.md),
[implementation log](../logs/2026-10-20-llm-task004-configuration-implementation.md),
[integration/review log](../logs/2026-10-20-llm-task-001-004-integration.md).

**Steps**

1. Red: write `LlmConfigurationTest` using temp synthetic files: valid quoted/unquoted
   values, first `=`, comments/Unicode, missing/duplicate keys, invalid quotes,
   whitespace/control token, HTTP/user-info/fragment URI, blank model; no shell expansion.
2. Run `.\gradlew.bat test --tests '*LlmConfigurationTest'`; verify missing-behaviour failures.
3. Green: implement immutable private settings, exact HTTPS URI, safe diagnostics,
   load-once behaviour and failure isolation. Refactor; rerun suite and account baseline.

**Acceptance criteria**

- [x] Red then green evidence for parser/validation partitions; no token in toString/errors/logs.
- [x] Missing configuration leaves manual application paths available; no secret file writes.

### TASK-005: Implement account quota admission through TDD

**Description:** Add `DescriptionQuota` with process-shared injectable monotonic time
and atomic reservation ownership; no persistent migration.
**Source:** Technical 1.3; NFR-005; TC-10.
**Dependencies:** TASK-003 — infrastructure; independent of configuration implementation.
**Completed:** Yes — 2026-10-20. Verified behavioural red then 28 passing quota
tests and both Checkstyle tasks; deterministic boundary/race/ownership/cleanup
evidence includes staggered retry-delay regression. Independent review has no
unresolved material findings. Shared server lifecycle wiring remains TASK-010.
Evidence: [red log](../logs/2026-10-20-llm-task005-quota-red.md),
[implementation log](../logs/2026-10-20-llm-task005-quota-implementation.md).

**Steps**

1. Red: `DescriptionQuotaTest` covers 5th/6th minute and 30th/31st hour admission,
   just-before/at/after expiry, independent accounts, two callers racing for one slot,
   simultaneous release/admission, failed dispatch counting, pre-dispatch rollback,
   stale reservation release, retry-delay ceiling and expired-entry cleanup.
2. Run `.\gradlew.bat test --tests '*DescriptionQuotaTest'` and record behavioural red.
3. Green: implement atomic timestamp/active state; refactor and rerun. Verify new
   server registry starts empty while multiple services sharing it see identical limits.

**Acceptance criteria**

- [x] All quota/race boundaries pass deterministically without sleeps.
- [x] Active slot released exactly once by matching request ID; no account bypass via new form.

### TASK-006: Implement fixed prompt and JSON request encoding through TDD

**Description:** Add request-building behaviour inside `HttpLlmClient` and input seams;
only title/condition plus fixed instruction and configured model are transmitted.
**Source:** Technical 2.1/2.2, 3.1; FR-002/003/004; TC-01/02/04–09.
**Dependencies:** TASK-004 — approved configuration; TASK-005 — admission contract.
**Completed:** Yes — 2026-10-20. Verified 23 behavioural red cases followed by
130 passing related offline tests and both Checkstyle checks. Fixed prompt,
two-stage streaming encoding, code-point validation and exact UTF-8 byte limits
verified; independent review has no unresolved material findings. Final
check/build pass: 818 tests, zero failures/errors/skips. No HTTP dispatch added.
Evidence: [red log](../logs/2026-10-20-llm-task006-request-red.md),
[implementation log](../logs/2026-10-20-llm-task006-request-implementation.md),
[integration/review log](../logs/2026-10-20-llm-task005-006-integration.md).

**Steps**

1. Red: `LlmRequestTest` tests valid five conditions; null/blank/1/120/121-code-point
   title partitions; proper quote/newline/Unicode JSON encoding; no-tool instruction;
   exactly `model`/`input`, 8,192/8,193 UTF-8 bytes and unchanged configuration under attacks.
2. Run `.\gradlew.bat test --tests '*LlmRequestTest'` before implementing the request builder.
3. Green/refactor: encode product facts then outer JSON via streaming writer; size-check
   before admission. Rerun with benign “System design textbook” and synthetic secrets
   outside product fields, asserting no secret/private context/tool definition enters body.

**Acceptance criteria**

- [x] Red/green request evidence covers encoding/size and minimum-input boundaries.
- [x] No banned-word defence, previous description, identity, history or tool definition sent.

### TASK-007: Implement strict response and description validation through TDD

**Description:** Add bounded JSON parsing and validated description extraction inside
the transport component; no nested fallback or reflection-based deserialisation.
**Source:** Technical 1.2, 2.2, 4; FR-004; NFR-003/005; TC-03/08/12.
**Dependencies:** TASK-006 — transport component seam and JSON dependency.
**Completed:** Yes — 2026-10-20. Verified behavioural red (124 cases, 29 expected
failures), then 127 passing response tests and 219 passing response/request/config
tests with Checkstyle Main passing. Independent read-only review found no
material issues. Strict full-envelope validation, canonical text, UTF-8,
duplicate/depth and word/code-point/byte limits verified; semantic model behaviour
remains unproven. Evidence:
[red log](../logs/2026-10-20-llm-task007-response-red.md),
[implementation log](../logs/2026-10-20-llm-task007-response-implementation.md),
[integration/review log](../logs/2026-10-20-llm-task007-008-integration.md).

**Steps**

1. Red: `LlmResponseTest` separately tests valid completed top-level text, conflicting
   nested copies, missing/null/wrong-type/blank text, duplicate keys, trailing JSON,
   comments, invalid UTF-8, depth 32/33, incomplete status, wrong role/content types,
   refusal/tool/function/MCP items alongside apparently valid text, and metadata-only output.
2. Test word counts 0/49/50/70/71/99/100/101, code-point limits 5,000/5,001,
   NBSP/supplementary characters/line breaks/punctuation/hyphens and no truncation.
3. Run `.\gradlew.bat test --tests '*LlmResponseTest'` for behavioural red; implement
   strict full-envelope parsing and exact word rule, refactor and rerun focused regressions.

**Acceptance criteria**

- [x] Valid text extracted once; metadata/nested text never override canonical top-level text.
- [x] Structural invalid/refusal/tool responses and hard-limit violations fail safely.
- [x] Evidence explicitly distinguishes structural validation from unproven semantic detection.

### TASK-008: Implement bounded asynchronous HTTPS transport through TDD

**Description:** Implement `HttpLlmClient` network lifecycle with dedicated executor,
scheduled deadline and bounded identity/gzip response collection.
**Source:** Technical 2.2/2.3, 5.1; NFR-002/003/005; TC-04/08/09/10.
**Dependencies:** TASK-007 — request/response contract and focused passing suites.
**Completed:** Yes — 2026-10-20. Verified 53 behavioural red cases followed by
69 passing transport cases covering HTTPS policy, status/error mappings,
independent deadline, streamed identity/gzip limits and cancellation/late arrivals.
Independent review has no outstanding material findings. Full test, both
Checkstyle tasks, check and build passed: 1,014 tests, zero failures/errors/skips.
No provider calls or application wiring added. Evidence:
[red log](../logs/2026-10-20-llm-task008-transport-red.md),
[implementation log](../logs/2026-10-20-llm-task008-transport-implementation.md),
[integration/review log](../logs/2026-10-20-llm-task007-008-integration.md).

**Steps**

1. Red: `HttpLlmClientTest` captures POST to exact URI, bearer/header/body rules,
   no redirects/callbacks, bad TLS/hostname/DNS, every status category, 10s connection
   timeout and 45s full-body/parse deadline using controllable timing seams.
2. Test exact/over 65,536 decoded bytes, absent/misleading Content-Length, chunked
   bodies, gzip compressed/decoded caps, unsupported encoding, stalled bodies,
   cancellation/late completion, resource release and zero automatic retries.
3. Run `.\gradlew.bat test --tests '*HttpLlmClientTest'` for red; green/refactor
   reusable client, strict TLS, bounded subscriber/decoder and single terminal result;
   rerun all `hotshop.llm.*` tests.

**Acceptance criteria**

- [x] Timeouts, stream bounds and HTTPS/error mappings pass; no unbounded body buffering.
- [x] No extra destination/tool execution; cancellation closes local resources and no retry occurs.
- [x] Raw HTTP failures, response bodies and credentials never escape as display/log payloads.

### TASK-009: Implement session-bound generation orchestration through TDD

**Description:** Add `DescriptionService`, typed results and `AuthenticatedSession`
revision; keep network/parsing off JavaFX and the serial service worker.
**Source:** Technical 1.2/1.3, 2.1, 3.1; FR-001–006; TC-01/02/07/10.
**Dependencies:** TASK-005 — quota; TASK-008 — transport contract.
**Completed:** Yes — 2026-10-20. Verified service/preparation behavioural red,
then 61 passing service, 22 preparation and 37 quota tests with account/transport
regressions. Session revision guards, responsive logout, actual-dispatch quota
timestamps and safe terminal ownership are verified. Independent review has no
outstanding material findings. Final test/style/check/build passed: 1,106 tests,
zero failures/errors/skips. Runtime wiring remains TASK-010. Evidence:
[service red](../logs/2026-10-20-llm-task009-service-red.md),
[preparation red](../logs/2026-10-20-llm-task009-preparation-red.md),
[implementation](../logs/2026-10-20-llm-task009-service-implementation.md),
[integration/review](../logs/2026-10-20-llm-task009-integration.md).

**Steps**

1. Red: `DescriptionServiceTest` covers logged-out/no-call, minimum valid inputs,
   unavailable configuration, pre-dispatch session change, same-account re-login,
   other-account switch, in-flight logout responsiveness, shared-service quota,
   no storage mutation, error mapping and safe unexpected failure handling.
2. Run `.\gradlew.bat test --tests '*DescriptionServiceTest'` and existing
   `*AccountServiceTest`; capture new behavioural failures before changing session/service code.
3. Green/refactor: capture/recheck revisions on worker, admit once, compose futures
   without blocking worker, release by reservation, return safe typed outcomes; rerun suites.

**Acceptance criteria**

- [x] Service authorises from session, has no caller user-ID parameter or listing mutation dependency.
- [x] Pending LLM does not block account/logout work; changed session gets no description.
- [x] Quota and all safe outcomes retain specified semantics with passing regression evidence.

### TASK-010: Wire runtime, shared quota and shutdown through TDD

**Description:** Integrate `ApplicationRuntime`/`Main` startup, dependency injection,
server quota ownership and nonblocking shutdown without weakening existing open/lock APIs.
**Source:** Technical 1.1/1.3, 3.2/3.3, 5.1; FR-001; NFR-002/005/007.
**Dependencies:** TASK-004 — settings; TASK-009 — service and session contracts.
**Completed:** Yes — 2026-10-20. Behavioural red preceded implementation;
12 runtime ownership/shutdown tests and existing runtime/account/listing regressions
pass. Independent review found no substantial defects. Full test, both Checkstyle
tasks, check and build passed: 1,118 tests, zero failures/errors/skips. Offline
overloads, shared quota ownership, cancellation and asynchronous draining with
retained directory lock are verified. Evidence:
[runtime red](../logs/2026-10-20-llm-task010-runtime-red.md),
[runtime implementation](../logs/2026-10-20-llm-task010-runtime-implementation.md).

**Steps**

1. Red: add `DescriptionRuntimeTest` and relevant `ApplicationRuntimeTest` cases:
   missing config/manual listing works, injected fake clients avoid real `.env`,
   server registry shared across services, one runtime close does not clear another
   service's quota, close twice, pending request/queued completion during close.
2. Run `.\gradlew.bat test --tests '*DescriptionRuntimeTest' --tests '*ApplicationRuntimeTest'`.
3. Green/refactor: wire owned resources, block new admission on close, cancel
   transport/deadlines before worker/storage teardown; retain data-directory lock
   and current session isolation limitations; rerun relevant listing/account/runtime suites.

**Acceptance criteria**

- [x] Existing runtime overloads and ordinary offline/manual operation preserved.
- [x] Close is idempotent, bounded and never blocks JavaFX; no quota reset on mere form/runtime change.
- [x] Shared quota lifecycle has explicit server owner and tested shutdown behaviour.

### TASK-011: Add Create Listing generation controls through TDD

**Description:** JavaFX/testing owners update only Create Listing presentation and
safe typed completion integration in `ListingEditor`.
**Source:** Technical 2.1, 5.1; FR-001/002/005/006; TC-01/03; UC-001.
**Dependencies:** TASK-010 — runtime service access.
**Completed:** Yes — 2026-10-20. Behavioural red preceded UI implementation;
31 generation UI cases pass, including input boundaries, all conditions, keyboard,
safe failures, shared quota feedback, unsaved replacement and manual save while
pending. Existing UI/listing regressions pass; the minimum-window screenshot was
inspected. Independent review found no substantial issues. Final full test, both
Checkstyle checks, check and build passed: 1,149 tests, zero failures/errors/skips.
TASK-012 revision/disposal safeguards remain outside this completion. Evidence:
[UI red](../logs/2026-10-20-llm-task011-generation-ui-red.md),
[UI implementation](../logs/2026-10-20-llm-task011-generation-ui-implementation.md),
[integration](../logs/2026-10-20-llm-task010-011-integration.md).

**Steps**

1. Red: `ListingDescriptionGenerationTest` verifies minimum fields, visible external
   data/replacement notice, loading/disabled Generate only, keyboard activation,
   field feedback, safe errors, editing/manual save responsiveness and Edit Listing exclusion.
2. Run `.\gradlew.bat test --tests '*ListingDescriptionGenerationTest'` for behavioural red.
3. Green/refactor: retain control references, add local progress/status, use FX callbacks
   without `UiPage.perform`, replace existing description immediately without confirmation,
   preserve other values and dirty tracking. Rerun UI and listing service regressions.

**Acceptance criteria**

- [x] Success inserts once as editable unsaved text, and only explicit save persists listing.
- [x] Failure preserves draft; no generation control on Edit Listing; keyboard/status checks pass.

### TASK-012: Guard stale UI completions and disposal through TDD

**Description:** JavaFX/testing owners extend `MarketplaceUi`/editor lifecycle guards
for field revisions, routes, authentication and accepted disposal.
**Source:** Technical 2.1, 4/5.1; FR-006; TC-10; UC-003.
**Dependencies:** TASK-011 — generation controls and completion path.
**Completed:** No

**Steps**

1. Red: extend generation/UI suites with changed title/condition/description, changed
   then reverted values, navigation/back/replace/login/logout, same-account re-login,
   save during pending generation, accepted/cancelled close, hidden window and queued
   completion before close delivered after disposal. Use latches/FX queue gates.
2. Run `.\gradlew.bat test --tests '*ListingDescriptionGenerationTest' --tests '*MarketplaceUiTest'`.
3. Green/refactor: add form/request/auth revisions and disposed guard, invalidate on
   all relevant paths; recheck before any field/status update. Keep quota slot until
   transport termination/deadline, not page detach. Rerun combined suites.

**Acceptance criteria**

- [ ] No late result/status reaches disposed, detached, edited or differently authenticated UI.
- [ ] Cancelled close leaves usable form; existing dirty/discard/navigation behaviour preserved.
- [ ] Full new/related UI regression suites pass deterministically.

### TASK-013: Verify integrated security boundaries and regressions

**Description:** Testing owner consolidates existing cases and adds missing integration
regressions test-first; reviewer checks feature-relevant trust/capability boundaries.
**Source:** Technical 3/4/5.2; NFR-001–007; TC-01–12 (live semantics in TASK-014).
**Dependencies:** TASK-012 — integrated candidate with stale guards.
**Completed:** No

**Steps**

1. Map each TC case to actual tests; exercise synthetic secret/internal canaries,
   safe diagnostics, filesystem/database snapshots around generation excluding setup,
   no output-directed URLs/actions and no tools/configuration override under attacks.
2. For a missing boundary, add runnable regression and verify behavioural red before
   its smallest approved fix; refactor/rerun. Do not silently redesign semantics to pass.
3. Run `.\gradlew.bat test checkstyleMain checkstyleTest`, then `.\gradlew.bat check build`;
   capture actual totals, baseline comparison and reviewer findings. Never run live task here.

**Acceptance criteria**

- [ ] Deterministic TC coverage traced and meaningful; all applicable non-live checks pass.
- [ ] No secret/tool/file side effects; no claim that mocked tests establish model immunity.
- [ ] Important review failures fixed through approved, test-backed corrections.

### TASK-014: Execute the separately authorised live security evaluation

**Description:** Testing owner runs the prewritten model corpus against privately
configured provider; record empirical semantics without secret values.
**Source:** Technical 3.1, 5.2; FR-004; NFR-001/006; TC-05/06/11.
**Dependencies:** TASK-003 — prewritten corpus; TASK-013 — local checks passing.
**Completed:** No

**Steps**

1. Obtain explicit live-run authorisation and confirm private configuration readiness;
   keep credentials out of logs and ordinary tests. Respect quotas or space cases
   deliberately; do not weaken production limits to make live runs faster.
2. Run `.\gradlew.bat llmLiveTest`; record model/configuration version, case IDs,
   observed outcomes, rubric review and actual run count. Distinguish provider outage
   or refused/rate-limited transport from a successfully evaluated semantic case.
3. If any attack succeeds, record failure/block acceptance; propose correction for
   approval and preserve failing corpus case before correction/retest. No universal guarantee.

**Acceptance criteria**

- [ ] Actual authorised live results distinguish pass/failure/unverified per case and rubric.
- [ ] All required cases pass observable criteria or task stays incomplete with findings.
- [ ] Provider tool non-use remains unverified unless independent assurance is obtained.

### TASK-015: Perform final implementation and security reviews

**Description:** Read-only independent reviewers assess stable candidate and test
coverage; coordinator resolves findings without granting unearned security acceptance.
**Source:** Technical 3.1/3.3, 5.2; NFR-001–007; G-03/04.
**Dependencies:** TASK-013 — local evidence; TASK-014 — live evidence.
**Completed:** No

**Steps**

1. Review service/UI concurrency, data/secret exposure, transport bounds, quota,
   raw-cause logging and test adequacy against the frozen candidate revision.
2. Seek provider capability assurance to close G-03; prompts and empty returned
   tool arrays do not prove remote non-use. Track G-04 without rewriting authentication.
3. Approve scoped correction plans, add regression-first evidence where behaviour
   changes, rerun affected checks/live cases and re-review the resulting stable candidate.

**Acceptance criteria**

- [ ] Review findings, candidate revision and actual corrective evidence recorded.
- [ ] Important defects resolved; full no-tool acceptance stays blocked while G-03 is open.
- [ ] Local review explicitly excludes unverified multi-user hosted safety; no blanket approval.

### TASK-016: Complete feature documentation and local packaging evidence

**Description:** Assigned implementers/coordinator update usage/design/instructions
and supporting records to describe actual verified behaviour, with independent review.
**Source:** Technical 3.2, 5.2; repository documentation/SDD gates; FR-001–006.
**Dependencies:** TASK-013 — actual implemented local behaviour and verification.
**Completed:** No

**Steps**

1. Update `docs/UserGuide.md` and relevant `docs/userGuide/SearchAndListings.md`
   entry for generation inputs, replacement, review/edit/save, failure and limits.
   Update `docs/DeveloperGuide.md`, AGENTS.md, feature SDD summary and task logs.
2. Document private key names/load syntax, synthetic setup, restart/quota semantics,
   Gson acknowledgement, safe test commands and current provider/hosting limitations.
3. If JAR/JPro packaging changes, run `.\gradlew.bat shadowJar jproRelease`; inspect
   resulting artifacts with synthetic secret fixtures and confirm no `.env`/credentials.
   Packaging output under tracked `release/` must not be committed automatically.
4. Independently review docs/links/traceability; run `.\gradlew.bat jproRun` only
   for authorised local manual checks with safe config. Record keyboard/form/save/
   error behaviour observed; no automatic deployment or real-secret exposure.

**Acceptance criteria**

- [ ] Both guides, instructions, SDD summary and per-task logs match actual behaviour/evidence.
- [ ] Applicable packaging/manual checks have actual results; all unperformed checks explicit.
- [ ] No container/Render publication claim; future secret mounts/image exclusion tracked separately.

### TASK-017: Record scoped acceptance and outstanding release gates

**Description:** Coordinator consolidates final evidence and completion state;
documentation reviewer checks traceability before feature Done.
**Source:** Technical readiness statement; all FR/NFR and TC IDs; repository Done gates.
**Dependencies:** TASK-015 — final reviewed candidate; TASK-016 — reviewed docs/packaging evidence.
**Completed:** No

**Steps**

1. Check candidate/diff remains within scope and record exact executed checks;
   rerun only if changes or unresolved concerns invalidate earlier evidence.
2. Reconcile product/technical acceptance, test outcomes, reviews and documentation.
   A partial local result does not close G-03 or multi-user G-04.
3. Mark selected tasks Yes only with dated supporting logs; feature remains not Done
   while required acceptance is blocked. Record separately authorised future hosting/
   Docker tasks rather than creating credentials, deploying, or expanding this scope.

**Acceptance criteria**

- [ ] Acceptance report links actual source/tests/results/reviews and documents residual limits.
- [ ] Full security and hosted acceptance blocked until G-03/G-04 respectively resolved;
  local completion and feature-level completion are distinguished honestly.
- [ ] Required guide/AGENTS/SDD/log gates satisfied before any feature Done declaration.

## Coverage and handoff

| Source | Tasks |
| --- | --- |
| Technical 1.1/1.2: components, types and constraints | TASK-002, TASK-004–012 |
| Technical 1.3: no persistence, quota and transitions | TASK-005, TASK-009–013 |
| Technical 2.1: service contract and UI threading/lifecycle | TASK-009–012 |
| Technical 2.2/2.3: request, parser, transport/error mapping | TASK-006–009 |
| Technical 3.1/3.2: capabilities, credentials, config | TASK-003/004/006–010/013–016 |
| Technical 3.3: current hosting constraint | G-04, TASK-010/015/017; broader fix separately scoped |
| Technical 4/5.1: edge/failure/recovery paths | TASK-004–013 |
| Technical 5.2: tests, actual verification and release limits | TASK-001–003/013–017 |
| TC-01/02/03 | TASK-006/007/009/011/013 |
| TC-04/07/08/09/12 | TASK-004/006–010/013 |
| TC-05/06/11 | TASK-003/006/013/014/015 |
| TC-10 | TASK-005/008–013 |

Current progress: **11/17 tasks complete** (TASK-001–011, dated evidence above).
Selected TASK-010–011 are complete; TASK-001–009 records were preserved.
Final full verification passed with 1,149 tests across 39 suites, zero
failures/errors/skips, both Checkstyle checks, check and build. Guides and logs
describe the implemented runtime/UI behavior and pending TASK-012 safeguards.
Existing configuration readiness permits offline verification only. G-03/04
still constrain final acceptance. No live calls, deployment or execution of
TASK-012 onward is authorised by this selection.
