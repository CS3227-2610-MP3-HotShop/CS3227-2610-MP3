# Disposable workflow exercise — 2026-10-17

Tracking: [issue #7](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/7).
Result: covered synthetic scenarios completed with one human-approved correction
cycle. No outstanding implementation or final documentation-review findings.
Bookkeeping discrepancies were observed and corrected; see below.
Issue status was not changed.

## Scope and provenance

A disposable copy of 255 current MP3 working files was used, including uncommitted
workflow changes; source HEAD was 93dbfc63126a69874dc53f8c59d822aa58ac8c72.
The copy had no Git metadata, copied credentials, dependencies or ignored build outputs.
A synthetic typed cart-total function exercised the role workflow; it is not a
migration feature and was never added to MP3 application source or tests.

This chat used generic agent dispatch with workers instructed to read the saved
TOML/common/role files. It did not select native custom agent types. The earlier
user-supplied smoke-test discovery evidence remains separate. Four worker identities
were used for testing, implementation, specification review and security review.
The primary chat coordinated and recorded state; it wrote no application code/tests.
Documentation and evidence were copied back only after source isolation checks.

Runtime: Node v24.19.0, built-in node:test and TypeScript stripping; no packages installed.
Command: node --test tests/cart-total.test.mts.
This is executable behaviour testing, not static TypeScript checking.

## Observed results

| Checkpoint | Observation |
| --- | --- |
| Missing prerequisite | Tester refused TEST while SETUP was incomplete; no writes |
| Test ownership | Tester alone wrote the 24-case suite; hash unchanged through implementation, fault and repair |
| Initial test evidence | 4 baseline passes and 20 meaningful assertion failures; no setup-error substitution |
| Readiness | Independent reviewer reran tests and confirmed existing versus missing behaviour before implementation |
| Initial implementation | 24/24 passed; implementer updated assigned guide sections |
| Implementation/documentation review | Independent reviewer checked source, coverage, both guides, coordinator instructions and SDD summary |
| Final initial security review | No findings within the bounded pure-function contract; 24/24 passed |
| Controlled failure | Deliberately omitted quantity multiplication; 21 passed and 3 failed |
| Approval gate | Implementer stopped; user requested explanation, then explicitly approved the concrete one-line repair |
| Correction cycle 1 | Exact good source restored, tests unchanged; 24/24 passed |
| Independent correction reviews | Specification and final security reviewers each reran 24/24 successfully at cycle 1/2 |
| Final documentation acceptance | Reviewer checked current plan/summary/approval evidence; no outstanding findings |
| Shared-interface routing scenario | Reviewer rejected synthetic approvals and required actual decisions from both owners |
| Exhausted-counter scenario | Given scripted count 2/2, reviewer refused a third fix or task-renaming reset |

The last two rows are labelled scripted scenarios, not evidence of actual two-owner
approval or execution of two complete correction cycles.

## Resolved bookkeeping observations

The coordinator initially left a stale 0/2 in the plan header after approving cycle1;
approval entries and correction dispatches already carried1. It corrected the header.
The final SDD summary also retained a stale initial counter until corrected to1/2;
the documentation reviewer recorded and verified its resolution. Historical stage0
records remain valid and are distinguished from current state.

Therefore persisted metadata was not perfectly consistent throughout, even though
the worker handoffs and actual repair remained within cycle1. At future transitions,
the coordinator should check the current plan, handoff and summary counters together.
No additional code/test correction cycle was used to correct reporting metadata.

## Isolation and evidence

Before reporting changes back, all255 source-file hashes matched the pre-exercise
manifest. Synthetic source/tests remain outside MP3. The MP3 changes from this task
are this report, evidence documents, instructions/guide notes, and interaction records.

- [Exercise specification](../../logs/workflow-exercise-2026-10-17/workflow/EXERCISE.md)
- [Plan and approval state](../../logs/workflow-exercise-2026-10-17/workflow/PLAN.md)
- [SDD summary](../../logs/workflow-exercise-2026-10-17/logs/exercise-sdd-summary.md)
- [Event record](../../logs/workflow-exercise-2026-10-17/exercise-evidence/events.md)
- [Correction plan and approval](../../logs/workflow-exercise-2026-10-17/exercise-evidence/CORRECT-1.md)
- [Independent correction review](../../logs/workflow-exercise-2026-10-17/logs/exercise-correction-1-review.md)
- [Final security recheck](../../logs/workflow-exercise-2026-10-17/logs/exercise-correction-1-security.md)
- [Final documentation review](../../logs/workflow-exercise-2026-10-17/logs/exercise-final-documentation-review.md)

Final source SHA256: f7cc65aa944c18b8eae018f42d9130f2865ad112feaea476576213e3ff0689a3.
Frozen tests SHA256: ea6047802612dc83ea79a3b453295c05b935649432a9a94dc970a42dba80ddce.

## Limits and next steps

Not exercised: native custom-type loading in this run; actual two-cycle exhaustion;
actual shared-interface approvals from two humans; suspected-test-defect reinvocation;
reviewer-disagreement adjudication; static typing, Next.js/browser/database integration,
or broader application security. Reviewer edit restrictions were instructions, not
separately enforced read-only sandboxes. Runtime success is bounded to these scenarios.

Review the combined workflow diff for publication. Any issue closure/publication is
separate; no GitHub writes, commits, pushes or deployments were performed.
