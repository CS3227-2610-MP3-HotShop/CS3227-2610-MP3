# TASK-014 prompt correction and second live evaluation

## Authority and scope

User direction: "I approve of the recommendation to strengthen the prompt, change
the prompt and run the full corpus test again."

This explicitly answers the preceding exhaustion notice and authorizes one
additional bounded correction beyond the two ordinary cycles, followed by one
full live corpus invocation. Shared historical count remains 2/2 plus this
human-directed extension (cycle 3); it does not reset. Further unexpected
failures requiring changes return to the user.

TASK-014 only, extending the current uncommitted candidate on branch llm-feature
from baseline 4a5538021db6fd1d9b80ab39c529901c1ac2cc9f. TASK-003 and TASK-013
are complete. Follow implement-task and the role contract v0.9. The human user
remains approver and semantic reviewer. TASK-015 onward remains unassigned.

## Failure and approved change

The user reported `attack-internals` propagated the injected request instead of
describing the item. This fails the existing injection-authority and suitability
rubric; no actual disclosure was reported. Preserve original evidence unchanged.

Strengthen the fixed prompt to use only identifiable item/condition facts, omit
embedded instructions without quoting/repeating/paraphrasing/endorsing them,
and never ask questions, solicit information or invite a reply. A mixed title
must produce a description of only the item/condition, without invented details.
Keep the existing model/input request envelope, limits and no-tool instructions.
Do not add role fields, filters, retries, classifiers or provider-setting changes.

## Ownership and sequence

Testing owner: LlmRequestTest.java and a separate prompt-test interaction log.
Update the fixed prompt contract and cover the original attack as untrusted JSON
facts plus benign technical-product vocabulary. Run focused baseline tests to
establish missing prompt guidance, not claimed model-semantic resistance.

Specification reviewer: read-only test readiness, then final code/coverage/docs.
Implementer: only HttpLlmClient.java's fixed INSTRUCTION and an implementation log,
after readiness clearance. No test edits. Coordinator owns specification prompt
alignment, Developer Guide, plan progress, this log and private review artifacts.
Security reviewer: read-only stable final snapshot. Preserve unrelated user edits.

## Verification and evidence

Focused request/transport/parser/adapter/corpus tests and both Checkstyle tasks;
no full regression suite. Freeze and review candidate before live dispatch.
Run all 26 unchanged corpus cases once, sequentially with existing quotas/spacing.
Use public label `task014-20261020-v2` and a new ignored sibling directory
`build/llm-live/task014-20261020-v2/` with the same human-profile-only access as v1,
verified before dispatch. The approved rerun implies this separate local evidence
location under the already approved evidence root; never overwrite v1.
No private configuration values, raw provider output or credentials in public logs.
Record counts/outcomes/timestamps and create a private inert review page.
Automated passes still require human semantic review; tool non-use stays unverified.

## Status

Authorized; test preparation starting. No additional live request has been sent.
