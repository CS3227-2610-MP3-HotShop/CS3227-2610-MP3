# Current project handoff

## UI-CI-FIX-1 — 2026-10-21

Jonat approved grouped six-failure correction. Source candidate5f0c3ea on PR11:
valid-query/provider-call preconditions, bounded async feedback/navigation checks,
shared JavaFX toolkit startup. Test-only; assertions preserved/strengthened. Local
baseline reproduced three preview failures and repeated toolkit init; feedback/nav
were LinuxCI failures, not locally reproduced. CombinedUI84 + finalAI21/styles pass.
FULL LinuxCI39568040457 test/styles/check/build/shadowJar PASS8m33s. Final independent
Luna spec/security source reviews PASS using supplied test evidence. Correction1/1
consumed. Supplementary Windows full run still progressing at review; report result
separately. Documentation-only acceptance consolidation follows source candidate;
no Java tests manually rerun for records. Original benchmarks/CSV unchanged.

PR10 postgres-migration -> master has passing CI. PR11 codex/ai-search-consolidated
-> postgres-migration remains draft; no merge/deployment. Supabase Storage/JPro
multi-user and image-directory lifecycle readiness remain separate.
