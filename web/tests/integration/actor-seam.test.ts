/**
 * ============================================================================
 * CONTRACT FOR THE IMPLEMENTER — INV17-B9, the actor seam
 * modules `@/server/actor` (new) and `@/server/db` (extended)
 * ============================================================================
 *
 * Source IDs: TD-11, TD-12, FR-1704, and the Security section of the technical
 * specification. This file is the contract; the acceptance criteria in the
 * INV17-B9 section of that specification are what it was written against, and
 * where the two disagree the specification wins and this file is wrong.
 *
 * This EXTENDS the handle contract in `db-transaction.test.ts` rather than
 * replacing it. Everything that file pins — one transaction on one connection,
 * a refusal out by identity, 23505 mapped only by the caller's mapper, a
 * swallowed failed write still failing — remains true. What changes is the
 * DOOR: `withTransaction` no longer hands out a bare `Tx`.
 *
 * ---------------------------------------------------------------------------
 * WHY THERE IS A SEAM AT ALL
 * ---------------------------------------------------------------------------
 *
 * TD-08 puts authorization entirely in application code and does not enable Row
 * Level Security, and the database role is privileged. So a repository query
 * with the actor's id in its WHERE clause and the same query without it are
 * both valid SQL, both return rows, both pass review on a tired afternoon, and
 * one of them is an unbounded cross-tenant read. Nothing underneath catches it.
 * TD-11 exists because the foundation made the safe version and the
 * catastrophic version look identical at the call site.
 *
 * THE HONEST LIMITS OF WHAT FOLLOWS, stated at the top rather than buried,
 * because a control that is believed to do more than it does is how INV-17
 * shipped a guard that never ran. The type system can guarantee that you cannot
 * obtain a query-capable handle without an actor. The runtime can guarantee
 * that the actor's id was BOUND INTO the statement. Beyond that:
 *
 *   L1. NOTHING HERE PROVES THE BINDING CONSTRAINS THE RESULT. A repository can
 *       satisfy every check with `select ${tx.actorId} as me, * from listings`,
 *       which leaks everything. Review and the lint allow-list are what cover
 *       that step.
 *
 *   L2. `authorize` IS OPT-IN, SO TD-12 IS CONDITIONAL ON THE CALLER. Nothing
 *       in this seam requires a service to perform its permission read through
 *       `authorize` rather than the ordinary scoped tag. A service that reads
 *       the grant on the plain tag, gates a write on the result and commits
 *       passes every test, every type check and every lint rule here, and is
 *       exactly the write skew criterion 4 forbids. The seam MAKES the
 *       guarantee available and does not IMPOSE it.
 *
 *       This is pinned by execution rather than asserted in prose — see
 *       'what this seam does not guarantee' at the end of this file — so the
 *       claim cannot quietly grow back. Requiring it is a real option (refuse a
 *       write on a handle where `authorize` has not run) and it is NOT taken
 *       here, because a create has no prior permission to read and the rule
 *       would have to carve that out before Slice C has written a single
 *       service to carve it out for. B9-R1 recorded the choice; whoever writes
 *       the first guarded write in Slice C should revisit it.
 *
 * Both limits were missing from the first version of this header. The second
 * was found at the readiness gate, by a reviewer who wrote the bypass and ran
 * it, which is the only reason it is written down rather than discovered in
 * Slice C.
 *
 * ---------------------------------------------------------------------------
 * THE SURFACE
 * ---------------------------------------------------------------------------
 *
 *   // ---- @/server/actor ----
 *
 *   // Declared, never exported as a value, so no caller can name it and no
 *   // object literal can satisfy `Actor`. Same idea as the refusal catalogue's
 *   // AUTHORING_TOKEN, applied one level earlier: at the TYPE.
 *   declare const ACTOR_IDENTITY: unique symbol;
 *
 *   export interface SessionRecord {
 *     readonly sessionId: string;
 *     readonly userId: string;
 *   }
 *
 *   export type Actor = {
 *     readonly sessionId: string;
 *     readonly userId: string;
 *     readonly [ACTOR_IDENTITY]: true;
 *   };
 *
 *   /** The ONLY producer of an Actor anywhere in src/. *\/
 *   export function actorFromSession(session: SessionRecord): Actor;
 *
 *   /** WeakSet membership: "this module made this". Unforgeable at runtime. *\/
 *   export function isAuthoredActor(value: unknown): value is Actor;
 *
 *   // ---- @/server/db ----
 *
 *   /** A programmer fault at the seam. NOT a refusal: no user can act on it. *\/
 *   export class ActorScopeError extends Error { readonly name: 'ActorScopeError' }
 *   export class ClosedHandleError extends Error { readonly name: 'ClosedHandleError' }
 *
 *   /** The one fixed token. See DECISION 4. *\/
 *   export const UNSCOPED_TOKEN = 'unscoped';
 *
 *   declare const ACTOR_REF: unique symbol;
 *   export type ActorRef = { readonly [ACTOR_REF]: true };
 *
 *   export interface ActorTx {
 *     /** The actor-scoped query tag. See DECISION 3. *\/
 *     <T extends readonly postgres.MaybeRow[] = postgres.Row[]>(
 *       strings: TemplateStringsArray,
 *       ...values: readonly unknown[]
 *     ): postgres.PendingQuery<T>;
 *
 *     /** The authorization read. Same scope rule, plus a row lock. DECISION 5. *\/
 *     readonly authorize: <T extends readonly postgres.MaybeRow[] = postgres.Row[]>(
 *       strings: TemplateStringsArray,
 *       ...values: readonly unknown[]
 *     ) => postgres.PendingQuery<T>;
 *
 *     readonly actor: Actor;
 *     /** Interpolate this, not `actor.userId`. See DECISION 3. *\/
 *     readonly actorId: ActorRef;
 *
 *     unscoped(reason: string): Tx;
 *   }
 *
 *   export function withTransaction<T>(
 *     actor: Actor,
 *     run: (tx: ActorTx) => Promise<T>,
 *     options?: TransactionOptions,
 *   ): Promise<T>;
 *
 * `TransactionOptions` is unchanged: `{ db?, onUniqueViolation? }`. There is
 * deliberately NO `actor` field on it. An option is a thing a caller may omit,
 * and the one argument that must never be omittable is this one.
 *
 * ---------------------------------------------------------------------------
 * DECISION 1 — THE ACTOR IS A PARAMETER OF `withTransaction`, NOT AN OPTION,
 *              AND NOT A SECOND ENTRY POINT
 * ---------------------------------------------------------------------------
 *
 * The acceptance criterion is that a query issued inside `withTransaction`
 * without an actor DOES NOT COMPILE. That rules out the comfortable design,
 * which is to leave `withTransaction` alone and add `withActorTransaction`
 * beside it: every repository would still be one import away from the
 * unscoped door, and the seam would be a convention rather than a control.
 *
 * THAT SENTENCE WAS TRUE AND INCOMPLETE, and B9-R1 proved it by writing the
 * bypass. `getDb()` is also one import away, it is exported from this very
 * module, and `getDb()\`select * from listings\`` returns every tenant's rows
 * with no actor, no diagnostic and no lint message. Gating `withTransaction`
 * alone leaves the door it was gating beside an ungated one. DECISION 8 closes
 * it, and without DECISION 8 this decision does not deliver criterion 2 at all.
 *
 * So `withTransaction` takes the actor FIRST and there is no overload without
 * it. Omitting it is TS2554 (wrong argument count) or TS2345 (a callback is not
 * an `Actor`), pinned in `tests/unit/actor-seam-compile.test.ts`.
 *
 * CONSEQUENCE THE IMPLEMENTER MUST NOT ABSORB QUIETLY: every existing caller in
 * `tests/integration/db-*.test.ts` uses the old two-argument form and will stop
 * compiling. Those files belong to Slice B, whose verification review (B6) has
 * not run and which reverts fixes against them. They are NOT migrated here, and
 * migrating them is not the implementer's to do either, since `web/tests/**` is
 * outside B9-I's allowed edits. This is reported as a ledger gap rather than
 * fixed in passing.
 *
 * ALSO DELIBERATE: the actor is a runtime argument, never ambient. Not an
 * AsyncLocalStorage, not a module-level "current actor", not a GUC. See
 * DECISION 7.
 *
 * ---------------------------------------------------------------------------
 * DECISION 2 — AN `Actor` COMES FROM A SESSION AND FROM NOWHERE ELSE
 * ---------------------------------------------------------------------------
 *
 * FR-1704: every operation acts as the session's own user, and a request
 * carries no user identifier chosen by the client. The seam enforces that by
 * making `Actor` unconstructible outside `@/server/actor`:
 *
 *   - At COMPILE TIME, by the `unique symbol` property. A caller cannot name
 *     the symbol, so `{ userId: 'victim', sessionId: 's' } as Actor` is the
 *     only way through and a cast is greppable and lint-visible.
 *   - At RUNTIME, by WeakSet membership, because a cast compiles. This is the
 *     half that actually holds: `withTransaction` must reject anything
 *     `isAuthoredActor` does not recognise, with a TypeError, BEFORE opening a
 *     transaction. A forged actor must not reach SQL even once.
 *
 * `actorFromSession` takes a `SessionRecord`, never a bare user id. The
 * signature is the point: there is no function in `src/` into which a route
 * handler can post a user id and receive the authority to act as them.
 *
 * WHAT C2 CHANGES, AND WHAT IT MUST NOT. There is no sessions table yet. C2
 * adds the read — resolve the opaque cookie token, find the session row, reject
 * it if expired or revoked — and hands the result to `actorFromSession`
 * unchanged. The assumption this file is built on is that the actor's identity
 * is exactly `{ sessionId, userId }` and that both are strings the session row
 * supplies. If C2 needs a third field (a role, say), it is ADDED to
 * `SessionRecord` and `Actor`; nothing in the handle, the scope check or the
 * lint rule depends on the field list.
 *
 * ---------------------------------------------------------------------------
 * DECISION 3 — THE SCOPED TAG REQUIRES THE ACTOR TO BE BOUND, BY IDENTITY
 * ---------------------------------------------------------------------------
 *
 * This is the part that earns the word "control", so it gets the longest note.
 *
 * Passing an actor to `withTransaction` and then forgetting to use it is the
 * ACTUAL accident. A seam that only required the argument would be satisfied by
 * every query that has ever leaked a row. So the handle itself checks.
 *
 *   await tx`select title from listings_fixture where owner = ${tx.actorId}`
 *
 * `tx.actorId` is a per-handle marker OBJECT. The scoped tag scans the
 * interpolated values for it BY IDENTITY (`===`), substitutes the actor's real
 * user id in its place, and delegates to the underlying `Tx`. A statement that
 * does not carry the marker throws `ActorScopeError` and never reaches the
 * server.
 *
 * Identity, and a per-handle object, for three reasons:
 *
 *   1. It cannot be forged. A string compare against `actor.userId` could be
 *      satisfied by any query that happened to mention that id for any reason,
 *      including one selecting it as a column value.
 *   2. It cannot be hoisted. A module-level constant marker shared by every
 *      handle could be smuggled into a query built for a different actor.
 *   3. It reads at the call site as what it is. `${tx.actorId}` in a WHERE
 *      clause is the thing a reviewer is looking for.
 *
 * AND THE LIMIT, again: the check proves the actor's id was bound into the
 * statement. It does not prove the binding constrains the result. A repository
 * can satisfy it with `select ${tx.actorId} as me, * from listings_fixture`,
 * which leaks everything. That query is wrong, this seam does not catch it, and
 * no test below claims otherwise.
 *
 * The scoped handle exposes NOTHING ELSE. No `unsafe`, no `begin`, no `file`,
 * no `end`, no `json`. Those are how a bypass gets written without anyone
 * typing the word `unscoped`, and `tests/unit/actor-seam-compile.test.ts` pins
 * their absence from the type while the test below pins their absence at
 * runtime.
 *
 * ---------------------------------------------------------------------------
 * DECISION 4 — `unscoped(reason)` IS THE ONE DOOR OUT, AND IT IS ONE TOKEN
 * ---------------------------------------------------------------------------
 *
 * Some queries genuinely are not actor-scoped. TD-11 names public listing
 * search. The rule is that they say so, in one fixed spelling, so the set of
 * them is a `grep` rather than a reading of every repository:
 *
 *   const sql = tx.unscoped('public listing search, FR-1706');
 *
 * `unscoped` returns the underlying `Tx`, with all of postgres.js on it. It
 * takes a `reason` and refuses an empty one, because the reason is the review
 * artefact: an `unscoped()` with no argument is a bypass nobody has to justify.
 *
 * ONE SPELLING, NO ALIASES. Not `raw()`, not `withoutActor()`, not a
 * `{ scoped: false }` option. The token `unscoped` is exported as
 * `UNSCOPED_TOKEN` so the lint rule and the tests agree on the string rather
 * than each hard-coding it.
 *
 * LINT, NOT TYPES, bounds where it may be called: an allow-list in
 * `eslint.config.mjs`, with every other path under `src/` rejected. The type
 * system cannot express "this method may be called from these files", and a
 * convention nobody enforces is what TD-11 was written to replace.
 * `tests/unit/actor-seam-boundaries.test.ts` drives ESLint itself and proves
 * the rule fires, because a lint rule that nothing tests is the same class of
 * artefact as a guard that never runs.
 *
 * ---------------------------------------------------------------------------
 * DECISION 5 — `authorize` IS A READ THAT LOCKS, AND TD-12 NEEDS IT TO
 * ---------------------------------------------------------------------------
 *
 * TD-12 says the authorization read happens inside the same transaction as the
 * writes it guards, on the same handle, and the acceptance criterion is sharper
 * than that: a permission revoked by a concurrent committed transaction cannot
 * let a write through.
 *
 * SAME TRANSACTION IS NOT ENOUGH, and this is the trap worth spelling out. At
 * READ COMMITTED, which is what this project runs and what the Slice B tests
 * assume, T1 reads the grant, T2 deletes it and commits, T1 writes, T1 commits.
 * Every statement is correct, one transaction, and the write landed on a
 * permission that no longer existed. REPEATABLE READ does not close it either:
 * the read and the write touch different rows, so there is no conflict to
 * detect, which is ordinary write skew.
 *
 * What closes it is the read taking a lock that the revoke must wait behind.
 * So the authorization read is its own tag, and it requires a row-lock clause:
 *
 *   await tx.authorize`
 *     select 1 from listing_grants_fixture
 *     where user_id = ${tx.actorId} and listing_id = ${listingId}
 *     for share
 *   `
 *
 * `authorize` applies the DECISION 3 scope check AND additionally rejects, with
 * `ActorScopeError`, any statement whose STATIC text does not contain one of
 * `for share`, `for update`, `for no key update`, `for key share`.
 *
 * Static text, specifically: the check reads the `strings` array of the
 * template and never the interpolated values. A bound value containing the
 * words "for share" is data and cannot satisfy it. That is not a nicety; it is
 * what stops the check being satisfiable by a listing title.
 *
 * AND STATIC TEXT IS NOT THE SAME AS THE SQL THE SERVER EXECUTES. A substring
 * scan over the raw template is satisfied by text PostgreSQL ignores, which
 * B9-R1 demonstrated:
 *
 *     tx.authorize`select 1 from listing_grants_fixture
 *                  where user_id = ${tx.actorId} -- TODO: for share`
 *
 * No lock is taken, the check passes, and TD-12 is silently unmet by a line
 * that reads like a reminder to do the thing it is standing in for. So before
 * the scan, the implementation MUST remove from the static text:
 *
 *   - `--` line comments, to end of line
 *   - `/* ... *\/` block comments
 *   - single-quoted string literals
 *
 * A `for share` surviving all three is in the executable SQL. The commented,
 * block-commented and quoted forms are each pinned below, because this check
 * is the whole of TD-12 and a check that a TODO satisfies is not one.
 *
 * Why the caller writes the lock clause rather than the seam appending it: the
 * seam does not know whether the guarded write updates the permission row
 * (`for update`) or merely depends on it (`for share`), and guessing the
 * stronger one serialises reads that need not be serialised while guessing the
 * weaker one is unsound. The repository knows. The seam checks that it said.
 *
 * ---------------------------------------------------------------------------
 * DECISION 6 — AN ACTOR-BOUND HANDLE DIES WITH ITS TRANSACTION
 * ---------------------------------------------------------------------------
 *
 * After `withTransaction` settles — committed OR rolled back — the `ActorTx`
 * and every `Tx` handed out by `unscoped()` during it must reject with
 * `ClosedHandleError`.
 *
 * Without this, a handle captured in a closure outlives the transaction and
 * then runs on a pooled connection that has since been handed to someone else,
 * carrying an actor from a request that finished. That is the same failure
 * shape as DECISION 7 and arrives by a different route.
 *
 * The closed check runs BEFORE the scope check, so a correctly-scoped query on
 * a dead handle reports the real problem.
 *
 * ---------------------------------------------------------------------------
 * DECISION 7 — THE ACTOR IS NEVER SESSION-LEVEL STATE ON THE CONNECTION
 * ---------------------------------------------------------------------------
 *
 * The obvious implementation of "the handle carries the actor" is to tell the
 * SERVER: `set_config('hotshop.actor', $1, false)` at the top of the
 * transaction, then let queries read it. It is also forbidden here.
 *
 * `set` and `set_config(..., false)` are SESSION-level: they survive the commit
 * and stay on the connection. With `max: 1` and a warm Vercel instance holding
 * that connection across invocations, the next request on that instance starts
 * with the previous user's identity already installed, and a query that forgot
 * its filter reads their rows under their name. The suite would never see it,
 * because a test process opens a fresh connection and its first request sets
 * the value anyway.
 *
 * The test below is therefore not a style check. It runs a transaction, asserts
 * the backend PID is unchanged afterwards (otherwise the probe proves nothing,
 * which is worse than failing), and then searches `pg_settings` for the actor's
 * own id. A `set_config(..., true)` — transaction-local — would be reverted at
 * commit and pass; so would carrying the actor in TypeScript, which is the
 * intended design. Nothing session-level passes.
 *
 * ---------------------------------------------------------------------------
 * DECISION 8 — THE RAW CLIENT IS NOT REACHABLE FROM `src/` OUTSIDE THE SEAM
 * ---------------------------------------------------------------------------
 *
 * ADDED AT THE READINESS GATE, by B9-R1 finding F1, and it is the finding that
 * mattered. Everything above gates the door marked `withTransaction`. Nobody
 * has to walk through it:
 *
 *     import { getDb } from '@/server/db';
 *     export async function everyListing() {
 *       return getDb()`select * from listings_fixture`;
 *     }
 *     export async function inATx() {
 *       return getDb().begin(async (tx) => tx`select * from listings_fixture`);
 *     }
 *
 * Every tenant's rows, zero `tsc` diagnostics, zero ESLint messages, suite
 * unchanged. Criterion 2 says `unscoped()` is REQUIRED for a deliberately
 * unscoped query; as the contract stood it was required only to get past a
 * function nobody had to call.
 *
 * So the raw client joins `unscoped` behind the same mechanism. `getDb` and
 * `createDb` may not be IMPORTED from `@/server/db` anywhere under `src/`
 * except the allow-list, which is the seam itself plus the operator script's
 * own entry point. Both import spellings are covered, because a namespace
 * import is the obvious way around a named-import rule:
 *
 *     import { getDb } from '@/server/db';     // rejected
 *     import * as db from '@/server/db';       // rejected
 *
 * Lint and not types, for the same reason as DECISION 4: TypeScript cannot say
 * "this export may be imported by these files". `withTransaction` keeps using
 * `getDb` internally — it is inside the allow-list — so nothing about the
 * connection lifecycle changes. `scripts/**` is outside `src/` and outside this
 * rule; `scripts/db-check.ts` connects on purpose and has no actor to carry.
 *
 * WHAT THIS DID NOT CHANGE, checked rather than assumed: DECISIONS 2, 3, 6 and
 * 7 are untouched, and the `Db`/`Tx` TYPES stay exported, because a repository
 * signature naming `Tx` is how `unscoped()` is consumed. The rule is about
 * reaching a client, not about naming its type.
 *
 * ---------------------------------------------------------------------------
 * WHAT IS NOT IN THIS SLICE
 * ---------------------------------------------------------------------------
 *
 * No sessions table and no cookie handling: C1 and C2. No roles, no admin
 * actor, no impersonation. No retry around the lock wait in DECISION 5, for the
 * reason `isRetryable` ships without one. No nested or savepointed transactions,
 * unchanged from the Slice B contract.
 * ============================================================================
 */

import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { actorFromSession, isAuthoredActor, type Actor } from '@/server/actor';
import {
  ActorScopeError,
  ClosedHandleError,
  UNSCOPED_TOKEN,
  closeDb,
  createDb,
  withTransaction,
  type ActorRef,
  type ActorTx,
  type Db,
  type Tx,
} from '@/server/db';

import { testActor } from '../helpers/actor';
import {
  recordingClient,
  sessionLevelStatements,
  settingsTouched,
} from '../helpers/recording-client';
import { TRUNCATE_FIXTURES, blockedBackendCount, deferred, waitFor } from '../helpers/test-database';
import { testDatabaseUrl } from '../setup/integration-setup';

let url: string;
let db: Db;
/** A second, independent client. Anything read here is outside the transaction. */
let observer: Db;
/**
 * A THIRD client, and it is not redundant. Every client here is `max: 1`, so
 * asking `observer` whether anything is blocked while `observer` itself is the
 * thing blocked waits for a connection that will never come free. The TD-12
 * test needs to watch the lock from outside both parties.
 */
let probe: Db;

beforeAll(() => {
  url = testDatabaseUrl();
  db = createDb(url);
  observer = createDb(url);
  probe = createDb(url);
});

afterAll(async () => {
  await db.end();
  await observer.end();
  await probe.end();
  await closeDb();
});

beforeEach(async () => {
  await db.unsafe(TRUNCATE_FIXTURES);
});

afterEach(async () => {
  vi.unstubAllEnvs();
  // The criterion-1 tests deliberately go through `getDb()`, so the singleton
  // may be holding a connection to the test database. Ended here rather than
  // only in `afterAll`, so one file's stubbed DATABASE_URL cannot outlive the
  // test that stubbed it.
  await closeDb();
  await db.unsafe(TRUNCATE_FIXTURES);
});

async function seedListing(owner: string, id = 'bike', title = 'Road bike'): Promise<void> {
  await db`
    insert into listings_fixture (id, owner, title) values (${id}, ${owner}, ${title})
  `;
}

async function seedGrant(userId: string, listingId = 'bike'): Promise<void> {
  await db`
    insert into listing_grants_fixture (user_id, listing_id) values (${userId}, ${listingId})
  `;
}

async function titleOf(client: Db, id = 'bike'): Promise<string | undefined> {
  const rows = await client<{ title: string }[]>`
    select title from listings_fixture where id = ${id}
  `;
  return rows[0]?.title;
}

async function grantCount(client: Db): Promise<number> {
  const rows = await client<{ n: number }[]>`
    select count(*)::int as n from listing_grants_fixture
  `;
  return rows[0]!.n;
}

/* ==========================================================================
 * CRITERION 1, AND IT COMES FIRST ON PURPOSE.
 *
 * Control-first, per the delegation protocol. Before any test of what the seam
 * decides, a test that the seam RUNS — reached the way the application reaches
 * it, from the configuration the project's own `.env.example`, `db-check.ts`
 * and `@/server/db` error message tell an operator to write: `DATABASE_URL`,
 * read by `getDb()`, with no `options.db` anywhere in sight.
 *
 * This is the shape of the INV-17 failure being guarded against. The
 * test-isolation guard was correct, reviewed three times, and never executed,
 * because every test called it with inputs the test supplied rather than
 * starting where the operator starts. A scope check that only fires when a test
 * passes `{ db }` is that bug again.
 * ========================================================================== */
describe('INV17-B9 criterion 1: the control fires on the operator-configured path', () => {
  /**
   * The shape every Slice C service will have: an actor in, a transaction, a
   * repository-style query on the handle it was given. No `options.db`, so the
   * client comes from `getDb()` reading `DATABASE_URL`.
   */
  async function renameMyListing(actor: Actor, id: string, title: string): Promise<number> {
    return withTransaction(actor, async (tx) => {
      const rows = await tx<{ id: string }[]>`
        update listings_fixture set title = ${title}
        where id = ${id} and owner = ${tx.actorId}
        returning id
      `;
      return rows.length;
    });
  }

  /** The same service with the filter forgotten. The accident, exactly. */
  async function renameAnyListing(actor: Actor, id: string, title: string): Promise<number> {
    return withTransaction(actor, async (tx) => {
      const rows = await tx<{ id: string }[]>`
        update listings_fixture set title = ${title} where id = ${id} returning id
      `;
      return rows.length;
    });
  }

  it('commits an actor-scoped write through DATABASE_URL, with no options.db', async () => {
    vi.stubEnv('DATABASE_URL', url);
    const actor = testActor();
    await seedListing(actor.userId);

    expect(await renameMyListing(actor, 'bike', 'Renamed')).toBe(1);
    expect(await titleOf(observer)).toBe('Renamed');
  });

  it('refuses the unscoped write on that same path, before it reaches the server', async () => {
    vi.stubEnv('DATABASE_URL', url);
    const actor = testActor();
    await seedListing(actor.userId);

    await expect(renameAnyListing(actor, 'bike', 'Renamed')).rejects.toBeInstanceOf(
      ActorScopeError,
    );

    // Not merely rejected: nothing was written, so the statement never ran.
    expect(await titleOf(observer)).toBe('Road bike');
  });

  it("names the actor marker in the refusal, so the fix is obvious from the message", async () => {
    vi.stubEnv('DATABASE_URL', url);
    const actor = testActor();

    const thrown = await renameAnyListing(actor, 'bike', 'Renamed').catch(
      (error: unknown) => error,
    );

    expect((thrown as Error).message).toContain('actorId');
    expect((thrown as Error).message).toContain(UNSCOPED_TOKEN);
  });

  it("another user's listing is untouched by the scoped write", async () => {
    vi.stubEnv('DATABASE_URL', url);
    const mine = testActor();
    const theirs = testActor();
    await seedListing(mine.userId, 'bike', 'Road bike');
    await seedListing(theirs.userId, 'lamp', 'Desk lamp');

    expect(await renameMyListing(mine, 'lamp', 'Stolen')).toBe(0);
    expect(await titleOf(observer, 'lamp')).toBe('Desk lamp');
  });
});

/* ==========================================================================
 * DECISION 3. The scope check itself.
 * ========================================================================== */
describe('the scoped tag requires the actor marker', () => {
  it('substitutes the marker with the actor user id', async () => {
    const actor = testActor();
    await seedListing(actor.userId);

    const rows = await withTransaction(
      actor,
      async (tx) =>
        tx<{ owner: string }[]>`
          select owner from listings_fixture where owner = ${tx.actorId}
        `,
      { db },
    );

    expect(rows).toHaveLength(1);
    expect(rows[0]!.owner).toBe(actor.userId);
  });

  it('throws ActorScopeError for a statement with no marker', async () => {
    const actor = testActor();

    const thrown = await withTransaction(
      actor,
      async (tx) => tx`select count(*) from listings_fixture`,
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(ActorScopeError);
  });

  it('is not satisfied by the actor user id passed as an ordinary string', async () => {
    // The whole reason the marker is an object compared by identity. A string
    // compare would wave this through, and this query is unscoped.
    const actor = testActor();
    await seedListing(actor.userId);

    const thrown = await withTransaction(
      actor,
      async (tx) => tx`select ${actor.userId}::text as me, count(*) from listings_fixture`,
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(ActorScopeError);
  });

  /**
   * Hoisting defence, and it has to be stolen from a LIVE handle.
   *
   * The first version of this test awaited the first transaction to completion
   * before starting the second, so the marker it stole always belonged to a
   * CLOSED handle. B9-R1 pointed out what that lets through: an implementation
   * that rejects markers only from settled handles passes, and then accepts a
   * marker taken from a concurrently open one and substitutes actor A's id into
   * actor B's transaction. That is reason 2 of the three DECISION 3 gives for
   * comparing by identity, and the old test did not reach it.
   *
   * Two clients, because every client here is `max: 1` and the two
   * transactions must genuinely overlap.
   */
  it("is not satisfied by a marker stolen from another LIVE handle", async () => {
    const first = testActor();
    const second = testActor();
    const open = deferred<ActorRef>();
    const release = deferred();

    const holder = withTransaction(
      first,
      async (tx) => {
        open.resolve(tx.actorId);
        await release.promise;
        return 'done';
      },
      { db },
    );

    const stolen = await open.promise;

    const thrown = await withTransaction(
      second,
      async (tx) => {
        // The handle is still open, right now, in the other transaction.
        void tx;
        return tx`select * from listings_fixture where owner = ${stolen}`;
      },
      { db: observer },
    ).catch((error: unknown) => error);

    release.resolve();
    await holder;

    expect(thrown).toBeInstanceOf(ActorScopeError);
    // Specifically NOT a closed-handle complaint: the donor was alive, so an
    // implementation that only rejects settled markers cannot pass by
    // accident.
    expect(thrown).not.toBeInstanceOf(ClosedHandleError);
  });

  it('still rejects a marker stolen from a handle that has since closed', async () => {
    const first = testActor();
    const second = testActor();

    let stolen: unknown;
    await withTransaction(
      first,
      async (tx) => {
        stolen = tx.actorId;
      },
      { db },
    );

    const thrown = await withTransaction(
      second,
      async (tx) => tx`select * from listings_fixture where owner = ${stolen}`,
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(ActorScopeError);
    expect(thrown).not.toBeInstanceOf(ClosedHandleError);
  });

  it('accepts the marker anywhere in the statement, once', async () => {
    const actor = testActor();
    await seedListing(actor.userId, 'bike');
    await seedListing(actor.userId, 'lamp');

    const rows = await withTransaction(
      actor,
      async (tx) =>
        tx<{ id: string }[]>`
          select id from listings_fixture
          where owner = ${tx.actorId} and id = any(${['bike', 'lamp']})
          order by id
        `,
      { db },
    );

    expect(rows.map((row) => row.id)).toEqual(['bike', 'lamp']);
  });

  /**
   * DECISION 3, the runtime half: nothing on the scoped handle reaches the
   * connection except the tags.
   *
   * THE NAMES ARE ENUMERATED FROM THE REAL HANDLE, not listed by hand. The
   * first version of this test checked a hand-written list of eight, and
   * B9-R1 found that four of them (`begin`, `end`, `reserve`, `listen`) are
   * not on a `TransactionSql` at all, so they passed against anything — while
   * `savepoint`, which IS on it, was absent from the list and is a complete
   * bypass:
   *
   *     tx.savepoint((sp) => sp`select * from listings_fixture`)
   *
   * returned every row, with no actor and no `unscoped`. A list maintained by
   * hand against someone else's library goes stale in exactly this direction,
   * so the list now comes from the library: whatever the underlying handle
   * carries, the scoped one must not.
   */
  it('exposes nothing the underlying transaction handle carries', async () => {
    const actor = testActor();

    const { leaked, enumerated } = await withTransaction(
      actor,
      async (tx) => {
        const raw = tx.unscoped('enumerating the real surface for comparison');
        const names = [
          ...new Set([...Object.keys(raw), ...Object.getOwnPropertyNames(raw)]),
        ].filter((name) => !['length', 'name', 'prototype'].includes(name));

        const handle = tx as unknown as Record<string, unknown>;
        return {
          enumerated: names,
          leaked: names.filter((name) => handle[name] !== undefined),
        };
      },
      { db },
    );

    // The enumeration must have found something, or the filter below is a
    // filter over nothing and this test cannot fail.
    expect(enumerated).toEqual(expect.arrayContaining(['unsafe', 'savepoint']));
    expect(enumerated.length).toBeGreaterThanOrEqual(6);

    expect(leaked).toEqual([]);
  });

  it('has savepoint in particular, and does not pass it on', async () => {
    // Named separately because it is the one that was missed, and because a
    // savepoint handle is a fully functional unscoped client rather than a
    // nuisance method.
    const actor = testActor();
    await seedListing(testActor().userId, 'bike');

    const reachable = await withTransaction(
      actor,
      async (tx) => (tx as unknown as Record<string, unknown>)['savepoint'],
      { db },
    );

    expect(reachable).toBeUndefined();
  });
});

/* ==========================================================================
 * DECISION 4. The one door out.
 * ========================================================================== */
describe(`${UNSCOPED_TOKEN}() is the only bypass, and it is deliberate`, () => {
  it('runs a query with no actor filter', async () => {
    const actor = testActor();
    await seedListing(testActor().userId, 'bike');
    await seedListing(testActor().userId, 'lamp');

    const rows = await withTransaction(
      actor,
      async (tx) => {
        const sql = tx.unscoped('public listing search, FR-1706');
        return sql<{ id: string }[]>`select id from listings_fixture order by id`;
      },
      { db },
    );

    expect(rows.map((row) => row.id)).toEqual(['bike', 'lamp']);
  });

  it('is the same transaction, so its writes roll back with everything else', async () => {
    const actor = testActor();

    await expect(
      withTransaction(
        actor,
        async (tx) => {
          const sql = tx.unscoped('fixture setup');
          await sql`insert into listings_fixture (id, owner, title) values ('bike', 'x', 'y')`;
          throw new Error('rollback');
        },
        { db },
      ),
    ).rejects.toThrow('rollback');

    expect(await titleOf(observer)).toBeUndefined();
  });

  it('refuses an empty reason, because the reason is the review artefact', async () => {
    const actor = testActor();

    await expect(
      withTransaction(actor, async (tx) => tx.unscoped('   '), { db }),
    ).rejects.toBeInstanceOf(ActorScopeError);
  });

  it('exports the token as one fixed string the lint rule and the tests share', () => {
    expect(UNSCOPED_TOKEN).toBe('unscoped');
  });
});

/* ==========================================================================
 * DECISION 2. FR-1704, the runtime half.
 * ========================================================================== */
describe('FR-1704: the actor on the handle is the one that came from the session', () => {
  it('hands the callback the identical actor object', async () => {
    const actor = testActor();

    const seen = await withTransaction(actor, async (tx) => tx.actor, { db });

    expect(seen).toBe(actor);
  });

  it('does not let the callback replace it', async () => {
    const actor = testActor();
    const other = testActor();

    const seen = await withTransaction(
      actor,
      async (tx) => {
        try {
          (tx as unknown as { actor: Actor }).actor = other;
        } catch {
          // A frozen or getter-only property throws in strict mode, which is
          // the preferred implementation. Either way the next line decides.
        }
        return tx.actor;
      },
      { db },
    );

    expect(seen).toBe(actor);
  });

  it('rejects a hand-built actor before opening a transaction', async () => {
    // The cast is the point: `Actor`'s unique symbol stops this at compile
    // time, so the only way here is a cast or plain JavaScript, and that is
    // exactly the path a runtime check exists for.
    const forged = { userId: 'victim', sessionId: 'whatever' } as unknown as Actor;

    await expect(
      withTransaction(forged, async (tx) => tx`select 1 where ${tx.actorId}::text is not null`, { db }),
    ).rejects.toBeInstanceOf(TypeError);
  });

  it('reaches no SQL at all when the actor is forged', async () => {
    // "Nothing was written" is satisfied by a transaction that opened, ran the
    // insert and rolled back, which is not what the header claims. B9-R1 asked
    // for the stronger pin, so the statements are counted rather than the rows:
    // nothing may be sent, not even a BEGIN.
    const forged = { userId: 'victim', sessionId: 'whatever' } as unknown as Actor;
    const recorder = recordingClient(db);

    await withTransaction(
      forged,
      async (tx) => {
        const sql = tx.unscoped('would be a write');
        return sql`insert into listings_fixture (id, owner, title) values ('x', 'y', 'z')`;
      },
      { db: recorder.db },
    ).catch(() => undefined);

    expect(recorder.statements).toEqual([]);
    expect(await titleOf(observer, 'x')).toBeUndefined();
  });

  it('records statements when the actor is real, so the probe above can fail', async () => {
    // The companion the empty-array assertion needs. Without it, a recorder
    // wired to nothing would prove the forged case by proving nothing.
    const recorder = recordingClient(db);

    await withTransaction(
      testActor(),
      async (tx) => tx`select ${tx.actorId}::text as me`,
      { db: recorder.db },
    );

    expect(recorder.statements).not.toEqual([]);
  });

  it('recognises only what actorFromSession made', () => {
    const actor = actorFromSession({ userId: 'u', sessionId: 's' });

    expect(isAuthoredActor(actor)).toBe(true);
    expect(isAuthoredActor({ ...actor })).toBe(false);
    expect(isAuthoredActor({ userId: 'u', sessionId: 's' })).toBe(false);
    expect(isAuthoredActor(undefined)).toBe(false);
  });

  it('carries the session id, not only the user id', () => {
    // FR-1702/1703 give one account several concurrent sessions, and a logout
    // ends one of them. An actor that knew only the user could not tell C2's
    // revocation which session acted.
    const actor = actorFromSession({ userId: 'u', sessionId: 's' });

    expect(actor.userId).toBe('u');
    expect(actor.sessionId).toBe('s');
  });
});

/* ==========================================================================
 * DECISION 5. TD-12.
 * ========================================================================== */
describe('TD-12: an authorization read and the writes it guards see one snapshot', () => {
  it('rejects an authorize read that takes no row lock', async () => {
    const actor = testActor();
    await seedGrant(actor.userId);

    const thrown = await withTransaction(
      actor,
      async (tx) =>
        tx.authorize`
          select 1 from listing_grants_fixture
          where user_id = ${tx.actorId} and listing_id = 'bike'
        `,
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(ActorScopeError);
    expect((thrown as Error).message).toMatch(/for share|for update|row lock/i);
  });

  it('rejects an authorize read with no actor marker', async () => {
    const actor = testActor();

    const thrown = await withTransaction(
      actor,
      async (tx) =>
        tx.authorize`select 1 from listing_grants_fixture where listing_id = 'bike' for share`,
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(ActorScopeError);
  });

  it('is not satisfied by a lock clause arriving as a bound value', async () => {
    // The check reads the static strings and never the values, so a listing
    // title cannot talk its way past it.
    const actor = testActor();

    const thrown = await withTransaction(
      actor,
      async (tx) =>
        tx.authorize`
          select ${'for share'}::text from listing_grants_fixture where user_id = ${tx.actorId}
        `,
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(ActorScopeError);
  });

  /**
   * B9-R1, F3. A substring scan over the raw template is satisfied by text the
   * server never executes, and the commented form is the realistic one: it
   * reads like a reminder to add the lock, while standing in for it.
   */
  it('is not satisfied by a lock clause inside a line comment', async () => {
    const actor = testActor();
    await seedGrant(actor.userId);

    const thrown = await withTransaction(
      actor,
      async (tx) =>
        tx.authorize`select 1 from listing_grants_fixture where user_id = ${tx.actorId} -- TODO: for share`,
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(ActorScopeError);
  });

  it('is not satisfied by a lock clause inside a block comment', async () => {
    const actor = testActor();
    await seedGrant(actor.userId);

    const thrown = await withTransaction(
      actor,
      async (tx) =>
        tx.authorize`select 1 from listing_grants_fixture where user_id = ${tx.actorId} /* for share */`,
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(ActorScopeError);
  });

  it('is not satisfied by a lock clause inside a quoted literal', async () => {
    const actor = testActor();
    await seedGrant(actor.userId);

    const thrown = await withTransaction(
      actor,
      async (tx) =>
        tx.authorize`
          select 1 from listing_grants_fixture
          where user_id = ${tx.actorId} and listing_id <> 'for share'
        `,
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(ActorScopeError);
  });

  it('still accepts a real lock clause that follows a comment', async () => {
    // The other failure direction. A check that stripped too much, or gave up
    // on any statement containing a comment, would refuse legitimate SQL, and
    // this project has already corrected one guard into refusing the thing it
    // existed to allow.
    const actor = testActor();
    await seedGrant(actor.userId);

    const rows = await withTransaction(
      actor,
      async (tx) =>
        tx.authorize<{ ok: number }[]>`
          select 1 as ok from listing_grants_fixture
          -- the grant must not move under us
          where user_id = ${tx.actorId} and listing_id = 'bike'
          for share
        `,
      { db },
    );

    expect(rows).toHaveLength(1);
  });

  it('accepts a locking authorize read and returns its rows', async () => {
    const actor = testActor();
    await seedGrant(actor.userId);

    const rows = await withTransaction(
      actor,
      async (tx) =>
        tx.authorize<{ ok: number }[]>`
          select 1 as ok from listing_grants_fixture
          where user_id = ${tx.actorId} and listing_id = 'bike'
          for share
        `,
      { db },
    );

    expect(rows).toHaveLength(1);
  });

  /**
   * THE CRITERION, and the only test in this file that needs two connections
   * racing.
   *
   * Without the lock, this is a textbook write skew: T1 checks, T2 revokes and
   * COMMITS, T1 writes, both succeed, and the write landed on a permission that
   * was gone before it happened. The assertion that discriminates is
   * `revokeSettled` being false at the moment T1 writes — not the final row
   * counts, which look identical either way.
   */
  it('a concurrent revoke cannot commit between the check and the write', async () => {
    const actor = testActor();
    await seedListing(actor.userId);
    await seedGrant(actor.userId);

    const checked = deferred();
    let revokeSettled = false;
    let seenAtWriteTime = true;

    const guarded = withTransaction(
      actor,
      async (tx) => {
        const allowed = await tx.authorize<{ ok: number }[]>`
          select 1 as ok from listing_grants_fixture
          where user_id = ${tx.actorId} and listing_id = 'bike'
          for share
        `;
        if (allowed.length === 0) throw new Error('not permitted');

        checked.resolve();
        // Wait until the revoke has REACHED the database, one way or the
        // other: parked on the lock, or already finished. A timer would make
        // this a guess; `pg_blocking_pids` makes the blocked case a fact, and
        // admitting the settled case too is what turns a broken guarantee into
        // a fast, explicit failure on the assertion below rather than a
        // ten-second timeout that says less.
        await waitFor(async () => revokeSettled || (await blockedBackendCount(probe)) > 0, {
          timeoutMs: 5_000,
          what: 'the concurrent revoke to reach the database',
        });

        seenAtWriteTime = !revokeSettled;

        await tx`
          update listings_fixture set title = 'Renamed'
          where id = 'bike' and owner = ${tx.actorId}
        `;
      },
      { db },
    );

    await checked.promise;
    const revoke = observer`
      delete from listing_grants_fixture where user_id = ${actor.userId}
    `.then(
      () => {
        revokeSettled = true;
      },
      () => {
        revokeSettled = true;
      },
    );

    await guarded;
    await revoke;

    // The guarantee: at the instant the guarded write was issued, the revoke
    // had NOT committed. Delete the `for share` from the read above and this
    // is the line that fails.
    expect(seenAtWriteTime).toBe(true);
    expect(await titleOf(observer)).toBe('Renamed');
    expect(await grantCount(observer)).toBe(0);
  });

  it('a revoke that committed first is seen by the check, which then refuses', async () => {
    // The other direction. A lock that made stale permissions survive would be
    // a worse bug than the one above, and this is cheap insurance against a
    // fix that swings that way.
    const actor = testActor();
    await seedListing(actor.userId);
    await seedGrant(actor.userId);
    await observer`delete from listing_grants_fixture where user_id = ${actor.userId}`;

    await expect(
      withTransaction(
        actor,
        async (tx) => {
          const allowed = await tx.authorize<{ ok: number }[]>`
            select 1 as ok from listing_grants_fixture
            where user_id = ${tx.actorId} and listing_id = 'bike'
            for share
          `;
          if (allowed.length === 0) throw new Error('not permitted');
          await tx`
            update listings_fixture set title = 'Renamed'
            where id = 'bike' and owner = ${tx.actorId}
          `;
        },
        { db },
      ),
    ).rejects.toThrow('not permitted');

    expect(await titleOf(observer)).toBe('Road bike');
  });
});

/* ==========================================================================
 * DECISION 6. The handle dies with its transaction.
 * ========================================================================== */
describe('an actor-bound handle is unusable after the transaction ends', () => {
  it('rejects a scoped query on a handle captured from a committed transaction', async () => {
    const actor = testActor();
    let escaped!: ActorTx;

    await withTransaction(
      actor,
      async (tx) => {
        escaped = tx;
      },
      { db },
    );

    await expect(
      escaped`select * from listings_fixture where owner = ${escaped.actorId}`,
    ).rejects.toBeInstanceOf(ClosedHandleError);
  });

  it('rejects after a rolled-back transaction too', async () => {
    const actor = testActor();
    let escaped!: ActorTx;

    await withTransaction(
      actor,
      async (tx) => {
        escaped = tx;
        throw new Error('rollback');
      },
      { db },
    ).catch(() => undefined);

    await expect(
      escaped`select * from listings_fixture where owner = ${escaped.actorId}`,
    ).rejects.toBeInstanceOf(ClosedHandleError);
  });

  it(`rejects an escaped ${UNSCOPED_TOKEN}() handle as well`, async () => {
    // The door out must not also be a door past DECISION 6.
    const actor = testActor();
    let escaped!: Tx;

    await withTransaction(
      actor,
      async (tx) => {
        escaped = tx.unscoped('escaping on purpose');
      },
      { db },
    );

    await expect(escaped`select 1`).rejects.toBeInstanceOf(ClosedHandleError);
  });

  it(`refuses to mint a new ${UNSCOPED_TOKEN}() handle after the transaction ends`, async () => {
    const actor = testActor();
    let escaped!: ActorTx;

    await withTransaction(
      actor,
      async (tx) => {
        escaped = tx;
      },
      { db },
    );

    expect(() => escaped.unscoped('too late')).toThrow(ClosedHandleError);
  });

  it('reports the closed handle rather than the scope rule', async () => {
    // Ordering. A correctly-scoped query on a dead handle must not be reported
    // as a scope mistake, which would send the next reader hunting for a
    // missing filter that is right there.
    const actor = testActor();
    let escaped!: ActorTx;

    await withTransaction(
      actor,
      async (tx) => {
        escaped = tx;
      },
      { db },
    );

    const thrown = await escaped`select 1 where ${escaped.actorId}::text is not null`.catch(
      (error: unknown) => error,
    );

    expect(thrown).toBeInstanceOf(ClosedHandleError);
    expect(thrown).not.toBeInstanceOf(ActorScopeError);
  });

  it('leaves the connection usable for the next transaction', async () => {
    const actor = testActor();
    let escaped!: ActorTx;

    await withTransaction(actor, async (tx) => {
      escaped = tx;
    }, { db });
    await escaped`select 1 where ${escaped.actorId}::text is not null`.catch(() => undefined);

    await seedListing(actor.userId);
    const rows = await withTransaction(
      actor,
      async (tx) => tx<{ id: string }[]>`select id from listings_fixture where owner = ${tx.actorId}`,
      { db },
    );

    expect(rows).toHaveLength(1);
  });
});

/* ==========================================================================
 * DECISION 7. The Slice B hazard, in its new disguise.
 * ========================================================================== */
describe('the actor is never session-level state on the connection', () => {
  /**
   * READ `tests/helpers/recording-client.ts` BEFORE CHANGING THIS.
   *
   * The first version of this test looked for the leftover setting in
   * `pg_settings` afterwards. PostgreSQL does not list custom GUCs there at
   * all, so it returned an empty result either way and a mutation that
   * deliberately carried the actor in `set_config(..., false)` sailed through
   * it. The probe could not fail, which per the delegation protocol means it
   * was not evidence.
   *
   * What is asserted now is the SQL the seam actually sent. Every way of
   * installing state on a connection is a statement, and a statement is
   * observable.
   */
  it('issues no session-level statement while serving a transaction', async () => {
    const actor = testActor();
    await seedListing(actor.userId);
    const recorder = recordingClient(db);

    await withTransaction(
      actor,
      async (tx) => tx`select id from listings_fixture where owner = ${tx.actorId}`,
      { db: recorder.db },
    );

    // Non-empty, or the recorder is not wired to anything and the line below
    // proves nothing.
    expect(recorder.statements).not.toEqual([]);
    expect(sessionLevelStatements(recorder.statements)).toEqual([]);
  });

  it('leaves no value behind for any setting it did touch', async () => {
    // The complement, and it needs no guess about the name: whatever the
    // implementation set, transaction-locally or otherwise, is read back by
    // name from the recorded SQL after the transaction has committed, on the
    // same client.
    const actor = testActor();
    await seedListing(actor.userId);
    const recorder = recordingClient(db);

    await withTransaction(
      actor,
      async (tx) => tx`select id from listings_fixture where owner = ${tx.actorId}`,
      { db: recorder.db },
    );

    for (const name of settingsTouched(recorder.statements)) {
      const [row] = await db<{ value: string | null }[]>`
        select current_setting(${name}, true) as value
      `;
      expect(row?.value ?? '', name).toBe('');
    }
  });

  /**
   * B9-R1, N1. DECISION 1 says the actor is a runtime argument and never
   * ambient — no AsyncLocalStorage, no module-level `currentActor` — and
   * nothing pinned it. A module-level `let currentActor` passes every other
   * test in this file and reproduces the warm-instance cross-request leak
   * criterion 5 exists to prevent, because the second request overwrites it
   * while the first is still in flight.
   *
   * So: two overlapping transactions, two actors, two clients. A's handle must
   * still bind A's id after B has been and gone.
   */
  it('binds its own actor even while another transaction is open with a different one', async () => {
    const a = testActor();
    const b = testActor();
    const opened = deferred();
    const interleaved = deferred();

    const first = withTransaction(
      a,
      async (tx) => {
        opened.resolve();
        await interleaved.promise;
        const [row] = await tx<{ me: string }[]>`select ${tx.actorId}::text as me`;
        return row!.me;
      },
      { db },
    );

    await opened.promise;

    const secondSaw = await withTransaction(
      b,
      async (tx) => {
        const [row] = await tx<{ me: string }[]>`select ${tx.actorId}::text as me`;
        return row!.me;
      },
      { db: observer },
    );

    interleaved.resolve();
    const firstSaw = await first;

    expect(secondSaw).toBe(b.userId);
    // The line an ambient actor fails: by now `currentActor` would be B.
    expect(firstSaw).toBe(a.userId);
  });

  it('does not let one transaction see the previous transaction actor', async () => {
    // The warm-instance shape, reproduced: two transactions, one connection,
    // nothing in between. Whatever the first installed must be gone, and the
    // names come from what the first was recorded doing rather than from a
    // list this test would have to keep up to date.
    const first = testActor();
    const second = testActor();
    const recorder = recordingClient(db);

    await withTransaction(first, async (tx) => tx`select ${tx.actorId}::text as me`, {
      db: recorder.db,
    });

    const leaked = await withTransaction(
      second,
      async (tx) => {
        const sql = tx.unscoped('reading server settings for the leak probe');
        const names = settingsTouched(recorder.statements);
        const found: string[] = [];
        for (const name of names) {
          const [row] = await sql<{ value: string | null }[]>`
            select current_setting(${name}, true) as value
          `;
          if ((row?.value ?? '').includes(first.userId)) found.push(name);
        }
        return found;
      },
      { db: recorder.db },
    );

    expect(leaked).toEqual([]);
  });
});

/* ==========================================================================
 * WHAT THIS SEAM DOES NOT GUARANTEE.
 *
 * Modelled on 'what a mapper does not protect against' in
 * `db-transaction.test.ts`, and here for the same reason: this header has
 * already carried one claim larger than the code beneath it, and prose is not
 * what stops that. These tests assert the GAP, so that closing it is a visible
 * change to this file rather than a quiet upgrade nobody records.
 * ========================================================================== */
describe('what this seam does not guarantee', () => {
  /** L2. `authorize` is available; nothing makes a service use it. */
  it('accepts a permission read on the ordinary tag, so TD-12 is the caller to honour', async () => {
    const actor = testActor();
    await seedListing(actor.userId);
    await seedGrant(actor.userId);

    // The same service as the TD-12 test, with the one word changed. Every
    // check in this file passes it.
    const result = await withTransaction(
      actor,
      async (tx) => {
        const allowed = await tx<{ ok: number }[]>`
          select 1 as ok from listing_grants_fixture
          where user_id = ${tx.actorId} and listing_id = 'bike'
        `;
        if (allowed.length === 0) throw new Error('not permitted');
        await tx`
          update listings_fixture set title = 'Renamed'
          where id = 'bike' and owner = ${tx.actorId}
        `;
        return 'committed';
      },
      { db },
    );

    expect(result).toBe('committed');
    // Nothing above took a lock, so a concurrent revoke between the read and
    // the write would have committed. The seam did not object. If a future
    // decision makes `authorize` mandatory, THIS is the test that fails, and
    // that is the intent.
  });

  /** L1. The marker proves binding, not constraint. */
  it('accepts a query that binds the actor without filtering on it', async () => {
    const mine = testActor();
    const theirs = testActor();
    await seedListing(mine.userId, 'bike');
    await seedListing(theirs.userId, 'lamp');

    const rows = await withTransaction(
      mine,
      async (tx) =>
        tx<{ me: string; id: string }[]>`
          select ${tx.actorId}::text as me, id from listings_fixture order by id
        `,
      { db },
    );

    // Two rows, one of them someone else's, through a fully scoped handle.
    expect(rows).toHaveLength(2);
    expect(rows.map((row) => row.id)).toEqual(['bike', 'lamp']);
  });
});
