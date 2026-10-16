---
name: plan-implementation
description: Create or update workflow/PLAN.md from workflow/TECHNICAL-SPEC.md with ordered TDD tasks, dependencies, and completion tracking. Use when planning implementation or revising remaining work across sessions while preserving completed tasks.
---

# Plan Implementation

Translate the technical specification into concrete tasks a coding agent can execute in order. Use test-driven development (TDD), explicit dependencies, and observable completion criteria while preserving the agreed design and scope.

## Scope and paths

- Resolve paths relative to the repository root. Read `workflow/TECHNICAL-SPEC.md` and create or update `workflow/PLAN.md`. Leading slashes in these paths mean repository root, not filesystem root.
- Planning does not implement the tasks. Do not write application tests or production code, install dependencies, run migrations, or deploy unless separately requested.
- Do not create a plan when the request is to create or edit this skill itself.

## Workflow and execution alignment

For migration work, read [the migration workflow](../../../workflow/MIGRATION-WORKFLOW.md).
Use its approved full inventory before detailing features. Plan implementation
only from approved feature details and required shared foundations; unresolved
features remain visible as blockers rather than silently disappearing from scope.
For role-based work, read the [role contract](../../../docs/agents/AgentSpecifications.md)
and align handoffs with [implement-task](../implement-task/SKILL.md).

Record the bounded task set proposed for approval and the actual authorisation
reference when supplied. Planning, issue creation, and readiness do not grant
execution authority. Within approved scope, the coordinator can select eligible
tasks as prerequisites finish; workers receive explicit assignments.

## 1. Establish the source and implementation baseline

Read the technical spec in full, including its referenced product requirements, acceptance criteria, constraints, and open questions. Read relevant parts of `workflow/PRODUCT-SPEC.md` to verify traceability when available. If the technical spec is missing, ask the user to establish it before deriving tasks; do not invent a design from a feature prompt.

Read applicable repository instructions, relevant architecture documentation, and any existing plan. Inspect affected code, interfaces, data structures, migrations, configuration, dependencies, and related tests to distinguish required work from capabilities that already exist. Use repository evidence for existing file paths and commands; label intended new files as new.

When resuming an existing plan, read each task's `Completed:` section before making changes. Preserve completed entries as historical records and plan only remaining work and necessary new follow-up tasks. A completed entry records satisfaction of its original acceptance criteria, not automatic compliance with later specifications.

Record the source spec's available revision marker and status. Identify the requested implementation scope rather than assuming every historical requirement must be rebuilt. Preserve source requirement and acceptance IDs; use section links and clear labels if IDs are absent without pretending new IDs exist in the source.

Ask the user about ambiguities, contradictions, incomplete technical decisions, missing referenced requirements, or discrepancies between the design and repository constraints. Explain the affected tasks and wait for clarification before finalizing them. Continue planning independent work where useful. Do not silently redesign the feature to make it easier to plan.

If unresolved decisions affect the implementation, save a draft with explicit blockers. Do not turn unresolved decisions into ordinary executable tasks that require the coding agent to choose product behavior. Record readiness per task or bounded task set so an unrelated blocker does not prevent independent approved work. Mark the entire plan ready only when all its source alignment and blocking decisions are resolved.

## 2. Decompose the design into tasks

Derive tasks from the technical spec's implementation mechanisms and verification strategy. Include applicable data model and migration work, service/API contracts, business rules, security enforcement, UI integration, failure handling, documentation, and required final checks. Do not add speculative features or unrelated refactoring.

Choose tasks small enough to have a clear result and be verified independently, without splitting every line of code into a task. Prefer incremental behavior slices that can complete a red-green-refactor cycle. Name the affected components and expected artifacts so the coding agent does not need to rediscover the design.

Map each in-scope requirement and acceptance criterion to implementation and verification tasks. Every task must have a source requirement, technical-spec section, or applicable repository obligation. Existing fulfilled requirements can be recorded as verified baseline rather than scheduled as new implementation.

### Parallel frontend/backend tasks

Split a feature into frontend, backend, and integration tasks where each has an
independent deliverable. Both implementation streams depend on the approved
shared contract and required foundations, not automatically on each other.
Frontend may use agreed contract fixtures while the backend is built. Integrating
the real frontend and backend depends on both; fixture-only tests do not satisfy
feature integration acceptance.

Record each task's area, role, allowed files, human approver, issue reference, and
contract/specification version. Unassigned owners may remain explicit during
planning but block affected implementation. Assign one owner at a time to shared
configuration, dependency manifests, schemas, contracts, and plan-file updates;
use dependencies or serial scheduling where edits overlap.

Area-local correction plans go to that area's human owner. Shared-interface or
cross-area changes require both owners' recorded agreement and updated contracts
before implementation. Preserve accepted requirements when dividing the work.

## 3. Order work using TDD and explicit dependencies

The first task should almost always write the relevant automated tests for the requested feature or change. Those tests should express specified observable behavior, relevant equivalence partitions, negative conditions, and boundaries. For a bug fix, begin with a regression test reproducing the defect.

Plan the following cycle for each behavior slice:

1. **Baseline evidence**: Write or extend relevant tests before production changes. For missing/incorrect behaviour, confirm a meaningful behavioural failure. For already-satisfied criteria, record implementation-path and passing-test evidence for independent reviewer confirmation under the common contract; never manufacture red.
2. **Test-readiness review**: The specification/coverage reviewer checks the test snapshot and coverage before production work.
3. **Green**: The implementer makes the smallest design-consistent production change that passes the tests. Depend explicitly on the test task and resolved test-readiness review.
4. **Refactor and verify**: Improve the changed code only where needed, preserving passing behavior, then run the appropriate regression and style checks. Include this as steps within the implementation task or a separate task when it has a meaningful independent outcome.

For role-based work, assign test writing and test corrections to the testing
agent; implementers may run but never edit tests or fixtures. Add post-implementation
specification/coverage review and final security review of a stable candidate.
Create explicit review/integration tasks when they have independent acceptance
criteria. Feature completion must depend on these final gates, not just the
production task's green tests. Reviewers run permitted checks without editing code.

For migration features, plan appropriate unit and integration coverage plus
Playwright checks for critical journeys or browser-specific risks. Keep edge cases
at the lowest useful layer, justify inapplicable layers, and include deterministic
seed/reset support where required. Do not claim PostgreSQL correctness from tests
against demo storage. Resettable initial delivery does not authorise missing
marketplace behaviour or silently decide the demo-state implementation.

Do not force all tests for the entire feature to be written before any implementation; preserve test-first ordering within each slice. Later tests may depend on an earlier completed slice, but tests for a slice must precede production behavior for that slice.

If test execution genuinely requires missing infrastructure, a minimal prerequisite setup task may come first. Explain why it is necessary, keep it free of feature behavior, and make the first test task depend on it. A prose-only change or behavior that cannot reasonably be automated can use a documented manual check. Record a specific justification for each exception instead of casually bypassing TDD.

Where a new contract does not yet exist, identify the minimal declaration or test harness needed to run tests. Do not accept compiler errors alone as proof that a behavioral regression test works. Plan runnable behavioral failures after minimal scaffolding, followed by implementation. Never plan to disable tests or weaken valid assertions to reach green.

Assign stable IDs such as TASK-001. List direct prerequisites by task ID, or `None` when there are none. Explain in the task description what each prerequisite must deliver before work can begin. Treat a dependency as complete only when its acceptance criteria have been met, not merely because its steps were attempted.

Order the task entries so prerequisites appear before dependents. Ensure the dependency graph has no cycles, missing IDs, duplicate IDs, or self-dependencies. External prerequisites and user decisions belong in a separate blockers list, not as invented task references. Use dependencies only for genuine prerequisites; tasks can still be listed in a recommended sequence without artificial coupling.

## 4. Write workflow/PLAN.md

Create the directory and file if absent. Include a short overview with scope, source-spec links and revision markers, revision date, plan readiness, and relevant implementation constraints. Explain how to follow the ordered tasks and require satisfied dependencies before proceeding.

Include a compact coverage table mapping requirement or acceptance references to their implementation and verification task IDs. Then write tasks in execution order, with these fields for every entry:

```markdown
### TASK-001 — Task name

- **Task ID:** TASK-001
- **Name:** A concrete action and result
- **Task dependencies:** None, or a list of prerequisite task IDs
- **Specification references:** Technical-spec sections and relevant requirement/acceptance references
- **Area / execution role / allowed files:** Frontend, backend, shared, or integration; assigned role and edit boundaries
- **Human approver:** Named area owner(s), or explicitly unassigned and blocked for implementation
- **Tracking / contract:** Issue reference and contract version, or N/A with reason
- **Readiness / authorisation:** Blockers and approved task-set reference; readiness alone is not approval

**Description**

State the purpose, affected components or intended new files, expected artifacts,
and scope. For each dependency, explain what it must deliver before this task can
begin. Distinguish planned work from anything already implemented.

**Steps**

1. Give a concrete operation the coding agent should perform.
2. Include the relevant test or verification command/check and expected outcome.
3. State any necessary handling of failure or recovery specified by the design.

**Acceptance criteria**

- List observable conditions that establish completion of this task.
- Tie behavior to source acceptance criteria and identify relevant checks.

**Completed:**

No
```

Replace the template guidance with task-specific content; do not leave generic instructions as the generated plan. Use checkboxes or concise bullets for acceptance criteria. A test-writing task is complete when its criteria have meaningful tests and verified expected red for missing behaviour, or reviewer-confirmed passing evidence for already-satisfied behaviour. Classify mixed tasks per criterion and retain the separate readiness review gate. An implementation task requires its specified outcomes and passing checks. Do not require passing feature tests as acceptance for the red task.

Every new task must include a `Completed:` section initialized to `No`. The coordinator alone records completion/progress updates; workers return evidence without editing plan status. The coordinator changes it to `Yes` only after all of that task's acceptance criteria are satisfied, recording concise completion evidence and the completion date in the same section. Partial progress or interrupted verification leaves it at `No`; record useful progress and blockers there so another session can resume. Creating a plan does not itself complete its tasks.

State commands using the repository's actual build tool, wrapper, test framework, and conventions. Explain expected outcomes instead of claiming commands have been executed. Prefer focused checks during each slice and full required verification at the end. Include documentation and logging tasks when repository instructions require them, plus migration or packaging checks only when applicable.

Add an **Open Questions and Blockers** section when needed. State which tasks are affected, what answer or external condition is required, and why the plan is not ready. Planned deployment or external actions must retain applicable authorization requirements; a plan entry does not itself authorize execution.

## 5. Update and verify the plan

For updates, preserve unrelated content, stable task IDs, and recorded progress. Do not mark tasks complete based on speculation. Once a task is marked `Completed: Yes`, do not edit, delete, reopen, renumber, or rewrite any part of that entry in subsequent calls to this skill, including its status, dependencies, steps, acceptance criteria, references, and completion evidence. Keep completed entries unchanged even when the specifications change.

If changed specifications or approved corrective work require revising tests or code associated with a completed task, create a new task with a fresh ID and `Completed: No` instead. Describe the changed requirement, link the original task, and specify the necessary edits or revalidation and new acceptance criteria. Preserve TDD by planning new or revised tests before the corresponding production changes. The completed task remains a historical record; the new task carries the current obligation.

For failed-loop corrections, link the concrete approved change plan and human
approval to the affected tasks. Planning a proposed correction is not permission
to implement it. Carry the same task/batch correction identifier and cycle count
into follow-up tasks: two cycles are shared across test readiness, tests, production,
implementation review, and security review. New IDs do not reset the budget.
At exhaustion, preserve the outstanding work and request explicit direction.
A new follow-up task still needs inclusion in authorised scope before execution;
retain completed entries unchanged and update current traceability outside them.

Update unfinished task entries and the plan's current coverage table or overview as needed. If an unfinished task requires revised behavior from a completed prerequisite, make it depend on the new follow-up task rather than treating the original completed entry as sufficient. Do not add new prerequisites to completed entries. Do not reuse retired IDs for different tasks. For older plans with missing or unclear completion markers, consult the user about completion status rather than guessing; preserve entries already explicitly recorded as completed.

Re-read the technical spec and target plan before finalizing, reconciling intervening edits. Review the written plan and diff to check:

- All in-scope design mechanisms and acceptance criteria are covered without extra scope.
- Every task includes its ID, name, dependencies, description, steps, acceptance criteria, and a `Completed:` section, except preserved legacy completed entries that predate this format.
- New tasks start at `Completed: No`; existing completed entries are unchanged, and specification changes affecting them are covered by new tasks with fresh IDs.
- Dependency IDs resolve, prerequisite results are explained, and the graph is acyclic and ordered correctly.
- Tests and readiness review precede production; post-implementation review, security review, and integration gates are included where required.
- Role/file ownership, human approvers, contract references, authorisation, and blockers are explicit; shared edits are coordinated.
- Corrections reference approval and preserve the shared cycle count across follow-up task IDs.
- Test tasks specify meaningful red for missing/incorrect behaviour or reviewer-confirmed baseline passing evidence per already-satisfied criterion; implementation and final verification require their specified passing checks.
- Paths, interfaces, tools, and checks match repository evidence or are clearly identified as planned additions.
- No task hides an unresolved product or technical choice, and source references are current.

Consult the user if this review exposes ambiguity or discrepancies. Follow applicable repository documentation and interaction logging requirements. A prose-only plan does not demonstrate passing application tests; report checks actually performed accurately.

Finish by linking `workflow/PLAN.md`, summarizing the task sequence and important dependencies, stating readiness, and identifying blockers or TDD exceptions. Do not begin executing the plan unless the user's instructions already authorize implementation.
