# INV-17 outcome summary

Platform and data foundations. One of the four authoritative feature records required by
the compact records policy in `workflow/MIGRATION-WORKFLOW.md`. Read this first: it carries
current status, approvals, the next action, blockers and the shared correction-cycle count.

Last updated 2026-10-19 (SGT), after verified partial-baseline publication.

## Current takeover status — 2026-10-19

This section supersedes the historical status below. Source handoff:
docs/HANDOFF-INV17.md. Working branch codex/nextjs-migration has code baseline
f36a520, committed and pushed externally during the coordinator interruption;
remote SHA verified on resume. It includes C1/C2-P and environment repairs.
The remaining publication is workflow/contracts/documentation and historical logs.
Preserve all existing local files; exclude Claude outputs/.

### Locally verified baseline

- ENV-1: repaired only coverage lock entries; no existing versions or package.json
  changed. Clean install with lifecycle scripts disabled, typecheck and lint passed.
- ENV-2: fixed Windows compiler-fixture path normalisation. 305 unit tests passed,
  no skips. Reverting the change reproduces failure. Independent reviewer confirmed
  unchanged assertions/fixtures and independently passed the focused 12 tests.
- Local PostgreSQL 17.11 is running at 127.0.0.1:55432, data at
  E:/HotShopTestPostgres17/data. Dedicated hotshop_test base and ignored
  web/.env.test.local configured. No production DB contacted or shared override used.
- Initial integration baseline: 164 passed/13 failed. All failures were in db-check
  subprocess tests because execFile('npm') fails ENOENT on Windows.
- ENV-3 portable npm launch and valid success-password fixture are repaired.
  Focused32/32 and integration177/177 passed; independent reviewer also passed32/32.
  Both revert probes reproduced the corresponding failures. No authentication weakening.
- C2-PI: focused8/8, combined unit/integration coverage490/490 passed (19 files); source branches88.38%, session module100%. Typecheck/lint passed. No local build run. Historic cloud counts remain separate evidence.

### Next action and boundaries

Environment recovery gates passed. User approved C2-P; stubs, eight meaningful red tests, independent specification/security readiness and implementation are complete. C2-PR2 passed independent specification and security review. In scratch copies, removing lookup restriction failed P1/P4 and removing deletion restriction failed P4; live source/test hashes remained unchanged. The bounded C2-P slice is complete, with 0/2 correction cycles used. C2-T for full routes remains unstarted. Credential
verifier interface shape is now owner-approved in TECHNICAL-SPEC.md, "C2 shared
contract — credential verifier"; exact types/error encoding remain pending.
Account storage is owned by INV-01; no users FK is added in C1. Next: agree the
remaining HTTP response/error contract before full C2 route work. Envelope shape
approved 2026-10-19: success `{ ok: true, data: ... }`, failure
`{ ok: false, error: ... }`; preserve refusal brand/code/message and add reason
and optional fieldErrors. The seven proposed failure reasons/statuses are approved
in the C2 HTTP envelope section, including 200 for confirmed signed-out session
checks versus 503/cookie preservation for unavailable lookup. Success payloads and
statuses are also approved in that section: registration 201 with username;
login/check 200 with explicit authentication state and minimal user; logout 200
with signed-out state only after termination is confirmed. Field-error format is
approved: affected fields map to safe-message arrays, general failures omit it,
and submitted values are never echoed. Credential rejection remains general.
Transport-only INTERNAL category is approved for INTERNAL_ERROR/500 with a fixed
safe message; the six domain refusal categories remain unchanged. The six remaining
reason/category assignments are approved in the technical spec, including STORAGE
for unavailable session lookup. General error messages are now approved in the
technical spec, preserving INV-01's credential/already-signed-in/lookup wording
and logout-specific feedback/page/input preservation. Owner directed return to
INV-17: cache policy for POST/GET/DELETE /api/session is approved: no-store on all
responses including errors, and current database session checks without shared
application caching. Cross-site request protection is also approved: exact configured
Origin match for POST/DELETE, rejecting absent/null/mismatch before credential or
session changes with PERMISSION/ORIGIN_NOT_ALLOWED/403. Remaining session-route/
cookie/security contracts still gate C2 readiness.
The previous broader cache proposal remains unapproved. Registration, account
forms and registration field-message detail stay with INV-01; prior shared design
approvals are preserved but do not authorise INV-01 implementation.
Persistent-cookie policy
is approved: 400-day lifetime renewed during authenticated use; server sessions
have no age/idle expiry. Browser retention limits are explicit in FR-1701 and the
technical spec. Exact renewal placement and remaining cookie setup details still
need design before readiness. No implementation gates have been advanced.
No browser login or deployment claim; no local build run. Owner now authorises a
commit and normal push of the partial baseline, superseding the earlier prohibition.
PUB-1 verification/package review passed: publication_tests reports coverage490/490,
branches88.38%, typecheck exit0 and lint exit0 (one unused eslint-disable warning
in ignored generated coverage/block-navigation.js, zero errors). Only the dedicated
local test database was used. publication_review found no material workflow-package
omissions. No code/test edits, build/browser verification or feature Done claim.
Documentation/workflow records published as cc257f6; remote SHA verified. PUB-1
complete. Only excluded Claude outputs/ remained untracked. Next authorised work:
remaining session-contract discussion, not full C2 implementation. INV-02 may
start specification work from the published baseline.
Core workflow/skills/roles/templates are tracked. INV-02 has inventory evidence,
not approved detailed design/implementation tasks here; teammate can start discovery
and specification, with implementation gated on its own approvals/dependencies.

Coordinator takeover confirmed workspace E:/CS3227-2610-MP3, branch
codex/nextjs-migration at 1761bc1 and existing dirty tree. Prior test/review results
above were read as recorded evidence, not rerun. INV-01 decisions were inspected
from committed branch 210fa9e without touching its worktree; integrating them remains
pending. Publication changes documentation only and does not authorise implementation.

Keep counters A 2/2, B 2/2 and B9 1/2 from the newer handoff; C2-P 0/2; full C2 routes unstarted. ENV-1,
ENV-2 and ENV-3 are owner-approved scoped environment repairs, not resets or
permission to reopen exhausted production batches. C3 content and C5 remain blocked
on their feature dependencies. INV-01's nine-conflict merge remains untouched.

## The other three records

| Record | Location |
| --- | --- |
| Product spec | `workflow/PRODUCT-SPEC.md`, FR-1701 to FR-1720, AC-1701 to AC-1719 |
| Technical spec, including the execution plan | `workflow/TECHNICAL-SPEC.md`, TD-01 to TD-10, Execution plan section |
| Selective interaction record | `docs/InteractionRecord.md` |

Historical per-worker logs under `logs/` are preserved but no longer produced; the policy
superseded them on 2026-10-18.

## Status

**In progress.** Slice A complete. Slice B complete and verified: specification review
passed (INV17-B6), security review passed after one correction cycle (INV17-B7, INV17-B8).
The security reviewer's verdict is **safe to commit**. Slice C not started.

350 of 350 tests passing, typecheck, lint and build clean, all in the assistant's cloud
container against PostgreSQL 16.15. Nothing has run on the owner's machine.

Nothing is committed. The branch `codex/nextjs-migration` is at `7c16bcf` and has never
been pushed; no `origin/` ref exists for it. All INV-17 work sits uncommitted in the
working tree at `E:/CS3227-2610-MP3/web`.

## Approvals on record

| What | When | By |
| --- | --- | --- |
| Inventory coverage, 17 groups | 2026-10-17 | Owner |
| Migration source frozen at `1d812ef` | 2026-10-17 | Owner |
| INV-17 product specification | 2026-10-17 | Owner |
| Stack: no ORM, plain SQL migrations, `postgres.js` over a direct connection, `supabase-js` for storage only | 2026-10-17 | Owner, reversing an earlier ORM decision |
| Hosted PostgreSQL from the start, no application reset | 2026-10-17 | Owner |
| Sessions persist until explicit logout | 2026-10-17 | Owner |
| Polling for cross-user update visibility | 2026-10-17 | Owner |
| One fixed application timezone, labelled | 2026-10-17 | Owner |
| Slice A correction cycles 1 and 2 | 2026-10-17 | Owner, before edits |
| Correction budget counted per task/batch | 2026-10-18 | Owner |
| Slice B correction cycle 1 retained despite missing prior approval | 2026-10-18 | Owner, after the fact |

The technical specification as a whole remains **draft**. The session-status and error
contracts need review with INV-01 before integration.

## Shared correction cycles

Counted per task/batch by the owner's 2026-10-18 ruling. Never reset for a new assistant
or policy.

| Batch | Used | Remaining |
| --- | --- | --- |
| Slice A, logical foundations | 2 of 2 | 0, batch closed |
| Slice B, database foundation | 2 of 2 | 0, exhausted |
| Slice C | 0 | 2 each |

**Slice B's budget is exhausted.** Any further change to Slice B requires explicit owner
direction, not merely approval of a plan. The three outstanding items below are therefore
decisions, not work queued up.

## Next action

**INV17-C1**, the migration runner and the `sessions` table migration, once the owner has
ruled on the three outstanding items below.

Remaining Slice C, in dependency order: C1 migrations, C2 session lifecycle and routes,
C3 guarded seed command, C4 authenticated image route. **C5, the polling routes, is blocked
externally**: FR-1714 to FR-1717 need the message and conversation contracts from INV-13 and
INV-14, which do not exist.

Also pending: **INV17-B9**, the actor-carrying seam, adopted as an INV-17 design decision on
2026-10-18 and not yet built.

## Outstanding decisions, Slice B budget exhausted

1. **A UTF-8 BOM in `.env.local` blinds the test-isolation guard.** `util.parseEnv` does not
   strip it, so the key becomes `\ufeffDATABASE_URL` and the candidate silently disappears,
   while Next.js's own loader does strip it, so the application sees the variable and the
   guard does not. Windows PowerShell writes a BOM by default and the owner is on Windows.
   Proven end to end: with the BOM, 117 of 117 green; without it, refused. One line in
   `readEnvFile`. Held below blocking only because reaching the destructive path now also
   needs `HOTSHOP_TEST_ALLOW_SHARED_DATABASE=1`, so two controls must fail together.
2. **The TLS rule duplicated into `scripts/db-check.ts` is pinned by no test.** Mutating it to
   remove TLS from the script entirely leaves all 350 green, whereas the same mutation in
   `@/server/db` fails four tests. This is the one command pointed at real Supabase credentials,
   and its whole purpose is to be run when something is already wrong.
3. **An empty `?sslmode=` resolves to plaintext on a remote host**, because the module defers
   to the query string and postgres.js reads the empty value as falsy. Narrow, but it is the
   shape a half-finished hand edit produces.

Low priority: the password redactor in `db-check.ts` is unpinned in both directions, though
the reviewer established that postgres.js error objects never carry the password, so it is
defence-in-depth behind a leak that could not be produced. And
`HOTSHOP_TEST_ALLOW_SHARED_DATABASE` can live persistently in `.env.test.local` rather than
being a per-run opt-in.

## Blockers and risks

- **INV-01 is blocked on this feature.** Its readiness assessment resumes only after a
  reviewed, committed, minimal INV-17 web and test baseline is integrated into
  `E:/CS3227-2610-MP3-INV01`. Neither "reviewed" nor "committed" is true yet, so INV-17
  sits on INV-01's critical path.
- **Nothing has run on the owner's machine.** Every test result on record was produced in
  the assistant's cloud container against PostgreSQL 16.15. The owner has not run
  `npm run db:check`, so the credentials in `web/.env.local` are still unverified.
- **No test has run against Supabase 17 or behind the transaction pooler.** `prepare: false`
  is the setting that works perfectly locally and fails only behind the pooler; its pin is
  behavioural and strong, but it has never been exercised where it can actually fail. The same
  is true of TLS: nothing here proves the bytes on the wire are encrypted, which needs a
  staging connection and `select ssl from pg_stat_ssl`, and belongs in a deployment check.
- **Two cross-request hazards are carried to INV-07, not fixed here.** Session-level state set
  inside `withTransaction`, such as `set application_name` or a temp table, survives the commit
  and is visible to the next request on the same warm instance; and the `tx` handle can be
  returned out of `withTransaction` and keeps executing silently in autocommit on a pooled
  connection. `max: 1` makes the first one more reachable, since every request on an instance
  now shares one connection. Supabase's pooler very likely masks both in production, which is
  exactly why they are live in local development and on the session pooler.
- **A declined unique violation carries another user's data.** For a 23505 the driver error's
  `detail` reads `Key (email)=(victim@example.com) already exists`, naming the pre-existing row
  rather than what the caller submitted, and it is an enumerable own property. In a marketplace
  that is account enumeration. The obligation not to render it is documented in the module and
  nowhere else; INV-07 owes a mechanism rather than a comment.
- **A TLS misconfiguration degrades rather than fails.** postgres.js backs off exponentially on
  a failed handshake, so the symptom is progressively slower queries and function timeouts, not
  a clean error.
- **The Supabase project-ref pattern is a calibrated guess.** It guards the test-isolation
  check that stands between a careless configuration and the production database. It fails
  safe, refusing rather than permitting when it cannot read a ref, but it has never been
  checked against a real connection string, and custom-domain endpoints are untested.
- **FR-1709 is deliberately unverified.** Its machinery is built and tested against a
  fixture table; the requirement itself is verified when the real `offers` table lands in
  INV-07 with a concurrent test. If that migration ships without the partial index, every
  INV-17 test still passes and the invariant is still broken.
- **The device bridge is unreliable in this environment.** There is no shell on the owner's
  machine, so no Git command has been executed by any assistant session; branch state is
  read from `.git` refs. File writes have reported success without writing, repeatedly.
  Verify every write by re-reading it.

## Coordinator process gaps

Recorded in full in the technical spec's Execution plan. In short: Slice B's test-readiness
review was never run, its correction cycle ran without prior approval, and no execution plan
existed before any delegation on this feature. The first of those removed a gate that had a
demonstrated hit rate on Slice A, where it returned NOT READY with five blocking findings.
