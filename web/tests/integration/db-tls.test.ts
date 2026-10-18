/**
 * B2. The connection must be encrypted, and the certificate must be verified.
 *
 * ---------------------------------------------------------------------------
 * WHAT IS WRONG TODAY
 * ---------------------------------------------------------------------------
 *
 * `CLIENT_OPTIONS` in `@/server/db` sets only `prepare: false`. postgres.js
 * 3.4.9 defaults `ssl` to `false`, so the default production path sends the
 * Supabase password and every row of marketplace data across the public
 * internet in cleartext.
 *
 * The obvious repair is `?sslmode=require`, and it is not enough.
 * `node_modules/postgres/src/connection.js:283` reads:
 *
 *     if (ssl === 'require' || ssl === 'allow' || ssl === 'prefer')
 *       options.rejectUnauthorized = false
 *
 * So `require` buys encryption with NO certificate verification: anything that
 * can get in the path presents its own certificate and reads the lot. Only
 * `verify-full`, `ssl: true`, or an object leaving `rejectUnauthorized` alone
 * actually validates.
 *
 * ---------------------------------------------------------------------------
 * HOW MUCH OF THIS IS BEHAVIOURAL, STATED PLAINLY
 * ---------------------------------------------------------------------------
 *
 * The downgrade itself IS pinned behaviourally: the tests below hand real
 * connection strings to the real driver and read back the options it resolved,
 * so if postgres.js changes how it treats `require`, this suite says so.
 *
 * What is NOT behavioural is the end-to-end proof that a production connection
 * negotiates verified TLS, and I could not make it so honestly. It needs a
 * TLS-enabled server with a certificate chaining to a CA the client trusts,
 * reached over a NON-loopback name so verification has a hostname to check.
 * This suite's PostgreSQL is plaintext on loopback, and requiring a TLS fixture
 * would mean the tests fail on every developer machine that has an ordinary
 * local PostgreSQL, which is the setup `docker-compose.yml` and `.env.example`
 * both document. A pin that cannot run is not a pin.
 *
 * So the configuration assertion below is STRUCTURAL: it reads the `ssl` option
 * the module resolved for a non-loopback URL and requires it to be one of the
 * verifying forms. It is as close to the vulnerability as a structural
 * assertion gets, because it names the exact three values the driver downgrades
 * on rather than asserting some option is merely set.
 *
 * The honest gap: nothing here proves the bytes on the wire are encrypted. That
 * needs a staging connection to Supabase and `select ssl from pg_stat_ssl`,
 * which belongs in a deployment check rather than this suite.
 */

import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { createDb, type Db } from '@/server/db';

import { testDatabaseUrl } from '../setup/integration-setup';

/**
 * The three values `connection.js:283` turns into `rejectUnauthorized: false`.
 * Encryption without authentication: the attacker presents any certificate.
 */
const UNVERIFIED = new Set(['require', 'allow', 'prefer']);

/**
 * Whether this resolved `ssl` value gives an AUTHENTICATED encrypted
 * connection.
 *
 * Written as one predicate because the first draft of these tests asked only
 * "is it one of the three downgrade strings", and `ssl: false` is not one of
 * them, so two tests passed green while the connection was plaintext. Absent
 * encryption has to fail every one of these, not slip between them.
 */
function isVerifyingTls(ssl: unknown): boolean {
  if (ssl === undefined || ssl === null || ssl === false || ssl === '') return false;
  if (typeof ssl === 'string') return !UNVERIFIED.has(ssl);
  if (typeof ssl === 'object') {
    return (ssl as { rejectUnauthorized?: unknown }).rejectUnauthorized !== false;
  }
  return ssl === true;
}

/** A Supabase-shaped URL. Nothing connects to it; only the options are read. */
const PRODUCTION_URL =
  'postgresql://postgres.abcdefghijklmnop:pw@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres';

function sslOptionFor(url: string): unknown {
  const db = createDb(url);
  const { ssl } = db.options;
  // Never connected, so there is nothing to close beyond the pool handle.
  void db.end({ timeout: 0 });
  return ssl;
}

describe('the driver really does downgrade: why `require` is not enough', () => {
  // Behavioural. These read what postgres.js resolves from real inputs, so a
  // future version that stops downgrading shows up here.

  it('resolves sslmode=require to a value the driver does not verify', () => {
    const db = createDb(`${PRODUCTION_URL}?sslmode=require`);
    try {
      expect(UNVERIFIED.has(String(db.options.ssl))).toBe(true);
    } finally {
      void db.end({ timeout: 0 });
    }
  });

  it('resolves sslmode=verify-full to a value the driver does verify', () => {
    const db = createDb(`${PRODUCTION_URL}?sslmode=verify-full`);
    try {
      expect(UNVERIFIED.has(String(db.options.ssl))).toBe(false);
      expect(db.options.ssl).toBeTruthy();
    } finally {
      void db.end({ timeout: 0 });
    }
  });
});

describe('a production connection is configured for verified TLS', () => {
  // Structural, for the reason set out in the header.

  it('does not leave ssl disabled, which would send the password in cleartext', () => {
    const ssl = sslOptionFor(PRODUCTION_URL);

    expect(ssl, 'ssl is false/undefined: the connection would be plaintext').toBeTruthy();
    expect(isVerifyingTls(ssl)).toBe(true);
  });

  it('does not settle for an unverified mode', () => {
    const ssl = sslOptionFor(PRODUCTION_URL);

    expect(
      isVerifyingTls(ssl),
      `ssl resolved to ${JSON.stringify(ssl)}, which is either disabled or one of the ` +
        'values postgres.js turns into rejectUnauthorized: false',
    ).toBe(true);
    expect(UNVERIFIED.has(String(ssl))).toBe(false);
  });

  it('does not disable certificate checking through an options object', () => {
    // `Object.assign(options, ssl)` for an object ssl, so an explicit
    // `rejectUnauthorized: false` is the same hole by another route.
    const ssl = sslOptionFor(PRODUCTION_URL);

    expect(isVerifyingTls(ssl)).toBe(true);
    if (typeof ssl === 'object' && ssl !== null) {
      expect((ssl as { rejectUnauthorized?: unknown }).rejectUnauthorized).not.toBe(false);
    }
  });

  it('applies to the direct endpoint as well as the pooler', () => {
    const ssl = sslOptionFor(
      'postgresql://postgres:pw@db.abcdefghijklmnop.supabase.co:5432/postgres',
    );

    expect(isVerifyingTls(ssl)).toBe(true);
  });

  it('still honours an explicit verify-full in the URL', () => {
    // Whatever default the module adopts must not override an operator who has
    // been more specific.
    const ssl = sslOptionFor(`${PRODUCTION_URL}?sslmode=verify-full`);

    expect(String(ssl)).toBe('verify-full');
  });
});

/**
 * A half-finished hand edit must not silently disable TLS.
 *
 * `resolveSsl` steps aside when the URL makes an explicit choice, which is
 * right. But `searchParams.has('sslmode')` is true for a VALUELESS
 * `?sslmode=`, so the module defers to a query string that says nothing;
 * postgres.js then evaluates `query.sslmode && ...`, the empty string is
 * falsy, and `ssl` lands on its `false` default. A remote connection string
 * ending in `?sslmode=` therefore sends the password in cleartext.
 *
 * Narrow, and exactly the shape a half-typed edit leaves behind, which is the
 * operator this project keeps designing around.
 */
describe('an empty sslmode does not silently mean plaintext', () => {
  it('does not fall back to plaintext for a remote host with ?sslmode=', () => {
    const ssl = sslOptionFor(`${PRODUCTION_URL}?sslmode=`);

    expect(
      isVerifyingTls(ssl),
      `ssl resolved to ${JSON.stringify(ssl)} for a remote host whose URL ends in ` +
        '"?sslmode=", so the password would cross the internet in the clear',
    ).toBe(true);
  });

  it('does not fall back to plaintext for a remote host with ?ssl=', () => {
    // The same shape through the other key the module accepts as an explicit
    // choice. This one resolves to the empty string rather than false, which
    // is just as falsy and just as plaintext.
    const ssl = sslOptionFor(`${PRODUCTION_URL}?ssl=`);

    expect(isVerifyingTls(ssl), `ssl resolved to ${JSON.stringify(ssl)}`).toBe(true);
  });

  it('still honours a sslmode that actually says something', () => {
    // The over-correction guard: a real explicit choice must keep working, or
    // the escape hatch is gone.
    expect(String(sslOptionFor(`${PRODUCTION_URL}?sslmode=disable`))).toBe('false');
    expect(String(sslOptionFor(`${PRODUCTION_URL}?sslmode=verify-full`))).toBe('verify-full');
  });
});

describe('an unrecognised spelling of the escape hatch fails safe', () => {
  // `?SSLMODE=disable` is seen by neither the module (which looks for the
  // lowercase key) nor postgres.js, so the documented escape hatch silently
  // does nothing. That direction is SAFE: the connection stays encrypted and
  // the operator notices, rather than being quietly downgraded.
  //
  // Pinned so it stays that way. If someone later makes the lookup
  // case-insensitive, this becomes a route to plaintext and should be thought
  // about rather than inherited.
  it('keeps verified TLS when sslmode is spelled in uppercase', () => {
    const ssl = sslOptionFor(`${PRODUCTION_URL}?SSLMODE=disable`);

    expect(isVerifyingTls(ssl)).toBe(true);
  });

  it('keeps verified TLS for an unrelated query parameter', () => {
    const ssl = sslOptionFor(`${PRODUCTION_URL}?pgbouncer=true`);

    expect(isVerifyingTls(ssl)).toBe(true);
  });
});

describe('the local test database still works', () => {
  // The over-correction guard, and this one IS behavioural. A fix that demands
  // verified TLS unconditionally would make every local PostgreSQL
  // unreachable, including the one running this test.
  let db: Db;

  beforeAll(() => {
    db = createDb(testDatabaseUrl());
  });

  afterAll(async () => {
    await db.end();
  });

  it('connects over plaintext loopback and runs a query', async () => {
    const rows = await db<{ one: number }[]>`select 1::int as one`;

    expect(rows[0]!.one).toBe(1);
  });
});
