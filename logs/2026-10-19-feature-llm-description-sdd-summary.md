# LLM description feature — SDD interaction summary

## Feature and current status

Stable identifier: llm-description; no issue assigned in this task. TASK-001–015
are recorded complete, including runtime/UI integration, stale-result protection,
deterministic boundary checks, two live corpus runs and final independent local
reviews. The user accepted all 26 second-run semantic results; the first run's
failure is preserved. TASK-016 completes final documentation; TASK-017 scoped acceptance recording
remains pending: 16/17 plan tasks recorded complete on 2026-10-20. See the [acceptance report](../docs/developerGuide/LlmAcceptance.md)
for their final status and evidence. **Feature not Done:** provider tool assurance
(G-03) and independent hosted-client session isolation (G-04) remain open.

## Specification baseline and evolution

[Product specification](../workflow/LLM_FEATURE_PRODUCT_SPEC.md), 2026-10-19:
FR-001–006, NFR-001–007 and TC-01–12.
[Technical specification](../workflow/LLM_FEATURE_TECHNICAL_SPEC.md) drafted on
2026-10-19. [Implementation plan](../workflow/LLM_FEATURE_PLAN.md) created 2026-10-20:
17 tasks initially incomplete; subsequent dated task evidence is in the plan.
The specifications retain their original design-stage wording; current implemented
behaviour and verification are recorded in the guides and acceptance report.

## Decisions and interactions

| Date | Question | Decision / evidence | Scope |
| --- | --- | --- | --- |
| 2026-10-19 | Requested feature | User requested title/condition-based generation over HTTPS with bearer token and model/input JSON; strong injection resistance, no tools, security tests. | Specification |
| 2026-10-19 | Existing description | User answered “Replace immediately”. | FR-005 |
| 2026-10-19 | Provider details | User keeps details private and supplies endpoint/token later; execute tests only after insertion. | FR-003, NFR-006 |
| 2026-10-19 | Description length | User sets hard maximum 100 words and normal target 50–70 words. OQ-01 resolved; OQ-02–04 await answers. | FR-004, NFR-005, TC-03 |
| 2026-10-19 | Response format | User supplies completed JSON response with equivalent top-level and nested assistant text. Format recorded; canonical-field/fallback proposal and no-tool capability await confirmation. | FR-003, OQ-02, TC-12 |
| 2026-10-19 | Deployment and extraction | User confirms JavaFX/JPro, later Docker/Render; private root `.env`; top-level `output_text` always present. Response mapping and target resolved. | OQ-02–03 |
| 2026-10-19 | Tools and limits | User cannot confirm provider tool disabling, requests no-tool prompt and authorises limit proposals. App tools remain prohibited; provider non-use is unverified. Numerical limits proposed, not approved. | NFR-003, OQ-04 |
| 2026-10-19 | Limit approval | User: “Yes I approve of these limits”. All proposed OQ-04 values now approved requirements; TC-10 updated. | NFR-005, TC-10 |
| 2026-10-19 | Quota deployment | During technical specification, user selects one server and rate limits resetting on restart. Product NFR-005 updated; in-memory shared process quota designed. | NFR-005, technical spec section 1 |
| 2026-10-19 | Security assurance | Draft translates security intent into no-secret-context/no-tool boundaries plus adversarial evaluation; does not promise proof of universal model immunity. Agent-authored requirements await review. | NFR-001–003 |
| 2026-10-20 | Implementation planning | User requests plan from technical spec. Plan preserves TDD, deferred tests and unresolved acceptance gates; planning only, no execution approval inferred. | TASK-001–017 |
| 2026-10-20 | Bounded execution and readiness | User explicitly selects TASK-001–004 of the LLM plan and confirms endpoint/key readiness. Offline verification allowed; live calls remain separately gated. | TASK-001–004 |
| 2026-10-20 | Next bounded selection | User selects TASK-005 and TASK-006 using implement-task-short. Completed prerequisites preserved; quota precedes request encoding. No live permission inferred. | TASK-005–006 |
| 2026-10-20 | Response and transport selection | User selects TASK-007 and TASK-008 using implement-task-short. Verified response validation precedes transport. Completed prerequisites preserved; offline readiness persists. | TASK-007–008 |
| 2026-10-20 | Service selection and resume | User selects TASK-009, then requests finishing after interruption. Session-bound orchestration and actual-dispatch quota correction stay within the task; runtime wiring remains later. | TASK-009 |
| 2026-10-20 | Configuration names | User confirms SOCLAAS_BASE_URL, SOCLAAS_API_KEY and SOCLAAS_MODEL, superseding earlier names in current guidance/tests. BASE_URL remains the exact full HTTPS URI. | Configuration/setup |

## Plan and execution

2026-10-20 execution update: user selected TASK-001–004 of
`workflow/LLM_FEATURE_PLAN.md` and explicitly confirmed endpoint/API key readiness.
This supersedes the earlier no-selection/test-deferral state for offline checks.
No live-call authorisation was given. TASK-001 is complete: Java 25.0.2, Wrapper
9.1.0, baseline 688 tests and both Checkstyle tasks passed. See
[readiness log](2026-10-20-llm-task-001-readiness.md). Coordinator owns shared
records, testing owners prepare seams/corpus/config tests, application owner writes
configuration after red evidence, and an independent reviewer checks the result.
TASK-002–004 are now complete. See
[infrastructure log](2026-10-20-task-002-llm-test-infrastructure.md),
[corpus log](2026-10-20-task-003-llm-security-corpus.md),
[configuration red evidence](2026-10-20-llm-task-004-configuration-red.md),
[configuration implementation](2026-10-20-llm-task004-configuration-implementation.md)
and [integration/review evidence](2026-10-20-llm-task-001-004-integration.md).
The next selected batch completed TASK-005–006 with testing/application owners
and independent read-only review. See
[quota red](2026-10-20-llm-task005-quota-red.md),
[quota implementation](2026-10-20-llm-task005-quota-implementation.md),
[request red](2026-10-20-llm-task006-request-red.md),
[request implementation](2026-10-20-llm-task006-request-implementation.md) and
[batch integration/review](2026-10-20-llm-task005-006-integration.md).
The selected TASK-007–008 batch added strict response validation and bounded
asynchronous HTTPS transport after verified red/green handoffs. See
[response red](2026-10-20-llm-task007-response-red.md),
[response implementation](2026-10-20-llm-task007-response-implementation.md),
[transport red](2026-10-20-llm-task008-transport-red.md),
[transport implementation](2026-10-20-llm-task008-transport-implementation.md) and
[integration/review evidence](2026-10-20-llm-task007-008-integration.md).
TASK-009 completed after resume with service/session revisions, asynchronous
prepared requests and actual HTTP-start quota marking. See
[service red](2026-10-20-llm-task009-service-red.md),
[preparation red](2026-10-20-llm-task009-preparation-red.md),
[implementation](2026-10-20-llm-task009-service-implementation.md),
[integration/review](2026-10-20-llm-task009-integration.md) and
[resume record](2026-10-20-llm-task009-resume.md).
The preceding paragraphs record the historical TASK-001–009 selections. Later
selections completed TASK-010–015; the current user selection is TASK-016/017.
The plan's current summary governs task counts, while parent-feature acceptance
remains blocked by G-03/G-04.

Only specification/documentation authorised by the original task. Follow-up
reference to implementing configurable code is recorded as future implementation
intent within that task; no production code written. The later explicit technical
specification request produced the proposed design; see
[technical-spec log](2026-10-19-llm-technical-spec.md). Read-only delegated inspection
covered current runtime/session/UI lifecycle. See original
[task log](2026-10-19-llm-product-spec.md).
The [planning log](2026-10-20-llm-implementation-plan.md) records the later authorised
planning task and read-only delegated review. At planning time progress was 0/17;
no implementation tasks had been selected. Task-local owners/TDD and coordinator-only
status updates are defined in the plan.

## Verification and review

The TASK-001–009 results below are historical, not the current implementation
status. Later evidence is consolidated in the acceptance report: TASK-010/011
full test/check/build passed 1,149 tests; TASK-012/013 used focused checks under
the user's full-regression deferral; TASK-014 v2 passed 26 automated cases and
26 user-reviewed semantic decisions after the approved prompt correction.
TASK-015 reviewed source candidate `81f71ee578e91a62a7e30be74ecd4d4ecd3ddd9e`
and passed 586 focused offline cases across 15 suites. These overlapping runs
must not be added into a full-suite total. TASK-016/017 change documentation
only; new packaging/manual JPro checks were not performed.

Specification-stage documentation was reviewed for sections, requirement references,
links and whitespace without executing runtime tests. During TASK-001–004,
coordinator final `.\gradlew.bat check build shadowJar` passed: 767 tests,
zero failures/errors/skips, both Checkstyle tasks passed. Configuration and local
HTTPS/test infrastructure are verified; live semantic cases remain unrun.
Independent selected-task code/coverage/security-boundary review found no unresolved
material findings after regression-backed quote/header validation fixes. No parent
feature acceptance or end-to-end security review is claimed. The subsequent
TASK-005–006 batch passed 28 quota and 23 request tests; related focused checks
passed 130 tests. Coordinator final `.\gradlew.bat check build` passed 818 tests
with zero failures/errors/skips and both Checkstyle checks. Read-only reviewer
confirmed no unresolved material findings after the staggered retry regression
and fix. Local JAR inspection found no .env or test keystore/corpus entries;
container/Render checks remain future.
TASK-007–008 coordinator final
`.\gradlew.bat test checkstyleMain checkstyleTest check build` passed in 4m46s:
1,014 tests across 35 suites, zero failures/errors/skips, both Checkstyle checks,
JAR and distribution tasks. This includes 127 strict response and 69 transport
cases. Independent read-only selected-task review found no outstanding material
issues. Local JAR entry inspection found no .env or synthetic test credentials/
corpus entries. These are local structural/transport checks, not live semantic
or provider-tool acceptance; runtime/UI wiring remains unimplemented.
TASK-009 final test/style/check/build passed in 4m58s: 1,106 tests across 37
suites, zero failures/errors/skips, both Checkstyle checks and packaging. Includes
61 service, 22 preparation and 37 quota cases. Final independent review has no
outstanding material findings; no live provider calls or real configuration reads.
Technical design review includes the delegated current-code findings: one identity
per runtime, exclusive data lock, incomplete universal page-leaving hooks and busy
page navigation restrictions. Design specifies local generation feedback and
session/form/window guards and retains multi-client hosting as a limitation.
Read-only plan review found no material issues in dependencies/TDD/test deferral or
lifecycle/security coverage. Document structure/dependency/link checks supplement
that review; none is runtime or feature acceptance evidence.

## Corrections and approval history

TASK-009 review found admission-time charging could weaken actual rolling
dispatch limits after executor delay. Ten quota/service regression cases failed
before correction; preparation callback tests also recorded four expected failures.
Matching idempotent actual-start marking resolved the finding. During correction,
an existing expiry test caught retained expired state after release; production
cleanup was fixed without changing the test. Both defects are covered by passing
final checks and independent review. No quota policy values changed.

User replies incorporated above. During TASK-004, inspection found unmatched
interior quote and Java header character boundaries. New tests failed before
the fixes, then passed; scope and specified behavior did not change. During
TASK-005 review, staggered attempt timestamps exposed retry advice using the
newest rather than oldest minute expiry. Two new regressions failed before the
correction; all 28 quota tests then passed. Open
question OQ-02 response mapping and OQ-03 are resolved by the
[deployment/limits update](2026-10-19-llm-deployment-limits-proposal.md).
Provider-side tool non-use remains unverified; OQ-04 is resolved by the
[limits approval](2026-10-19-llm-limits-approval.md). Earlier proposal entries above
are historical and superseded by this approval.
Original response shape: [response-format update](2026-10-19-llm-response-format-decision.md).
During TASK-008 review, a suspected concatenated-gzip issue based on an older JDK
was withdrawn after five passing regression cases and actual Java 25 source
inspection. It was not a reproduced implementation defect and caused no
production correction or custom decoder. The reviewer recorded a limitation
for pathological injected HTTP clients blocking inside sendAsync; no such
failure was demonstrated with the production asynchronous client.
OQ-01 length decision recorded in the
[follow-up log](2026-10-19-llm-description-length-decision.md). No silence treated
as approval. Word-counting mechanics remain a technical-design detail.

## Documentation and completion

[User Guide](../docs/UserGuide.md#generating-a-listing-description),
[listing instructions](../docs/userGuide/SearchAndListings.md),
[Developer Guide](../docs/DeveloperGuide.md#llm-description-preparation-and-specification),
[AGENTS.md](../AGENTS.md), this summary and per-task logs now describe actual
local behaviour and limits. TASK-016/017 records are linked from the acceptance
report; [independent final documentation review](2026-10-20-task016-017-documentation-review.md)
found no material issue. Its minor deadline wording clarification was applied.
The removed general interaction record is not restored; feature decisions and
approvals remain linked here and in task logs.

Later execution and approval evidence:

- [Runtime/UI integration](2026-10-20-llm-task010-011-integration.md) and
  [stale UI guards](2026-10-20-llm-task012-integration.md).
- [Integrated boundaries](2026-10-20-llm-task013-integration.md) and
  [TC-01–12 trace](../docs/developerGuide/LlmSecurityCoverage.md).
- [Live correction approval](2026-10-20-task014-prompt-correction-plan.md),
  [second run](2026-10-20-task014-prompt-rerun.md) and
  [user semantic acceptance](2026-10-20-task014-completion.md).
- [Independent final reviews](2026-10-20-task015-independent-reviews.md) and
  [local scope/G-03 decision](2026-10-20-task015-final-reviews.md).

TASK-014 correction history remains two ordinary cycles and one explicitly
approved exceptional prompt cycle. Documentation reconciliation authorizes no
further model correction or live rerun. No runtime settings/private outputs were
read for TASK-016/017, and no deployment or release artifact was produced.
