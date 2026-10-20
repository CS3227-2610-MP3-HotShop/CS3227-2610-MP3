# Current project handoff

## Shared UI benchmark setup frozen; fresh readings required

UI-SETUP and UI-FREEZE are accepted in PLAN. The owner-approved setup correction1/1
is complete; both final focused reviews passed. Common source/test checkpoint is
3bb5d1960a2997f3c7bc3a8b00d43be0ad8db9c8. A following shared metadata-only revision
records this freeze and is advanced identically to both arms before dispatch.
Use their verified current HEAD as the final common revision in the run brief.

Branches/workspaces:
- codex/multiagent-ai-search-ui: tools/benchmark/multiagent-ai-search-ui
- codex/singleagent-ai-search-ui: tools/benchmark/singleagent-ai-search-ui
Both are clean, identical, with frozen test blob be4e072d0de179fe17988de6162e5559b3e5ff45.
No measured implementation or timer has started. Old37% five-hour/27% weekly readings
are pre-setup only. Ask fresh current readings and explicit ready before multi;
then stop after its final reviews/candidate for ending readings. Single needs its
own separate fresh ready/start reading. Never share arm results or code.

## Frozen packet and ownership

AI-P3/AI-T3/BENCH-P3 define exact AI Search button beside one existing query field,
normal search zero provider calls, preview/Apply/Dismiss, pending/failure feedback
and stale edit/page/session guards. UI21 baseline:3pass/18 expected missing-button
assertion failures,0 errors/skips,31.039s. Corrected test SHA256:
FA8791E97D6EC34F5342B785AAA23B813801936A7C1FDD0808343DD2956AC05D.
Seam SHA256:2ED59E588402F72E804823687E23369ACAE55D4168FF6652E5554B59AABE61E7.
Unchanged client159/legacyUI2/mainstyle/packaging evidence reused; correction ran
only changed UI tests/test style/diff. No full DB suite or provider calls repeated.

Both arms: fresh no-history Luna implementer, same role, own SearchPage/small UI
helpers, necessary SearchState, Main wiring, arm-local tests and guides. Frozen
common tests/client/provider seam/specs/PLAN are read-only. Multi receives final
independent Luna spec/security reviews after stable code; single self-checks only.
Common evaluator assesses both saved snapshots afterward. All have the same source
assessment for Main env/client ownership AND visible safe missing-config feedback;
disabled-state UI test alone does not satisfy that requirement. No requirement waived.

Measured checks: wrapper UI benchmark test + main/test styles, then check/build/
shadowJar -x test; no broad unchanged DB suite in timing. Record focused/full limits
honestly. No post-submission fixes without grouped owner approval; preserve initial
candidates and coarse manual credit readings, no token/dollar estimates. No worker
reporting Markdown; coordinator maintains existing shared records.

## Preservation

Original validator/connection benchmark branches/commits/results CSV unchanged.
Accepted Chat Completions client and lifecycle fixes included in common checkpoint;
historical readiness2/2 consumed. No new client repair authorised. Local .env.local
ignored/key preserved, no secrets in commits/briefs; no live calls/hosted DB tests.
Presentation drafts remain untracked and excluded; Canva DAHYgpyzhe4 unchanged.
No deployment, seller merge or JPro session redesign authorised.
