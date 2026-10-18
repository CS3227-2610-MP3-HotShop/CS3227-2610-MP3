/**
 * CONTRACT FOR THE IMPLEMENTER — module `@/domain/errors`
 * =======================================================
 *
 * DECISION 1 — AUTHORING IS CATALOGUE-ONLY. The `Refusal` constructor is NOT
 * exported. `Refusal` is exported as a TYPE only. The catalogue is the sole way
 * to author a refusal message.
 *
 *   Why: FR-1712 (every refusal states real values, and leaks no SQL, stack or
 *   framework detail) is a property of EVERY refusal, and a public constructor
 *   makes it a convention enforced by review. Two breaches were demonstrated
 *   against the previous design and both typechecked and linted clean:
 *   `new Refusal('STORAGE', err.stack)` and a template literal interpolating a
 *   raw Date, which renders the host zone and breaches FR-1718/FR-1720. A lint
 *   rule confining `new Refusal` to this module is strictly weaker evidence
 *   than making the breach unrepresentable, and lint rules get disabled.
 *   Catalogue entries take TYPED, NON-STRING parameters, so no caller can hand
 *   a message a Date, a stack or a raw SQL string in the first place.
 *
 *   SCOPE OF THAT CLAIM, stated honestly. Removing the export does NOT by
 *   itself make an SQL-carrying refusal inexpressible. Two escapes exist:
 *     (a) `Object.getPrototypeOf(someRefusal).constructor` (and the equivalent
 *         `someRefusal.constructor`) hands any caller the internal class.
 *         This one IS closable: the internal constructor must require a
 *         module-private token that only the catalogue holds, and must throw
 *         for a caller that cannot present it. Pinned below.
 *     (b) a plain object literal carrying the exported brand and code passes
 *         isRefusal and typechecks as a RefusalPayload. This one is NOT
 *         closable while the wire format needs a serializable brand: anything
 *         a client can construct, anything can construct. It is not chased.
 *         The honest distinction is pinned instead, via isAuthoredRefusal.
 *
 *   Cost, accepted: every later feature adds its own catalogue entries, and the
 *   taxonomy can only be exercised over the codes the catalogue actually uses.
 *   NOT_FOUND, PERMISSION, SESSION and STORAGE are declared but unreached until
 *   the features that raise them land. That is reported as a known gap rather
 *   than papered over by constructing refusals the application never makes.
 *
 * DECISION 2 — INVALID INPUT THROWS TypeError, NEVER RENDERS. Every public
 * entry point rejects anything that is not a safe integer with a TypeError.
 * `Number.isSafeInteger` is the single predicate, so there is a single error
 * type. A TypeError is a programmer error and MUST NOT be a refusal:
 * `isRefusal(thrown)` is false, so it can never reach a user as a message.
 *
 *   Why it matters: `formatMoney(4000.004)` previously returned
 *   'S$40.0.0039999999999054126', and `duplicatePendingOffer(NaN)` rendered
 *   'S$NaN.NaN'. This becomes a live path once Supabase is wired, because a
 *   numeric column arrives as a string and a null column arrives as null, and
 *   both land on NaN. Failing loudly at the boundary is the only safe answer.
 *
 *   export const REFUSAL_CODES: readonly [
 *     'VALIDATION', 'NOT_FOUND', 'PERMISSION', 'INVALID_STATE', 'SESSION', 'STORAGE'
 *   ];
 *   export type RefusalCode = (typeof REFUSAL_CODES)[number];
 *
 * DECISION 3 — RECOGNITION IS BRAND-BASED, NOT instanceof, and transport is
 * explicit. A refusal raised in a server action is serialized across the
 * Next.js boundary, where `instanceof` is false. Verified serializer behaviour,
 * which the tests below pin exactly:
 *   - `{...refusal}` / `Object.assign({}, refusal)` -> {brand, code, message}
 *     ONLY IF `message` is an own ENUMERABLE property (Error.prototype.message
 *     is non-enumerable, so a bare subclass loses it) and `name` is made
 *     NON-enumerable via defineProperty so it stays out of the spread.
 *   - `JSON.stringify` -> the payload.
 *   - `structuredClone(refusal)` DROPS brand and code and resets name to
 *     'Error'. That cannot be fixed; the spec fixes Error cloning. So a cloned
 *     refusal must FAIL CLOSED: isRefusal() returns false rather than yielding
 *     a half-built refusal. `structuredClone(refusal.asPayload())` is the
 *     supported path.
 *   - `asPayload()` is the explicit transport so nothing depends on which
 *     serializer happens to run.
 *
 *   export const REFUSAL_BRAND: 'hotshop/refusal';
 *   export interface RefusalPayload {
 *     readonly brand: typeof REFUSAL_BRAND;
 *     readonly code: RefusalCode;
 *     readonly message: string;
 *   }
 *   // Type-only export. There is no exported constructor.
 *   export type Refusal = Error & RefusalPayload & {
 *     readonly name: 'Refusal';          // own, NON-enumerable
 *     readonly message: string;          // own, ENUMERABLE
 *     asPayload(): RefusalPayload;
 *     toJSON(): RefusalPayload;
 *   };
 *   export type RefusalLike = Refusal | RefusalPayload;
 *
 *   // "Is this refusal-SHAPED data." Use at render sites. Must test an OWN
 *   // brand property (Object.hasOwn), not `in`: `in` walks the prototype chain
 *   // and also admits arrays. Object.hasOwn handles a null-prototype wire
 *   // object perfectly, so there is no reason to prefer `in`.
 *   export function isRefusal(value: unknown): value is RefusalLike;
 *
 *   // "Did THIS module's catalogue author this value." Unforgeable from
 *   // outside; suggested mechanism is a module-private WeakSet of authored
 *   // instances, which is realm-independent, unlike instanceof. Use this
 *   // wherever a value is about to be trusted as a domain refusal rather than
 *   // treated as attacker-influenced input.
 *   export function isAuthoredRefusal(value: unknown): value is Refusal;
 *
 *   // Renderers. Safe integers in, display strings out. TypeError otherwise.
 *   export function formatMoney(cents: number): string;   // 4000 -> 'S$40.00'
 *   export function formatCount(value: number): string;   // 1234 -> '1,234'
 *
 *   // The catalogue. Messages state what is wrong AND what to do next, using
 *   // real values interpolated from the arguments. Nothing is hardcoded.
 *   export const refusals: {
 *     duplicatePendingOffer(existingAmountCents: number): Refusal;  // INVALID_STATE
 *     offerOnUnavailableListing(): Refusal;                         // INVALID_STATE
 *     messageTooLong(actualLength: number, limit: number): Refusal; // VALIDATION
 *   };
 */

import { describe, expect, it } from 'vitest';

import * as errorsModule from '@/domain/errors';
import {
  REFUSAL_BRAND,
  REFUSAL_CODES,
  formatCount,
  formatMoney,
  isAuthoredRefusal,
  isRefusal,
  refusals,
  type Refusal,
  type RefusalCode,
} from '@/domain/errors';

/** Runs fn and hands back whatever it threw. Fails the test if it threw nothing. */
function thrownBy(fn: () => unknown): unknown {
  try {
    fn();
  } catch (error: unknown) {
    return error;
  }
  throw new Error('expected the call to throw, but it returned normally');
}

/**
 * Patterns that must never reach a user: SQL, stack frames, framework noise,
 * numeric garbage, and a raw Date rendered in the host zone.
 */
const LEAKY_PATTERNS: readonly RegExp[] = [
  /\bselect\b.*\bfrom\b/i,
  /\binsert\s+into\b/i,
  /\bupdate\b.*\bset\b/i,
  /\bdelete\s+from\b/i,
  /\bsqlstate\b/i,
  /\bpostgres\b/i,
  /\bsupabase\b/i,
  /\bat\s+\S+\s+\(.*:\d+:\d+\)/,
  /\bundefined\b/i,
  /\bnull\b/i,
  /\bNaN\b/,
  /\bInfinity\b/i,
  /∞/,
  /\[object \w+\]/,
  // A raw Date interpolated into a template literal: 'Fri Oct 02 2026 ...'.
  // The legitimate format is 'Fri 2 Oct 2026', which does not match.
  /\b[A-Z][a-z]{2} [A-Z][a-z]{2} \d{2} \d{4}\b/,
  /GMT[+-]\d{4}/,
  /\b(?:Standard|Daylight|Summer) Time\b/,
];

function expectNoLeak(message: string, context: string): void {
  for (const pattern of LEAKY_PATTERNS) {
    if (pattern.test(message)) {
      throw new Error(`${context} leaked ${String(pattern)}: ${message}`);
    }
  }
}

/**
 * Every catalogue entry, with a set of valid arguments and a positional caller.
 * Built lazily inside each `it`: calling the catalogue in a `describe` body runs
 * it at collection time, so a module that throws zeroes the whole file instead
 * of failing the handful of tests that actually exercise the broken path.
 */
interface CatalogueEntry {
  readonly name: string;
  readonly valid: readonly number[];
  call(args: readonly unknown[]): Refusal;
}

function catalogueEntries(): readonly CatalogueEntry[] {
  return [
    {
      name: 'duplicatePendingOffer',
      valid: [4000],
      call: (a) => refusals.duplicatePendingOffer(a[0] as number),
    },
    {
      name: 'offerOnUnavailableListing',
      valid: [],
      call: () => refusals.offerOnUnavailableListing(),
    },
    {
      name: 'messageTooLong',
      valid: [1234, 1000],
      call: (a) => refusals.messageTooLong(a[0] as number, a[1] as number),
    },
  ];
}

/** Values that are not safe integers. Several are real Supabase arrival shapes. */
const NOT_SAFE_INTEGERS: readonly unknown[] = [
  Number.NaN,
  Number.POSITIVE_INFINITY,
  Number.NEGATIVE_INFINITY,
  4000.004,
  0.1,
  -0.5,
  Number.MAX_SAFE_INTEGER + 1,
  -(Number.MAX_SAFE_INTEGER + 1),
  1e21,
  '4000', // a numeric column arrives as a string
  '',
  'NaN',
  null, // a null column
  undefined,
  new Date('2026-10-02T06:00:00.000Z'), // the raw-Date interpolation hazard
  {},
  [],
  [4000],
  true,
];

/** Safe integers spanning the interesting magnitudes. */
const SAFE_INTEGERS: readonly number[] = [
  0, 1, 2, 99, 100, 999, 1000, 1001, 1234, 4000, 99999, 100000, 1000000, 100000000,
  Number.MAX_SAFE_INTEGER,
];

describe('module surface', () => {
  it('the errors module, its public surface, exports no constructible Refusal', () => {
    // Catalogue-only authoring. With no exported constructor, both demonstrated
    // FR-1712 breaches become unrepresentable outside this module rather than
    // merely discouraged: `new Refusal('STORAGE', err.stack)`, and a template
    // literal interpolating a raw Date, which renders the host zone and
    // breaches FR-1718/FR-1720. Both previously typechecked and linted clean.
    expect(Object.keys(errorsModule)).not.toContain('Refusal');
  });

  it('the errors module, its public surface, exports the catalogue the predicate and the renderers', () => {
    const surface = Object.keys(errorsModule);

    for (const name of [
      'REFUSAL_CODES',
      'REFUSAL_BRAND',
      'isRefusal',
      'formatMoney',
      'formatCount',
      'refusals',
    ]) {
      expect(surface, `missing export ${name}`).toContain(name);
    }
  });
});

describe('refusal authoring', () => {
  /** The internal class, reached the way an outside caller would reach it. */
  type AnyCtor = new (...args: unknown[]) => unknown;

  function constructorViaPrototype(value: object): AnyCtor {
    return (Object.getPrototypeOf(value) as { constructor: AnyCtor }).constructor;
  }

  function constructorViaProperty(value: object): AnyCtor {
    return (value as unknown as { constructor: AnyCtor }).constructor;
  }

  it('the internal constructor reached through getPrototypeOf, called from outside, throws instead of building a refusal', () => {
    // Without a module-private authoring token this route produces a refusal
    // whose message carries SQL verbatim, and isRefusal returns true for it.
    const Ctor = constructorViaPrototype(refusals.offerOnUnavailableListing());

    expect(() => new Ctor('STORAGE', 'insert into offers ... duplicate key')).toThrow(TypeError);
  });

  it('the internal constructor reached through the constructor property, called from outside, throws', () => {
    const Ctor = constructorViaProperty(refusals.offerOnUnavailableListing());

    expect(() => new Ctor('STORAGE', 'select * from users')).toThrow(TypeError);
  });

  it('the internal constructor, refused to an outside caller, throws something that is not itself a refusal', () => {
    const Ctor = constructorViaPrototype(refusals.offerOnUnavailableListing());
    const error = thrownBy(() => new Ctor('STORAGE', 'select * from users'));

    expect(isRefusal(error)).toBe(false);
  });

  it('the catalogue, which holds the authoring token, still builds refusals normally', () => {
    for (const entry of catalogueEntries()) {
      expect(isRefusal(entry.call(entry.valid)), entry.name).toBe(true);
    }
  });
});

describe('isAuthoredRefusal', () => {
  // The honest distinction. isRefusal answers "is this refusal-SHAPED data",
  // which is all that can be known about a value that crossed a wire: the
  // brand and the codes are exported and RefusalPayload is the documented wire
  // format, so anything a client can construct, anything can construct. That
  // route cannot be closed without breaking serialization, and is not chased.
  //
  // isAuthoredRefusal answers the narrower, unforgeable question: did THIS
  // module's catalogue author this value. Server-side code that is about to
  // trust a value as a domain refusal rather than as attacker-influenced input
  // should require this one. A module-private WeakSet of authored instances is
  // the suggested mechanism, since it is realm-independent and cannot be
  // forged from outside; instanceof would also work but is realm-bound.

  it('isAuthoredRefusal, a catalogue refusal, reports true', () => {
    for (const entry of catalogueEntries()) {
      expect(isAuthoredRefusal(entry.call(entry.valid)), entry.name).toBe(true);
    }
  });

  it('isAuthoredRefusal, a hand-built payload carrying the real brand, reports false', () => {
    const forged = {
      brand: REFUSAL_BRAND,
      code: 'STORAGE' as RefusalCode,
      message: 'select * from users',
    };

    expect(isRefusal(forged)).toBe(true); // shape-valid, and that is all
    expect(isAuthoredRefusal(forged)).toBe(false);
  });

  it('isAuthoredRefusal, a refusal round-tripped through JSON, reports false', () => {
    const revived: unknown = JSON.parse(JSON.stringify(refusals.duplicatePendingOffer(4000)));

    expect(isRefusal(revived)).toBe(true);
    expect(isAuthoredRefusal(revived)).toBe(false);
  });

  it('isAuthoredRefusal, a payload produced by asPayload, reports false because a payload is data not a refusal', () => {
    const payload = refusals.duplicatePendingOffer(4000).asPayload();

    expect(isRefusal(payload)).toBe(true);
    expect(isAuthoredRefusal(payload)).toBe(false);
    expect(isAuthoredRefusal(structuredClone(payload))).toBe(false);
  });

  it('isAuthoredRefusal, an ordinary Error, reports false', () => {
    expect(isAuthoredRefusal(new Error('boom'))).toBe(false);
    expect(isAuthoredRefusal(undefined)).toBe(false);
    expect(isAuthoredRefusal(null)).toBe(false);
  });

  it('isAuthoredRefusal, a catalogue refusal, narrows to the full Refusal surface', () => {
    const value: unknown = refusals.messageTooLong(1234, 1000);

    expect(isAuthoredRefusal(value)).toBe(true);
    if (!isAuthoredRefusal(value)) throw new Error('unreachable');

    // Only reachable if the predicate narrows to Refusal rather than RefusalLike.
    expect(value.asPayload().code).toBe<RefusalCode>('VALIDATION');
    expect(value.name).toBe('Refusal');
  });
});

describe('REFUSAL_CODES', () => {
  it('REFUSAL_CODES, the shared taxonomy, contains exactly the six reason codes', () => {
    expect([...REFUSAL_CODES].sort()).toEqual([
      'INVALID_STATE',
      'NOT_FOUND',
      'PERMISSION',
      'SESSION',
      'STORAGE',
      'VALIDATION',
    ]);
  });

  it('REFUSAL_CODES, every catalogue entry, reports a code drawn from the taxonomy', () => {
    for (const entry of catalogueEntries()) {
      const refusal = entry.call(entry.valid);

      expect(REFUSAL_CODES).toContain(refusal.code);
    }
  });
});

describe('refusal shape', () => {
  it('refusal shape, any catalogue refusal, carries a brand, a code and a human message', () => {
    const refusal = refusals.offerOnUnavailableListing();

    expect(REFUSAL_BRAND).toBe('hotshop/refusal');
    expect(refusal.brand).toBe(REFUSAL_BRAND);
    expect(refusal.code).toBe<RefusalCode>('INVALID_STATE');
    expect(refusal.message.length).toBeGreaterThan(0);
  });

  it('refusal shape, any catalogue refusal, is still catchable as an Error', () => {
    expect(refusals.offerOnUnavailableListing()).toBeInstanceOf(Error);
  });

  it('refusal shape, thrown and caught, exposes code and message from the catch block', () => {
    const error = thrownBy(() => {
      throw refusals.duplicatePendingOffer(4000);
    });

    expect(isRefusal(error)).toBe(true);
    if (!isRefusal(error)) throw new Error('unreachable');

    expect(error.code).toBe<RefusalCode>('INVALID_STATE');
    expect(error.message).toContain('S$40.00');
  });

  it('refusal shape, its name, is readable but stays out of enumeration', () => {
    const refusal = refusals.offerOnUnavailableListing();

    expect(refusal.name).toBe('Refusal');
    expect(Object.keys(refusal)).not.toContain('name');
  });

  it('refusal shape, its own enumerable keys, are exactly brand code and message', () => {
    const refusal = refusals.offerOnUnavailableListing();

    expect(Object.keys(refusal).sort()).toEqual(['brand', 'code', 'message']);
  });
});

describe('refusal transport', () => {
  it('asPayload, any catalogue refusal, returns the brand code and message', () => {
    const refusal = refusals.messageTooLong(1234, 1000);

    expect(refusal.asPayload()).toEqual({
      brand: REFUSAL_BRAND,
      code: 'VALIDATION',
      message: 'Messages can be at most 1,000 characters, but this one has 1,234.',
    });
  });

  it('spread, any catalogue refusal, preserves the message rather than dropping it', () => {
    // Error.prototype.message is non-enumerable, so a bare subclass spreads to
    // {brand, code} with the message silently missing.
    const refusal = refusals.messageTooLong(1234, 1000);

    expect({ ...refusal }).toEqual(refusal.asPayload());
  });

  it('Object.assign, any catalogue refusal, preserves the message rather than dropping it', () => {
    const refusal = refusals.messageTooLong(1234, 1000);

    expect(Object.assign({}, refusal)).toEqual(refusal.asPayload());
  });

  it('JSON round trip, any catalogue refusal, preserves the payload and stays recognisable', () => {
    const refusal = refusals.duplicatePendingOffer(4000);

    const revived: unknown = JSON.parse(JSON.stringify(refusal));

    expect(revived).toEqual(refusal.asPayload());
    expect(isRefusal(revived)).toBe(true);
  });

  it('structuredClone of a refusal, which drops brand and code, fails closed rather than half working', () => {
    // structuredClone uses the spec's Error path: it keeps message, resets name
    // to 'Error', and discards own properties. Nothing can fix that, so the
    // brand check must reject the result instead of yielding a broken refusal.
    const refusal = refusals.duplicatePendingOffer(4000);

    const cloned = structuredClone(refusal);

    expect(isRefusal(cloned)).toBe(false);
  });

  it('structuredClone of a payload, the supported path, preserves the payload and stays recognisable', () => {
    const refusal = refusals.duplicatePendingOffer(4000);

    const cloned: unknown = structuredClone(refusal.asPayload());

    expect(cloned).toEqual(refusal.asPayload());
    expect(isRefusal(cloned)).toBe(true);
  });

  it('an instance and its payload, serialized, produce byte-identical JSON', () => {
    // toEqual ignores key order, so the transports can agree while string
    // equality, cache keys and snapshot tests still see a difference. Pinning
    // the stronger property costs the implementer only an assignment order.
    for (const entry of catalogueEntries()) {
      const refusal = entry.call(entry.valid);
      const payload = refusal.asPayload();

      expect(Object.keys({ ...refusal }), entry.name).toEqual(Object.keys(payload));
      expect(JSON.stringify({ ...refusal }), entry.name).toBe(JSON.stringify(payload));
      expect(JSON.stringify(refusal), entry.name).toBe(JSON.stringify(payload));
    }
  });

  it('every transport, across the whole catalogue, agrees with asPayload', () => {
    for (const entry of catalogueEntries()) {
      const refusal = entry.call(entry.valid);
      const payload = refusal.asPayload();

      expect({ ...refusal }).toEqual(payload);
      expect(Object.assign({}, refusal)).toEqual(payload);
      expect(JSON.parse(JSON.stringify(refusal))).toEqual(payload);
      expect(structuredClone(payload)).toEqual(payload);
    }
  });
});

describe('isRefusal', () => {
  it('isRefusal, a catalogue refusal, reports true', () => {
    expect(isRefusal(refusals.offerOnUnavailableListing())).toBe(true);
  });

  it('isRefusal, a branded object with no prototype, reports true', () => {
    // A null-prototype object is not an instance of anything, not even Object.
    // An implementation using `instanceof` fails here. This is the pin for the
    // mechanism, since the constructor is not exported to compare against.
    const wireValue: unknown = Object.assign(Object.create(null) as object, {
      brand: REFUSAL_BRAND,
      code: 'PERMISSION' as RefusalCode,
      message: 'Only the seller can close this listing.',
    });

    expect(wireValue).not.toBeInstanceOf(Object);
    expect(isRefusal(wireValue)).toBe(true);
  });

  it('isRefusal, a branded array, reports false', () => {
    // `'brand' in value` accepts this. A refusal is never a list.
    const brandedArray: unknown = Object.assign([], {
      brand: REFUSAL_BRAND,
      code: 'STORAGE' as RefusalCode,
      message: 'select * from users',
    });

    expect(isRefusal(brandedArray)).toBe(false);
  });

  it('isRefusal, a brand inherited through the prototype chain, reports false', () => {
    // `in` walks the prototype chain, so a brand on a shared prototype would
    // make every object derived from it a refusal. Object.hasOwn does not, and
    // handles the null-prototype wire object below perfectly well, so the
    // stated reason for preferring `in` does not hold.
    const inherited: unknown = Object.create({
      brand: REFUSAL_BRAND,
      code: 'STORAGE' as RefusalCode,
      message: 'select * from users',
    });

    expect(isRefusal(inherited)).toBe(false);
  });

  it('isRefusal, a branded function, reports false', () => {
    const brandedFunction: unknown = Object.assign(() => undefined, {
      brand: REFUSAL_BRAND,
      code: 'STORAGE' as RefusalCode,
      message: 'select * from users',
    });

    expect(isRefusal(brandedFunction)).toBe(false);
  });

  it('isRefusal, an ordinary Error, reports false', () => {
    expect(isRefusal(new Error('boom'))).toBe(false);
  });

  it('isRefusal, a TypeError thrown by the runtime, reports false', () => {
    expect(isRefusal(new TypeError('x is not a function'))).toBe(false);
  });

  it('isRefusal, an unbranded look-alike object, reports false', () => {
    expect(isRefusal({ code: 'VALIDATION', message: 'looks like one' })).toBe(false);
  });

  it('isRefusal, an object branded with the wrong value, reports false', () => {
    expect(isRefusal({ brand: 'something/else', code: 'VALIDATION', message: 'nope' })).toBe(false);
  });

  it('isRefusal, a non-object value, reports false', () => {
    expect(isRefusal(undefined)).toBe(false);
    expect(isRefusal(null)).toBe(false);
    expect(isRefusal('VALIDATION')).toBe(false);
    expect(isRefusal(42)).toBe(false);
  });
});

describe('refusals.duplicatePendingOffer', () => {
  it('refusals.duplicatePendingOffer, an existing pending offer of 4000 cents, reports invalid state with the amount', () => {
    const refusal = refusals.duplicatePendingOffer(4000);

    expect(refusal.code).toBe<RefusalCode>('INVALID_STATE');
    expect(refusal.message).toBe(
      'You already have a pending offer of S$40.00 on this listing. Withdraw it before making a new one.',
    );
  });

  it('refusals.duplicatePendingOffer, an existing pending offer of 1 cent, formats the real amount into the message', () => {
    expect(refusals.duplicatePendingOffer(1).message).toBe(
      'You already have a pending offer of S$0.01 on this listing. Withdraw it before making a new one.',
    );
  });
});

describe('refusals.offerOnUnavailableListing', () => {
  it('refusals.offerOnUnavailableListing, a listing that is not available, reports invalid state with the rule', () => {
    const refusal = refusals.offerOnUnavailableListing();

    expect(refusal.code).toBe<RefusalCode>('INVALID_STATE');
    expect(refusal.message).toBe('Only available listings can receive offers.');
  });
});

describe('refusals.messageTooLong', () => {
  it('refusals.messageTooLong, 1234 characters against the 1000 limit, reports validation with both counts', () => {
    const refusal = refusals.messageTooLong(1234, 1000);

    expect(refusal.code).toBe<RefusalCode>('VALIDATION');
    expect(refusal.message).toBe('Messages can be at most 1,000 characters, but this one has 1,234.');
  });

  it('refusals.messageTooLong, 1001 characters against the 1000 limit, names the one character over', () => {
    expect(refusals.messageTooLong(1001, 1000).message).toBe(
      'Messages can be at most 1,000 characters, but this one has 1,001.',
    );
  });

  it('refusals.messageTooLong, 5000 characters against a 2000 limit, interpolates the limit it was given', () => {
    expect(refusals.messageTooLong(5000, 2000).message).toBe(
      'Messages can be at most 2,000 characters, but this one has 5,000.',
    );
  });

  it('refusals.messageTooLong, 150 characters against a 100 limit, renders small counts without separators', () => {
    expect(refusals.messageTooLong(150, 100).message).toBe(
      'Messages can be at most 100 characters, but this one has 150.',
    );
  });
});

describe('refusals catalogue, fuzzed', () => {
  it('refusals catalogue, any argument that is not a safe integer, throws TypeError instead of rendering it', () => {
    for (const entry of catalogueEntries()) {
      for (let position = 0; position < entry.valid.length; position += 1) {
        for (const bad of NOT_SAFE_INTEGERS) {
          const args = [...entry.valid];
          args[position] = bad as number;

          expect(
            () => entry.call(args),
            `${entry.name} argument ${position} = ${String(bad)}`,
          ).toThrow(TypeError);
        }
      }
    }
  });

  it('refusals catalogue, a TypeError from a bad argument, is never itself a refusal', () => {
    const error = thrownBy(() => refusals.duplicatePendingOffer(Number.NaN));

    expect(error).toBeInstanceOf(TypeError);
    expect(isRefusal(error)).toBe(false);
  });

  it('refusals catalogue, every safe-integer argument combination, leaks nothing into the message', () => {
    for (const entry of catalogueEntries()) {
      if (entry.valid.length === 0) {
        expectNoLeak(entry.call([]).message, entry.name);
        continue;
      }
      if (entry.valid.length === 1) {
        for (const a of SAFE_INTEGERS) {
          expectNoLeak(entry.call([a]).message, `${entry.name}(${a})`);
        }
        continue;
      }
      for (const a of SAFE_INTEGERS) {
        for (const b of SAFE_INTEGERS) {
          expectNoLeak(entry.call([a, b]).message, `${entry.name}(${a}, ${b})`);
        }
      }
    }
  });

  it('refusals catalogue, every safe-integer argument combination, ends its message in a full stop', () => {
    for (const entry of catalogueEntries()) {
      for (const a of SAFE_INTEGERS) {
        const args = entry.valid.length === 0 ? [] : entry.valid.map(() => a);
        const message = entry.call(args).message;

        expect(message.trim()).toBe(message);
        expect(message.endsWith('.')).toBe(true);
      }
    }
  });
});

describe('formatMoney', () => {
  it('formatMoney, 4000 cents, renders S$40.00', () => {
    expect(formatMoney(4000)).toBe('S$40.00');
  });

  it('formatMoney, 1 cent, renders S$0.01', () => {
    expect(formatMoney(1)).toBe('S$0.01');
  });

  it('formatMoney, 100000000 cents, renders S$1,000,000.00 with thousands separators', () => {
    expect(formatMoney(100000000)).toBe('S$1,000,000.00');
  });

  it('formatMoney, 0 cents, renders S$0.00', () => {
    expect(formatMoney(0)).toBe('S$0.00');
  });

  it('formatMoney, 99999 cents, renders S$999.99 without a separator', () => {
    expect(formatMoney(99999)).toBe('S$999.99');
  });

  it('formatMoney, 100000 cents, renders S$1,000.00 at the separator boundary', () => {
    expect(formatMoney(100000)).toBe('S$1,000.00');
  });

  it('formatMoney, the largest safe integer, renders exact integer arithmetic with no floating point noise', () => {
    expect(formatMoney(Number.MAX_SAFE_INTEGER)).toBe('S$90,071,992,547,409.91');
  });

  it('formatMoney, every safe integer tested, renders exactly one decimal point and two trailing digits', () => {
    for (const cents of SAFE_INTEGERS) {
      const rendered = formatMoney(cents);

      expect(rendered, `formatMoney(${cents})`).toMatch(/^S\$[\d,]+\.\d{2}$/);
      expect(rendered.split('.').length - 1, `formatMoney(${cents})`).toBe(1);
      expectNoLeak(rendered, `formatMoney(${cents})`);
    }
  });

  it('formatMoney, a non-integer, throws TypeError rather than rendering floating point noise', () => {
    // Previously returned 'S$40.0.0039999999999054126'.
    expect(() => formatMoney(4000.004)).toThrow(TypeError);
    expect(() => formatMoney(0.1)).toThrow(TypeError);
  });

  it('formatMoney, NaN, throws TypeError', () => {
    expect(() => formatMoney(Number.NaN)).toThrow(TypeError);
  });

  it('formatMoney, Infinity, throws TypeError', () => {
    expect(() => formatMoney(Number.POSITIVE_INFINITY)).toThrow(TypeError);
  });

  it('formatMoney, negative Infinity, throws TypeError', () => {
    expect(() => formatMoney(Number.NEGATIVE_INFINITY)).toThrow(TypeError);
  });

  it('formatMoney, a value beyond the safe integer range, throws TypeError', () => {
    expect(() => formatMoney(Number.MAX_SAFE_INTEGER + 1)).toThrow(TypeError);
    expect(() => formatMoney(1e21)).toThrow(TypeError);
  });

  it('formatMoney, a numeric string of the shape a database column arrives in, throws TypeError', () => {
    expect(() => formatMoney('4000' as unknown as number)).toThrow(TypeError);
    expect(() => formatMoney(null as unknown as number)).toThrow(TypeError);
  });

  it('formatMoney, anything it rejects, throws something that is not a refusal', () => {
    for (const bad of NOT_SAFE_INTEGERS) {
      const error = thrownBy(() => formatMoney(bad as number));

      expect(error, `formatMoney(${String(bad)})`).toBeInstanceOf(TypeError);
      expect(isRefusal(error)).toBe(false);
    }
  });
});

describe('formatCount', () => {
  it('formatCount, 999, renders without a separator', () => {
    expect(formatCount(999)).toBe('999');
  });

  it('formatCount, 1000, renders with a thousands separator', () => {
    expect(formatCount(1000)).toBe('1,000');
  });

  it('formatCount, 1234, renders with a thousands separator', () => {
    expect(formatCount(1234)).toBe('1,234');
  });

  it('formatCount, 0, renders as a bare zero', () => {
    expect(formatCount(0)).toBe('0');
  });

  it('formatCount, 1000000, renders with both separators', () => {
    expect(formatCount(1000000)).toBe('1,000,000');
  });

  it('formatCount, the largest safe integer, renders every digit', () => {
    expect(formatCount(Number.MAX_SAFE_INTEGER)).toBe('9,007,199,254,740,991');
  });

  it('formatCount, every safe integer tested, renders digits and separators only', () => {
    for (const value of SAFE_INTEGERS) {
      const rendered = formatCount(value);

      expect(rendered, `formatCount(${value})`).toMatch(/^\d{1,3}(,\d{3})*$/);
      expectNoLeak(rendered, `formatCount(${value})`);
    }
  });

  it('formatCount, a non-integer, throws TypeError', () => {
    expect(() => formatCount(1234.5)).toThrow(TypeError);
  });

  it('formatCount, NaN, throws TypeError', () => {
    expect(() => formatCount(Number.NaN)).toThrow(TypeError);
  });

  it('formatCount, Infinity, throws TypeError rather than rendering the infinity sign', () => {
    // Previously rendered '∞' into a user-facing message.
    expect(() => formatCount(Number.POSITIVE_INFINITY)).toThrow(TypeError);
  });

  it('formatCount, negative Infinity, throws TypeError', () => {
    expect(() => formatCount(Number.NEGATIVE_INFINITY)).toThrow(TypeError);
  });

  it('formatCount, a value beyond the safe integer range, throws TypeError', () => {
    expect(() => formatCount(Number.MAX_SAFE_INTEGER + 1)).toThrow(TypeError);
  });

  it('formatCount, anything it rejects, throws something that is not a refusal', () => {
    for (const bad of NOT_SAFE_INTEGERS) {
      const error = thrownBy(() => formatCount(bad as number));

      expect(error, `formatCount(${String(bad)})`).toBeInstanceOf(TypeError);
      expect(isRefusal(error)).toBe(false);
    }
  });
});
