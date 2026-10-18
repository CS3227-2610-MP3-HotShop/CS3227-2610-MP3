/**
 * THE TEST THAT WAS MISSING.
 *
 * `tests/config/test-database-isolation.test.ts` has 48 tests proving the
 * comparison is right. Every one of them passed while the comparison was never
 * reached, because `DATABASE_URL` was read from `process.env` and nothing in
 * the test process ever loaded `.env.local` — which is the one place
 * `.env.example`, `scripts/db-check.ts` and `@/server/db`'s own error message
 * all tell the operator to put it.
 *
 * The reviewer's demonstration: a decoy database, `.env.local` naming it, and
 * `.env.test.local` set to the IDENTICAL string. The integration lane ran 95 of
 * 95 green with no refusal.
 *
 * So these tests do not exercise the comparison. They exercise the BOOTSTRAP,
 * from files on disk, through the real `setup` function, to the verdict it
 * hands the workers. A logic test cannot catch a wiring bug; only something
 * that starts where the operator starts can.
 *
 * They need no database: every case here is refused before anything connects,
 * which is the property under test.
 */

import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { appUrlCandidates, hasUnexpandedReference, readEnvFile } from '../helpers/test-database';
import { setup, type TestDatabaseState } from '../setup/integration-global-setup';

const PROD = 'postgresql://app:pw@db.example.com:5432/hotshop';
const SAFE = 'postgresql://hotshop:pw@localhost:55432/hotshop_test';

let root: string;
let rootUrl: URL;

beforeEach(() => {
  root = mkdtempSync(join(tmpdir(), 'hotshop-bootstrap-'));
  // pathToFileURL on the directory gives a trailing slash, so `new URL` inside
  // the setup resolves `.env.local` as a child rather than a sibling.
  rootUrl = pathToFileURL(join(root, '/'));
});

afterEach(() => {
  rmSync(root, { recursive: true, force: true });
});

function writeEnvFile(name: string, contents: string): void {
  writeFileSync(join(root, name), contents, 'utf8');
}

/** Runs the real global setup and returns the verdict it provided. */
async function runSetup(env: Record<string, string | undefined>): Promise<TestDatabaseState> {
  const provide = vi.fn();
  await setup({ provide }, { root: rootUrl, env });

  expect(provide).toHaveBeenCalledTimes(1);
  expect(provide.mock.calls[0]![0]).toBe('testDatabase');
  return provide.mock.calls[0]![1] as TestDatabaseState;
}

function reason(state: TestDatabaseState): string {
  if (state.ok) throw new Error('expected the bootstrap to refuse, but it permitted the run');
  return state.reason;
}

describe('the bootstrap reads DATABASE_URL from .env.local', () => {
  it('refuses when .env.local names the same database as DATABASE_URL_TEST', async () => {
    // The exact scenario that ran 95 of 95 green.
    writeEnvFile('.env.local', `DATABASE_URL=${PROD}\n`);

    const state = await runSetup({ DATABASE_URL_TEST: PROD });

    expect(state.ok).toBe(false);
    expect(reason(state)).toMatch(/refused/i);
  });

  it('names .env.local as the source, so the operator knows which file to edit', async () => {
    writeEnvFile('.env.local', `DATABASE_URL=${PROD}\n`);

    const state = await runSetup({ DATABASE_URL_TEST: PROD });

    expect(reason(state)).toContain('.env.local');
  });

  it('refuses a .env.local sibling Supabase endpoint, not just an identical string', async () => {
    // The project-ref work is worth nothing if it is not reached from the file
    // the operator actually writes.
    const POOLER = 'aws-0-ap-southeast-1.pooler.supabase.com';
    writeEnvFile(
      '.env.local',
      `DATABASE_URL=postgresql://postgres.abcdefghijklmnop:pw@${POOLER}:6543/postgres\n`,
    );

    const state = await runSetup({
      DATABASE_URL_TEST: 'postgresql://postgres:pw@db.abcdefghijklmnop.supabase.co:5432/postgres',
    });

    expect(reason(state)).toMatch(/same supabase project/i);
  });

  it('also reads a plain .env', async () => {
    writeEnvFile('.env', `DATABASE_URL=${PROD}\n`);

    const state = await runSetup({ DATABASE_URL_TEST: PROD });

    // Asserting the REASON, not just `ok: false`. An unreachable host also
    // produces `ok: false`, from the provisioning step, long after the guard
    // was supposed to have stopped the run.
    expect(reason(state)).toMatch(/refused/i);
    expect(reason(state)).toContain('.env');
  });

  it('checks EVERY candidate, not just the first one it finds', async () => {
    // A shell export that looks safe, and a file that does not. Stopping at the
    // environment would permit this.
    writeEnvFile('.env.local', `DATABASE_URL=${PROD}\n`);

    const state = await runSetup({
      DATABASE_URL: 'postgresql://app:pw@somewhere-else.example.com:5432/other',
      DATABASE_URL_TEST: PROD,
    });

    expect(reason(state)).toContain('.env.local');
  });

  it('is not fooled by a .env.local that differs only in query string', async () => {
    writeEnvFile('.env.local', `DATABASE_URL=${PROD}?sslmode=require\n`);

    const state = await runSetup({ DATABASE_URL_TEST: PROD });

    expect(reason(state)).toMatch(/refused/i);
    expect(reason(state)).toContain('.env.local');
  });
});

/**
 * The guard must see what the APPLICATION sees.
 *
 * Both failures below are the same shape: the file parses, the application gets
 * a `DATABASE_URL` from it, and the guard gets something else or nothing. The
 * run is then permitted against the very database the variable names.
 */
describe('a byte-order mark does not blind the guard', () => {
  // `util.parseEnv` does not strip a BOM; Next's loader does. With one left in
  // place the key becomes "﻿DATABASE_URL", the lookup misses, and the
  // candidate silently disappears.
  //
  // PowerShell 5.1's Out-File and Set-Content write a BOM by default, so on the
  // machine this project is developed on, this is the ORDINARY shape of a
  // hand-written .env.local, not an exotic one.
  const BOM = '﻿';

  it('still refuses when .env.local starts with a BOM', async () => {
    writeEnvFile('.env.local', `${BOM}DATABASE_URL=${PROD}\n`);

    const state = await runSetup({ DATABASE_URL_TEST: PROD });

    expect(reason(state)).toMatch(/refused/i);
    expect(reason(state)).toContain('.env.local');
  });

  it('still refuses with a BOM and Windows line endings together', async () => {
    writeEnvFile('.env.local', `${BOM}DATABASE_URL=${PROD}\r\n`);

    const state = await runSetup({ DATABASE_URL_TEST: PROD });

    expect(reason(state)).toMatch(/refused/i);
  });

  it('reads the value rather than a key with three stray bytes in front of it', () => {
    writeEnvFile('.env.local', `${BOM}DATABASE_URL=${PROD}\n`);

    const parsed = readEnvFile(join(root, '.env.local'));

    expect(parsed.DATABASE_URL).toBe(PROD);
    expect(Object.keys(parsed)).toEqual(['DATABASE_URL']);
  });

  it('does not corrupt a file that has no BOM', () => {
    writeEnvFile('.env.local', `DATABASE_URL=${PROD}\n`);

    expect(readEnvFile(join(root, '.env.local')).DATABASE_URL).toBe(PROD);
  });
});

describe('a dotenv variable reference refuses rather than mis-compares', () => {
  // Next expands ${DB_HOST}, $DB_HOST, ${X:-default} and nested references
  // through dotenv-expand when it loads the file. `util.parseEnv` does none of
  // it, so the guard would compare a literal while the application connects
  // somewhere else entirely.
  //
  // This guard does NOT reimplement dotenv-expand. A reimplementation that is
  // subtly different is worse than no comparison, because it looks like one.
  // It refuses instead.

  it('refuses a .env.local whose DATABASE_URL interpolates another variable', async () => {
    writeEnvFile(
      '.env.local',
      `DB_HOST=db.example.com\nDATABASE_URL=postgresql://app:pw@\${DB_HOST}:5432/hotshop\n`,
    );

    const state = await runSetup({ DATABASE_URL_TEST: SAFE });

    expect(reason(state)).toMatch(/refused/i);
    expect(reason(state)).toMatch(/variable reference/i);
  });

  it('refuses the bare-dollar spelling too', async () => {
    writeEnvFile(
      '.env.local',
      `DB_HOST=db.example.com\nDATABASE_URL=postgresql://app:pw@$DB_HOST:5432/hotshop\n`,
    );

    expect(reason(await runSetup({ DATABASE_URL_TEST: SAFE }))).toMatch(/variable reference/i);
  });

  it('tells the operator what to do instead', async () => {
    writeEnvFile('.env.local', `DATABASE_URL=postgresql://app:pw@\${DB_HOST}:5432/hotshop\n`);

    const message = reason(await runSetup({ DATABASE_URL_TEST: SAFE }));

    expect(message).toMatch(/in full|export/i);
  });

  it('does not flag an ordinary password that merely contains a dollar sign', () => {
    // `pa$$` has no identifier after the dollar, so dotenv-expand leaves it and
    // refusing would be gratuitous.
    expect(hasUnexpandedReference('postgresql://app:pa$$@db.example.com:5432/hotshop')).toBe(
      false,
    );
    expect(hasUnexpandedReference('postgresql://app:cost$@db.example.com:5432/hotshop')).toBe(
      false,
    );
  });

  it('flags the forms Next would expand', () => {
    for (const value of [
      'postgresql://u@${DB_HOST}/d',
      'postgresql://u@$DB_HOST/d',
      'postgresql://u@${DB_HOST:-fallback}/d',
      'postgresql://u:${PW}@db.example.com/d',
    ]) {
      expect(hasUnexpandedReference(value), value).toBe(true);
    }
  });
});

describe('.env.test.local is read the same careful way', () => {
  it('finds DATABASE_URL_TEST in the file', async () => {
    writeEnvFile('.env.test.local', `DATABASE_URL_TEST=${PROD}\n`);
    writeEnvFile('.env.local', `DATABASE_URL=${PROD}\n`);

    // Reaching the same-database refusal proves the test URL was read.
    expect(reason(await runSetup({}))).toMatch(/same database/i);
  });

  it('finds it through a BOM, which process.loadEnvFile would not', async () => {
    // The same three bytes, on the other file. Here the old behaviour failed
    // CLOSED — DATABASE_URL_TEST looked unset — so it was safe but baffling:
    // the operator is told to set a variable they are looking straight at.
    writeEnvFile('.env.test.local', `﻿DATABASE_URL_TEST=${PROD}\n`);
    writeEnvFile('.env.local', `DATABASE_URL=${PROD}\n`);

    expect(reason(await runSetup({}))).toMatch(/same database/i);
  });

  it('lets the environment win over the file', async () => {
    writeEnvFile('.env.test.local', `DATABASE_URL_TEST=${PROD}\n`);

    // The environment names a safe database, so no refusal on those grounds.
    const state = await runSetup({ DATABASE_URL_TEST: SAFE });

    if (!state.ok) expect(state.reason).not.toMatch(/same database/i);
  });
});

describe('the destructive opt-in can only be given for one run', () => {
  // HOTSHOP_TEST_ALLOW_SHARED_DATABASE says "everything in this database is
  // fair game". `.env.test.local` is the right home for a location and the
  // wrong home for a permission to destroy, because a file travels and the
  // acknowledgement then applies to databases its author never saw.

  it('still lets the file supply the location, which is not a permission', async () => {
    writeEnvFile(
      '.env.test.local',
      `DATABASE_URL_TEST=${SAFE}\nHOTSHOP_TEST_ALLOW_SHARED_DATABASE=1\n`,
    );
    const env: Record<string, string | undefined> = {};

    await runSetup(env);

    expect(env.DATABASE_URL_TEST).toBe(SAFE);
  });

  // That the flag itself is ignored when it comes from the file is pinned
  // behaviourally, against a real refused CREATE DATABASE, in
  // `tests/integration/db-provisioning.test.ts`.
});

describe('the bootstrap does not ADOPT what it reads', () => {
  it('leaves DATABASE_URL out of the environment it was given', async () => {
    // The obvious repair for the bug above is "load .env.local in the global
    // setup too". It is the wrong repair: `getDb()` reads process.env
    // DATABASE_URL, so adopting the file would let any test that forgot
    // `options.db` open a live connection to production.
    writeEnvFile('.env.local', `DATABASE_URL=${PROD}\n`);
    const env: Record<string, string | undefined> = { DATABASE_URL_TEST: SAFE };

    await runSetup(env);

    expect(env.DATABASE_URL).toBeUndefined();
  });

  it('removes a DATABASE_URL that WAS exported, once the guard has used it', async () => {
    const env: Record<string, string | undefined> = {
      DATABASE_URL: 'postgresql://app:pw@db.example.com:5432/hotshop',
      DATABASE_URL_TEST: SAFE,
    };

    await runSetup(env);

    // Used for the comparison, then taken away, so no worker can reach it.
    expect(env.DATABASE_URL).toBeUndefined();
  });

  it('readEnvFile never writes to process.env', () => {
    writeEnvFile('.env.local', 'HOTSHOP_BOOTSTRAP_CANARY=leaked\n');

    const parsed = readEnvFile(join(root, '.env.local'));

    expect(parsed.HOTSHOP_BOOTSTRAP_CANARY).toBe('leaked');
    expect(process.env.HOTSHOP_BOOTSTRAP_CANARY).toBeUndefined();
  });
});

describe('the bootstrap tolerates a missing or unreadable file', () => {
  it('treats an absent .env.local as no candidate, not as an error', () => {
    expect(appUrlCandidates({}, [new URL('.env.local', rootUrl)])).toEqual([]);
  });

  it('treats an unreadable .env.local as no candidate', () => {
    writeEnvFile('.env.local', '\u0000not a dotenv file at all\u0000');

    expect(appUrlCandidates({}, [new URL('.env.local', rootUrl)])).toEqual([]);
  });

  it('ignores a .env.local with no DATABASE_URL in it', () => {
    writeEnvFile('.env.local', 'SUPABASE_URL=https://example.supabase.co\n');

    expect(appUrlCandidates({}, [new URL('.env.local', rootUrl)])).toEqual([]);
  });

  it('reads DATABASE_URL past comments and blank lines', () => {
    writeEnvFile('.env.local', `# pasted from the dashboard\n\nDATABASE_URL=${PROD}\n`);

    expect(appUrlCandidates({}, [new URL('.env.local', rootUrl)])).toEqual([
      { source: 'DATABASE_URL in .env.local', url: PROD, resolved: true, committed: false },
    ]);
  });
});
