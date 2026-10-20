# TASK-014 correction plan 1

## Authority and snapshot

User selected TASK-014 under implement-task. Shared correction budget is 0/2;
this proposed fix set and re-review would consume cycle 1. Human approval is
required before code/test edits. No live request has occurred.

Baseline: `4a5538021db6fd1d9b80ab39c529901c1ac2cc9f` plus the initial test adapter.
SHA-256 of the failed candidate:

- ProductionLiveLlmClientFactory.java:
  `246EA446DC56EF2C5C1DB91C593BD9BD6F0DFA1C643EEDF8A1AF0EA73D5D7A0D`
- LiveLlmAdapterTest.java:
  `6A62D44E8456FE3A09BEE4D945AF0A9B9174AEA8AC0F7CFFFAEE2D8EA5EAFE74`

## Evidence and cause

Initial registration red was reviewed before adapter implementation. Initial
green passed six focused offline tests, but checkstyleTest failed one 122-character
line at LiveLlmAdapterTest.java:37. Main style was up to date.

Initial specification/security reviews identified:

- F014-COVERAGE: no offline integration verification yet for real adapter dispatch,
  persisted evidence, incremental manifest, write failures and resource cleanup.
- S014-1: invalid evidence path and cleanup failures can escape fixed-message
  handling, or replace a sanitized preflight error. No token leak was observed.
- S014-2: a case-file write failure after dispatch leaves manifest dispatch counts
  stale because the manifest update occurs only after successful case persistence.
- Evidence directory permissions initially covered the sandbox execution principal;
  access for the actual human Windows profile must be verified before live use.

Requirements: TASK-014 steps 1–3 and acceptance; technical 3.1/5.2; evaluation rubric
requires real production generation, safe evidence, accurate counts, separate
human review and no credential/raw exception capture.

## Proposed edits and owners

Testing specialist owns all code/test changes:

1. Wrap the one overlong assertion without changing its behavior.
2. Add deterministic offline tests using temporary synthetic settings and the
   existing loopback HTTPS fixture. Cover the real authenticated service path,
   actual dispatch versus case counts, complete per-case fields/timestamps/text,
   required-corpus/human-review/provider-tool manifest fields, persistence before
   an automated semantic assertion, missing/invalid preflight settings, existing
   manifest or case-file collision, post-dispatch write failure and cleanup.
3. Add only narrow package-private test construction seams if necessary to inject
   controlled transport/setup/cleanup failures. ServiceLoader's no-argument live
   constructor must retain the real production client and unmodified service quota.
4. Sanitize path, setup and cleanup errors to fixed messages without raw causes;
   attempt independent resource cleanup without masking the primary safe failure.
   Preserve accurate actual-dispatch counts in a safe partial/final manifest even
   when case evidence cannot be written. Do not overwrite prior case evidence.
5. Keep sequential execution explicit and retain 13-second terminal-to-next-start
   spacing, the 26-case corpus, prompt, configuration rules, quotas and no retry.

Allowed files: LiveLlmAdapterTest.java, ProductionLiveLlmClientFactory.java,
LlmLiveSecurityTest.java, the test factory registration and evaluation instructions;
build.gradle only if the existing live-property pass-through needs corresponding
test configuration. No application source changes or external provider changes.

Coordinator owns approval/verification logs, plan progress and workflow guide;
also verifies restricted access for the human profile and authorized live runner.
Specification and security reviewers remain read-only and re-review the corrected
stable snapshot before live execution. No reviewer disagreement has been reported.

## Verification and paused work

Run focused adapter/corpus offline tests and both Checkstyle tasks, inspect actual
reports and the evidence schema, then obtain specification/security re-review.
No real settings are read by ordinary tests. No live call is allowed until checks,
reviews and human-profile evidence access are verified. Full application regression
checking remains outside the focused evaluation workflow.

All code/test edits and live execution are paused pending approval. Read-only
diagnosis, documentation and evidence-directory access verification may continue.
Further findings outside this fix set require revised approval. After this cycle,
one shared correction cycle remains; approval does not extend the two-cycle limit.

## Approval

The user explicitly replied: "Approve correction cycle 1" on 2026-10-20.
This fix set is authorized; shared cycle state is 1/2 while fixes/re-review run.
