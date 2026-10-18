/**
 * Actors for tests. TEST FIXTURE, owned by the tester.
 *
 * Deliberately thin. It does NOT build an `Actor` by hand, because the whole
 * point of the seam is that nothing can: it goes through `actorFromSession`,
 * the one exported producer, exactly as a route handler will. A helper that
 * cast its way to an `Actor` would be the first bypass, and it would be in the
 * test suite, which is the worst place for one — every later test would inherit
 * a shape the production path cannot produce.
 *
 * `sessionId` is a plain unique string here. C2 replaces the SOURCE of these
 * values with a row from the sessions table; it does not change their type, so
 * nothing below has to move.
 */

import { randomUUID } from 'node:crypto';

import { actorFromSession, type Actor } from '@/server/actor';

/**
 * A fresh actor with ids nothing else in the run shares.
 *
 * Unique by default, and that matters for more than tidiness: the
 * session-state leak test in `actor-seam.test.ts` searches the server's
 * settings for the actor's own id, so two tests sharing one id would let a
 * leak from the first be attributed to the second.
 */
export function testActor(overrides: { userId?: string; sessionId?: string } = {}): Actor {
  return actorFromSession({
    userId: overrides.userId ?? `user-${randomUUID()}`,
    sessionId: overrides.sessionId ?? `session-${randomUUID()}`,
  });
}
