/**
 * ============================================================================
 * CONTRACT FOR THE IMPLEMENTER — module `@/server/db` (src/server/db/index.ts)
 * ============================================================================
 *
 * This is the first database-dependent slice of INV-17: a connection, a
 * transaction helper, and the mapping from SQLSTATE 23505 to a refusal.
 * Services land later and will take a transaction handle the way the Java
 * services take a `Connection` from `Database.executeTransaction`.
 *
 * ---------------------------------------------------------------------------
 * THE SURFACE
 * ---------------------------------------------------------------------------
 *
 *   import type { Sql, TransactionSql } from 'postgres';
 *   import type { Refusal } from '@/domain/errors';
 *
 *   export type Db = Sql;
 *   export type Tx = TransactionSql;
 *
 *   export const UNIQUE_VIOLATION = '23505';
 *
 *   export function createDb(connectionString: string): Db;
 *   export function getDb(): Db;
 *   export function closeDb(): Promise<void>;
 *
 *   export interface UniqueViolation {
 *     readonly constraint: string | undefined;
 *     readonly table: string | undefined;
 *   }
 *
 *   export function isUniqueViolation(error: unknown): boolean;
 *   export function describeUniqueViolation(error: unknown): UniqueViolation | undefined;
 *   export function isRetryable(error: unknown): boolean;
 *
 *   export interface TransactionOptions {
 *     readonly db?: Db;
 *     readonly onUniqueViolation?: (violation: UniqueViolation) => Refusal | undefined;
 *   }
 *
 *   export function withTransaction<T>(
 *     run: (tx: Tx) => Promise<T>,
 *     options?: TransactionOptions,
 *   ): Promise<T>;
 *
 * ---------------------------------------------------------------------------
 * DECISION 1 — ONE CLIENT, CREATED LAZILY, READ FROM THE ENVIRONMENT AT FIRST USE
 * ---------------------------------------------------------------------------
 *
 * `getDb()` memoizes. `getDb() === getDb()`. postgres.js is already a pool, so
 * a client per call would mean a pool per call, and on Supabase's transaction
 * pooler that exhausts the connection budget in a way that only shows up under
 * load.
 *
 * The connection string is read on the FIRST CALL, not at module load. Next
 * evaluates modules during `next build`, where DATABASE_URL is routinely
 * absent, and a module-load read turns a missing variable into a build failure
 * far away from the cause. Importing `@/server/db` with no environment at all
 * must succeed; calling `getDb()` with no DATABASE_URL must throw an ordinary
 * Error naming the variable. That error is NOT a refusal: a missing deployment
 * variable is an operator fault, never a message for a user. (Same reasoning as
 * DECISION 2 in `@/domain/errors`.)
 *
 * `createDb(url)` is the explicit-URL factory. A fresh client every call. It
 * exists for two callers that must not touch the singleton: this test suite,
 * and `scripts/db-check.ts`.
 *
 * `closeDb()` ends the singleton and forgets it, so the next `getDb()` builds a
 * new one. Idempotent: calling it with nothing open resolves quietly. Without
 * this the suite hangs on open handles at the end of a run.
 *
 * ---------------------------------------------------------------------------
 * DECISION 2 — `prepare: false`, ALWAYS, IN BOTH FACTORIES
 * ---------------------------------------------------------------------------
 *
 * Supabase's pooler in transaction mode hands a different backend to each
 * transaction, so a named prepared statement parsed on one connection is not
 * there when the next statement tries to bind it. The symptom is
 * `prepared statement "s1" does not exist`, under load, in production only.
 * Locally, against a direct connection, prepared statements work perfectly,
 * which is the worst possible failure shape: the thing that would catch it is
 * exactly the thing that does not happen in development.
 *
 * So it is pinned twice, in `db-connection.test.ts`: once on the option, and
 * once behaviourally, by proving the server has no prepared statements after
 * the client has run parameterised queries. The behavioural pin is the one that
 * survives postgres.js renaming an option.
 *
 * ---------------------------------------------------------------------------
 * DECISION 3 — THE HANDLE IS THE UNIT OF WORK
 * ---------------------------------------------------------------------------
 *
 * `withTransaction(run)` opens ONE transaction on ONE connection, passes that
 * handle to `run`, and commits once if `run` resolves or rolls back once if it
 * throws. Every repository in an operation receives the SAME `Tx`. This is the
 * direct port of `Database.executeTransaction`, and it is why repositories take
 * a handle rather than reaching for a connection themselves.
 *
 *   - resolve  -> COMMIT, and `withTransaction` returns what `run` returned.
 *   - throw    -> ROLLBACK, and the error leaves `withTransaction`.
 *
 * `options.db` selects the client; omitted, it uses `getDb()`. Tests always
 * pass it explicitly, because a test that falls through to `getDb()` is a test
 * that could reach the application database.
 *
 * ---------------------------------------------------------------------------
 * DECISION 4 — A REFUSAL THROWN INSIDE COMES OUT UNCHANGED
 * ---------------------------------------------------------------------------
 *
 * Domain code says no by throwing a refusal from the catalogue in
 * `@/domain/errors`. `withTransaction` must roll back and rethrow THE SAME
 * OBJECT: identity-equal, still passing `isAuthoredRefusal`, same `code`, same
 * `message`. Not wrapped, not re-created, not converted to a payload.
 *
 * Why identity and not just shape: `isAuthoredRefusal` is WeakSet membership
 * (see `@/domain/errors`), and it is the unforgeable test of "this module's
 * catalogue wrote this". A helper that rebuilt the refusal, even perfectly,
 * would produce a value that fails `isAuthoredRefusal` and would therefore be
 * treated downstream as attacker-influenced input. Pinned with `toBe`.
 *
 * The check on the way out is ordered: an authored refusal is rethrown before
 * any error mapping is considered.
 *
 * ---------------------------------------------------------------------------
 * DECISION 5 — 23505 IS MAPPED BY THE CALLER, NOT BY THE HELPER
 * ---------------------------------------------------------------------------
 *
 * `withTransaction` does NOT invent a refusal for a unique violation on its
 * own. It takes `onUniqueViolation`, calls it only for SQLSTATE 23505, hands it
 * `{constraint, table}`, and throws whatever it returns.
 *
 * Why not map automatically: FR-1712 requires a refusal to state what is wrong
 * and what to do next, USING THE REAL VALUES INVOLVED. A generic helper knows
 * the constraint name and nothing else, so the only message it could author is
 * "that already exists", which is the sort of text FR-1712 exists to prevent.
 * Only the caller knows that this particular index means "one pending offer per
 * buyer per listing" and can reach for
 * `refusals.duplicatePendingOffer(existingAmountCents)`.
 *
 * Consequences, both deliberate:
 *   - With no `onUniqueViolation`, a 23505 propagates as the driver error. The
 *     helper does not pretend to have handled it.
 *   - The mapper is handed the constraint NAME so it can discriminate, and the
 *     refusal it returns must not contain it. Pinned in
 *     `db-unique-violation.test.ts`.
 *
 * THE MAPPER MAY DECLINE, by returning `undefined`. A declined violation
 * propagates as the original driver error.
 *
 *   Why it needs to: a mapper is written for the indexes its author knew about,
 *   and TD-02a's marketplace operations are all multi-write, so a transaction
 *   will routinely raise a 23505 from a table the mapper has never heard of.
 *   Without a way to say "not mine", the offers mapper answers for that table
 *   too, and `withTransaction` accepts the answer because `isAuthoredRefusal`
 *   passes. The user then reads an invented amount about an offer that does not
 *   exist. FR-1712 requires a refusal to state what is ACTUALLY wrong using the
 *   REAL values, so a mapper that cannot decline cannot be correct.
 *
 * `undefined` means "not mine". Returning any OTHER non-refusal is a programmer
 * error, and `withTransaction` throws a TypeError rather than throwing the
 * value, for the same reason `@/domain/errors` throws on a non-integer: it must
 * fail loudly at the boundary and must never reach a user as a message.
 *
 * ---------------------------------------------------------------------------
 * WHAT THE MAPPER DOES AND DOES NOT GUARANTEE
 * ---------------------------------------------------------------------------
 *
 * This document used to claim that if a mapper is supplied, nothing
 * driver-shaped escapes. That is false, and it contradicted DECISION 6 three
 * paragraphs further down. A mapper handles 23505 and NOTHING else, so every
 * other database error leaves `withTransaction` exactly as postgres.js built
 * it, carrying the failing SQL, the offending row values, schema and table
 * names, PostgreSQL's own C source file and line, and a stack through
 * node_modules.
 *
 * That is the right behaviour, and 'what a mapper does not protect against'
 * below pins it so the claim cannot drift back. It is also a standing
 * instruction for the service layer: a driver error reaching a user is a
 * rendering bug at the boundary above this one, not something this module
 * quietly prevents.
 *
 * ---------------------------------------------------------------------------
 * DECISION 6 — ONLY 23505 IS MAPPED
 * ---------------------------------------------------------------------------
 *
 * `isUniqueViolation` is true for SQLSTATE 23505 and nothing else. A not-null
 * violation (23502) or an undefined table (42P01) is a FAULT: a schema drift, a
 * bad migration, a bug. Turning them into polite refusals would show a user
 * "that already exists" while the real message, the one an operator needs,
 * disappears. They propagate untouched, and `onUniqueViolation` is not called
 * for them.
 *
 * A SERIALIZATION FAILURE IS NOT IN THAT LIST, and used to be. 40001 and 40P01
 * are retryable transients produced by correct concurrent code, not faults.
 * Deadlock in particular needs no special isolation level and happens at the
 * READ COMMITTED these tests assume. Calling them bugs would tell the INV-07
 * implementer not to retry, which is the one thing that actually fixes them.
 * `isRetryable` names the category; `db-retryable.test.ts` provokes both codes
 * against the real server and explains why this slice deliberately ships the
 * predicate without a retry policy.
 *
 * ---------------------------------------------------------------------------
 * WHAT IS NOT IN THIS SLICE
 * ---------------------------------------------------------------------------
 *
 * No savepoints, no nested `withTransaction`, no isolation-level argument, and
 * no retry policy. Each needs a caller to justify its shape, and none has one
 * yet. The default isolation level (READ COMMITTED) is what these tests assume.
 *
 * `isRetryable` ships WITHOUT the retry it is named for, on purpose: the
 * predicate is a fact about PostgreSQL and can be pinned today, while a retry
 * needs an attempt count, a backoff, a rule about which operations are safe to
 * replay and an answer for when the attempts run out. See `db-retryable.ts`.
 * ============================================================================
 */

import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { REFUSAL_CODES, isAuthoredRefusal, isRefusal, refusals } from '@/domain/errors';
import {
  UNIQUE_VIOLATION,
  closeDb,
  createDb,
  withTransaction,
  type Db,
  type Tx,
} from '@/server/db';

import { testActor } from '../helpers/actor';
import { TRUNCATE_FIXTURES, deferred } from '../helpers/test-database';
import { testDatabaseUrl } from '../setup/integration-setup';

const REASON =
  'the transaction primitive under test (commit, rollback, handle identity); offers_fixture has no owner, so no actor-scoped table is involved';

let db: Db;
/** A second, independent client. Reads here are outside the transaction. */
let observer: Db;

beforeAll(() => {
  const url = testDatabaseUrl();
  db = createDb(url);
  observer = createDb(url);
});

afterAll(async () => {
  await db.end();
  await observer.end();
  // The singleton may have been created by a test that omitted `options.db`.
  await closeDb();
});

beforeEach(async () => {
  await db.unsafe(TRUNCATE_FIXTURES);
});

afterEach(async () => {
  await db.unsafe(TRUNCATE_FIXTURES);
});

async function countRows(client: Db): Promise<number> {
  const rows = await client<{ n: number }[]>`select count(*)::int as n from offers_fixture`;
  return rows[0]!.n;
}

/**
 * Stands in for a repository: it takes the handle it is given rather than
 * reaching for a connection. This is the shape every INV-07 repository will
 * have.
 */
async function insertOffer(
  tx: Tx,
  buyer: string,
  listing: string,
  status = 'PENDING',
  amountCents = 4_000,
): Promise<void> {
  await tx`
    insert into offers_fixture (buyer, listing, status, amount_cents)
    values (${buyer}, ${listing}, ${status}, ${amountCents})
  `;
}

describe('withTransaction: commit', () => {
  it('commits when the callback resolves, and the writes are visible afterwards', async () => {
    await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertOffer(tx, 'ana', 'bike');
        await insertOffer(tx, 'ben', 'bike');
      },
      { db },
    );

    expect(await countRows(observer)).toBe(2);
  });

  it('returns the value the callback produced to the caller', async () => {
    const result = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertOffer(tx, 'ana', 'bike');
        const rows = await tx<{ id: string }[]>`select id::text from offers_fixture`;
        return { inserted: rows.length, label: 'ok' as const };
      },
      { db },
    );

    expect(result).toEqual({ inserted: 1, label: 'ok' });
  });

  it('returns the identical object the callback returned, not a copy', async () => {
    const sentinel = { marker: Symbol('sentinel') };

    const returned = await withTransaction(testActor(), async () => sentinel, {
      db,
    });

    expect(returned).toBe(sentinel);
  });

  /**
   * An ARRAY, specifically.
   *
   * The test above uses an object, which is the one value type that cannot
   * detect the bug the implementation's boxing exists to prevent. postgres.js
   * inspects what the transaction callback returns and, if it is an array,
   * resolves it with `Promise.all`, handing back a NEW array:
   *
   *     const x = fn(sql)
   *     Promise.resolve(Array.isArray(x) ? Promise.all(x) : x).then(resolve, reject)
   *
   * A repository returning `await tx\`select ...\`` returns an array, so this
   * is the ordinary return shape for the services in INV-07, not a curiosity.
   *
   * This pins the BEHAVIOUR and says nothing about how it is achieved. See the
   * note on the boxing in the report; whether the implementation keeps the
   * wrapper or drops it, the array that goes in is the array that comes out.
   */
  it('returns the identical array the callback returned, not a Promise.all copy', async () => {
    const rows = [{ id: 1 }, { id: 2 }, { id: 3 }];

    const returned = await withTransaction(testActor(), async () => rows, {
      db,
    });

    expect(returned).toBe(rows);
    expect(returned).toHaveLength(3);
  });

  it('returns a query result array by identity', async () => {
    // The realistic version: what a repository actually hands back.
    let inner: unknown;

    const returned = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertOffer(tx, 'ana', 'bike');
        inner = await tx`select buyer from offers_fixture`;
        return inner;
      },
      { db },
    );

    expect(returned).toBe(inner);
  });

  it('passes one handle to every repository in the operation, committing once', async () => {
    // The Java shape: executeTransaction opens one connection and hands it to
    // each repository. Two "repositories" here, one commit.
    const handles: Tx[] = [];

    await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        handles.push(tx);
        await insertOffer(tx, 'ana', 'bike');
        handles.push(tx);
        await insertOffer(tx, 'ana', 'lamp');
      },
      { db },
    );

    expect(handles[0]).toBe(handles[1]);
    expect(await countRows(observer)).toBe(2);
  });
});

describe('withTransaction: rollback', () => {
  it('rolls back every write when the callback throws', async () => {
    const boom = new Error('deliberate failure after four writes');

    await expect(
      withTransaction(
        testActor(),
        async (scope) => {
          const tx = scope.unscoped(REASON);
          await insertOffer(tx, 'ana', 'bike');
          await insertOffer(tx, 'ben', 'bike');
          await insertOffer(tx, 'cal', 'lamp');
          await insertOffer(tx, 'dee', 'lamp');
          throw boom;
        },
        { db },
      ),
    ).rejects.toBe(boom);

    // Four writes, so a partial application would be visible as 1, 2 or 3
    // rather than hiding behind an all-or-nothing count of a single row.
    expect(await countRows(observer)).toBe(0);
  });

  it('rolls back writes made before an error raised by the database itself', async () => {
    await expect(
      withTransaction(
        testActor(),
        async (scope) => {
          const tx = scope.unscoped(REASON);
          await insertOffer(tx, 'ana', 'bike');
          await insertOffer(tx, 'ben', 'bike');
          // 23502: amount_cents is NOT NULL.
          await tx`
            insert into offers_fixture (buyer, listing, status, amount_cents)
            values ('cal', 'lamp', 'PENDING', null)
          `;
        },
        { db },
      ),
    ).rejects.toThrow();

    expect(await countRows(observer)).toBe(0);
  });

  it('leaves the connection usable for the next transaction after a rollback', async () => {
    await expect(
      withTransaction(
        testActor(),
        async (scope) => {
          const tx = scope.unscoped(REASON);
          await insertOffer(tx, 'ana', 'bike');
          throw new Error('rollback');
        },
        { db },
      ),
    ).rejects.toThrow();

    await withTransaction(
      testActor(),
      async (scope) => insertOffer(scope.unscoped(REASON), 'ben', 'bike'),
      { db },
    );

    expect(await countRows(observer)).toBe(1);
  });
});

describe('withTransaction: the handle passed in is the one actually used', () => {
  it('shows a write to a read on the same handle, and hides it from another connection until commit', async () => {
    const inserted = deferred();
    const release = deferred();

    const transaction = withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertOffer(tx, 'ana', 'bike');
        const [row] = await tx<{ n: number }[]>`select count(*)::int as n from offers_fixture`;
        inserted.resolve();
        await release.promise;
        return row!.n;
      },
      { db },
    );

    await inserted.promise;

    // Same transaction: the write is there.
    // Separate connection, before commit: under READ COMMITTED it is not.
    const seenFromOutside = await countRows(observer);

    release.resolve();
    const seenFromInside = await transaction;

    expect(seenFromInside).toBe(1);
    expect(seenFromOutside).toBe(0);
    expect(await countRows(observer)).toBe(1);
  });

  it('hides a rolled-back write from the other connection permanently', async () => {
    const inserted = deferred();
    const release = deferred();

    const transaction = withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertOffer(tx, 'ana', 'bike');
        inserted.resolve();
        await release.promise;
        throw new Error('rollback');
      },
      { db },
    );

    await inserted.promise;
    const duringTransaction = await countRows(observer);
    release.resolve();

    await expect(transaction).rejects.toThrow('rollback');

    expect(duringTransaction).toBe(0);
    expect(await countRows(observer)).toBe(0);
  });
});

describe('withTransaction: a refusal thrown inside reaches the caller unchanged', () => {
  it('rethrows the identical refusal object', async () => {
    const refusal = refusals.offerOnUnavailableListing();

    const thrown = await withTransaction(
      testActor(),
      async () => {
        throw refusal;
      },
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBe(refusal);
  });

  it('keeps the refusal authored, so isAuthoredRefusal still holds', async () => {
    const thrown = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertOffer(tx, 'ana', 'bike');
        throw refusals.duplicatePendingOffer(4_000);
      },
      { db },
    ).catch((error: unknown) => error);

    expect(isAuthoredRefusal(thrown)).toBe(true);
    expect(isRefusal(thrown)).toBe(true);
  });

  it('preserves the code and the message verbatim', async () => {
    const refusal = refusals.duplicatePendingOffer(4_000);

    const thrown = await withTransaction(
      testActor(),
      async () => {
        throw refusal;
      },
      { db },
    ).catch((error: unknown) => error);

    expect((thrown as typeof refusal).code).toBe('INVALID_STATE');
    expect((thrown as typeof refusal).message).toBe(refusal.message);
    expect((thrown as typeof refusal).message).toContain('S$40.00');
  });

  it('still rolls back the writes the refusal interrupted', async () => {
    await expect(
      withTransaction(
        testActor(),
        async (scope) => {
          const tx = scope.unscoped(REASON);
          await insertOffer(tx, 'ana', 'bike');
          await insertOffer(tx, 'ben', 'bike');
          throw refusals.offerOnUnavailableListing();
        },
        { db },
      ),
    ).rejects.toSatisfy(isAuthoredRefusal);

    expect(await countRows(observer)).toBe(0);
  });

  /**
   * RENAMED. This was called an "ordering pin" and claimed to prove that an
   * authored refusal leaves before mapping is considered. It proves no such
   * thing, and the reviewer demonstrated it: delete
   * `if (isAuthoredRefusal(error)) throw error;` from `withTransaction` and
   * this still passes, because a refusal's `code` is one of the six domain
   * codes and never `'23505'`, so `describeUniqueViolation` returns undefined
   * and the fallthrough rethrows the refusal anyway.
   *
   * The ordering is UNOBSERVABLE through the public catalogue, and the test
   * below pins the invariant that makes it so. The early return stays as
   * intentional defence, but it is defence, not something this suite verifies.
   */
  it('does not re-map a refusal when a mapper is configured', async () => {
    const refusal = refusals.offerOnUnavailableListing();
    const onUniqueViolation = vi.fn(() => refusals.duplicatePendingOffer(1));

    const thrown = await withTransaction(
      testActor(),
      async () => {
        throw refusal;
      },
      { db, onUniqueViolation },
    ).catch((error: unknown) => error);

    expect(thrown).toBe(refusal);
    // The part that is worth asserting: the mapper was never consulted.
    expect(onUniqueViolation).not.toHaveBeenCalled();
  });
});

describe('why the refusal check can be ordered first without being observable', () => {
  it('has no refusal code that could collide with a SQLSTATE', () => {
    // This is the invariant that makes the early `isAuthoredRefusal` return in
    // `withTransaction` unobservable: for a refusal to take a different path
    // depending on that check, its `code` would have to be '23505'.
    //
    // Pinned rather than assumed. The day someone adds a SQLSTATE-shaped entry
    // to REFUSAL_CODES, the ordering starts to matter and this fires here,
    // where the reasoning is written down, instead of silently in production.
    for (const code of REFUSAL_CODES) {
      expect(code).not.toMatch(/^[0-9A-Z]{5}$/);
    }

    expect(REFUSAL_CODES).not.toContain(UNIQUE_VIOLATION);
  });
});

/**
 * The most likely repository bug, and the reason it is worth a test.
 *
 * A repository wraps a write in try/catch, swallows the error, and returns
 * normally. The caller sees success. PostgreSQL does not: once a statement
 * fails, the transaction is aborted and every later statement answers 25P02,
 * so the COMMIT silently becomes a ROLLBACK and nothing persists.
 *
 * That `withTransaction` surfaces this rather than reporting a phantom success
 * is load-bearing and NOT obvious: it works because postgres.js records the
 * failed query as `uncaughtError` and rethrows it at commit time. A
 * reimplementation that lost that behaviour would compile, pass every other
 * test in this file, and lose writes in production.
 */
describe('withTransaction: a callback that swallows a failed write', () => {
  it('does not report success when the transaction was already aborted', async () => {
    const thrown = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertOffer(tx, 'ana', 'bike');
        try {
          // 23502: amount_cents is NOT NULL.
          await tx`
            insert into offers_fixture (buyer, listing, status, amount_cents)
            values ('ben', 'lamp', 'PENDING', null)
          `;
        } catch {
          // The bug being modelled: a repository deciding this is survivable.
        }
        return 'committed';
      },
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).not.toBe('committed');
    expect((thrown as { code?: string }).code).toBe('23502');
  });

  it('persists nothing, so the caller and the database agree', async () => {
    await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertOffer(tx, 'ana', 'bike');
        try {
          await tx`
            insert into offers_fixture (buyer, listing, status, amount_cents)
            values ('ben', 'lamp', 'PENDING', null)
          `;
        } catch {
          /* swallowed */
        }
        return 'committed';
      },
      { db },
    ).catch(() => undefined);

    expect(await countRows(observer)).toBe(0);
  });
});

/**
 * B4, second part. The contract used to claim that supplying a mapper meant
 * nothing driver-shaped escaped. This pins what is actually true, so the claim
 * cannot drift back in.
 */
describe('withTransaction: what a mapper does not protect against', () => {
  it('lets a non-23505 driver error out with its internals intact', async () => {
    const onUniqueViolation = vi.fn(() => refusals.duplicatePendingOffer(4_000));

    const thrown = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`
          insert into offers_fixture (buyer, listing, status, amount_cents)
          values ('ana', 'bike', 'PENDING', null)
        `;
      },
      { db, onUniqueViolation },
    ).catch((error: unknown) => error);

    const error = thrown as Error & { table_name?: string; file?: string };

    expect(onUniqueViolation).not.toHaveBeenCalled();
    expect(isRefusal(thrown)).toBe(false);

    // Everything a refusal is forbidden to carry, carried: the column and
    // relation by name, PostgreSQL's own C source file, and a stack frame.
    expect(error.message).toMatch(/amount_cents/);
    expect(error.message).toMatch(/offers_fixture/);
    expect(error.table_name).toBe('offers_fixture');
    expect(typeof error.file).toBe('string');
    expect(error.stack).toMatch(/\n\s*at /);

    // Which makes this a rendering obligation at the layer above: the service
    // boundary must never put a caught database error in front of a user.
    // Nothing in @/server/db makes that safe.
  });
});

describe('withTransaction: non-refusal errors are not converted', () => {
  it('rethrows an ordinary Error as itself', async () => {
    const boom = new TypeError('programmer error');

    const thrown = await withTransaction(
      testActor(),
      async () => {
        throw boom;
      },
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBe(boom);
    expect(isRefusal(thrown)).toBe(false);
  });

  it('rethrows a thrown non-Error value as itself', async () => {
    const thrown = await withTransaction(
      testActor(),
      async () => {
        throw 'a bare string';
      },
      { db },
    ).catch((error: unknown) => error);

    expect(thrown).toBe('a bare string');
  });
});
