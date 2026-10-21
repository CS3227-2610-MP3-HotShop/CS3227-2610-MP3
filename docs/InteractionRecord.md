# Current project handoff

## Docker packaging and publication — 2026-10-21

Owner Jonat authorised .dockerignore and Java25 JDK-to-JRE packaging after Render
failed to find the host-generated ZIP. DOCKER-PACKAGE-1 builds Linux jproRelease
inside Docker, normalizes wrapper CRLF there, and copies ZIP into JRE25. Local
settings/certificates/build outputs excluded. Actual image build passed; root
no-network runtime-layout and diff checks passed. Final Luna specification/security
source reviews clear, relying on supplied build results; no app/DB/live calls.
Initial implementation, post-submission correction0/1. Existing guides/PLAN/AGENTS
updated; original benchmark snapshots unchanged. Preparing update to draft PR11.

PR10 postgres-migration -> master; PR11 codex/ai-search-consolidated ->
postgres-migration is draft. UI acceptance still blocked by three frozen tests
with blank-query helper; correction requires explicit approval. Prior163 focused
tests/styles/package checks passed before packaging change; Java tests not repeated
for packaging/docs. Hosted operator provisioning succeeded separately. Supabase
Storage, JPro browser-session/directory-lock readiness and deployment acceptance
remain separate. See existing PLAN and benchmark-results.csv for measured evidence.
