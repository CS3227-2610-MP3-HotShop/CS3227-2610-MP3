# Current project handoff

## SoC connection benchmark — initial comparison complete, 2026-10-20

Root branch: multiagent-soc-search. Common checkpoint: f5ec63b.
Multi candidate: a47df937694c854b25641b8df383c918a907c4a7.
Single candidate: 560ffcb73516e51e030d204f8104a651028ba4e1 in
E:/CS3227-2610-MP3/tools/benchmark/singleagent-soc-search.

Both pass 149 focused tests, 100 security-tagged cases and both styles.
Frozen client-test blob: b67b9e8f24eef1067b8caa22bf039c2ff6f7b830.
Observed workflow times: multi 398 seconds, single 271 seconds.
Common source assessment fails both: 4 multi / 1 single attributable findings.
One shared request-encoding test/spec conflict is unscorable, excluded from those
counts; setup/readiness reviews missed it. No tests or candidates changed during
common evaluation. No corrections approved or performed: 0/1 each.
Details, evidence and limits: workflow/PLAN.md and workflow/benchmark-results.csv.

Owner-selected readings: multi session 36% to 28%, weekly 43% to 42%; single
session 28% to 25%, weekly 42% to 41%. Observed decreases: 8 versus 3 session
percentage points, 1 weekly point each. Original multi ending 30%/42% retained;
owner explicitly revised to 28%/42%, including extra wrap-up beyond timed workflow.
No absolute credit/token conversion; dashboard direction, rounding and lag were
not independently verified. Common preparation/evaluation costs are separate.

Common evaluation finished outside measured windows. Standalone shadowJar passes
on both; JARs include client, Gson and PostgreSQL, zero SQLite classes. Broader
check/build commands were interrupted after about 14 minutes in isolated PostgreSQL
tests; no full regression/build pass claimed. Owned test workers stopped. Docker
CLI was not found, but Testcontainers PostgreSQL was reachable. No hosted/demo DB,
live SoC call, deployment or seller merge occurred. Full buyer AI feature incomplete.

Next work requires explicit direction: reconcile oracle conflict and propose one
bounded correction plan while preserving initial candidates/results. Complete full
regression and real SoC compatibility before actual feature acceptance. No automatic
repairs or reruns. Benchmark results ready for the case study with stated limits.

## Scope and working rules

Owner authorised independent transport/parser/buyer adapter despite overlap with
friend's llm-feature; reconcile later. Friend snapshot 7d028e4 informed API
conventions and supplied synthetic HTTPS fixtures; never merge its SQLite/build
setup wholesale. Provider-wide quotas, live model quality, UI/session integration
and seller reconciliation remain separate gates. No keys read or logged.

Fresh Luna workers use targeted briefs without chat history. One implementer
finishes the batch; independent reviews at final candidate only. Workers write no
Markdown task logs. Coordinator owns shared docs/PLAN and this current handoff.
Manual credit checkpoints before/after measured workflows. Preserve failed initial
results and correction counters; no silent reruns or weakening valid assertions.

## Previous work

Validator benchmark results remain in PLAN/CSV; original branches preserved.
PostgreSQL migration aac5548 is published on postgres-migration with prior 746-test
and build evidence. Hosted Supabase provisioning/trust remains unverified; owner
reports PG17.11. Public CA outside repo: C:/Users/jonat/.postgresql/prod-supabase.cer.
Secrets/env files remain local and ignored. Historical PG correction 3/3 exhausted.
