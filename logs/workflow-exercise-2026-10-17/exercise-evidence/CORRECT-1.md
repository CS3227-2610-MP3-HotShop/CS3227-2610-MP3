# CORRECT-1 change plan — awaiting human approval

Batch EX7-CART. Proposed cycle 1 of maximum 2; consumed cycles remain 0.
Scope: disposable function only. Real MP3 application and tests are unchanged.

## Failure evidence
Injected source: 611b91c173a3e18fe71b4274ac2f0f222f76d481d196e46a1785405e2417edf6.
Tests unchanged: ea6047802612dc83ea79a3b453295c05b935649432a9a94dc970a42dba80ddce.
node --test tests/cart-total.test.mts exited1:21 passed/3 failed.
AC2 mixed example returned175 not400; maximum line100000 not10000000;
maximum cart10000000 not1000000000. Evidence: logs/exercise-controlled-fault.md.
This is a labelled synthetic injection, not a naturally occurring implementation bug.

## Cause and proposed edits
Deliberately omitted quantity multiplication in src/cart-total.mts.
Implementation owner restores total += priceCents * quantity.
No tests, requirements, interfaces, dependencies or real application changes.
Tests remain tester-owned and frozen.
Coordinator records approval and cycle state in plan; workers cannot reset it.

## Verification
Implementer reruns same 24 tests; require24/24 green and exact original good source
hash f7cc65aa944c18b8eae018f42d9130f2865ad112feaea476576213e3ff0689a3.
Independent specification reviewer rechecks corrected code/test/documentation evidence.
Final security reviewer rechecks stable corrected candidate.
Coordinator records completion and updates final SDD summary/report.
Correction/re-review share cycle1; extra failed-loop edits require renewed approval.

## Paused and continuing work
Repair and final completion paused. Read-only evidence inspection and reporting may continue.
No inferred approval from original exercise request. Human approval: PENDING.

## Approval recorded
After requesting an explanation and receiving it, the user replied "ok approved".
Approval applies to the one-line restoration, frozen tests, and independent reviews
specified above. Cycle1/2 authorised; no approval for additional corrections inferred.
