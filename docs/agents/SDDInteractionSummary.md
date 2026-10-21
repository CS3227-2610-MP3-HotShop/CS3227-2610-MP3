# SDD interaction summary

Use one feature-level summary under logs/, named YYYY-MM-DD-feature-<issue-number>-sdd-summary.md.
Coordinator consolidates it after final reviews pass; maintain only one short current handoff during implementation. Subagents do not write summaries or task logs.
Use a stable feature identifier if an issue number is not assigned yet.
Do not create a completed-feature summary for unimplemented work.

This consolidates final approvals and docs/InteractionRecord.md. Link existing evidence rather than
duplicating transcripts or specification text. Record concise engineering rationale,
not private reasoning, secrets, or unrelated personal conversation.

## Summary template

### Feature and current status

Parent issue, owner, scope, current status, and related task/PR links.
Distinguish implemented, reviewed, accepted, and still pending.

### Specification baseline and evolution

Product/technical spec versions or commit links; acceptance criterion IDs.
Record approved changes and superseded decisions with approval references.

### Decisions and interactions

| Date | Question or discovery | Decision and rationale | Human approval / evidence | Affected tasks |
| --- | --- | --- | --- | --- |

Summarise consequential human/agent exchanges, including unresolved questions.
Agent proposals alone are not human approval.

### Plan and execution

Link plan versions/task IDs, assigned roles, handoffs, changes delivered, and
supporting per-task logs. Explain newly discovered work and its dependencies.
Preserve completed entries when follow-up work is needed.

### Verification and review

| Criterion | Implementation / test reference | Actual result | Review evidence / limitations |
| --- | --- | --- | --- |

Record actual checks and snapshots. Report failed and unperformed checks honestly.
Include required readiness, coverage/implementation, integration and security evidence.

### Corrections and approval history

Link findings, approved correction plans, owners, resulting checks and the shared
cycle count. Link related follow-up tasks; new issue IDs do not reset the count.

### Documentation and completion

Link updated User Guide, Developer Guide and AGENTS.md sections, final acceptance
evidence, and outstanding work. A feature is not Done until required acceptance,
reviews, both guides, agent instructions and this summary are complete.

## Ownership

The coordinator maintains this feature summary from attributed worker evidence and
human decisions. The specification reviewer independently checks traceability and
missing evidence before feature Done. Other agents provide inputs; shared summary
writes follow the common contract's single-writer assignment.
