/**
 * The second fail-open, pinned.
 *
 * `provisionRunDatabase` used to catch 42501, 25006 and 0A000, print a
 * `console.warn`, and carry on INSIDE the database `DATABASE_URL_TEST` names.
 * That is the path the reviewer's decoy was destroyed through: with a role
 * lacking CREATEDB, the suite created its fixture tables, ran
 * `truncate ... restart identity cascade` between every test and
 * `drop table ... cascade` at teardown, all in the application database, and
 * reported 95 of 95 green with one warning line scrolled off the top.
 *
 * A warning is not a control. It does not change what happens next, CI logs
 * that surface only failures never show it, and it asks a human to be watching
 * at the exact moment nobody is. The policy everywhere else in this file's
 * module is refuse-when-uncertain, and running destructive DDL in a database
 * the suite did not create is the most uncertain thing it does.
 *
 * HOW THE FAILURE IS PROVOKED, and why it is portable: a connection carrying
 * `-c default_transaction_read_only=on` makes the server reject
 * `CREATE DATABASE` with a genuine SQLSTATE 25006. No superuser, no special
 * role, no fixture server. The error comes from PostgreSQL, not from a stub.
 */

import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  ALLOW_SHARED_VAR,
  TestDatabaseConfigError,
  provisionRunDatabase,
} from '../helpers/test-database';
import { setup, type TestDatabaseState } from '../setup/integration-global-setup';
import { testDatabaseUrl } from '../setup/integration-setup';

/** The run database, reached over a connection that cannot create anything. */
let readOnlyUrl: string;

beforeAll(() => {
  const url = new URL(testDatabaseUrl());
  url.searchParams.set('options', '-c default_transaction_read_only=on');
  readOnlyUrl = url.toString();
});

describe('when a per-run database cannot be created', () => {
  it('refuses the run instead of falling back to the named database', async () => {
    const error = await provisionRunDatabase(readOnlyUrl, {}).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(TestDatabaseConfigError);
  });

  it('explains what it would otherwise have done to that database', async () => {
    const error = await provisionRunDatabase(readOnlyUrl, {}).catch((caught: unknown) => caught);
    const message = (error as Error).message;

    expect(message).toMatch(/refused/i);
    expect(message).toMatch(/truncate/i);
    expect(message).toMatch(/25006/);
  });

  it('names the opt-in, so the operator is not stuck', async () => {
    const error = await provisionRunDatabase(readOnlyUrl, {}).catch((caught: unknown) => caught);

    expect((error as Error).message).toContain(ALLOW_SHARED_VAR);
    expect((error as Error).message).toMatch(/createdb/i);
  });

  it('refuses for every SQLSTATE that used to be silently tolerated', async () => {
    // 25006 is the one provoked here. The refusal must not be special-cased to
    // it: 42501 and 0A000 took exactly the same branch and reached the decoy.
    const error = await provisionRunDatabase(readOnlyUrl, {}).catch((caught: unknown) => caught);

    expect((error as Error).message).not.toMatch(/42501|0A000/);
    expect(error).toBeInstanceOf(TestDatabaseConfigError);
  });
});

describe('the explicit opt-in', () => {
  it('lets the run past the gate when the operator has set it', async () => {
    // Behavioural evidence that the gate opened: provisioning gets PAST the
    // refusal and on to the fixture DDL, which then fails on its own merits
    // because this connection cannot write either. A TestDatabaseConfigError
    // here would mean the opt-in was ignored.
    const error = await provisionRunDatabase(readOnlyUrl, {
      [ALLOW_SHARED_VAR]: '1',
    }).catch((caught: unknown) => caught);

    expect(error).not.toBeInstanceOf(TestDatabaseConfigError);
    expect((error as { code?: string }).code).toBe('25006');
  });

  it('is not satisfied by any old truthy value', async () => {
    // An opt-in that accepts 'false', '0' or 'no' is an opt-in nobody can read.
    for (const value of ['', '0', 'false', 'no', 'true', 'yes']) {
      const error = await provisionRunDatabase(readOnlyUrl, {
        [ALLOW_SHARED_VAR]: value,
      }).catch((caught: unknown) => caught);

      expect(error, `${ALLOW_SHARED_VAR}=${JSON.stringify(value)} should not open the gate`).toBeInstanceOf(
        TestDatabaseConfigError,
      );
    }
  });
});

/**
 * Where the opt-in is allowed to come from.
 *
 * `HOTSHOP_TEST_ALLOW_SHARED_DATABASE` says "everything in this database is
 * fair game". `.env.test.local` is the right home for a LOCATION — which
 * database to connect to, written once and forgotten — and the wrong home for
 * a PERMISSION TO DESTROY. A file travels: copied from a colleague, restored
 * from a dotfiles backup, pasted into a setup guide. The acknowledgement then
 * applies to databases whoever wrote it never saw, which is the opposite of an
 * acknowledgement.
 *
 * So the global setup captures it from the environment BEFORE merging the
 * file, and this drives the real `setup` against a scratch directory to prove
 * it, using the same read-only connection that provokes a genuine 25006.
 */
describe('the destructive opt-in is a per-run decision', () => {
  let root: string;
  let rootUrl: URL;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), 'hotshop-optin-'));
    rootUrl = pathToFileURL(join(root, '/'));
  });

  afterEach(() => {
    rmSync(root, { recursive: true, force: true });
  });

  async function runSetup(env: Record<string, string | undefined>): Promise<TestDatabaseState> {
    const provide = vi.fn();
    await setup({ provide }, { root: rootUrl, env });
    return provide.mock.calls[0]![1] as TestDatabaseState;
  }

  it('refuses when the flag is only in .env.test.local', () => {
    writeFileSync(
      join(root, '.env.test.local'),
      `DATABASE_URL_TEST=${readOnlyUrl}\n${ALLOW_SHARED_VAR}=1\n`,
      'utf8',
    );

    return runSetup({}).then((state) => {
      expect(state.ok).toBe(false);
      if (!state.ok) expect(state.reason).toMatch(/refused/i);
    });
  });

  it('honours the flag when it comes from the environment', async () => {
    writeFileSync(join(root, '.env.test.local'), `DATABASE_URL_TEST=${readOnlyUrl}\n`, 'utf8');

    const state = await runSetup({ [ALLOW_SHARED_VAR]: '1' });

    // Past the gate, and then failing on its own merits at the fixture DDL,
    // which this read-only connection also cannot run. A refusal naming the
    // opt-in would mean the environment was ignored too.
    expect(state.ok).toBe(false);
    if (!state.ok) expect(state.reason).not.toContain(ALLOW_SHARED_VAR);
  });
});

describe('errors that are not about creating databases', () => {
  it('propagates a connection failure as itself, not as a config refusal', async () => {
    // Nothing is listening on 5434. This must not be mistaken for "you lack
    // CREATEDB", which would send the operator looking at grants.
    const error = await provisionRunDatabase(
      'postgresql://hotshop@127.0.0.1:5434/postgres',
      {},
    ).catch((caught: unknown) => caught);

    expect(error).not.toBeInstanceOf(TestDatabaseConfigError);
    expect((error as { code?: string }).code).toBe('ECONNREFUSED');
  });
});
