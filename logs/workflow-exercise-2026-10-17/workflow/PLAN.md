# Disposable exercise plan

Specification: EXERCISE.md revision 1.
Batch: EX7-CART; correction cycle 1/2 completed after specification and security re-review; one cycle consumed.
Authorised initial tasks: SETUP, TEST, READY, IMPLEMENT, REVIEW, SECURITY.
Each dependent task requires coordinator-recorded prerequisite evidence.

## SETUP
Owner: implementer. Dependencies: None.
Acceptance: runnable exported stub and types; no tests or feature completion claim.
Completed: Yes — coordinator verified module import and empty input 0; source SHA256 6f487c16f6f76ddf99140a27185163677c710dd3d8b7721178fe409073817cde. Evidence: logs/exercise-implementation-setup.md. 2026-10-17.

## TEST
Owner: testing. Dependencies: SETUP.
Acceptance: public-seam cases map AC1-AC6; actual red or existing-baseline evidence.
Completed: Yes — tester reports 24 cases, 4 baseline passes and 20 behavioural failures, exit 1; tests SHA256 ea6047802612dc83ea79a3b453295c05b935649432a9a94dc970a42dba80ddce. Readiness remains separate. 2026-10-17.

## READY
Owner: specification reviewer. Dependencies: TEST.
Acceptance: independent readiness/coverage report, correct baseline classification.
Completed: Yes — independent reviewer reran 24 tests (4 baseline passes/20 meaningful failures), confirmed AC1/AC6 existing behaviour and AC2-AC5 missing behaviour, no findings; logs/exercise-readiness-review.md. Cycle0/2. 2026-10-17.

## IMPLEMENT
Owner: implementer. Dependencies: READY.
Acceptance: AC1-AC6, passing tests, user/developer guide updates, own task log.
Completed: Yes — implementer reports 24/24 passing with unchanged tests and both exercise guide sections; logs/exercise-implementation.md. Independent REVIEW still required. Source f7cc65aa944c18b8eae018f42d9130f2865ad112feaea476576213e3ff0689a3. Cycle0/2. 2026-10-17.

## REVIEW
Owner: specification reviewer. Dependencies: IMPLEMENT.
Acceptance: implementation, coverage and documentation review of exact snapshot.
Completed: Yes — independent REVIEW passes AC1-AC6 and exercise documentation; 24/24 tests, six manifest hashes stable, no findings. logs/exercise-implementation-review.md. Cycle0/2. 2026-10-17.

## SECURITY
Owner: security reviewer. Dependencies: REVIEW.
Acceptance: final-candidate security report within pure-function boundaries.
Completed: Yes — security reviewer reports no in-scope findings, 24/24 tests and six stable manifest hashes; logs/exercise-security-review.md. Cycle0/2. 2026-10-17.

## Follow-up scenarios
Controlled injection and its repair are separate from initial implementation.
Repair is NOT authorised until human approval of the concrete correction plan.

## INJECT-FAULT
Owner: implementer. Dependencies: SECURITY.
Explicit synthetic stimulus: preserve the good candidate and replace the accumulation
priceCents * quantity with priceCents only. No requirement change; tests remain frozen.
Acceptance: exactly this labelled defect injected, fail evidence preserved, no repair.
Completed: Yes — one-line injection;21/24 pass,3 AC2 failures; implementer stopped without repair. logs/exercise-controlled-fault.md. 2026-10-17.

## CORRECT-1
Owner: implementer after human approval. Dependencies: INJECT-FAULT.
Proposed action: restore price-times-quantity; tests unchanged; required re-review.
Authorisation: user explicitly replied "ok approved" after explanation of CORRECT-1. Cycle1/2 authorised. 2026-10-17.
Completed: Yes — one-line restoration,24/24 tests, independent specification and final security re-review passed at cycle1/2. Good source restored and tests unchanged. Evidence: logs/exercise-correction-1.md, logs/exercise-correction-1-review.md, logs/exercise-correction-1-security.md.