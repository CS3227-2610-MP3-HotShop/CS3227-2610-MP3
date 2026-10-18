# Delegation protocol

Adopted 2026-10-18. Four mechanisms that control the cost and reliability of delegated
work. They exist because INV-17 paid for each of them.

This document is deliberately **tool-agnostic**. It names roles, gates, evidence and
capability tiers, never a specific product, model name or launch command. Any coordinator
can apply it: an agent CLI, an IDE extension, another vendor's harness, or a human running
the roles by hand. Platform-specific wiring belongs in
[AgentConfiguration.md](../docs/agents/AgentConfiguration.md), not here.

It supplements the collaboration rules in
[MIGRATION-WORKFLOW.md](MIGRATION-WORKFLOW.md) and the role definitions in
[docs/agents/agents/](../docs/agents/agents/). Where it is silent, those govern. It does
not authorise feature work and does not change the two shared correction cycles.

---

## 1. The gate ledger

**Problem it solves.** INV-17's Slice B skipped its test-readiness review. The same gate on
Slice A had returned NOT READY with five blocking findings, including one proving by
execution that the suite could not catch the implementation it existed to catch. Nothing
made the absence visible, so the slice proceeded and the equivalent defects surfaced after
implementation instead, each then costing a full test-fix-review round rather than a test edit.

**The mechanism.** Every slice carries a task table in the Execution plan section of its
technical spec. Tasks are created **before** delegation, all rows at once, including the
rows for gates that have not happened yet.

| Column | Meaning |
| --- | --- |
| Task | Stable ID and title |
| Role | Testing, Implementation, Specification reviewer, Security reviewer |
| Allowed edits | Paths this role may change; everything else is read-only |
| Blocked by | Task IDs that must be Complete first |
| Evidence required | What must be recorded for this row to close |
| Completed | `No`, or a one-line evidence reference |

**Rules.**

1. A task whose `Blocked by` is not satisfied **cannot start**. Not "should not": the
   coordinator does not issue the brief.
2. Only the coordinator changes `Completed`, and only against evidence returned by the
   worker. A worker's claim that it finished is not evidence; its actual command output is.
3. Completed rows are immutable. Later work gets new rows.
4. **If a downstream row is Complete while a gate row above it still reads `No`, that is a
   protocol breach.** It is recorded in the plan and in the interaction record, not quietly
   backfilled. A skipped gate that nobody can see later is worse than one everybody can.

The ledger is the thing the coordinator updates as it goes, not a record written afterwards.
A plan reconstructed at the end cannot prevent anything.

**Update the row when the worker returns, before the next brief goes out.** Not at the end of
the slice, not when convenient. INV-17 let rows go stale three times in one sitting, and each
time a worker read the stale row and reasoned from it: one declined a migration because a
completed review still read `No`, one asked whether it was allowed to start, and one gate ran
while the row it was blocked by still read `In progress`. A ledger that lags is a ledger that
lies, and it lies to exactly the people who were told to trust it over their brief.

---

## 2. Control-first ordering

**Problem it solves.** INV-17's test-isolation guard was written, reviewed, corrected for
one failure direction, corrected again for the opposite direction, and signed off on
residual risk. The security review then found it **never executed** in the project's own
documented configuration, because the variable it compared lived in a file the test process
never read. Three rounds of careful work on a control that was not running.

The tester's own words afterwards are the rule:

> It was never called with the configuration an operator actually writes, and no test
> started where the operator starts.

**The mechanism.** A task is **control-type** when its purpose is to prevent something: a
guard, an isolation check, an authorization check, a validation boundary, a destructive-action
confirmation, a credential redactor.

For a control-type task:

1. **The first acceptance criterion is execution, not correctness.** An end-to-end test that
   begins from the documented operator setup, exactly as the project's own README, example
   files and error messages tell a person to configure it, and proves the control fires at
   all. Unit tests of its internal logic come second and do not substitute.
2. **The security review runs at the readiness gate, not at the end.** A correctness reviewer
   asks whether the logic is right. A security reviewer asks whether it runs, what happens
   when it is bypassed, and what the blast radius is. For a control, that second question is
   the cheaper one to answer first.
3. **State the configuration the control was tested from.** Not "tested", but "tested with
   the variable in the file the quickstart tells you to put it in".

A control that fails open is worse than no control, because the next person relies on it.

---

## 3. Briefing by reference

**Problem it solves.** INV-17's briefs ran to 1500 words each because no written plan
existed, so every brief reconstructed scope, allowed edits, acceptance criteria and
stopping conditions from the coordinator's head. Eleven delegations rebuilt the same
context from scratch.

**The mechanism.** A brief contains four things and nothing else:

1. **Task ID**, resolvable in the slice's gate ledger.
2. **Sections to read**, as document plus heading. Never whole documents when a section
   will do, and never a paraphrase in place of the source.
3. **This turn's new information**: the actual failing output, the specific finding, the
   mutation that proves a claim. Only what is not yet written down anywhere.
4. **Stopping conditions**: what ends the task, and what must be escalated rather than fixed.

**The rule that makes it work:** if context has to be inlined to make the brief
intelligible, that context belongs in a record first. Write it down, then point at it. A
brief is not a place to store knowledge, because nothing reads a brief twice.

Workers read their role file, their task row, and the named sections. They consult history
when a decision is unclear, conflicting or being reconsidered. Summaries never replace
source requirements, and a reviewer always inspects the artefact itself rather than a
description of it.

---

## 4. Verification budget

**What mutation testing is.** Deliberately break the production code in one small, specific
way, run the suite, and see whether anything fails. If nothing fails, no test was actually
checking that behaviour. The broken version is a *mutant*; one that passes the suite
*survived*, and a survivor is a coverage gap stated as a fact rather than a worry.

It is the only reliable way this project has found to tell a real guarantee from a comment.
Two results from INV-17:

- `prepare: false` flipped to `true` killed three tests, including one reading
  `pg_prepared_statements`, so that setting is genuinely pinned and the pin survives an
  option rename.
- Deleting a guard clause that a comment called "ordered deliberately" killed nothing. The
  line was decorative, and four consecutive review rounds had found a comment asserting more
  than the code beneath it guaranteed.

**Problem it solves.** It is also unbounded. A sweep of twenty mutations against a 200-line
operator script found fifteen survivors, and chasing those to zero has no natural stopping
point and no proportionate payoff.

**The mechanism.** Three tiers, assigned per task in the gate ledger.

| Tier | Applies to | Budget |
| --- | --- | --- |
| **A, sweep** | **Only** a control preventing one account's data from reaching another | Every claimed guarantee gets a mutation. A fix with no discriminating mutation is not closed, whatever the green count says |
| **B, spot check** | A blocking finding's fix, wherever it is | One mutation proving the fix is discriminated. Nothing more |
| **C, none** | Everything else, which is most things | None. Record survivors noticed in passing as follow-ups |

**Tier A was narrowed on 2026-10-18.** It previously covered all control-type tasks,
concurrency guarantees, trust boundaries and any closed review claim, which in practice meant
almost everything, and each surviving mutant became a permanent test. That is a ratchet with no
stopping point: INV-17 reached a 5:1 test-to-code ratio partly this way, including a 20-mutation
sweep of a 200-line operator script that found fifteen survivors nobody should chase.

**Rules.**

1. **Report survivors, do not chase them.** A survivor on a Tier B or C item is a recorded
   follow-up, not work, unless it is promoted deliberately.
2. **Every blocking finding closes with a mutation.** This is the one place the budget does
   not bend: a reviewer who says a defect is fixed must show the revert that makes the suite
   fail. INV-17 had two "fixes" that no test discriminated, one of them load-bearing.
3. **A mutation that cannot fail is not evidence.** A worker that finds its probe passes
   under the mutated code must say so and remove the test rather than bank a green that
   proves nothing.

---

## 5. Capability tiers

**Problem it solves.** Every delegation in INV-17 ran at maximum capability, including
routine test authoring against a settled design. Reviewers earn it; a seed command does not.

**The mechanism.** Tasks are assigned a tier in the gate ledger. Tiers are described by what
the work demands, so each platform maps them to whatever it actually offers.

| Tier | Work it covers | Why |
| --- | --- | --- |
| **Deep** | Specification review, security review, any task touching authentication, authorization, concurrency or a trust boundary, and any correction cycle | Every blocking finding in INV-17 came from adversarial execution: constructing a hostile input, mutating the implementation, building a decoy database. That is the capability being paid for |
| **Standard** | Test authoring and implementation against an approved design with settled acceptance criteria: migrations, seed commands, straightforward route handlers, mechanical refactors | The design decisions are already made and written down. The work is careful execution against them |

**Mapping.** The tiers above are authoritative; the table below is a convenience. A harness
that cannot offer a listed model substitutes by capability, and a harness not listed adds a
row rather than reinterpreting the tiers.

| Tier | Claude harnesses, this project's default | Another harness |
| --- | --- | --- |
| **Deep** | Claude Opus | The strongest reasoning tier the account offers |
| **Standard** | Claude Sonnet | A mid tier that still writes and runs tests competently |

Two notes on substituting:

- **Do not map Standard to the cheapest available tier.** Standard work still authors tests
  and runs a suite. The saving comes from not paying deep rates for settled work, not from
  going as low as possible.
- **Pin exact model identifiers per harness in
  [AgentConfiguration.md](../docs/agents/AgentConfiguration.md), not here**, together with
  the date they were set. Version strings change faster than this document should, and a
  stale identifier in a protocol file is read as authoritative long after it stops being
  true. The Codex row is filled in on first use there rather than guessed in advance.

**Rules.**

1. **A tier is a floor, not a ceiling.** Standard work may run deep; deep work may never run
   standard.
2. **Reviewers are always deep**, including a re-review that only verifies fixes. The
   verification pass is where a weak reviewer rubber-stamps.
3. **Escalate on contact.** A standard-tier worker that hits a trust boundary, a concurrency
   question or an unanticipated design decision stops and returns it rather than deciding.
4. **The tier is recorded with the evidence**, so a later reader knows what produced a result.

---


## 6. The coverage ceiling

**Problem it solves.** Nothing bounded how many tests were enough. With no number, the
reviewer's judgement was the only limit, and judgement under an adversarial mandate is unbounded
by construction. INV-17 produced **9,293 lines of tests for 1,772 lines of production code**, a
5:1 ratio, reaching **89% branch coverage**. A threshold would have stopped it thousands of
lines earlier, and the marginal test code above 80% was byte-order marks, percent escapes and
hostname spellings, none of them on any user's path.

**The mechanism.** Branch coverage of the production source, **80%**, as a floor *and a
ceiling*.

- **Floor:** below it, the slice is not done.
- **Ceiling:** above it, nobody may ask for more tests. Reviewers included. This half is the one
  that actually changes behaviour, and it is unusual enough to state outright.

Operator scripts, diagnostics and test helpers are excluded from the measurement. Chasing those
to a branch target is how the ratio happened.

**Coverage has two blind spots that matter here, so one rule goes beside it.** Coverage measures
execution, not assertion: a test that calls a function and asserts nothing counts as covered.
And it cannot see a control that is never called, which was INV-17's most serious defect, an
isolation guard with excellent coverage of logic that nothing ever invoked.

So: **every acceptance criterion in the ledger row has at least one test that fails if the
behaviour is removed.** Per criterion, not per branch. A handful of checks per slice, and the
criteria are already written, so it costs nothing to state.

**What a reviewer may block on, consequently, is exactly two things:** an unmet acceptance
criterion, or coverage below the threshold. Everything else is a note, carried to the outcome
summary, capped at three per review. A reviewer with more than three is exploring rather than
reviewing.

**One exception, and only one.** Where a control prevents one account's data reaching another,
attack it properly, by execution. That is the single failure mode worth unbounded effort in a
marketplace, and it is not a licence to treat the rest of the slice the same way.

**The honest cost.** INV-17's two most valuable findings, a guard that never executed and TLS
left off, both came from the open-ended adversarial mode this section closes. Under these rules
a reviewer would probably still catch the first, because "does this control execute" is an
acceptance criterion under control-first ordering. It would likely miss the second, because no
requirement mentioned TLS. That trade is deliberate.

## Applying this to a slice

1. Write the gate ledger rows for the whole slice before delegating anything, marking
   control-type tasks and assigning verification tier and capability tier per row.
2. Brief by reference. If a brief needs inlined context, write that context into a record
   first.
2a. Keep the slice's tests near 1.5x its production lines. Materially over means the design is
   wrong, not the coverage.
3. Run gates in ledger order. A gate row at `No` blocks everything below it.
4. Close each row against evidence. Blocking findings close with a mutation.
5. Carry survivors and non-blocking findings into the outcome summary rather than spending
   a correction cycle on them.

## What this does not change

The tester and implementer separation, the readiness and final reviews, the final security
review, explicit correction-plan approval before corrective edits, and the maximum of two
shared correction cycles per task or batch across roles, stages and restarts. New task IDs
and restarts do not reset that count. Workflow changes do not authorise unfinished feature
code.
