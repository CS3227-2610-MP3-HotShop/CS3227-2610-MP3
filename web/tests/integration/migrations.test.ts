/**
 * C1. Migration runner and the `sessions` migration (FR-1701 to FR-1703).
 *
 * Criteria, from the C1 row: migrations apply to an empty database; re-running is
 * idempotent; the `sessions` schema matches the TECHNICAL-SPEC data model.
 *
 * INTERFACE, specified by the coordinator rather than derived from the spec:
 * `runMigrations(dir?)` from `@/server/db/migrate`, reading `DATABASE_URL_DIRECT` at call
 * time and applying `*.sql` in filename order from `dir`, default `supabase/migrations`.
 * The runner is for tests and CI; the Supabase CLI still authors migrations (TD-02).
 *
 * Each test gets a brand-new empty database. `DATABASE_URL` is pointed at a dead port
 * so a runner that used the pooled string fails instead of passing by accident.
 *
 * `sessions.user_id` deliberately has NO foreign key: INV-01 owns `users` and adds it.
 * NOT asserted, because the spec is silent: column defaults, the name of any
 * bookkeeping table, the runner's return value.
 */

import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import postgres from 'postgres';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { runMigrations } from '@/server/db/migrate';

import { testDatabaseUrl } from '../setup/integration-setup';

const opts = { max: 1, prepare: false, onnotice: () => {} } as const;
const DEAD_POOLED_URL = 'postgresql://nobody:nothing@127.0.0.1:1/none';

let admin: postgres.Sql;
let sql: postgres.Sql;
let dbName: string;
let url: string;
const saved = { direct: process.env.DATABASE_URL_DIRECT, pooled: process.env.DATABASE_URL };

async function migrate(dir?: string): Promise<void> {
  process.env.DATABASE_URL_DIRECT = url;
  process.env.DATABASE_URL = DEAD_POOLED_URL;
  await runMigrations(dir);
}

beforeEach(async () => {
  dbName = `hotshop_c1_${Math.random().toString(36).slice(2, 10)}`;
  admin = postgres(testDatabaseUrl(), opts);
  await admin.unsafe(`create database "${dbName}"`);
  const target = new URL(testDatabaseUrl());
  target.pathname = `/${dbName}`;
  url = target.toString();
  sql = postgres(url, opts);
});

afterEach(async () => {
  await sql.end();
  await admin.unsafe(`drop database if exists "${dbName}" with (force)`);
  await admin.end();
  for (const [key, value] of [
    ['DATABASE_URL_DIRECT', saved.direct],
    ['DATABASE_URL', saved.pooled],
  ] as const) {
    if (value === undefined) delete process.env[key];
    else process.env[key] = value;
  }
});

describe('C1 migrations', () => {
  it('applies to an empty database and creates sessions with exactly the specified columns', async () => {
    expect(await sql`select 1 from pg_tables where tablename = 'sessions'`).toHaveLength(0);

    await migrate();

    const columns = await sql<{ column_name: string; data_type: string; is_nullable: string }[]>`
      select column_name, data_type, is_nullable
      from information_schema.columns
      where table_schema = 'public' and table_name = 'sessions'
      order by column_name`;

    // Five columns and no more: "No expiry column" is part of the data model.
    expect(columns.map((c) => [c.column_name, c.data_type, c.is_nullable])).toEqual([
      ['created_at', 'timestamp with time zone', 'NO'],
      ['id', 'uuid', 'NO'],
      ['last_seen_at', 'timestamp with time zone', 'NO'],
      ['token_hash', 'text', 'NO'],
      ['user_id', 'uuid', 'NO'],
    ]);
  });

  it('makes id the primary key and token_hash unique, with no foreign key on user_id', async () => {
    await migrate();

    const indexes = await sql<{ is_primary: boolean; is_unique: boolean; partial: boolean; cols: string[] }[]>`
      select i.indisprimary as is_primary, i.indisunique as is_unique,
             i.indpred is not null as partial,
             array(select a.attname::text from pg_attribute a
                   where a.attrelid = i.indrelid and a.attnum = any(i.indkey)) as cols
      from pg_index i where i.indrelid = 'public.sessions'::regclass`;
    expect(indexes.some((i) => i.is_primary && i.cols.join() === 'id')).toBe(true);
    // A partial unique index would not make the column unique, hence `!partial`.
    expect(indexes.some((i) => i.is_unique && !i.partial && i.cols.join() === 'token_hash')).toBe(true);

    // The decision made visible: INV-01 adds the users constraint, not this migration.
    expect(await sql`select 1 from pg_constraint where conrelid = 'public.sessions'::regclass and contype = 'f'`).toHaveLength(0);
  });

  it('is idempotent: a second run succeeds and leaves the existing table untouched', async () => {
    await migrate();
    // A marker only a surviving table can still carry. A runner that re-applies
    // `create table` throws below; one that drops and recreates loses the column.
    await sql`alter table sessions add column rerun_marker integer`;

    await migrate();

    const marker = await sql`
      select 1 from information_schema.columns
      where table_name = 'sessions' and column_name = 'rerun_marker'`;
    expect(marker).toHaveLength(1);
  });

  it('applies the .sql files in the directory it is given, in filename order', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'c1-migrations-'));
    // Written out of order: 0002 only succeeds if 0001 ran first.
    writeFileSync(join(dir, '0002_row.sql'), `insert into fixture_probe values ('applied');`);
    writeFileSync(join(dir, '0001_table.sql'), `create table fixture_probe (note text);`);

    await migrate(dir);

    expect(await sql`select note from fixture_probe`).toEqual([{ note: 'applied' }]);
    expect(await sql`select 1 from pg_tables where tablename = 'sessions'`).toHaveLength(0);
  });
});
