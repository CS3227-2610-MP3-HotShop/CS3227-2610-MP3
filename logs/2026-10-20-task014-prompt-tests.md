# Agent Interaction Log

## User Prompt

"I approve of the recommendation to strengthen the prompt, change the prompt and
run the full corpus test again."

The coordinator assigned only `src/test/java/hotshop/llm/LlmRequestTest.java` and
this log. The approved change is recorded in
`logs/2026-10-20-task014-prompt-correction-plan.md`.

## Steps Taken

Read the approved exceptional correction plan, current testing-role contract,
existing request tests and fixed production prompt, and the public original
`attack-internals` corpus row. Updated the expected request instruction to include
the exact approved omission/no-questions guidance before the existing final
"Return only the description." sentence.

Added a request regression using the original attack title and FAIR condition.
Added three benign technical-product cases: System design textbook, Database
systems textbook and Shell programming handbook. These assert that the request
still contains only the existing model/input envelope and title/condition JSON
facts, preserving the original title and condition. Existing assertions remain.

## Reasoning Summary

The fixed-instruction contract detects whether the approved trusted guidance is
present while maintaining untrusted product facts as separately encoded JSON.
Benign technical vocabulary remains eligible rather than being filtered. These
tests establish request construction only; they cannot establish whether a real
model omits the attack or produces a suitable grounded description. The unchanged
live corpus and human semantic review remain necessary.

## Changes Made

- `src/test/java/hotshop/llm/LlmRequestTest.java`: strengthened expected prompt
  contract and four added request cases.
- This log: actual baseline evidence and handoff.

No production code, corpus, private configuration or live evidence was changed
or accessed by this worker.

## Verification

Executed `.\gradlew.bat test --tests hotshop.llm.LlmRequestTest` with approved
Wrapper escalation. Compilation succeeded. The expected baseline red run exited
1: 27 executed, 16 failed, 11 passed, no errors or skips. All 16 failures occur
at the shared fixed-instruction prefix assertion (`LlmRequestTest.java:211`)
because production still uses the earlier prompt. The 11 passing cases cover
existing invalid-input/configuration and request-boundary behavior. No setup or
compiler failure was counted as red.

Inspected XML totals, checked edited line lengths and ran `git diff --check` for
the assigned test; no line-length or whitespace issue was found. Checkstyle and
post-production focused tests were not run by this worker in this phase.

Stable test SHA-256:
`B6F65AE1BC75BE1E8C7B76DC30941B6E23F999A5969947EE42B5F4D6168FE293`.

## Final Output and Conclusion

Expected prompt-contract red is ready for independent test-readiness review.
Root received the exact command, counts, failure location and stable test hash.
Production implementation awaits the required readiness clearance; semantic
acceptance awaits the separately authorized full live run and human review.

Historical correction budget remains 2/2 plus the explicitly approved bounded
exceptional cycle 3; it was not reset. No unapproved further correction was made.
Timing: 2026-10-20 SGT; baseline XML timestamp 07:35:16 UTC. Usage: N/A.
