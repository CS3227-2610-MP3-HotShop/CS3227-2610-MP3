import { createHash, randomBytes, randomUUID } from 'node:crypto';

import type { Clock } from '@/domain/time';
import type { SessionRecord } from '@/server/actor';
import type { Db } from '@/server/db';

/** Safe storage failure; never accepts driver details or session credentials. */
export class SessionStorageError extends Error {
  override readonly name = 'SessionStorageError';

  constructor() {
    super('Session storage is unavailable.');
  }
}

function tokenHash(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

/** Called only after trusted server-side credential verification. */
export async function createSession(
  userId: string,
  options: { db: Db; clock: Clock },
): Promise<{ token: string; session: SessionRecord }> {
  const session: SessionRecord = { sessionId: randomUUID(), userId };
  const token = randomBytes(32).toString('base64url');
  const hash = tokenHash(token);
  const now = options.clock.now();

  try {
    await options.db`insert into sessions (id, token_hash, user_id, created_at, last_seen_at)
      values (${session.sessionId}, ${hash}, ${userId}, ${now}, ${now})`;
  } catch {
    throw new SessionStorageError();
  }

  return { token, session };
}

export async function resolveSession(
  token: string,
  options: { db: Db },
): Promise<SessionRecord | null> {
  const hash = tokenHash(token);
  try {
    const [row] = await options.db<{ id: string; user_id: string }[]>`
      select id, user_id from sessions where token_hash = ${hash}`;
    return row ? { sessionId: row.id, userId: row.user_id } : null;
  } catch {
    throw new SessionStorageError();
  }
}

export async function revokeSession(
  token: string,
  options: { db: Db },
): Promise<void> {
  const hash = tokenHash(token);
  try {
    await options.db`delete from sessions where token_hash = ${hash}`;
  } catch {
    throw new SessionStorageError();
  }
}
