# Single-agent baseline (Run A)

Version: 0.1, 2026-10-15 (SGT)

Status: agreed decisions recorded; not installed or behaviourally tested. Feature, baseline, workspace paths, and budgets remain pending.

## Purpose and inputs

Deliver the same approved feature as Run B in one agent context without delegation. Use the [common contract and run packet](../AgentSpecifications.md), the same requirements, eligible tasks, review checklists, verification commands, and evaluation policy. The intended difference is self-review instead of separate workers and reviewers.

## Sequence

1. Validate the complete run packet and assigned isolated directory before edits. Record baseline, scope, start time, and available measurement fields. Report missing prerequisites before implementation.
2. Write specification-derived tests using the [testing brief](testing.md)'s test-design and meaningful-red criteria. Record acceptance mappings and actual outcomes. Expected initial red tests are normal TDD evidence; compilation/setup failures are not meaningful red.
3. Self-review test readiness using the pre-implementation checklist in the [specification and coverage reviewer](specification-reviewer.md). Record coverage, gaps, and evidence before production implementation starts.
4. Implement the approved behaviour once tests are ready. Apply the [implementer](implementer.md)'s scope and design limits. This baseline agent owns both production and development tests; the multi-agent test-ownership and reinvocation rules do not apply. The independent evaluation suite remains immutable.
5. Run required checks, preserve a candidate snapshot, and self-review actual implementation and test coverage using the reviewer's post-implementation criteria. Record every criterion as satisfied, failed, or unverified with evidence.
6. Perform the [security reviewer](security-reviewer.md)'s final-stage checklist and permitted synthetic-data checks against the stable candidate. Record findings and limitations before completion.
7. Return the common final handoff, actual verification results, unresolved issues, timestamps, human interventions, and reported usage or `N/A`. Completion requires all applicable requirements and checks; budget exhaustion produces an incomplete handoff.

Use the referenced roles' substantive criteria, while performing all work personally. Do not spawn or invoke subagents, including for tests or reviews. Keep stage reports separate so self-review findings and subsequent changes remain measurable.

## Progress, corrections, and user decisions

Automatically advance through already approved eligible stages and report current work, progress, results, and blockers. Pause affected work and its dependents; independent approved work may continue.

After any failed review or verification loop requiring code or test edits, draft a concrete change plan and obtain explicit user approval before making those edits. Include the finding, requirement and snapshot, test evidence, proposed files/changes, verification, scope effects, and correction-cycle number. Diagnosis may continue while approval is pending. Expected initial TDD red does not trigger this correction gate.

Use the same maximum of two correction cycles per task/batch across all stages, including test readiness and final security review. Initial scheduled assessments do not consume a correction cycle; approved corrections and re-review do. Stage changes do not reset the count. At exhaustion, preserve evidence and ask the user how to proceed.

Escalate ambiguous requirements, material design decisions, and conflicting review conclusions with clear specification and test evidence. Do not silently weaken valid tests to fit the implementation. Changes beyond an approved correction plan need renewed approval. Apply the same task/prerequisite restrictions as Run B; unresolved implementation-skill compatibility must be settled before either run.

## Workspace boundary

Work only in Run A's assigned checkout and approved report/scratch/data locations. Keep the actual MP3 checkout and Run B's artifacts outside the run's work. Separate runtime databases, generated files, and local service resources where applicable; directory separation alone does not isolate shared external state or enforce permissions.

Follow the [comparison protocol](../../MiniPresentation.md#6-comparison-protocol) for workspace preparation. Do not merge or push benchmark changes as part of this run. Integration is a separate decision after evaluation.
