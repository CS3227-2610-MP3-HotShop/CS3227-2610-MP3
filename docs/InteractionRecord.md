# Project decisions and interactions

## 2026-10-20 — Commit TASK-007–009 separately

User requests exactly three commits on the current llm-feature branch, aligned
with completed tasks. Split shared HttpLlmClient changes by task; include parser
tests/logs in TASK-007, HTTP streaming fixtures/tests/logs in TASK-008 and service,
preparation, actual-dispatch quota and approved SOCLAAS naming in TASK-009.
Shared completion records accompany TASK-009. Separate agent-rule removals and
untracked workflow skills stay uncommitted. No push or deployment authorised.
See [commit grouping](../logs/2026-10-20-llm-task007-009-commit-grouping.md).
Verification: exported TASK-007 snapshot passed 150 response/request cases and
both styles; TASK-008 passed all 298 offline LLM cases and both styles. Final
check/build succeeded using up-to-date outputs. Task commits begin with
064752e (TASK-007) and ff3ba6b (TASK-008); TASK-009 contains remaining service
work and shared completion records. Separate AGENTS.md rule removals remain
unstaged; no application source changes remain outside the three-commit scope.

## 2026-10-20 — TASK-009 completed after resume

Completed session-bound DescriptionService, login/logout revisions, asynchronous
prepared requests and shared quota orchestration. Review found actual HTTP start
could lag admission timestamps; failing regressions preceded the matching,
idempotent before-send timestamp correction. One existing expiry-cleanup test
caught a regression during correction; production cleanup was fixed and the
valid test retained. Final independent review has no outstanding material findings.

Final `.\gradlew.bat test checkstyleMain checkstyleTest check build` passed in
4m58s: 1,106 tests across 37 suites, zero failures/errors/skips, both Checkstyle
checks and packaging passed. Includes 61 service, 22 preparation and 37 quota
cases. No real configuration inspected or live provider calls made. Current
SOCLAAS configuration names remain as explicitly approved. See
[integration evidence](../logs/2026-10-20-llm-task009-integration.md).

Current handoff: branch llm-feature; TASK-001–009 completed, TASK-010–017
incomplete/unselected. Existing uncommitted TASK-007–008 work, unrelated skill
directories and separate AGENTS.md edits preserved. No new commits, pushes or
deployment. Runtime/UI wiring, hosted identity, provider tool assurance and live
semantic acceptance remain pending; further implementation needs new selection.

## 2026-10-20 — Resume unfinished TASK-009

User requests finishing TASK-009 after the session stopped. Existing service,
preparation and lifecycle green evidence is preserved. Independent review found
one unresolved quota integration issue: admission timestamps could precede actual
HTTP dispatch after executor delay. Resume with regression-first correction using
a trusted before-send callback and matching, idempotent quota timestamp update.
This implements the already-approved rolling dispatch limits, without changing
their values or reopening completed prerequisite tasks. TASK-010 remains excluded.
Coordinator resumed testing, implementation and independent review owners; no
live-call, commit or deployment permission is inferred.

## 2026-10-20 — LLM TASK-009 selected and configuration names confirmed

User selects TASK-009 using implement-task-short; the intended plan remains
workflow/LLM_FEATURE_PLAN.md from the active LLM implementation conversation.
All direct/transitive prerequisites TASK-001–008 have completion evidence.
Scope: session-bound description orchestration and session revision, with the
minimal preparation/dispatch transport seams needed for quota correctness.
TASK-010 runtime wiring and later UI tasks remain unselected.

Inspection found an existing uncommitted configuration-key rename. User explicitly
confirmed: keep SOCLAAS_BASE_URL, SOCLAAS_API_KEY and SOCLAAS_MODEL and align
synthetic tests/current documentation. This supersedes HOTSHOP_LLM_* names in
current setup guidance; historical task logs remain historical. BASE_URL still
means the exact full configured HTTPS request URI; no path is appended. No real
.env contents were read. Existing TASK-007–008 and unrelated skill edits preserved.

Coordinator owns records/key-name alignment; testing specialist owns service
tests and red evidence; application specialist owns production after red;
independent reviewer assesses the stable candidate. Offline readiness persists;
no live calls, commits or deployment inferred. Branch: llm-feature.

## 2026-10-20 — LLM TASK-007–008 completed

Implemented strict full-envelope response validation and bounded asynchronous
HTTPS transport after verified behavioural red handoffs, then focused green
checks and independent review. Response tests cover canonical text, strict
UTF-8/JSON, complete output structures and Unicode limits. Transport tests cover
exact destination/headers, TLS, status mappings, wire/decoded limits, gzip,
deadlines, backpressure, cancellation and late resources. A suspected older-JDK
gzip issue was withdrawn after passing regressions and Java 25 source inspection;
no production correction or extra decoder was introduced.

Final `.\gradlew.bat test checkstyleMain checkstyleTest check build` passed in
4m46s: 1,014 tests across 35 suites, zero failures/errors/skips, both Checkstyle
checks, JAR and distribution tasks passed. Local JAR entry inspection found the
new transport classes and no .env or synthetic keystore/corpus entries.
Independent review has no outstanding demonstrated material findings. See
[integration/review evidence](../logs/2026-10-20-llm-task007-008-integration.md).

Current handoff: workspace unchanged, branch llm-feature; TASK-001–008 completed,
TASK-009–017 remain incomplete and unselected. No live calls, real configuration
reads, runtime/UI integration, new commits, pushes or deployment. Unrelated
untracked short-workflow skills preserved. Provider-tool, live semantic and
hosted identity gates remain open. Next implementation requires a new selection.

## 2026-10-20 — LLM TASK-007–008 selected

User selects TASK-007 and TASK-008 in workflow/LLM_FEATURE_PLAN.md using
implement-task-short. All transitive prerequisites TASK-001–006 are completed
with dated evidence; preserve those entries. TASK-007 response validation must
pass before TASK-008 transport implementation. Offline readiness persists;
no live-call permission or real configuration inspection is inferred.

Coordinator owns shared records and final verification. Testing specialist owns
specification-derived tests and behavioural red evidence; application specialist
owns production changes after red; independent review covers the stable candidate.
One writer at a time owns HttpLlmClient. Scope excludes service/runtime/UI work
in TASK-009 onward, provider assurance, deployment and commits. Existing untracked
short-workflow skill directories remain untouched. Branch: llm-feature.

## 2026-10-20 — Commit completed LLM tasks

User requests committing the completed TASK-001–006 work on the current branch,
using task boundaries to group changes. Approved scope: local commits on
`llm-feature`; no push or further implementation. Group readiness, infrastructure,
security corpus, configuration, quota and request encoding separately, followed
by shared completion documentation and integration records. Preserve the four
untracked short-workflow skill directories outside these commits.

Verification: `.\gradlew.bat check build` succeeded with all 15 actionable tasks
up-to-date; existing test/build outputs were reused. `git diff --check` passed.
Handoff: TASK-001–006 complete; generation and TASK-007 onward remain pending.
Provider tools, live evaluation and hosted identity gates remain open. Commit
details and final repository checks are recorded in
[the grouping log](../logs/2026-10-20-llm-task-commit-grouping.md).

## 2026-10-20 — LLM TASK-005–006 completed

Implemented account quota admission and fixed-prompt request encoding using
delegated test-first handoffs and independent read-only reviews. The quota tests
cover races, expiry, reservation ownership, rollback, cleanup and signed/wrapped
monotonic clocks. Review found overstated retry delays for staggered attempts;
two failing regressions preceded the correction. Request tests cover both JSON
layers, permitted fields, canonical conditions, code-point and UTF-8 byte limits,
safe failures and private configuration isolation. No API scope or product
decision was expanded; TASK-007 onward remains unselected.

Handoff: branch llm-feature; TASK-001–006 Completed Yes, TASK-007–017 remain No.
Final `.\gradlew.bat check build` passed: 818 tests, zero failures/errors/skips,
both Checkstyle checks and JAR/distribution tasks passed. Initial sandbox cache
lock failure was resolved by retrying with the approved wrapper permission.
Local JAR inspection found no .env or test keystore/corpus entries. Independent
review has no unresolved material findings. Source/tests/docs/logs updated;
existing edits preserved, no commits/pushes/deployment. No real private settings
were read and no live calls made. Actual transport, shared server wiring,
runtime/UI, semantic evaluation and provider-tool/hosted-identity assurance remain
pending. Next implementation needs new selected scope. See
[integration and review log](../logs/2026-10-20-llm-task005-006-integration.md).

## 2026-10-20 — LLM TASK-005–006 selected

User requests implement-task-short for TASK-005 and TASK-006 in the LLM plan.
Prerequisites TASK-001–004 retain completed evidence; TASK-005 precedes TASK-006,
and no unfinished prerequisite lies outside selection. The stale 0/17 plan footer
is reconciled to its four completed entries without reopening them. Scope is
quota admission and request encoding only; offline readiness persists, with no
permission for live calls or real-secret inspection. Testing and application
owners use sequential red/green handoffs; coordinator owns records/integration
and independent review. TASK-007 onward remains outside this selection.

## 2026-10-20 — LLM TASK-001–004 completed

Executed only the selected plan tasks, with delegated testing, application
implementation and independent read-only review. TASK-001 records readiness and
688-test baseline; TASK-002 adds Gson 2.14.0, typed transport seam and isolated
fixtures; TASK-003 adds the unrun 26-case live corpus/rubric; TASK-004 adds private
immutable configuration with verified red/green evidence. Review-found quote and
HTTP header character boundaries received failing regressions before correction.
Final review has no unresolved material findings. No live calls or real settings
reads occurred. No broader implementation or release acceptance is implied.

Handoff: branch llm-feature; TASK-001–004 Completed Yes, TASK-005–017 remain No.
Final `.\gradlew.bat check build shadowJar` passed, 767 tests with zero
failures/errors/skips; both Checkstyle checks passed. Local JAR excludes .env and
test fixtures. Existing workspace edits preserved; no commit/push/deployment.
Generation/runtime/UI, live semantics, provider-tool assurance and hosted session
isolation remain pending. Next implementation needs a new selected scope; there
are no incomplete selected tasks. See
[integration and review evidence](../logs/2026-10-20-llm-task-001-004-integration.md).

## 2026-10-20 — LLM plan selection and configuration readiness confirmed

The user confirmed `workflow/LLM_FEATURE_PLAN.md` as the intended plan and stated
that endpoints and API key are ready. This resolves the preceding selection
blocker and the deferred offline-test readiness condition. Scope is TASK-001–004
only, using the plan's specified design contracts; no live-call permission is
inferred. No real configuration values were inspected. Provider-tool assurance
and independent JPro client identity remain unresolved release gates.

Coordinator owns records/integration; testing specialists own infrastructure,
prewritten live corpus and configuration tests; application specialist implements
configuration after behavioural red evidence; reviewer checks the final candidate.
All selected prerequisites are within this batch. Further execution evidence and
the final handoff will be recorded here and in task logs.

## 2026-10-20 — TASK-001 through TASK-004 selection blocked

The user authorised TASK-001 through TASK-004 using implement-task-short, with
delegation where appropriate. Its default `workflow/PLAN.md` is absent. The
candidate `workflow/LLM_FEATURE_PLAN.md` requires explicit selection clarification;
its existing configuration-readiness restriction still applies to all test runs.
No implementation, tests, plan completion or delegation occurred. No previous
decision is superseded. See logs/2026-10-20-task-001-004-plan-selection-blocked.md.

Handoff: branch llm-feature; existing workspace edits preserved. Only this request
record and its log were added. TASK-001 through TASK-004 remain unverified and
unexecuted. Next authorised step is to confirm the intended plan and, if it is the
LLM plan, obtain configuration readiness before gated execution. Do not inspect
secret values to infer readiness.

## Current development agreement

- MP3 is the migration source; verify all existing features against guides, code, and tests.
- Approve the complete feature inventory before detailed feature specifications.
- Target TypeScript/Next.js on a migration branch, with resettable demo data initially and PostgreSQL later.
- Frontend/backend owners approve area-local corrections; both approve shared interfaces. Assign owners before implementation.
- The coordinator delegates only. Testing owns tests; implementation owns production code.
- Review test readiness, then implementation and coverage, then final-candidate security.
- Failed-loop edits require an approved change plan. Two correction cycles are shared across the same task/batch.
- Use focused unit/integration coverage and Playwright for critical integrated journeys.
- Link issues, approved specs, tasks, acceptance evidence, and PRs.
- Update repository instructions, Developer Guide, and project records when completing changes.

## 2026-10-17 — Documentation organisation

Organised reusable agent guidance under [docs/agents](agents/AgentSpecifications.md).
Updated configuration and skill references. Repository records retain project decisions and outcomes.
Runtime discovery and the disposable workflow exercise remain pending.
No application changes or published-history changes were made.

## Pending decisions and work

- Native discovery/launch passed in the supplied report; see the exercise report for scoped generic-dispatch evidence and remaining unverified paths.
- Issue/PR conventions and templates are written; GitHub board/label setup remains pending.
- Freeze the source baseline; verify and review the full inventory.
- Assign owners and establish demo-state design and target build/test commands.
- Review and commit local workflow changes as authorised; publishing is a separate next step.

## 2026-10-17 — Decision recording and validation sequence

Agreed to update this record after every decision, including discussion-only turns, and maintain an actionable handoff. This prevents new chats from depending on unsaved conversation context.

Next is a read-only custom-agent smoke test before issue #6. Confirm actual configured-type discovery and launch; generic agents with copied instructions do not establish TOML discovery. The suggested issue structure (feature parents with frontend/backend tasks where useful) remains a proposal until agreed.

## Current handoff

### Historical LLM specification and planning handoff — before TASK-001 execution

- Workspace: C:/Users/Yeo Jian Ming/y3/cs3227/CS3227-2610-MP3; observed branch:
  `llm-feature`. Older migration handoffs below are historical context.
- Completed: requested draft `workflow/LLM_FEATURE_PRODUCT_SPEC.md`, with six
  sections, observable criteria and security test specifications; supporting guide,
  agent instructions and feature/task records updated.
- User decisions: successful generation replaces existing description immediately;
  provider details remain private and will be inserted through configuration;
  tests run only after the user inserts endpoint and bearer token.
- Status: specification only. No application code, executable tests or live LLM
  requests. No implementation or security acceptance claimed.
- Resolved OQ-01: maximum 100 generated words, normally 50–70; target is not a
  minimum. Updated acceptance and word-boundary test definitions; manual limits
  unchanged. See [follow-up log](../logs/2026-10-19-llm-description-length-decision.md).
- OQ-02 partially resolved: user supplied response JSON with `status: completed`,
  top-level `output_text`, equivalent nested assistant text and usage metadata.
  Example recorded under FR-003; parsing cases added as TC-12. Example model is
  not a configured-model decision. User now confirms top-level `output_text` is
  always present: authoritative extraction, no nested fallback. Provider tools
  cannot be confirmed disabled; user requests a no-tool prompt. App-side execution
  remains prohibited; provider-side non-use is unverified, not guaranteed by prompt.
- OQ-03 resolved: current JavaFX/JPro, later Docker/Render; untracked root `.env`
  holds credentials on the trusted Java side. Git ignore/tracking checks confirm
  `.env` is ignored/untracked; contents not read. Packaging/secrets not implemented.
- OQ-04 resolved: user approves 10s connect/45s total, 8 KiB
  request/64 KiB response, one active request/account, 5/minute and 30/hour/account,
  no automatic retry. Recorded as requirements and updated TC-10 boundary cases;
  see [approval log](../logs/2026-10-19-llm-limits-approval.md).
- Verification: documentation structure, local links and whitespace/diff review;
  see the task log for executed checks. Runtime and security tests unverified.
- Technical design requested and drafted in `workflow/LLM_FEATURE_TECHNICAL_SPEC.md`:
  service/transport contracts, strict JSON response handling, session/form revisions,
  private config, quota, failure recovery and planned verification. No runtime edits.
- Design clarification: user approved one server; quota resets on restart. Product
  NFR-005 updated in place. No persistent/shared cross-replica limiter planned.
- Read-only delegated integration inspection found existing single session/runtime
  and exclusive data lock; independent JPro browser users are not established safe.
  This limitation is recorded without an authentication rewrite. See
  [technical-spec log](../logs/2026-10-19-llm-technical-spec.md).
- Technical-spec review complete: added explicit window-disposal guard after
  delegated review; five-section/reference/local-link/whitespace checks passed.
  No runtime or security tests executed.
- Implementation planning requested and completed in `workflow/LLM_FEATURE_PLAN.md`:
  17 ordered incomplete tasks with ownership, dependencies, TDD steps, acceptance,
  commands and source coverage. Read-only delegated review found no material issues.
  No implementation, test execution or credential access occurred. See
  [planning log](../logs/2026-10-20-llm-implementation-plan.md).
- At planning time: 0/17; configuration readiness and selected execution scope were
  unconfirmed. Provider no-tool assurance and JPro multi-user release gates remain open.
- Next authorised scope: await user-selected implementation tasks/scope; retain explicit provider-side
  tool-assurance limitation. See the [deployment/limits log](../logs/2026-10-19-llm-deployment-limits-proposal.md) and
  [response-format log](../logs/2026-10-19-llm-response-format-decision.md).
  Implementation needs a separate task and resolution of affected open decisions.
- Records: [task log](../logs/2026-10-19-llm-product-spec.md) and
  [feature summary](../logs/2026-10-19-feature-llm-description-sdd-summary.md).

### Earlier migration handoff

- Target workspace: E:/CS3227-2610-MP3. The originating chat is attached to MP2; use a fresh MP3 project chat for configuration discovery.
- Working branch: codex/migration-workflow; verify before changing files.
- Written: migration workflow, guide-derived inventory, aligned SDD skills, five development role briefs, four worker TOML configs, and startup guidance.
- Checked: documentation links/whitespace; supplied smoke test reports four custom-type launches. The disposable generic-dispatch exercise observed initial handoffs, failure approval, one actual repair cycle and final reviews; isolated/native loading and unexercised paths remain unverified.
- Current saved role instructions were read by exercise workers through generic dispatch. This does not revalidate native custom-config loading after instruction edits.
- Current task: INV-17 product specification drafted in workflow/PRODUCT-SPEC.md, awaiting user approval. INV-01 to INV-16 remain inventory-only.
- Publication plan: publish the accumulated workflow and inventory work on its own branch with a pull request, then start INV-17 implementation on a separate feature branch.
- Storage: hosted PostgreSQL from the start, no reset feature. Supersedes resettable demo data with deferred PostgreSQL.
- Blocker: the assistant session has no shell on the workspace machine and no valid GitHub credential, so it cannot run Git or gh. Branching, committing, pushing and PR creation are performed by the user; the assistant writes files only. Workflow setup was committed locally as 1d812ef. F1-F4 have documentation corrections; F2 now explicitly distinguishes missing behaviour from independently verified existing behaviour. The covered disposable exercise is now complete; see docs/agents/WorkflowExercise.md for observations and unexercised scenarios. Feature/task/PR templates and stage conventions are written locally; GitHub board/label setup remains pending. Issue #7 exercise results and remaining limits are recorded in docs/agents/WorkflowExercise.md; no remote issue update was performed.
- Coverage of the 17 groups was approved on 2026-10-17 (feature list only).
- Source baseline frozen at 1d812ef on 2026-10-17; re-baselining needs a diff and explicit
  approval.
- Next: discuss detailed specifications one feature at a time, starting with a feature the
  user chooses. Source ambiguities and web adaptations remain explicit.
- INV-17 (platform and data foundations) was added on 2026-10-17 and is not source-traced.
- Agreed 2026-10-17: concurrent multi-user web access replaces the desktop
  single-logged-in-user constraint. Session mechanism, cross-user update visibility and
  concurrency control remain undecided.
- Agreed 2026-10-17: the desktop 960 x 640 minimum window size and fixed layout units
  are not target criteria; the web layout adapts to arbitrary viewport sizes, with
  breakpoints and acceptance viewports still to be specified.
- Tooling limit: the current assistant session has no shell on the workspace machine.
  Git state is read from .git refs and the reflog; no Git command is executed here.
- Pending: human owners, demo-state design, web runtime/test/build commands, source baseline freeze, and feature approvals.
- Workflow setup is committed locally as 1d812ef; current feature-inventory evidence changes remain uncommitted. Nothing was pushed; published history is unchanged.
- References: [startup](agents/AgentConfiguration.md), [role contract](agents/AgentSpecifications.md), [migration workflow](../workflow/MIGRATION-WORKFLOW.md), [inventory](../workflow/FEATURE-INVENTORY.md).

## 2026-10-17 — Smoke-test result received

Accepted the user-supplied report as discovery/launch evidence for all four configured types. This supersedes the pending launch step above; it does not establish isolated instruction loading or exercised failure handling. Full evidence and limitations are in [AgentConfiguration.md](agents/AgentConfiguration.md).

The workspace change was concurrent with this chat's decision-handoff update. The log was created here, not evidence of smoke-worker writes. A globally unchanged workspace cannot be claimed.

Aligned implementer TOML with the existing role brief: production code plus explicitly assigned documentation/evidence, with tests and reviewer reports protected. No application changes, runtime re-test, commits, pushes, or issue updates. Next is issue #6 discussion.

## 2026-10-17 — Feature and task issue structure approved

User: "ya ok this works"

Approved one feature parent issue with separate linked frontend/backend task issues.
Each task has an owner, dependencies, status, acceptance criteria, and linked PRs.
Create an integration issue when substantial; keep small features in one issue when
splitting adds no useful ownership or tracking. Parent completion requires all
required tasks and feature-level acceptance checks. This supersedes the earlier
proposal-only status for issue structure.

Next within issue #6: agree required template fields and status/readiness conventions,
then prepare templates. No new GitHub issues, comments, or PRs were posted.

## 2026-10-17 — Incremental planning approved and feature template drafted

User: "ok lets continue with this first"

Approved progressive task discovery rather than exhaustive upfront issue scoping.
Record known feature behaviour and criteria, unresolved questions and dependencies;
add separate task issues as work becomes concrete. Require sufficiently detailed,
approved requirements for the task about to start. Scope/interface changes retain
existing approval gates; inventory approval and final acceptance are still required.

Created .github/ISSUE_TEMPLATE/feature.md with optional/pending detail at issue creation
and explicit readiness requirements before implementation. No live issues were posted.
Next: review this parent template, then define the frontend/backend task template.
Status conventions and PR template remain pending.

## 2026-10-17 — Task issue template drafted

User: "ok lets move on to that"

Created one shared frontend/backend/integration task template, linked to parent
features and plan IDs. It allows unknowns at creation but records approval, ownership,
contracts and prerequisites before execution. It preserves test ownership, shared
correction limits and separate task versus feature completion.

Updated the workflow and developer/agent documentation. No live issues were posted.
Next: review task template, then settle status conventions and PR template.
## 2026-10-17 — Task stages and hold flags agreed

User: "ok thats fine lets do with this 3 more also"

Recorded the preceding agreement: retain Backlog for unstarted work, with Paused
(deliberate hold) and Blocked (dependency/decision) separate from the five stages:
Backlog, Ready, In progress, Review, Done. Flags include reasons and resumption
conditions and preserve the underlying stage. No additional unnamed stages inferred.

The user previously accepted the task specification-link explanation and moved on.
Updated workflow, task template and developer/agent guidance. Next is the PR template.
No GitHub fields, labels, issues, or PRs were changed.
## 2026-10-17 — SDD summary and documentation completion gates

User requested feature completion to require updated user/developer guides and an
interaction summary similar to MP2, focused on SDD.

Recorded both guide updates, AGENTS.md, and a feature-level SDD interaction summary
as feature Done gates. The summary links approved spec versions, criterion IDs,
decisions/approvals, plan tasks, role handoffs, corrections, actual checks and logs.
Per-task logs and this running record remain; no duplicate transcript is required.

Created the PR template and summary guidance. Intermediate task PRs record remaining
documentation work; they cannot close the parent feature prematurely.
Next: review these templates and agree how to apply tracking in GitHub.
No feature implementation, remote publication, commit or push occurred.

## 2026-10-17 — Documentation ownership assigned

User: "ok lets tag ownership then"

Assigned User Guide and design/setup Developer Guide updates to implementers;
workflow Developer Guide, AGENTS.md, feature SDD summaries and decision/handoff
records to the coordinator. Each agent owns its task log; the coordinator checks
completeness. The specification reviewer independently checks final feature guides,
instructions and SDD traceability, including coordinator-authored documents.

One writer per shared file at a time, with explicit ownership transfer. Read-only
workers return reports for authorised consolidation; no implied write permission.
Updated all five role briefs and four worker configurations. Runtime validation of
these added duties remains pending.

Next: consistency-review the workflow/templates/roles before publication, then
perform the disposable workflow exercise. No application code, remote changes,
commits or pushes in this task.

## 2026-10-17 — Prepublication consistency review

User: "i meant ok can u run the checks"

Completed the static review. Findings: ambiguous plan-status writer, mandatory-red
wording conflicting with already-satisfied baseline cases, missing documentation
closure prompts in the parent issue template, and stale current status pointers.
See [review evidence and proposed corrections](../logs/2026-10-17-workflow-prepublication-review.md).
Only this handoff and the review log changed; policy/configuration fixes remain pending.
File checks passed; this is not behavioural validation.
## 2026-10-17 — Review corrections approved

User approved coordinator-only plan-status writes (F1), requested feature-template
documentation completion fields (F3), and requested current status updates (F4).
Applied these changes across skills, common contract, role/config instructions,
workflow and developer documentation. Historical entries remain historical.

User requested an explanation of F2. It remains open and unchanged: new/missing
behaviour needs meaningful red evidence, while existing satisfied criteria should
not require artificial failures. Await the user's response before changing that wording.
No runtime re-test, commits, pushes or remote changes.
## 2026-10-17 — Explicit baseline-test policy approved

User: "yes that works but be very clear and explicit with the intent"

Approved meaningful red for missing/incorrect behaviour and independently confirmed
passing evidence for already-satisfied behaviour. Recorded evidence requirements,
unexpected-pass investigation, mixed criteria, unverified setup failures and the
JavaFX/Next.js boundary. Artificial failures and green-only completion are excluded.
All existing reviews, approvals and completion gates remain. Updated skills, roles,
testing config and guidance; this supersedes F2's open status above.
Next: review combined changes for publication; full runtime exercise remains pending.
## 2026-10-17 — Disposable workflow exercise completed

User requested running issue7's exercise. A separate copy ran the synthetic cart-total
task with tester, implementer, specification reviewer and final security reviewer.
24 tests showed4 baseline passes/20 initial meaningful failures, then24/24 green.
A labelled fault caused3 failures; implementer stopped. User asked for explanation,
then explicitly said "ok approved". The one-line repair and both independent reviews
passed at correction cycle1/2 with frozen tests.

Prerequisite and shared-interface routing checks behaved as expected. Exhausted
count2 refusal was a scripted scenario, not two actual executed cycles.
Stale plan/SDD counter headers were corrected and independently documented.
All255 captured source files were unchanged before documentation-only reporting back.

Evidence: [exercise report](agents/WorkflowExercise.md).
Disposable workspace:
C:/Users/jonat/.codex/visualizations/2026/10/12/01a12ee5-593a-75e1-9ed7-4c0e34c39577/issue-7-20261017

Next: review combined workflow changes for publication, then return to verifying the
full feature inventory. Native custom dispatch was not revalidated here; worker
instructions were read via generic dispatch. No GitHub issue change, commit or push.
## 2026-10-17 — Review and commit workflow setup

User authorised review and local commit of the accumulated workflow changes. Two independent reviewers assessed standards and agreement with the approved workflow. Stale live validation/status summaries were corrected; historical reports remain unchanged. The commit includes roles/configuration, skills, issue/PR templates, migration guidance and exercise evidence. Application code is unchanged. No push or merge is authorised by this task.

Current next step after the local commit: verify the full feature inventory against code/tests and obtain approval before detailed specifications. Publishing the branch and updating remote issue status remain separate actions.

## 2026-10-17 — Feature inventory evidence pass

User requested working on the feature list. Three read-only workers traced all 16 existing groups to source and representative test assertions; coordinator consolidated the reports in workflow/FEATURE-EVIDENCE.md and updated the inventory. Baseline is 1d812ef270d780d248c74f7a37c543345df70026, with unchanged application source versus the earlier reference. No whole feature group was added or dropped. Current coverage is proposed for user approval, not approved by this inspection.

Recorded direct listing-detail access, UI-only meetup constraints and stale architecture chat-creation wording for explicit specification decisions. No tests, manual UI checks, feature implementation, issue creation, commit or push occurred in this task. Next: review coverage, then detailed specifications one feature at a time.

## 2026-10-17 — Handoff to Claude

User requested a saved handoff to continue with Claude. Created docs/CLAUDE-HANDOFF.md
with repository state, decisions, evidence, pending coverage approval and next steps.
The feature list remains unapproved; no implementation or publication is authorised
by this transition. Preserve local inventory changes. Next assistant should read the
handoff and resume the coverage discussion without repeating the completed audit.

## 2026-10-17 — Guide-derived inventory recheck and concurrent-user decision

User asked for an independent recheck of the 16 groups against the User Guide and
Developer Guide before approving coverage. The feature list was re-derived from the
guides alone; the completed code audit was not repeated and nothing was executed.

Result: no group to remove. Every group maps to guide content and every guide chapter
maps back to a group. One gap found: session lifecycle, local data lifecycle,
transactional consistency and the refusal-message standard appear only as cross-cutting
prose, so no inventory ID, owner or completion gate covers them. INV-17, platform and
data foundations, is proposed and not yet decided. Also flagged for explicit decisions:
the disabled wishlist affordances that exist in the current UI, and INV-16 mixing
portable behaviour with JavaFX presentation specifics.

Decision: the web application no longer keeps one user logged in at a time. Concurrent
multi-user access replaces that desktop constraint, and local-only assumptions are open
to revision rather than carried over by default. This supersedes single-session
statements inherited from the JavaFX implementation, including the guides' "the other
person sees your message the next time they log in" behaviour.

Decision: the desktop minimum window size of 960 x 640, and the fixed JavaFX card and
layout units built around it, are not carried over. A web layout is expected to adapt to
arbitrary viewport sizes rather than to a fixed minimum, so the desktop sizing figures
are source constraints and not target acceptance criteria. Exact breakpoints and the
viewport sizes used for acceptance testing remain a specification detail.

Recorded as pending consequences, not decided here: the web session and authentication
mechanism; whether another participant's messages, offers and meetup changes appear on
refresh or through live updates; concurrency control now that the single service worker
no longer serialises every operation; read and unread behaviour with simultaneous
viewers; the browser equivalent of unsaved-change protection; and User Guide wording
that currently describes single-user behaviour.

User: "Add INV-17". The group is added as platform and data foundations: web session
and authentication lifecycle, concurrent multi-user access and the integrity constraints
that replace desktop operation serialisation, demo data lifecycle and reset, atomic
multi-entity changes, and the refusal-message standard. It is recorded as different in
kind from INV-01 to INV-16 and has no source trace in the evidence document, because it
is not an existing JavaFX screen feature.

Updated workflow/FEATURE-INVENTORY.md, docs/DeveloperGuide.md and AGENTS.md in one pass
with the new group and both decisions. Inventory coverage for the 17 groups remains
unapproved. No application code, tests, commits, pushes or remote issue changes
occurred.

## 2026-10-17 — Inventory coverage approved

User: "Approve coverage".

Coverage of the 17 feature groups is approved. The approval covers the feature list
only. It does not approve detailed specifications, does not resolve the recorded source
ambiguities (direct listing-detail access, UI versus service meetup rules, conversation
creation timing), does not freeze the migration source baseline, and does not authorise
implementation.

Updated the inventory status and remaining-work item, the evidence document header, the
Developer Guide and the handoff. Next decision: whether 1d812ef is the frozen migration
source reference and how later source changes are handled. No code, tests, commits,
pushes or remote issue changes.

## 2026-10-17 — Migration source baseline frozen

User approved freezing the migration source reference at
`1d812ef270d780d248c74f7a37c543345df70026` on `codex/migration-workflow`.

Specifications cite behaviour at that commit. A later change to the JavaFX source does
not change migration scope by itself. It requires a proposed re-baseline with a diff of
the affected paths and explicit user approval first. Finding such a change is a reason
to raise a re-baseline, not to specify against newer source.

The re-baseline procedure itself (trigger, who produces the diff, which gate approves)
was deliberately not written now; the user chose the freeze without defining it in
advance. Recorded in the inventory, AGENTS.md, the Developer Guide and this handoff.

Next: detailed specifications, one feature at a time. No code, tests, commits, pushes or
remote issue changes.

## 2026-10-17 — INV-17 demo data decision

Started the INV-17 product specification using the manage-product-spec skill. No
specification file was created yet; the skill requires the interview first.

User asked why seeded in-flight deals were proposed. Explained that incoming offers,
My Sales and My Purchases, the cancellation flow, the meetup bar, the conversation
"Offers and sales" group and most dashboard tiles have no visible state until a deal is
part-way through, and that the counterweights are fixture complexity, the risk of a
wrong fixture reading as an application bug, seeded state existing without the path that
creates it having run, and the fact that concurrent users now make building a deal quick.

Decision: seed content is seeded accounts and a spread of listings across statuses, with
no seeded offers, sales or meetups. Every offer, sale, meetup and conversation a viewer
sees is produced by the real flow. Account count, listing status spread and whether
listings carry photos remain open within this decision.

## 2026-10-17 — Publication and implementation branching

User authorised publishing the accumulated work on a separate branch with a pull
request, then starting INV-17 implementation on a new feature branch with multi-agent
SDD roles.

Not performed by the assistant: this session has no shell on the workspace machine
(the isolated Linux environment fails to start) and no valid GitHub credential in the
cloud container, so no Git or gh command can be executed here. Exact commands were given
to the user. Nothing is claimed as pushed, branched or opened until the user confirms it.

Sequence agreed for the implementation work: finish the INV-17 product specification,
then its technical specification, then planning and tasks, then implementation. Owner
assignment and the coordinator role's restriction on writing production code remain open
gates before implementation starts.

## 2026-10-17 — No reset; persistent PostgreSQL from the start

User: "in the js app ther should be no resets, a reset only happens on the java app
cause persistence might be an issue", then "Hosted Postgres from the start".

Decision: the web application has no reset feature, and uses a hosted PostgreSQL database
from the start. This supersedes the earlier approval of resettable demo data with durable
storage deferred to PostgreSQL. Reset existed on the desktop only because the data sat in
a folder on one person's disk, and a hosted application has no equivalent.

Reason recorded for the pairing: on a serverless host, keeping the deferral would mean
server state in memory, which disappears between deployments and cold starts. That is not
a reset feature, but users would experience it as one, at unpredictable times. "No reset"
and "no database yet" cannot both hold there. A file database on a persistent volume was
offered as the third option and not chosen; it would have meant moving off Vercel
serverless.

Fresh demo data still applies: existing accounts, records and images are not imported.
Seeding runs once at provisioning.

Still open: the managed PostgreSQL provider, schema and migration approach, seed content
detail (account count, listing status spread, whether listings carry photos), image
storage, and the session mechanism.

Updated AGENTS.md, workflow/FEATURE-INVENTORY.md, docs/DeveloperGuide.md,
docs/CLAUDE-HANDOFF.md and both specification skills, which all carried the superseded
constraint as live instruction. No code, tests, commits, pushes or remote changes.

## 2026-10-17 — Sessions and cross-user update visibility

Decisions taken in the INV-17 product specification interview:

- A login lasts until the user logs out explicitly. The session survives closing the
  browser, so a returning visitor is still signed in. No inactivity expiry.
- The same account may be signed in from more than one browser at the same time, with no
  eviction of an earlier session. Taken as a default rather than asked, on the grounds
  that it is ordinary web behaviour and the desktop restriction it would echo is the
  single-logged-in-user constraint already dropped. Open to reversal if the user asks.
- A participant's new message, and offer or meetup changes, appear to the other
  participant without that person navigating or pressing anything, within a few seconds.
  The scope is the conversation page and the sidebar unread total; other pages load
  current data on navigation and through the existing Refresh controls.
- Mechanism: background polling, decided at product level rather than deferred.

Reasoning recorded for the polling choice. Only the conversation page, the unread total
and the offer and meetup bars are watched rather than browsed, so this is one surface,
not an application-wide concern. Vercel's serverless functions do not host websocket
connections, per Vercel's own support documentation, so push would mean server-sent
events with reconnection handling or a third-party realtime service. A realtime channel
also needs participant access checks that mirror the HTTP routes, written separately and
failing quietly when wrong, which is the exact failure this marketplace's rules exist to
prevent; polling reuses the already-secured route. The one case that would have narrowed
the gap, a provider such as Supabase whose Realtime feature listens to table inserts,
was not available to weigh because the database provider is still undecided. The user
chose to commit to polling now rather than defer the mechanism to the technical spec.

Open within this area: polling intervals, behaviour when a poll fails, and whether the
unread total polls at a different rate from an open conversation. These are technical
specification details, not product decisions.

No code, tests, commits, pushes or remote changes.

## 2026-10-17 — Concurrency refusals and meetup timezone

Concurrency refusals: when two users act at once and one action loses, for example a
second buyer booking a slot just taken, the loser receives a refusal naming what changed
in real values, and the affected part of the page shows the new state. Recorded as the
existing refusal standard applied to a situation the desktop could not produce, not as a
new decision. The user was told it was being taken as settled and did not object.

Timezone decision: meetup times use one fixed application timezone, displayed with a
zone label. Both participants always see the same time string, and the 15-minute start
grid, the 60-day-ahead cutoff, durations and overlap checks are evaluated in that one
zone. Asia/Singapore was named as the example; the configured zone is a technical
specification detail.

Rejected alternatives and why they matter: showing each viewer their browser zone while
anchoring the rules to a canonical zone is correct for travellers but requires refusal
messages to state which zone a limit was applied in; letting both display and rules
follow each viewer's zone would make the 60-day cutoff and start grid differ between the
two participants, so one could offer a time the other cannot book.

This affects INV-11 and INV-12 as well as INV-17. The desktop used the machine's default
zone, which was unambiguous only because one machine ran everything.

No code, tests, commits, pushes or remote changes.

## 2026-10-17 — INV-17 demo logins and product specification drafted

Decision: demo account credentials are documented in the User Guide and the repository
README, and no page in the application displays them. A grader who reads the docs gets a
populated seller view; a passing visitor is not invited to act as a seeded user. Seeded
listings therefore belong to accounts that can be signed into by anyone who reads the
documentation, which is accepted for a demo deployment.

Created workflow/PRODUCT-SPEC.md. INV-17 carries draft detail: FR-1701 to FR-1720 and
AC-1701 to AC-1719 covering sessions, persistence and seeding, concurrent-access
invariants, atomic multi-record changes, the refusal standard, background update of an
open conversation and the unread total, and fixed-zone time display. INV-01 to INV-16 are
recorded as inventory-only. The document is draft and INV-17 awaits approval.

Open questions recorded in the specification: seed content detail including how reserved
and sold listings can exist when seeding creates no offers or sales; image storage given
a serverless filesystem; whether a signed-out visitor may browse, which parity says they
may not; the configured timezone value; polling intervals; and human owner assignment.

No code, tests, commits, pushes or remote changes.

## 2026-10-17 — Seed listing statuses

User: "available only but tests should create all 4".

Decision: seeding creates available listings only. Reserved, sold and archived are
produced by the application's own operations rather than written into the seed. This
resolves the contradiction raised while drafting the specification, where a spread of
listing statuses could not coexist with seeding no offers or sales, because a listing is
reserved only through an accepted offer and sold only through a completed sale.

Accompanying verification expectation recorded: automated tests reach all four statuses
by performing the operations that produce them, not by setting a status value directly
in storage. Some existing JavaFX listing tests use the direct route to reach reserved and
sold; the web tests do not inherit that shortcut.

Updated FR-1706, AC-1707, the scope section and open question 1 in
workflow/PRODUCT-SPEC.md. Still open within seed content: demo account count, listing
count, and whether seeded listings carry photos.

No code, tests, commits, pushes or remote changes.

## 2026-10-17 — No anonymous browsing

Decision: every marketplace surface requires a signed-in user, including search and
listing details, matching the source application. A signed-out visitor reaches only the
login and registration surfaces. The alternative, readable search and listing pages when
signed out, was declined; it would have been a scope change beyond parity and would have
added a signed-out state to specify for INV-03, INV-04 and INV-16.

Recorded as FR-1705a and AC-1708a in workflow/PRODUCT-SPEC.md, with anonymous browsing
added to the exclusions and open question 3 closed.

No code, tests, commits, pushes or remote changes.

## 2026-10-17 — Database engine, environments and test isolation

User asked for an exhaustive account of how persistent PostgreSQL changes the decisions
made so far, noting that data created during test runs is now saved permanently.

Analysis recorded. SQLite is not viable for the deployed application: a serverless
filesystem is ephemeral and per-instance, so concurrent requests would not share a file.
Hosted SQLite over HTTP exists but buys nothing here, because the two reasons to want
SQLite, zero infrastructure and deleting a file to reset, are the two things already
given up by choosing hosting and no reset. A single-writer engine also cannot produce
the write concurrency that FR-1709 exists to guarantee.

Decision: PostgreSQL in every environment including automated tests.

Consequences recorded as specification changes rather than left implicit:

- Each environment has its own database: local development, tests, previews and the demo.
  Tests never target the demo database. Dropping and recreating a development or test
  database is ordinary practice and is not the reset the application excludes. FR-1705
  now says "within the application" and names operator provisioning explicitly.
- Seeding is an idempotent provisioning step rather than an application route, recorded
  as FR-1706a with AC-1708b. It never restores an environment to its seed.
- Domain timestamps come from an injectable application clock, never database time or a
  column default. Without it the 60-day cutoff, the future-start rule, the
  meetup-has-ended rule and the dashboard upcoming-meetup boundary cannot be tested
  without waiting in real time. The source controls this through
  ApplicationRuntime.open(Path, Clock).
- Test runs start from a known database state. Usernames are globally and
  case-insensitively unique, so a test registering a fixed username passes once and then
  fails against a dirty database; a clean starting state is the fix, not randomised names.
- Serverless functions each hold a connection and PostgreSQL has a hard ceiling, so
  pooled connections or an HTTP serverless driver are required. This influences provider
  choice.
- Accepted limitation: with open registration and no reset, the demo accumulates whatever
  visitors leave. Archived listings cannot be reopened and accounts cannot be deleted,
  both by parity, so nothing can be tidied from inside. Operator re-provisioning is the
  only remedy.
- The six JavaFX SQLite migrations are a reference for intent, not files to port. The
  PostgreSQL schema is written fresh. The source rule that merged migrations are only
  appended to, never edited or reordered, carries over.

Nothing already agreed was reversed. INV-01 to INV-16, the invariant list, the timezone,
polling, session, available-only seeding and no-anonymous-browsing decisions stand.

Updated workflow/PRODUCT-SPEC.md, AGENTS.md and the technical specification skill.
No code, tests, commits, pushes or remote changes.

## 2026-10-17 — Tracker publication and board decision

The user selected repository issues and labels and explicitly deferred GitHub Projects.
Stages and Paused/Blocked flags are configured; no extra Projects token scope is needed.
Setup issues #3, #4, #5 and #7 are closed with evidence and limits. The user authorised
committing and publishing the remaining tracker documentation, followed by closure of
#6 and parent #2 once remote publication is verified.

Publication is isolated on codex/migration-workflow; Claude continues separately on
codex/nextjs-migration. Only setup documents and task logs are included; the active
product/technical specs, web source and mixed Claude handoff/history edits are excluded.
No merge or issue #8 change is authorised by this publication. See the setup issues for
actual final remote status. The published source baseline remains 1d812ef.
