/**
 * SQLSTATE 23505 -> refusal. Contract and reasoning: DECISION 5 and DECISION 6
 * at the top of `db-transaction.test.ts`.
 *
 * ---------------------------------------------------------------------------
 * WHAT THIS FILE PROVES, AND WHAT IT DOES NOT
 * ---------------------------------------------------------------------------
 *
 * `offers_fixture` carries the same partial unique index the marketplace will
 * use:
 *
 *   create unique index offers_fixture_one_pending_per_buyer_listing
 *     on offers_fixture (buyer, listing) where status = 'PENDING';
 *
 * It exists to prove the MAPPING: that a real 23505 raised by a real index
 * under real concurrency comes out of `@/server/db` as a refusal with no driver
 * detail in it. It is NOT the marketplace invariant, and a green run here is
 * NOT evidence for FR-1709 ("at most one pending offer per buyer per listing").
 *
 * FR-1709 is about the real `offers` table, which does not exist yet. It is
 * verified in INV-07, when that table lands, by a concurrent test written
 * against it. If the real migration ships without the index, every test in this
 * file still passes and the invariant is still broken. That gap is deliberate
 * and is the reason this paragraph is here rather than in a commit message.
 */

import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  REFUSAL_CODES,
  isAuthoredRefusal,
  isRefusal,
  refusals,
  type Refusal,
} from '@/domain/errors';
import {
  UNIQUE_VIOLATION,
  createDb,
  describeUniqueViolation,
  isUniqueViolation,
  withTransaction,
  type Db,
  type Tx,
  type UniqueViolation,
} from '@/server/db';

import { testActor } from '../helpers/actor';
import {
  TRUNCATE_FIXTURES,
  blockedBackendCount,
  deferred,
  waitFor,
} from '../helpers/test-database';
import { testDatabaseUrl } from '../setup/integration-setup';

const REASON =
  'unique-violation mapping and the concurrent-insert race on offers_fixture, which has no owner; no actor-scoped table is involved';

/** Three independent clients: two contenders and one observer. */
let first: Db;
let second: Db;
let observer: Db;

beforeAll(() => {
  const url = testDatabaseUrl();
  first = createDb(url);
  second = createDb(url);
  observer = createDb(url);
});

afterAll(async () => {
  await first.end();
  await second.end();
  await observer.end();
});

beforeEach(async () => {
  await observer.unsafe(TRUNCATE_FIXTURES);
});

afterEach(async () => {
  await observer.unsafe(TRUNCATE_FIXTURES);
});

const BUYER = 'ana';
const LISTING = 'bike';

async function insertPending(tx: Tx, amountCents: number): Promise<void> {
  await tx`
    insert into offers_fixture (buyer, listing, status, amount_cents)
    values (${BUYER}, ${LISTING}, 'PENDING', ${amountCents})
  `;
}

/**
 * The caller's mapper, and the contract's own model of good practice.
 *
 * THIS FUNCTION CURRENTLY FAILS `npm run typecheck`, at every call site, and
 * that is the B3 pin in its sharpest form. `TransactionOptions.onUniqueViolation`
 * is declared `(violation: UniqueViolation) => Refusal`, which makes declining
 * unrepresentable: there is no value of type `Refusal` that means "not mine".
 * The fix is in `@/server/db`, widening the return type to
 * `Refusal | undefined`. It is NOT a cast here. A cast would make the suite
 * green while leaving every mapper in the codebase obliged to invent an answer
 * for constraints it has never heard of.
 *
 * It answers for the ONE index it was written for and returns `undefined` for
 * anything else. The previous version of this function ended
 * `return refusals.offerOnUnavailableListing();`, which is the exact defect
 * pinned in 'a mapper that does not recognise the constraint' below: a mapper
 * that answers for every index in the transaction will, sooner or later, tell a
 * user something false about a table it has never heard of.
 */
function mapper(existingAmountCents: number) {
  return vi.fn((violation: UniqueViolation): Refusal | undefined => {
    if (violation.constraint === 'offers_fixture_one_pending_per_buyer_listing') {
      return refusals.duplicatePendingOffer(existingAmountCents);
    }
    return undefined;
  });
}

/**
 * Patterns that would mean driver or schema detail reached a user. Word
 * boundaries throughout, so ordinary prose in a refusal message cannot trip
 * them by accident.
 */
const LEAKS: ReadonlyArray<readonly [string, RegExp]> = [
  ['SQL keywords', /\b(insert|select|update|delete|values|from|where|into|on conflict)\b/i],
  ['schema vocabulary', /\b(constraint|unique index|relation|column|table|duplicate key)\b/i],
  ['the fixture table name', /offers_fixture/i],
  ['the index name', /one_pending_per_buyer_listing/i],
  ['an SQLSTATE', /\b23505\b|\bsqlstate\b/i],
  ['a stack frame', /\n\s*at\s|\.ts:\d+:\d+|\.js:\d+:\d+/],
  ['driver internals', /postgres\.js|node_modules|PostgresError|cjs\/src/i],
];

function expectNoLeak(message: string): void {
  for (const [what, pattern] of LEAKS) {
    expect(message, `refusal message leaks ${what}: ${JSON.stringify(message)}`).not.toMatch(
      pattern,
    );
  }
}

describe('the fixture index itself', () => {
  // Preconditions. If these fail, the mapping tests below are not testing what
  // they claim, because the index would not be firing.

  it('allows the same buyer and listing when neither row is pending', async () => {
    await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`
          insert into offers_fixture (buyer, listing, status, amount_cents)
          values (${BUYER}, ${LISTING}, 'WITHDRAWN', 1000),
                 (${BUYER}, ${LISTING}, 'WITHDRAWN', 2000)
        `;
      },
      { db: first },
    );

    const rows = await observer<{ n: number }[]>`select count(*)::int as n from offers_fixture`;
    expect(rows[0]!.n).toBe(2);
  });

  it('allows one pending offer alongside any number of non-pending ones', async () => {
    await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`
          insert into offers_fixture (buyer, listing, status, amount_cents)
          values (${BUYER}, ${LISTING}, 'WITHDRAWN', 1000),
                 (${BUYER}, ${LISTING}, 'REJECTED', 2000)
        `;
        await insertPending(tx, 3000);
      },
      { db: first },
    );

    const rows = await observer<{ n: number }[]>`select count(*)::int as n from offers_fixture`;
    expect(rows[0]!.n).toBe(3);
  });
});

describe('two genuinely concurrent inserts of the same pending pair', () => {
  it('lets exactly one succeed and refuses the other', async () => {
    const onUniqueViolation = mapper(4_000);

    const firstInserted = deferred();
    const releaseFirst = deferred();

    // Contender A: inserts, announces, then waits to be released.
    const a = withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertPending(tx, 4_000);
        firstInserted.resolve();
        await releaseFirst.promise;
        return 'A' as const;
      },
      { db: first, onUniqueViolation },
    );

    await firstInserted.promise;

    // Contender B starts while A is still open. Its INSERT reaches the index
    // and parks there: PostgreSQL makes it wait on A's transaction id rather
    // than deciding immediately.
    const b = withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertPending(tx, 5_000);
        return 'B' as const;
      },
      { db: second, onUniqueViolation },
    );

    // The proof that both are in flight: B is blocked by another backend. A
    // poll rather than a sleep, so this is as fast as the machine allows and
    // does not go flaky on a slow one.
    await waitFor(async () => (await blockedBackendCount(observer)) >= 1, {
      what: 'the second insert to block on the partial unique index',
    });

    releaseFirst.resolve();

    const outcomes = await Promise.allSettled([a, b]);
    const fulfilled = outcomes.filter((o) => o.status === 'fulfilled');
    const rejected = outcomes.filter((o) => o.status === 'rejected');

    expect(fulfilled).toHaveLength(1);
    expect(rejected).toHaveLength(1);

    const loser = (rejected[0] as PromiseRejectedResult).reason;
    expect(isAuthoredRefusal(loser)).toBe(true);

    // And the database agrees: one pending row survived.
    const rows = await observer<{ n: number }[]>`
      select count(*)::int as n from offers_fixture where status = 'PENDING'
    `;
    expect(rows[0]!.n).toBe(1);
  });

  it('gives the loser a refusal carrying a reason code', async () => {
    const onUniqueViolation = mapper(4_000);
    const loser = await raceForTheSamePair(onUniqueViolation);

    expect(isAuthoredRefusal(loser)).toBe(true);
    expect(isRefusal(loser)).toBe(true);
    expect(REFUSAL_CODES).toContain(loser.code);
    expect(loser.code).toBe('INVALID_STATE');
  });

  it('gives the loser a message with no SQL, constraint name, stack frame or driver internals in it', async () => {
    const onUniqueViolation = mapper(4_000);
    const loser = await raceForTheSamePair(onUniqueViolation);

    expectNoLeak(loser.message);

    // It is not empty prose either: FR-1712 wants the real values.
    expect(loser.message).toContain('S$40.00');
    expect(loser.message.length).toBeGreaterThan(20);
  });

  it('does not leak through the serialized payload either', async () => {
    const onUniqueViolation = mapper(4_000);
    const loser = await raceForTheSamePair(onUniqueViolation);

    // A refusal crosses the Next.js boundary as a payload. Whatever a render
    // site can read must be as clean as the message.
    expectNoLeak(JSON.stringify(loser.asPayload()));
  });

  it('hands the mapper the constraint and the table so it can discriminate', async () => {
    const onUniqueViolation = mapper(4_000);
    await raceForTheSamePair(onUniqueViolation);

    expect(onUniqueViolation).toHaveBeenCalledTimes(1);
    const violation = onUniqueViolation.mock.calls[0]![0];
    expect(violation.constraint).toBe('offers_fixture_one_pending_per_buyer_listing');
    expect(violation.table).toBe('offers_fixture');
  });

  /**
   * Runs the race and returns the loser's rejection reason, typed as a refusal.
   * Fails the test if nobody lost or if more than one did.
   */
  async function raceForTheSamePair(
    onUniqueViolation: ReturnType<typeof mapper>,
  ): Promise<ReturnType<typeof refusals.duplicatePendingOffer>> {
    const firstInserted = deferred();
    const releaseFirst = deferred();

    const a = withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertPending(tx, 4_000);
        firstInserted.resolve();
        await releaseFirst.promise;
        return 'A' as const;
      },
      { db: first, onUniqueViolation },
    );

    await firstInserted.promise;

    const b = withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await insertPending(tx, 5_000);
        return 'B' as const;
      },
      { db: second, onUniqueViolation },
    );

    await waitFor(async () => (await blockedBackendCount(observer)) >= 1, {
      what: 'the second insert to block on the partial unique index',
    });

    releaseFirst.resolve();

    const outcomes = await Promise.allSettled([a, b]);
    const rejected = outcomes.filter((o) => o.status === 'rejected');
    expect(rejected).toHaveLength(1);

    return (rejected[0] as PromiseRejectedResult).reason as ReturnType<
      typeof refusals.duplicatePendingOffer
    >;
  }
});

describe('a sequential duplicate maps the same way', () => {
  it('refuses the second insert once the first has committed', async () => {
    const onUniqueViolation = mapper(4_000);

    await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 4_000),
      {
        db: first,
        onUniqueViolation,
      },
    );

    const thrown = await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 5_000),
      {
        db: first,
        onUniqueViolation,
      },
    ).catch((error: unknown) => error);

    expect(isAuthoredRefusal(thrown)).toBe(true);
    expectNoLeak((thrown as Error).message);
    expect(onUniqueViolation).toHaveBeenCalledTimes(1);
  });
});

describe('only a unique violation is mapped', () => {
  it('leaves a not-null violation alone and does not call the mapper', async () => {
    const onUniqueViolation = mapper(4_000);

    const thrown = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`
          insert into offers_fixture (buyer, listing, status, amount_cents)
          values (${BUYER}, ${LISTING}, 'PENDING', null)
        `;
      },
      { db: first, onUniqueViolation },
    ).catch((error: unknown) => error);

    expect(isRefusal(thrown)).toBe(false);
    expect(isAuthoredRefusal(thrown)).toBe(false);
    expect((thrown as { code?: string }).code).toBe('23502');
    expect(onUniqueViolation).not.toHaveBeenCalled();
  });

  it('leaves an undefined table alone, because that is a migration fault', async () => {
    const onUniqueViolation = mapper(4_000);

    const thrown = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`select * from table_that_does_not_exist`;
      },
      { db: first, onUniqueViolation },
    ).catch((error: unknown) => error);

    expect(isRefusal(thrown)).toBe(false);
    expect((thrown as { code?: string }).code).toBe('42P01');
    expect(onUniqueViolation).not.toHaveBeenCalled();
  });

  it('leaves a check-style failure alone', async () => {
    const onUniqueViolation = mapper(4_000);

    const thrown = await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        // 22P02: invalid text representation.
        await tx`select 'not-a-number'::integer`;
      },
      { db: first, onUniqueViolation },
    ).catch((error: unknown) => error);

    expect(isRefusal(thrown)).toBe(false);
    expect(onUniqueViolation).not.toHaveBeenCalled();
  });

  it('propagates a unique violation untouched when no mapper is supplied', async () => {
    // Deliberate: the helper does not invent a message it has no business
    // authoring. See DECISION 5.
    await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 4_000),
      { db: first },
    );

    const thrown = await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 5_000),
      {
        db: first,
      },
    ).catch((error: unknown) => error);

    expect(isRefusal(thrown)).toBe(false);
    expect((thrown as { code?: string }).code).toBe(UNIQUE_VIOLATION);
  });
});

describe('isUniqueViolation and describeUniqueViolation', () => {
  async function captureUniqueViolation(): Promise<unknown> {
    await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 4_000),
      { db: first },
    );
    return withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 5_000),
      { db: first },
    ).catch((error: unknown) => error);
  }

  it('recognises SQLSTATE 23505', async () => {
    expect(UNIQUE_VIOLATION).toBe('23505');
    expect(isUniqueViolation(await captureUniqueViolation())).toBe(true);
  });

  it('rejects anything that is not 23505', async () => {
    for (const value of [
      undefined,
      null,
      'a string',
      new Error('plain'),
      { code: '23502' },
      { code: '42P01' },
      refusals.offerOnUnavailableListing(),
    ]) {
      expect(isUniqueViolation(value)).toBe(false);
    }
  });

  it('describes the violation for a 23505 and returns undefined otherwise', async () => {
    const violation = describeUniqueViolation(await captureUniqueViolation());

    expect(violation).toEqual({
      constraint: 'offers_fixture_one_pending_per_buyer_listing',
      table: 'offers_fixture',
    });
    expect(describeUniqueViolation(new Error('plain'))).toBeUndefined();
  });
});

/**
 * B3. A mapper is written for the indexes its author knows about, and a
 * transaction raises 23505 from wherever it happens to touch.
 *
 * TD-02a's four marketplace operations are all multi-write, so this is the
 * normal case rather than the edge one: an offers mapper will find itself
 * handed a violation from some other table in the same unit of work. Without a
 * way to decline, it answers anyway, and `withTransaction` accepts the answer
 * because `isAuthoredRefusal` passes. The user then reads a refusal about a
 * pending offer, with an amount that was never looked up, for a constraint on a
 * table that has nothing to do with offers. That is precisely what FR-1712
 * forbids: a refusal must state what is actually wrong using the real values
 * involved.
 *
 * THE DECISION: `onUniqueViolation` returns `Refusal | undefined`. `undefined`
 * means "not mine", and a declined violation propagates as the original driver
 * error rather than being dressed up as something the caller understands.
 *
 * Declining deliberately produces an ugly, leaky driver error rather than a
 * polite refusal. That is correct. An unmapped 23505 is a constraint nobody
 * wrote a message for, which is a gap in the code, and it should read like one
 * in the logs instead of being smoothed over for a user who cannot act on it.
 */
describe('a mapper that does not recognise the constraint', () => {
  async function violateUnrelatedIndex(
    onUniqueViolation: ReturnType<typeof mapper>,
  ): Promise<unknown> {
    await withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        await tx`insert into unrelated_fixture (id, label) values (1, 'first')`;
      },
      { db: first },
    );

    return withTransaction(
      testActor(),
      async (scope) => {
        const tx = scope.unscoped(REASON);
        // A write the mapper's author never considered, in the same unit of
        // work as the offers writes it was built for.
        await tx`insert into unrelated_fixture (id, label) values (1, 'second')`;
      },
      { db: first, onUniqueViolation },
    ).catch((error: unknown) => error);
  }

  it('is still offered the violation, so it can decide', async () => {
    const onUniqueViolation = mapper(4_000);
    await violateUnrelatedIndex(onUniqueViolation);

    expect(onUniqueViolation).toHaveBeenCalledTimes(1);
    expect(onUniqueViolation.mock.calls[0]![0]).toEqual({
      constraint: 'unrelated_fixture_pkey',
      table: 'unrelated_fixture',
    });
  });

  it('may decline by returning undefined, which is not a programmer error', async () => {
    // The crisp distinction. `undefined` means "not mine" and is a supported
    // answer; returning some OTHER non-refusal is a bug and still raises a
    // TypeError (pinned in 'a mapper that returns the wrong thing' below).
    const onUniqueViolation = mapper(4_000);
    const thrown = await violateUnrelatedIndex(onUniqueViolation);

    expect(onUniqueViolation.mock.results[0]!.value).toBeUndefined();
    expect(thrown).not.toBeInstanceOf(TypeError);
  });

  it('propagates the original driver error when the mapper declines', async () => {
    const thrown = await violateUnrelatedIndex(mapper(4_000));

    expect(isRefusal(thrown)).toBe(false);
    expect(isAuthoredRefusal(thrown)).toBe(false);
    expect((thrown as { code?: string }).code).toBe(UNIQUE_VIOLATION);
    expect((thrown as { constraint_name?: string }).constraint_name).toBe('unrelated_fixture_pkey');
  });

  it('never shows the user a refusal invented for a different table', async () => {
    // The concrete harm. Before the decline path existed, this produced
    // "You already have a pending offer of S$40.00 on this listing", for a
    // primary-key collision on a table with no offers in it, with an amount
    // that was never read from anywhere.
    const thrown = await violateUnrelatedIndex(mapper(4_000));

    expect((thrown as Error).message).not.toContain('S$40.00');
    expect((thrown as Error).message).not.toMatch(/pending offer/i);
    // And what came out IS the genuine violation, not some substitute for it.
    // Without this the assertions above pass for any error at all, including
    // the TypeError the helper currently raises for a declining mapper.
    expect(isUniqueViolation(thrown)).toBe(true);
  });

  it('still maps the index the mapper does recognise, in the same suite', async () => {
    // Declining must not become "declines everything". The offers index still
    // produces the offers refusal.
    const onUniqueViolation = mapper(4_000);

    await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 4_000),
      {
        db: first,
        onUniqueViolation,
      },
    );
    const thrown = await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 5_000),
      {
        db: first,
        onUniqueViolation,
      },
    ).catch((error: unknown) => error);

    expect(isAuthoredRefusal(thrown)).toBe(true);
    expectNoLeak((thrown as Error).message);
  });

  it('decides per violation, not per transaction', async () => {
    // One mapper, two different indexes, two different answers. A mapper that
    // latched onto its first decision would fail this.
    const onUniqueViolation = mapper(4_000);

    const declined = await violateUnrelatedIndex(onUniqueViolation);

    await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 4_000),
      {
        db: first,
        onUniqueViolation,
      },
    );
    const mapped = await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 5_000),
      {
        db: first,
        onUniqueViolation,
      },
    ).catch((error: unknown) => error);

    expect(onUniqueViolation).toHaveBeenCalledTimes(2);
    expect(isUniqueViolation(declined)).toBe(true);
    expect(isAuthoredRefusal(declined)).toBe(false);
    expect(isAuthoredRefusal(mapped)).toBe(true);
  });
});

describe('a mapper that throws', () => {
  it('lets the thrown error out rather than burying it under the driver error', async () => {
    // A bug inside a mapper (a bad lookup, a null dereference) must be visible.
    // Swapping it for the 23505 would hide the real fault behind a symptom.
    const boom = new TypeError('mapper looked up a listing that was not loaded');

    await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 4_000),
      { db: first },
    );

    const thrown = await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 5_000),
      {
        db: first,
        onUniqueViolation: () => {
          throw boom;
        },
      },
    ).catch((error: unknown) => error);

    expect(thrown).toBe(boom);
  });

  it('lets a refusal thrown by the mapper out as that refusal', async () => {
    // Throwing a catalogue refusal instead of returning it is a reasonable
    // thing for a mapper author to do by mistake, and it should behave.
    const refusal = refusals.offerOnUnavailableListing();

    await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 4_000),
      { db: first },
    );

    const thrown = await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 5_000),
      {
        db: first,
        onUniqueViolation: () => {
          throw refusal;
        },
      },
    ).catch((error: unknown) => error);

    expect(thrown).toBe(refusal);
    expect(isAuthoredRefusal(thrown)).toBe(true);
  });
});

describe('a mapper that returns the wrong thing fails loudly', () => {
  it('throws a TypeError rather than throwing a forged refusal', async () => {
    await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 4_000),
      { db: first },
    );

    const thrown = await withTransaction(
      testActor(),
      async (scope) => insertPending(scope.unscoped(REASON), 5_000),
      {
        db: first,
        // A plain object passes isRefusal but not isAuthoredRefusal. See the
        // escape (b) note in @/domain/errors: this is exactly the value the
        // helper must not pass through.
        onUniqueViolation: (() => ({
          brand: 'hotshop/refusal',
          code: 'INVALID_STATE',
          message: 'forged',
        })) as never,
      },
    ).catch((error: unknown) => error);

    expect(thrown).toBeInstanceOf(TypeError);
    expect(isRefusal(thrown)).toBe(false);
  });
});
