/**
 * The database boundary: one connection, one transaction helper, and the
 * mapping from SQLSTATE 23505 to a refusal the caller authored.
 *
 * The contract and the reasoning behind every decision here live in the header
 * of `tests/integration/db-transaction.test.ts`. The short version:
 *
 *   - `getDb()` memoizes one client and reads DATABASE_URL at FIRST CALL, so
 *     importing this module during `next build` (where the variable is
 *     routinely absent) is harmless.
 *   - Every client is built by `createDb`, which resolves `prepare: false`,
 *     a serverless-shaped pool and the TLS mode from the connection string.
 *     `getDb` delegates to it, so there is one place these are decided.
 *   - `withTransaction` opens ONE transaction on ONE connection and hands that
 *     handle to every repository in the operation.
 *   - A refusal thrown inside comes back out BY IDENTITY, because
 *     `isAuthoredRefusal` is WeakSet membership and a rebuilt refusal would be
 *     treated downstream as attacker-influenced input.
 *   - Only SQLSTATE 23505 is mapped, and only by the caller's
 *     `onUniqueViolation`, which may DECLINE by returning `undefined`.
 *
 * WHAT THIS MODULE DOES NOT DO, AND WHAT THE LAYER ABOVE THEREFORE OWES.
 * Supplying a mapper does NOT mean nothing driver-shaped escapes. A mapper
 * handles 23505 and nothing else, so every other database error leaves here
 * exactly as postgres.js built it.
 *
 * Be concrete about what that carries, because "driver internals" sounds like
 * noise and this is not noise. A PostgresError's `detail`, `schema_name`,
 * `table_name`, `constraint_name`, `file` and `line` are all ENUMERABLE OWN
 * properties, so `NextResponse.json({ error })` ships every one of them. For a
 * unique violation `detail` reads:
 *
 *     Key (email)=(victim@example.com) already exists.
 *
 * That names a PRE-EXISTING ROW, not the value the caller submitted. Returning
 * it to whoever asked is account enumeration: anyone can learn whether an
 * address is registered by trying it. The same shape leaks any other unique
 * column.
 *
 * So rendering is an obligation at the service boundary ABOVE this one: a
 * caught database error must never be serialized to a response. Nothing in
 * `@/server/db` makes that safe, and this paragraph is prose in one file rather
 * than an enforced guarantee, which is a gap INV-07 has to close with a
 * deliberate mechanism rather than by remembering.
 */

import postgres from 'postgres';

import { isAuthoredRefusal, type Refusal } from '@/domain/errors';
import { isAuthoredActor, type Actor } from '@/server/actor';

export type Db = postgres.Sql;
export type Tx = postgres.TransactionSql;

/** SQLSTATE `unique_violation`. The only code this module maps. */
export const UNIQUE_VIOLATION = '23505';

/** SQLSTATE `serialization_failure`. Two transactions could not be ordered. */
export const SERIALIZATION_FAILURE = '40001';

/** SQLSTATE `deadlock_detected`. PostgreSQL broke a lock cycle by aborting one. */
export const DEADLOCK_DETECTED = '40P01';

/**
 * The transients. Neither means anything is broken: both are produced by
 * correct concurrent code, and the only thing that fixes them is replaying the
 * whole unit of work. Deadlock in particular needs no special isolation level
 * and happens at the READ COMMITTED this module uses.
 */
const RETRYABLE_CODES: ReadonlySet<string> = new Set([
  SERIALIZATION_FAILURE,
  DEADLOCK_DETECTED,
]);

const APP_URL_VAR = 'DATABASE_URL';

/**
 * Host spellings that mean "this machine". A connection to one of these never
 * reaches a network interface, so there is nothing between the client and the
 * server for anyone to sit in the middle of.
 */
const LOOPBACK: ReadonlySet<string> = new Set([
  'localhost',
  '127.0.0.1',
  '::1',
  '[::1]',
  '0.0.0.0',
]);

function isLoopbackTarget(parsed: URL): boolean {
  const host = parsed.hostname.toLowerCase();
  // A unix socket path is the other way of saying "this machine".
  if (host === '' || host.startsWith('/') || host.startsWith('%2f')) return true;
  if (LOOPBACK.has(host)) return true;
  // 127.0.0.0/8 is all loopback, not just 127.0.0.1.
  return /^127\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(host);
}

/**
 * Does this URL carry a TLS instruction postgres.js will ACT ON?
 *
 * NOT "is one of these keys present", which is the question `has()` answers and
 * the wrong one. `has('sslmode')` is true for a valueless `?sslmode=` — the
 * shape a half-finished hand edit leaves behind — so the module stepped aside
 * for a query string that said nothing, and the driver then fell through to its
 * `ssl: false` default. A remote connection string ending in `?sslmode=` sent
 * the password in cleartext. `?ssl=` did the same thing one step further along,
 * surviving as `''`, which is just as falsy.
 *
 * Both are the same bug, and the fix is to ask about the VALUE rather than the
 * key, because that is what the driver reads:
 *
 *   index.js:443  `query.sslmode && (query.ssl = query.sslmode, ...)`
 *                 — folded only when TRUTHY, so an empty sslmode is as good
 *                   as absent.
 *   index.js:446  `query.sslrootcert === 'system' && (query.ssl = 'verify-full')`
 *                 — the one value of a THIRD key that sets a mode.
 *   index.js:473  `k in o ? o[k] : k in query ? ... : default`
 *                 — presence-based, so an empty `ssl` survives as `''`.
 *
 * THE INVARIANT THAT CLOSES THIS, rather than patching the two spellings:
 * deferring is allowed only when the value is non-empty after trimming, and a
 * non-empty value cannot resolve to a falsy `ssl`. Where our reading and the
 * driver's disagree — a whitespace-only value, say — the disagreement can only
 * make the connection MORE encrypted, never less: we see nothing and apply
 * `verify-full`, or we defer and the driver has something truthy to act on.
 * There is no path from "we stepped aside" to "plaintext".
 *
 * A key we do not know about therefore cannot reopen this. It makes us apply
 * `verify-full` where the operator asked for something else, which is visible
 * and loud, instead of silently dropping encryption.
 */
function urlSpecifiesTls(parsed: URL): boolean {
  const params = parsed.searchParams;

  for (const key of ['sslmode', 'ssl']) {
    if ((params.get(key) ?? '').trim() !== '') return true;
  }

  return params.get('sslrootcert') === 'system';
}

/**
 * The TLS decision, and it belongs HERE, with the connection string, because
 * the connection string is the only thing that knows where it points.
 *
 * postgres.js defaults `ssl` to `false`, so leaving it alone sends the password
 * and every row across the public internet in cleartext.
 *
 * `verify-full` and not `require`. This is the part worth reading twice:
 * `connection.js:283` turns `require`, `allow` and `prefer` into
 * `rejectUnauthorized: false`, which is encryption with NO certificate
 * verification — anything that can get into the path presents its own
 * certificate and reads the lot. `verify-full` matches neither that branch nor
 * the object branch, so it reaches `tls.connect` with Node's default
 * `rejectUnauthorized: true` and a `servername` to check the certificate
 * against. Encryption without authentication is a comfortable-looking nothing.
 *
 * Two deliberate exits from the default, in priority order:
 *
 *   1. AN EXPLICIT CHOICE IN THE URL WINS, where "explicit" means a value the
 *      driver will act on and not merely a key that is present — see
 *      `urlSpecifiesTls`, which is where that distinction is argued. This is
 *      how an operator says something more specific than this module can know:
 *      a custom CA, a proxy, `disable` for a local server. Returning
 *      `undefined` leaves the option unset so the driver reads the URL,
 *      because an explicit option here would silently BEAT the query string
 *      (`index.js:473`: `k in o ? o[k] : k in query`), making the documented
 *      escape hatch a no-op.
 *   2. LOOPBACK STAYS PLAINTEXT. Not a special case for anyone's hostname: the
 *      rule is that bytes which never leave the machine have no wire to be
 *      intercepted on, and demanding a verified certificate from a local
 *      PostgreSQL would break every developer machine and this test suite,
 *      which is the documented setup in `docker-compose.yml` and `.env.example`.
 *      Anything that is NOT this machine crosses a network and must verify.
 *
 * An unparseable string gets the strict default: if we cannot tell where it
 * points, it is not local.
 */
function resolveSsl(connectionString: string): 'verify-full' | undefined {
  let parsed: URL;
  try {
    parsed = new URL(connectionString);
  } catch {
    return 'verify-full';
  }

  if (urlSpecifiesTls(parsed)) return undefined;
  if (isLoopbackTarget(parsed)) return undefined;

  return 'verify-full';
}

/**
 * The options every client in the application gets.
 *
 * `prepare: false` is not a tuning choice. Supabase's pooler in transaction
 * mode hands a different backend to each transaction, so a named prepared
 * statement parsed on one is not there when the next statement binds it.
 * Against a direct connection prepared statements work perfectly, which makes
 * this the worst failure shape there is: correct locally, broken in production.
 *
 * `max: 1` and a short `idle_timeout` are about WHERE THIS RUNS. On Vercel each
 * instance holds its own pool and freezes between invocations while still
 * holding the sockets, so a pool of 10 is 10 server-side connections consumed
 * by an instance doing nothing, and ten warm instances is a hundred. Supabase's
 * pooler has a `default_pool_size` far below that, and past it NEW instances
 * cannot connect at all: the site is down for everyone rather than slow for
 * someone. One connection per instance, released after a few idle seconds,
 * keeps a frozen instance from holding anything. postgres.js reconnects
 * transparently when the next query arrives, which is pinned.
 */
function clientOptions(
  connectionString: string,
): postgres.Options<Record<string, postgres.PostgresType>> {
  const ssl = resolveSsl(connectionString);

  return {
    prepare: false,
    max: 1,
    idle_timeout: 2,
    // Spread rather than `ssl: resolveSsl(...)`, and the difference is the
    // whole of exit 1 above: postgres.js tests `k in o`, and `'ssl' in {ssl:
    // undefined}` is TRUE. Setting the key to undefined would therefore beat
    // the query string with undefined and turn `?sslmode=` into a no-op.
    ...(ssl === undefined ? {} : { ssl }),
  };
}

/**
 * A fresh client on every call, for the callers that must not touch the
 * singleton: the integration suite, and anything pointing at an explicit URL.
 */
export function createDb(connectionString: string): Db {
  return postgres(connectionString, clientOptions(connectionString));
}

let client: Db | undefined;

/**
 * The application client. One per process: postgres.js is already a pool, so a
 * client per call would mean a pool per call.
 *
 * The missing-variable error is an ordinary Error on purpose. A missing
 * deployment variable is an operator fault and must never reach a user as a
 * message, which is exactly what a refusal is for.
 */
export function getDb(): Db {
  if (client === undefined) {
    const connectionString = process.env[APP_URL_VAR]?.trim() ?? '';
    if (connectionString === '') {
      throw new Error(
        `${APP_URL_VAR} is not set, so there is no database to connect to. ` +
          `Set ${APP_URL_VAR} in the environment (or in .env.local for local development).`,
      );
    }
    client = createDb(connectionString);
  }

  return client;
}

/**
 * Ends the singleton and forgets it, so the next `getDb()` builds a new one.
 * Idempotent: with nothing open it resolves quietly.
 *
 * The reference is cleared BEFORE awaiting, so a `closeDb()` that overlaps
 * another cannot end up handing a half-closed client to a concurrent `getDb()`.
 */
export async function closeDb(): Promise<void> {
  const closing = client;
  client = undefined;
  if (closing !== undefined) {
    await closing.end();
  }
}

/** Reads a string property off an unknown value without widening to `any`. */
function readString(value: unknown, key: string): string | undefined {
  if (typeof value !== 'object' || value === null) return undefined;
  const raw = (value as Record<string, unknown>)[key];
  return typeof raw === 'string' ? raw : undefined;
}

/**
 * True for SQLSTATE 23505 and nothing else. A not-null violation (23502) or an
 * undefined table (42P01) is a FAULT: schema drift, a bad migration, a bug.
 * Dressing one up as a polite refusal would hide the message an operator needs.
 *
 * A serialization failure is NOT in that list. See `isRetryable`.
 */
export function isUniqueViolation(error: unknown): boolean {
  return readString(error, 'code') === UNIQUE_VIOLATION;
}

/**
 * True for the two retryable transients, 40001 and 40P01.
 *
 * THIS SLICE SHIPS THE PREDICATE AND NO RETRY POLICY, deliberately. A retry
 * needs to decide how many attempts, what backoff, which operations are safe to
 * replay and what a user sees when the attempts run out, and none of that can
 * be designed without a caller. What this slice owes the next one is an honest
 * NAME for the category, so nobody has to rediscover which SQLSTATEs belong in
 * it. This is the seam a retry gets built on.
 *
 * Calling these faults would be the expensive mistake: it tells the next
 * implementer to go hunting for a bug in code that is already correct, and to
 * skip the one response that actually works.
 */
export function isRetryable(error: unknown): boolean {
  const code = readString(error, 'code');
  return code !== undefined && RETRYABLE_CODES.has(code);
}

export interface UniqueViolation {
  readonly constraint: string | undefined;
  readonly table: string | undefined;
}

/**
 * The constraint and table behind a 23505, so the caller can tell one index
 * from another. `undefined` for anything that is not a unique violation.
 */
export function describeUniqueViolation(error: unknown): UniqueViolation | undefined {
  if (!isUniqueViolation(error)) return undefined;

  return {
    constraint: readString(error, 'constraint_name'),
    table: readString(error, 'table_name'),
  };
}

/* ==========================================================================
 * THE ACTOR SEAM — INV17-B9, recording TD-11 and TD-12.
 *
 * The contract, and the reasoning for every decision below, is the header of
 * `tests/integration/actor-seam.test.ts`. Read it before changing anything
 * here; the comments in this section are navigation, not the argument.
 *
 * THE HONEST LIMITS, stated where the code is rather than only in the test,
 * because a control believed to do more than it does is how INV-17 shipped a
 * guard that never ran:
 *
 *   L1. The scope check proves the actor's id was BOUND INTO the statement. It
 *       does NOT prove the binding constrains the result. A repository can
 *       satisfy it with `select ${tx.actorId} as me, * from listings`, which
 *       leaks everything. Review and the lint allow-list cover that step.
 *   L2. `authorize` is OPT-IN, so TD-12 is conditional on the caller. A service
 *       that reads a grant on the ordinary tag, gates a write on the result and
 *       commits passes every check here and is exactly the write skew TD-12
 *       forbids. The seam MAKES the guarantee available; it does not IMPOSE it.
 *       Both limits are pinned by execution under 'what this seam does not
 *       guarantee' in the test file, so they cannot quietly grow back.
 * ========================================================================== */

/**
 * A programmer fault at the seam: a query that forgot its actor, an `authorize`
 * with no row lock, an `unscoped()` with no reason.
 *
 * NOT a refusal. No user can act on it and no user may ever see it, which is
 * why it is a plain `Error` subclass and not a member of the refusal catalogue.
 */
export class ActorScopeError extends Error {
  override readonly name = 'ActorScopeError';
}

/**
 * A handle used after its transaction ended. Separate from `ActorScopeError`
 * and never a subclass of it, because a correctly-scoped query on a dead handle
 * must not send the next reader hunting for a filter that is right there.
 */
export class ClosedHandleError extends Error {
  override readonly name = 'ClosedHandleError';
}

/**
 * The one fixed spelling of the door out, exported so the lint rule, the tests
 * and this module agree on the string rather than each hard-coding it.
 */
export const UNSCOPED_TOKEN = 'unscoped';

declare const ACTOR_REF: unique symbol;

/**
 * The per-handle marker a scoped query must interpolate. Opaque on purpose:
 * its only job is to be recognised by identity.
 */
export type ActorRef = { readonly [ACTOR_REF]: true };

const SCOPE_MESSAGE =
  'This query was issued on an actor-scoped handle but never interpolated tx.actorId, ' +
  'so the actor was not bound into the statement and it was not sent. Put ${tx.actorId} ' +
  'in the where clause. If the query is deliberately not actor-scoped, take the one ' +
  `documented door out and say why: tx.${UNSCOPED_TOKEN}('public listing search, FR-1706').`;

const LOCK_MESSAGE =
  'An authorize read must take a row lock, or the permission it reads can be revoked and ' +
  'committed between the read and the write it guards (TD-12). End the statement with ' +
  '"for share", "for update", "for no key update" or "for key share". The check reads the ' +
  'static template text only, so a lock clause inside a comment, a quoted literal or a ' +
  'bound value does not count: none of those is executed by the server.';

const CLOSED_MESSAGE =
  'This handle belongs to a transaction that has already ended, so the statement was not ' +
  'sent. A handle captured in a closure outlives its transaction and would then run on a ' +
  'pooled connection since handed to another request, carrying an actor that finished.';

const REASON_MESSAGE =
  `tx.${UNSCOPED_TOKEN}(reason) requires a non-empty reason. The reason is the review ` +
  `artefact: an ${UNSCOPED_TOKEN}() nobody has to justify is a bypass, not an exception.`;

/**
 * Removes from SQL every region PostgreSQL does not execute as SQL, so the
 * row-lock scan cannot be satisfied by text the server ignores.
 *
 * B9-R1 demonstrated the failure this exists to stop:
 *
 *     tx.authorize`select 1 from grants where user_id = ${tx.actorId} -- TODO: for share`
 *
 * No lock is taken, a substring scan passes, and TD-12 is silently unmet by a
 * line that reads like a reminder to do the thing it is standing in for.
 *
 * Each removed region leaves whitespace behind rather than nothing, so the word
 * boundaries either side survive and a comment cannot fuse two tokens into a
 * third. The failure direction is checked both ways in the test file: a real
 * `for share` that merely FOLLOWS a comment must still be accepted, because a
 * guard corrected into refusing what it exists to allow is the way this project
 * has already broken one.
 */
function stripNonExecutable(sql: string): string {
  let out = '';
  let index = 0;

  while (index < sql.length) {
    const pair = sql.slice(index, index + 2);

    // `-- ...` to end of line.
    if (pair === '--') {
      const newline = sql.indexOf('\n', index);
      index = newline === -1 ? sql.length : newline;
      out += ' ';
      continue;
    }

    // `/* ... */`, which PostgreSQL allows to nest.
    if (pair === '/*') {
      let depth = 1;
      index += 2;
      while (index < sql.length && depth > 0) {
        const inner = sql.slice(index, index + 2);
        if (inner === '/*') {
          depth += 1;
          index += 2;
        } else if (inner === '*/') {
          depth -= 1;
          index += 2;
        } else {
          index += 1;
        }
      }
      out += ' ';
      continue;
    }

    // `$tag$ ... $tag$`. A dollar-quoted body is data and may contain anything.
    const dollar = /^\$([A-Za-z_][A-Za-z0-9_]*)?\$/.exec(sql.slice(index));
    if (dollar !== null) {
      const tag = dollar[0];
      const close = sql.indexOf(tag, index + tag.length);
      index = close === -1 ? sql.length : close + tag.length;
      out += ' ';
      continue;
    }

    // `'...'` with `''` as the embedded quote, and `"..."` the same way for a
    // quoted identifier. Both are data or a name, never a lock clause.
    const quote = sql[index];
    if (quote === "'" || quote === '"') {
      index += 1;
      while (index < sql.length) {
        if (sql[index] === quote) {
          if (sql[index + 1] === quote) {
            index += 2;
            continue;
          }
          index += 1;
          break;
        }
        index += 1;
      }
      out += ' ';
      continue;
    }

    out += quote;
    index += 1;
  }

  return out;
}

const ROW_LOCK = /\bfor\s+(?:share|update|no\s+key\s+update|key\s+share)\b/i;

/**
 * Does the STATIC text of this template carry a row-lock clause?
 *
 * The `strings` array only, never the interpolated values, because a bound
 * value containing the words "for share" is data: a listing title must not be
 * able to talk its way past the check. The chunks are joined with a placeholder
 * rather than concatenated, so a value sitting between "for" and "share"
 * cannot fuse into a lock clause that was never written.
 */
function hasRowLock(strings: TemplateStringsArray): boolean {
  return ROW_LOCK.test(stripNonExecutable(strings.join(' ? ')));
}

/**
 * The actor-scoped transaction handle. The whole surface, and nothing else:
 * no `unsafe`, no `savepoint`, no `begin`, no `end`, no `file`, no `json`.
 * Those are how a bypass gets written without anyone typing the word
 * `unscoped`, and a savepoint handle in particular is a fully functional
 * unscoped client rather than a nuisance method.
 */
export interface ActorTx {
  /** The actor-scoped query tag. Requires `${tx.actorId}` to be interpolated. */
  <T extends readonly postgres.MaybeRow[] = postgres.Row[]>(
    strings: TemplateStringsArray,
    ...values: readonly unknown[]
  ): postgres.PendingQuery<T>;

  /** The authorization read: the same scope rule, plus a required row lock. */
  readonly authorize: <T extends readonly postgres.MaybeRow[] = postgres.Row[]>(
    strings: TemplateStringsArray,
    ...values: readonly unknown[]
  ) => postgres.PendingQuery<T>;

  readonly actor: Actor;
  /** Interpolate this, not `actor.userId`. Compared by identity. */
  readonly actorId: ActorRef;

  unscoped(reason: string): Tx;
}

interface ActorHandle {
  readonly tx: ActorTx;
  /** Called once the transaction has settled, either way. */
  close(): void;
}

/**
 * A rejected query rather than a synchronous throw.
 *
 * The tag is called in places a `throw` would not reach the caller as a
 * rejection — `escaped\`select 1\`.catch(...)` on a handle that outlived its
 * transaction is the one in the test file — and a tag that sometimes throws and
 * sometimes returns a promise is a worse surface than one that always returns.
 * Nothing is sent to the server either way.
 */
function rejectedQuery<T extends readonly postgres.MaybeRow[]>(
  error: Error,
): postgres.PendingQuery<T> {
  return Promise.reject(error) as unknown as postgres.PendingQuery<T>;
}

/**
 * Binds one actor to one live transaction handle.
 *
 * THE ACTOR IS A CLOSURE VARIABLE, which is the whole of DECISION 7 and half of
 * criterion 5. Not `set_config`, not `set`, not an AsyncLocalStorage, not a
 * module-level `currentActor`. Session-level state survives the commit and
 * stays on the pooled connection, so the next request on a warm instance starts
 * with the previous user's identity installed; a module-level variable is
 * overwritten by the second concurrent request while the first is still in
 * flight. Both are pinned by execution in the test file.
 */
function createActorHandle(actor: Actor, raw: Tx): ActorHandle {
  let closed = false;

  /**
   * Per-handle, and an object compared by identity, for three reasons. It
   * cannot be forged, because a string compare against `actor.userId` would be
   * satisfied by any query mentioning that id for any reason, including one
   * selecting it as a column value. It cannot be hoisted, because a
   * module-level marker shared by every handle could be smuggled into a query
   * built for a different actor. And it reads at the call site as what it is.
   */
  const marker = Object.freeze({}) as unknown as ActorRef;

  /**
   * Substitutes the marker with the actor's real user id, or reports that the
   * statement never carried it. The marker of ANOTHER handle is simply not this
   * one, so a stolen marker — from a settled handle or from one open right now
   * in a concurrent transaction — fails the same way.
   */
  function bind(values: readonly unknown[]): unknown[] | undefined {
    let found = false;
    const bound = values.map((value) => {
      if (value === marker) {
        found = true;
        return actor.userId;
      }
      return value;
    });

    return found ? bound : undefined;
  }

  function scoped<T extends readonly postgres.MaybeRow[] = postgres.Row[]>(
    strings: TemplateStringsArray,
    ...values: readonly unknown[]
  ): postgres.PendingQuery<T> {
    // The closed check runs FIRST, so a correctly-scoped query on a dead handle
    // reports the real problem rather than a scope mistake it did not make.
    if (closed) return rejectedQuery<T>(new ClosedHandleError(CLOSED_MESSAGE));

    const bound = bind(values);
    if (bound === undefined) return rejectedQuery<T>(new ActorScopeError(SCOPE_MESSAGE));

    return raw<T>(strings, ...(bound as never[]));
  }

  function authorize<T extends readonly postgres.MaybeRow[] = postgres.Row[]>(
    strings: TemplateStringsArray,
    ...values: readonly unknown[]
  ): postgres.PendingQuery<T> {
    if (closed) return rejectedQuery<T>(new ClosedHandleError(CLOSED_MESSAGE));

    const bound = bind(values);
    if (bound === undefined) return rejectedQuery<T>(new ActorScopeError(SCOPE_MESSAGE));
    if (!hasRowLock(strings)) return rejectedQuery<T>(new ActorScopeError(LOCK_MESSAGE));

    return raw<T>(strings, ...(bound as never[]));
  }

  /**
   * The underlying transaction, wrapped so that it dies with the transaction
   * too. The door out must not also be a door past DECISION 6.
   */
  function unscopedHandle(): Tx {
    const target = raw as unknown as (...args: unknown[]) => unknown;

    return new Proxy(target, {
      apply(fn, _thisArg, args) {
        if (closed) return Promise.reject(new ClosedHandleError(CLOSED_MESSAGE));
        return Reflect.apply(fn, raw, args);
      },
      get(fn, property) {
        const value = Reflect.get(fn, property) as unknown;
        return typeof value === 'function'
          ? (value as (...args: unknown[]) => unknown).bind(raw)
          : value;
      },
    }) as unknown as Tx;
  }

  function unscoped(reason: string): Tx {
    // Synchronous throws here, not rejections: `unscoped` returns a handle
    // rather than a query, so there is no promise for a caller to catch.
    if (closed) throw new ClosedHandleError(CLOSED_MESSAGE);
    if (typeof reason !== 'string' || reason.trim() === '') {
      throw new ActorScopeError(REASON_MESSAGE);
    }

    return unscopedHandle();
  }

  const handle = scoped as unknown as Record<string, unknown>;
  const fixed = { writable: false, enumerable: false, configurable: false } as const;

  // Non-writable and non-configurable: a callback that assigns `tx.actor`
  // throws in strict mode rather than acting as someone else for the rest of
  // the transaction.
  Object.defineProperties(handle, {
    authorize: { value: authorize, ...fixed },
    actor: { value: actor, ...fixed },
    actorId: { value: marker, ...fixed },
    unscoped: { value: unscoped, ...fixed },
  });

  return {
    tx: handle as unknown as ActorTx,
    close: () => {
      closed = true;
    },
  };
}

export interface TransactionOptions {
  readonly db?: Db;
  /**
   * Called ONLY for SQLSTATE 23505, and handed the constraint name so it can
   * discriminate between indexes.
   *
   * It MAY DECLINE, by returning `undefined`, and a declining mapper is the
   * normal case rather than the exotic one: a mapper is written for the indexes
   * its author knew about, and a multi-write operation will routinely raise a
   * 23505 from a table that author never considered. Without a way to say "not
   * mine" the mapper answers anyway, and the user reads something false about a
   * table it has never heard of, with values nobody looked up. FR-1712 requires
   * a refusal to state what is ACTUALLY wrong using the REAL values involved,
   * so a mapper that cannot decline cannot be correct.
   *
   * A declined violation propagates as the original driver error. That is ugly
   * on purpose: an unmapped 23505 is a constraint nobody wrote a message for,
   * which is a gap in the code, and it should read like one in the logs rather
   * than be smoothed over for a user who cannot act on it.
   */
  readonly onUniqueViolation?: (violation: UniqueViolation) => Refusal | undefined;
}

/**
 * One transaction, one connection, one actor-bound handle for every repository
 * in the operation. Commits once if `run` resolves, rolls back once if it
 * throws.
 *
 * THE ACTOR IS THE FIRST PARAMETER AND THERE IS NO OVERLOAD WITHOUT IT, which
 * is the whole of criterion 1. The comfortable design — leave `withTransaction`
 * alone and add `withActorTransaction` beside it — would leave every repository
 * one import away from the unscoped door and make the seam a convention rather
 * than a control. It is not an option on `TransactionOptions` either: an option
 * is a thing a caller may omit, and this is the one argument that must never be
 * omittable.
 *
 * Gating this function is necessary and NOT sufficient. `getDb()` is exported
 * from this module and nobody has to walk through here at all, so DECISION 8 —
 * the lint rule in `eslint.config.mjs` forbidding `getDb` and `createDb`
 * imports under `src/` outside this seam — is what actually closes criterion 2.
 */
export async function withTransaction<T>(
  actor: Actor,
  run: (tx: ActorTx) => Promise<T>,
  options: TransactionOptions = {},
): Promise<T> {
  // BEFORE the client is even resolved, so a forged actor reaches no SQL at
  // all — not a statement, not a BEGIN. `Actor`'s unique symbol stops a hand
  // built one at compile time, so the only way here is a cast or plain
  // JavaScript, which is exactly what a runtime check exists for.
  if (!isAuthoredActor(actor)) {
    throw new TypeError(
      'withTransaction requires an Actor produced by actorFromSession in @/server/actor. ' +
        'An object that merely has the right shape is not one: per FR-1704 the only ' +
        'authority to act as a user comes from that user’s own session.',
    );
  }

  const db = options.db ?? getDb();

  try {
    // The cast corrects a declared return type that is wrong for this call
    // shape. `begin` is declared `Promise<UnwrapPromiseArray<T>>`, and that
    // conditional DOES fire: it keys on `T extends any[]`, where `T` is the
    // already-awaited payload, so a repository returning rows lands in the true
    // branch and the declared type stops matching `Promise<T>`. The necessity
    // of the cast is the proof — without it this does not compile (TS2322).
    //
    // It is nonetheless safe, for a reason the type cannot see. The declaration
    // describes a RUNTIME branch that keys on something else: `Array.isArray(x)`
    // where `x` is the raw callback return, before awaiting. `run` is
    // `(tx) => Promise<T>`, so `x` is always a Promise, that branch never runs,
    // and the value handed back is the one `run` resolved with. Array identity
    // is pinned in `db-transaction.test.ts` for both a bare array and a query
    // result, so if the signature is ever widened to accept a synchronous
    // callback, the tests fail rather than this comment going quietly stale.
    //
    // The handle is closed in a `finally` rather than after `begin` resolves,
    // so it is dead from the moment `run` settles: there is no window in which
    // a captured handle still works while the transaction is committing.
    return (await db.begin(async (raw) => {
      const bound = createActorHandle(actor, raw as Tx);
      try {
        return await run(bound.tx);
      } finally {
        bound.close();
      }
    })) as T;
  } catch (error) {
    // An authored refusal leaves here untouched, by identity.
    //
    // WHAT THIS LINE ACTUALLY BUYS, stated honestly because three rounds of
    // review have caught this comment claiming more: deleting it changes no
    // observable behaviour today, and no test can tell the difference. A
    // refusal's `code` is one of the six in REFUSAL_CODES, never the string
    // '23505', so `describeUniqueViolation` below returns undefined for one and
    // the fallthrough rethrows it unchanged anyway.
    //
    // It is kept for two reasons that are about the next edit rather than this
    // one. It states the precedence explicitly where a reader looks for it, and
    // this catch block is where a retry branch will go when `isRetryable` gets
    // its policy; whoever writes that must not capture a refusal on the way
    // past. The guarantee it names is real and tested, it is simply the
    // fallthrough that enforces it today.
    if (isAuthoredRefusal(error)) throw error;

    const violation = describeUniqueViolation(error);
    if (violation === undefined || options.onUniqueViolation === undefined) {
      // No mapper, or not a unique violation: the helper does not pretend to
      // have handled it, and does not invent a message it cannot author well.
      throw error;
    }

    // A mapper that throws is left alone: a bug inside it (a bad lookup, a null
    // dereference) must stay visible rather than be swapped for the 23505,
    // which would hide the real fault behind a symptom.
    const refusal = options.onUniqueViolation(violation);

    // Declined: not this mapper's constraint. The original driver error stands.
    if (refusal === undefined) throw error;

    if (!isAuthoredRefusal(refusal)) {
      // `undefined` is a supported answer; any OTHER non-refusal is a bug at
      // the boundary. Throwing the value instead would let a forged refusal
      // reach a user as a message.
      throw new TypeError(
        'onUniqueViolation must return a refusal from the catalogue in @/domain/errors, ' +
          'or undefined to decline.',
      );
    }

    throw refusal;
  }
}
