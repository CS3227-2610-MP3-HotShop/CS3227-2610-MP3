# TASK-015 independent review reports

The coordinator saved these attributed summaries of three separate read-only
reviewer handoffs. Candidate: `81f71ee578e91a62a7e30be74ecd4d4ecd3ddd9e`,
branch `llm-feature`, 2026-10-20. Scope: TASK-015 only, using implement-task-short;
product/technical requirements dated 2026-10-19 with the approved TASK-014 prompt
amendment and common agent contract v0.9. Source/test/build paths matched HEAD.
Unrelated working-tree changes were preserved.

All reviewers inspected actual source and test assertions. Their executed checks
were read-only PowerShell file inspection, rg searches and Git identity/diff/status
commands, exit 0. None edited files, executed Gradle/tests, accessed real .env,
private live evidence or JPro logs, or contacted a provider. Prior test/live counts
were attributed to public logs; fresh coordinator execution is recorded separately
in the coordination log. Timing/usage unavailable unless noted below.

## Implementation and concurrency reviewer

Agent: `/root/task015_code_review`, code_reviewer.
Conclusion: no substantial actionable defect demonstrated within inspected scope;
scoped static assessment, not blanket security acceptance.

| Area | Inspected execution path and tests | Assessment |
| --- | --- | --- |
| Worker authorization | DescriptionService captures account/revision on ServiceWorker, rechecks before admission and delivery; logout, account switch and same-account relogin tests | Satisfied by inspection |
| Draft/lifecycle safety | ListingEditor field/request revisions; MarketplaceUi page, auth and disposal guards; accepted logout invalidates immediately | Satisfied by inspection |
| Nested discard dialog | MarketplaceUi defers completions until the dialog caller handles its decision; accepted/cancelled close, logout and relogin queue tests | Satisfied by inspection |
| Responsiveness and save | Generation has independent progress/button state, avoids whole-page UiPage.perform, remains dirty/unsaved; manual-save-while-pending tests | Satisfied by inspection |
| Admission ownership | DescriptionQuota atomic account reservations and actual-dispatch timestamp; DescriptionService matching release/rollback and retained dispatched charges | Satisfied by inspection |
| Transport terminal state | HttpLlmClient deadline includes dispatch/body/parse; terminal claim and cleanup precede observable completion; bounded subscriber cancellation wakes decoder | Satisfied by inspection |
| Runtime close | ApplicationRuntime drains and releases lock on a non-daemon shutdown thread; one borrowed runtime cannot clear server quota/close shared transport | Satisfied by inspection |
| Independent hosted identity | Existing authenticated runtime/data lock does not establish distinct concurrent browser identities | Unverified, G-04 open |
| Provider tools | No local dispatcher; remote non-use cannot be inferred | Unverified, G-03 open |

Inspected ApplicationRuntime, DescriptionRuntime, Main, DescriptionService,
DescriptionQuota, AuthenticatedSession, ServiceWorker, all five llm production
classes, ListingEditor, MarketplaceUi and UiPage. Reviewed service/quota/runtime,
generation UI, HTTPS/preparation and integration test assertions. Reference paths
include DescriptionService begin/admit/guardFailure, ListingEditor generation
completion, MarketplaceUi discard deferral and ApplicationRuntime closeAsync.
Real JPro/browser/classloader lifecycle and live provider semantics were excluded.
Deterministic deadlines are not measured production-network timing. Finish:
2026-10-20 08:16:30 UTC; start and usage N/A. No corrective authorization requested.

## Security reviewer

Agent: `/root/task015_security_review`, hotshop_security_reviewer.
Conclusion: no confirmed security defect found in inspected local boundaries.

| Checklist | Status and execution-path evidence |
| --- | --- |
| Authentication/session checks | Checked: DescriptionService captures worker-owned identity/revision and guards before admission and delivery; tests cover logout/relogin/switching |
| Untrusted authority | Checked: trusted configured model plus fixed instruction and JSON title/condition only; no repository, file executor, browser or tool dispatcher in generation graph |
| Validation/inert output | Checked: strict UTF-8/JSON, duplicate/depth/size limits, top-level authoritative text, completed assistant messages, structured tool/refusal rejection; JavaFX TextArea output |
| Secrets/diagnostics | Checked: package-private setting access, redacted configuration, Authorization header only, fixed warning codes with no attached causes; nonempty synthetic diagnostic assertions |
| Transport/resource bounds | Checked: no redirects/retries, connect/request deadlines, request and separate wire/decoded byte bounds; TLS, gzip, stall/cancel and boundary assertions |
| Quota/lifecycle | Checked: synchronized reservation ownership, rolling windows, actual-send timestamps, retained failure charge, stale completion protection and server-shared history |
| Persistence/human save | Checked: generation lacks listing mutation dependency; storage snapshot/callback tests; explicit Save remains separate, stale UI guards protect drafts |
| Live semantic behavior | Not independently verified by this reviewer: private evidence deliberately excluded; TASK-014 user acceptance is recorded public evidence, not universal resistance |
| Provider tool non-use | Unverified: user reports no independent evidence; G-03 open |
| Concurrent hosted identities | Unverified/excluded: G-04 open; no hosted multi-user safety claim |
| Container/release secret exclusion | Not applicable to local review; later packaging/deployment task |

Inspected all LLM production classes, service/quota/session, runtime/startup,
ListingEditor/MarketplaceUi, Gradle live-test separation and corresponding tests.
No runtime reproduction was executed. Review completion does not grant full
end-to-end no-tool or hosted acceptance. Finish: 2026-10-20 16:15:47 SGT;
start/elapsed/usage N/A. Existing correction history was not reset.

## Specification and coverage reviewer

Agent: `/root/task015_coverage_review`, hotshop_specification_reviewer.
Conclusion: no new material local implementation or test defect found.

| Requirement/cases | Evidence and assessment |
| --- | --- |
| TASK-015 criteria | Stable direct implementation/coverage assessment performed; no important new local defect; G-03 and independent hosted safety explicitly unverified |
| NFR-001, TC-05/06/11 | Fixed instruction/untrusted JSON boundary, benign technical vocabulary, canary/integration assertions, unchanged 26-case corpus/human rubric; reported accepted v2 samples only |
| NFR-002, TC-04/07/09 | Redacted settings, trusted exact destination, redirects disabled, synthetic payload/storage/log canaries and safe transport failures; packaging not assessed |
| NFR-003, TC-08 | No local tool dependencies, full response-envelope rejection, inert script/URL assertions; provider non-use remains unverified |
| NFR-004 | External-processing/replacement/review notice, typed UI feedback and fixed diagnostic without attached throwable |
| NFR-005, TC-02/03/10 | Title/word/code-point/byte bounds, shared quota/dispatch ownership, deadlines, JavaFX edit/revert/detach/save/logout/relogin/close gates |
| NFR-006 | Ordinary/live tag separation, synthetic fixtures, live transport failures cannot count as semantic resistance, mandatory human review |
| NFR-007, TC-01/12 | Existing asynchronous editor/session patterns and explicit persistence, minimum title/condition, canonical top-level output/no nested fallback/single insertion |

The existing semanticallyQuestionablePlainText and plainScriptAndUrl tests
deliberately establish structural validation/inert handling, not universal model
obedience. This matches the approved design's live empirical evaluation boundary.
The reviewer inspected existing parser XML (127 tests, zero failures/errors/skips,
07:38:37 UTC), but did not execute any tests or treat this as a fresh broad run.

Documentation debt to carry to TASK-016: DeveloperGuide's old preparation section
still says generation is unavailable (line 303 at this candidate) and that live
semantics are unverified (lines 333-334), contrary to later current sections and
TASK-014 acceptance. Final guide reconciliation remains pending, not a request
to reopen completed implementation entries. No application/test correction needed.
No final feature-documentation, release or full regression/build acceptance.
Finish approximately 16:18 SGT; start/usage N/A. Final coordinator-authored review
records require a subsequent public-record accuracy check.
