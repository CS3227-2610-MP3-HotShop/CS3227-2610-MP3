/**
 * The actor: who a transaction is acting as, and the ONE place one can be made.
 *
 * The contract and the reasoning behind every decision here live in the header
 * of `tests/integration/actor-seam.test.ts` (INV17-B9), against the acceptance
 * criteria in the INV17-B9 section of `workflow/TECHNICAL-SPEC.md`. The short
 * version, because the reasoning is load-bearing rather than decorative:
 *
 *   - TD-08 puts authorization entirely in application code and does not enable
 *     Row Level Security, and the database role is privileged. A repository
 *     query with the actor's id in its WHERE clause and the same query without
 *     it are both valid SQL and nothing underneath catches the difference.
 *   - FR-1704 says every operation acts as the session's own user and no request
 *     carries a user identifier the client chose. That is enforced here by
 *     making `Actor` unconstructible outside this module, in two halves:
 *
 *       COMPILE TIME, by the `unique symbol` property. It is `declare`d and
 *       never exported as a value, so no caller can name it and no object
 *       literal can satisfy `Actor`. The only way past is a cast, and a cast is
 *       greppable — `tests/unit/actor-seam-boundaries.test.ts` greps for it.
 *
 *       RUNTIME, by WeakSet membership, because a cast compiles. This is the
 *       half that actually holds. `withTransaction` rejects anything
 *       `isAuthoredActor` does not recognise BEFORE it opens a transaction, so
 *       a forged actor reaches no SQL at all, not even a BEGIN.
 *
 * `actorFromSession` takes a `SessionRecord`, never a bare user id, and the
 * signature is the point: there is no function in `src/` into which a route
 * handler can post a user id and receive the authority to act as them.
 *
 * WHAT C2 CHANGES AND WHAT IT MUST NOT. There is no sessions table yet. C2 adds
 * the read — resolve the opaque cookie token, find the session row, reject it if
 * expired or revoked — and hands the result here unchanged. If it needs a third
 * field (a role, say) it is ADDED to `SessionRecord` and `Actor`; nothing in the
 * handle, the scope check or the lint rule depends on the field list.
 */

/**
 * Declared, never exported as a value. Same idea as the refusal catalogue's
 * authoring token, applied one level earlier: at the TYPE.
 */
declare const ACTOR_IDENTITY: unique symbol;

/** What a session row supplies. C2 produces these; nothing else may. */
export interface SessionRecord {
  readonly sessionId: string;
  readonly userId: string;
}

export type Actor = {
  readonly sessionId: string;
  readonly userId: string;
  readonly [ACTOR_IDENTITY]: true;
};

/**
 * "This module made this." A WeakSet and not a brand property, because a brand
 * property is copied by a spread and a WeakSet entry is not: `{ ...actor }` is
 * indistinguishable from an actor at the type level, and distinguishable here.
 */
const authored = new WeakSet<object>();

function requireIdentifier(value: unknown, field: string): string {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new TypeError(
      `actorFromSession requires a non-empty string ${field} from the session row; ` +
        `received ${value === null ? 'null' : typeof value}.`,
    );
  }
  return value;
}

/**
 * The ONLY producer of an `Actor` anywhere in `src/`.
 *
 * The returned object is frozen, so nothing downstream can swap a user id into
 * an actor the WeakSet already recognises. That matters more than it looks:
 * membership is by object identity, so a mutable member would be a forged actor
 * that passes every check.
 */
export function actorFromSession(session: SessionRecord): Actor {
  const sessionId = requireIdentifier(session?.sessionId, 'sessionId');
  const userId = requireIdentifier(session?.userId, 'userId');

  const actor = Object.freeze({ sessionId, userId }) as Actor;
  authored.add(actor);

  return actor;
}

/**
 * WeakSet membership. Unforgeable at runtime: a cast, a spread, a hand-built
 * literal and a Proxy over a real actor are all rejected, because none of them
 * is the object this module put in the set.
 */
export function isAuthoredActor(value: unknown): value is Actor {
  return typeof value === 'object' && value !== null && authored.has(value);
}
