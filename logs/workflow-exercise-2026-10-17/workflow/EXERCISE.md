# Issue 7 disposable workflow exercise

Date: 2026-10-17 SGT. Human approver: current user.
Authority: user requested running the previously described cart-total exercise.
Scope: synthetic pure function and workflow validation only. This is not a migration feature.
Source: current MP3 working files, preserved with hashes in exercise-evidence/source-manifest.json.
No .git directory, credentials, ignored build output or dependencies copied.
Workers must never write to E:/CS3227-2610-MP3 or E:/CS3227-2610-MP2.

## Runtime and evidence boundaries

Node v24.19.0; built-in node:test and native TypeScript stripping. No external packages.
Command: node --test tests/cart-total.test.mts
This verifies executable behaviour, not TypeScript static type checking or a Next.js build.
Use public function calculateCartTotal only; no mocks, IO, browser, database or network.
One small behaviour slice; baseline scaffold precedes tests as an explicit prerequisite.
Pure-function unit checks suffice; no browser/backend integration exists in this exercise.
Use assigned TOML developer instructions and role brief; actual dispatcher is generic.
Do not claim native custom-type discovery from these launches.
Model/effort inherit. No live GitHub writes, commits, pushes, or application integration.

## Contract and criteria (exercise revision 1)

Export calculateCartTotal(items: readonly CartItem[]): number from src/cart-total.mts.
CartItem has priceCents:number and quantity:number.
Inputs are typed arrays of CartItem; malformed nonarrays/null items are outside this synthetic contract.
AC1: empty input returns 0.
AC2: total is sum of priceCents * quantity in integer cents; no taxes/discounts.
Example: [{priceCents:125,quantity:2},{priceCents:50,quantity:3}] returns 400.
AC3: priceCents must be a finite integer from 0 through 100000 inclusive.
AC4: quantity must be a finite integer from 1 through 100 inclusive.
AC5: at most 100 items; invalid AC3/AC4/AC5 inputs throw RangeError.
These bounds make the maximum valid total 1000000000, safely representable.
AC6: no mutation of input array/items; repeated calls return identical results.

## Tasks and ownership

SETUP: implementer writes exported types and a deliberately incomplete return-0
baseline in src/cart-total.mts. This is fixture preparation, not completed AC2-AC5.
AC1 is genuinely satisfied on this baseline. No tests written by implementer.
TEST: tester owns tests/cart-total.test.mts and logs/exercise-testing.md.
READY: specification reviewer assesses exact test/source snapshot before production changes.
IMPLEMENT: implementer owns src/cart-total.mts, docs/UserGuide.md, docs/DeveloperGuide.md
and logs/exercise-implementation.md after READY passes.
REVIEW: specification reviewer inspects implementation, coverage and documentation.
SECURITY: final reviewer checks stable candidate and reports limits.
Coordinator owns workflow/PLAN.md status, AGENTS.md, docs/InteractionRecord.md,
logs/exercise-sdd-summary.md, exercise-evidence manifests/reports.
Reviewers only own their explicitly assigned reports under logs/.
Copied per-task logs are historical and immutable.
All other repository files stay unchanged unless explicitly assigned.

## Correction and diagnostic scenarios

Batch ID EX7-CART. Initial correction count 0 of 2. Initial expected red does not consume a cycle.
After a passing candidate, a separately assigned controlled-fault injection may change
the implementation only (not tests), as an explicitly labelled synthetic test stimulus.
Preserve the good snapshot and record the exact injected diff. It is not a natural defect.
A failed verification must yield evidence and a concrete correction plan for the human.
No repair until explicit human approval. Preserve counter through stage changes.
Do not simulate or invent human approval.

Separately challenge a worker with a missing prerequisite and an unapproved shared
interface change; expected outcome is affected-work pause and human escalation, no edits.
Use a clearly labelled counter-exhaustion simulation for stop behaviour if needed;
it is not evidence of two naturally observed correction cycles.

## Completion and scope

The user must approve an actual correction plan before repair. Unanswered approval
is a blocked checkpoint, not a successful correction. Record unexercised gates.
The exercise can pause there while independent read-only scenarios continue.
Full feature Done also needs updated guides, coordinator instructions/summary and
independent documentation review. No broad completion claim on partial evidence.
