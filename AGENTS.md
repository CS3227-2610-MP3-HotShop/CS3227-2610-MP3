# AGENTS.md

This file defines the repository-wide instructions that AI coding agents must follow when working on HotShop.

These instructions apply to all files in this repository unless a more specific instruction file exists for a particular directory.

---

## Project Overview

HotShop is a Java-based e-commerce application.

Users can interact with the application primarily as buyers or sellers.

Core functionality includes:

- Creating user accounts;
- Authenticating existing users;
- Creating listings for items that are offered for sale;
- Viewing available listings;
- Managing functionality associated with buyers and sellers.

Do not assume that a feature exists merely because it is described as part of the intended product. Inspect the current implementation before modifying or documenting functionality.

---

## Project Specifications

The Java/Gradle/JUnit versions, Java source directories, Java coding conventions,
and Gradle verification commands below apply to the existing JavaFX application.
For the TypeScript/Next.js migration, use the approved migration testing policy
and target setup. Record exact target commands and source locations before
implementation; do not apply Java-only tooling to TypeScript changes.
- Java version 25
- Gradle version 9.1.0
- Testing framework: JUnit 5

Always use the Gradle Wrapper when executing Gradle tasks:

```bash
./gradlew <task>
```

Do not assume that the system-installed Gradle version matches the project's required Gradle version.

Do not downgrade Java or Gradle versions to solve compatibility problems.

---

## Repository Structure
Respect the existing repository structure.

Application source code belongs under:
```
src/main/java/
```
Application resources belong under:
```
src/main/resources/
```
Tests belong under:
```
src/test/java/
```
Test resources belong under:
```
src/test/resources/
```
Project documentation belongs under:
```
docs/
```
Agent interaction logs belong under:
```
logs/
```
Do not create new top-level directories unless they are required by the task or
explicitly requested by the user.

Do not reorganize existing packages or directories merely because another structure appears preferable.

---

## Before Making Changes
Before modifying the repository:
1. Read the user's request completely.
2. Read the specifications relevant to the user's request in `HotShop_Architecture.md`
3. Inspect the files relevant to the requested change.
4. Inspect related tests.
5. Inspect relevant interfaces, models, controllers, services, or other
dependencies before modifying their behaviour.
6. Check existing conventions in nearby code before introducing a new pattern.
7. Determine the smallest reasonable set of files that must be changed.

Do not begin by creating new classes when existing classes may already provide the required functionality.

Prefer modifying or extending the existing design over introducing parallel implementations.

---

## Scope of Changes
Make only changes necessary to satisfy the user's requested task.

Do not:
- perform unrelated refactoring;
- rename unrelated classes or methods;
- reformat unrelated files;
- change public APIs unnecessarily;
- introduce speculative functionality;
- create abstractions solely for possible future requirements;
- delete existing functionality without explicit justification;
- modify unrelated tests merely to make a build pass.

If an unrelated problem is discovered, report it separately rather than fixing it as part of the current task unless it prevents completion of the requested work.

---

## Migration collaboration

For configured worker names and coordinator startup, read
[AgentConfiguration.md](docs/agents/AgentConfiguration.md). Files alone
do not prove instruction loading or behavioural validation; use the recorded smoke-test evidence and remaining readiness gates. Record external test reports with their provenance and distinguish them from checks executed in this chat.

For authorised task execution, use the aligned
[implement-task skill](.agents/skills/implement-task/SKILL.md). It supports
coordinator selection within approved scope and per-task blocker handling;
completed entries remain immutable and corrective edits require explicit approval.
For task decomposition, use the aligned
[planning skill](.agents/skills/plan-implementation/SKILL.md) to record ownership,
contract dependencies, review/integration gates, and separate readiness from approval.
Use the aligned product/technical specification skills for inventory-first discovery
and feature-level approval. Shared contracts require both area owners; unresolved
shared foundations block dependent designs and implementation, not unrelated work.

Before assigning frontend/backend migration work or approving corrections, read
[the migration workflow](workflow/MIGRATION-WORKFLOW.md).
Review and obtain approval of the complete feature inventory before detailing
features individually; inventory approval alone does not authorise implementation.
Inventory coverage was approved on 2026-10-17 for the 17 groups, feature list only.
The migration source baseline is frozen at commit 1d812ef. Specify behaviour as it
exists at that commit. If the JavaFX source changes later, propose a re-baseline with a
diff and obtain explicit approval; do not specify or implement against newer source
without it.
Use .github/ISSUE_TEMPLATE/feature.md for feature issues and .github/ISSUE_TEMPLATE/task.md for linked frontend/backend/integration tasks. Task creation and assignment do not authorise implementation. Record unknowns and add tasks incrementally; require approved detail and prerequisites for the task about to start, without demanding exhaustive upfront decomposition.
Use Backlog, Ready, In progress, Review, and Done for task stages. Keep Paused and Blocked as separate flags with reasons and resumption conditions; follow the migration workflow for transition gates.
Track split frontend/backend tasks as separate GitHub issues linked to a parent feature; follow the migration workflow for ownership and parent completion gates.
Use [the feature inventory](workflow/FEATURE-INVENTORY.md) to track guide-derived
features and their evidence status. Consult workflow/FEATURE-EVIDENCE.md for the
pinned source paths, representative assertions and unresolved source ambiguities.
Static assertion inspection is not a passing-test result or inventory approval.
Verify documented claims against code/tests
before calling them implementation-verified; distinguish future work from parity.
Assign human owners before implementation. Each owner approves correction plans
within their area; shared-interface changes require both owners' explicit
agreement before implementation. Record approvals against the affected contract
and tasks. Existing correction limits and test ownership remain in force.
Superseded on 2026-10-17: the web application has no reset feature and uses a hosted
PostgreSQL database from the start. PostgreSQL is used in every environment including
automated tests; each environment has its own database, and tests never run against the
demo database. Dropping and recreating a development or test database is ordinary
practice, not the reset the application excludes. Domain timestamps come from an
injectable application clock, never from the database clock or a column default, or the
meetup and dashboard time boundaries cannot be tested. Seeding is an idempotent
provisioning step, not an application route. Reset was a local-disk concern of the JavaFX app and
has no web equivalent. Data persists; seeding runs once at provisioning rather than on a
schedule or on demand. Fresh demo data is still approved, so existing accounts, records
and images are not imported. Preserve functional state and access rules; specify the
seed content and the provisioning path before implementing storage-dependent tasks. Do
not plan interim in-memory or ephemeral server state: on a serverless host it disappears
between deployments and cold starts, which users experience as an uncontrolled reset.
The web target supports concurrent multi-user access; the desktop single-logged-in-user
constraint and its single-worker serialisation are not carried over, so database
integrity constraints protect the marketplace invariants instead. The desktop minimum
window size and fixed layout units are not target criteria. Platform and data
foundations are tracked as INV-17 in the feature inventory. Do not reintroduce a
superseded desktop constraint because a guide chapter still describes it; the affected
User Guide wording changes with its feature.
For migration features, follow the workflow's agreed unit/integration/Playwright
testing policy and completion gates. These target-stack rules supplement the
Java-source checks; executable Next.js commands remain to be established.

## Architecture and Design

Follow the architecture already established in the repository.

Before introducing a new architectural pattern, inspect how similar existing functionality is implemented.

Maintain clear separation of responsibilities.

In particular:
- UI code should primarily handle presentation and user interaction.
- Business rules should not be embedded unnecessarily in UI classes.
- Persistence concerns should remain separate from presentation concerns.
- Domain objects should represent application concepts and enforce appropriate domain invariants.
- Authentication and authorization checks should not depend solely on UI
visibility or navigation restrictions.

Avoid creating unnecessary layers such as additional managers, services,
repositories, factories, or utility classes unless they solve a concrete
design problem.

Prefer simple designs that satisfy the current requirements.

---

## Java Coding Standards
All Java code must follow the [SE-EDU Java Coding Standard](https://se-education.org/guides/conventions/java/intermediate.html)

The configured Checkstyle rules are the executable source of truth for
automatically enforceable style requirements.

When existing code demonstrates an additional consistent convention not covered by Checkstyle, follow the existing convention.

General Java Guidelines
- Use descriptive class, method, field, and variable names.
- Keep methods focused on a single responsibility.
- Avoid excessive method length and deeply nested control flow.
- Avoid duplicated logic.
- Prefer clear code over unnecessarily clever code.
- Avoid magic numbers and unexplained string literals where a named constant is appropriate.
- Use the narrowest appropriate visibility.
- Avoid mutable global state.
- Do not leave commented-out code.
- Do not leave debugging output such as temporary System.out.println
statements.
- Remove unused imports and variables.
- Handle exceptional conditions deliberately rather than silently ignoring them.

Do not suppress compiler or Checkstyle warnings simply to make verification pass.

---

## Dependencies

Before adding a new dependency:
1. Check whether the required functionality can reasonably be implemented using Java's standard library or an existing project dependency.
2. Verify compatibility with Java 25 and Gradle 9.1.0.
3. Prefer actively maintained and established libraries.
4. Add only the dependency modules actually required.

Do not introduce a dependency merely to avoid implementing trivial
functionality.

Do not remove or upgrade unrelated dependencies unless required by the task.

Any significant new dependency should be mentioned in the final task summary.

---

## Documentation

### User Documentation
All user-facing features added to this application must be documented in:
```
docs/UserGuide.md
```
Documentation for user-facing feature should include, where applicable:
- an explanation of the feature;
- instructions for using the feature;
- example use cases;
- expected outcomes;
- relevant restrictions;
- warnings or important behaviour the user should know.

Documentation must describe the actual current implementation.

Do not:
- document functionality that has not been implemented;
- describe planned behaviour as existing behaviour;
- invent commands, screens, buttons, error messages, or workflows;
- claim that functionality has been tested when it has not.

When an existing user-facing feature changes, update its existing User Guide documentation as part of the same task.

### Feature completion record

Before declaring a feature Done, update docs/UserGuide.md, docs/DeveloperGuide.md, AGENTS.md, and its feature-level SDD interaction summary. Follow docs/agents/SDDInteractionSummary.md and link supporting task logs. Intermediate task completion does not waive parent-feature documentation gates.

### Developer Documentation

For every completed feature or change, update both `AGENTS.md` and
`docs/DeveloperGuide.md` as part of the same task, before reporting completion.
This includes changes to agent configuration, workflow, and documentation.

- In `AGENTS.md`, maintain the relevant durable instructions, constraints, or a
  concise pointer to the updated developer documentation for future agents.
- In the Developer Guide, describe the resulting behaviour, design, setup, or
  workflow and relevant verification or limitations. For a small change, use a
  brief note in the relevant section.
- Update existing guidance in place and remove superseded statements. Keep
  detailed interaction history and command output in the task's `logs/` file.
- Explanatory conversations that complete no change do not require edits to
  these two documents. Newly agreed standing rules count as workflow changes.

When external code, ideas, designs, or documentation are reused or adapted, ensure that any acknowledgement required by the project is recorded in the Developer Guide's acknowledgement section.

---

## Testing
Java automated tests use JUnit 5. The web migration uses the agreed unit/integration tests and Playwright browser tests; its unit/integration runners and commands must be chosen during target setup.

New behaviour should be accompanied by appropriate tests unless the behavious cannot reasonably be tested automatically.

Bug fixes should include a regression test when practical.

### Test Case Design
Functions requiring testing should have tests covering their relevant
equivalence partitions.

Equivalence partitions are not limited to explicit method parameters. Consider also:
- the state of the target object;
- relevant collaborating objects;
- persistent state;
- application state;
- global state accessed by the function.

Use Boundary Value Analysis (BVA) where boundaries exist.

### Positive and Negative Cases
Compatible positive conditions may be combined when doing so does not obscure which behaviour is being tested.

Test negative conditions separately when separate tests make failures easier to diagnose.

Do not combine unrelated failure conditions into one test.

### Test Naming
JUnit test methods should follow:
```
<functionName>_<inputOrState>_<expectedOutcome>()
```
Example:
```
execute_duplicatePerson_throwsCommandException()
```
Names should identify:
1. the function or operation under test;
2. the relevant input/state;
3. the expected result.

### Test Quality
Tests must:
- be deterministic;
- be independent where practical;
- test observable behaviour rather than implementation details;
- contain meaningful assertions;
- avoid depending on execution order;
- avoid external services unless explicitly testing an integration.

Do not modify a valid test simply because the implementation fails it.

Determine whether the implementation or the test is incorrect before making changes.

---

## Build and Verification

The Gradle requirements in this section apply to Java implementation tasks.
For web changes, run the configured target checks required by the migration
workflow; exact commands remain a setup prerequisite. Mixed changes require
applicable checks for both stacks.
After modifying Java source code, run the relevant verification tasks.

At minimum:
```bash
./gradlew test
./gradlew checkstyleMain
./gradlew checkstyleTest
```
Before completing a substantial implementation task, run:
```bash
./gradlew check
./gradlew build
```
When changes afffect application packaging, also run:
```bash
./gradlew shadowJar
```
Do not claim that a command passed unless it was actually executed successfully.

If a verification command cannot be run because of an environment limitation, state this explicitly in the final response.

If verification fails:
- inspect the failure;
- determine its cause;
- fix failures caused by the current task;
- rerun the relevant verification.

Do not disable tests, Checkstyle, or other verification mechanisms merely to make the build succeed.

## Logging Agent Interactions

After every agreed decision, update docs/InteractionRecord.md before ending the turn, including discussion-only turns. Record the decision, reason when known, affected scope, and any superseded decision. Maintain a current handoff covering workspace/branch, completed work, unverified work, blockers, and next authorised step. Distinguish proposals from approvals and planned checks from executed checks. Keep personal decisions in their separate external record.


Maintain [docs/InteractionRecord.md](docs/InteractionRecord.md) for project decisions and outcomes. Keep personal conversation records and unrelated material outside the repository. For mixed requests, record the project-relevant task summary here and preserve the original conversation separately; this overrides the verbatim-prompt logging requirement for such requests. Preserve the separate project task logs required below.
At the end of every workflow task performed by the LLM, create a new Markdown file under:
```
logs/
```
Do not overwrite previous task logs.

Use a descriptive filename that allows logs to be distinguished from one
another. Prefer:
```
YYYY-MM-DD-<short-task-description>.md
```
If multiple tasks with the same description occur on the same date, add a numeric suffix.

Each log must contain:
```
# Agent Interaction Log

## User Prompt

Record the user's original task prompt.

## Steps Taken

Summarize the significant steps performed by the LLM.

## Reasoning Summary

Explain the engineering rationale behind significant decisions.

Do not include hidden chain-of-thought or private internal reasoning.
Record concise, user-facing engineering justifications instead.

## Changes Made

List the important files created, modified, or deleted and explain why.

## Verification

Record the commands or checks actually performed and their outcomes.

## Final Output and Conclusion

Summarize the final result, including any unresolved issues.

```
Logs must accurately reflect what occured.

Do not claim that commands, tests, or manual checks were performed when they were not.

Do not include passwords, tokens, credentials, personal data, or other secrets in logs

---

## Handling Ambiguity
When requirements are ambiguous:
- inspect existing behaviour and documentation for evidence;
- prefer behaviour consistent with the existing system;
- avoid inventing new product requirements.

If multiple interpretations would result in materially different user-facing behaviour, data models, security properties, or architecture, ask the user for clarification before implementing the decision.

For minor implementation details that do not affect externally observable behaviour, use reasonable engineering judgement and follow existing project conventions.

---

## Definition of Done
A task is complete only when all applicable conditions below are satisfied:

- [ ] The requested functionality is implemented.
- [ ] Changes remain within the requested scope.
- [ ] Existing architecture and conventions have been respected.
- [ ] Relevant JUnit tests have been added or updated.
- [ ] Existing relevant tests still pass.
- [ ] Boundary and negative cases have been considered.
- [ ] Checkstyle passes for modified Java code.
- [ ] The project builds successfully.
- [ ] User-facing changes are reflected in docs/UserGuide.md.
- [ ] AGENTS.md and docs/DeveloperGuide.md are updated for the completed feature or change.
- [ ] No credentials or sensitive information were introduced.
- [ ] No temporary/debugging code remains.
- [ ]The final diff has been inspected for unrelated changes.
- [ ]The agent interaction has been recorded under logs/.
- [ ]Any verification that could not be performed is clearly reported.

---

## Git Conventions
All Git conventions when creating commits should follow from the [SE-EDU Git Conventions](https://se-education.org/guides/conventions/git.html)While it is not always necessary to have a body for the commit message, it must always have a subject.

---

## Final Response
When completing a task, provide a concise summary containing:
- what was changed;
- important implementation or design decisions;
- tests added or modified;
- verification performed and its results;
- documentation updated;
- any limitations or unresolved issues.

Never report a test, build, Checkstyle check, application run, or other verification as successful unless it was actually executed successfully.

## Documentation assignment

For role-based work, follow the documentation ownership table in
docs/agents/AgentSpecifications.md. Assign one writer per shared file at a time.
The coordinator owns AGENTS.md, workflow documentation, decision records and feature
SDD summaries; implementers own assigned user/design/setup guide updates.
The specification reviewer independently checks final feature documentation.
Each agent supplies its own task log; read-only assignments return content for
authorised consolidation. Feature Done requires the documented checks and evidence.

Only the coordinator updates workflow/PLAN.md completion/progress fields from worker
evidence and required gates. Workers report results without changing plan status.
Keep completed entries immutable and create authorised follow-up work through planning.

For role-based TDD, follow the common contract's per-criterion baseline evidence
policy: meaningful red for missing/incorrect behaviour; independently reviewed
implementation-path and passing-test evidence for already-satisfied behaviour.
Never manufacture failure, count setup errors as red, or treat legacy Java tests
as proof of target web behaviour. Preserve readiness and final acceptance gates.

For workflow-validation evidence and its limits, read docs/agents/WorkflowExercise.md.
When advancing a correction cycle, reconcile the current plan, handoff and SDD-summary
counters in the same transition; preserve prior stage counts as historical evidence.
A successful synthetic exercise does not establish migration-feature correctness.

Keep current readiness/status summaries aligned with recorded exercise evidence.
Distinguish native configuration loading, generic-dispatch observations and unexercised
controls; preserve historical reports rather than rewriting prior outcomes.

For the current assistant transition, read docs/CLAUDE-HANDOFF.md after these
instructions; recheck repository state and preserve uncommitted inventory evidence.

Before reading or writing GitHub issues/PRs, read docs/agents/issue-tracker.md
for the MP3 target and execution gates. Before triaging, read
docs/agents/triage-labels.md. Preserve concurrent feature-specification work.

Tracking choice agreed 2026-10-17: use GitHub issues and labels; Projects is
explicitly deferred. Follow docs/agents/issue-tracker.md for stage/flag updates.
