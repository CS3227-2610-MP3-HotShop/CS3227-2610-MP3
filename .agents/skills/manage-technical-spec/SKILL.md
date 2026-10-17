---
name: manage-technical-spec
description: Translate workflow/PRODUCT-SPEC.md into a traceable implementation design in workflow/TECHNICAL-SPEC.md for Spec-Driven Development. Use when creating or updating a technical specification before coding, consulting the user about ambiguities and discrepancies.
---

# Manage Technical Spec

Describe how the agreed product requirements will be implemented. Ground the design in the product spec and repository evidence, maintain requirement traceability, and consult the user whenever ambiguity or a discrepancy prevents a definite design.

## Scope and paths

- Resolve paths relative to the repository root. Read `workflow/PRODUCT-SPEC.md` and create or edit `workflow/TECHNICAL-SPEC.md`. Leading slashes in these paths mean repository root, not filesystem root.
- Produce an implementation design, not application code. Do not install dependencies, change schemas, implement features, or deploy anything unless separately requested.
- Treat the product spec as the source of agreed product intent. Do not silently rewrite its requirements to accommodate a technical design.
- Do not generate either specification when the task is to create or edit this skill itself.

## Migration design and shared contracts

For migration design, read [the migration workflow](../../../workflow/MIGRATION-WORKFLOW.md)
and the approved inventory/feature references in the product specification.
Keep the whole-document status separate from per-feature design readiness. Record
selected approved product IDs and versions, required shared foundations, dependent
features, open decisions, and human approval references. Unrelated draft features
do not block independent design; unresolved shared decisions block their dependents.
Do not bypass the approved inventory-first product-discovery sequence.

Design frontend/backend boundaries against one shared contract. For each feature,
identify frontend, backend, shared, and integration responsibilities; requests,
responses, field semantics and units, validation, errors, identity/permissions,
and state changes must agree across both sides. Include contract versions and
representative fixtures sufficient for parallel work. Both human owners approve
shared-interface changes before dependent implementation; record the decision and
update affected feature designs and issue links. Owner names can remain unassigned
during drafting, but ownership gaps block implementation assignments.

Distinguish the legacy JavaFX architecture from the proposed TypeScript/Next.js
web design. Explain necessary changes to desktop-only assumptions such as local
files, one active user, serialized operations, and window lifecycle. Preserve
business invariants and server-side access checks; do not assume the desktop
execution model supplies concurrency guarantees to the web application.

Initial delivery uses fresh seed data on a persistent hosted PostgreSQL database, with
no reset feature. PostgreSQL is used in every environment including tests, each with its
own database; tests never target the demo database. Specify connection pooling for the
serverless runtime, an injectable application clock rather than database time, idempotent
seeding as a provisioning step, and per-run test database isolation. The six JavaFX
SQLite migrations are a reference for intent, not files to port. This superseded the earlier resettable-demo and deferred-PostgreSQL
constraint on 2026-10-17. Specify the database choice and provisioning, schema and
migration approach, sharing between users and requests, seed content and when seeding
runs, image storage, and deployment limitations. Do not specify interim in-memory or
file-on-disk server state for a serverless host: it disappears between deployments and
cold starts, which users experience as an uncontrolled reset. Verify proposed
deployment and runtime assumptions against applicable documentation before treating
them as feasible.

Apply the agreed selective test layers: unit tests for rules/validation, integration
tests for server/storage/authorisation effects, and Playwright for critical real
frontend/backend journeys and browser risks. Specify deterministic independent
fixtures and teardown, and explain non-applicable layers. Mocked frontend checks
are not final integrated acceptance; tests against a substitute store do not establish
correctness against the deployed database. Keep test ownership and review gates consistent with the role briefs.

Use existing approved decisions before asking questions; discuss remaining material
choices one at a time. Design readiness enables planning within that scope, not
execution. Issues link to requirements, contracts, and later plan tasks without
becoming a second authoritative technical specification.

## 1. Read the product spec and establish the baseline

Begin by reading `workflow/PRODUCT-SPEC.md` in full. If it is missing, ask the user to provide or establish the product spec before deriving an implementation design. Do not invent requirements from the build prompt or architecture documentation as a substitute.

Read applicable repository instructions, the existing technical spec if present, and relevant architecture documentation. Inspect the actual interfaces, models, services, controllers, persistence, configuration, dependencies, and related tests needed to assess the requested design. For a new product without code, explicitly identify that condition. Do not assume functionality exists just because documentation describes it.

Identify the product spec's revision or other available version marker, its readiness status, requirements, acceptance criteria, constraints, and exclusions. Preserve its IDs. If IDs are absent, use unambiguous section links and requirement labels rather than pretending invented IDs exist in the source. Ask the user about unclear references or scope.

Separate existing verified mechanisms, agreed future changes, and proposed technical choices. Identify the smallest reasonable set of affected components and avoid introducing layers or dependencies without a concrete requirement. Existing conventions can supply a clear design choice, but conflicts or uncertainty must go to the user.

## 2. Resolve ambiguity throughout the writing process

If any ambiguity or discrepancy is detected, consult the user before deciding the affected behavior or design. This applies to conflicts among requirements, disagreements between specs and code, missing business rules, incompatible constraints, unclear acceptance criteria, and unresolved technical choices. Do not pick an interpretation on the user's behalf.

Ask focused questions that identify:

- The conflicting statements or missing decision, with requirement references.
- Why the answer affects implementation, data, security, compatibility, or scope.
- Feasible options and their consequences; label any recommendation as a proposal.

Wait for the answer before finalizing dependent sections. Continue independent analysis and drafting when useful. Reuse decisions already made in the session; do not require the user to repeat them. Silence and elapsed time are not approval.

Record unresolved items explicitly as open questions and mark affected design sections as blocked. If the user stops early or defers a decision, save useful agreed content as a draft and identify the consequences of the deferral. Do not fill gaps with unconfirmed assumptions or present proposals as committed designs.

If clarification changes product intent, identify the required product-spec update and obtain or reuse explicit authorization to make it. Otherwise leave that source unchanged and report that synchronization is needed. Do not mark the technical spec ready while it contradicts its source. A draft product spec can support design of explicitly approved feature requirements. Unresolved dependencies keep affected designs blocked and the overall document in draft status; independently approved feature designs retain their recorded readiness.

## 3. Design and write the technical spec

Create the output directory and file if absent. For an existing technical spec, update it as a coherent current design, preserving unaffected content and stable technical decision identifiers. Link to `PRODUCT-SPEC.md` using paths relative to the document, and reference product requirement and acceptance IDs throughout.

Include the following sections. Adapt an existing document's organization while preserving equivalent coverage. For an inapplicable section, explain why it is not applicable rather than inventing infrastructure.

### Overview, scope, and source baseline

State the implementation objective, included and excluded changes, source product-spec version, revision date, and document status plus per-feature design readiness and approval references. Identify affected components, established constraints, and implementation compatibility requirements. Distinguish proposed design from current implemented behavior.

### Requirements traceability

Map every in-scope product requirement and acceptance criterion to the technical mechanism, owning component, and planned verification. Cover UI and quality requirements as well as functional requirements. Mark gaps explicitly; do not equate a reference alone with implementation coverage. Every proposed mechanism should support a requirement or an identified technical constraint.

### Architecture and component responsibilities

Describe boundaries, responsibilities, dependencies, and the flow of data or control through affected components. Explain reuse and changes to the existing architecture. Use diagrams when they clarify relationships or sequences. Identify threading, concurrency, transaction boundaries, and external integrations where relevant. Document the rationale for significant agreed choices without speculative abstractions.

### Data model

Define entities, fields and types, identifiers, relationships and cardinality, optionality, defaults, uniqueness, validation, and domain invariants. Explain storage representation, units, precision, state transitions, ownership, and lifecycle. Include applicable indexes, referential integrity, transaction requirements, and deletion or retention behavior. Describe migrations and treatment of existing records when data changes. Do not fabricate schema details that depend on unresolved product decisions.

### API specification

Specify relevant contracts between components and external consumers. An API can be an in-process service or interface; do not assume a web server or invent REST endpoints for a desktop application.

For each operation, define its name or route, caller, inputs and types, outputs, preconditions, validation, permissions, side effects, and failure contract. Describe ordering, pagination, idempotency, concurrency, and compatibility when applicable. For network APIs, include method, path, request/response schema, status codes, and authentication where relevant. Label new interfaces as proposed and verify names of existing interfaces against code.

### UI integration and workflow execution

Explain how the specified screens or interaction surfaces call the implementation, manage state, display outcomes, and recover from errors. Cover relevant asynchronous work, loading and empty states, validation feedback, and state refresh. Trace normal workflows through components. Follow the product spec's UI intent without silently choosing new user-visible behavior.

### Security information

Describe trust boundaries, authenticated identity, authorization and ownership enforcement, input validation, sensitive data handling, credential or secret storage, and relevant protection mechanisms. Identify where checks execute; UI visibility alone is insufficient authorization. Include applicable logging redaction and failure disclosure rules. Reference established security mechanisms and unresolved security decisions. Never put actual secrets or credentials in the specification.

### Edge cases and unhappy paths

For each applicable case, describe the trigger or state, detection point, technical response, resulting persistent state, user-visible outcome, and recovery. Cover relevant invalid or boundary input, unauthorized access, missing or stale records, conflicting changes, duplicate actions, dependency failures, partial writes, interruption, and retries. Explain atomicity, rollback, and prevention of inconsistent state where needed. Consult the user if the desired outcome is not defined; do not invent a business policy to complete this section.

### Quality, dependencies, and operational considerations

Explain how the design meets agreed performance, reliability, accessibility, offline, platform, and other quality constraints. Identify required dependencies and compatibility, configuration, resource management, packaging, deployment, and observability only where relevant. For significant new dependencies, document purpose and alternatives. Do not introduce numeric targets absent from agreed requirements.

### Verification strategy

Map acceptance criteria to planned unit, integration, end-to-end, or manual checks as appropriate. Identify positive, negative, and boundary cases, necessary fixtures, and important invariants. Include migration, security, failure recovery, and compatibility checks when relevant. Describe intended verification without claiming that unexecuted tests have passed.

### Technical decisions and open questions

Record significant agreed choices, brief rationale, alternatives considered, and relevant requirement references. Clearly separate confirmed decisions from proposals, unresolved questions, and user-approved deferrals. For each unresolved item, identify affected sections and whether it blocks implementation.

## 4. Keep updates synchronized

Re-read the source and target before completing edits. Reconcile intervening changes and regenerate affected traceability entries if product requirements changed. Preserve unrelated user edits; avoid replacing the document with an appended transcript or competing designs.

When requirements change, update all affected data, API, security, workflow, edge-case, and verification sections. Remove superseded design statements consistently without deleting unrelated content. Preserve product IDs and technical IDs; do not reuse retired identifiers for different meanings. Record the updated source baseline using an available revision marker without inventing one.

## 5. Verify readiness and hand off

Read the written file and inspect the diff. Check that:

- Every in-scope requirement and acceptance criterion has a concrete implementation mechanism and planned verification.
- Data, API, permissions, workflows, and recovery behavior agree with each other and the product spec.
- Existing mechanisms referenced by the design have been verified against repository evidence.
- Proposed changes fit established constraints and have no unexplained scope expansion.
- No unconfirmed choice, ambiguity, discrepancy, or blocking question is disguised as a finalized design.
- The source baseline is current, links resolve, and unaffected content is preserved.

Consult the user about gaps found during review. Mark a feature design ready for planning only when its blocking decisions and shared dependencies are resolved, required approvals are recorded, and product-spec alignment is established. Mark the whole document ready only when that holds for all in-scope designs. Follow applicable repository documentation and interaction logging requirements. Application tests are not evidence for a prose-only design unless actually executed; report any verification performed accurately.

Finish by linking the technical spec, summarizing the design and requirement coverage, stating feature-level and overall readiness separately, and listing unresolved questions or agreed deferrals. Creating the spec does not itself authorize implementation.
