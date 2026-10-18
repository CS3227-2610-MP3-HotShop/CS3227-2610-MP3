# MP3 issue tracker

Use GitHub repository CS3227-2610-MP3-HotShop/CS3227-2610-MP3. Always pass
--repo CS3227-2610-MP3-HotShop/CS3227-2610-MP3 to gh issue/pr commands;
for gh api use that repository in the endpoint. Never inherit MP2's tracker target.

## Operating sequence

1. Read the issue body, comments, linked specs and approvals before changing it.
2. Use the feature/task templates under .github/ISSUE_TEMPLATE. Keep requirements
   in versioned specs; issues link sections, acceptance IDs and plan task IDs.
3. Record parent/child relationships in both directions. Split frontend/backend
   tasks into separate issues where separately tracked; integration can have its
   own task. Use native sub-issues where available, plus readable parent links.
4. Record owner, stage, dependencies and open questions. Pending values are allowed
   during discovery; implementation requires approved scope, owner, contracts and
   prerequisites. Issue assignment or stage changes are not approval.
5. Post evidence and limitations before closure. Close parent features only after
   required children and integrated acceptance/documentation/reviews are satisfied.
6. Use exact UTF-8 files with --body-file for multiline edits/comments. Preserve
   concurrent changes; read latest issue before overwriting its body.

## Stages, flags and triage

Use one stage label: stage:backlog, stage:ready, stage:in-progress, stage:review,
or stage:done. They map to Backlog, Ready, In progress, Review and Done and obey
workflow/MIGRATION-WORKFLOW.md transition gates. Remove the old stage on transition.
Use flag:paused for a deliberate hold and flag:blocked for unresolved dependencies
or decisions; flags can coexist and retain the stage. Record reason, responsible
person if known, linked blocker and resume condition. Never silently reset correction
cycles or reopen completed plan entries. Labels do not grant execution authority.

The five triage labels are defined in triage-labels.md; they are separate from
execution stages. Existing type labels such as bug/documentation remain available.

## Branches and integration

Workflow setup was published on codex/migration-workflow in PR #9. The local
codex/nextjs-migration integration branch has been created; verify remote publication
before targeting PRs. Use separate checkouts and codex/<feature>-frontend or
codex/<feature>-backend branches where useful; never switch a teammate's active
checkout. One writer owns a shared file at a time. Integrate small slices against
approved contracts; mock completion alone does not close a feature.

## Tracking choice — agreed 2026-10-17

Use repository issues and labels; defer GitHub Projects. Stage labels are the
current execution-stage record. Use issue filters by assignee, stage and flags.
Keep owner, parent/dependencies, acceptance evidence and resume conditions on issues.
No Project board or additional Projects token scope is required for this workflow.
A board can be added later by explicit decision using the same existing issues.
The earlier missing-scope check is historical and no longer blocks setup.
Issue #6 remains open only until the latest tracker documentation is published;
parent #2 depends on that completion. Do not close either based on local files alone.

## Current setup scope

Issues #2–#7 concern workflow setup. Issue #8 and product/technical specifications
are being continued separately by the user with Claude. This setup pass must not
close #8 or edit those feature specifications. Refer to the latest decision record
for superseding scope decisions; hosted PostgreSQL from the start replaces the
older resettable/interim-storage proposal.
