/**
 * Global setup for the `integration` project only. TEST CONFIGURATION.
 *
 * Runs ONCE per `npm run test`, before any worker starts, and never for the
 * `unit` project, so the 168 pure unit tests keep running with no database and
 * no startup cost.
 *
 * Order matters here: the guard runs BEFORE anything connects. If
 * DATABASE_URL_TEST is missing or aims at the application database, nothing is
 * opened and nothing is provisioned.
 *
 * WHY THE FAILURE IS CARRIED RATHER THAN THROWN. Vitest runs every project's
 * global setup before collecting any test file, so throwing here aborts the
 * whole run, unit lane included, and a developer with no PostgreSQL could not
 * run `npm run test` at all. That would make the pure unit tests depend on a
 * database, which is the thing they must never do. So the reason is recorded
 * and handed to the integration workers, where `integration-setup.ts` throws it
 * in a `beforeAll`. Every integration file then fails with the full message,
 * `npm run test` exits non-zero, and the unit lane is untouched. Nothing falls
 * back to DATABASE_URL at any point.
 */

import type { ProvidedContext } from 'vitest';

import {
  ALLOW_SHARED_VAR,
  APP_URL_VAR,
  appUrlCandidates,
  readEnvFile,
  provisionRunDatabase,
  resolveTestDatabaseUrl,
  type ProvisionedDatabase,
} from '../helpers/test-database';

/**
 * The files an operator may have put `DATABASE_URL` in. `.env.local` is the one
 * that matters: `.env.example`, `scripts/db-check.ts` and `@/server/db` all
 * name it, and the guard used to ignore it entirely.
 *
 * These are READ for comparison and never loaded into the environment. See
 * `readEnvFile` for why that distinction is the whole fix.
 */
export function appEnvFiles(root: URL): readonly URL[] {
  return [new URL('.env.local', root), new URL('.env', root)];
}

const PROJECT_ROOT = new URL('../../', import.meta.url);

/**
 * Injection points, used ONLY by `tests/config/test-database-bootstrap.test.ts`.
 *
 * They exist because the bug this file now guards against was a WIRING bug, not
 * a logic bug: the comparison was correct and well tested and simply was not
 * called with the operator's real configuration. A test that can drive this
 * function against a scratch directory is the only kind that would have caught
 * it, so `setup` has to be callable with somewhere else to look.
 */
export interface SetupOptions {
  readonly root?: URL;
  readonly env?: Record<string, string | undefined>;
}

/** Either a usable database, or the reason there is not one. */
export type TestDatabaseState =
  | { readonly ok: true; readonly url: string; readonly isolated: boolean }
  | { readonly ok: false; readonly reason: string };

declare module 'vitest' {
  interface ProvidedContext {
    /** Read with `inject('testDatabase')`. */
    testDatabase: TestDatabaseState;
  }
}

/**
 * The shape Vitest hands a global setup. Written out rather than imported:
 * `GlobalSetupContext` is not an exported type name in Vitest 5, and only
 * `provide` is used here.
 */
interface GlobalSetup {
  provide<K extends keyof ProvidedContext & string>(key: K, value: ProvidedContext[K]): void;
}

let provisioned: ProvisionedDatabase | undefined;

/**
 * `.env.test.local` is read here rather than by Vitest, so that the variable
 * reaches the guard the same way in a local shell, in CI, and in a Windows
 * PowerShell session where `export` does not exist. It never overwrites a
 * variable the environment already set, so CI stays authoritative.
 */
/**
 * Merges `.env.test.local` into `env`, without overwriting anything already
 * set, so CI stays authoritative.
 *
 * Read with `readEnvFile` rather than `process.loadEnvFile`, which matters for
 * the same reason it mattered for `.env.local`: `loadEnvFile` leaves a BOM on
 * the first key. On Windows, where PowerShell writes one by default, that made
 * `DATABASE_URL_TEST` invisible and the suite reported it as unset while the
 * operator was looking straight at it. Failing closed, but baffling.
 */
function loadTestEnvFile(root: URL, env: Record<string, string | undefined>): void {
  for (const [key, value] of Object.entries(readEnvFile(new URL('.env.test.local', root)))) {
    if (value !== undefined && env[key] === undefined) env[key] = value;
  }
}

export async function setup({ provide }: GlobalSetup, options: SetupOptions = {}): Promise<void> {
  const root = options.root ?? PROJECT_ROOT;
  const env = options.env ?? process.env;

  // Captured BEFORE the file is merged, so the destructive opt-in can only be
  // given for THIS run, from the environment.
  //
  // `.env.test.local` is the right home for a LOCATION: which database to
  // connect to, written once and forgotten. It is the wrong home for a
  // PERMISSION TO DESTROY. A file travels — copied from a colleague, restored
  // from a dotfiles backup, pasted into a README — and the acknowledgement
  // then applies to databases whoever wrote it never saw. Requiring it on the
  // command line is the friction a destructive opt-in should have.
  const allowSharedDatabase = env[ALLOW_SHARED_VAR];

  loadTestEnvFile(root, env);

  let baseUrl: string;
  try {
    baseUrl = resolveTestDatabaseUrl(env, appUrlCandidates(env, appEnvFiles(root)));
  } catch (error) {
    provide('testDatabase', { ok: false, reason: (error as Error).message });
    return;
  }

  // The application URL is now out of reach of every worker, deliberately.
  //
  // The guard above has already used it. Leaving it in the environment would
  // mean `getDb()` in `@/server/db`, which reads this exact variable, could
  // open a live connection to the application database from any test that
  // forgot to pass `options.db`. Removing it turns that mistake from a silent
  // success into a loud throw, which is what a test wants.
  delete env[APP_URL_VAR];

  try {
    provisioned = await provisionRunDatabase(baseUrl, {
      [ALLOW_SHARED_VAR]: allowSharedDatabase,
    });
  } catch (error) {
    provide('testDatabase', {
      ok: false,
      reason:
        `DATABASE_URL_TEST is set, but the test database could not be prepared:\n` +
        `  ${(error as Error).message}\n` +
        'Check the server is running and that the account may create databases.',
    });
    return;
  }

  provide('testDatabase', {
    ok: true,
    url: provisioned.url,
    isolated: provisioned.isolated,
  });
}

export async function teardown(): Promise<void> {
  await provisioned?.teardown();
  provisioned = undefined;
}
