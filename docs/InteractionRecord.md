# Project decisions and interactions

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

- Target workspace: E:/CS3227-2610-MP3. The originating chat is attached to MP2; use a fresh MP3 project chat for configuration discovery.
- Working branch: codex/migration-workflow; verify before changing files.
- Written: migration workflow, guide-derived inventory, aligned SDD skills, five development role briefs, four worker TOML configs, and startup guidance.
- Checked: documentation links/whitespace; supplied smoke test reports four custom-type launches. The disposable generic-dispatch exercise observed initial handoffs, failure approval, one actual repair cycle and final reviews; isolated/native loading and unexercised paths remain unverified.
- Current saved role instructions were read by exercise workers through generic dispatch. This does not revalidate native custom-config loading after instruction edits.
- Current task: review and commit the combined workflow diff on codex/migration-workflow; no push is authorised in this task. F1-F4 have documentation corrections; F2 now explicitly distinguishes missing behaviour from independently verified existing behaviour. The covered disposable exercise is now complete; see docs/agents/WorkflowExercise.md for observations and unexercised scenarios. Feature/task/PR templates and stage conventions are written locally; GitHub board/label setup remains pending. Issue #7 exercise results and remaining limits are recorded in docs/agents/WorkflowExercise.md; no remote issue update was performed.
- Later: verify the complete feature inventory against code/tests and obtain approval before detailed feature specifications and implementation.
- Pending: human owners, demo-state design, web runtime/test/build commands, source baseline freeze, and feature approvals.
- Workflow changes remain local and uncommitted; published history is unchanged.
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
