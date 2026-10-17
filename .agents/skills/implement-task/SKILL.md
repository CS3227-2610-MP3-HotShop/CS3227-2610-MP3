---
name: implement-task
description: Execute selected eligible tasks from workflow/PLAN.md under direct user approval or an approved coordinator scope, respecting role ownership, correction approvals, and evidence-based completion.
---

# Implement Task

Execute eligible plan tasks within recorded user authorisation. A request may
select task IDs or unique names, or approve a bounded set/milestone mapped to
specific tasks for coordinator selection. Neither mode authorises extra scope.
Editing this skill does not invoke implementation.

## 1. Establish authority and role

Read applicable repository instructions, `workflow/PLAN.md`, and the referenced
product/technical specifications. Paths are relative to the repository root.
For the migration, read [the migration workflow](../../../workflow/MIGRATION-WORKFLOW.md).
For a role-based assignment, read the
[common contract and assigned role](../../../docs/agents/AgentSpecifications.md).

Record selected task IDs, approved scope and specification versions, assigned
role, allowed files, human approver, verification requirements, and shared
correction-cycle state. A milestone must map to identifiable tasks and acceptance
criteria. Missing authority or an ambiguous scope blocks dependent work.

The coordinator selects and delegates; it never writes application code or tests.
Workers execute their assigned eligible tasks. This skill does not authorise a worker
to start other plan tasks or spawn agents beyond its role's permitted routing.

## 2. Validate eligibility per task

Read each complete task entry, including dependencies, instructions, acceptance
criteria, references, and completion state. Verify that every prerequisite exists,
has `Completed: Yes`, and has supporting evidence. Detect missing, ambiguous,
self-referencing, or cyclic dependencies before dependent implementation.

A blocked task pauses that task and its dependents. Report exact IDs and blockers;
the coordinator may continue independent eligible work already inside approved
scope. A worker may continue only independent work already assigned to it and
must report the partial blocker. If scope, plan, or ownership is globally missing,
all work relying on it remains blocked. Follow repository logging obligations;
logging is not permission to edit the blocked implementation or invent plan entries.

Selecting a prerequisite and its dependent together does not make the dependent
eligible. Complete and verify the prerequisite first; then the coordinator may
dispatch the dependent within the existing approved scope, rechecking eligibility.
A request for one task alone does not authorise its prerequisites or next task.

Completed task entries remain immutable. Changed requirements or corrections to
completed work need a fresh follow-up task through planning, linked to the original
entry. The follow-up must be explicitly included in authorised scope; a correction
plan can identify and authorise it. Do not silently reopen entries or create tasks
as a workaround inside this execution skill.

## 3. Resolve gaps at the appropriate level

Inspect relevant code, tests, interfaces, architecture, and existing conventions;
preserve user edits. Use judgment for names, private helpers, and comparable local
choices within approved behaviour and design. This is not permission for unrelated
refactoring or changes to public APIs, architecture, dependencies, or requirements.

For material ambiguity or a specification conflict, pause affected work and give
the coordinator or human approver the task, disputed requirement, evidence, and
impact. Preserve partial work and leave the task incomplete. Independent approved
work may continue where it does not share the blocker or overlapping files.

Migration corrections confined to one assigned area use that area's human owner.
Shared-interface or cross-area changes require both owners' explicit agreement,
recorded against the contract and tasks before edits. Missing ownership blocks
affected assignments. Reviewer disagreements always go to the human approver(s)
with both positions and specification/test evidence; agents do not adjudicate them.

## 4. Execute the assigned TDD phase and ownership

- Testing tasks: the testing agent writes tests/fixtures within its assignment and
  records meaningful behavioural failures. Compiler/setup failures are not red
  evidence. Report already-passing behaviour honestly; never manufacture failure.
- Test readiness: obtain the specification/coverage review before the corresponding
  production task. Resolve affected findings through the correction gate below.
- Production tasks: the implementer edits production code and runs tests, but never
  edits, deletes, disables, or weakens tests, fixtures, or test configuration.
  Suspected test defects return to the assigned testing agent with evidence and
  coordinator visibility. Route via the coordinator if direct invocation is unavailable.
- Review tasks: reviewers may run permitted checks but cannot edit code or tests.
  Review implementation and coverage after production work; perform security review
  against the stable final candidate as defined by the role specifications.

Required documentation and logs are part of the task's
repository obligations; coordinate ownership of shared files. A separately planned
out-of-scope prerequisite is still a blocker, not implicit implementation authority.

## 5. Gate failed-loop corrections

Expected initial TDD red permits the approved initial green task after readiness
review. For any unexpected failed review/verification loop requiring code or test
edits, stop those edits and preserve failure evidence. Diagnosis and permitted
reproduction may continue. Do not automatically fix even an obvious in-scope defect.

The coordinator drafts a concrete change plan identifying requirements, failed
snapshot, checks/results, cause or uncertainty, proposed edits and owners, impacts,
verification, paused/continuing work, and cycle count. Obtain explicit approval from the appropriate human
owner(s) before implementation; silence and original feature approval do not suffice.
Further edits outside the approved plan require revised approval.

Use one shared maximum of two correction cycles per reviewed task/batch across
readiness, tests, production, implementation review, and final security review.
A cycle is an approved fix set plus re-review; initial scheduled assessments do
not consume a cycle. Preserve the count across phases, agents, and follow-up tasks
for the same corrective work. At exhaustion, pause unresolved affected work and ask
for explicit direction; change-plan approval alone does not extend the limit.

Never disable checks or weaken valid tests to claim completion. Classify unrelated
failures and environment limitations honestly without fixing outside approved scope.

## 6. Verify and record acceptance

Assess each criterion using actual evidence and run applicable verification for
the assigned phase and stack. Migration test layers and completion gates are in
the migration workflow; Java-source checks continue to apply to Java changes.
Report intended red results as expected failures, not passing suites. If mandatory
checks conflict with a planned red task, clarify the conflict rather than bypassing
checks or implementing an unassigned green task.

A worker handoff does not establish final feature acceptance. Resolve the reviews
and integrated checks required by the task/feature before claiming that level of
completion. Preserve distinctions between test readiness, implementation readiness,
and final reviewed feature completion.

Workers return task-completion evidence to the coordinator and do not edit plan status. The coordinator alone records completion/progress in the shared plan. Immediately before recording completion, the coordinator re-reads task scope and dependencies.
Update only the selected task's `Completed:` section with `Yes`, date, and concise
acceptance/check evidence when every criterion and required gate is satisfied.
Keep all task definitions and previous completed entries unchanged. Failed,
unverified, ambiguous, or blocked criteria retain `Completed: No` with partial
progress and outstanding actions. Coordinate writes to the shared plan file.

## 7. Report and progress

Return task IDs, role, specification/snapshot references, changes, actual checks,
criterion evidence, review findings, approvals, cycle state, blockers, and next
action. Record timing and available usage; otherwise `N/A`.

The coordinator reports progression and may select the next eligible task within
approved scope. A worker returns its handoff and awaits another assignment. Scope
expansion, altered requirements, and external actions such as deployment retain
their existing authorisation requirements.

### Interpreting baseline evidence

Apply the common contract's test-first evidence policy per criterion. Missing or
incorrect behaviour needs meaningful behavioural red before production changes.
Already-satisfied behaviour requires implementation-path and passing-test evidence
confirmed by the specification reviewer; green alone is insufficient. Unexpected
passes must be investigated. Setup failures stay unverified; classify mixed tasks
separately and never use source JavaFX checks as proof of target Next.js behaviour.
Preserve the approved task scope, all review gates and correction approvals.
