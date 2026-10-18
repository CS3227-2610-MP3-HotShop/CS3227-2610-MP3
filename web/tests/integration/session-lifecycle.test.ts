/** C2-P-v1: internal session boundary only; HTTP/cookie guards remain unverified. */
import { createHash, randomUUID } from 'node:crypto';

import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { fixedClock } from '@/domain/time';
import { actorFromSession, isAuthoredActor } from '@/server/actor';
import { createDb, type Db } from '@/server/db';
import { runMigrations } from '@/server/db/migrate';
import { createSession, resolveSession, revokeSession, SessionStorageError } from '@/server/db/sessions';

import { testDatabaseUrl } from '../setup/integration-setup';

const USER = '11111111-1111-4111-8111-111111111111';
const OTHER_USER = '22222222-2222-4222-8222-222222222222';
const INSTANT = new Date('2001-02-03T04:05:06.789Z');
const hash = (token: string) => createHash('sha256').update(token).digest('hex');
let db: Db;

beforeAll(async () => {
  const previous = process.env.DATABASE_URL_DIRECT;
  try {
    process.env.DATABASE_URL_DIRECT = testDatabaseUrl();
    await runMigrations();
  } finally {
    if (previous === undefined) delete process.env.DATABASE_URL_DIRECT;
    else process.env.DATABASE_URL_DIRECT = previous;
  }
  db = createDb(testDatabaseUrl());
});
beforeEach(async () => { await db`truncate sessions`; });
afterAll(async () => { await db?.end(); });

async function seed(token: string, userId = USER) {
  const sessionId = randomUUID();
  await db`insert into sessions (id, token_hash, user_id, created_at, last_seen_at)
    values (${sessionId}, ${hash(token)}, ${userId}, ${INSTANT}, ${INSTANT})`;
  return { sessionId, userId };
}

describe('C2-P internal session lifecycle', () => {
  it('P1 rejects unknown and modified tokens and resolves only the matching session/user', async () => {
    const first = await seed('first-browser-token');
    const second = await seed('second-browser-token', OTHER_USER);
    await expect(resolveSession('unknown-token', { db })).resolves.toBeNull();
    await expect(resolveSession('first-browser-tokeN', { db })).resolves.toBeNull();
    await expect(resolveSession('first-browser-token', { db })).resolves.toEqual(first);
    await expect(resolveSession('second-browser-token', { db })).resolves.toEqual(second);
  });

  it('P2 creates independent 32-byte base64url tokens and UUIDs, storing only SHA-256 hashes', async () => {
    const options = { db, clock: fixedClock(INSTANT) };
    const first = await createSession(USER, options);
    const second = await createSession(USER, options);
    expect(first.token).not.toBe(second.token);
    expect(first.session.sessionId).not.toBe(second.session.sessionId);
    const rows = await db`select * from sessions`;
    expect(rows).toHaveLength(2);
    for (const result of [first, second]) {
      expect(result.token).toMatch(/^[A-Za-z0-9_-]{43}$/);
      expect(Buffer.from(result.token, 'base64url')).toHaveLength(32);
      expect(result.session.sessionId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
      expect(result.session.userId).toBe(USER);
      expect(rows.find((row) => row.id === result.session.sessionId)?.token_hash).toBe(hash(result.token));
      expect(JSON.stringify(rows).includes(result.token)).toBe(false);
    }
  });

  it('P3 stores injected timestamps exactly and does not expire as time advances', async () => {
    const clock = fixedClock(INSTANT);
    vi.useFakeTimers({ toFake: ['Date'] });
    try {
      vi.setSystemTime(INSTANT);
      const created = await createSession(USER, { db, clock });
      const [row] = await db`select created_at, last_seen_at from sessions where id = ${created.session.sessionId}`;
      expect(row?.created_at).toEqual(INSTANT);
      expect(row?.last_seen_at).toEqual(INSTANT);
      const elapsed = 100 * 366 * 24 * 60 * 60 * 1000;
      clock.advanceBy(elapsed);
      vi.setSystemTime(clock.now());
      await expect(resolveSession(created.token, { db })).resolves.toEqual(created.session);
    } finally {
      vi.useRealTimers();
    }
  });

  it('P4 revokes one session, preserves other same-user and other-user sessions, and is idempotent', async () => {
    const first = await seed('revoke-this');
    const sibling = await seed('keep-same-user');
    const other = await seed('keep-other-user', OTHER_USER);
    await revokeSession('revoke-this', { db });
    const rows = await db`select id from sessions`;
    expect(rows.map((row) => row.id).sort()).toEqual([sibling.sessionId, other.sessionId].sort());
    await expect(resolveSession('revoke-this', { db })).resolves.toBeNull();
    await expect(resolveSession('keep-same-user', { db })).resolves.toEqual(sibling);
    await expect(resolveSession('keep-other-user', { db })).resolves.toEqual(other);
    await expect(revokeSession('revoke-this', { db })).resolves.toBeUndefined();
    await expect(revokeSession('unknown-token', { db })).resolves.toBeUndefined();
    expect(await db`select id from sessions where id = ${first.sessionId}`).toHaveLength(0);
    expect(await db`select id from sessions`).toHaveLength(2);
  });

  it.each(['create', 'resolve', 'revoke'] as const)('P5 reports a safe error on actual database failure during %s', async (operation) => {
    const token = 'private-session-token';
    await seed(token);
    // An actual PostgreSQL undefined-table failure, restored even when assertions fail.
    await db`alter table sessions rename to unavailable_session_storage`;
    try {
      const action = operation === 'create'
        ? createSession(USER, { db, clock: fixedClock(INSTANT) })
        : operation === 'resolve' ? resolveSession(token, { db }) : revokeSession(token, { db });
      const failure: unknown = await action.then(() => null, (error: unknown) => error);
      expect(failure).toBeInstanceOf(SessionStorageError);
      const error = failure as Error;
      expect(error.message).toBe('Session storage is unavailable.');
      expect(Object.getOwnPropertyNames(error).sort()).toEqual(['message', 'name', 'stack']);
      const exposed = `${String(error)} ${error.stack} ${JSON.stringify(error)}`;
      for (const detail of [token, '42P01', 'does not exist', 'unavailable_session_storage']) {
        expect(exposed.includes(detail)).toBe(false);
      }
    } finally {
      await db`alter table unavailable_session_storage rename to sessions`;
    }
  });

  it('P6 supplies the existing actor factory with the resolved record unchanged', async () => {
    const session = await seed('actor-token');
    const resolved = await resolveSession('actor-token', { db });
    expect(resolved).toEqual(session);
    if (resolved === null) throw new Error('Expected seeded session');
    const actor = actorFromSession(resolved);
    expect(isAuthoredActor(actor)).toBe(true);
    expect(actor.sessionId).toBe(session.sessionId);
    expect(actor.userId).toBe(USER);
  });
});
