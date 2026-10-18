/**
 * The guard that stands between a careless CI config and the demo database.
 *
 * These tests touch NO database on purpose. They run in the `unit` project,
 * alongside the refusal and clock suites, and they must stay there: the whole
 * point of the guard is that it fires before anything connects, so a test that
 * needed a connection to prove it would be testing the wrong moment.
 *
 * What is being defended against, concretely:
 *   - CI sets DATABASE_URL and nothing else. A fallback would truncate the
 *     demo data on the first green build.
 *   - A developer copies .env.local to .env.test.local and changes nothing.
 *   - Someone "fixes" a TLS warning by appending ?sslmode=require to the
 *     production URL and calling the result a test database.
 */

import { describe, expect, it } from 'vitest';

import {
  APP_URL_VAR,
  TEST_URL_VAR,
  TestDatabaseConfigError,
  describeTarget,
  resolveTestDatabaseUrl,
  sameTarget,
  supabaseProjectRef,
} from '../helpers/test-database';

const APP = 'postgresql://hotshop:secret@db.example.com:5432/hotshop';
const TEST = 'postgresql://hotshop:secret@db.example.com:5432/hotshop_test';

describe('resolveTestDatabaseUrl: missing configuration', () => {
  it('fails rather than falling back to DATABASE_URL', () => {
    expect(() => resolveTestDatabaseUrl({ [APP_URL_VAR]: APP })).toThrow(TestDatabaseConfigError);
  });

  it('names the variable to set, and says there is no fallback', () => {
    let message = '';
    try {
      resolveTestDatabaseUrl({ [APP_URL_VAR]: APP });
    } catch (error) {
      message = (error as Error).message;
    }

    expect(message).toContain(TEST_URL_VAR);
    expect(message).toMatch(/no fallback/i);
    expect(message).toMatch(/docker compose|\.env\.test\.local/i);
  });

  it('treats an empty or whitespace-only value as unset', () => {
    expect(() => resolveTestDatabaseUrl({ [TEST_URL_VAR]: '' })).toThrow(TestDatabaseConfigError);
    expect(() => resolveTestDatabaseUrl({ [TEST_URL_VAR]: '   ' })).toThrow(
      TestDatabaseConfigError,
    );
  });

  it('never returns DATABASE_URL under any circumstance', () => {
    for (const env of [
      { [APP_URL_VAR]: APP },
      { [APP_URL_VAR]: APP, [TEST_URL_VAR]: '' },
    ]) {
      expect(() => resolveTestDatabaseUrl(env)).toThrow();
    }
  });
});

describe('resolveTestDatabaseUrl: the same-database guard', () => {
  it('refuses to run when the two variables are the identical string', () => {
    expect(() =>
      resolveTestDatabaseUrl({ [APP_URL_VAR]: APP, [TEST_URL_VAR]: APP }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('explains that the run was refused and why, in the message', () => {
    let message = '';
    try {
      resolveTestDatabaseUrl({ [APP_URL_VAR]: APP, [TEST_URL_VAR]: APP });
    } catch (error) {
      message = (error as Error).message;
    }

    expect(message).toContain(TEST_URL_VAR);
    expect(message).toContain(APP_URL_VAR);
    expect(message).toMatch(/refused/i);
  });

  it('is not fooled by a differing query string', () => {
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: APP,
        [TEST_URL_VAR]: `${APP}?sslmode=require&pgbouncer=true`,
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('is not fooled by a differing password or user casing', () => {
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: APP,
        [TEST_URL_VAR]: 'postgresql://HOTSHOP:other@db.example.com:5432/hotshop',
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('is not fooled by localhost spelled three ways', () => {
    for (const [left, right] of [
      ['postgresql://u@localhost:5432/app', 'postgresql://u@127.0.0.1:5432/app'],
      ['postgresql://u@127.0.0.1:5432/app', 'postgresql://u@[::1]:5432/app'],
    ]) {
      expect(() =>
        resolveTestDatabaseUrl({ [APP_URL_VAR]: left, [TEST_URL_VAR]: right }),
      ).toThrow(TestDatabaseConfigError);
    }
  });

  it('is not fooled by an omitted default port', () => {
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'postgresql://u@db.example.com:5432/app',
        [TEST_URL_VAR]: 'postgresql://u@db.example.com/app',
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('is not fooled by the postgres:// and postgresql:// spellings', () => {
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'postgres://u@db.example.com:5432/app',
        [TEST_URL_VAR]: 'postgresql://u@db.example.com:5432/app',
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('allows a different database name on the same server', () => {
    expect(resolveTestDatabaseUrl({ [APP_URL_VAR]: APP, [TEST_URL_VAR]: TEST })).toBe(TEST);
  });

  it('allows a different host with the same database name', () => {
    const local = 'postgresql://hotshop@localhost:5432/hotshop';
    expect(resolveTestDatabaseUrl({ [APP_URL_VAR]: APP, [TEST_URL_VAR]: local })).toBe(local);
  });

  it('allows a different port on a host where nothing else ties the two together', () => {
    // Rewritten. This previously read 'allows a different port on the same
    // host', which stated a rule far broader than what is safe and locked in
    // the Supabase hole pinned below: on the pooler, 6543 and 5432 are the same
    // database. A different port is only evidence of a different database when
    // nothing ELSE identifies the two as one server. Here nothing does: a
    // second PostgreSQL on another port, with credentials that carry no shared
    // identity, is a genuinely separate server, which is the ordinary
    // "docker-compose alongside a system install" case.
    const other = 'postgresql://hotshop:secret@db.example.com:55432/hotshop';
    expect(resolveTestDatabaseUrl({ [APP_URL_VAR]: APP, [TEST_URL_VAR]: other })).toBe(other);
  });

  it('permits the run when DATABASE_URL is unset, since there is nothing to clash with', () => {
    expect(resolveTestDatabaseUrl({ [TEST_URL_VAR]: TEST })).toBe(TEST);
  });

  it('returns the value untrimmed of meaning but trimmed of whitespace', () => {
    expect(resolveTestDatabaseUrl({ [TEST_URL_VAR]: `  ${TEST}  ` })).toBe(TEST);
  });
});

/**
 * The deployment target is Supabase, and Supabase is the shape this guard was
 * built for, so it is the shape it has to get right.
 *
 * Every Supabase project's database is named `postgres`, and one project is
 * reachable by three endpoint spellings that differ in host, in port, or in
 * both, while addressing IDENTICAL ROWS:
 *
 *   transaction pooler  <region>.pooler.supabase.com:6543  user postgres.<ref>
 *   session pooler      <region>.pooler.supabase.com:5432  user postgres.<ref>
 *   direct              db.<ref>.supabase.co:5432          user postgres
 *
 * A {host, port, database} comparison lets all three pairings through, so a
 * developer with DATABASE_URL_TEST on the session pooler and DATABASE_URL on
 * the transaction pooler gets a run that proceeds to
 * `truncate ... restart identity cascade` and `drop table ... cascade` against
 * the production database. The database name is no help, because it is
 * `postgres` for every project in existence.
 *
 * WHAT ACTUALLY IDENTIFIES A PROJECT is the project ref, and it is present in
 * both spellings, in different places: in the pooler USERNAME
 * (`postgres.<ref>`), and in the direct HOSTNAME (`db.<ref>.supabase.co`).
 * `DatabaseTarget` already computes `user` and `sameTarget` never looks at it.
 * Port alone cannot close this, because direct-vs-pooler shares neither host
 * nor port.
 *
 * The tests below pin the three pairings as refused. The block after them pins
 * the other half, because a guard that refuses everything is no safer than one
 * that refuses nothing: it just gets switched off.
 */
describe('resolveTestDatabaseUrl: the Supabase topology', () => {
  const REF = 'abcdefghijklmnop';
  const POOLER_HOST = 'aws-0-ap-southeast-1.pooler.supabase.com';

  const TRANSACTION_POOLER = `postgresql://postgres.${REF}:pw@${POOLER_HOST}:6543/postgres`;
  const SESSION_POOLER = `postgresql://postgres.${REF}:pw@${POOLER_HOST}:5432/postgres`;
  const DIRECT = `postgresql://postgres:pw@db.${REF}.supabase.co:5432/postgres`;

  it('refuses the transaction pooler against the session pooler', () => {
    // Same host, same database name, same project. Only the port differs.
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: TRANSACTION_POOLER,
        [TEST_URL_VAR]: SESSION_POOLER,
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('refuses the transaction pooler against the direct connection', () => {
    // Neither host nor port match. The project ref is the only thing tying
    // these together, and it is in the username on one side and the hostname
    // on the other.
    expect(() =>
      resolveTestDatabaseUrl({ [APP_URL_VAR]: TRANSACTION_POOLER, [TEST_URL_VAR]: DIRECT }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('refuses the session pooler against the direct connection', () => {
    expect(() =>
      resolveTestDatabaseUrl({ [APP_URL_VAR]: SESSION_POOLER, [TEST_URL_VAR]: DIRECT }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('refuses a trailing-dot FQDN against the same project', () => {
    // `db.<ref>.supabase.co.` is a legal, fully-qualified spelling that
    // resolves identically. `isSupabaseHost` tests `endsWith('.supabase.co')`,
    // so before the trailing dot was stripped this fell out of Supabase
    // handling entirely, was classified `elsewhere`, and was ALLOWED against
    // its own pooler. One character.
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: TRANSACTION_POOLER,
        [TEST_URL_VAR]: `postgresql://postgres:pw@db.${REF}.supabase.co.:5432/postgres`,
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('refuses a trailing-dot pooler host too', () => {
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: DIRECT,
        [TEST_URL_VAR]: `postgresql://postgres.${REF}:pw@${POOLER_HOST}.:6543/postgres`,
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('treats a trailing dot as the same host for ordinary databases as well', () => {
    // Not a Supabase concern: `db.example.com.` and `db.example.com` are one
    // server, so the plain host comparison has to agree too.
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'postgresql://app:pw@db.example.com:5432/hotshop',
        [TEST_URL_VAR]: 'postgresql://app:pw@db.example.com.:5432/hotshop',
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('still allows a trailing-dot spelling of a genuinely different project', () => {
    // The stripping must not collapse distinct refs into each other.
    const test = `postgresql://postgres:pw@db.qrstuvwxyz123456.supabase.co.:5432/postgres`;
    expect(
      resolveTestDatabaseUrl({ [APP_URL_VAR]: TRANSACTION_POOLER, [TEST_URL_VAR]: test }),
    ).toBe(test);
  });

  it('refuses the pairings in either direction', () => {
    // Which variable holds which endpoint is an accident of how someone filled
    // in a file, so the guard must not depend on the order.
    for (const [left, right] of [
      [SESSION_POOLER, TRANSACTION_POOLER],
      [DIRECT, TRANSACTION_POOLER],
      [DIRECT, SESSION_POOLER],
    ]) {
      expect(() =>
        resolveTestDatabaseUrl({ [APP_URL_VAR]: left, [TEST_URL_VAR]: right }),
      ).toThrow(TestDatabaseConfigError);
    }
  });

  it('says the run is refused, in terms the reader can act on', () => {
    let message = '';
    try {
      resolveTestDatabaseUrl({ [APP_URL_VAR]: TRANSACTION_POOLER, [TEST_URL_VAR]: DIRECT });
    } catch (error) {
      message = (error as Error).message;
    }

    expect(message).toMatch(/refused/i);
    expect(message).toContain(TEST_URL_VAR);
  });
});

/**
 * The other half of B1. A guard that over-refuses is not a safe guard: it is a
 * guard someone deletes. These pin the cases that must stay ALLOWED, so the fix
 * for the block above cannot be "compare less, refuse more".
 */
describe('resolveTestDatabaseUrl: legitimately distinct databases stay allowed', () => {
  const REF_A = 'abcdefghijklmnop';
  const REF_B = 'qrstuvwxyz123456';
  const POOLER_HOST = 'aws-0-ap-southeast-1.pooler.supabase.com';

  it('allows two different Supabase projects on the same pooler host and port', () => {
    // Same host, same port, same database name, DIFFERENT project. This is the
    // case a host+port rule would wrongly refuse, and it is the normal shape of
    // a dedicated test project.
    const test = `postgresql://postgres.${REF_B}:pw@${POOLER_HOST}:6543/postgres`;
    expect(
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: `postgresql://postgres.${REF_A}:pw@${POOLER_HOST}:6543/postgres`,
        [TEST_URL_VAR]: test,
      }),
    ).toBe(test);
  });

  it('allows two different Supabase projects on their direct endpoints', () => {
    const test = `postgresql://postgres:pw@db.${REF_B}.supabase.co:5432/postgres`;
    expect(
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: `postgresql://postgres:pw@db.${REF_A}.supabase.co:5432/postgres`,
        [TEST_URL_VAR]: test,
      }),
    ).toBe(test);
  });

  it('allows one project direct against a different project pooled', () => {
    // The mirror of the refused pairing: different refs, so different data.
    const test = `postgresql://postgres.${REF_B}:pw@${POOLER_HOST}:6543/postgres`;
    expect(
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: `postgresql://postgres:pw@db.${REF_A}.supabase.co:5432/postgres`,
        [TEST_URL_VAR]: test,
      }),
    ).toBe(test);
  });

  it('allows two different local databases on the same host and port', () => {
    // The everyday case, and the one most at risk from an over-broad fix: one
    // server, two databases, same credentials.
    const test = 'postgresql://hotshop:hotshop@localhost:5432/hotshop_test';
    expect(
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'postgresql://hotshop:hotshop@localhost:5432/hotshop',
        [TEST_URL_VAR]: test,
      }),
    ).toBe(test);
  });

  it('allows the same local database name on two different ports', () => {
    // A docker-compose PostgreSQL beside a system install.
    const test = 'postgresql://hotshop:hotshop@localhost:55432/hotshop';
    expect(
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'postgresql://hotshop:hotshop@localhost:5432/hotshop',
        [TEST_URL_VAR]: test,
      }),
    ).toBe(test);
  });

  it('does not treat a shared non-Supabase username as shared identity', () => {
    // `postgres` is the default superuser name everywhere. If the fix keys on
    // the username rather than on the project ref inside it, this breaks.
    const test = 'postgresql://postgres:pw@db-test.internal:5432/postgres';
    expect(
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'postgresql://postgres:pw@db-prod.internal:5432/postgres',
        [TEST_URL_VAR]: test,
      }),
    ).toBe(test);
  });
});

describe('resolveTestDatabaseUrl: malformed values fail at the bootstrap', () => {
  it('rejects a value that is not a URL', () => {
    expect(() => resolveTestDatabaseUrl({ [TEST_URL_VAR]: 'host=localhost dbname=app' })).toThrow(
      TestDatabaseConfigError,
    );
  });

  it('rejects a non-postgres scheme', () => {
    expect(() => resolveTestDatabaseUrl({ [TEST_URL_VAR]: 'mysql://u@h:3306/app' })).toThrow(
      TestDatabaseConfigError,
    );
  });
});

describe('describeTarget', () => {
  it('reduces a URL to the four things that decide which rows get written', () => {
    expect(describeTarget('postgresql://User:pw@DB.Example.com:6543/HotShop?sslmode=require')).toEqual(
      { host: 'db.example.com', port: '6543', database: 'hotshop', user: 'user' },
    );
  });

  it('defaults an empty path to the user name, the way libpq does', () => {
    expect(describeTarget('postgresql://hotshop@localhost')).toEqual({
      host: 'localhost',
      port: '5432',
      database: 'hotshop',
      user: 'hotshop',
    });
  });

  it('decodes a percent-escaped database name', () => {
    expect(describeTarget('postgresql://u@h:5432/my%20db').database).toBe('my db');
  });
});

describe('sameTarget', () => {
  it('is reflexive for an unparseable string', () => {
    expect(sameTarget('not a url', 'not a url')).toBe(true);
  });

  it('refuses two different unparseable strings rather than calling them distinct', () => {
    // REWRITTEN, and the inversion is the point. This previously expected
    // `false`, on the reasoning that two strings that cannot be parsed cannot
    // be shown to be the same. True, but it is the wrong question: the guard
    // does not need them shown to be the same, it needs them shown to be
    // DIFFERENT before it permits a run that truncates and drops.
    //
    // `sameTarget` is therefore "has NOT been shown to address a different
    // database", and anything unreadable answers true. B2 is the reason this
    // matters in practice: a hand-edited .env.local with a typo in it is the
    // ordinary case, not the exotic one.
    expect(sameTarget('not a url', 'also not a url')).toBe(true);
  });
});

/**
 * Failing closed, pinned as its own property rather than left implicit in the
 * cases above.
 *
 * The asymmetry worth stating: refusing a run that would have been safe costs a
 * developer one error message, and permitting one that is not costs the
 * production database. Every ambiguous answer therefore resolves to "refuse".
 */
describe('resolveTestDatabaseUrl: ambiguity refuses rather than permits', () => {
  const VALID_TEST = 'postgresql://hotshop:pw@localhost:55432/hotshop_test';

  it('refuses when DATABASE_URL cannot be parsed at all', () => {
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'host=db.example.com dbname=hotshop user=app',
        [TEST_URL_VAR]: VALID_TEST,
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('refuses when DATABASE_URL is a URL but not a postgres one', () => {
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'mysql://app:pw@db.example.com:3306/hotshop',
        [TEST_URL_VAR]: VALID_TEST,
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('says the comparison could not be made, not that the databases match', () => {
    // The message has to be honest about which check failed, or the reader
    // goes looking for a clash that does not exist.
    let message = '';
    try {
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'postgres ql://app:pw@db.example.com:5432/hotshop',
        [TEST_URL_VAR]: VALID_TEST,
      });
    } catch (error) {
      message = (error as Error).message;
    }

    expect(message).toMatch(/could not be compared/i);
    expect(message).toMatch(/refused/i);
  });

  it('refuses two Supabase endpoints when either project cannot be named', () => {
    // A pooler host with a username that carries no project ref. Which project
    // this reaches is unknown, so it is not knowably a different one.
    const POOLER = 'aws-0-ap-southeast-1.pooler.supabase.com';
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: `postgresql://postgres.abcdefghijklmnop:pw@${POOLER}:6543/postgres`,
        [TEST_URL_VAR]: `postgresql://someuser:pw@${POOLER}:5432/postgres`,
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('refuses two unidentified Supabase endpoints', () => {
    expect(() =>
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'postgresql://someuser:pw@api.supabase.co:5432/postgres',
        [TEST_URL_VAR]: 'postgresql://otheruser:pw@other.supabase.co:5432/postgres',
      }),
    ).toThrow(TestDatabaseConfigError);
  });

  it('still allows a Supabase application database against a local test one', () => {
    // The limit of failing closed. One side is Supabase and the other is not,
    // so they cannot share a host and the ordinary comparison is sound. This
    // is the single most common safe setup and must not be collateral damage.
    const POOLER = 'aws-0-ap-southeast-1.pooler.supabase.com';
    expect(
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: `postgresql://postgres.abcdefghijklmnop:pw@${POOLER}:6543/postgres`,
        [TEST_URL_VAR]: VALID_TEST,
      }),
    ).toBe(VALID_TEST);
  });

  it('allows a Supabase endpoint whose project cannot be named against a local one', () => {
    expect(
      resolveTestDatabaseUrl({
        [APP_URL_VAR]: 'postgresql://someuser:pw@api.supabase.co:5432/postgres',
        [TEST_URL_VAR]: VALID_TEST,
      }),
    ).toBe(VALID_TEST);
  });
});

/**
 * The over-generalisation guard. `postgres.abcdefghijklmnop` is a SUPABASE
 * shape, and reading a ref out of anything that merely resembles it would
 * invent identities that collide.
 */
describe('supabaseProjectRef', () => {
  it('reads the ref from a pooler username', () => {
    expect(
      supabaseProjectRef(
        describeTarget(
          'postgresql://postgres.abcdefghijklmnop:pw@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres',
        ),
      ),
    ).toBe('abcdefghijklmnop');
  });

  it('reads the ref from a direct hostname', () => {
    expect(
      supabaseProjectRef(
        describeTarget('postgresql://postgres:pw@db.abcdefghijklmnop.supabase.co:5432/postgres'),
      ),
    ).toBe('abcdefghijklmnop');
  });

  it('reads the ref for a db user other than postgres', () => {
    expect(
      supabaseProjectRef(
        describeTarget(
          'postgresql://readonly.abcdefghijklmnop:pw@aws-0-eu-west-2.pooler.supabase.com:6543/postgres',
        ),
      ),
    ).toBe('abcdefghijklmnop');
  });

  it('finds nothing for a local user that merely contains a dot', () => {
    // The trap. `app.service` is not a project ref, and treating it as one
    // would make two unrelated local databases look like one project.
    expect(
      supabaseProjectRef(describeTarget('postgresql://app.service:pw@localhost:5432/hotshop')),
    ).toBeUndefined();
  });

  it('finds nothing on a non-Supabase host, whatever the username looks like', () => {
    expect(
      supabaseProjectRef(
        describeTarget('postgresql://postgres.abcdefghijklmnop:pw@db.internal:5432/postgres'),
      ),
    ).toBeUndefined();
  });

  it('finds nothing for a Supabase host with no ref anywhere in it', () => {
    expect(
      supabaseProjectRef(describeTarget('postgresql://postgres:pw@api.supabase.co:5432/postgres')),
    ).toBeUndefined();
  });

  it('does not treat two unrelated local databases as one project', () => {
    const left = describeTarget('postgresql://app.service:pw@localhost:5432/hotshop');
    const right = describeTarget('postgresql://app.service:pw@localhost:5432/hotshop_test');

    expect(supabaseProjectRef(left)).toBeUndefined();
    expect(supabaseProjectRef(right)).toBeUndefined();
    expect(sameTarget(
      'postgresql://app.service:pw@localhost:5432/hotshop',
      'postgresql://app.service:pw@localhost:5432/hotshop_test',
    )).toBe(false);
  });
});
