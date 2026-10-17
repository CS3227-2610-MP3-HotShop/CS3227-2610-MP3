# MP3 migration workflow

Status: workflow policy agreed through the recorded user discussions; setup
publication/closure is tracked by #2–#7. Feature implementation still requires its
own approvals and prerequisites. Reconciled 2026-10-17 (SGT).

Tracking: [W1: workflow and completion gates](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/3).
Parent: [migration workflow setup](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/2).

This is the authoritative record of migration collaboration decisions. It is
not the product or technical specification and does not authorise feature
implementation merely by being read. Preserve the existing role specifications
in [the role index](../docs/agents/AgentSpecifications.md).

## Setup and discovery issues

| Work | Issue | Status at creation |
| --- | --- | --- |
| Agree workflow and completion gates | [#3](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/3) | Discussion started; approval pending |
| Align roles and skills | [#4](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/4) | Awaiting workflow decisions |
| Configure agents | [#5](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/5) | Awaiting instruction alignment |
| Establish repository tracking | [#6](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/6) | Awaiting policy decisions |
| Validate workflow | [#7](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/7) | Awaiting role/config readiness |
| Inventory JavaFX features | [#8](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/8) | Initial documentation pass recorded; source/test verification pending |

Issue links track ongoing progress; the table records the starting state. No
owners, due dates, labels, or Project board were assigned during publication.

## Agreed migration and collaboration rules

Agreed on 2026-10-16. Use the MP3 repository as the migration source because it
contains the JavaFX implementation and additional specifications. Freeze an
exact reference commit before extracting the feature inventory. Target all
existing features in a TypeScript/Next.js application developed on a dedicated
migration branch. Existing accounts, records, and images do not need transferring;
the web application starts with demo data. The later 2026-10-17 decision supersedes
the interim resettable proposal: hosted PostgreSQL from the start, no application
reset feature, and idempotent provisioning seeds. See the current policy below.

### Parallel ownership

Frontend and backend may proceed simultaneously against an agreed interface.
Human ownership is currently unassigned and must be recorded before implementation.
The frontend owner covers pages, components, forms, navigation, and browser
interaction. The backend owner covers server endpoints, business rules,
authorisation, and persistence. Both participate in end-to-end integration.

Use separate checkouts and short-lived feature/side branches targeting the
migration integration branch. The proposed integration branch is
`codex/nextjs-migration`; it now exists locally. Remote publication must be checked
before opening implementation PRs against it.
Designate one owner at a time for shared configuration, dependencies, schemas,
and contract files. Coordinators must not assign overlapping edits independently.

### Shared interfaces and approvals

Before parallel implementation, agree on request and response shapes, field
semantics, validation, error codes, authentication behaviour, and relevant
acceptance criteria. Frontend mocks must match that contract. Mock-backed
behaviour is not evidence that backend integration is complete.

Each human owner may approve a correction plan confined to their assigned area
that preserves the agreed contract and requirements. A shared-interface change
requires explicit agreement from both owners before implementation. Examples
include changing field names or units, required inputs, response structures,
error codes, pagination, or how sessions are sent and checked.

Present the proposed contract change, affected features and tasks, impact on both
sides, and verification plan. Record both owners' agreement in the linked issue
or versioned change plan; update the specification and affected tasks before
implementation. Direct communication between owners is encouraged, but agents
must have a recorded decision rather than infer consent from silence.

The existing user-approved correction-plan gate, shared two-cycle correction
limit, testing-agent ownership, and reviewer-disagreement escalation remain in
force. For migration tasks, the assigned human owner is the approver for their
area; cross-area or shared-contract corrections involve both owners. Unassigned
ownership blocks affected implementation, not unrelated approved work.

### Feature tracking and integration

Use a parent feature issue with separate linked GitHub issues for frontend, backend, and integration work
where those require separate tracking. Issues identify owners, blockers, and
progress; specifications define requirements and contracts; the implementation
plan retains executable task IDs and evidence. Link these artifacts instead of
maintaining competing requirements. Actual feature issues follow source discovery;
setup and discovery issues are now posted; feature issues follow discovery and GitHub Projects is deferred; feature/task/PR templates are published on the workflow branch in PR #9.

Use the [task template](../.github/ISSUE_TEMPLATE/task.md) for frontend, backend, and integration issues; link each task to its feature parent and implementation-plan IDs.
Each frontend/backend task that is split out has its own issue, owner, dependencies,
status, acceptance criteria, and linked PR. The parent records feature behaviour,
specification links, overall acceptance criteria, and child issues. Create a separate
integration issue when the integration work is substantial. Small features may remain
one issue when splitting adds no useful ownership or tracking. Close the parent only
after all required child issues and feature-level acceptance checks are complete.

#### Task stages and flags — agreed 2026-10-17

| Stage | Meaning |
| --- | --- |
| Backlog | Captured work not yet started; detail or approval may be pending. |
| Ready | Approved task scope, owner, acceptance criteria and relevant contracts are recorded; prerequisites are resolved. |
| In progress | Authorised work has started. |
| Review | Work and verification evidence are submitted for required review or acceptance. |
| Done | Task acceptance criteria and required checks, reviews and handoffs are satisfied. |

Paused and Blocked are separate flags, not replacement stages. Paused means work
is deliberately on hold; Blocked means a dependency or unresolved decision prevents
progress. Record the reason, linked blocker/decision, responsible person where known,
and condition for resuming. Retain the stage so work can be resumed from its actual
position. Flags may coexist when both reasons apply; clearing one does not clear the
other. Recheck readiness and approval before resuming. A flag or stage change does
not grant authority, reset correction cycles, or reopen completed plan entries.

Parent feature completion still requires all required tasks and integrated feature
acceptance; task stage changes alone do not complete a feature. These conventions
are documented locally; Stage, flag and triage labels are configured; GitHub Projects is explicitly deferred; current tracking uses issues and labels.

#### Incremental issue planning — agreed 2026-10-17

Create feature issues with known behaviour and acceptance criteria, marking proposals
and open questions explicitly. Use the [feature template](../.github/ISSUE_TEMPLATE/feature.md).
Specifications, owners and task links can be pending at issue creation. Add separate
task issues as concrete frontend/backend/integration work emerges; an exhaustive task
list is not an initial requirement. Record why newly discovered tasks are needed and
their dependencies. Changes to approved behaviour, scope or shared interfaces require
the applicable human approvals. Only a task about to start needs sufficiently detailed,
approved requirements, ownership, contracts and prerequisite evidence. This does not
waive the inventory-first gate, correction approvals, or final feature acceptance.
Keep completed-task history intact and use authorised follow-up tasks where required.

Approve the interface, implement both sides against it, and integrate small slices
frequently. Each PR records its task and acceptance references, actual checks,
review results, documentation updates, and limitations. A feature is complete
only after its end-to-end acceptance and required verification pass; completing
both side-specific tasks alone is insufficient. Changes to requirements or shared
interfaces must be reflected in the linked specifications and task records.

Workflow setup is being developed on `codex/migration-workflow`, based on the
existing specs branch. Worker TOML files are written and the user-supplied smoke test reports four successful custom-type launches. Custom-type launches were reported by the user; the generic-dispatch exercise
validated its documented scenarios. Native loading after edits remains unverified.
The source baseline is frozen at 1d812ef; migration implementation remains pending. The Java-specific rules elsewhere describe the current source
application; target-specific implementation rules must be established before
Next.js implementation begins.


## Specification approval cadence — agreed 2026-10-17

Inspect and review the complete feature inventory first. Obtain the user's
approval of its coverage before moving into detailed feature specifications.
Then work through detailed specifications feature by feature, using the agreed
inventory to preserve full migration scope and identify dependencies.

An approved inventory establishes scope, not permission to implement unspecified
behaviour. Each implementation assignment still requires approved detailed
requirements, relevant shared foundations and interfaces, and eligible tasks.
Resolve uncertain source behaviour with evidence and user input. Do not silently
defer features or infer approval from an issue or board status.

## Data foundations — superseding decision 2026-10-17

Hosted PostgreSQL is required from initial delivery, including separate databases
for development, automated tests and demo use. Tests must never use demo data.
The app has no reset feature. Fresh accounts and available listings are seeded by
an idempotent provisioning step; no existing JavaFX data import is required.
Ordinary recreation of isolated development/test databases is distinct from an
application reset. Inject an application clock for domain time boundaries.

This replaces the earlier resettable/interim-storage agreement. Define provisioning,
seed content, images, permissions and database integrity before dependent work.
Concurrent web users replace the desktop global session/single worker; preserve
atomic operations and ownership rules with target-appropriate mechanisms.
See the current approved product scope and draft technical specification for details.

## Remaining implementation prerequisites

1. Source baseline is frozen at 1d812ef. Later source changes require a proposed
   re-baseline with a diff and explicit approval.
2. Approve affected detailed specifications and shared contracts before implementation.
3. Establish exact target runtime, dependencies, commands and environment provisioning;
   stack choices in the decision record are not evidence of installed tools.
4. Assign human owners and one writer per shared file before implementation assignments.

## Feature completion evidence — agreed 2026-10-17

- Approved acceptance criteria linked to source evidence and specifications.
- Relevant positive, negative, boundary, and permission tests.
- Test-readiness and implementation/coverage reviews, followed by final security
  review as required by the agreed role workflow.
- Integrated browser checks of the complete user journey with the real backend.
- Passing configured lint, type-check, automated-test, and production-build checks.
- Updated docs/UserGuide.md, docs/DeveloperGuide.md, and AGENTS.md for the delivered feature.
- Updated feature-level [SDD interaction summary](../docs/agents/SDDInteractionSummary.md), linking decisions, approved specs, plan tasks, corrections, actual verification and task logs.
- Linked issue, plan tasks, PR, actual verification results, and unresolved limits.

Exact commands and deployment checks will be defined from the selected target
setup; none have been executed or validated by creating this draft.

## Testing layers — agreed 2026-10-17

Use unit, integration, and Playwright browser tests selectively. Cover observable
behaviour and relevant failures, boundaries, and permissions at the lowest useful
level; do not repeat every case in all three layers.

| Level | Coverage responsibility | Example |
| --- | --- | --- |
| Unit | Business rules, validation, calculations, and state transitions | Whether sale cancellation is direct or needs agreement |
| Integration | Server operations, storage effects, authorisation, and atomic multi-entity behaviour | Accepting an offer reserves the listing, creates a sale, and rejects competing offers |
| Playwright end-to-end | Critical user journeys through actual frontend/backend integration and important browser-specific risks | Buyer makes an offer, seller accepts, and both see the sale |

Each feature requires appropriate unit/integration coverage; justify cases where
a layer does not apply. Maintain a focused browser suite across the critical
journeys: registration/login, creating/searching listings, offer acceptance,
completion/cancellation, messaging, and meetup booking. Scope exact scenarios
against approved feature criteria rather than treating this list as a test count.

Provide deterministic seed/reset support and independent test state. Tests must
not depend on another test or a human's earlier actions. Browser mocks can support
frontend development but do not establish integrated feature completion.
Run storage integration checks against isolated PostgreSQL test databases, never
the demo database. Schema/transaction/permission verification remains required.

The testing agent owns tests and fixtures. The implementer owns production code.
The specification/coverage reviewer checks whether the selected layers provide
sufficient evidence. Preserve the existing approval and correction-cycle rules.

Exact test runner choices, dependencies, commands, CI execution, and reset controls
remain target-setup decisions. This policy does not claim that tools are installed
or any tests have passed.

## Verification and completion of this workflow document

The workflow decisions and completion gates were agreed incrementally with the
user. Setup closure records those approvals and evidence; it does not require
repeating the discussion. Remaining prerequisites above block dependent feature
implementation, not acceptance of the workflow policy. Documentation checks alone
do not validate agent behaviour.

## Source confirmation and documentation pass — 2026-10-17

The user confirms all MP2 functionality is present in MP3 and directs discovery
to its User Guide and Developer Guide. The [initial feature inventory](FEATURE-INVENTORY.md)
records local HEAD 93dbfc63126a69874dc53f8c59d822aa58ac8c72 and documented scope.
This is not an independently verified repository-parity or complete feature audit.
Code/test evidence and web-adaptation decisions remain pending.

## Documentation consistency check — 2026-10-17

The four SDD skills and five role briefs were reviewed together. See the
[audit record](../docs/agents/WorkflowConsistencyCheck.md) for findings,
corrections, and remaining setup gates. This is a static documentation review;
no agents were installed, spawned, or behaviourally tested.

## PR and feature documentation gates

Use [.github/PULL_REQUEST_TEMPLATE.md](../.github/PULL_REQUEST_TEMPLATE.md).
Feature Done requires both guides, agent instructions and the feature-level SDD
interaction summary to be updated alongside acceptance and review evidence.
Intermediate task PRs may link outstanding feature documentation tasks and owners;
they must not claim the parent feature is complete. Non-feature changes may explain
why user-facing documentation does not apply. Per-task logs remain required.
## Baseline test evidence

Follow the [common contract](../docs/agents/AgentSpecifications.md) for the explicit
missing-versus-existing behaviour policy. Require meaningful red for missing or
incorrect behaviour, or independent reviewer confirmation of implementation-path
and passing-test evidence for each already-satisfied criterion. Green alone does
not waive readiness, integration, security, documentation or final acceptance gates.

## Tracker setup

Use [MP3 tracker instructions](../docs/agents/issue-tracker.md) for repository,
stage/flag/triage meanings and branch conventions. The user selected issues and labels on 2026-10-17; GitHub Projects is deferred.
The earlier token-scope limitation no longer blocks setup. Issue #6 records tracker
publication evidence; parent #2 tracks setup completion.
