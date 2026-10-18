# INV-17 handoff

Current takeover verification (2026-10-18): see workflow/INV17-OUTCOME.md,
"Current takeover status". Code baseline f36a520 now contains C1/C2-P and was
verified on the remote on 2026-10-19. Use the outcome for publication status.
Local environment recovery passed (ENV-1/2/3); the isolated PostgreSQL baseline is verified. Approved C2-P internal session implementation and final reviews are complete; full login/cookie contracts remain pending. The environment/Git assertions below are the
previous assistant's historical snapshot, not current verified state.


Written 2026-10-18 at the owner's request, to hand the remainder of INV-17 to another
assistant. Deliberately tool-agnostic: nothing here assumes which harness you are.

## Read these, in this order, and nothing else first

1. **`workflow/INV17-OUTCOME.md`** — status, approvals, blockers, correction-cycle count.
   This is the entry point. It is maintained from the start, not written at the end.
2. **`workflow/TECHNICAL-SPEC.md`** — TD-01 to TD-12, and the **Execution plan** section,
   which holds the gate ledger. Your next task is a row in it.
3. **`workflow/DELEGATION-PROTOCOL.md`** — how work is delegated and bounded here. It changed
   substantially on 2026-10-18 and the changes are the point; see "What changed" below.
4. **`workflow/PRODUCT-SPEC.md`** — FR-1701 to FR-1720, AC-1701 to AC-1719.
5. **`docs/agents/agents/*.md`** — the five role definitions. Each gained a Delegation protocol
   section and a coverage budget or blocking-rules section on 2026-10-18.

`docs/InteractionRecord.md` is 66KB. Read it by section when a decision is unclear, not whole.

Do not re-read `docs/CLAUDE-HANDOFF.md` for INV-17 status; it predates all of this.

## Where things actually stand

486 tests pass. `typecheck`, `lint` and `build` exit 0. Branch coverage of `src/` is 88.23%
against an enforced 80% threshold. All of it was run in a cloud container against
**PostgreSQL 16.15**. Nothing has ever run against Supabase 17 or the transaction pooler, and
nothing has been run on the owner's machine.

**Built and reviewed:** the refusal taxonomy, the injectable clock and fixed-zone formatting,
the connection and transaction helper with unique-violation and retryable classification,
verified TLS, serverless pool limits, the `db:check` operator command, the actor seam, the
migration runner and the `sessions` migration.

**1,772 lines of production code. 9,293 lines of tests.** That ratio is a known problem and
the 2026-10-18 workflow changes exist to stop it recurring. Do not treat the existing ratio as
the standard to match.

**Not built:** C2 session create/destroy/resolve and route handlers; C3 seed command; C4
authenticated image route; C5 polling routes.

**Not done beyond code:** nothing is committed (see below); FR-1707's documentation-only demo
credentials; User Guide and Developer Guide updates; the feature SDD summary; GitHub feature
issues; any Playwright journey.

## Your next action

**C2-T**, the first row of C2 in the gate ledger. C2 is session create, destroy and resolve,
plus the route handlers and the signed-out refusal, against FR-1701 to FR-1705a and AC-1701 to
AC-1706.

C2 is **control-type**, so its first acceptance criterion is that the control fires at all,
from the configuration an operator actually writes, before any test of its internal logic. The
control is: a request without a valid session cookie cannot reach data. Prove that end to end,
not by unit-testing a predicate.

C2 is the one row deliberately kept at full depth: both review gates, mutation tier A on the
authorization behaviour, and the security review as a **readiness** gate before implementation
rather than at the end. That ordering is not ceremony; see "The expensive lesson" below.

It is buildable now. `sessions` exists, and because `user_id` carries no foreign key, create,
destroy and resolve all work without a `users` table. Resolve returns a user id; whether that
user exists is INV-01's concern.

## Decisions already made. Do not relitigate these

- **`sessions.user_id` has no foreign key.** `users` is undefined and unowned; creating it here
  would decide INV-01's account schema unilaterally, and INV-01 is in flight on its own branch.
  INV-01 adds the constraint. A test asserts `sessions` has zero foreign-key constraints, so
  adding one here fails loudly. Recorded in the spec.
- **FR-1709 keeps its machinery here and its verification at INV-07.** The transaction helper
  and unique-violation mapping are tested against a fixture table with a partial unique index.
  The marketplace invariant is proven when the real `offers` table lands with a concurrent test.
  If that migration ships without the partial index, every INV-17 test still passes and the
  invariant is still broken.
- **Slice B's four suites migrate through `unscoped(reason)`**, not by binding the actor. They
  test the transaction primitive, not authorization, and binding would rewrite SQL that the
  concurrency proofs pin.
- **The correction-cycle budget is counted per task or batch**, never reset for a new assistant
  or policy. Slice A used 2 of 2. Slice B used 2 of 2. B9 used 1 of 2. Slice C rows have 2 each.
- **No ORM.** Plain `.sql` migrations, `postgres.js` over a direct connection, `supabase-js` for
  object storage only. This was reversed once already; the reasoning is in TD-02.

## C3 is partly blocked. Check before starting it

FR-1706 seeds demo accounts and listings, but `users` belongs to INV-01 and listings to
INV-04/05. Neither table exists. Only the guarded idempotent mechanism is buildable now, with
the content and AC-1707 verified when those tables land. Same shape as FR-1709. C5 is blocked
outright on INV-13 and INV-14 contracts.

## What changed on 2026-10-18, and why it matters to you

The workflow was tightened because this one feature consumed far more effort than its size
justifies. Four mechanisms, all in `DELEGATION-PROTOCOL.md`:

1. **The gate ledger.** Every task is a row written before delegation, gates included. A row
   whose `Blocked by` is unsatisfied is not briefed. A downstream row marked Complete above a
   gate still reading `No` is a recorded protocol breach, not a backfill. **Update the row when
   the worker returns, before the next brief goes out.** This failed three times in one sitting
   and each time a worker read the stale row and reasoned from it.
2. **Control-first ordering.** For anything whose job is to prevent something, test that it
   executes from the documented setup before testing whether its logic is right.
3. **Briefing by reference.** A brief gives a task ID, sections to read, this turn's new
   information, and stopping conditions. If context must be inlined, write it into a record
   first, then point at it.
4. **The coverage ceiling.** 80% branch coverage of `src/`, as a floor **and a ceiling**. Above
   it, nobody may ask for more tests, reviewers included. Alongside it: every acceptance
   criterion has at least one test that fails if the behaviour is removed, because coverage
   measures execution rather than assertion.

Reviewers may now block on **exactly two things**: an unmet acceptance criterion, or coverage
below threshold. Everything else is a note, capped at three per review. The old instruction to
attack and hunt is withdrawn, with one exception: where a control prevents one account's data
reaching another, attack it properly, by execution.

Mutation tier A is now reserved for that same exception. Tier B is one mutation proving a
blocking fix is discriminated. Tier C, meaning none, is the default.

## The expensive lesson, in one paragraph

A specification review of the database slice found four blocking defects. A security review
then found that the **test-isolation guard never executed** in the documented configuration,
because the variable it compared lived in a file the test process never read. A run could
truncate the application database, and the control had been corrected twice already without
anyone checking whether it ran. The same review found **TLS was off by default**, with
`sslmode=require` giving encryption without certificate verification. Both are fixed. The
lesson encoded in control-first ordering is: ask whether a control executes before asking
whether it is correct.

## Known gaps carried forward, none blocking

- **F7.** The raw-client lint rule restricts the exact specifier `@/server/db`. Five spellings
  reach a query-capable client from `src/` with clean lint and typecheck: a relative `../db`, a
  deeper relative path, `@/server/db/index`, a dynamic `import()`, and importing `postgres`
  directly. Core `no-restricted-imports` matches specifiers, not resolved paths. The owner chose
  to spend B9's cycle on it and then paused; the row exists as **B9-F7** with a fix sketched.
- **The seam's two declared limits**, pinned by tests rather than prose. It proves the actor was
  bound into a statement, not that it constrains the result. `authorize()` is available rather
  than imposed, so TD-12's one-snapshot guarantee is the caller's to honour. C2 and C4 are
  control-type and Deep precisely because the seam does not do their job for them.
- **No test raises a 23505 through the scoped tag.** One test in C2 or a later row closes it.
- **Cross-request hazards**, likely masked by Supabase's pooler but live in local development
  and on the session pooler: session-level state set inside `withTransaction` survives the
  commit and is visible to the next request on a warm instance, which `max: 1` makes more
  reachable; and the `tx` handle can be returned out of `withTransaction` and keeps executing in
  autocommit on a pooled connection.
- **A declined unique violation carries another user's row data.** For a 23505 the driver
  error's `detail` reads `Key (email)=(victim@example.com) already exists`, naming the
  pre-existing row, as an enumerable own property. In a marketplace that is account enumeration.
  The obligation not to render it is documented in `src/server/db` and nowhere else. A mechanism
  is owed, not another comment.
- **No statement or transaction timeout.**
- **The Supabase project-ref pattern** in the test-isolation guard is calibrated to fail safe but
  has never been checked against a real connection string. Custom-domain endpoints are untested.
- **The four migrated Slice B suites have no pristine baseline.** `expect()` counts and spot
  reads are consistent with a mechanical migration; that is not the same as verified.

## Environment facts you will need

**Nothing is committed.** Branch `codex/nextjs-migration` is at `7c16bcf` and has never been
pushed; no `origin/` ref exists for it. A prepared message sits at `COMMIT_MSG.txt` in the repo
root. Delete that file after committing; it is not meant to be tracked.

**INV-01 is blocked on this.** Its readiness assessment resumes only after a reviewed, committed
INV-17 baseline is integrated into `E:/CS3227-2610-MP3-INV01`, which has no `web/` directory at
all. "Reviewed" is satisfied; committed and integrated are not.

**No Git command has been executed by any assistant session in this migration.** The previous
assistant had no shell on the owner's machine, so branch state was read from `.git` refs as
files. If you have a shell, verify the state yourself rather than trusting that.

**Windows Git** needed command-scoped `safe.directory=E:/CS3227-2610-MP3` in this environment.
Do not change global Git configuration to work around it.

**`web/.env.local` holds real Supabase credentials and is gitignored.** It has never been
verified: run `npm run db:check` against it, which prints the server version and exits non-zero
on failure. Treat `web/AGENTS.md` as genuine; Next.js 16 generates that block itself, confirmed
against `node_modules/next/dist/server/lib/generate-agent-files.js`.

**Tests need `DATABASE_URL_TEST`** pointing at a throwaway PostgreSQL, separate from
`DATABASE_URL`. `docker-compose.yml` provides one, and any PostgreSQL works. Run the integration
lane single-lane; parallel runs clobber the shared test database. A UTF-8 BOM in `.env.local`
used to blind the isolation guard and is fixed, but it is the shape of bug to watch for on
Windows, where PowerShell writes BOMs by default.
