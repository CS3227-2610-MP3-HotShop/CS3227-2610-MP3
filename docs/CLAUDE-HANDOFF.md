# Migration handoff to Claude

Prepared 2026-10-17 at the user's request to continue with another assistant.

## Start here

Work in E:/CS3227-2610-MP3, not the older MP2 checkout. Read AGENTS.md first.
Read this handoff, docs/InteractionRecord.md (current handoff and latest entries),
workflow/FEATURE-INVENTORY.md and workflow/FEATURE-EVIDENCE.md. Then read
workflow/MIGRATION-WORKFLOW.md and docs/agents/AgentSpecifications.md before work.
Use the detailed guides/source only as needed; avoid repeating the completed audit.

User prefers a back-and-forth process, one feature/specification decision at a time.
Report actions and progress transparently. Do not infer approval from silence.

## Exact repository state

- Repository: https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3
- Working branch: codex/migration-workflow.
- HEAD: 1d812ef270d780d248c74f7a37c543345df70026 — Establish migration workflow and agent roles.
- That workflow commit is local; it was not pushed by this chat.
- Before this handoff, modified files: AGENTS.md, docs/DeveloperGuide.md,
  docs/InteractionRecord.md, workflow/FEATURE-INVENTORY.md.
- Before this handoff, untracked files: workflow/FEATURE-EVIDENCE.md and
  logs/2026-10-17-feature-inventory-evidence.md. Preserve them.
- This handoff adds this file and logs/2026-10-17-claude-handoff.md, and updates
  the three required instruction/guide/decision records. It does not commit or push.
- Recheck actual status on arrival. Do not reset, discard or overwrite local work.
- Windows Git needed command-scoped safe.directory=E:/CS3227-2610-MP3 in this
  environment. Do not change global Git configuration just to work around that.

## Current stopping point and immediate next action

All 16 existing JavaFX feature groups were traced to implementation paths and
representative test assertions. The full list was presented to the user, who had
NOT yet approved its coverage when this handoff was written.

Superseded on 2026-10-17: after a guide-derived recheck and the addition of INV-17, the
user approved coverage of the 17 groups. That approval covers the feature list only.
The user also froze the migration source baseline at 1d812ef; a later JavaFX source
change needs a proposed re-baseline with a diff and explicit approval before it affects
scope. The next step is detailed specifications, one feature at a time.

Resume by briefly presenting/referencing the 16 groups and asking whether that
coverage is approved. Do not repeat the entire code audit or start coding. Confirm
whether the inspected commit should be the frozen migration source reference;
record later-source-change handling rather than silently accepting new source.
Once inventory coverage is approved, discuss detailed specifications one feature
at a time, starting with a user-agreed feature (accounts is a reasonable proposal).
Product/technical specs and executable task plans have not yet been created for
the migration. Inventory approval alone does not authorise implementation.

Update 2026-10-17 (Claude session): repository state was rechecked by reading .git
refs and the reflog, because this session has no shell on the workspace machine and
executed no Git command. Branch codex/migration-workflow is at 1d812ef, unpushed, and
the uncommitted file set listed above is intact.

A guide-derived recheck of the 16 groups found nothing to remove and one gap: session
lifecycle, data lifecycle, transactional consistency and the refusal-message standard
have no inventory ID. INV-17, platform and data foundations, is proposed and undecided.

The user decided that the web application no longer keeps one user logged in at a time.
Concurrent multi-user access replaces that desktop constraint, and local-only decisions
are open to revision rather than inherited. Session mechanism, cross-user update
visibility, concurrency control, simultaneous-viewer read state and the browser
equivalent of unsaved-change protection remain undecided.

The user also decided that the desktop minimum window size of 960 x 640 and the fixed
JavaFX layout units are not carried over. The web layout adapts to arbitrary viewport
sizes; breakpoints and acceptance viewports remain a specification detail.

## Feature coverage

01 accounts; 02 profiles/password/images; 03 search/filter/sort; 04 listing display;
05 create/edit/photos; 06 listing management/archive/delete; 07 buyer offers;
08 seller offers; 09 sales/purchases/completion; 10 cancellation;
11 meetup slots/booking; 12 meetup changes; 13 messaging lifecycle;
14 conversation list/unread/actions; 15 seller dashboard; 16 shared UI behaviour.

The evidence document contains source paths, named existing tests, boundary rules,
interactions, and limits. No whole feature group was added or removed by the audit.
Important unresolved distinctions:

- ListingService.getListing allows any logged-in user to fetch any existing listing
  by ID, including unavailable/archived ones; search hides them. Choose the web
  detail-access policy explicitly, not from assumed participant-only navigation.
- Meetup UI uses quarter-hour starts and fixed durations. Services accept broader
  valid intervals; UI/service expiry and pending-move cancellation rules also differ.
  Record intended web rules explicitly. See evidence for precise differences.
- Opening Chat with seller does not persist an empty conversation; the first message
  or offer does. HotShop_Architecture contains stale wording on that and meetup closure.
- Sale snapshot freezes title, description, condition and agreed price, not all fields.
- Wishlist, notifications, password recovery, account deletion and four documented
  future enhancements are not automatically parity requirements.
- Separate MP3 assignment requirements are not certified by this existing-feature audit.

## Agreed migration scope

MP3 contains the MP2 JavaFX implementation and is the source of truth. Migrate all
existing features to TypeScript/Next.js for web/Vercel delivery. Use a separate
migration branch; codex/nextjs-migration was proposed but has not been created.
Fresh demo data is approved; existing accounts/records/images need not be imported.
Superseded 2026-10-17: the web app has no reset feature and uses hosted PostgreSQL from
the start; data persists and seeding runs once at provisioning. Seed content is seeded
accounts plus a spread of listings, with no seeded offers, sales or meetups.
Database choice, seed provisioning, sessions, concurrent-update visibility, images,
timezones, exact runtime/dependencies/test/build commands and human owners remain
undecided.
The two-day delivery is an aspiration, not proof the full scope fits that deadline.

Frontend/backend owners may work in parallel against an approved shared contract.
Each approves their own area; both must explicitly approve shared-interface or
cross-area changes. Assign human owners and shared-file writers before execution.

## Workflow and role rules

- Coordinator delegates production/test work and consolidates reports/docs; does not
  implement production code or tests. Tester owns tests/fixtures/test configuration;
  implementer owns production and assigned usage/design/setup documentation.
- Specification reviewer checks test readiness, then implementation/coverage and
  final documentation. Security reviewer checks the stable final candidate.
- Missing/incorrect behaviour needs meaningful behavioural red before production.
  Already-correct behaviour needs implementation-path and passing-test evidence,
  independently reviewed. Setup failure is unverified, not red; never manufacture red.
- Unexpected failed-loop code/test changes require a concrete correction plan and
  explicit approval BEFORE corrective edits, even within existing requirements.
  Initial expected TDD red is normal initial work.
- Maximum TWO shared correction cycles per task/batch across stages/agents/followups;
  new IDs/restarts do not reset count. Exhaustion requires explicit human direction.
- All reviewer disagreements go to the human with both positions and evidence.
  Pause affected/dependent work only; continue independent authorised work.
- Implementer routes suspected test problems back to tester; no direct test editing.
- Coordinator alone updates PLAN completion/progress from evidence. Completed entries
  stay immutable; authorised follow-up work retains applicable correction budget.
- One writer per shared file at a time; explicit transfer. Workers supply task logs.
- Unit/integration tests selectively, Playwright for critical real frontend+backend
  journeys. Mocks and legacy Java tests do not prove integrated web completion.

Detailed roles: docs/agents/agents/ and docs/agents/AgentSpecifications.md.
Aligned skills: .agents/skills/manage-product-spec, manage-technical-spec,
plan-implementation and implement-task. Read relevant SKILL.md before that stage.

## Documentation and project management

Every agreed decision updates docs/InteractionRecord.md, including discussion-only
turns. Keep current handoff current; preserve historical records. Completed changes
update AGENTS.md and docs/DeveloperGuide.md. Feature Done additionally requires
actual User Guide updates, feature SDD summary, test/review evidence and integration.
Use docs/agents/SDDInteractionSummary.md and a fresh task log under logs/.

Use feature parent issues; separately tracked frontend/backend tasks have their own
issues, with integration issue if substantial. Plan incrementally; unknowns can be
Pending until the affected task approaches execution. Approved detail, owners,
contracts and prerequisites are required before starting. Backlog/Ready/In progress/
Review/Done are stages; Paused/Blocked are separate flags with reasons/resume gates.
Templates exist in .github/ISSUE_TEMPLATE and .github/PULL_REQUEST_TEMPLATE.md.

Last known remote state (not refreshed for this handoff): PR #1 is the older agent
specifications PR; issues #2 parent setup, #3 workflow gates, #4 roles/skills,
#5 configuration, #6 issue conventions, #7 disposable exercise, #8 feature inventory.
No feature issues or GitHub board/labels were configured here. Exercise findings
have not been posted to #7. Do not infer closure from local completion evidence.
Current request authorises handoff only, not publishing/merging or creating issues.

## Verification already performed and limits

- Workflow commit reviewed on standards and specification axes; stale current-status
  summaries corrected and rechecked, no outstanding review findings at commit time.
- Historical disposable synthetic exercise: 24 tests, controlled failure, approved
  one-line repair and independent reviews; one actual correction cycle. Scripted
  shared-interface/exhaustion checks are not actual two-human approval/two cycles.
  See docs/agents/WorkflowExercise.md. No app implementation resulted from it.
- Custom worker launch was user-reported in another MP3 chat. Actual exercise here
  used generic dispatch reading saved instructions; native loading not revalidated.
- Feature audit used three read-only workers and coordinator consolidation. Source
  unchanged between earlier93dbfc6 and current1d812ef for inspected application paths.
  All32 cited test method names exist;105 relative links resolve; whitespace passed.
  Tests/assertions were READ, not executed. No Java build/manual walkthrough/web test.
- Codex .codex/agents/*.toml are client-specific. Do not assume Claude loads them.
  Read portable Markdown roles and explicitly map available Claude capabilities;
  if enforcement/delegation differs, report it rather than claiming equivalence.
- Do not run Java tests for this handoff. For future Java changes use Java25,
  Gradle9.1.0 wrapper and JUnit5; choose web commands during target setup.

## Scope hygiene

Personal non-project material was separated from this repository and should stay
outside current project documents. The user accepted existing published history;
do not rewrite history or restore removed material. MP2 is a separate older working
checkout with unrelated changes; leave it untouched.
