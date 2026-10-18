# Project decisions and interactions

## Current coordinator handoff — 2026-10-19

Workspace E:/CS3227-2610-MP3, branch codex/nextjs-migration at f36a520 verified
through Git; existing modified/untracked files preserved. Current status is
workflow/INV17-OUTCOME.md, Current takeover status, superseding older handoffs below.
C2-P complete per prior review evidence; publication_tests recovered its actual
coverage run: 490 tests/88.38% branches, exit0. Reviews remain prior evidence.
Full C2 unstarted. A 2/2, B 2/2, B9 1/2, C2-P 0/2 unchanged. No build/browser run.
Code baseline f36a520 was committed/pushed externally during the interruption;
remote SHA verified on resume. Documentation publication remains in progress.
The separate INV-01 worktree and unresolved merge remain untouched.

Owner answered "yes" to the proposed credential-verifier interface shape: INV-01
takes username/exact password, trims and case-insensitively looks up username,
returns only user ID/original-case username/display name on success, uses equivalent
unknown-user/wrong-password rejection, and distinguishes safe operational errors.
Verifier creates no session; INV-17 creates it after success. Existing-session
checks precede verification/creation and preserve "Already signed in." behaviour.
Reason: preserve INV-01 ownership and approved minimal responses without exposing
credential storage. Source decisions inspected via Git at INV-01 commit 210fa9e;
no replacement of its approved behaviours. Contract recorded in technical spec;
outcome and developer/agent pointers aligned. No application or test edits.
Next authorised step: publish the partial migration baseline under PUB-1, then
finish remaining INV-17 cookie/route details before readiness. Cross-site request
protection is approved as recorded below.
Session-only no-store/current-database cache policy is now approved below.
Registration and account UI detail remain with INV-01. Approval is design-only;
full route work stays blocked. Existing execution ledger is preserved.

Owner subsequently answered "yes thats fine" to the persistent-cookie proposal:
400-day lifetime renewed during authenticated use, HttpOnly/Secure/SameSite=Lax,
path `/`, and no server age/inactivity expiry. Browser expiry, clearing data or
privacy policies may require login while the server session remains valid. This
clarifies the earlier indefinite browser-persistence wording in FR-1701; it does
not change server revocation. Chrome's documented 400-day limit informed the
proposal (https://developer.chrome.com/blog/cookie-max-age-expires); no browser
experiment was performed. Product/technical specs, outcome and developer/agent
guidance aligned. No new handoff or duplicate log created. Counters unchanged;
no production/test edits, database use, build, browser run, commit or push.

## Current development agreement

### 2026-10-19 — HTTP envelope shape approved

Owner answered "approved" to the proposed shared authentication HTTP envelope:
`{ ok: true, data: ... }` on success and `{ ok: false, error: ... }` on failure.
Error preserves existing refusal brand/category code/safe message, adds a specific
reason and optional fieldErrors. Passwords, hashes and session tokens stay out of
response bodies. Rationale: preserve INV-01's approved logical responses and allow
frontend decisions without parsing messages. This approves only the shape, not
exact reason names/statuses, payloads or changes to completed domain code.
Technical spec, outcome and developer/agent pointers updated; next discussion is
reason/status mapping. Branch rechecked as codex/nextjs-migration. Counters and
completed ledger entries unchanged; full C2 remains unstarted. No production/test
edits, database connections, test/build/browser runs, commit or push.

### 2026-10-19 — HTTP failure reason/status mapping approved

Owner answered "approved" to the seven-row mapping now recorded in the technical
spec's C2 HTTP envelope section: validation 400, credential rejection and required
authentication 401, already signed in 409, unavailable lookup/storage 503, and
unexpected server failure 500, each with its proposed specific reason identifier.
Confirmed signed-out session checks return 200; unavailable lookup returns 503
and preserves the cookie, consistent with INV-01. Broad error categories remain.
No change to earlier account/session behaviour. Next authorised discussion is
success-response payloads; remaining transport details still block full C2.
Technical spec, outcome and developer/agent guidance aligned. No code/test edits,
database use, application checks, commit or push. Counters and ledger unchanged.

### 2026-10-19 — HTTP success payload/status mapping approved

Owner answered "approved" to registration 201 with username; login and signed-in
session check 200 with authenticated true and user { id, username, displayName };
signed-out check and confirmed logout 200 with authenticated false and user null.
All are inside the approved success envelope. Registered username case is
preserved; registration creates no session and login sets its cookie separately.
Already-ended logout succeeds, but unavailable lookup/revocation does not confirm
termination. Rationale: preserve INV-01's logical responses and use the same login
and session-check shapes for lost-response recovery. No replacement of existing
product behaviour. Technical spec, outcome and developer/agent pointers aligned.
Next authorised discussion: field-error format, then remaining transport details.
Full C2 unstarted; all counters and completed ledger entries preserved. No code/test
edits, database use, test/build/browser runs, commit or push.

### 2026-10-19 — Field-error format approved

Owner answered "approve" to fieldErrors mapping affected fields to arrays of safe
messages, including registration's taken-username field error. Omit unaffected
fields and omit fieldErrors for general failures. Never echo passwords or submitted
values. Invalid login credentials remain a general error without identifying which
credential was wrong. Rationale: support INV-01's agreed inline registration errors
while preserving uniform credential rejection. Exact message catalogue and request
field allowlist remain pending. Technical spec, outcome and developer/agent pointers
aligned; next discussion is category mapping. All counters/ledger gates unchanged;
full C2 unstarted. No code/test edits, database use, tests/build/browser run, commit
or push. Documentation whitespace and targeted readback checks performed only.

### 2026-10-19 — Transport-only INTERNAL category approved

Owner answered "approved" to adding INTERNAL only to the HTTP error contract for
INTERNAL_ERROR/500. Keep the six domain refusal categories unchanged. Unexpected
failures use a fixed safe message and never expose exception details. Rationale:
programming failures must not be falsely classified as session/storage problems.
Exact safe wording, transport type/brand handling and remaining category mappings
are not approved by this decision. Technical spec, outcome and developer/agent
guidance aligned; next discussion is remaining reason/category mapping. Completed
ledger entries and correction counters unchanged; full C2 still unstarted.
No code/test edits, database use, application tests/build/browser run, commit or
push. Verification limited to documentation whitespace and targeted readback.

### 2026-10-19 — Remaining reason/category mapping approved

Owner answered "appproved" to VALIDATION_FAILED -> VALIDATION;
INVALID_CREDENTIALS, AUTHENTICATION_REQUIRED and ALREADY_SIGNED_IN -> SESSION;
SESSION_LOOKUP_UNAVAILABLE and STORAGE_UNAVAILABLE -> STORAGE. The already-approved
INTERNAL_ERROR -> transport-only INTERNAL mapping is unchanged. Rationale: lookup
unavailability is a storage failure, not an invalid session; the specific reason
still drives INV-01 recovery. Consolidated the technical-spec table without
changing approved reasons/statuses. Outcome and developer/agent pointers aligned.
Next discussion: remaining safe messages, preserving INV-01's existing wording.
No code/test edits or gate/counter changes; full C2 unstarted. No database use,
application tests/build/browser run, commit or push. Documentation whitespace and
targeted readback checks only.

### 2026-10-19 — General error messages approved

Owner answered "yup approved" to the four proposed general messages for
VALIDATION_FAILED, AUTHENTICATION_REQUIRED, STORAGE_UNAVAILABLE and INTERNAL_ERROR,
now recorded verbatim in the technical spec. Existing INV-01 credential rejection,
already-signed-in and unavailable-session wording remains unchanged. Logout failure
retains "Couldn't confirm logout. Please try again." and the current page/unsaved
input. Rationale: safe actionable feedback without exposing internals or overriding
approved operation-specific recovery. Technical spec, outcome and developer/agent
pointers aligned. Next discussion is cache policy, then remaining transport details.
No code/test edits, gate/counter changes, database use, application tests/build/browser
run, commit or push. Full C2 remains unstarted. Documentation whitespace and targeted
readback checks only.

### 2026-10-19 — Return to INV-17 session-route scope

Owner asked whether INV-17 was done and why discussion moved into INV-01. Clarified
that only internal C2-P is complete, full session routes remain unimplemented, and
registration/field-message discussion had drifted beyond the needed boundary.
Owner then said "ok lets return". Active discussion returns to INV-17 session
routes and their necessary shared dependencies; earlier approvals are preserved.
Registration/account UI implementation and further registration-only detail remain
with INV-01. The cache proposal is still unapproved; returning is not approval of
that proposal or implementation. Outcome, spec scope and developer/agent pointers
aligned. No code/test edits, delegation, database use, application verification,
commit or push. Counters and completed gates unchanged. Documentation checks only.

### 2026-10-19 — Session-only cache policy approved

Owner answered "approved." to no-store for every POST/GET/DELETE /api/session
response including errors, and resolving session checks against current database
state without a shared application cache. Reason: avoid stale authentication
results in recovery and logout confirmation. The earlier broader proposal remains
unapproved; this does not authorise registration caching or frontend refresh work.
Technical spec, outcome and developer/agent guidance aligned. Next discussion:
session-route cross-site request protection. Full C2 unstarted; ledger/counters
unchanged. No code/test edits, database use, tests/build/browser run, commit or push.
Documentation whitespace and targeted readback checks only.

### 2026-10-19 — Origin policy approval and requested baseline push

Owner approved exact configured-Origin checking for POST/DELETE /api/session:
reject absent/null/mismatch before credential verification or session changes;
PERMISSION/ORIGIN_NOT_ALLOWED/403 with the proposed safe message. No trust from
caller-supplied host headers. Recorded in the technical spec; implementation pending.
Owner then explicitly requested a push so a friend can start INV-02 and asked if
necessary workflow files are present. Commit/push now authorised for the partial
baseline, superseding the earlier prohibition; no INV-01 merge or new code authorised.
Remote branch check confirmed 1761bc1. Workflow, inventory/evidence, four skills,
role/configuration files and templates are tracked. No standalone PLAN.md: INV-17
uses the technical-spec execution ledger. Added teammate startup pointers.
INV-02 detailed design/implementation approval was not found in this checkout;
publishing enables specification work, not bypassing feature readiness.
PUB-1 rows written before tester/reviewer delegation. Existing tracked changes,
C1/C2-P files, historical handoff and project logs selected; Claude outputs/ and
all local environment files excluded. No local secret contents printed. Initial
sandbox remote connection failed; allowed network escalation verified the remote.
Tests/package review and publication outcome are pending. A/B/B9/C2-P counts remain
2/2, 2/2, 1/2, 0/2. Separate INV-01 worktree remains untouched.

### 2026-10-19 — Resumed publication verification

After owner said "can you continue", verified code baseline f36a520 and matching
remote SHA; this commit/push occurred externally during the interruption. Existing
documentation remained unstaged. publication_tests recovered its actual session
60316 result: coverage exit0,19 files/490 tests,137/155 branches(88.38%),49.68s.
It then ran typecheck exit0 and lint exit0: one warning in ignored generated
coverage/block-navigation.js for unused eslint-disable, zero errors. No corrections.
The tester confirmed loopback127.0.0.1:55432/test-named base, no shared override;
no production/demo database used. No build/browser checks.
publication_review passed workflow-package availability; no material omissions,
not INV-02 implementation readiness. Core workflow/skills/roles/templates tracked.
Coordinator compared candidate contents to local secret values without printing
values: zero matches; local environment/Claude outputs files excluded. Final
documentation staging/commit/push follows under existing owner authorisation.
Completed implementation gates and correction counters unchanged.

### Earlier development agreement (historical)

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

- Target workspace: E:/CS3227-2610-MP3. The originating chat is attached to MP2; use a fresh MP3 project chat for configuration discovery.
- Working branch: codex/migration-workflow; verify before changing files.
- Written: migration workflow, guide-derived inventory, aligned SDD skills, five development role briefs, four worker TOML configs, and startup guidance.
- Checked: documentation links/whitespace; supplied smoke test reports four custom-type launches. The disposable generic-dispatch exercise observed initial handoffs, failure approval, one actual repair cycle and final reviews; isolated/native loading and unexercised paths remain unverified.
- Current saved role instructions were read by exercise workers through generic dispatch. This does not revalidate native custom-config loading after instruction edits.
- Current task: INV-17's logical foundations are implemented and green (142 unit tests in web/). Next is the persistence layer, which needs a database. INV-01 to INV-16 remain inventory-only.
- Roles: the assistant acts as coordinator and delegates to subagents for tests, production code and review. Human approval remains with the user; human owners are still unassigned.
- Stack: Next.js App Router with TypeScript, Drizzle over PostgreSQL, zod, Vitest with React Testing Library, Playwright, Tailwind, own sessions table with node:crypto PBKDF2.
- Published 2026-10-17: commit 7c16bcf on codex/migration-workflow, pushed to origin. Feature branch codex/nextjs-migration created at the same commit and checked out. Pull request status not verified from this session.
- Storage: hosted PostgreSQL from the start, no reset feature. Supersedes resettable demo data with deferred PostgreSQL.
- Blocker: the assistant session has no shell on the workspace machine and no valid GitHub credential, so it cannot run Git or gh. Branching, committing, pushing and PR creation are performed by the user; the assistant writes files only and verifies Git state by reading .git refs. Workflow setup was committed locally as 1d812ef. F1-F4 have documentation corrections; F2 now explicitly distinguishes missing behaviour from independently verified existing behaviour. The covered disposable exercise is now complete; see docs/agents/WorkflowExercise.md for observations and unexercised scenarios. Feature/task/PR templates and stage conventions are written locally; GitHub board/label setup remains pending. Issue #7 exercise results and remaining limits are recorded in docs/agents/WorkflowExercise.md; no remote issue update was performed.
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

## 2026-10-17 — Workflow branch published and feature branch created

User performed the Git work, which this session cannot execute. Verified by reading
.git refs rather than by running Git:

- Commit `7c16bcf`, "Record inventory approval, migration scope decisions and draft
  INV-17 product spec", on `codex/migration-workflow`.
- `refs/remotes/origin/codex/migration-workflow` matches that commit, so the branch is
  pushed.
- `codex/nextjs-migration` created at the same commit and checked out as HEAD.

Pull request status was not verified; this session has no GitHub credential. A pull
request body was generated for the user to paste, matching
.github/PULL_REQUEST_TEMPLATE.md, and corrected on review feedback: the plan line now
states that no implementation plan exists rather than implying an empty
workflow/PLAN.md, and branch ancestry is called out because `codex/migration-workflow`
carries bf47d26 and 93dbfc6 from PR #1 on `codex/agent-specifications`, with the
squash-merge case noted as requiring a rebase.

Separately noted for later correction: AGENTS.md instructs that only the coordinator
updates workflow/PLAN.md, a file that has not been created.

No code, tests or remote changes were made by this session.

## 2026-10-17 — INV-17 product specification approved

User approved the INV-17 product specification. The feature status table in
workflow/PRODUCT-SPEC.md records INV-17 as approved 2026-10-17. Approval covers INV-17's
product intent only. It does not approve INV-01 to INV-16, is not a technical design, and
does not authorise implementation.

## 2026-10-17 — Role model and stack decisions

Role decision: with one assistant and no assigned human owners, the assistant acts as
coordinator and delegates to subagents taking the tester, implementer and reviewer roles,
rather than writing tests and production code itself. This preserves the workflow's role
separation and its red-then-green evidence requirement. Human approval stays with the
user. Human owner assignment remains outstanding and still blocks implementation
assignment.

Stack decisions, taken after comparing current practice against this project's specific
constraints rather than popularity alone:

- Next.js App Router with TypeScript on Vercel.
- Drizzle over PostgreSQL. The deciding factor is that five marketplace invariants are
  enforced by partial unique indexes carried over from the Java schema, and those indexes
  are now load-bearing because the single service worker is gone. Drizzle expresses a
  partial unique index directly and emits readable SQL a reviewer can compare with the
  Java migration. Prisma's schema language has not covered partial unique indexes, so its
  migration SQL would be hand-edited anyway, and it needs specific configuration for
  transaction-mode pooling.
- Zod at the request boundary, with domain invariants staying in the domain layer.
- Vitest with React Testing Library, and Playwright for browser journeys.
- Tailwind. The existing warm palette becomes theme tokens rather than a ported stylesheet.
- Sessions in a database table with an opaque token in an httpOnly cookie. Lucia is
  discontinued, and Auth.js and Better Auth are built around provider and email identity,
  which does not fit username login with the existing password policy. A stateless signed
  cookie could not satisfy FR-1703, which requires logout to end only one session.
- PBKDF2-HMAC-SHA256 through node:crypto at the source's parameters, avoiding the native
  modules argon2 and bcrypt need on a serverless runtime.

## 2026-10-17 — INV-17 technical specification drafted

Created workflow/TECHNICAL-SPEC.md as a draft covering INV-17: ten technical decisions
with rationale, layering that mirrors the source services, the sessions table, the
partial-index concurrency mechanism, the API surface for sessions, authenticated image
serving and polling, security including cookie and hashing parameters, edge cases,
verification strategy by layer, and requirements traceability from FR-1701 to FR-1720.

Recorded explicitly in that document: Row Level Security is not used and the database
role is privileged, so an authorization bug is not caught by a second layer. This matches
the source, where services are the only check, and the mitigation is the same.

The domain layer is specified as depending on nothing but TypeScript, which makes it the
largest body of work that can proceed before any database exists.

Open: the application timezone value, polling intervals, seed counts, the PostgreSQL
major version pending from provisioning, the package manager and Node version, and human
owner assignment.

Also drafted, outside the repository, a script that creates one parent feature issue per
inventory group plus the Supabase provisioning task, for the user to run since this
session has no GitHub credential.

No code, tests, commits, pushes or remote changes by this session.

## 2026-10-17 — No ORM; Supabase CLI migrations, generated types, direct connection

User challenged the need for an ORM given TypeScript and Supabase. The challenge was
correct and the earlier Drizzle decision is superseded.

Correction recorded: Drizzle was partly justified on partial unique indexes being
expressible. That was a weak argument, since hand-written SQL expresses them better and
the Java migrations already contain them. Its real benefit was typed query results, and
`supabase gen types typescript` provides those from the live schema without defining the
schema a second time in TypeScript.

Decision: no ORM. Schema as numbered `.sql` migrations managed by the Supabase CLI, row
types generated from the schema, and `postgres.js` for queries and transactions.

Why `supabase-js` is not the data path, explained to the user and recorded as TD-02a:
PostgREST commits each HTTP request before replying and returns the connection to its
pool, so several writes cannot form one unit. Four operations need that: accepting an
offer, completing a sale, cancelling a sale, and deleting an enquiry-only listing.
Accepting an offer alone writes four changes across three tables, and a partial failure
leaves a listing reserved with no sale, which no screen can repair because releasing a
listing is something only a sale's cancellation does.

The user asked whether promises or after-the-fact verification could substitute. Recorded
reasoning: promises order writes but do not make them atomic; catch-and-undo fails when
the undo itself fails, when the process is killed between writes with no chance to run
it, when an undo contradicts a rule such as rejected offers staying rejected, and because
the intermediate state is visible to other users who may act on it; a reconciliation pass
leaves visible windows of inconsistency and cannot always decide the right repair. Source
non-functional requirement 1 states every such change is one database transaction, and
`acceptOffer_saleSaveFailure_rollsBackListingAndOffers` already asserts the rollback, so
parity requires it.

The alternative of plpgsql functions called by RPC was offered and declined: it gives
real transactions but moves the hardest rules out of TypeScript and out of reach of the
unit tests.

`supabase-js` is retained for object storage only. Supabase Auth and Row Level Security
remain excluded on parity grounds.

Updated workflow/TECHNICAL-SPEC.md: TD-02 replaced, TD-02a and TD-02b added, the
repository layer, migrations, row types, transactions, dependencies and connection
management sections revised.

A scaffold task had been prepared with Drizzle and was stopped before it ran, so no code
was created under the superseded decision. No commits, pushes or remote changes.

## 2026-10-17 — First implementation task: scaffold, test readiness review, FR-1719 narrowed

Work began on INV-17's purely logical parts under the agreed subagent role model: the
assistant coordinates, subagents take the tester, implementer and reviewer roles.

Scaffold (implementer): Next 16.4.2, React 19.3.1, TypeScript 5.9.3, Vitest 5.0.5,
Tailwind 4.3.3, postgres.js 3.4.9, zod 4.6.7, Supabase JS 2.118.0, Supabase CLI 2.121.0.
No ORM, confirmed by `npm ls`. Install, typecheck, test and build all verified passing.
Delivered to `web/` in the repository; the Java source at `src/main/java` is untouched,
since Next.js also wants `src/` and the two would otherwise overlap.

Recorded deviations from the scaffold: TypeScript is 5.9.3 rather than the registry's
7.0.2, because typescript-eslint does not yet support TS 7 and that reaches the project
through eslint-config-next; the Geist fonts were replaced with a system font stack
because the assistant's sandbox blocks fonts.googleapis.com, which is an environment
limitation and not a design decision; and a lint rule now enforces the domain layer's
purity by blocking imports of next, react, postgres and @supabase/* inside src/domain.

Tests (tester): 102 cases across the refusal taxonomy, the clock and fixed-zone
formatting. No implementation exists, so they fail.

Test readiness review (specification reviewer) returned NOT READY with five blocking
findings, three of which were defects that would have passed silently:

- The block intended to prove formatting ignores the host timezone could not catch the
  most likely wrong implementation, because Intl.DateTimeFormat resolves and caches its
  timezone at construction, so a module-level formatter without an explicit timeZone
  locks onto the host zone at import. Demonstrated with actual output in the container.
- `delete process.env.TZ` resets the process to UTC rather than restoring the host zone,
  silently weakening every suite declared after it.
- `messageTooLong` was tested twice with the same limit, so an implementation hardcoding
  the limit into the template would pass.
- A taxonomy assertion checked the module's own code list against itself, passing even if
  the constructor discarded its code argument.
- `zoneLabel()` was satisfied by returning the IANA identifier rather than a label.

The review also confirmed what was strong: the 60-day horizon boundaries reject both a
millisecond-arithmetic and a UTC-calendar implementation, and clock injection is enforced
rather than assumed, verified by substituting Date.now() and watching three tests fail.

All findings were corrected by the tester, who verified the timezone finding by building
a deliberately broken module and confirming the revised tests fail against it. The
assistant treated a test-readiness finding as a normal review gate rather than a failed
loop, so no correction-cycle budget was spent.

Scope decision: FR-1719 narrowed. INV-17 owns the evaluation zone and the date and time
primitives. The 15-minute start grid, the 15-minute-to-4-hour duration bounds and overlap
checks move to INV-11 and INV-12. The reviewer had flagged them as untested under INV-17,
reading the approved wording literally; the narrowing resolves that rather than widening
the group.

Also recorded: `isRefusal` is brand-based rather than instanceof, because a refusal
raised in a server action is serialized across the React server component boundary where
instanceof is false.

Noted as gaps belonging to later features: money and message-length validators. INV-17
specifies formatters with no range checking, so the 1-cent-to-1,000,000 and
1-to-1,000-character ranges have no reject-side coverage and must land with the listing,
offer and message features.

No commits, pushes or remote changes by this session.

## 2026-10-17 — INV-17 foundations: correction cycle and close

The implementation and coverage review returned three blocking defects and four
improvements, all demonstrated by running code:

- `formatMoney(4000.004)` produced `S$40.0.0039999999999054126`, two decimal points and
  floating-point noise in a user-facing string.
- `duplicatePendingOffer(NaN)` produced `S$NaN.NaN` inside a refusal message, a direct
  FR-1712 breach. The suite's leak guard screened neither NaN nor infinity and ran
  against three fixtures only.
- The brand-based refusal survived `JSON.stringify` alone: spread and `Object.assign`
  dropped the message, because `Error.prototype.message` is non-enumerable, and
  `structuredClone` dropped the brand and the reason code.
- An Invalid Date was handled three different ways, the dangerous one being
  `hasMeetupEnded` returning false, which would leave a sale open forever.
- `Date.UTC(50, 5, 15)` means 1950, so the day-number helper was off by 693,960 days for
  years 0 to 99, green only by coincidence.
- Nothing prevented interpolating a raw Date into a refusal message, which rendered in
  the host zone and passed all 102 tests.
- The `Refusal` constructor was public and unsanitised.

The user approved a correction cycle, treated as the normal review-then-fix flow rather
than an unexpected failed loop, so no correction-cycle budget was spent.

The tester extended the suite from 102 to 142 cases, each finding pinned, 33 failing. It
resolved the design tension in favour of catalogue-only authoring: the constructor
becomes module-internal and `Refusal` a type-only export, reasoning that making a breach
unrepresentable is stronger than forbidding it by convention, with the raw-Date case as
proof. It also established that the year 0-99 defect was reachable from outside after
all, through four-digit ISO year strings, and that the bug is invisible when both dates
sit inside that range because both shift equally.

The implementer made all 142 pass with typecheck, lint and build clean.

Re-review verdict: nothing blocking, the task can close. The horizon rewrite was
differential-tested against an independently written oracle over 680,000 pairs spanning
year 1 to 9999 CE with zero mismatches; money formatting over roughly 640,000 values
against BigInt truth with zero mismatches; the catalogue fuzzed across 45 hostile
invocations with zero leaks.

Five items carried as follow-ups against INV-17, the first two being claims in code
comments that are now false:

1. The comment saying a refusal carrying a stack or SQL is "not expressible from outside"
   is false. `Object.getPrototypeOf(r).constructor` reaches the internal class, and a
   plain RefusalPayload literal passes `isRefusal` because the brand and codes are
   exported. Both need deliberate effort, so the accidental-leakage concern is met, but
   an overstated invariant is its own defect in a spec-driven codebase.
2. `advanceBy` validates its argument but not the resulting instant, contradicting its
   own comment. Introduced by the correction cycle.
3. `requireValidDate` uses instanceof, rejecting a valid cross-realm Date, and a
   Proxy-wrapped Date leaks a raw engine message rather than the deliberate guard.
4. `isRefusal` accepts a branded array and a prototype-inherited brand, and its
   justification comment is incorrect.
5. Cosmetic: duplicated guard helpers, own-key ordering, an unused return value.

Carried forward unresolved: whether Next.js preserves a thrown refusal across the server
and client boundary, given it replaces thrown server errors with an opaque digest in
production. The returned path through `asPayload()` is solid and should be standardised
before the pattern spreads.

Task log: logs/2026-10-17-inv17-foundations-scaffold-and-primitives.md.

All verification ran in the assistant's cloud workspace. Nothing was run on the user's
machine, no database exists, no integration or Playwright test was written, nothing was
deployed, and no Git command was executed by this session.

## 2026-10-17 — INV-17 foundations: follow-up cycle cleared

The user directed that the five re-review follow-ups be cleared before any feature
imports these modules. Tester pinned them, suite grew from 142 to 168 cases with 21
failing, implementer made all 168 pass, and a focused re-review confirmed the result.

Notable decisions in this cycle:

- A module-private Symbol authoring token now gates the internal refusal constructor, so
  the prototype-constructor route is closed. The plain-payload route is not closable
  while the wire format needs a serializable brand, and the comment that claimed
  otherwise was corrected rather than defended.
- `isAuthoredRefusal` was added to answer the question the open route forces: `isRefusal`
  tells you something is refusal-shaped data, which is all you can know about a value
  that crossed a wire, while `isAuthoredRefusal` tells you this module's catalogue made
  it. A module-private WeakSet backs it, chosen over instanceof because instanceof is
  realm-bound and realm-bound checks were the subject of another finding.
- `requireValidDate` became `requireInstant` and the formatters now work in epoch
  milliseconds, which also removed the realm question from the formatting path entirely.

Verification by the focused re-review, all by running rather than asserting: 680,000
horizon differential pairs across five ranges with zero mismatches, matching the
pre-refactor baseline; 1,250,000 formatter comparisons against an oracle built in the
pre-refactor shape, also zero; six distinct construction attacks on the token, all
closed, with no token leak found by a recursive namespace walk; an exhaustive advanceBy
boundary table; and confirmation that the stack header survives `super()` taking no
argument.

Carried as a single INV-17 follow-up: an object carrying `Symbol.toStringTag: 'Date'`
passes the brand check and then reaches the engine's own error message. It needs a
deliberately crafted object, so it is not a realistic path, but the guard exists
precisely to keep engine wording out of refusals.

Recorded as a process observation rather than a defect: this was the third round in which
a claim written in a code comment reached one step further than the mechanism beneath it.
Each time the mechanism was sound and the sentence describing it was not. Worth tightening
the habit as much as the individual lines.

Still open and untouched: whether Next.js preserves a thrown refusal across the server and
client boundary, given it substitutes an opaque digest in production. The returned path
through `asPayload()` is solid and should be standardised before the first feature throws
a refusal out of a server action.

Task log updated: logs/2026-10-17-inv17-foundations-scaffold-and-primitives.md.

## 2026-10-17 — Tracker documentation published

User authorised commit/publication. An isolated codex/migration-workflow checkout
published commit 78b90de4bbfff44995fcc858570283e39a8020b5; remote SHA verified.
PR #9 updated and remains unmerged. Issues #6 and #2 were closed with publication
evidence, so setup issues #2–#7 are closed. Issue #8 is untouched. Checked 49
documentation links and whitespace; no application tests run.

Claude’s active checkout remains on codex/nextjs-migration. Its original local
setup-document copies and ongoing feature edits were preserved, so do not blindly
commit all files: reconcile the published workflow commit before subsequent Git work.
This outcome note is appended locally for Claude; publication evidence is on GitHub.

## 2026-10-17 — Handoff rewritten

User asked for a handoff to continue the work with Claude in a new session.

docs/CLAUDE-HANDOFF.md was rewritten rather than appended to. It had become layered
updates on a superseded base, with the original stopping point still stated at the top
and corrections bolted underneath, which is the pattern AGENTS.md tells us to remove
rather than extend. Historical narrative stays here, in this append-only record.

The rewrite states repository state, what exists in web/, the decisions in force with
superseded ones marked so nobody reinstates them, the open decisions, how the subagent
role model is run, the verification performed and its limits, and the carried follow-ups.

It also records the environment traps that cost real time to discover: there is no shell
on the workspace machine so no Git command can run, the cloud session has no GitHub
credential, device_commit_files sometimes reports success without writing and must be
verified by comparing file size afterwards, work is built and tested in the cloud
workspace and copied across, and fonts.googleapis.com is blocked there, which is why the
scaffold's fonts were replaced.

Correction made on the user's prompt: the first draft read as though INV-17 were
finished. It is not. Five of its twenty requirements are done, being the refusal taxonomy,
the six reason codes, and fixed-zone display with the scheduling primitives. Everything
else needs a database: sessions and per-session logout, request identity, persistence and
the signed-out refusal, seeding, the concurrency invariants, atomic multi-record changes,
and the polling routes, which are additionally blocked on INV-13 and INV-14 because no
conversations exist to poll. The handoff now carries that table plus a build order for
the persistence half.

No code, tests, commits, pushes or remote changes.

## 2026-10-17 — Supabase provisioning details and storage decisions

The user's friend granted access to the Supabase project. Facts established:

- PostgreSQL 17.11.0.003. The assistant's cloud workspace runs 16.15, so local tests
  and the deployed database are on different major versions. Low risk for what is being
  built, since partial unique indexes, timestamptz and uuid behave identically, and
  migrations will be kept free of version-specific syntax. Recorded rather than left to
  surface at deploy time.
- Region: Singapore.

Decision: two storage buckets, `profiles` and `listings`, rather than one bucket with
path prefixes. This mirrors the source's two image namespaces. The deciding argument is
the delete path: image cleanup is a reconciliation pass that removes storage objects no
longer referenced in the database, and with a single bucket the only thing keeping it off
the other kind's files is a prefix filter buried in a query, where a wrong filter still
looks plausible in review. With two buckets the separator is the bucket argument at the
call site. The source guards against exactly this failure: "recovery in one namespace
never processes or deletes the other's files."

Per-bucket settings recorded as defence in depth, not the primary check, since the
application validates image contents and dimensions before writing as the source does:
profiles private at 5 MiB, listings private at 10 MiB, both restricted to image/jpeg and
image/png.

Decision: storage is reached through the native Supabase client, not the S3-compatible
endpoint. The user initially believed S3 had been chosen, and then that only S3 was
available because the storage settings page shows S3 configuration. The premise was
corrected: that page shows S3 because S3 is the part needing configuration, while the
native API needs none. Both routes genuinely work for put, get and delete. The native
client costs two environment variables already being set and a dependency already in the
project; the S3 route would cost `@aws-sdk/client-s3`, four variables and a second set of
credentials, buying portability to any S3-compatible store. The user chose the native
client after seeing both. No specification change was needed, since TD-02b already
records supabase-js for storage only.

Changes to web/.env.example:

- `APP_TIMEZONE` removed. It was dead: the application timezone is a constant in
  src/domain/time because both participants of a sale must see the same time string, and
  the domain layer is lint-blocked from reading process.env. Leaving it implied the zone
  was configurable per deployment, which contradicts why it was fixed. A note in the file
  records this so it is not reintroduced.
- `SUPABASE_SERVICE_KEY` renamed `SUPABASE_SECRET_KEY`, matching Supabase's current key
  naming; `anon` and `service_role` are legacy JWT-based names being deprecated.
- `SUPABASE_STORAGE_BUCKET` split into `SUPABASE_BUCKET_PROFILES` and
  `SUPABASE_BUCKET_LISTINGS`.
- Every variable now carries its reason in a comment, including why there are two
  connection strings and why they are not interchangeable.

No code, tests, commits, pushes or remote changes.

## 2026-10-18 — INV-17 Slice B, database foundation: reviews, two blockers, and process gaps

### Decisions taken by the user

- **Correction-cycle budget is counted per task/batch**, not across INV-17 as a whole. Never
  reset for a new assistant or policy. Slice A used two and is closed; Slice B began with a
  fresh two.
- **Slice B's first correction cycle is retained despite having run without the required
  prior approval**, with the gap recorded rather than buried. See process gaps below.
- **Slice B's second and last correction cycle was approved** for the two security blockers
  plus cheap hardening: TLS, pool limits, the `%GG` try-placement pin, the trailing-dot FQDN
  hole, the redactor over-scrub, five incorrect comments and the `atInPassword` DNS
  assumption.
- **The actor-carrying seam is adopted as an INV-17 design decision** rather than deferred to
  INV-01. With RLS off by design and the Supabase secret key full-access, authorization lives
  entirely in application code, and the foundation currently makes the safe version and the
  catastrophic version look identical at the call site. Recorded as task INV17-B9, not yet
  built.
- **FR-1709 keeps its machinery here and its verification at INV-07.** The transaction helper
  and unique-violation mapping are tested against a fixture table carrying a partial unique
  index; FR-1709 is marked verified only when the real `offers` table lands with a genuinely
  concurrent test. Stated by the assistant, not objected to.
- **The local database setup goes into the repository** as `docker-compose.yml` plus a
  documented path for any PostgreSQL, rather than depending on a server started by hand.

### Coordinator process gaps, recorded at the user's direction

Three required gates were missed on Slice B. All three are also written into the Execution
plan in `workflow/TECHNICAL-SPEC.md`.

1. **No execution plan existed before delegation.** Subagents were briefed from the
   coordinator's own context, so allowed edits, acceptance criteria, approvers and stopping
   conditions were never written anywhere the user could check them against. The plan now
   exists and reconstructs Slices A and B from evidence.
2. **The test-readiness review, INV17-B2, was never run.** The equivalent gate on Slice A
   returned NOT READY with five blocking findings, including one proving by execution that
   the suite's host-timezone guard could not catch the implementation it existed to catch.
3. **The first correction cycle ran without prior approval.** The user had previously
   approved exactly this kind of cycle explicitly.

### What the reviews found

The specification review returned four blocking defects, all by executing adversarial code.
The most serious: the test-isolation guard compared host, port and database, but every
Supabase project's database is named `postgres` and the three endpoint spellings differ in
host and port while reaching identical rows, so a test run could `truncate` and `drop table`
against the production database. The guard was already computing the pooler username, which
embeds the project ref, and discarding it. A test asserted the hole as intended behaviour.

The tester then found the guard failed in **both** directions: two genuinely different
Supabase projects share a pooler host and port, so the guard refused the correct setup while
permitting the dangerous one.

The security review, INV17-B7, the first this feature has had, then found the whole control
**never executes in the documented configuration**. `resolveTestDatabaseUrl` short-circuits
when `DATABASE_URL` is empty, and nothing in the test process loaded `.env.local`, which is
where every other part of the project tells the operator to put it. Demonstrated with a decoy
database: 95 of 95 green with no refusal, and with a role lacking `CREATEDB` the run executed
destructive DDL inside the database named by `DATABASE_URL`. Both earlier rounds of fixing
that guard were reasoning about a control that did not run.

It also found TLS off by default, so the Supabase password and all marketplace data would
cross the public internet in cleartext, and that the obvious remedy is a trap: postgres.js
turns `sslmode=require` into `rejectUnauthorized: false`, giving encryption with no
certificate verification.

### Notable engineering judgements by the workers

- The tester **declined the obvious fix** for the guard, on the grounds that loading
  `.env.local` into the process would put the production `DATABASE_URL` where `getDb()` reads
  it, leaving the suite's own `truncate` one forgotten argument away from production. It
  parses the file into a value instead, checks every candidate rather than the first found,
  and deletes `DATABASE_URL` from the environment after use so a test that forgets a handle
  fails loudly.
- The implementer found that `ssl: resolveSsl(url)` would have silently broken the documented
  `?sslmode=` escape hatch, because postgres.js merges with `k in o` and `'ssl' in {ssl:
  undefined}` is true, so a present-but-undefined key beats the query string. An operator who
  explicitly asked for TLS would have got plaintext.
- A comment asserting more than the code guaranteed shipped in **four consecutive rounds**.
  The reviewer's disproof of the last one is the one worth keeping: the necessity of the cast
  is itself evidence that the sentence beside it is wrong, because if neither branch fired no
  cast would be needed.

### Outstanding decisions for the user

Slice B's correction budget is now exhausted, so each of these needs explicit direction.

1. **A UTF-8 BOM in `.env.local` blinds the isolation guard again.** `util.parseEnv` does not
   strip it; Next.js's own loader does, so the application sees the variable and the guard does
   not. Windows PowerShell writes a BOM by default and the user is on Windows. One line in
   `readEnvFile`. Held below blocking only because reaching the destructive path now also
   requires `HOTSHOP_TEST_ALLOW_SHARED_DATABASE=1`.
2. **The TLS rule duplicated into `scripts/db-check.ts` is pinned by no test.** Removing TLS
   from the script entirely leaves the suite green. This is the one command pointed at real
   Supabase credentials.
3. **An empty `?sslmode=` gives plaintext on a remote host**, because the module defers to the
   query string and postgres.js reads the empty value as falsy.

### Verification status

350 of 350 tests passing in the assistant's cloud container against PostgreSQL 16.15, with
typecheck, lint and build clean. Nothing has run on the user's machine, no Git command has been
executed by any assistant session, and nothing is committed. Branch `codex/nextjs-migration` is
at `7c16bcf` and has never been pushed. The security reviewer's verdict after the correction
cycle is safe to commit.

## 2026-10-18 - Publish INV-17 and attempt INV-01 integration

User authorised path-scoped staging of web, workflow, docs and AGENTS.md, committing
with COMMIT_MSG.txt, pushing codex/nextjs-migration, deleting the scratch message,
and merging that branch into codex/inv01-accounts in the separate INV-01 worktree.
Executed: commit 1761bc1; push succeeded and upstream tracking is configured;
COMMIT_MSG.txt deleted. Ignore check returned web/.gitignore:25 for web/.env.local.
76 files were staged; no .env.local or node_modules paths were staged. A corrected
local environment-value comparison found no values of 16+ characters in the staged
diff. The first comparison used the wrong worktree and is not verification evidence.
No application tests ran; existing test claims remain external reports.

Current handoff: E:/CS3227-2610-MP3 on codex/nextjs-migration is published at 1761bc1.
Untracked pre-existing logs and Claude outputs remain excluded. This record and the
publication task log are local follow-up evidence, not part of that commit.
E:/CS3227-2610-MP3-INV01 remains on codex/inv01-accounts with a merge in progress.
Nine conflicts: AGENTS.md, docs/CLAUDE-HANDOFF.md, docs/DeveloperGuide.md,
docs/InteractionRecord.md, docs/agents/agents/coordinator.md,
docs/agents/issue-tracker.md, workflow/MIGRATION-WORKFLOW.md,
workflow/PRODUCT-SPEC.md, workflow/TECHNICAL-SPEC.md.
Automatic approval review rejected a broad resolution script due to possible loss
of incoming workflow/specification changes. No resolution edits were applied.
Next step: review and resolve each conflict preserving both feature designs and
histories, then verify and commit the merge. INV-01 readiness is not yet established.

## 2026-10-18 — Continue INV-17 from HANDOFF-INV17

User requested takeover from docs/HANDOFF-INV17.md. Read its task ledger/protocol
and checked Git/environment without altering Claude's code or the INV-01 merge.
Corrected current outcome/handoff status: 1761bc1 committed; C1 migration files
untracked; 486 tests/88.23% are Claude cloud evidence only. Reconciled the stale
sessions FK table entry with the already-approved C1 no-FK decision.
Local clean dependency installation failed EUSAGE on missing coverage lock entries.
Recorded ENV-1 and requested approval before corrective edits. Docker/PostgreSQL
commands and .env.test.local absent; asked for dedicated test DB availability.
No database accessed, tests run, production edits, commits or pushes.

Owner approved scoped ENV-1 lockfile repair. Asked whether production DB could be
used for tests; explained that the integration bootstrap has destructive provisioning/
truncation paths and production is excluded. No production DB testing is authorised
by this question. Dedicated test DB remains pending; independent unit checks proceed.

Owner selected local disposable PostgreSQL setup. Official PostgreSQL Windows
download page links EDB binary archives; selected PostgreSQL 17.11 (EDB file
1260616), matching the target major version. Downloading to a dedicated directory
under local application data; no system service or production connection planned.
Test data will be loopback-only with a dedicated local role and per-run databases.

ENV-1 testing agent changed only lockfile (+104 lines; coverage-v8 5.0.5 plus seven
transitives). Clean install passed with scripts disabled; typecheck and lint passed.
Unit baseline: 293 passed, 12 skipped, 1 suite failed on virtual fixture lookup.
Owner approved ENV-2 Windows fixture normalisation only, preserving assertions,
with focused/full unit rerun and independent review. No database tests run yet.

ENV-2 worker normalised PROJECT_ROOT separators in actor-seam-compile.test.ts;
assertions and fixtures unchanged. Focused 12/12 passed; removing only the fix
reproduced the original setup failure; restored full unit baseline 305/305 passed,
8 files, no skips. Independent review pending. No production edits or DB tests.

ENV-2-R passed: independent focused 12/12, unchanged assertions/fixtures, no existing
lock entries changed. Local PostgreSQL 17.11 ready at 127.0.0.1:55432 using
E:/HotShopTestPostgres17/data; C: initialisation failed on WAL rename. Download
used official EDB binary link; no system service installed. ENV-DB test configuration
and single integration baseline delegated next; production database untouched.

ENV-DB local baseline: 9 integration files passed, 1 failed; 164 passed/13 failed.
All failures in db-check-script subprocess suite; harmless direct npm execFile probe
returned ENOENT on Windows. Migrations and remaining suites passed. Owner approved
ENV-3 to launch same npm CLI via Node, only editing that test file, preserving all
assertions and production script; focused/integration reruns and independent review.

ENV-3 launched actual npm command: focused 31 passed/1 failed; reverting invocation
reproduced failure of reachable-database positive control. Remaining success fixture
replaces real local password with synthetic value; authentication correctly fails.
Requested scoped fixture correction, preserving actual test credentials internally
and checking non-disclosure without printing them. No authentication weakening.
Read-only C2 triage found credential-verifier interface, cookie persistence and HTTP
encoding unresolved; lifecycle primitives are separable but full route tests are not
ready. C2-T remains unstarted; no scope silently invented from the handoff.

Owner explicitly approved amended ENV-3 positive-connection password fixture fix
in the same test file, with focused/integration reruns and independent review.

Owner approved bounded internal session slice. Recorded C2-P-v1 internal interface
and C2-P0/PT/PR1/PS/PI/PR2 gates before delegation. Real login routes, credential
verification, cookie lifetime and HTTP mapping remain pending; no full C2 completion
is implied. C2-P starts only after ENV-3-R baseline acceptance.

Amended ENV-3 returned: focused 32/32 and full integration177/177 (10 files) pass.
Both invocation-only and fixture-only revert probes reproduce failures; corrected
file restored. No production/auth edits. Independent ENV-3-R is next before C2-P0.

ENV-3-R independently passed focused32/32 and reviewed invocation/fixture diff;
no blocking findings. Full integration177/177 is tester evidence. Environment
baseline gates now satisfied for C2-P0. User approved the internal slice earlier;
login/routes/cookie persistence/HTTP encoding remain pending.

C2-P0 returned: only sessions.ts API stubs and safe error class, no lifecycle logic.
Lint/typecheck passed; sandbox generated-types write needed elevated rerun. Stub
SHA256 begins6594E2D5. C2-P correction count0/2. C2-PT may now establish red.

C2-PT: one136-line test file, eight cases P1–P6; focused run all8 fail on
lifecycle stubs after real migration/seed success, not imports/setup. Typecheck
and lint passed. Independent readiness review follows; production unchanged.

C2-PR1 ready: independent8/8 behavioural red and P1–P6 coverage confirmed; no
blocking findings. Final review must inspect cryptographic RNG, since format and
distinctness alone do not prove entropy. C2-PS security readiness is next.

C2-PS security readiness passed by inv17_session_security, inspection only; token
boundary/hash storage/error/isolation tests accepted. Actual RNG, parameterised
queries and token-restricted operations remain final checks. C2-PI now eligible;
correction count0/2 and full HTTP scope remains pending.

C2-PI returned: sessions.ts implements the approved internal lifecycle. Focused8/8 and combined coverage run490/490 passed (305 unit,185 integration); source branch88.38%, sessions100%. Typecheck/lint passed; no test edits. Counter0/2. C2-PR2 final evidence review is now eligible; full C2 remains incomplete.

C2-PR2 completed: independent specification/coverage review and security inspection
passed without blockers. Source coverage137/155 branches (88.38%); session module
2/2 branches and all statements/functions covered. Security worker ran two isolated
scratch mutations: removing lookup restriction produced P1/P4 failures (6 pass),
removing deletion restriction produced P4 failure (7 pass). Both were assertion
failures after successful PostgreSQL setup; scratch configuration removed and live
source/test hashes unchanged. No production/test corrections; C2-P count0/2.

Bounded internal slice complete. Updated DeveloperGuide (local database setup and
internal session API), AGENTS pointers, outcome and existing handoff. UserGuide
unchanged because no user-facing journey is delivered. No build/browser/deployment
claim, commit or push. Full C2 needs agreed credential-verifier, persistent-cookie
and HTTP response contracts. Existing INV-01 conflicted merge remains untouched.
