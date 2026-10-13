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

### Technical specification workflow

Use the `manage-technical-spec` agent skill in
`.agents/skills/manage-technical-spec/SKILL.md` after establishing the product
specification. It reads `workflow/PRODUCT-SPEC.md` and creates or updates
`workflow/TECHNICAL-SPEC.md` with requirement traceability, data and interface
contracts, security mechanisms, failure handling, and planned verification.
Ambiguities and discrepancies are referred to the user before dependent design
decisions are finalized. Unresolved blockers keep the document in draft status;
the skill does not implement the design.

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

### Task implementation workflow

Use the `implement-task` skill at `.agents/implement-task/SKILL.md` with explicit
task IDs or unique task names from `workflow/PLAN.md`. It implements only those
tasks. Missing tasks or incomplete prerequisites cause a read-only rejection;
ambiguities and instructions delegating design decisions to the agent stop
implementation for user clarification. A task is marked complete only after all
acceptance criteria are met. Completed entries remain unchanged, and revised
work requires a new planned task and an explicit implementation request.

## Acknowledgements

See [Acknowledgements](developerGuide/Acknowledgements.html) for the sources of
reused or adapted ideas, code, documentation, tools, and third-party libraries.
