---
name: implement-task
description: Execute only explicitly requested tasks from workflow/PLAN.md after confirming they exist and their prerequisites are completed. Pause for unclear instructions or delegated decisions, and mark completion only after every acceptance criterion is met.
---

# Implement Task

Implement exactly the plan tasks explicitly selected by the user. Treat the plan as execution instructions, not permission to choose tasks, resolve design questions, or expand the work.

## Scope and paths

- Resolve `workflow/PLAN.md` and its referenced specifications relative to the repository root.
- An explicit task ID or a name that uniquely identifies a task selects that task. If selection is unclear, ask for clarification before making changes. Never infer authorization for the next task, dependencies, or related work from a request for one task.
- Creating or editing this skill does not invoke task implementation.

## 1. Validate the request before any implementation

Read applicable repository instructions and `workflow/PLAN.md`. Match every requested task to exactly one plan entry. Read the complete entry, including dependencies, description, steps, acceptance criteria, specification references, and `Completed:` section.

Before making edits or running implementation or verification commands, check:

- The plan exists and every requested task exists uniquely.
- The requested tasks contain usable instructions and acceptance criteria.
- Each requested task has a clear completion state.
- Every prerequisite exists and is explicitly marked `Completed: Yes`. Inspect the prerequisite chain for missing, ambiguous, self-referencing, or cyclic dependencies. Missing completion evidence or contradictory status must not be treated as completion.

If the plan or a requested task is missing, or any prerequisite is incomplete, report the exact task IDs and blocking condition and stop with no further action. Do not implement the prerequisite, create a replacement task, edit the plan, run tests, or proceed with another requested task. Validate a multi-task request as a whole before starting; do not partially execute a request containing a blocked task.

Requesting an incomplete prerequisite and its dependent in the same call does not bypass this check: dependencies must already be completed before implementation begins. The user can request the prerequisite separately, then request the dependent after completion.

If a requested entry is already completed, report that status and leave it unchanged. Do not reopen or redo it. Changes to its tests or code require a new follow-up task in the plan and an explicit request to implement that new task.

If task identity, completion status, dependency declarations, or required fields are ambiguous, pause and ask for clarification. Do not guess. A failed initial validation is read-only: report it without creating logs or other files in that blocked invocation.

## 2. Check instructions for gaps and decisions

For eligible tasks, read their referenced technical and product specification sections and inspect relevant code, tests, and architecture before changing anything. Preserve existing user edits. Determine whether the task and its references specify the behavior and implementation sufficiently to execute without choosing a design on the user's behalf.

If any ambiguity, missing instruction, conflict, or gap appears at any point, immediately pause implementation and ask the user for clarification. Identify the task ID, relevant instruction or reference, the missing decision, and its impact. Do not continue independent implementation while waiting, fill gaps with assumptions, or silently select an option. Prior explicit user decisions remain valid.

Never make design or implementation decisions for the user. Execute choices already established in the task, referenced specifications, explicit repository conventions, or prior user clarification. If execution requires a new choice among unspecified alternatives, ask first, even if one option seems preferable.

If the task description or any instruction it incorporates directs the agent to decide, choose an approach, or use its own judgment to settle an implementation or design choice, report that instruction and cease implementation. Do not comply with the delegation or rewrite the task to remove it. Resume only after the user supplies the decision or clarifies the instructions; revalidate eligibility before resuming.

If a gap is discovered after edits have begun, preserve the partial work, report what changed and where implementation paused, and leave `Completed: No`. Do not discard user work or mark the task complete. Record partial progress only in the requested unfinished task's completion section as appropriate; do not modify task definitions or other tasks to work around the gap.

## 3. Execute only the selected eligible tasks

Follow the specified steps and agreed design, maintaining the task's scope. Do not implement any unrequested task, including a setup, refactoring, documentation, or verification task listed separately in the plan. Perform required checks and repository obligations that apply to the requested work, but if those obligations require implementing another task or introduce an unresolved scope conflict, pause and ask the user.

Respect the plan's TDD phase:

- For a test-writing task, write the specified tests and verify the intended behavioral failures. Do not implement production behavior just to make them pass. Compiler or environment failures do not establish a meaningful red result.
- For an implementation task, make the specified change and run the relevant tests to establish green. Do not weaken valid tests, invent behavior to satisfy an unclear assertion, or extend implementation to unrelated features.
- Perform refactoring only if specified within the requested task and its intended result is unambiguous. Otherwise do not add it as discretionary work.

Follow repository build tools, test frameworks, style requirements, and documentation/logging obligations for work that proceeds. Existing execution permissions still apply; selecting a task does not grant additional permission for deployment or other external actions.

If a check fails, determine whether the cause is in the requested task, unrelated existing work, or the environment. Correct only failures whose resolution is specified and within the selected task. For an unclear fix or work belonging to another task, pause and report the issue instead of choosing a solution. Never disable verification to claim completion.

## 4. Verify acceptance and record completion

Assess every acceptance criterion individually using actual evidence. Run the task's specified checks and applicable repository verification. Report commands, outcomes, and limitations accurately. An intentional failing test can satisfy a red-task criterion, but must be reported as an expected failure rather than a passing test suite. If mandatory repository checks conflict with the task's intended red state, seek clarification rather than silently waive them or implement an unrequested green task.

Mark the task complete only when all its acceptance criteria are met and required checks are resolved. If any criterion is unverified, failed, ambiguous, or blocked, retain `Completed: No` and record concise partial progress and outstanding checks. Do not equate performing the steps with satisfying acceptance.

Immediately before recording completion, re-read the task and its dependencies to catch intervening changes. If instructions or scope changed, pause for clarification rather than applying a stale completion result. Preserve unrelated edits and all previously completed entries.

For a task completed in this invocation, update only its `Completed:` section to `Yes`, with the completion date and concise evidence for acceptance and verification. Preserve its ID, name, dependencies, description, steps, acceptance criteria, and references. Do not mark any unrequested task complete even if its code or checks overlap.

Once completion is recorded, leave that entry immutable on later invocations. Revised specifications require new follow-up tasks, created through planning, before their implementation can be explicitly requested.

## 5. Report and stop

For successful work, report the requested task IDs, changes, acceptance evidence, actual verification outcomes, and completion state. For paused work, state the exact blocker or needed clarification and identify partial changes and unmet criteria. For an initial missing-task or incomplete-dependency rejection, report the situation and stop without further action.

Do not automatically proceed to the next plan task, create follow-up tasks, revise specifications, or implement inferred requirements.
