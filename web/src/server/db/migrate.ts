/**
 * Applies `*.sql` files in filename order over a DIRECT connection. For tests and CI;
 * the Supabase CLI still authors migrations (TD-02).
 *
 * `DATABASE_URL_DIRECT`, never the pooler: transaction-mode pooling does not give DDL
 * a stable session. Applied files are recorded in `schema_migrations`, so a re-run
 * skips them. Each file and its record commit together.
 */

import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

import { createDb } from '@/server/db';

const DEFAULT_DIR = 'supabase/migrations';
const DIRECT_URL_VAR = 'DATABASE_URL_DIRECT';

export async function runMigrations(dir: string = DEFAULT_DIR): Promise<void> {
  const url = process.env[DIRECT_URL_VAR]?.trim() ?? '';
  if (url === '') {
    throw new Error(`${DIRECT_URL_VAR} is not set, so migrations have no direct connection to run on.`);
  }

  const files = readdirSync(dir)
    .filter((name) => name.endsWith('.sql'))
    .sort();

  const sql = createDb(url);
  try {
    await sql`create table if not exists schema_migrations (name text primary key)`;
    const done = new Set((await sql<{ name: string }[]>`select name from schema_migrations`).map((r) => r.name));

    for (const name of files) {
      if (done.has(name)) continue;
      const body = readFileSync(join(dir, name), 'utf8');
      await sql.begin(async (tx) => {
        await tx.unsafe(body);
        await tx`insert into schema_migrations (name) values (${name})`;
      });
    }
  } finally {
    await sql.end();
  }
}
