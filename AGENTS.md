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

When writing human-readable documentation, prefer to explain relevant concepts and details using simple and easy to understand english. Avoid using jargon or ambiguous terms unless absolutely necessary. If something is unable to be explained simply, help by providing examples to aid with understanding.

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

### Developer Documentation

For every completed feature or change, update `docs/DeveloperGuide.md`, before reporting completion.
This includes changes to agent configuration, workflow, and documentation.

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
- [ ] docs/DeveloperGuide.md is updated for the completed feature or change.
- [ ] No credentials or sensitive information were introduced.
- [ ] No temporary/debugging code remains.
- [ ] The final diff has been inspected for unrelated changes.
- [ ] The agent interaction has been recorded under logs/.
- [ ] Any verification that could not be performed is clearly reported.

---

## Git Conventions
All Git conventions when creating commits should follow from the [SE-EDU Git Conventions](https://se-education.org/guides/conventions/git.html)While it is not always necessary to have a body for the commit message, it must always have a subject.
