---
  layout: default.md
  title: "Developer Guide"
  pageNav: 3
---

# HotShop Developer Guide

---

## Introduction

Welcome to the HotShop Developer Guide. This guide introduces the architecture,
codebase, and development workflow of HotShop, a JavaFX desktop marketplace with
local SQLite storage.

Start with [Development Workflow](developerGuide/DevelopmentWorkflow.html) to set
up your environment, run checks, and build the application, including selecting
the JavaFX platform for Intel and Apple Silicon macOS JARs. Then read
[Architecture Overview](developerGuide/ArchitectureOverview.html) to understand
how the application is organised. Use the sidebar to explore each topic in detail.

## Purpose of this Guide

The main objectives of this guide are to:

1. Orient developers to the codebase structure, application lifecycle, and
   responsibilities of each architectural layer.
2. Explain the [JavaFX UI](developerGuide/JavaFXUI.html),
   [Service Worker](developerGuide/ServiceWorker.html),
   and feature services.
3. Provide a reference for environment setup, dependencies, development checks,
   packaging, and engineering tools.
4. Record the product's [requirements](developerGuide/Requirements.html) and the
   steps for [manual testing](developerGuide/ManualTesting.html).

Proposed features and enhancements are documented separately in
[Future Work](developerGuide/FutureWork.html).

### Development verification

The full test suite takes more than ten minutes on a typical laptop, mostly
because each test account's password is hashed with 600,000 PBKDF2 iterations.
Run the targeted checks below while developing and the full suite before
committing.

Targeted development checks:

```powershell
.\gradlew.bat test --tests hotshop.service.AccountServiceTest
.\gradlew.bat test --tests hotshop.service.ProfileImageTest
.\gradlew.bat test --tests "hotshop.service.Listing*" --tests hotshop.service.PublicListingsTest
.\gradlew.bat test --tests hotshop.service.OfferServiceTest
.\gradlew.bat test --tests hotshop.service.TransactionServiceTest
.\gradlew.bat test --tests hotshop.service.MeetupServiceTest --tests "hotshop.model.Meetup*"
.\gradlew.bat test --tests hotshop.service.ChatServiceTest --tests hotshop.model.ConversationTest --tests hotshop.model.MessageTest
.\gradlew.bat test --tests hotshop.storage.ImageStorageTest
.\gradlew.bat test --tests hotshop.ApplicationRuntimeTest --tests hotshop.database.DatabaseTest
```

### Product specification workflow

Use the `manage-product-spec` agent skill in
`.agents/skills/manage-product-spec/SKILL.md` to clarify a new product or proposed
change before design and implementation. It interviews the user, resolves
material ambiguities, and creates or updates `workflow/PRODUCT-SPEC.md` with
agreed requirements, UI expectations, acceptance criteria, and scope. Unresolved
product decisions are recorded in a draft. Using the skill does not implement
the feature or authorize later workflow stages.

For migration work, approve the complete evidence-backed feature inventory first,
then detail features individually. The product spec records per-feature status,
source references, acceptance IDs, and approval evidence. A partially drafted
document may contain approved features; no draft section silently becomes approved.

### Technical specification workflow

Use the `manage-technical-spec` agent skill in
`.agents/skills/manage-technical-spec/SKILL.md` after establishing the product
specification. It reads `workflow/PRODUCT-SPEC.md` and creates or updates
`workflow/TECHNICAL-SPEC.md` with requirement traceability, data and interface
contracts, security mechanisms, failure handling, and planned verification.
Ambiguities and discrepancies are referred to the user before dependent design
decisions are finalized. Unresolved blockers keep the document in draft status;
the skill does not implement the design.

The technical spec records per-feature design readiness, shared-contract versions,
frontend/backend responsibilities, and both owners' agreement to interface changes.
It defines the resettable demo's state lifecycle and deployment assumptions while
keeping PostgreSQL integration deferred. Verification plans include the agreed unit,
integration, and Playwright layers. Unresolved shared foundations block dependents;
independent approved designs can proceed to planning. Readiness is not permission
to implement. These skills are updated instructions, not completed specifications.

### Implementation planning workflow

Use the `plan-implementation` agent skill in
`.agents/skills/plan-implementation/SKILL.md` to turn the technical specification
into `workflow/PLAN.md`. The plan lists ordered tasks with IDs, prerequisites,
descriptions, steps, and acceptance criteria, traced to the specification.
Tests normally come first, followed by implementation and verification for each
behavior slice. Necessary test setup and manual-check exceptions are explained
explicitly. Unresolved design decisions keep the plan in draft status; planning
does not execute the tasks.

Each task has a `Completed:` section for progress across sessions. New tasks
start at `No`; execution changes this to `Yes` after the task's acceptance
criteria are satisfied. Subsequent planning preserves completed entries
unchanged. Specification changes requiring edits to their code or tests receive
new follow-up tasks with fresh IDs.

Planning now records task-level readiness, human approvers, role/file ownership,
issue/contract references, and bounded authorisation separately. Frontend and
backend tasks may proceed against an approved shared interface; final integration
depends on both. The plan includes test-readiness, implementation/coverage, and
final security reviews plus the agreed test layers. Corrective follow-up tasks
retain the original shared correction budget and require approval before execution.
These are instruction changes; no implementation plan or runtime verification has
been produced by editing the planning skill.

### Task implementation workflow

Use the `implement-task` skill at `.agents/skills/implement-task/SKILL.md` for
selected plan tasks or coordinator assignments inside an explicitly approved
bounded scope. Eligibility is checked per task: incomplete prerequisites block
affected work, while independent approved work may continue. Dependent tasks are
dispatched only after prerequisite completion evidence is available.

The skill permits minor local choices within approved behaviour and design.
Material decisions go to the human owner; shared-interface changes require both
owners' agreement. Testing agents own tests and implementers own production code.
Failed-loop edits require an approved correction plan and share the two-cycle
limit across all review stages.

Completed plan entries remain immutable; corrections to completed work require
authorised follow-up tasks. Task completion requires actual acceptance evidence,
and final feature completion additionally requires its specified review and
integration gates. Role/skill alignment is documented; the scoped disposable
exercise and remaining validation limits are recorded below.

### Agent configuration and interaction records

[Agent configuration](agents/AgentConfiguration.md) maps four worker
TOML files to their role briefs and provides main-chat startup instructions for
the coordinator. The user-supplied smoke-test report confirms all four custom types launched; isolated instruction loading remains unverified. Scoped generic-dispatch behavioural
validation is recorded in the exercise report. Models and permissions inherit from the
parent; review instructions do not create a separate sandbox.

Maintain the [project decision record](InteractionRecord.md) alongside task logs. Repository records contain project-relevant decisions and outcomes; personal conversation records stay outside the repository.

Update the project decision record whenever a decision is agreed, including discussion-only turns. Maintain its current handoff with the workspace/branch, completed and unverified work, blockers, and next authorised step. Preserve earlier decisions and explicitly mark replacements.

### Documentation upkeep

Every completed feature or change includes updates to both
[AGENTS.md](../AGENTS.md) and this Developer Guide before completion is reported.
AGENTS.md retains the instructions and documentation pointers future agents need;
this guide explains the resulting behaviour, design, setup, and workflow, with
relevant verification and limitations. Update the existing sections so they
reflect the current implementation. Record detailed interaction history in a
separate task log under `logs/`.

This applies to application changes, agent configuration, documentation changes,
and newly agreed workflow rules. Explanatory conversations without a completed
change do not require edits. User-facing changes also update the User Guide.

### Migration collaboration workflow

The [migration workflow](../workflow/MIGRATION-WORKFLOW.md) records agreed scope,
frontend/backend ownership and approval rules, shared-interface handoffs, and
integration requirements. The full document is still under discussion in
[issue #3](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/3).
Use its issue links for setup/discovery tracking and its open-decision section
before starting dependent work. Setup issues are published; feature discovery has started with a documentation pass. Worker configuration files are written and four custom types launched in the supplied smoke-test report. Generic-dispatch behaviour was exercised; native loading after instruction edits and migration implementation remain unverified or pending.
The agreed sequence is to review and approve the full feature inventory first,
then develop detailed specifications feature by feature. The inventory establishes
scope; implementation still requires approved details and satisfied prerequisites.
See the [initial feature inventory](../workflow/FEATURE-INVENTORY.md) for documented
feature groups, deferred functionality, and evidence still requiring verification.
Initial delivery may use resettable demo data; durable storage follows in the
PostgreSQL phase. The technical specification will define shared demo state, seed
data, image handling, and reset behaviour before storage-dependent implementation.
The agreed testing policy combines focused unit tests, server/storage integration
tests, and Playwright coverage of critical integrated journeys. Use independent
seeded test state and avoid repeating every edge case in each layer. Feature
completion also requires the agreed reviews, configured checks, and documentation;
see the workflow for the authoritative gates. Tools and commands are not yet configured.

A [workflow consistency audit](agents/WorkflowConsistencyCheck.md)
records the cross-skill/role checks and corrections. Java checks apply to the
JavaFX source; web test/build commands remain target-setup decisions. Document
consistency does not establish agent runtime behaviour or feature correctness.

The implementer may edit production code and explicitly assigned documentation/evidence;
tests, fixtures and test configuration remain tester-owned. Its TOML now matches
the existing role brief. The updated instructions were exercised through generic dispatch; native configuration loading has not been revalidated.
See the agent configuration document for smoke-test provenance and limitations.

### Feature and task issues

Use a feature parent issue for behaviour, specification links, acceptance criteria,
and linked task issues. Split frontend/backend tasks are separate GitHub issues
with owners, dependencies, status, acceptance criteria, and linked PRs. Substantial
integration work may have its own issue; small features can remain one issue.
Close the parent only when required tasks and feature-level acceptance checks are
complete. This structure is agreed; feature and task templates are written locally. Task stage conventions are agreed; the PR template is also written locally.
The [feature issue template](../.github/ISSUE_TEMPLATE/feature.md) supports incremental
planning: known outcomes, draft criteria, open questions, and later task discovery.
Owners/specs/task links may be pending when an issue is created. Before implementation,
the selected task must have approved detail, ownership, relevant contracts, and resolved
prerequisites. Newly discovered changes to agreed scope or interfaces retain human
approval gates. The template is local; no GitHub issues were created by adding it.

The [implementation task template](../.github/ISSUE_TEMPLATE/task.md) is shared across
frontend, backend, and integration issues. It links the parent, specs and plan IDs;
records ownership, acceptance, dependencies, verification and approval; and collects
actual completion evidence. Fields can be pending at creation. Readiness is checked
before implementation, and correction cycles remain shared across related tasks.
Task stages are Backlog, Ready, In progress, Review, and Done. Paused denotes a
deliberate hold; Blocked denotes an unresolved dependency or decision. Both are
flags preserving the task stage, with a reason and resumption condition recorded.
See the migration workflow for stage gates. The task template contains these fields;
GitHub Project fields and labels have not yet been configured.
### Feature completion documentation

A feature is Done only after the User Guide, this Developer Guide, AGENTS.md, and
its [SDD interaction summary](agents/SDDInteractionSummary.md) are updated, alongside
required acceptance and review evidence. The summary links specifications, decisions,
plan tasks, human approvals, corrections, verification and per-task logs. Maintain one
summary per feature under logs/; keep the running decision record for cross-task handoff.

The [PR template](../.github/PULL_REQUEST_TEMPLATE.md) asks for those links and separates
intermediate task completion from feature completion. Intermediate PRs identify any
outstanding documentation owner/task rather than asserting complete feature delivery.

### Documentation ownership and review

The [common role contract](agents/AgentSpecifications.md#documentation-ownership)
assigns document writers and reviewers. Implementers update usage/design/setup
guidance; the coordinator consolidates instructions, workflow and SDD/decision records.
The coordinator serialises shared-file writes and checks task-log completeness.
The specification reviewer independently assesses final guides, instructions and
SDD traceability, including coordinator-authored content, before feature completion.
Workers may write only their assigned logs/reports beyond normal role scope;
explicitly read-only work returns report content without writing files.

The coordinator alone maintains shared plan completion/progress fields from worker
reports and required acceptance/review evidence. Workers return evidence without
editing plan status. Feature issues explicitly collect both guide updates, AGENTS.md,
the SDD summary, task logs and independent documentation-review evidence before closure.

### Test-first evidence and existing behaviour

Tests establish approved behaviour, not an artificial red-green ceremony. Missing or
incorrect behaviour requires a relevant behavioural failure before production edits.
Already-correct behaviour can use passing tests, but the specification reviewer must
confirm the implementation path, meaningful assertions and applicable edge cases.
Record baseline, criterion IDs, actual commands and outcomes. Unexpected passing tests
require investigation; setup failures are unverified. Classify mixed cases separately.
Legacy JavaFX tests do not establish that the web implementation works. Existing
review, approval, integration, documentation and task-completion gates still apply.

### Disposable workflow validation

The [exercise report](agents/WorkflowExercise.md) records the isolated cart-total
exercise, including test-first handoffs, independent documentation/security reviews,
a deliberately introduced failure, explicit human approval and one repair cycle.
The final suite passed 24/24 tests; no application source/tests were added to MP3.

Stale current counters in the plan/SDD summary were caught and corrected. Keep those
current values aligned at handoffs while preserving historical stage evidence.
Workers used generic dispatch with saved role instructions; native loading and
broader application/runtime properties remain outside this exercise's evidence.

## Acknowledgements

See [Acknowledgements](developerGuide/Acknowledgements.html) for the sources of
reused or adapted ideas, code, documentation, tools, and third-party libraries.
