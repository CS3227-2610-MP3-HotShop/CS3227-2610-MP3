# Agent Interaction Log

## User Prompt

Continue INV-17 after approving inventory coverage: "lets do this one by one lets start
with 17, but before that push what we have alrady, and instead of specificying each
feature first lets finish inv-17, can you start the dev work on that after the spec",
followed by "push what we have on a seperate branch, open a pr for that, then start a new
feature branch and start the multi agent sdd dev work there".

Decisions given during the interview: seeded accounts and listings only; no resets in the
web application because reset was a local-persistence concern of the Java app; hosted
PostgreSQL from the start; sessions last until explicit logout; commit to polling for
cross-user updates; one fixed application timezone with a label; demo credentials
documented rather than shown on the login page.

## Steps Taken

1. Read `.agents/skills/manage-product-spec/SKILL.md` before any specification writing,
   and `manage-technical-spec/SKILL.md` when updating superseded constraints.
2. Ran the product interview one decision at a time, as the skill and the user's stated
   preference both require.
3. Explained the reasoning behind the in-flight seed proposal when questioned, and the
   polling versus push trade-off when the user asked for help reasoning it out.
4. Verified Vercel's websocket position against Vercel's own support documentation
   rather than asserting it from memory.
5. Applied the storage supersession across every file that carried the old constraint as
   live instruction.
6. Wrote `workflow/PRODUCT-SPEC.md` with INV-17 detailed and INV-01 to INV-16 marked
   inventory-only.

## Reasoning Summary

The no-reset decision could not stand on its own. On a serverless host, keeping the
earlier deferral of durable storage would have meant server state that disappears between
deployments and cold starts, which users would experience as an uncontrolled reset at
unpredictable times. Pairing the two and recording why keeps a later reader from treating
them as unrelated choices.

The superseded constraint was live instruction in two specification skills, not only in
records. Leaving it would have told a future agent to specify an interim in-memory store,
so the skills were updated alongside the documents.

For cross-user updates, the deciding factor offered was risk asymmetry rather than
effort: polling that lags is an annoyance, while a mis-scoped realtime channel leaks
participant-only data, which is the failure this marketplace's rules exist to prevent.
The user chose polling at product level rather than deferring the mechanism.

Seed content excludes offers, sales and meetups so that every deal state a viewer sees
was produced by the application's own flows, which also removes the risk of a wrong
fixture being mistaken for an application bug.

## Changes Made

- `workflow/PRODUCT-SPEC.md`: created. INV-17 detailed with FR-1701 to FR-1720 and
  AC-1701 to AC-1719, a feature status table, scope, exclusions, constraints and six open
  questions.
- `AGENTS.md`, `workflow/FEATURE-INVENTORY.md`, `docs/DeveloperGuide.md`,
  `docs/CLAUDE-HANDOFF.md`, `.agents/skills/manage-product-spec/SKILL.md`,
  `.agents/skills/manage-technical-spec/SKILL.md`: the resettable-demo and
  deferred-PostgreSQL constraint replaced with the superseding decision.
- `docs/InteractionRecord.md`: entries for demo data, publication and branching, storage,
  sessions and update visibility, concurrency refusals and timezone.
- `logs/2026-10-17-inv17-product-spec-draft.md`: this log.

No application code or tests were written. No branch, commit, push, pull request or
remote issue change was made.

## Verification

- `manage-product-spec` SKILL.md read before writing the specification, as the repository
  instructions require.
- Vercel websocket support checked against Vercel's support documentation and its
  community discussion of Fluid compute, rather than asserted from training knowledge.
- One `docs/InteractionRecord.md` write reported success but did not land on disk. Caught
  by comparing the file size afterwards, rebuilt from the current on-disk version with the
  missing entries re-applied, and confirmed by size. Later writes verified the same way.
- No Git command was executed. The session has no shell on the workspace machine, because
  the isolated Linux environment fails to start, and the cloud container's GitHub token is
  invalid. Branching, committing, pushing and pull request creation were handed to the
  user as exact commands.
- No application build, test or runtime check was performed, and none applies to a prose
  specification.

## Final Output and Conclusion

INV-17 has draft detail awaiting user approval. The remaining open questions are seed
content detail, image storage, whether signed-out browsing is permitted, the configured
timezone value, polling intervals, and human owner assignment.

Two gates stand between approval and implementation: owners are unassigned, and the
coordinator role as written does not implement production code or tests, which needs
settling for a single-assistant session. The publication steps the user asked for could
not be performed here and are outstanding on the user's side.
