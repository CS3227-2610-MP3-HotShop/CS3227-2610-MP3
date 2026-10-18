/**
 * B4. Retryable transients, and the seam to handle them later.
 *
 * ---------------------------------------------------------------------------
 * THE CORRECTION
 * ---------------------------------------------------------------------------
 *
 * DECISION 6 in `db-transaction.test.ts` used to list a serialization failure
 * alongside a not-null violation and an undefined table as a FAULT, "a schema
 * drift, a bad migration, a bug". That is wrong, and wrong in the direction
 * that costs money: it tells the INV-07 implementer that a transient produced
 * by correct concurrent code is a defect to fix rather than an operation to
 * retry.
 *
 *   40001  serialization_failure  — two transactions could not be ordered
 *   40P01  deadlock_detected      — PostgreSQL broke a lock cycle by aborting
 *                                   one of the participants
 *
 * Neither means anything is broken. Both mean "that did not work, do it again",
 * and the correct response is to replay the whole unit of work. Both are
 * reachable here: deadlock needs no special isolation level and happens at
 * READ COMMITTED, which is what every test in this suite assumes, and TD-02a's
 * multi-write marketplace operations are exactly the shape that produces one,
 * because two operations touching the same rows in different orders is the
 * textbook case.
 *
 * ---------------------------------------------------------------------------
 * WHAT THIS SLICE DOES ABOUT IT: NOTHING, DELIBERATELY
 * ---------------------------------------------------------------------------
 *
 * No retry logic here. A retry needs a policy — how many attempts, what
 * backoff, which operations are safe to replay, what a user sees when the
 * attempts run out — and none of that can be designed without a caller. Writing
 * it now would be guessing at four decisions at once.
 *
 * What this slice owes the next one is an honest NAME for the category, so the
 * retry has something to be built on and nobody has to rediscover which
 * SQLSTATEs belong in the set. `isRetryable` is that seam, and it sits beside
 * `isUniqueViolation` as the second thing the module knows how to recognise.
 *
 * Every error below is PROVOKED against the real server. A synthetic
 * `{code: '40001'}` would pin the predicate against this file's idea of what
 * PostgreSQL does, which is the thing most worth not assuming.
 */

import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';

import { isRefusal, refusals } from '@/domain/errors';
import { createDb, isRetryable, isUniqueViolation, withTransaction, type Db } from '@/server/db';

import { testActor } from '../helpers/actor';
import { TRUNCATE_FIXTURES, deferred } from '../helpers/test-database';
import { testDatabaseUrl } from '../setup/integration-setup';

const REASON =
  'provoking real 40001 and 40P01 failures on offers_fixture, which has no owner; no actor-scoped table is involved';

let first: Db;
let second: Db;

beforeAll(() => {
  const url = testDatabaseUrl();
  first = createDb(url);
  second = createDb(url);
});

afterAll(async () => {
  await first.end();
  await second.end();
});

beforeEach(async () => {
  await first.unsafe(TRUNCATE_FIXTURES);
  // Two rows, so the deadlock test has two lock targets to take in opposite
  // order.
  await first`
    insert into offers_fixture (buyer, listing, status, amount_cents)
    values ('ana', 'bike', 'WITHDRAWN', 1000),
           ('ben', 'lamp', 'WITHDRAWN', 2000)
  `;
});

afterEach(async () => {
  await first.unsafe(TRUNCATE_FIXTURES);
});

/**
 * Provokes a genuine 40001.
 *
 * At REPEATABLE READ a transaction works from the snapshot it took at its first
 * statement. If another transaction commits a change to a row it then tries to
 * update, PostgreSQL cannot order the two and aborts this one. The barriers
 * sequence it exactly: snapshot, then the other commit, then the update.
 */
async function provokeSerializationFailure(): Promise<unknown> {
  const snapshotTaken = deferred();
  const otherCommitted = deferred();

  const blocked = withTransaction(
    testActor(),
    async (scope) => {
      const tx = scope.unscoped(REASON);
      await tx`set transaction isolation level repeatable read`;
      // Establishes the snapshot.
      await tx`select amount_cents from offers_fixture where buyer = 'ana'`;
      snapshotTaken.resolve();

      await otherCommitted.promise;
      await tx`update offers_fixture set amount_cents = 9999 where buyer = 'ana'`;
    },
    { db: first },
  ).catch((error: unknown) => error);

  await snapshotTaken.promise;

  await withTransaction(
    testActor(),
    async (scope) => {
      const tx = scope.unscoped(REASON);
      await tx`update offers_fixture set amount_cents = 5555 where buyer = 'ana'`;
    },
    { db: second },
  );

  otherCommitted.resolve();
  return blocked;
}

/**
 * Provokes a genuine 40P01, at the default READ COMMITTED.
 *
 * Two transactions take the same two row locks in opposite order. Each holds
 * what the other wants, PostgreSQL's deadlock detector notices the cycle and
 * aborts one of them. Which one it picks is its choice, so the test asserts
 * "exactly one", never "the first".
 */
async function provokeDeadlock(): Promise<unknown> {
  const anaLocked = deferred();
  const benLocked = deferred();

  const forwards = withTransaction(
    testActor(),
    async (scope) => {
      const tx = scope.unscoped(REASON);
      await tx`update offers_fixture set amount_cents = amount_cents + 1 where buyer = 'ana'`;
      anaLocked.resolve();
      await benLocked.promise;
      await tx`update offers_fixture set amount_cents = amount_cents + 1 where buyer = 'ben'`;
    },
    { db: first },
  ).catch((error: unknown) => error);

  const backwards = withTransaction(
    testActor(),
    async (scope) => {
      const tx = scope.unscoped(REASON);
      await tx`update offers_fixture set amount_cents = amount_cents + 1 where buyer = 'ben'`;
      benLocked.resolve();
      await anaLocked.promise;
      await tx`update offers_fixture set amount_cents = amount_cents + 1 where buyer = 'ana'`;
    },
    { db: second },
  ).catch((error: unknown) => error);

  const [a, b] = await Promise.all([forwards, backwards]);
  const losers = [a, b].filter((outcome) => outcome !== undefined);

  expect(losers).toHaveLength(1);
  return losers[0];
}

describe('isRetryable: a real serialization failure', () => {
  it('provokes SQLSTATE 40001 rather than asserting against a made-up object', async () => {
    const error = await provokeSerializationFailure();

    expect((error as { code?: string }).code).toBe('40001');
  });

  it('recognises it as retryable', async () => {
    expect(isRetryable(await provokeSerializationFailure())).toBe(true);
  });

  it('does not mistake it for a unique violation', async () => {
    expect(isUniqueViolation(await provokeSerializationFailure())).toBe(false);
  });

  it('does not convert it to a refusal, since this slice has no retry policy', async () => {
    // It propagates as the driver error. A refusal would be a lie: nothing the
    // user did is wrong, and there is nothing for them to do differently.
    expect(isRefusal(await provokeSerializationFailure())).toBe(false);
  });
});

describe('isRetryable: a real deadlock', () => {
  it('provokes SQLSTATE 40P01 at the default isolation level', async () => {
    // Pinning the isolation level matters: the contract says these tests assume
    // READ COMMITTED, and a reader could reasonably believe deadlock needs
    // something stricter. It does not.
    const error = await provokeDeadlock();

    expect((error as { code?: string }).code).toBe('40P01');
  });

  it('recognises it as retryable', async () => {
    expect(isRetryable(await provokeDeadlock())).toBe(true);
  });

  it('does not convert it to a refusal', async () => {
    expect(isRefusal(await provokeDeadlock())).toBe(false);
  });
});

describe('isRetryable: everything else is not retryable', () => {
  it('is false for a unique violation, which replaying would only repeat', async () => {
    await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`
          insert into offers_fixture (buyer, listing, status, amount_cents)
          values ('cal', 'desk', 'PENDING', 4000)
        `;
      },
      { db: first },
    );

    const error = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`
          insert into offers_fixture (buyer, listing, status, amount_cents)
          values ('cal', 'desk', 'PENDING', 5000)
        `;
      },
      { db: first },
    ).catch((caught: unknown) => caught);

    expect((error as { code?: string }).code).toBe('23505');
    expect(isRetryable(error)).toBe(false);
  });

  it('is false for a not-null violation, which is a genuine fault', async () => {
    const error = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`
          insert into offers_fixture (buyer, listing, status, amount_cents)
          values ('cal', 'desk', 'PENDING', null)
        `;
      },
      { db: first },
    ).catch((caught: unknown) => caught);

    expect((error as { code?: string }).code).toBe('23502');
    expect(isRetryable(error)).toBe(false);
  });

  it('is false for an undefined table', async () => {
    const error = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`select * from table_that_does_not_exist`;
      },
      { db: first },
    ).catch((caught: unknown) => caught);

    expect((error as { code?: string }).code).toBe('42P01');
    expect(isRetryable(error)).toBe(false);
  });

  it('is false for values that are not database errors at all', async () => {
    for (const value of [
      undefined,
      null,
      'a string',
      new Error('plain'),
      new TypeError('programmer error'),
      { code: '23505' },
      refusals.offerOnUnavailableListing(),
    ]) {
      expect(isRetryable(value)).toBe(false);
    }
  });
});
