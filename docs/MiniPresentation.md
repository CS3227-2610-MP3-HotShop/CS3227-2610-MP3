# Mini-presentation working record

Last updated: 2026-10-15 (SGT)

Status: coordinator, implementer, and testing roles written separately; specification/coverage review is now agreed at two checkpoints; security review is agreed at the end; the single-agent baseline sequence and approval gates are now agreed. Failed-loop changes now require user-approved correction plans. No benchmark implementations or agent runs completed.

This is the living record for the mini-presentation, from the existing SDD skills through proposed agent assignments, implementation experiments, measurements, and reflections. Update it as this work proceeds. Preserve original observations and record corrections explicitly. Link substantial specifications, diffs, logs, and reports rather than duplicating them here.

This record was originally drafted in the MP2 workspace and copied into MP3 with the agreed role specifications. Historical logs retain their original workspace context. This documentation does not implement features or start benchmark runs.

## 1. Presentation purpose and synopsis

**Working title:** One Agent or a Team? Comparing AI Implementation Workflows in HotShop

**Research question:** For a bounded HotShop feature, does a multi-agent workflow improve implementation quality enough to justify its coordination, resource, and human-effort costs?

**Synopsis:** Using a bounded feature from HotShop, our marketplace project, we will compare a single agent responsible for implementation and verification with a coordinator working with an implementation agent and independent reviewers. Both approaches will receive the same approved specification and starting code. Results will be evaluated against predefined acceptance and security checks, considering correctness, elapsed time, human intervention, and resource usage where available. The presentation will examine how shared specifications, task boundaries, and review handoffs affect the outcome. Findings will be presented as a small case study with explicit limitations.

**Format:** approximately 8 minutes of presentation and 2 minutes of Q&A.

**Signup deadline supplied by the user:** 16 October, 2pm. Tutorial week, presentation date, and permitted lecture coverage still need confirmation.

### Agreed direction

- Keep the exercise useful for actual MP3 development.
- Include implementation as a comparison, rather than only describing a proposed workflow.
- Maintain a separate Markdown record of findings, agent specifications, implementation work, and metrics.

### Proposed boundaries, not yet finalised

- Start with one feature, implemented independently by each workflow.
- Add a second feature only if time permits; use both workflows on each feature for a paired comparison.
- Keep full application deployment and broad performance claims outside the presentation.
- Earlier rough preparation estimate: 5-7 hours of active work if the feature is small and the environment already works. This is a planning estimate, not a measurement.

## 2. Evidence register

| ID | Evidence | Status and limits |
| --- | --- | --- |
| E01 | [MP3 commit 227ba99](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/commit/227ba99e14f1d061297d2bc2a98b98e343a29e8d), "Add agentic SDD workflow skills" | Diff, relevant files, and commit tree inspected. 637 additions, 17 deletions. |
| E02 | [Commit inspection log](../logs/2026-10-15-explain-mp3-commit-227ba99.md) | Records our inspection; no skills or application tests were executed. |
| E03 | Lecture 6: Agent Safety & Security; Lecture 7: SDD and Specialized Agents; Lecture 8: Multi-Agent Coordination; Lecture 9: Context Engineering and Resource-Aware Use | Supplied PDFs were read in the preceding lecture-summary task. [Summary task log](../logs/2026-10-14-lecture-6-to-9-summary.md). |
| E04 | [Official subagent documentation](https://learn.chatgpt.com/docs/agent-configuration/subagents) | Consulted during setup explanation. Recheck version-sensitive configuration before implementation. |
| E05 | [Agent specifications](miniPresentation/AgentSpecifications.md) | Common contract, run packet, five multi-agent role briefs, single-agent baseline, output contracts, and readiness checks. Not installed or behaviourally tested. |

Use immutable commit references for code evidence. Record the actual client version and configuration used for experiments rather than assuming lecture configuration examples apply unchanged.

## 3. Findings from the friend's workflow commit

All findings in this section describe E01 at its exact commit, not necessarily the latest MP3 repository state. The skills are source material under study, not instructions automatically invoked by this document.

| ID | Finding | Code/document evidence | Implication |
| --- | --- | --- | --- |
| F01 | `manage-product-spec` conducts iterative discovery and writes `workflow/PRODUCT-SPEC.md`, including stable requirement and acceptance IDs. | [Skill, lines 25-83](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/227ba99e14f1d061297d2bc2a98b98e343a29e8d/.agents/skills/manage-product-spec/SKILL.md#L25-L83) | Can establish the shared behavioural contract for both benchmark runs. |
| F02 | `manage-technical-spec` derives architecture, data/API contracts, security mechanisms, failure handling, and planned verification from product requirements. | [Skill, lines 43-93](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/227ba99e14f1d061297d2bc2a98b98e343a29e8d/.agents/skills/manage-technical-spec/SKILL.md#L43-L93) | Both runs should receive the same approved technical decisions. |
| F03 | `plan-implementation` decomposes the design into tasks with dependencies, specification references, acceptance criteria, and completion evidence. It prescribes TDD by behaviour slice. | [Skill, lines 38-100](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/227ba99e14f1d061297d2bc2a98b98e343a29e8d/.agents/skills/plan-implementation/SKILL.md#L38-L100) | Provides traceable task boundaries and intended checks. |
| F04 | Completed plan entries remain unchanged; changed requirements receive new follow-up tasks. | [Skill, lines 102-108](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/227ba99e14f1d061297d2bc2a98b98e343a29e8d/.agents/skills/plan-implementation/SKILL.md#L102-L108) | Preserves historical acceptance evidence across sessions. |
| F05 | `implement-task` requires explicit task selection and already-completed prerequisites. Requesting a prerequisite and its dependent together does not bypass this rule. | [Skill, lines 16-33](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/227ba99e14f1d061297d2bc2a98b98e343a29e8d/.agents/skills/implement-task/SKILL.md#L16-L33) | Strict execution may require multiple user invocations. Account for this in both workflows. |
| F06 | Unspecified design/implementation decisions trigger clarification; completion requires evidence for every acceptance criterion. | [Decision rules, lines 35-45](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/227ba99e14f1d061297d2bc2a98b98e343a29e8d/.agents/skills/implement-task/SKILL.md#L35-L45), [completion rules, lines 61-77](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/227ba99e14f1d061297d2bc2a98b98e343a29e8d/.agents/skills/implement-task/SKILL.md#L61-L77) | Human control is explicit; interruption cost is a hypothesis to measure, not an observed result. |
| F07 | This commit adds skills, documentation, and logs, not application behaviour or multi-agent orchestration. `workflow/` contains only `.gitkeep`. | [Commit diff](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/commit/227ba99e14f1d061297d2bc2a98b98e343a29e8d), [workflow tree](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/tree/227ba99e14f1d061297d2bc2a98b98e343a29e8d/workflow) | Skills do not establish that multiple agents were launched or that the workflow was exercised. |
| F08 | Developer Guide points to `.agents/implement-task/SKILL.md`; actual committed path includes `/skills/`. | [Guide, line 101](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/227ba99e14f1d061297d2bc2a98b98e343a29e8d/docs/DeveloperGuide.md#L101), [actual skill](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/227ba99e14f1d061297d2bc2a98b98e343a29e8d/.agents/skills/implement-task/SKILL.md) | Documentation mismatch observed; not fixed in this task. |
| F09 | `AGENTS.md` removes the previous issue-tracker, triage-label, and domain-document routing section. | [Commit diff](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/commit/227ba99e14f1d061297d2bc2a98b98e343a29e8d) | Record as an instruction change; the diff alone does not establish why it was removed. |

The commit's interaction logs report structural skill validation and explicitly state that real workflow execution was not tested. These are the original logs' reports, not verification we independently reran.

## 4. Proposed subagent specifications

Status: [coordinator](miniPresentation/agents/coordinator.md), [implementer](miniPresentation/agents/implementer.md), and [testing](miniPresentation/agents/testing.md) specifications are written separately. The [role index](miniPresentation/AgentSpecifications.md) also links the agreed [specification/coverage reviewer](miniPresentation/agents/specification-reviewer.md) and [security reviewer](miniPresentation/agents/security-reviewer.md). The [single-agent baseline](miniPresentation/agents/single-agent.md) now records the agreed equivalent self-review sequence; the table below summarises the multi-agent roles. No custom agents have been installed or launched. Feature-specific inputs, task authorisation, budgets, and behavioural checks remain pending before freezing the run configuration.

All agents should use the same approved feature specification. Role-specific task briefs narrow responsibility; they must not redefine product requirements independently.

| Role | Inputs | Responsibility and output | Boundaries |
| --- | --- | --- | --- |
| Coordinator | Approved specs, selected tasks, run protocol | Assign tasks, manage dependencies, reconcile reports, maintain evidence and handoffs | No silent requirement changes; do not bypass task-selection or prerequisite rules. |
| Testing agent | Approved specs, existing interfaces/conventions, assigned task | Write specification-derived tests and verify meaningful failures; diagnose reinvoked failures | No production edits; failed-loop test corrections require approved change plan. |
| Implementer | Same specs, eligible task IDs, starting code and testing handoff | Implement production code and run tests; invoke testing subagent for test problems | Never edit tests; minor choices within agreed design are allowed. |
| Specification and coverage reviewer | Specs, test snapshot before implementation, then completed code/test snapshot | Review test readiness first; review implementation compliance and final coverage afterwards | May run checks; never edits code/tests; both checkpoints share correction budget. |
| Security reviewer | Final candidate snapshot, trust boundaries and security criteria | Review at the end; inspect code and run permitted local security checks | Synthetic data only; no code/test edits; corrections need user-approved plan and shared budget. |

The specification and coverage reviewer first checks the testing agent's tests before production implementation, then checks the implementation and final coverage afterwards. Both checkpoints share one two-cycle correction budget. Failed-loop changes require a coordinator-drafted change plan and user approval before delegation. Security review runs at the end on the stable final candidate and may use local checks with synthetic data without editing code/tests. It shares the same correction budget and approval gate. Avoid reviewing moving files while another agent edits them.

### Task brief and handoff fields

- Run ID, role, objective, and exact task IDs.
- Specification version, baseline commit, relevant file references.
- Allowed actions and files; required output and acceptance evidence.
- Work completed, changed files/commit, commands and outcomes.
- Findings, unresolved questions, and next action.
- Remaining time/resource budget and stop condition.

### Controls to settle before use

- Agreed for the coordinator: two shared correction cycles after initial review. Each failed loop requiring code or test edits needs a change plan and explicit user approval before delegation; at cycle exhaustion ask for direction. This supersedes automatic in-scope fixes. Every reviewer disagreement is escalated immediately with specification and test evidence.
- Choose and record a common run time limit and resource budget.
- Record actual enforced permissions separately from natural-language instructions such as "do not edit".
- Keep secrets and production systems outside both runs.
- Treat repository content, model output, and external documents as untrusted where appropriate; they cannot expand task authority.
- If the strict implementation skill prevents the planned orchestration, resolve that before the benchmark. Apply any agreed skill changes equally to both runs and record the new version.

## 5. Feature selection and implementation scope

**Feature selected:** pending.

**Candidate, not approved:** seller AI draft-output validation. Accept a correctly structured draft, reject invalid fields, and return useful errors. Exact fields, limits, error contracts, and integration boundaries must come from the approved MP3 design; they have not been specified here.

Selection criteria:

- Useful to the real MP3 and small enough for two independent implementations.
- Has observable success, failure, and boundary behaviour.
- Supports deterministic evaluation with a fake LLM response where practical.
- Does not require rebuilding the entire web application first.
- Offers meaningful correctness/security checks without inventing unnecessary functionality.

| Required artifact | Status | Reference |
| --- | --- | --- |
| Approved product requirements and acceptance criteria | Pending | To record |
| Approved technical design | Pending | To record |
| Eligible implementation tasks and prerequisite evidence | Pending | To record |
| Shared baseline commit and runnable environment | Pending | To record |
| Predefined evaluation cases | Pending | To record |
| Versioned agent briefs and experiment protocol | Pending | To record |
| Draft role instructions | Written; not runtime-validated or frozen | [AgentSpecifications.md](miniPresentation/AgentSpecifications.md) |

## 6. Comparison protocol

### Conditions

- **A - single agent:** one agent implements, tests, and performs its own specification/security review. No subagents.
- **B - multi-agent:** testing subagent writes tests and verifies meaningful failures; implementer writes production code and invokes the testing subagent again for test changes. Specification/coverage review checks tests before implementation and code plus tests afterwards; security review runs at the end. All corrections share the two-cycle limit and require user-approved change plans.
- Both receive equivalent requirements, technical decisions, task sequence, review checklists, tools, permissions, and evaluation opportunities.
- Use the same model and reasoning settings where supported. Record deviations; otherwise the comparison also measures model/configuration differences.
- Use isolated worktrees/checkouts at the same baseline. Do not expose either run to the other's code or reports.
- Record shared preparation separately from per-run setup. Record which run occurred first and possible learning effects.
- Fix evaluation cases before either run. Agents may write their own tests, but an independently maintained evaluation suite determines reported acceptance results. Do not let implementations edit that suite.
- Log clarifications. If an answer changes the spec materially, invalidate or restart affected comparisons under the same revised spec; do not give one run a better specification without disclosure.
- Record dependency/environment failures separately from implementation failures; include their time rather than silently discarding it.
- Save an initial implementation snapshot and a final reviewed snapshot when feasible, making review benefits observable.
- Select one implementation for integration after evaluation. Merging is outside the timed benchmark and requires the normal project review.

### Workspace isolation

Confirmed actual development checkout: `E:/CS3227-2610-MP3`. Read-only inspection on 2026-10-15 found a clean working tree on `master` at `227ba99e14f1d061297d2bc2a98b98e343a29e8d`. This is an observed candidate baseline, not a frozen benchmark choice. The user has not created a single-agent directory. Proposed sibling locations are `E:/CS3227-2610-MP3-benchmark-single` and `E:/CS3227-2610-MP3-benchmark-multi`; neither has been created by this workflow.

The user agreed that both benchmarks should run separately from actual MP3 development, with the chosen implementation integrated after evaluation. Planned setup: two standalone clones of the same frozen MP3 commit, one for each benchmark, leaving the actual development checkout outside both runs. Exact paths and clone setup remain pending; no directories have been created.

For example, use sibling folders named `MP3-benchmark-single` and `MP3-benchmark-multi` under a separately chosen benchmark location. Standalone clones have separate Git metadata; worktrees are an alternative with separate working files but shared repository metadata. Record the chosen arrangement before execution.

Give each run separate application data, synthetic databases, generated outputs, and local service resources where relevant. Verify the actual MP3 configuration before choosing data paths or ports. Directory separation is not an enforced security sandbox: record actual write permissions and shared caches/resources. Keep run contexts and reports separate to prevent cross-run contamination. Neither run may merge or push into actual MP3 during the benchmark; selecting and integrating a result happens after evaluation.

### Procedure

1. Finalise scope, specs, shared prerequisites, evaluation cases, budgets, and agent instructions.
2. Verify the starting environment and record the immutable baseline commit.
3. Create isolated A and B workspaces and fresh agent contexts.
4. Run A and B according to the fixed protocol, capturing timestamps, interventions, artifacts, and usage when available.
5. Freeze each final commit. Evaluate both with identical independent checks.
6. Validate review findings; deduplicate reports describing the same defect.
7. Compare quality, time, resources, and human effort. Preserve incomplete and failed runs.
8. Choose the implementation to integrate and document limitations and reflections.

One run per workflow on one feature is a case study. Repeated paired runs or additional features improve confidence but are not yet committed scope. Different features assigned to different workflows are not a controlled comparison.

## 7. Metrics and collection

Prioritise correctness and security; report efficiency alongside them rather than awarding a win purely for speed. Do not invent a combined score or weights after seeing results.

| Metric | Definition | Collection and interpretation |
| --- | --- | --- |
| Acceptance coverage | Passed predefined acceptance criteria / total applicable criteria | Map criteria to evaluation checks. Count criteria rather than inflating the denominator with many similar tests. |
| Security outcomes | Pass/fail for each predefined misuse case; confirmed unresolved defects by agreed severity | Record expected and actual behaviour. Passing selected cases does not establish complete security. |
| End-to-end elapsed time | Final submission/stop timestamp minus initial task-dispatch timestamp | Includes orchestration, reviews, fixes, tool waits, and human waits. Record timestamps in SGT or UTC consistently. |
| Time breakdown | Implementation, review, fixes, environment waits, and human waits | Parallel intervals overlap: do not sum worker durations and call that elapsed time. |
| Human active time | Minutes spent answering, steering, inspecting, and correcting during a run | Timer/manual log; separate common preparation and final independent evaluation. |
| Interventions | Count by clarification, permission, scope correction, environment repair, and defect correction | Count distinct events, not every message. Include whether the event was necessary and useful. |
| Token/resource use | Aggregate usage across coordinator and all subagents | Record input, cached input, output/reasoning fields if exposed; avoid double-counting included categories. Use N/A when unavailable. |
| Monetary cost | Actual reported cost, or a clearly labelled estimate based on recorded usage and dated rates | Subscription usage is not automatically a per-run dollar cost. Do not infer tokens from word counts. |
| Rework | Correction rounds and confirmed defects fixed after first implementation | Separate pre-review and final results. More findings alone does not mean better final quality. |
| Review usefulness | Confirmed unique actionable findings / total unique actionable findings reported | Human adjudication required. Also record rejected/duplicate findings and defects the reviewers missed. |
| Scope compliance | Unrequested changes, altered requirements, or weakened valid tests | Compare final diff with approved scope; explain each deviation. |
| Handoff quality | Missing or incorrect facts that caused repeated work or clarification | Record concrete incidents and evidence; keep qualitative judgments explicit. |

Suggested efficiency calculations, only for comparable completed runs:

- Speedup = single-agent elapsed time / multi-agent elapsed time.
- Human-time saving = single-agent active human minutes - multi-agent active human minutes.
- Resource ratio = multi-agent total tokens / single-agent total tokens, if both totals are available and comparable.

If one workflow is faster but fails requirements, report that trade-off rather than treating it as a successful speedup.

## 8. Run records and results

No runs have been performed. Blank or pending cells are not zero measurements.

| Field | A - single agent | B - multi-agent |
| --- | --- | --- |
| Run ID / date / order | Pending | Pending |
| Feature and spec version | Pending | Pending |
| Baseline commit / workspace | Pending | Pending |
| Model, reasoning, client version | Pending | Pending |
| Agent instructions / permissions | Pending | Pending |
| Time/resource limits | Pending | Pending |
| Start / finish / stop reason | Pending | Pending |
| Initial / final implementation commit | Pending | Pending |
| Acceptance results | Not run | Not run |
| Security results | Not run | Not run |
| Elapsed / human active time | Not measured | Not measured |
| Usage / cost | Not measured | Not measured |
| Review findings / fixes / interventions | Not measured | Not measured |
| Commands, reports, and logs | Pending | Pending |

### Observation ledger

Add one entry per meaningful event: ID, run, timestamp, observation, evidence reference, impact, human decision, and resolution. Separate measured facts from hypotheses and interpretations. Never overwrite an unsuccessful result with the outcome of a later rerun.

### Implementation ledger

For each task record: task ID, run ID, spec references, files/commit, tests actually executed, acceptance status, unresolved issues, and whether selected for integration. No entries yet.

## 9. Interpretation and presentation evidence

Questions to answer after the runs:

- Did independent review catch an issue the implementer missed?
- Did the same criteria remain clear across agent handoffs?
- How much feedback was duplicated or incorrect?
- Did strict task gates prevent an error or create avoidable interruption?
- Where did parallelism save elapsed time, and where were stages necessarily sequential?
- Was any quality improvement worth the additional resources and human effort?
- What would we change before using this workflow routinely in MP3?

Threats to validity: small sample, model nondeterminism, feature difficulty, run order, human learning, shared caches, unequal permissions/context, changing requirements, unavailable usage data, and evaluator bias. Record which actually affected the exercise.

### Proposed slide outline

1. Question, HotShop feature, and comparison boundaries.
2. Existing SDD skills and the added multi-agent responsibilities.
3. Shared specification, controlled setup, and evaluation metrics.
4. Actual results plus one concrete finding/correction with code evidence.
5. Trade-offs, limitations, and implications for MP3.

Do not populate a results slide with estimated or fabricated measurements. If implementation remains incomplete, present it as an incomplete experiment and explain the observed blockers.

## 10. Next steps and change history

### Next steps

1. Confirm presentation date/lecture eligibility and available preparation time.
2. Select a bounded feature against the current MP3 design and baseline.
3. Finalise product/technical specs and eligible tasks using the existing workflow where appropriate.
4. Freeze the actual agent briefs, budgets, and independent evaluation cases.
5. Run the comparison, append evidence here, and prepare slides from the results.

### Change history

| Date | Change | Evidence |
| --- | --- | --- |
| 2026-10-15 | Created living record consolidating prior commit inspection, proposed role briefs, benchmark design, metrics, and pending results. No experiment executed. | [Creation log](../logs/2026-10-15-mini-presentation-working-record.md) |
| 2026-10-15 | Drafted coordinator, implementer, specification/security reviewer, and equivalent single-agent briefs. Added common inputs, output evidence, task-gate handling, and readiness checks. | [Specifications](miniPresentation/AgentSpecifications.md), [task log](../logs/2026-10-15-presentation-agent-specifications.md) |
| 2026-10-15 | Agreed and extracted coordinator role through discussion: bounded task selection, automatic progress reporting, delegation only, partial blockers, in-scope fixes, two correction cycles, and user resolution of all reviewer disagreements. Other roles remain pending discussion. | [Coordinator](miniPresentation/agents/coordinator.md), [task log](../logs/2026-10-15-coordinator-role-specification.md) |
| 2026-10-15 | Agreed separate test ownership, TDD handoff, implementer reinvocation of the testing subagent, minor implementation judgment, and one shared two-cycle correction budget. Testing/reviewer specs and single-agent baseline reconciliation remain pending. | [Implementer](miniPresentation/agents/implementer.md), [task log](../logs/2026-10-15-implementer-role-specification.md) |
| 2026-10-15 | Wrote testing role. Replaced automatic in-scope fixes with coordinator-drafted, user-approved change plans for every failed loop requiring test/code changes. Preserved shared two-cycle limit and test ownership. | [Testing](miniPresentation/agents/testing.md), [task log](../logs/2026-10-15-testing-role-approval-gates.md) |
| 2026-10-15 | Wrote combined specification/coverage reviewer role with permitted verification commands and two checkpoints. Updated testing, implementation, and coordinator handoffs; corrections remain approval-gated and share one two-cycle limit. | [Reviewer](miniPresentation/agents/specification-reviewer.md), [task log](../logs/2026-10-15-specification-reviewer-role.md) |
| 2026-10-15 | Wrote security reviewer: final-stage review only, local synthetic-data checks permitted, no code/test edits, corrections approval-gated within the existing shared budget. | [Security reviewer](miniPresentation/agents/security-reviewer.md), [task log](../logs/2026-10-15-security-reviewer-role.md) |
| 2026-10-15 | Agreed single-agent sequence and matching correction approvals; wrote separate baseline role. Recorded requested separation from actual MP3 and proposed isolated clones/data for both runs. Paths and setup remain pending; no benchmark started. | [Single agent](miniPresentation/agents/single-agent.md), [task log](../logs/2026-10-15-single-agent-role-isolation.md) |
