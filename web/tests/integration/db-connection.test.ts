/**
 * The connection module. Contract and reasoning live at the top of
 * `db-transaction.test.ts`; this file pins DECISION 1 (one lazily built client)
 * and DECISION 2 (`prepare: false`, always).
 *
 * The `prepare: false` pin is the reason this file exists. Getting it wrong
 * passes every local test and fails only against Supabase's transaction-mode
 * pooler, under load, in production. It is pinned twice: on the option, and on
 * what the server actually ends up holding.
 */

import { afterEach, beforeAll, afterAll, describe, expect, it, vi } from 'vitest';

import { closeDb, createDb, getDb, withTransaction, type Db } from '@/server/db';

import { testActor } from '../helpers/actor';
import { testDatabaseUrl } from '../setup/integration-setup';

const REASON =
  'reading pg_prepared_statements to pin prepare:false; no actor-scoped table is involved';

/**
 * A note on the DATABASE_URL stubbing below. The `getDb` tests deliberately
 * point DATABASE_URL at the per-run TEST database, which is the one thing the
 * bootstrap guard forbids in configuration. That is not a hole in the guard:
 * the guard protects the run from a CI config, and what it guarantees is that
 * DATABASE_URL_TEST never aims at the application database. Here the direction
 * is the other way round, the stub is scoped to one test, and `afterEach`
 * removes it. The alternative, letting `getDb` read a real DATABASE_URL, is the
 * thing that must never happen.
 */

let url: string;

beforeAll(() => {
  url = testDatabaseUrl();
});

afterEach(async () => {
  vi.unstubAllEnvs();
  await closeDb();
});

afterAll(async () => {
  await closeDb();
});

describe('createDb', () => {
  it('connects and runs a query', async () => {
    const db = createDb(url);
    try {
      const rows = await db<{ one: number }[]>`select 1::int as one`;
      expect(rows[0]!.one).toBe(1);
    } finally {
      await db.end();
    }
  });

  it('hands back a new client on every call', async () => {
    const a = createDb(url);
    const b = createDb(url);
    try {
      expect(a).not.toBe(b);
    } finally {
      await a.end();
      await b.end();
    }
  });
});

describe('createDb: prepared statements are disabled', () => {
  it('sets prepare: false on the client', async () => {
    const db = createDb(url);
    try {
      expect(db.options.prepare).toBe(false);
    } finally {
      await db.end();
    }
  });

  it('leaves the server holding no prepared statements after parameterised queries', async () => {
    // The behavioural pin, and the one that matters. `pg_prepared_statements`
    // is session-local, so the queries and the check run inside a single
    // transaction, which postgres.js keeps on one backend.
    //
    // With prepare:true this returns 2 or more. With prepare:false it is 0.
    const db = createDb(url);
    try {
      const held = await withTransaction(
        testActor(),
        async (scope) => {
          const tx = scope.unscoped(REASON);
          for (const value of [1, 2, 3]) {
            await tx`select ${value}::int as n`;
          }
          const rows = await tx<{ n: number }[]>`
            select count(*)::int as n from pg_prepared_statements
          `;
          return rows[0]!.n;
        },
        { db },
      );

      expect(held).toBe(0);
    } finally {
      await db.end();
    }
  });
});

describe('getDb', () => {
  it('reads DATABASE_URL at first use, not at module load', async () => {
    // Importing with no environment at all must be harmless: Next evaluates
    // modules during `next build`, where DATABASE_URL is routinely absent.
    vi.stubEnv('DATABASE_URL', '');
    vi.resetModules();

    await expect(import('@/server/db')).resolves.toBeDefined();
  });

  it('throws a plain Error naming the variable when DATABASE_URL is unset', async () => {
    vi.stubEnv('DATABASE_URL', '');

    expect(() => getDb()).toThrow(/DATABASE_URL/);
  });

  it('does not raise a refusal for a missing variable', async () => {
    vi.stubEnv('DATABASE_URL', '');

    const { isRefusal } = await import('@/domain/errors');
    let thrown: unknown;
    try {
      getDb();
    } catch (error) {
      thrown = error;
    }

    // An operator fault, not something a user should ever read.
    expect(isRefusal(thrown)).toBe(false);
    expect(thrown).toBeInstanceOf(Error);
  });

  it('reuses one client rather than building a new one per call', async () => {
    vi.stubEnv('DATABASE_URL', url);

    const first = getDb();
    const second = getDb();

    expect(first).toBe(second);
  });

  it('applies prepare: false to the singleton too', async () => {
    vi.stubEnv('DATABASE_URL', url);

    const db: Db = getDb();
    expect(db.options.prepare).toBe(false);
  });

  it('is usable for real work', async () => {
    vi.stubEnv('DATABASE_URL', url);

    const rows = await getDb()<{ two: number }[]>`select 2::int as two`;
    expect(rows[0]!.two).toBe(2);
  });
});

describe('closeDb', () => {
  it('resolves quietly when nothing is open', async () => {
    await expect(closeDb()).resolves.toBeUndefined();
    await expect(closeDb()).resolves.toBeUndefined();
  });

  it('ends the singleton, so the old handle can no longer be queried', async () => {
    vi.stubEnv('DATABASE_URL', url);

    const db = getDb();
    await db`select 1`;
    await closeDb();

    await expect(db`select 1`).rejects.toThrow();
  });

  /**
   * The reference is cleared BEFORE the `end()` is awaited, and the ordering is
   * load-bearing: reversing it leaves every other test in this suite green.
   *
   * With clear-first, a `getDb()` that lands while a close is in flight builds
   * a new client. With await-first, it is handed the client being torn down,
   * and whatever it does next fails for reasons that will not reproduce. This
   * is a request arriving during a graceful shutdown, which is the normal case,
   * not a rare one.
   */
  it('stops handing out the closing client as soon as closeDb is called', async () => {
    vi.stubEnv('DATABASE_URL', url);

    const first = getDb();
    // Deliberately NOT awaited: this is the window the ordering protects.
    const closing = closeDb();
    const during = getDb();

    expect(during).not.toBe(first);

    await closing;

    // And the client handed out during the close is a real, usable one.
    const rows = await during<{ four: number }[]>`select 4::int as four`;
    expect(rows[0]!.four).toBe(4);
  });

  it('lets the next getDb build a fresh, working client', async () => {
    vi.stubEnv('DATABASE_URL', url);

    const first = getDb();
    await closeDb();
    const second = getDb();

    expect(second).not.toBe(first);
    const rows = await second<{ three: number }[]>`select 3::int as three`;
    expect(rows[0]!.three).toBe(3);
  });
});
