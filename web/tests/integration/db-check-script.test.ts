/**
 * `npm run db:check` — the "are my Supabase credentials right" script.
 *
 * ---------------------------------------------------------------------------
 * CONTRACT FOR THE IMPLEMENTER — `scripts/db-check.ts`
 * ---------------------------------------------------------------------------
 *
 * package.json already carries:
 *
 *   "db:check": "node scripts/db-check.ts"
 *
 * Node 22 strips TypeScript types natively, so there is no build step and no
 * extra dependency.
 *
 * Behaviour:
 *   1. If DATABASE_URL is not already in the environment, load `.env.local`
 *      from the project root (`process.loadEnvFile`). This is the whole point
 *      of the script: the credentials the user just pasted live in that file,
 *      and asking them to also export a shell variable to check it is asking
 *      them to test the wrong thing. An existing environment variable always
 *      wins, so CI stays authoritative. A missing file is not an error.
 *
 *      A GOTCHA worth stating, because it costs an hour otherwise:
 *      `process.loadEnvFile` will not overwrite a variable that already exists,
 *      and an exported-but-empty `DATABASE_URL=` counts as existing. Treat a
 *      blank value as unset and `delete process.env.DATABASE_URL` before
 *      loading the file, or the file is silently ignored. Pinned below.
 *
 *      `DB_CHECK_ENV_FILE` overrides which file that is. It exists for two
 *      reasons: checking a second environment (`.env.staging.local`) without
 *      editing anything, and letting the test below reach the "nothing is
 *      configured" branch on a machine that does have a `.env.local`. Pointing
 *      it at a path that does not exist disables the fallback.
 *   2. If DATABASE_URL is still unset or blank: print a message naming
 *      DATABASE_URL and `.env.local`, and exit with a NON-ZERO status.
 *   3. Otherwise connect with postgres.js directly, `prepare: false` as
 *      everywhere else, and run `select version()`.
 *
 *      Note on why it does NOT import `@/server/db`: node runs this file
 *      without the `@/` alias, which only Vite and tsc know about, and the
 *      module graph behind that alias would have to resolve for the script to
 *      start. A credentials checker that cannot run until the application
 *      compiles is useless at exactly the moment it is wanted. The cost is one
 *      duplicated `prepare: false`, which is cheap; the connection module's own
 *      copy is pinned twice in `db-connection.test.ts`.
 *   4. On success: print the server version on stdout and exit 0.
 *   5. On failure: print a diagnosis on stderr and exit NON-ZERO. The output
 *      must not contain the password from the connection string. A connection
 *      check is the most likely thing a developer pastes into a chat window
 *      when it goes wrong.
 *   6. Close the client either way, so the command does not hang.
 *
 * Use a short connect timeout (around 10 seconds) so a wrong host fails rather
 * than appearing to work slowly.
 *
 * ---------------------------------------------------------------------------
 * HOW MUCH OF THIS IS TESTABLE, HONESTLY
 * ---------------------------------------------------------------------------
 *
 * The exit status, the presence of a version string, and the absence of the
 * password are mechanical and pinned below against the real PostgreSQL this
 * suite already runs.
 *
 * The VALUE of the script is operational and is NOT pinned here: whether the
 * user's Supabase credentials are correct can only be learned by pointing it at
 * Supabase. No test in this repository can tell them that, and a test that
 * mocked the connection would assert that the mock works. So the script is
 * covered as a command (does it run, does it report, does it leak) and trusted
 * as a diagnostic.
 */

import { execFile } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';

import { startFakePostgres, type FakePostgres } from '../helpers/fake-postgres';
import { testDatabaseUrl } from '../setup/integration-setup';

const run = promisify(execFile);
const projectRoot = fileURLToPath(new URL('../..', import.meta.url));

interface Outcome {
  readonly code: number;
  readonly stdout: string;
  readonly stderr: string;
}

async function dbCheck(env: Record<string, string | undefined>): Promise<Outcome> {
  // npm supplies its CLI path; invoking it with Node also works without a shell on Windows.
  const npmCli = process.env.npm_execpath;
  if (!npmCli) throw new Error('Run this suite through npm run test:integration.');
  try {
    const { stdout, stderr } = await run(process.execPath, [npmCli, 'run', '--silent', 'db:check'], {
      cwd: projectRoot,
      env: { ...process.env, ...env },
      timeout: 30_000,
    });
    return { code: 0, stdout, stderr };
  } catch (error) {
    const failure = error as { code?: number; stdout?: string; stderr?: string };
    return {
      code: typeof failure.code === 'number' ? failure.code : 1,
      stdout: failure.stdout ?? '',
      stderr: failure.stderr ?? '',
    };
  }
}

/** A path that does not exist, which switches the env-file fallback off. */
const NO_ENV_FILE = fileURLToPath(new URL('../../.env.absent-on-purpose', import.meta.url));

/** Port 5434 has nothing listening: a refused connection, not a timeout. */
const UNREACHABLE = 'postgresql://hotshop:hunter2@127.0.0.1:5434/postgres';

/**
 * Malformed connection strings, each carrying the same recognisable password.
 *
 * `.env.local` is a file a user hand-edits with real credentials, so a typo in
 * it is the ORDINARY case, not an exotic one. Every password test above uses
 * the well-formed UNREACHABLE constant, so none of them reaches the path a typo
 * actually takes.
 *
 * Two distinct ways the secret escapes, both pinned below:
 *
 *   1. The client is constructed before anything can catch it, so postgres.js
 *      throws `Invalid URL` straight out of the process. Node prints the whole
 *      error, and its `input:` field is the connection string, password and
 *      all, followed by a driver stack.
 *   2. The URL parses but not as intended, leaving `password` empty, so the
 *      redactor is the identity function. The credentials then ride out inside
 *      whatever the script prints about the target.
 */
const SECRET = 'SUPERSECRET';
const MALFORMED = {
  /** A stray space. Does not parse at all. */
  spaceInScheme: `postgres ql://user:${SECRET}@host:5432/db`,
  /** The slashes dropped. Parses as an opaque path, so `password` is empty. */
  missingSlashes: `postgresql:user:${SECRET}@host:5432/db`,
  /**
   * An unencoded `@` inside the password. Pointed at 127.0.0.1:5434, where
   * UNREACHABLE already relies on nothing listening, because this is the only
   * one of the four that parses cleanly enough to reach the network: with a
   * made-up hostname it was really testing whatever the machine's resolver
   * does with an unknown name, which differs between a laptop, CI and a
   * container behind a wildcard DNS.
   */
  atInPassword: `postgresql://user:SUPER@${SECRET}@127.0.0.1:5434/db`,
  /** An unencoded `/` inside the password. */
  slashInPassword: `postgresql://user:SUPER/${SECRET}@host:5432/db`,
  /**
   * A malformed percent escape. This one parses, passes the script's own
   * validation, and then makes `postgres()` throw `URIError: URI malformed`
   * from `decodeURIComponent` deep inside the driver.
   *
   * It is here because it DISCRIMINATES. Moving the `postgres(...)` call back
   * outside the try block left 311 of 311 passing: the placement was
   * load-bearing and nothing tested it. This fixture is the test.
   */
  badPercentEscape: `postgresql://user:SUPER%GG${SECRET}@host:5432/db`,
} as const;

/**
 * "Exits non-zero" and "does not print the password" are both true of a script
 * that does not exist, so every failure-path test also asserts the command
 * actually reached the script. Without this, four of these tests would go green
 * before a line of `scripts/db-check.ts` was written.
 */
function expectTheScriptRan(result: Outcome): void {
  const output = `${result.stdout}${result.stderr}`;
  expect(output).not.toMatch(/Cannot find module|MODULE_NOT_FOUND|missing script/i);
}

let url: string;
/** The same reachable database, but with a password the output must not echo. */
let urlWithPassword: string;
let successPassword: string;

beforeAll(() => {
  url = testDatabaseUrl();
  const withPassword = new URL(url);
  if (!withPassword.password) withPassword.password = 'hunter2';
  successPassword = decodeURIComponent(withPassword.password);
  urlWithPassword = withPassword.toString();
});

describe('npm run db:check', () => {
  it('exits zero and prints the server version for a reachable database', async () => {
    const result = await dbCheck({ DATABASE_URL: url });

    expect(result.code).toBe(0);
    expect(result.stdout).toMatch(/PostgreSQL\s+\d+/i);
  });

  it('exits non-zero when the database cannot be reached', async () => {
    const result = await dbCheck({ DATABASE_URL: UNREACHABLE });

    expectTheScriptRan(result);
    expect(result.code).not.toBe(0);
  });

  it('says something about what failed rather than exiting silently', async () => {
    const result = await dbCheck({ DATABASE_URL: UNREACHABLE });

    expectTheScriptRan(result);
    expect(`${result.stdout}${result.stderr}`.trim()).not.toBe('');
  });

  it('never echoes the password back when the connection fails', async () => {
    const result = await dbCheck({ DATABASE_URL: UNREACHABLE });

    expectTheScriptRan(result);
    expect(`${result.stdout}${result.stderr}`).not.toContain('hunter2');
  });

  it('never echoes the password back when the connection succeeds', async () => {
    const result = await dbCheck({ DATABASE_URL: urlWithPassword });

    expect(result.code).toBe(0);
    // Compare booleans so a failed assertion cannot print the configured secret.
    expect(`${result.stdout}${result.stderr}`.includes(successPassword)).toBe(false);
  });

  it('exits non-zero and names DATABASE_URL when it is malformed', async () => {
    const result = await dbCheck({ DATABASE_URL: MALFORMED.spaceInScheme });

    expectTheScriptRan(result);
    expect(result.code).not.toBe(0);
  });

  it('exits non-zero and names DATABASE_URL when nothing is configured', async () => {
    const result = await dbCheck({ DATABASE_URL: '', DB_CHECK_ENV_FILE: NO_ENV_FILE });

    expectTheScriptRan(result);
    expect(result.code).not.toBe(0);
    expect(`${result.stdout}${result.stderr}`).toContain('DATABASE_URL');
  });

  /**
   * One block, four malformations, four properties each. Written as a loop so a
   * new malformation inherits every property rather than being pinned for
   * whichever one its author remembered.
   */
  describe.each(Object.entries(MALFORMED))('a malformed DATABASE_URL (%s)', (_name, url) => {
    it('exits non-zero', async () => {
      const result = await dbCheck({ DATABASE_URL: url });

      expectTheScriptRan(result);
      expect(result.code).not.toBe(0);
    });

    it('says what is wrong rather than crashing out of the process', async () => {
      const result = await dbCheck({ DATABASE_URL: url });
      const output = `${result.stdout}${result.stderr}`;

      // A thrown-and-unhandled error is not a diagnosis. The user should read
      // that DATABASE_URL could not be understood, not a Node crash dump.
      expect(output).toContain('DATABASE_URL');
      expect(output).not.toMatch(/^\s*node:internal/m);
    });

    it('does not print the password', async () => {
      const result = await dbCheck({ DATABASE_URL: url });

      expect(`${result.stdout}${result.stderr}`).not.toContain(SECRET);
    });

    it('does not print the raw connection string or a driver stack frame', async () => {
      const result = await dbCheck({ DATABASE_URL: url });
      const output = `${result.stdout}${result.stderr}`;

      // The raw string carries the password even when the password itself has
      // been scrubbed from the message, and a stack frame points into
      // node_modules, which tells the user nothing they can act on.
      expect(output).not.toContain(url);
      expect(output).not.toMatch(/node_modules|postgres\/src\/index\.js|\bat new URL\b/);
    });
  });

  /**
   * The script's TLS rule, pinned off the wire.
   *
   * `scripts/db-check.ts` duplicates the TLS decision from `@/server/db`,
   * because it imports postgres.js directly rather than going through the `@/`
   * alias. Nothing tested that copy: making `sslModeFor` return `undefined`
   * unconditionally, removing TLS from the script entirely, left 350 of 350
   * green, while the same mutation in the module fails four tests.
   *
   * This is the one command the operator points at real Supabase credentials,
   * and it exists to be run when something is already wrong. A plaintext
   * default here sends the password they are trying to verify across the
   * internet in the clear.
   *
   * BEHAVIOURAL, and it is worth saying how, because the module's equivalent
   * pin had to settle for structural. The script is a separate process, so its
   * options cannot be read; what it puts on the socket can. A fake server
   * answers the PostgreSQL SSLRequest handshake and records whether one
   * arrived. See `tests/helpers/fake-postgres.ts` for the eight bytes involved
   * and for why the host is `127.0.0.2`.
   */
  describe('TLS', () => {
    let fake: FakePostgres;

    beforeEach(async () => {
      fake = await startFakePostgres();
    });

    afterEach(async () => {
      await fake.close();
    });

    function urlFor(host: string, port: number, query = ''): string {
      return `postgresql://user:${SECRET}@${host}:${port}/hotshop${query}`;
    }

    it('asks for TLS before sending credentials to a non-loopback host', async () => {
      await dbCheck({ DATABASE_URL: urlFor(fake.host, fake.port) });

      expect(fake.connected(), 'the script never reached the server').toBe(true);
      expect(
        fake.requestedTls(),
        'the script opened the connection without requesting TLS, so the password ' +
          'would cross the network in cleartext',
      ).toBe(true);
    });

    it('gives up rather than falling back to plaintext when TLS is refused', async () => {
      // The fake answers 'N'. A client that merely preferred TLS would carry
      // on unencrypted; one that requires it must stop.
      const result = await dbCheck({ DATABASE_URL: urlFor(fake.host, fake.port) });

      expect(result.code).not.toBe(0);
      expect(`${result.stdout}${result.stderr}`).not.toContain(SECRET);
    });

    it('does not require TLS for a loopback host, so local development works', async () => {
      // The over-correction guard. 127.0.0.1 is the shape of every local
      // PostgreSQL, including the one this suite runs against.
      const local = await startFakePostgres({ host: '127.0.0.1' });
      try {
        await dbCheck({ DATABASE_URL: urlFor(local.host, local.port) });

        expect(local.connected()).toBe(true);
        expect(local.requestedTls()).toBe(false);
      } finally {
        await local.close();
      }
    });

    it('still honours an explicit sslmode=disable in the URL', async () => {
      // The documented escape hatch has to keep working, or the rule becomes
      // something people work around by other means.
      await dbCheck({ DATABASE_URL: urlFor(fake.host, fake.port, '?sslmode=disable') });

      expect(fake.connected()).toBe(true);
      expect(fake.requestedTls()).toBe(false);
    });
  });

  it('reads DATABASE_URL from the env file when the environment has none', async () => {
    // Proves the fallback that makes the script useful right after the user
    // pastes credentials into .env.local.
    const result = await dbCheck({ DATABASE_URL: '', DB_CHECK_ENV_FILE: envFileWith(url) });

    expect(result.code).toBe(0);
    expect(result.stdout).toMatch(/PostgreSQL\s+\d+/i);
  });
});

/** Writes a throwaway env file under the OS temp dir and returns its path. */
function envFileWith(databaseUrl: string): string {
  const path = join(tmpdir(), `hotshop-db-check-${process.pid}-${Date.now()}.env`);
  writeFileSync(path, `DATABASE_URL=${databaseUrl}\n`, 'utf8');
  return path;
}
