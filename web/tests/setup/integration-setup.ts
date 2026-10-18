/**
 * Per-worker setup for the `integration` project. TEST CONFIGURATION.
 *
 * This is where a bad configuration becomes a visible failure. Global setup
 * recorded the reason rather than throwing it, so that the unit lane could run
 * (see the note in `integration-global-setup.ts`); this `beforeAll` throws it,
 * once per integration file, with the full message.
 *
 * The guard is then re-run against the worker's own environment. A worker
 * inherits `process.env`, and the connection tests legitimately stub
 * DATABASE_URL, so this is the cheap check that nothing has been left pointing
 * somewhere it should not.
 */

import { beforeAll, inject } from 'vitest';

import { TestDatabaseConfigError, resolveTestDatabaseUrl } from '../helpers/test-database';

/**
 * The database this run owns. Throws rather than returning a fallback: a test
 * that reached this point without a database must fail, not improvise.
 */
export function testDatabaseUrl(): string {
  const state = inject('testDatabase');
  if (!state.ok) throw new TestDatabaseConfigError(state.reason);
  return state.url;
}

beforeAll(() => {
  const state = inject('testDatabase');
  if (!state.ok) throw new TestDatabaseConfigError(state.reason);

  resolveTestDatabaseUrl(process.env);
});
