# Agent configuration and startup

Status: all four custom types launched successfully in the user-supplied 2026-10-17 smoke-test report. Scoped generic-dispatch behaviour is recorded in [WorkflowExercise.md](WorkflowExercise.md). Isolated instruction loading and native loading after instruction edits remain unverified.
Tracking: [issue #5](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/5).

## Worker configurations

| Agent name | Config | Detailed role |
| --- | --- | --- |
| hotshop_testing | [.codex/agents/hotshop_testing.toml](../../.codex/agents/hotshop_testing.toml) | [Testing](agents/testing.md) |
| hotshop_implementer | [.codex/agents/hotshop_implementer.toml](../../.codex/agents/hotshop_implementer.toml) | [Implementer](agents/implementer.md) |
| hotshop_specification_reviewer | [.codex/agents/hotshop_specification_reviewer.toml](../../.codex/agents/hotshop_specification_reviewer.toml) | [Specification/coverage](agents/specification-reviewer.md) |
| hotshop_security_reviewer | [.codex/agents/hotshop_security_reviewer.toml](../../.codex/agents/hotshop_security_reviewer.toml) | [Security](agents/security-reviewer.md) |

The configurations reference the role briefs and common contract. Model and reasoning settings inherit from the parent; no global settings were changed.

The main chat acts as coordinator. Required assignment inputs must be supplied before work starts.

## Coordinator startup brief

Use the assigned isolated repository checkout as the working directory. Read
AGENTS.md, docs/agents/AgentSpecifications.md, and
docs/agents/agents/coordinator.md. For migration work also read
workflow/MIGRATION-WORKFLOW.md. Validate the supplied approved task set, specs,
plan version, prerequisite evidence, role/file ownership, human approvers,
permitted checks, and shared correction-cycle count.

Report missing inputs and pause affected work. With eligible authorised tasks,
act as coordinator: delegate to the four named workers above in the agreed order,
retain their IDs for follow-ups, report progress, and consolidate evidence.
Never write application code or tests. Route test reinvocations to the assigned
testing worker when direct routing is unavailable. Schedule within actual available
concurrency; four configured roles do not require four simultaneously active workers.
Apply approved correction plans and human-owner routing without bypassing gates.

## Validation and limits

Configuration discovery depends on the installed client and trusted project
configuration. Confirm the named agents are discovered in the MP3 session before
using them. Merely creating files does not prove discovery or instruction loading.
The CLI inspected on 2026-10-17 reports 0.162.0-alpha.17.2; the sandboxed shell also
reported that its home directory could not be found. Verify discovery in the actual project session.

Custom agents inherit parent permissions. Review-only instructions are behavioural
constraints, not separate enforced read-only sandboxes. No config here changes
sandbox or approval policy. See [official subagent documentation](https://learn.chatgpt.com/docs/agent-configuration/subagents).

The smoke-test report and subsequent exercise establish different evidence:
reported native launches and instruction receipt versus observed generic-dispatch
handoffs, ownership, correction approval and reporting. Preserve these boundaries. No feature implementation was started by writing these files.

The CLI diagnostic could not load configuration because CODEX_HOME could not be
resolved in this shell. Agent discovery was therefore not verified by that check.

## 2026-10-17 smoke-test evidence

Source: user supplied the result from a separate MP3 chat; this chat did not independently inspect its tool trace.

| Configured type | Reported completed agent |
| --- | --- |
| hotshop_testing | /root/smoke_testing |
| hotshop_implementer | /root/smoke_implementer |
| hotshop_specification_reviewer | /root/smoke_specification |
| hotshop_security_reviewer | /root/smoke_security |

The report verifies sequential custom-type selection and completion without generic substitutes. Workers reported reading repository instructions, the common contract, migration workflow, and role briefs. Their reported ownership, human approvals, and two shared correction cycles matched the intended workflow.

Limits: document loading is worker self-report; exact TOML provenance was not independently traced; inherited context prevents an isolated loading claim. Failure handling was described, not exercised. No writes were issued by the smoke-test commands, but the workspace changed concurrently. The decision-handoff-upkeep log was created by this originating chat; therefore a globally unchanged working tree is not claimed.

Follow-up: aligned the implementer TOML with its existing brief by permitting explicitly assigned documentation/evidence alongside production code. Tests remain tester-owned; reviewer reports remain protected. The subsequent generic-dispatch exercise used the updated instructions, but native loading was not retested. This smoke test establishes reported discovery/launch readiness only.

## Subsequent disposable exercise

The [exercise report](WorkflowExercise.md) records actual testing/implementation/review
handoffs and a human-approved correction using the current saved instructions.
Workers used generic dispatch, so these results do not replace native custom-type
discovery evidence. One actual cycle and scripted routing/limit scenarios were exercised;
the report distinguishes observations, resolved bookkeeping defects and untested paths.

## Setup acceptance scope

Issue #5 covers the four migration worker types and main-chat coordinator.
Single-agent comparison entry instructions are outside the current migration scope.
The reported four-type smoke test satisfies the launch check with its stated
self-report/provenance limits; a new isolated loading experiment is not silently
added as a setup requirement. Revalidate native loading when configuration changes
affect a future execution environment. Available concurrency is runtime-dependent;
this coordinator session exposes four total active slots including the coordinator,
so schedule at most three simultaneous workers here. Recheck limits in other clients.
