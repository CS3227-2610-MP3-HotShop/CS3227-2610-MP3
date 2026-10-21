# Current project handoff

## UI-CI-FIX-1 — 2026-10-21

Jonat explicitly approved one grouped correction pass for six PR11 CI failures.
Baseline fb61099; CI39564388245 has869 tests/six failures, same set before Docker.
Three known blank-query preview fixtures; reproduce feedback and navigation lookup
failures; fix JavaFX shared-toolkit initialization. Preserve assertions, specs and
original measured snapshots/results. Single Luna implementer owns fix/tests and
required checks; final spec/security reviews only. Shared correction1/1 in progress.
See workflow/PLAN.md task for scope/checks. No hosted data/live SoC/deployment.

PR10 postgres-migration -> master; PR11 codex/ai-search-consolidated ->
postgres-migration draft. Docker packaging fb61099 built and final-reviewed locally.
Prior163 focused tests passed; full Linux CI exposed additional UI setup failures.
Supabase Storage and JPro browser-session/directory-lock readiness remain separate.
