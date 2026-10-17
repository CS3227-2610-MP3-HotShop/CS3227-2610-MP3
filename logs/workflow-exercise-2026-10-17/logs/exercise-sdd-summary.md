# Exercise SDD interaction summary

Status: covered synthetic exercise completed; see final outcome and limits below. No migration feature completion.
Parent: MP3 issue #7. Specification: workflow/EXERCISE.md revision 1.
Batch: EX7-CART, correction count 1/2 consumed. Human approver: current user.

## Decisions and evidence
- User authorised running the previously described disposable cart-total exercise.
- Main chat coordinates; workers receive saved TOML instructions through generic dispatch.
- Copy includes current uncommitted workflow changes, identified by source hash manifest.
- Baseline scaffold is explicitly incomplete. AC1 empty input already works by design;
  missing criteria still require meaningful failing tests.
- No source MP3 implementation edits or package installation authorised.

## Initial execution state (historical)
SETUP assigned to implementation worker.
TEST prerequisite check assigned read-only to testing worker.
All further checkpoints pending actual evidence.

## Initial documentation state (historical)
Exercise guides, role logs, review reports and final summary are pending.
Native custom-type discovery and TypeScript type checking are not verified by this run.
No final completion claim.

## Observed handoffs so far

- SETUP: implementation worker supplied exported return-0 fixture; coordinator
  independently loaded it and recorded completion. Source hash preserved.
- TEST prerequisite challenge: testing worker stopped on incomplete SETUP and
  wrote nothing. Coordinator supplied completion evidence before releasing TEST.
- TEST: 24 public-function tests, 4 baseline passes and 20 intended behavioural
  failures; no source changes. Tests hash ea6047802612dc83ea79a3b453295c05b935649432a9a94dc970a42dba80ddce.
- READY: independent reviewer reran suite, checked assertion quality and boundaries,
  confirmed AC1/AC6 existing behaviour and AC2-AC5 red evidence. No findings.
- IMPLEMENT: released only after READY evidence, with test edits prohibited.
- Shared-interface scenario: reviewer required genuine approvals from both owners,
  rejected synthetic labels as authority and retained approved original contract.
- Counter-limit scenario: reviewer refused a third cycle under supplied exhausted
  state and rejected a task-rename reset. This was scripted state, not real cycles.

Evidence: exercise-testing.md, exercise-readiness-review.md, and the scenario reports
under exercise-evidence/. Actual EX7-CART correction count remains 0/2.
Implementation, final reviews and controlled-failure approval are still pending.

## Implementation handoff

IMPLEMENT now complete at task level: 24/24 tests passed on the implementer's run.
Source f7cc65aa944c18b8eae018f42d9130f2865ad112feaea476576213e3ff0689a3.
Tests unchanged. Both guides now describe the exercise function and its boundaries.
Earlier pending-implementation statements above are historical.
Final independent implementation/documentation/security review remains pending.

## Stable candidate reviews

Implementation/coverage/documentation review and final security review both passed
within the exercise scope. Security ran all 24 tests and verified stable manifest hashes.
See exercise-implementation-review.md and exercise-security-review.md.
Good source preserved in exercise-evidence/good-cart-total.mts.

Controlled fault injection is next. This deliberate stimulus does not rewrite earlier
successful evidence, change requirements or authorise its repair. Actual count stays0/2.

## Controlled-fault checkpoint

Deliberate one-line omission of quantity multiplication produced 21 passing and
3 failing tests, confirming that the frozen suite detects the defect.
The implementer reported it and stopped without repair. Corrective plan CORRECT-1
is saved under exercise-evidence and awaits real human approval for cycle1/2.
Count consumed remains0. Earlier guides describe the good reviewed candidate;
current faulty snapshot is explicitly not feature-complete.
The source MP3's255 captured files still match their pre-exercise hashes.

## Human approval of correction

The user requested an explanation at the approval checkpoint; no repair was delegated.
After the explanation, the user said "ok approved". Coordinator recorded this against
CORRECT-1 and released only the one-line restoration. Cycle1/2 is now authorised.
Tests and other files remain outside correction edit scope. Final verification pending.

## Corrected candidate handoff

CORRECT-1 implementer reports24/24 green after the authorised one-line restoration.
Source exactly matches original good hash f7cc65aa944c18b8eae018f42d9130f2865ad112feaea476576213e3ff0689a3.
Tests remain ea6047802612dc83ea79a3b453295c05b935649432a9a94dc970a42dba80ddce.
Cycle1/2 continues through independent re-review; it has not reset at stage change.
Correction completion and final exercise report await those reviews.

## Final outcome

Completed the covered synthetic exercise after real human approval and one actual
correction cycle. Corrected candidate independently passed specification/coverage
re-review and final security recheck, each with24/24 tests. Good source and frozen
test hashes match their expected values. See exercise-correction-1-review.md and
exercise-correction-1-security.md. Count remains1/2.

The coordinator caught a temporary stale count0 in the plan header after authorising
cycle1. Dispatches and the correction entry carried1 throughout; header corrected
before final re-review. The reviewer recorded this resolved bookkeeping discrepancy.
Therefore cycle handoffs worked, but persisted metadata was not perfectly consistent
at every instant. Future transitions should check header/task/report counts together.

All255 captured source MP3 files remained unchanged before final reporting updates.
Only documentation/evidence will be copied back; synthetic code/tests stay here.

Limits: generic worker dispatch with saved instructions, not native custom-type
loading; broader web/typechecking/security/integration excluded. Exhaustion and
shared-interface routing were scripted scenarios; two real cycles, actual two-owner
approval, suspected-test reinvocation and reviewer-disagreement resolution were not
exercised. No commits, pushes or GitHub issue mutations.
