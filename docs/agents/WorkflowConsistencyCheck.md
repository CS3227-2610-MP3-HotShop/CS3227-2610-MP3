# Workflow consistency check

Date: 2026-10-17 (SGT). Scope: static documentation review for
[issue #4](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/4).
No live agent exercise or application verification was performed.

## Documents reviewed

- The four SDD skills: manage-product-spec, manage-technical-spec,
  plan-implementation, and implement-task.
- [Common contract](AgentSpecifications.md) and its five linked role briefs.
- [Migration workflow](../../workflow/MIGRATION-WORKFLOW.md), repository
  [instructions](../../AGENTS.md), and [Developer Guide](../DeveloperGuide.md).

## Findings and corrections

| Finding | Correction |
| --- | --- |
| Repository instructions required all tests to use JUnit, conflicting with approved web tests | Scoped Java tools/conventions/checks to Java work; preserved the web policy with exact runners/commands pending setup |
| Roles referred generically to the user while migration has area owners and shared-interface approvals | Added common human-approver routing and linked it from worker/coordinator roles; both owners approve shared changes |
| Common reviewer inputs named one initial implementation snapshot despite multiple review checkpoints | Defined test, implementation, and final security snapshots; retained earlier-checkpoint evidence and correction findings for re-review |
| Implementer required meaningful failures for every handoff even though testing/reviewer rules allow already-passing baseline cases | Allowed evidenced already-satisfied cases without artificial red failures |
| Compatibility notes still claimed skill alignment was pending after the four skill edits | Updated current status while retaining historical commit differences and explicit runtime-validation limits |

## Cross-document checks

These outcomes describe instruction consistency, not observed agent behaviour.

| Policy | Review outcome |
| --- | --- |
| Complete inventory before detailed feature specs | Product skill and workflow agree; discovery evidence is distinct from scope approval |
| Incremental feature readiness | Product, technical, planning, and execution distinguish approved features from overall draft state and unresolved shared dependencies |
| Bounded coordinator authority | Planning records authorisation; execution/coordinator select only eligible tasks inside it |
| Partial blockers | Affected work/dependents pause; independent authorised tasks can proceed |
| Ownership and minor judgment | Implementer owns production, tester owns tests; small local choices allowed, material decisions escalated |
| TDD/review handoffs | Tests, readiness review, production, implementation/coverage review, final security; initial expected red is not a corrective failure |
| Correction approvals | Failed-loop edits require a concrete approved plan; diagnosis alone is not permission to edit |
| Shared correction limit | Two cycles span stages and related follow-up tasks; initial assessments do not consume cycles and new IDs do not reset the count |
| Completed-task history | Planning/execution preserve completed entries and use authorised follow-up tasks; task completion differs from final feature acceptance |
| Integration and layered tests | Frontend fixtures alone cannot satisfy final integration; unit/integration/Playwright responsibilities and deterministic data are specified |
| Review disagreement | Human decision with both positions and evidence; no agent majority-vote resolution |

No additional unresolved policy contradiction was identified in this pass. This
is not a guarantee that every ambiguity has been eliminated.

## Remaining readiness gates

- Worker TOML files are written; four configured types launched in the supplied smoke-test report. Isolated loading, full behaviour and later instruction changes remain unverified.
- The disposable workflow exercise in issue #7 remains required for behavioural evidence.
- Source-baseline freezing, actual area/shared-file owners, demo-state/reset design,
  target-stack commands, and feature approvals remain stage-specific prerequisites.
- Unit/integration runner choices and target runtime/dependency versions remain open.
- Python is unavailable in the inspected environment, so the bundled Python skill
  validator could not run. Basic metadata and local-link checks are not equivalent
  to that validator or a runtime exercise.

Current changes remain local. No GitHub issue status was modified by this audit.

## Later prepublication review

The earlier audit above is historical. The subsequent
prepublication review (historical log deleted at owner request2026-10-20)
identified four findings. Plan-status ownership, parent documentation prompts and
current status pointers have now been corrected per user approval. Mandatory-red
wording versus already-satisfied baseline behaviour remains open for explanation
and decision. No behavioural validation is implied by these documentation fixes.

F2 was subsequently approved and corrected: planning, execution, coordinator,
testing and reviewer guidance now distinguish meaningful red for missing behaviour
from independently confirmed passing evidence for already-satisfied criteria.
The common contract defines evidence, unexpected passes, mixed cases and migration
boundaries. All four prepublication findings have documentation corrections;
runtime enforcement and later configuration loading remain unverified.
