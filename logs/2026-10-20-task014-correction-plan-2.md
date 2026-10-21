# TASK-014 correction plan 2

## Authority and failed snapshot

TASK-014 only. Cycle 1 was explicitly approved and its re-verification failed.
Shared correction state is 1/2; this fix set plus re-review is the second and last
ordinary correction cycle. No real private configuration or live provider was used.

Failed adapter SHA-256:
`D99DBBF056562CA61E48549071224B91E033F8F23BA766ED66B6A123C9253684`.
Failed test SHA-256:
`13240B2F15F955ED1022BB6A3E417F91F718F432E43438775B7F53DB6F48FA74`.

The focused run compiled and executed 19 cases: 13 passed and 6 failed in adapter
setup. All six stopped before their intended boundary assertions. They are
unverified behavior, not successful TDD red. The extra style attempt encountered
an environment cache-lock permission failure; it did not establish style results.

## Evidence and cause

`User.java` accepts `[A-Za-z0-9_]{3,30}`, while the test adapter registers/logs in
as `live-evaluation`. Its hyphen makes the synthetic account invalid; the adapter
correctly returns the fixed preflight failure before any dispatch. Test coverage
added in cycle 1 exposed this initial adapter setup defect.

After failed setup, cleanup can run again through try-with-resources/AfterAll.
The current factory retains references to already released/deleted resources;
a second walk of its removed temporary directory fails. Independent cleanup
must remain fixed-message and become idempotent. Before recursive Windows cleanup,
the resolved target must also be verified as the generated evaluation directory
directly under the configured Java temporary root.

Both independent reviewers agree that the earlier path-error sanitization and
dispatch-count preservation changes are present in source. Their new behavioral
tests remain unverified until the setup defect is corrected. No further critical
finding or reviewer disagreement was reported.

## Proposed changes and ownership

Testing specialist owns these changes only:

1. In `ProductionLiveLlmClientFactory.java`, use the same valid synthetic username
   `live_evaluation` for registration and login. Do not relax application validation.
2. Make factory close single-use/idempotent, releasing each owned resource at most
   once while still independently attempting runtime, transport, manifest and temp
   cleanup. Preserve fixed errors and avoid masking a primary failure.
3. Validate the normalized absolute cleanup target's parent against the Java
   temporary root, its generated `hotshop-live-synthetic-` name and non-symlink root
   before walking/deleting; a missing already-cleaned target is harmless. Reject
   unrelated paths without deletion or path-bearing errors.
4. In `LiveLlmAdapterTest.java`, retain all current assertions and add focused
   repeated-close/failure and unrelated-cleanup-target regression coverage.
5. Update owned `SECURITY-EVALUATION.md` wording: offline tests now exercise the
   real adapter with synthetic configuration/loopback TLS, while never reading
   the real private `.env`; remove the stale future-adapter heading.

No application source, provider configuration, corpus, prompt, quota or retry
policy change is included. Coordinator owns plan/workflow/log consolidation.
Reviewers remain read-only.

## Verification and limits

Run the focused adapter/corpus tests and both Checkstyle tasks using the approved
Wrapper escalation where cache permissions require it. Preserve XML totals and
check actual evidence behavior. Both reviewers reassess the stable snapshot.
Live evaluation remains paused until focused checks and reviews pass; the human
profile and authorized runner's restricted evidence access are already verified.

Any unexpected failure requiring further edits after this cycle exhausts the
shared limit: pause and ask for explicit direction. Approval of this plan does
not extend the two-cycle budget. No automatic fix/retest or live retry is allowed.

## Approval

The user explicitly approved on 2026-10-20: "I approve of the correction cycle 2
plan, continue implementing TASK-014". This fix set is authorized; shared state
is 2/2 while fixes and re-review run. The ordinary correction budget is not extended.
