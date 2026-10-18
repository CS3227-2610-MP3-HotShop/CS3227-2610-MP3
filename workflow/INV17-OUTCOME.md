# INV-17 outcome summary

Platform and data foundations. One of the four authoritative feature records required by
the compact records policy in `workflow/MIGRATION-WORKFLOW.md`. Read this first: it carries
current status, approvals, the next action, blockers and the shared correction-cycle count.

Last updated 2026-10-18 (SGT), after INV17-B6, B7 and B8.

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
