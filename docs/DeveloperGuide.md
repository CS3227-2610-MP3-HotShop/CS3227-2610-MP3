---
  layout: default.md
  title: "Developer Guide"
  pageNav: 3
---

# HotShop Developer Guide

See [Agreed agent workflow and project evidence](developerGuide/DevelopmentWorkflow.html#agreed-agent-workflow-and-project-evidence) for ownership, correction and persistent interaction-record rules.

---

## Introduction

Welcome to the HotShop Developer Guide. This guide introduces the architecture,
codebase, and development workflow of HotShop, a JavaFX/JPro marketplace with
PostgreSQL persistence and private Supabase image storage.

Start with [Development Workflow](developerGuide/DevelopmentWorkflow.html) to set
up your environment, run checks, and build the application, including selecting
the JavaFX platform for Intel and Apple Silicon macOS JARs. Then read
[Architecture Overview](developerGuide/ArchitectureOverview.html) to understand
how the application is organised. Use the sidebar to explore each topic in detail.

## Project reflections

[Project reflections](Reflections.md) contains Jonathen Cheng's reflection on AI security, specification-driven development and the three workflow benchmarks, with concrete security-test assertions, their assurance limits, supporting evidence and a link to the preserved seller-feature reflection.

## Workflow evidence

The `workflow/PRODUCT-SPEC.md`, `workflow/TECHNICAL-SPEC.md` and `workflow/PLAN.md` files record approved requirements, design and implementation status. The corresponding `LLM_FEATURE_*` files record the seller AI feature's specification, design and plan. `workflow/benchmark-results.csv` preserves the measured candidates and their limitations; its results are not a test report for the current release. Agent roles and skills remain in their natural configuration locations under `.codex/`, `.agents/` and `docs/agents/`, with repository instructions in `AGENTS.md` and automation under `.github/workflows/`.

`logs/ExecutionRecord.md` holds the current handoff and concise development evidence; existing feature summaries and teammate logs remain under `logs/`. The reflection is a report supported by this evidence, rather than a transcript of its drafting conversation.

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

See [LLM Implementation](developerGuide/LLMImplementation.html) for description
generation setup, design and testing, and [Acknowledgements](developerGuide/Acknowledgements.html)
for third-party resources.

For PostgreSQL configuration, provisioning and schema details, read
[Account Service and PostgreSQL Persistence](developerGuide/AccountServiceAndLocalPersistence.html).

See [Private image storage setup and import](developerGuide/AccountServiceAndLocalPersistence.html#private-image-storage-setup-and-import) for bucket configuration, separate activation, preservation and rollback.

See [Architecture Overview](developerGuide/ArchitectureOverview.html) for process-owned directory leases and isolated browser runtimes, and [LLM Implementation](developerGuide/LLMImplementation.html) for the shared AI request ceiling and cancellation lifecycle.

Browser image chooser lifecycle and isolated two-session/image checks are described in [Architecture Overview](developerGuide/ArchitectureOverview.html) and [Manual testing](developerGuide/ManualTesting.html).
