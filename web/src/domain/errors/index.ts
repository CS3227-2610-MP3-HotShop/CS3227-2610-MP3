/**
 * Refusals: the single way domain code says no.
 *
 * FR-1712 — every refusal states what is wrong and what to do next, using the
 * real values involved, and never exposes SQL, stack traces or framework error
 * text.
 * FR-1713 — six reason categories, shared by every service.
 *
 * Three decisions shape this module.
 *
 * AUTHORING IS CATALOGUE-ONLY. The constructor is module-internal, guarded by a
 * module-private token, and `Refusal` is a type-only export. The catalogue
 * below is the only code that can author a refusal message, and its entries
 * take typed, non-string parameters, so a caller cannot hand a message a stack,
 * a raw SQL string or a Date rendered in the host zone.
 *
 *   The honest scope of that claim. Removing the export alone did NOT make an
 *   SQL-carrying refusal inexpressible, and two escapes were demonstrated:
 *     (a) `Object.getPrototypeOf(someRefusal).constructor`, and the equivalent
 *         `someRefusal.constructor`, hand any caller this class. That route is
 *         CLOSED: the constructor demands the private token and throws a
 *         TypeError without it.
 *     (b) a plain object literal carrying the exported brand and code satisfies
 *         `isRefusal` and typechecks as a `RefusalPayload`. That route CANNOT
 *         be closed while the wire format needs a serializable brand: anything
 *         a client can construct, anything can construct. It is not chased.
 *         `isAuthoredRefusal` pins the real distinction instead.
 *
 * INVALID INPUT THROWS, NEVER RENDERS. `Number.isSafeInteger` is the single
 * predicate at every entry point, so there is a single error type. A TypeError
 * is a programmer error and is deliberately NOT a refusal, so it can never
 * reach a user as a message.
 *
 * RECOGNITION IS BRAND-BASED. A refusal raised in a server action is serialized
 * across the Next.js boundary and rebuilt as a plain object, where `instanceof`
 * is false but the brand survives. `asPayload()` is the supported transport.
 */

export const REFUSAL_CODES = [
  'VALIDATION',
  'NOT_FOUND',
  'PERMISSION',
  'INVALID_STATE',
  'SESSION',
  'STORAGE',
] as const;

export type RefusalCode = (typeof REFUSAL_CODES)[number];

export const REFUSAL_BRAND = 'hotshop/refusal';

export interface RefusalPayload {
  readonly brand: typeof REFUSAL_BRAND;
  readonly code: RefusalCode;
  readonly message: string;
}

/**
 * Type-only export: there is no exported constructor. See the authoring note
 * at the top of the file.
 */
export type Refusal = Error &
  RefusalPayload & {
    readonly name: 'Refusal';
    readonly message: string;
    asPayload(): RefusalPayload;
    toJSON(): RefusalPayload;
  };

export type RefusalLike = Refusal | RefusalPayload;

/**
 * Never exported, so it cannot be presented by a caller who reached the class
 * through `refusal.constructor`.
 */
const AUTHORING_TOKEN = Symbol('hotshop/refusal-authoring');

/**
 * Membership is the unforgeable record of what this module authored. A WeakSet
 * rather than `instanceof` because it is realm-independent: the same value
 * crossing a `node:vm` boundary or a jsdom realm still answers correctly.
 */
const AUTHORED = new WeakSet<object>();

/**
 * Module-internal. `brand` and `code` are ordinary own enumerable fields.
 * `message` and `name` need `defineProperty` because `Error.prototype.message`
 * and `.name` are non-enumerable, which would drop the message out of a spread
 * and let the name leak into one.
 *
 * `super()` takes no argument on purpose: passing the message there would
 * establish an own `message` slot FIRST, and own string keys keep insertion
 * order, so every transport would serialize as {message, brand, code} while
 * `asPayload()` serialized as {brand, code, message}. Byte-identical JSON
 * matters for cache keys and snapshots. The stack header is unaffected, since
 * V8 formats it lazily and reads the message at that point.
 */
class RefusalError extends Error {
  readonly brand: typeof REFUSAL_BRAND;
  readonly code: RefusalCode;

  declare readonly name: 'Refusal';

  constructor(token: symbol, code: RefusalCode, message: string) {
    if (token !== AUTHORING_TOKEN) {
      throw new TypeError(
        'Refusals are authored through the catalogue in @/domain/errors, not by construction.',
      );
    }
    super();

    this.brand = REFUSAL_BRAND;
    this.code = code;

    Object.defineProperty(this, 'message', {
      value: message,
      enumerable: true,
      writable: false,
      configurable: true,
    });
    Object.defineProperty(this, 'name', {
      value: 'Refusal',
      enumerable: false,
      writable: false,
      configurable: true,
    });

    AUTHORED.add(this);
  }

  /** The supported transport. Nothing should depend on which serializer runs. */
  asPayload(): RefusalPayload {
    return { brand: this.brand, code: this.code, message: this.message };
  }

  toJSON(): RefusalPayload {
    return this.asPayload();
  }
}

function refusal(code: RefusalCode, message: string): Refusal {
  return new RefusalError(AUTHORING_TOKEN, code, message);
}

/** `Object.hasOwn` with the narrowing the standard lib signature does not give. */
function hasOwnKey<K extends string>(value: object, key: K): value is Record<K, unknown> {
  return Object.hasOwn(value, key);
}

/**
 * "Is this refusal-SHAPED data." All that can be known about a value that
 * crossed a wire, and the right question at a render site.
 *
 * `Object.hasOwn` rather than `in`, because `in` walks the prototype chain, so
 * a brand on a shared prototype would make everything derived from it a
 * refusal. Arrays are rejected outright: a refusal is never a list.
 * `Object.hasOwn` handles a null-prototype wire object perfectly well, so there
 * is no reason to prefer `in` for that case either.
 *
 * `structuredClone` of a refusal fails this check by design: the spec's Error
 * path drops own properties, so the brand is gone and the clone is rejected
 * rather than half-rebuilt. Clone `asPayload()` instead.
 */
export function isRefusal(value: unknown): value is RefusalLike {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
  if (!hasOwnKey(value, 'brand')) return false;
  if (!hasOwnKey(value, 'code')) return false;
  if (!hasOwnKey(value, 'message')) return false;

  const { brand, code, message } = value;

  return (
    brand === REFUSAL_BRAND &&
    typeof message === 'string' &&
    REFUSAL_CODES.some((candidate) => candidate === code)
  );
}

/**
 * "Did THIS module's catalogue author this value." The narrower question, and
 * unforgeable from outside. Require it wherever a value is about to be trusted
 * as a domain refusal rather than treated as attacker-influenced input; a
 * payload off the wire is data and correctly answers false.
 */
export function isAuthoredRefusal(value: unknown): value is Refusal {
  return typeof value === 'object' && value !== null && AUTHORED.has(value);
}

/** Avoids String() on an arbitrary value, which throws for a symbol. */
function describeValue(value: unknown): string {
  return typeof value === 'number' ? String(value) : typeof value;
}

function requireSafeInteger(value: number, label: string): void {
  if (!Number.isSafeInteger(value)) {
    throw new TypeError(`${label} must be a safe integer, received ${describeValue(value)}`);
  }
}

/** Explicit locale: the host locale must not change what a user reads. */
const COUNT_FORMAT = new Intl.NumberFormat('en-US');

export function formatCount(value: number): string {
  requireSafeInteger(value, 'count');

  return COUNT_FORMAT.format(value);
}

/**
 * Singapore dollars. The dollars and cents are split with integer arithmetic
 * rather than `cents / 100`, so no binary-floating-point residue can reach the
 * string, and the prefix is applied deliberately: `Intl` renders SGD as
 * `$40.00` or `SGD 40.00`, never `S$40.00`.
 */
export function formatMoney(cents: number): string {
  requireSafeInteger(cents, 'cents');

  const sign = cents < 0 ? '-' : '';
  const absolute = Math.abs(cents);
  const remainder = absolute % 100;
  const dollars = (absolute - remainder) / 100;

  return `${sign}S$${formatCount(dollars)}.${String(remainder).padStart(2, '0')}`;
}

export const refusals = {
  duplicatePendingOffer(existingAmountCents: number): Refusal {
    return refusal(
      'INVALID_STATE',
      `You already have a pending offer of ${formatMoney(existingAmountCents)} on this listing. ` +
        'Withdraw it before making a new one.',
    );
  },

  offerOnUnavailableListing(): Refusal {
    return refusal('INVALID_STATE', 'Only available listings can receive offers.');
  },

  messageTooLong(actualLength: number, limit: number): Refusal {
    return refusal(
      'VALIDATION',
      `Messages can be at most ${formatCount(limit)} characters, ` +
        `but this one has ${formatCount(actualLength)}.`,
    );
  },
};
