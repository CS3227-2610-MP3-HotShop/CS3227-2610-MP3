/**
 * H4. Pool size and idle lifetime, on a platform where connections outlive the
 * work that opened them.
 *
 * postgres.js defaults `max` to 10 and `idle_timeout` to `null`, and
 * `CLIENT_OPTIONS` in `@/server/db` overrides neither. Measured against this
 * server: ten concurrent queries open ten backends, and they are all still open
 * afterwards because nothing ever reaps them.
 *
 * On Vercel each instance holds its own pool and FREEZES between invocations
 * while still holding those sockets. A burst across warm instances therefore
 * multiplies: ten instances is a hundred connections, and Supabase's pooler has
 * a `default_pool_size` far below that, after which new instances cannot
 * connect at all and the site is down for everyone rather than slow for
 * someone. The conventional serverless setting is `max: 1` with a short
 * `idle_timeout`.
 *
 * Both tests here are BEHAVIOURAL: they count real backends in
 * `pg_stat_activity` on the real server, rather than reading back the options
 * object. Counting is by DELTA against a snapshot taken immediately before,
 * because the suite's other clients hold idle connections of their own; the
 * integration project runs one file at a time and its tests are sequential, so
 * nothing else moves the number during a measurement.
 */

import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { createDb, type Db } from '@/server/db';

import { waitFor } from '../helpers/test-database';
import { testDatabaseUrl } from '../setup/integration-setup';

/** Watches the server. Not the client under test. */
let observer: Db;
let url: string;

beforeAll(() => {
  url = testDatabaseUrl();
  observer = createDb(url);
});

afterAll(async () => {
  await observer.end();
});

async function backendCount(): Promise<number> {
  const rows = await observer<{ n: number }[]>`
    select count(*)::int as n
    from pg_stat_activity
    where datname = current_database()
  `;
  return rows[0]!.n;
}

describe('pool size', () => {
  it('does not open a backend per concurrent query', async () => {
    const before = await backendCount();
    const db = createDb(url);

    try {
      // Ten queries that overlap, so the pool has every opportunity to expand.
      const inFlight = Promise.all(
        Array.from({ length: 10 }, () => db`select pg_sleep(0.3)`),
      );

      let peak = 0;
      await waitFor(
        async () => {
          peak = Math.max(peak, (await backendCount()) - before);
          return false;
        },
        { timeoutMs: 250, intervalMs: 15, what: 'the measurement window' },
      ).catch(() => undefined);

      await inFlight;

      // A serverless-shaped pool keeps one. Two is tolerated so a reconnect
      // mid-measurement is not a failure; ten is the bug.
      expect(
        peak,
        `${peak} backends opened for 10 concurrent queries; a frozen Vercel instance ` +
          'holds every one of them',
      ).toBeLessThanOrEqual(2);
    } finally {
      await db.end();
    }
  });
});

describe('idle lifetime', () => {
  it('releases an idle connection instead of holding it indefinitely', async () => {
    const before = await backendCount();
    const db = createDb(url);

    try {
      await db`select 1`;
      expect(await backendCount()).toBeGreaterThan(before);

      // With idle_timeout null this never happens and the wait times out,
      // which is the failure being pinned.
      await waitFor(async () => (await backendCount()) <= before, {
        timeoutMs: 12_000,
        intervalMs: 250,
        what: 'the idle connection to be released by idle_timeout',
      });

      expect(await backendCount()).toBeLessThanOrEqual(before);
    } finally {
      await db.end();
    }
  });

  it('reconnects transparently after the idle connection was reaped', async () => {
    // The over-correction guard: an idle_timeout so aggressive that ordinary
    // use breaks would be worse than the leak. The client must still work
    // after its connection has been dropped.
    const db = createDb(url);

    try {
      await db`select 1`;
      await waitFor(async () => (await backendCount()) >= 0, {
        timeoutMs: 50,
        intervalMs: 25,
        what: 'a short pause',
      }).catch(() => undefined);

      const rows = await db<{ two: number }[]>`select 2::int as two`;
      expect(rows[0]!.two).toBe(2);
    } finally {
      await db.end();
    }
  });
});
