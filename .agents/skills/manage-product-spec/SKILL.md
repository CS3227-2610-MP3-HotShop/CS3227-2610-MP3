---
name: manage-product-spec
description: Clarify product requirements through iterative user interviews, then create or update workflow/PRODUCT-SPEC.md for Spec-Driven Development. Use when defining a new product or specifying changes before design and implementation.
---

# Manage Product Spec

Turn a user's build or change request into an agreed, actionable product specification. Interview the user thoroughly, resolve material ambiguity, and record intended behavior before handing off to design and implementation.

## Scope and paths

- Resolve paths relative to the repository root. The output is `workflow/PRODUCT-SPEC.md`; a leading slash in the user's workflow path means repository root, not filesystem root.
- Create the directory and file if absent. Otherwise update the existing spec in place, preserving unaffected requirements and stable requirement identifiers.
- Specify the product. Do not implement code or create a detailed implementation plan unless separately requested. Record agreed technical constraints without inventing architecture.
- Do not create a product spec merely because the user asks to create or edit this skill itself.

## Migration discovery and approval

For migration work, read [the migration workflow](../../../workflow/MIGRATION-WORKFLOW.md)
and [feature inventory](../../../workflow/FEATURE-INVENTORY.md). Start with the
complete MP3 feature inventory, grounded in the User Guide, Developer Guide, and
source/test evidence. Record the exact reference commit and evidence status.
Obtain user approval of inventory coverage before detailing features individually.

Distinguish documented behaviour, code/test-verified behaviour, runtime-verified
behaviour, proposed web adaptations, and documented future work. Resolve conflicts
with the user; neither legacy code nor guide text automatically approves a new
requirement. Preserve all existing-feature scope unless the user changes it.
Deferred wishlist or notifications are not automatically migration requirements.

After inventory approval, discuss one feature or tightly connected decision at a
time, reusing earlier answers. Record each feature's inventory references, stable
requirement/acceptance IDs, dependencies, evidence, outstanding decisions, and
explicit approval reference/version. Keep a feature-status table distinguishing
inventory-only, draft detail, and approved detail. The whole document can remain
draft while specified features are approved; independent approved features may
proceed to design without implying approval of unresolved shared foundations.

Use the agreed target constraints: TypeScript/Next.js, a separate migration branch,
all existing features, fresh demo data, and persistent storage on hosted PostgreSQL
from the start. Existing-data import is not required. The web application has no reset
feature; that decision superseded the earlier resettable-demo and deferred-PostgreSQL
constraint on 2026-10-17. Specify coherent marketplace behaviour, permissions, image
use and seed content; do not choose a database provider or schema here.

Frontend/backend separation is implementation ownership, not two competing product
specifications. One set of acceptance criteria governs the integrated feature.
Record owner approval for area-specific decisions and both owners' agreement for
shared-interface or cross-area changes, with impact on dependent features. An
unassigned owner remains explicit rather than being invented.

Issues track ownership, progress, and blockers and link to these requirements.
Keep detailed product intent in this specification and stable inventory references;
issue creation, status changes, and specification readiness do not authorise coding.

## 1. Establish context

Read applicable repository instructions and the existing product spec. Inspect relevant architecture, product documentation, and implementation as needed to understand the request and identify conflicts. Disclose missing required references rather than inventing their contents.

Determine whether the request concerns a new product, addition, behavior change, or removal. Summarize the desired outcome and facts already established by the prompt and conversation. Distinguish verified current behavior from proposed behavior; existing code is evidence, not automatic approval of desired requirements.

Maintain a working list of confirmed decisions, proposed assumptions, unresolved questions, and exclusions. Reuse answers already provided. Bring contradictions between the request and existing specifications to the user instead of silently choosing one.

## 2. Interview iteratively

Ask focused rounds of questions and wait for answers before making dependent decisions. For this migration, use the agreed one-feature/decision-at-a-time discussion; otherwise choose a small related question set appropriate to the user. Start with the goal and users, then examine individual workflows and exceptions. Extensive discovery means sufficient depth, not a fixed question count or exhaustive questionnaire.

Use plain language and explain tradeoffs. Offer concrete choices where useful while allowing another answer. Ask for examples to clarify vague terms such as "fast", "simple", or "admin". Identify recommendations as proposals until accepted.

After each round, summarize new decisions and ask the next questions exposed by the answers. Do not repeat settled questions unless new information conflicts with them. Silence, elapsed time, and agent confidence are not agreement.

Cover these areas to the extent relevant to the request:

| Area | Decisions to establish |
| --- | --- |
| Product description | Problem, intended value, primary use cases, and what success means. |
| Users and roles | Intended users, assigned or contextual roles, guest behavior, permissions, ownership, and prohibited actions. |
| Functional requirements | Triggers, preconditions, inputs, outputs, business rules, state transitions, and interactions with existing features. |
| Data | Required and optional fields, validation, units, limits, uniqueness, persistence, editing, deletion, retention, and existing records. |
| UI | Platform, entry points, screens or interaction surfaces, navigation, controls, feedback, accessibility needs, and relevant empty, loading, success, and error states. |
| Failure and recovery | Invalid input, unauthorized actions, unavailable data or services, cancellation, retries, duplicate actions, and partial failure. |
| Quality and constraints | Relevant privacy, security, performance, reliability, offline behavior, platform, integration, and operational constraints; measurable targets where needed. |
| Scope | What must be designed and implemented in this iteration, dependencies, deferred features, and exclusions. |
| Acceptance | Observable evidence for each requirement, including relevant positive, negative, and boundary cases. |

For each workflow, walk through a normal example and applicable edge cases. Use concrete inputs and states to reveal ambiguity: absent versus empty values, minimum and maximum limits, duplicate submissions, stale or deleted records, conflicting actions, lost connectivity, and recovery after interruption. Discuss plausible cases for this product without adding features solely to address hypothetical situations.

For changes, establish before/after behavior, affected roles and workflows, handling of existing data, and intended compatibility breaks. Confirm removals or changed requirements through the discussion and update affected sections consistently.

## 3. Determine readiness

Before finalizing the selected feature or scope, check that:

- The goal, users, in-scope behavior, and exclusions are clear.
- Each workflow has defined inputs, permissions, outcomes, and applicable failure and boundary behavior.
- UI expectations and relevant constraints guide design without leaving product decisions to guesswork.
- Requirements are consistent and conflicts with existing specifications have been resolved.
- Every functional requirement has observable acceptance criteria.
- No unresolved question would materially change behavior, scope, data, permissions, or acceptance.

Present a concise synthesis of the proposed spec, including scope and significant edge-case decisions, and invite corrections before finalizing. Ask for confirmation of decisions not already agreed; prior explicit agreement remains valid. Continue discovery if the synthesis exposes gaps.

If the user cannot decide or asks to stop early, record agreed content in a clearly labeled draft. List unresolved questions, their impact, and which block design or implementation. Do not declare the affected scope or entire spec ready; retain any independently approved feature statuses. Defer details only when the user agrees to the deferral and its limits are recorded. Never claim that every conceivable edge case has been exhausted.

## 4. Write the specification

Write discussed requirements in `workflow/PRODUCT-SPEC.md`. Use the sections below for a new document; adapt an existing document's organization while retaining equivalent coverage. Include every required section, using "Not applicable" with a reason when appropriate rather than fabricating content.

### Required sections

1. **Product Description**: Purpose, problem, intended outcomes, and context. Include revision date and document status, plus feature-level readiness and approval references for incremental work. Product readiness permits the corresponding design handoff; it is not implementation authorisation.
2. **Users/Roles**: User groups, goals, permissions, ownership rules, and interactions between roles.
3. **Functional Requirements**: Assign stable IDs such as FR-001. Describe actor, trigger, preconditions, expected behavior, validation, resulting state or data, and relevant failures. Distinguish intended behavior from implementation status.
4. **UI**: Agreed interaction surfaces, entry points, navigation, inputs, feedback, and relevant states, linked to requirement IDs. Specify exact wording or visual detail only when agreed; identify designer discretion. For products without a graphical UI, describe the applicable CLI/API contract or explain why this section is not applicable.
5. **Acceptance Criteria**: Assign IDs such as AC-001 and link each to its requirement. Use Given/When/Then or equally precise observable conditions. Cover success, denied or invalid operations, boundaries, and recovery where relevant. Criteria describe intended checks, not claims that tests have passed.
6. **Scope of Design and Implementation**: Included capabilities and affected surfaces, existing capabilities to preserve, agreed constraints and dependencies, and decisions delegated to later design. Keep detailed implementation tasks and speculative architecture outside the spec.
7. **Out of Scope**: Explicit exclusions and deferred capabilities. Distinguish "not in this iteration" from "must never be supported" when relevant.

Add **Constraints and Quality Requirements** when relevant and **Open Questions and Deferred Decisions** when anything remains unresolved or explicitly deferred. Label unconfirmed assumptions as proposals, never requirements. Record material resolved decisions and brief rationale when needed to prevent later misinterpretation.

Use consistent domain terms and testable statements. Avoid vague qualifiers, unsupported numeric targets, invented UI flows, and requirements inferred from silence. Record agreed intent even when unimplemented, without describing it as current functionality.

For updates, preserve unrelated content and existing IDs. Add new IDs without reusing removed ones. Update corresponding UI, acceptance, scope, and exclusion sections together so obsolete behavior is not left elsewhere. Re-read the file before editing and reconcile intervening changes rather than overwriting them. Produce a coherent current spec, not an appended transcript or conflicting versions.

## 5. Verify and hand off

Read the written document and inspect the diff. Check requirement-to-acceptance coverage, permissions, consistency of scope and exclusions, unresolved decisions, and alignment with the user's answers. Verify the repository-root output path and preservation of unrelated requirements.

Follow applicable repository documentation and interaction logging requirements. Do not run or claim application tests solely to validate a prose spec unless repository instructions require them.

Finish by linking the spec, summarizing changes, stating readiness for the selected features and whole document separately, and identifying remaining questions or agreed deferrals. The handoff enables subsequent design and implementation; it does not authorize those stages by itself.
