/**
 * Test-database bootstrap. TEST CONFIGURATION, owned by the tester.
 *
 * Nothing here is application code and nothing here is imported from `src/`.
 * The guard functions are deliberately PURE and separately exported so they can
 * be exercised without a database at all, in the fast unit lane. See
 * `tests/config/test-database-isolation.test.ts`.
 *
 * THE ONE JOB OF THIS FILE: make it impossible for a test run to write to the
 * application database. The failure being defended against is not malice, it is
 * a CI config that sets one variable and forgets the other, or a developer who
 * copies `.env.local` to `.env.test.local` and changes nothing.
 *
 * THAT SECOND DEVELOPER WAS MISSED FOR TWO REVISIONS, and the way it happened is
 * worth keeping written down. The comparison was never wrong; it was never
 * REACHED. `DATABASE_URL` was read from `process.env`, nothing in the test
 * process ever loaded `.env.local`, and `.env.local` is precisely where this
 * project tells the operator to put it. So `appUrl` was the empty string, the
 * guard short-circuited, and a run with DATABASE_URL and DATABASE_URL_TEST set
 * to the IDENTICAL string went 95 of 95 green against the application database.
 * Two rounds of work went into making the comparison sharper while it sat
 * behind a branch that never executed.
 *
 * The lesson paid for: a control has to be tested end to end from the
 * configuration an operator actually writes, not from the inputs the unit test
 * hands it. The unit tests for the comparison were right and thorough and
 * proved nothing about safety.
 */

import { readFileSync } from 'node:fs';
import { basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseEnv } from 'node:util';

import postgres from 'postgres';

export const TEST_URL_VAR = 'DATABASE_URL_TEST';
export const APP_URL_VAR = 'DATABASE_URL';

/** Thrown by the bootstrap. Not a domain refusal: this is a configuration fault. */
export class TestDatabaseConfigError extends Error {
  override readonly name = 'TestDatabaseConfigError';
}

/** Host spellings that mean the same machine. Compared after lowercasing. */
const LOOPBACK = new Set(['localhost', '127.0.0.1', '::1', '[::1]']);

export interface DatabaseTarget {
  readonly host: string;
  readonly port: string;
  readonly database: string;
  readonly user: string;
}

/**
 * The part of a connection string that decides WHICH database is written to.
 * Query parameters (`sslmode`, `pgbouncer`, `connect_timeout`, ...) are
 * deliberately excluded: appending `?sslmode=require` to the production URL
 * produces a different string that reaches exactly the same rows, and a
 * raw-string comparison would wave it through.
 */
export function describeTarget(raw: string): DatabaseTarget {
  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    throw new TestDatabaseConfigError(
      `${TEST_URL_VAR} is not a valid connection URL. ` +
        'Expected something like postgresql://user:password@host:5432/database.',
    );
  }

  if (parsed.protocol !== 'postgres:' && parsed.protocol !== 'postgresql:') {
    throw new TestDatabaseConfigError(
      `${TEST_URL_VAR} must use the postgres:// or postgresql:// scheme, got ${parsed.protocol}//.`,
    );
  }

  // A trailing dot is a legal, fully-qualified spelling of the same name:
  // `db.<ref>.supabase.co.` resolves exactly where `db.<ref>.supabase.co`
  // does. Stripped here, at the single point where a host becomes comparable,
  // so neither `isSupabaseHost` nor the host comparison can be stepped around
  // by one character.
  const host = parsed.hostname.toLowerCase().replace(/\.+$/, '');
  const user = decodeURIComponent(parsed.username).toLowerCase();
  const database = decodeURIComponent(parsed.pathname.replace(/^\//, '')).toLowerCase();

  return {
    host: LOOPBACK.has(host) ? 'localhost' : host,
    port: parsed.port === '' ? '5432' : parsed.port,
    // An empty path means "the database named after the user", which is what
    // libpq does, so the two spellings must compare equal.
    database: database === '' ? user : database,
    user,
  };
}

/* ------------------------------------------------------------------ */
/* Supabase project identity                                           */
/* ------------------------------------------------------------------ */

/**
 * Why host/port/database is not enough.
 *
 * Every Supabase project's database is called `postgres`, and one project
 * answers to three endpoint spellings that reach IDENTICAL ROWS:
 *
 *   transaction pooler  <region>.pooler.supabase.com:6543  user postgres.<ref>
 *   session pooler      <region>.pooler.supabase.com:5432  user postgres.<ref>
 *   direct              db.<ref>.supabase.co:5432          user postgres
 *
 * Comparing {host, port, database} lets all three pairings through, so
 * DATABASE_URL_TEST on the session pooler and DATABASE_URL on the transaction
 * pooler is a run that truncates and drops against production. The same
 * comparison ALSO refuses two genuinely different projects on one pooler host,
 * which is the normal shape of a dedicated test project, so the old rule was
 * wrong in both directions at once.
 *
 * The project ref is what actually identifies the data, and it is present in
 * both spellings, in different places: the pooler puts it in the USERNAME, the
 * direct endpoint puts it in the HOSTNAME. That asymmetry is why
 * pooler-vs-direct, which shares neither host nor port, needs this and cannot
 * be closed by comparing ports.
 */
export type SupabaseIdentity =
  /** A Supabase endpoint whose project is known. */
  | { readonly kind: 'project'; readonly ref: string }
  /** A Supabase endpoint whose project could NOT be determined. */
  | { readonly kind: 'unidentified' }
  /** Not Supabase at all. */
  | { readonly kind: 'elsewhere' };

function isSupabaseHost(host: string): boolean {
  return (
    host === 'supabase.co' ||
    host === 'supabase.com' ||
    host.endsWith('.supabase.co') ||
    host.endsWith('.supabase.com')
  );
}

/**
 * The ref, or undefined. Both spellings are checked, username first.
 *
 * Deliberately NOT generalised. The username form is consulted only when the
 * host is Supabase-operated, so a local account that happens to contain a dot
 * (`app.service@localhost`) yields nothing and falls through to the ordinary
 * host/port/database comparison rather than inventing a ref that could collide
 * with a real project.
 *
 * A ref that does not match is not waved through: `supabaseIdentity` reports
 * `unidentified` for it, and the comparison refuses rather than guesses. So the
 * patterns here can afford to be strict.
 */
export function supabaseProjectRef(target: DatabaseTarget): string | undefined {
  if (!isSupabaseHost(target.host)) return undefined;

  // Pooler: `<dbuser>.<ref>`. Any db user, not just `postgres`.
  const fromUsername = /^[a-z0-9_-]+\.([a-z0-9]{6,})$/.exec(target.user);
  if (fromUsername !== null) return fromUsername[1];

  // Direct: `db.<ref>.supabase.co`.
  const fromHostname = /^db\.([a-z0-9]{6,})\.supabase\.(?:co|com)$/.exec(target.host);
  if (fromHostname !== null) return fromHostname[1];

  return undefined;
}

export function supabaseIdentity(target: DatabaseTarget): SupabaseIdentity {
  if (!isSupabaseHost(target.host)) return { kind: 'elsewhere' };

  const ref = supabaseProjectRef(target);
  return ref === undefined ? { kind: 'unidentified' } : { kind: 'project', ref };
}

/* ------------------------------------------------------------------ */
/* Comparison                                                          */
/* ------------------------------------------------------------------ */

/**
 * How two connection strings are related. `distinct` is the ONLY verdict that
 * permits a run; everything else refuses.
 */
export type TargetRelation =
  | 'same-string'
  | 'same-project'
  | 'same-target'
  | 'not-comparable'
  | 'distinct';

/**
 * FAILING CLOSED IS THE DEFAULT. Anything this function cannot understand
 * becomes `not-comparable`, which refuses, because the cost of refusing a safe
 * run is a developer reading an error message and the cost of permitting an
 * unsafe one is the production database. Malformed connection strings are the
 * ordinary case, not the exotic one: `.env.local` is hand-edited with real
 * credentials and a typo in it is exactly how this guard gets reached.
 */
export function relateTargets(a: string, b: string): TargetRelation {
  if (a.trim() === b.trim()) return 'same-string';

  let left: DatabaseTarget;
  let right: DatabaseTarget;
  try {
    left = describeTarget(a);
    right = describeTarget(b);
  } catch {
    // One of them could not be understood, so "different" is not something
    // that has been established. It is something that has not been checked.
    return 'not-comparable';
  }

  const leftId = supabaseIdentity(left);
  const rightId = supabaseIdentity(right);

  if (leftId.kind === 'project' && rightId.kind === 'project') {
    // The ref decides, and it decides BOTH ways: same ref is the same data
    // whichever endpoints were used, and different refs are different projects
    // even on one pooler host and port.
    return leftId.ref === rightId.ref ? 'same-project' : 'distinct';
  }

  if (leftId.kind !== 'elsewhere' && rightId.kind !== 'elsewhere') {
    // Both are Supabase and at least one project could not be named. There is
    // no honest way to call these different.
    return 'not-comparable';
  }

  // At most one side is Supabase, so a shared host is impossible and the
  // ordinary comparison is sound.
  return left.host === right.host && left.port === right.port && left.database === right.database
    ? 'same-target'
    : 'distinct';
}

/**
 * True when the two strings have NOT been shown to address different databases.
 * Note the double negative: this is deliberately not "they are the same", so
 * that an unparseable or ambiguous pair answers true and refuses the run.
 */
export function sameTarget(a: string, b: string): boolean {
  return relateTargets(a, b) !== 'distinct';
}

export type EnvLike = Readonly<Record<string, string | undefined>>;

/** One place `DATABASE_URL` was found, and where that was. */
export interface AppUrlCandidate {
  /** Human-readable origin, quoted back to the operator in the refusal. */
  readonly source: string;
  readonly url: string;
  /**
   * False when the value carries a dotenv reference this guard will not
   * interpret. Such a candidate is refused rather than compared: see
   * `hasUnexpandedReference`.
   */
  readonly resolved: boolean;
  /** True when the value came from a file that is normally committed. */
  readonly committed?: boolean;
}

/**
 * Reads `KEY=VALUE` pairs from a dotenv file WITHOUT touching `process.env`.
 *
 * The not-touching is the entire point, and it is why this uses
 * `util.parseEnv` rather than `process.loadEnvFile`. The obvious repair for the
 * bug described at the top of this file is "load `.env.local` in the global
 * setup too". That repair is worse than the bug. It would put the production
 * `DATABASE_URL` into the environment of every test worker, where
 * `@/server/db`'s `getDb()` reads exactly that variable, so a single test that
 * omitted `options.db` would open a live connection to production and the
 * suite's own `truncate` would be one typo away. Silently adopting a value
 * from a file is how this class of bug starts.
 *
 * So the file is read for COMPARISON ONLY. Nothing here is ever exported into
 * the process.
 *
 * A missing or unreadable file yields `{}`: not every developer has one, and
 * its absence is not a configuration error.
 */
/**
 * A byte-order mark, which `util.parseEnv` does NOT strip and Next's loader
 * does.
 *
 * Left in place it becomes part of the first key, so `.env.local` yields
 * `"﻿DATABASE_URL"` and a lookup of `DATABASE_URL` is `undefined`: the
 * candidate silently disappears and the guard permits a run it should refuse.
 * The application, loading the same file through Next, sees the variable
 * perfectly well.
 *
 * This is not exotic. PowerShell 5.1's `Out-File` and `Set-Content` write a BOM
 * by default, so it is the ordinary shape of a hand-written `.env.local` on
 * Windows, which is where this project's operator is.
 */
const BOM = '﻿';

/**
 * A value that still contains a dotenv variable reference.
 *
 * Matches `${...}` and `$NAME`, and deliberately not a bare `$` or `$$`, so an
 * ordinary password ending in a dollar sign is not caught.
 */
const UNEXPANDED_REFERENCE = /\$\{|\$[A-Za-z_]/;

export function hasUnexpandedReference(value: string): boolean {
  return UNEXPANDED_REFERENCE.test(value);
}

export function readEnvFile(path: string | URL): EnvLike {
  let contents: string;
  try {
    contents = readFileSync(path, 'utf8');
  } catch {
    return {};
  }

  try {
    return parseEnv(contents.startsWith(BOM) ? contents.slice(BOM.length) : contents);
  } catch {
    // Unparseable. Treated as empty here; if it held a DATABASE_URL, the
    // caller is left comparing against one fewer candidate, which is why
    // `appUrlCandidates` reports the file as unreadable rather than silent.
    return {};
  }
}

/**
 * Every place `DATABASE_URL` could be configured, in the order an operator
 * would expect them to win.
 *
 * `.env.local` is included because that is what `.env.example`,
 * `scripts/db-check.ts` and `@/server/db`'s own error message all tell the
 * operator to write. A guard that reads only `process.env` is a guard aimed at
 * a configuration nobody was told to create.
 */
export function appUrlCandidates(
  env: EnvLike,
  files: readonly (string | URL)[] = [],
): readonly AppUrlCandidate[] {
  const found: AppUrlCandidate[] = [];

  const fromEnvironment = env[APP_URL_VAR]?.trim() ?? '';
  if (fromEnvironment !== '') {
    // Already final: whatever expanded it did so before the process started.
    found.push({
      source: `${APP_URL_VAR} in the environment`,
      url: fromEnvironment,
      resolved: true,
    });
  }

  for (const file of files) {
    const url = readEnvFile(file)[APP_URL_VAR]?.trim() ?? '';
    if (url !== '') {
      const name = describeFile(file);
      found.push({
        source: `${APP_URL_VAR} in ${name}`,
        url,
        resolved: !hasUnexpandedReference(url),
        committed: name === '.env',
      });
    }
  }

  return found;
}

function describeFile(file: string | URL): string {
  const path = typeof file === 'string' ? file : fileURLToPath(file);
  return basename(path);
}

/**
 * The guard. Returns the URL the suite may use, or throws with a message that
 * says what to do rather than what went wrong.
 *
 * Pure, so the unit lane can prove it. `candidates` defaults to empty, so the
 * single-argument form still means "compare against the environment only"; the
 * bootstrap passes the full set gathered from disk as well.
 */
export function resolveTestDatabaseUrl(
  env: EnvLike,
  candidates: readonly AppUrlCandidate[] = [],
): string {
  const testUrl = env[TEST_URL_VAR]?.trim() ?? '';

  if (testUrl === '') {
    throw new TestDatabaseConfigError(
      `${TEST_URL_VAR} is not set, so the database tests have nowhere safe to run.\n` +
        `There is deliberately NO fallback to ${APP_URL_VAR}: a silent fallback is how a ` +
        'test run ends up truncating the demo data.\n' +
        'Start one with `docker compose up -d db` and set, in .env.test.local:\n' +
        `  ${TEST_URL_VAR}=postgresql://hotshop:hotshop@localhost:55432/hotshop_test\n` +
        'Any PostgreSQL you can reach works just as well; Docker is only a convenience.',
    );
  }

  // Validated BEFORE the comparison, so a malformed test URL is reported as
  // malformed rather than as "could not be compared", which would be true but
  // unhelpful.
  describeTarget(testUrl);

  // `appUrlCandidates` already includes the environment, so the single-argument
  // form synthesises it here and the two-argument form does not, rather than
  // comparing an exported DATABASE_URL twice.
  const fromEnvironment = env[APP_URL_VAR]?.trim() ?? '';
  const everyCandidate: AppUrlCandidate[] =
    candidates.length > 0
      ? [...candidates]
      : fromEnvironment === ''
        ? []
        : [
            {
              source: `${APP_URL_VAR} in the environment`,
              url: fromEnvironment,
              resolved: true,
            },
          ];

  const DESTRUCTIVE =
    'The database tests create, truncate and drop. Pointing them at the application ' +
    'database would destroy it.';

  // EVERY candidate, not the first one found. An operator with DATABASE_URL
  // exported in the shell AND a different one in .env.local has two chances to
  // be pointing at production, and the guard has to check both.
  const COMMITTED_WARNING =
    '\nSeparately, and worth more than the quickest unblock: a real ' +
    `${APP_URL_VAR} in a committed \`.env\` is a credential in version control. ` +
    'Deleting the line to get past this message leaves it in the git history, where ' +
    'it stays readable and has to be rotated. Move it to `.env.local`, which is ' +
    'gitignored, and rotate the password.';

  for (const candidate of everyCandidate) {
    const committedNote = candidate.committed === true ? COMMITTED_WARNING : '';

    if (!candidate.resolved) {
      // Next expands `${...}` and `$NAME` through dotenv-expand; this guard
      // does not reimplement that, because a reimplementation that is subtly
      // different is worse than no comparison at all. So it refuses.
      throw new TestDatabaseConfigError(
        `${candidate.source} contains a variable reference this guard will not ` +
          `interpret, so this run is refused.\n` +
          DESTRUCTIVE +
          '\nNext expands references like ${DB_HOST} when it loads that file, so the ' +
          'application and this check would be looking at two different connection ' +
          'strings, and only one of them is the one that gets written to.\n' +
          `Write ${APP_URL_VAR} out in full in that file, or export it in the ` +
          'environment instead.' +
          committedNote,
      );
    }

    switch (relateTargets(testUrl, candidate.url)) {
      case 'distinct':
        continue;

      case 'same-project':
        throw new TestDatabaseConfigError(
          `${TEST_URL_VAR} and ${candidate.source} are two endpoints of the SAME Supabase ` +
            `project, so this run is refused.\n` +
            DESTRUCTIVE +
            '\nA project’s transaction pooler, session pooler and direct connection differ ' +
            'in host and port but reach identical rows, and every project’s database is ' +
            'named `postgres`, so neither of those tells them apart.\n' +
            `Point ${TEST_URL_VAR} at a SEPARATE Supabase project, or at a local PostgreSQL.` +
            committedNote,
        );

      case 'not-comparable':
        throw new TestDatabaseConfigError(
          `${TEST_URL_VAR} and ${candidate.source} could not be compared, so this run is ` +
            `refused.\n` +
            DESTRUCTIVE +
            '\nRefusing is the deliberate answer here: one of the two could not be understood, ' +
            'which means they have not been shown to be different databases, only that the ' +
            'check could not be made.\n' +
            'Check both for typos, and that each is a full connection URL of the form ' +
            'postgresql://user:password@host:5432/database.' +
            committedNote,
        );

      default:
        throw new TestDatabaseConfigError(
          `${TEST_URL_VAR} points at the same database as ${candidate.source}, so this run ` +
            `is refused.\n` +
            DESTRUCTIVE +
            `\nGive ${TEST_URL_VAR} a separate database (a different name on the same server ` +
            `is enough).` +
            committedNote,
        );
    }
  }

  return testUrl;
}

/* ------------------------------------------------------------------ */
/* Per-run database lifecycle                                          */
/* ------------------------------------------------------------------ */

/**
 * The fixture schema.
 *
 * `offers_fixture` MIRRORS the shape the marketplace will use, and exists ONLY
 * to prove the 23505 -> refusal MAPPING in `@/server/db`. It is NOT the
 * marketplace invariant. FR-1709 ("at most one pending offer per buyer per
 * listing") is verified when the real `offers` table lands in INV-07, with a
 * concurrent test against that table. A green suite here says the plumbing
 * carries a unique violation safely; it says nothing about whether the real
 * offers table has the index.
 */
export const FIXTURE_DDL = `
  create table if not exists offers_fixture (
    id            bigint generated always as identity primary key,
    buyer         text    not null,
    listing       text    not null,
    status        text    not null,
    amount_cents  integer not null
  );

  create unique index if not exists offers_fixture_one_pending_per_buyer_listing
    on offers_fixture (buyer, listing)
    where status = 'PENDING';

  -- A SECOND table with a unique constraint of its own, carrying no relation to
  -- offers. It exists so the suite can ask what happens when a transaction
  -- raises a 23505 the caller's mapper was never written for.
  --
  -- This is not hypothetical. TD-02a's four marketplace operations are all
  -- multi-write, so a transaction will routinely touch a table the mapper does
  -- not know about. Without a way to decline, the offers mapper answers for
  -- this table too and the user reads a fabricated message about an offer.
  create table if not exists unrelated_fixture (
    id    integer primary key,
    label text not null
  );

  -- INV17-B9, the actor seam.
  --
  -- Two tables, because the seam's hardest claim (TD-12) is about the
  -- relationship between a PERMISSION READ and the WRITES it guards, which
  -- needs the permission to live somewhere a second transaction can revoke it.
  --
  -- listings_fixture is the thing written. listing_grants_fixture is the
  -- permission. Neither is the marketplace schema: owner is a bare text
  -- column rather than a foreign key to an accounts table, because no accounts
  -- table exists yet and the seam does not need one. What these prove is that
  -- the HANDLE carries an actor into the SQL and that a revoke cannot slip
  -- between the check and the write. What the real tables are called, and
  -- whether the grant is a row or a column, is Slice C's business.
  create table if not exists listings_fixture (
    id    text primary key,
    owner text not null,
    title text not null
  );

  create table if not exists listing_grants_fixture (
    user_id    text not null,
    listing_id text not null,
    primary key (user_id, listing_id)
  );
`;

export const FIXTURE_TABLES = [
  'offers_fixture',
  'unrelated_fixture',
  'listings_fixture',
  'listing_grants_fixture',
] as const;

function runDatabaseName(): string {
  const stamp = Date.now().toString(36);
  const salt = Math.random().toString(36).slice(2, 8);
  return `hotshop_test_${stamp}_${salt}`;
}

function withDatabase(raw: string, database: string): string {
  const url = new URL(raw);
  url.pathname = `/${database}`;
  return url.toString();
}

export interface ProvisionedDatabase {
  /** The URL every integration test should connect to. */
  readonly url: string;
  /** False when the per-run database could not be created and we fell back. */
  readonly isolated: boolean;
  readonly teardown: () => Promise<void>;
}

/**
 * Creates a database for THIS RUN, applies the fixture schema, and hands back a
 * teardown that drops it.
 *
 * WHEN `create database` IS REFUSED, SO IS THE RUN, unless the operator has
 * explicitly opted in by setting `HOTSHOP_TEST_ALLOW_SHARED_DATABASE=1`.
 *
 * This used to print a warning and carry on. That was the second fail-open in
 * this file and it is the path that reached the reviewer's decoy: with a role
 * lacking CREATEDB, the 42501 branch ran `FIXTURE_DDL`, a `truncate ...
 * restart identity cascade` between every test and a `drop table ... cascade`
 * at teardown, all inside the database named by `DATABASE_URL`, and reported
 * 95 of 95 green with one warning line scrolled off the top.
 *
 * A `console.warn` is not a control. It has no effect on what happens next, it
 * is invisible in CI logs that only surface failures, and it asks a human to
 * notice something at the exact moment they are least likely to be watching.
 * The policy everywhere else in this file is refuse-when-uncertain, and
 * "destructive DDL in a database I did not create" is the most uncertain thing
 * the suite does.
 *
 * The opt-in exists because the shared-database mode is genuinely needed on a
 * managed instance that cannot grant CREATEDB. Putting it in configuration
 * makes it a decision someone wrote down, which a warning never was.
 */
export const ALLOW_SHARED_VAR = 'HOTSHOP_TEST_ALLOW_SHARED_DATABASE';

/** SQLSTATEs meaning "this server will not let you create a database". */
const CANNOT_CREATE_DATABASE = new Set([
  '42501', // insufficient_privilege
  '25006', // read_only_sql_transaction
  '0A000', // feature_not_supported (poolers reject CREATE DATABASE outright)
]);

/**
 * @see the note above on why the no-CREATEDB path refuses rather than warns.
 */
export async function provisionRunDatabase(
  baseUrl: string,
  env: EnvLike = {},
): Promise<ProvisionedDatabase> {
  const name = runDatabaseName();
  const admin = postgres(baseUrl, { max: 1, prepare: false, onnotice: () => {} });

  let created = false;
  try {
    await admin.unsafe(`create database "${name}"`);
    created = true;
  } catch (error) {
    const code = (error as { code?: string }).code ?? '';
    if (!CANNOT_CREATE_DATABASE.has(code)) {
      await admin.end();
      throw error;
    }

    if ((env[ALLOW_SHARED_VAR] ?? '').trim() !== '1') {
      await admin.end();
      throw new TestDatabaseConfigError(
        `A database could not be created for this run (SQLSTATE ${code}), so the run is ` +
          `refused.\n` +
          'Without one, the suite would create its fixture tables, truncate between every ' +
          `test and drop tables at the end INSIDE the database ${TEST_URL_VAR} names. That ` +
          'is only safe if that database exists to be destroyed.\n' +
          'Either grant the account CREATEDB, which is the recommended fix, or point ' +
          `${TEST_URL_VAR} at a throwaway database and set ${ALLOW_SHARED_VAR}=1 to confirm ` +
          'you accept that everything in it is fair game.',
      );
    }

    console.warn(
      `[test-database] Could not create a per-run database (SQLSTATE ${code}). ` +
        `${ALLOW_SHARED_VAR}=1 is set, so the run continues inside the database ` +
        `${TEST_URL_VAR} names. It is NOT isolated from anything else using that database.`,
    );
  }

  const url = created ? withDatabase(baseUrl, name) : baseUrl;
  const target = postgres(url, { max: 1, prepare: false, onnotice: () => {} });

  try {
    await target.unsafe(FIXTURE_DDL);
  } catch (error) {
    // `admin` is otherwise only closed by `teardown`, which never runs if we
    // fail here, and an open handle hangs the whole run at exit.
    await admin.end();
    throw error;
  } finally {
    await target.end();
  }

  return {
    url,
    isolated: created,
    teardown: async () => {
      try {
        if (created) {
          // WITH (FORCE) terminates leftover backends. Available since
          // PostgreSQL 13, so it is safe on both 16 here and 17 on Supabase.
          await admin.unsafe(`drop database if exists "${name}" with (force)`);
        } else {
          const scratch = postgres(url, { max: 1, prepare: false, onnotice: () => {} });
          try {
            await scratch.unsafe(`drop table if exists ${FIXTURE_TABLES.join(', ')} cascade`);
          } finally {
            await scratch.end();
          }
        }
      } finally {
        await admin.end();
      }
    },
  };
}

/** Between tests. Cheap, and leaves the schema in place. */
export const TRUNCATE_FIXTURES = `truncate table ${FIXTURE_TABLES.join(', ')} restart identity cascade`;

/* ------------------------------------------------------------------ */
/* Waiting, without sleeping                                           */
/* ------------------------------------------------------------------ */

export interface Deferred<T> {
  readonly promise: Promise<T>;
  resolve(value: T): void;
  reject(reason: unknown): void;
}

/**
 * A one-shot barrier. Two transactions coordinate through these rather than
 * through timers, so "A has inserted" and "release A" are facts rather than
 * guesses about how long a round trip takes.
 */
export function deferred<T = void>(): Deferred<T> {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/**
 * Polls until `condition` holds. Used instead of a sleep so the concurrency
 * test is deterministic on a slow machine and fast on a quick one.
 */
export async function waitFor(
  condition: () => Promise<boolean>,
  { timeoutMs = 10_000, intervalMs = 20, what = 'condition' } = {},
): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    if (await condition()) return;
    if (Date.now() > deadline) {
      throw new Error(`Timed out after ${timeoutMs}ms waiting for ${what}.`);
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
}

/**
 * True once at least `count` backends on this database are blocked waiting for
 * a lock held by another backend. This is how the concurrency test knows the
 * second INSERT is genuinely in flight and parked on the unique index, rather
 * than guessing with a timer.
 *
 * `pg_blocking_pids` has existed since PostgreSQL 9.6, so this reads the same
 * on 16 and on 17.
 */
export async function blockedBackendCount(sql: postgres.Sql): Promise<number> {
  const rows = await sql<{ blocked: number }[]>`
    select count(*)::int as blocked
    from pg_stat_activity
    where datname = current_database()
      and cardinality(pg_blocking_pids(pid)) > 0
  `;
  return rows[0]?.blocked ?? 0;
}
