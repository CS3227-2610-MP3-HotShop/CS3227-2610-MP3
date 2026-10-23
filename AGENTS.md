# AGENTS.md

## Owner-approved workflow — 2026-10-22

This agreement takes precedence over conflicting older skills, role briefs, agent
configuration and workflow text. It changes future execution, not historical results
or completed benchmark/correction records.

- One implementer owns the approved batch: production code, tests, fixtures, build
  configuration and associated guides. It may write/update tests, preserving valid
  assertions. No separate tester or intermediate review handoffs by default.
- After implementation and checks, independent specification/coverage and security
  reviewers inspect one stable candidate. The coordinator consolidates their findings.
- Plan one shared correction pass. Additional passes are permitted only for confirmed
  errors or mistakes within approved scope, supported by failing checks, a contract
  violation or concrete execution-path evidence. Record the defect, scoped fix,
  pass number and affected verification; proceed without repeated approval requests.
  New features, requirement changes, speculative improvements, unresolved decisions
  and live/deployment actions retain their own approval gates. Do not loop unchanged
  attempts; surface a blocker when progress needs a new decision or external change.
- Use targeted source/spec sections and compact briefs. New specialists receive
  fork_turns=none; do not request full chats or unrelated histories. Prefer a Sol
  coordinator and Luna specialists; saved settings do not prove a live model change.
- Persist every human-visible interaction batch: sanitized user request/approval,
  agent roles and assignment, decisions, concise results, review findings, corrections,
  actual checks and limitations. Subagents return concise messages; the coordinator
  appends these as work progresses to one existing feature record under logs/.
  Preserve existing teammate logs. Do not create a new file per exchange or task,
  restore previously deleted histories, or record secrets/private reasoning.
- All logs, interaction records and handoffs live under logs/. The current shared
  handoff is logs/ExecutionRecord.md; AGENTS.md holds durable rules, not activity logs.
  Product guides and PLAN retain their normal behavior/status responsibilities.
- Preserve school evidence: approved specifications and acceptance tests; actual
  specialized-agent work and reviews; AI threat boundaries/evaluations; SoC model
  configuration, quotas and error handling; team contributions and quantified MP2
  reuse; and separately managed deployment automation. Record executed evidence
  honestly, with benchmark/release limitations. Definitions alone do not prove use.

The submission reflection entry point is docs/Reflections.md. Preserve named
contributor reflections and check personal claims and benchmark figures against
their recorded evidence when editing it. Distinguish tested application boundaries
from model semantic correctness; security-tag counts are not security guarantees.

This file defines the repository-wide instructions that AI coding agents must follow when working on HotShop.

These instructions apply to all files in this repository unless a more specific instruction file exists for a particular directory.

---

## Project Overview

Current owner direction: retain JavaFX/JPro, hosted on Render, with Supabase
PostgreSQL and Supabase Storage. Next.js migration instructions below are
historical where they conflict with this direction. PostgreSQL replaces SQLite
in every environment, including local development and tests (approved 2026-10-19).
See workflow/PRODUCT-SPEC.md for the current database scope and unresolved gates.
Use the workflow shipped on jpro-docker: .agents/skills, docs/agents role contracts,
and .github/workflows/gradle.yml. Missing old migration artifacts do not mean the
retained agent workflow is absent; create task-specific specifications and plans
through the existing skills rather than restoring abandoned migration scope.
Never run automated tests against hosted/demo data or read credentials into logs.
JPro runtimes lease one process-owned data-directory lock while retaining separate
authentication and service state. See the Architecture Overview for lifecycle ownership
and LLM Implementation for the shared production AI dispatch ceiling; retain standalone
client ownership and seller account quotas when changing transport code.
Bind JPro image pickers before the first click and explicitly enable click selection;
keep chooser cancellation interactive and discard abandoned selections before upload.
See the manual-testing guide for isolated browser acceptance and temporary-object cleanup.

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

Use plain language in human-facing documentation, with concrete examples where helpful.

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

Before feature completion, update relevant user/developer guidance and the existing
feature record under logs/. AGENTS.md changes only when durable instructions change.

### Developer Documentation

For every completed behavior change, update the Developer Guide. Update AGENTS.md only for durable instruction changes; put activity records in logs/.
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

Follow the owner-approved workflow above. Persist each interaction batch concisely
in an existing feature record under logs/, with the current handoff in
logs/ExecutionRecord.md. Preserve teammate records. Subagents report in messages;
the coordinator records assignments, decisions, outcomes, defects and actual checks.
No per-exchange files, activity logs in AGENTS.md, credentials or private reasoning.
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
- [ ] Developer documentation is updated; durable instruction changes go in AGENTS.md and activity records in logs/.
- [ ] No credentials or sensitive information were introduced.
- [ ] No temporary/debugging code remains.
- [ ]The final diff has been inspected for unrelated changes.
- [ ] Final approvals and evidence are consolidated in existing records after reviews pass.
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

## Documentation

Use plain language in human-facing documentation, with concrete examples where helpful. assignment

For role-based work, follow the documentation ownership table in
docs/agents/AgentSpecifications.md. Assign one writer per shared file at a time.
The coordinator owns AGENTS.md, workflow documentation, decision records and feature
SDD summaries; implementers own assigned user/design/setup guide updates.
The specification reviewer independently checks final feature documentation.
Each agent returns a concise result message; coordinator consolidates existing records for
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

## Delivery workflow

Follow the owner-approved workflow at the top of this file: one implementer owns
code/tests/guides; final independent reviews follow the completed batch.

## Retained Java PostgreSQL conversion

Use PostgreSQL in every environment, separate migrateDatabase provisioning and
read-only startup validation. Tests use disposable local PostgreSQL, never hosted
data. Preserve directory-lock protection until separately approved integration.
Current acceptance evidence lives under logs/ and in workflow/PLAN.md.

## Correction policy

Use one planned shared pass and the evidenced-error exception defined above.
Historical experiment outcomes and completed correction counts remain unchanged.

## Targeted context and handoff rule — owner approved 2026-10-19

Coordinators and workers must read only context needed for the current assignment.
Search headings/symbols first; read bounded relevant sections or line ranges. Use the
current handoff plus selected task/acceptance criteria and relevant source/tests.
Do not reread whole chats, long decision records, historical logs or unrelated specs.
For new subagents use fork_turns=none and a compact self-contained brief containing
scope, ownership, current decisions, exact document sections, checks and blockers.
Reuse an active implementer instead of restarting it merely to remove old context.
Mandatory instructions still apply: read applicable instructions once, then consult
only changed/relevant sections. Expand reads only when needed to resolve a concrete
ambiguity or dependency. Keep current handoffs short and link supporting evidence.
Already injected app conversation context cannot be selectively removed by agents;
this rule controls requested history, file/tool reads and future agent handoffs.


## Owner model preference — 2026-10-19

Use gpt-6.1-sol for the coordinator and gpt-6-luna for specialist subagents.
Repository .codex/config.toml sets the main/default specialist preferences; the four
.codex/agents/hotshop_*.toml roles explicitly select Luna. New specialist launches
use no full-history inheritance. Do not claim an existing session changed model
merely because configuration changed; the coordinator model is selected in the app.
Let an already-running verification finish rather than restart it solely for model
selection. Final specialist reviews use Luna. Settings persistence is verified by
file inspection; a new live session's actual model requires runtime confirmation.

## Private image storage integration constraints

Use private listingImages/profileImages REST buckets and the existing storage seam. Preserve authorized reads, create-only writes, bounded staging/recovery, durable tombstones and lifecycle fencing. Provision schema separately and verify/import photos before jointly activating bindings; normal startup validates them. Keep local originals, server keys and deployment certificates outside source control. Automated tests use local fixtures/disposable PostgreSQL; hosted import/activation and browser persistence are separate operator actions. See the Developer Guide storage setup topic for current commands and limits.
