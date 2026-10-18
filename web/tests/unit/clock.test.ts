/**
 * CONTRACT FOR THE IMPLEMENTER — module `@/domain/time` (clock half)
 * ==================================================================
 * Domain code must never call `Date.now()`, `new Date()` with no argument, or
 * read a timestamp from the database. It takes a Clock. That is what makes the
 * 60-day meetup horizon and the "meetup has ended" rule testable.
 *
 * INVALID INPUT THROWS TypeError AT EVERY PUBLIC ENTRY POINT. One policy for
 * the whole module, matching `@/domain/errors`. An Invalid Date is never a
 * legitimate domain value: it comes from `new Date(row.starts_at)` on a null
 * column, and answering a boolean question about it produces a confidently
 * wrong answer that propagates. The explicit TypeError also distinguishes a
 * deliberate guard from a raw RangeError leaking out of Intl internals.
 *
 *   export interface Clock {
 *     // The current instant. Returns a fresh Date each call: callers must not
 *     // be able to mutate the clock through the value they are handed.
 *     now(): Date;
 *   }
 *
 *   export interface FixedClock extends Clock {
 *     // Move the clock forward (or back, with a negative value) by whole
 *     // milliseconds. Mutates this clock in place and returns nothing.
 *     advanceBy(milliseconds: number): void;
 *   }
 *
 *   // A clock pinned to one instant until advanced. The argument is copied.
 *   export function fixedClock(instant: Date): FixedClock;
 *
 *   // The real clock. The ONLY place the host clock may be read. It must READ
 *   // the host clock on every now() call, not capture it at construction.
 *   export function systemClock(): Clock;
 *
 *   // Duration helpers used to express advances readably.
 *   export const MILLIS_PER_SECOND: 1000;
 *   export const MILLIS_PER_MINUTE: 60000;
 *   export const MILLIS_PER_HOUR: 3600000;
 *   export const MILLIS_PER_DAY: 86400000;
 */

import { runInNewContext } from 'node:vm';

import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  MILLIS_PER_DAY,
  MILLIS_PER_HOUR,
  MILLIS_PER_MINUTE,
  MILLIS_PER_SECOND,
  fixedClock,
  systemClock,
} from '@/domain/time';

/** 2 Oct 2026, 14:00 in Singapore. Written in UTC so the host zone cannot matter. */
const ANCHOR_ISO = '2026-10-02T06:00:00.000Z';

/**
 * A real Date built in another realm. `instanceof Date` is realm-bound and
 * reports false for it, but it has a genuine internal date slot, so
 * `Date.prototype.getTime.call(it)` works and Intl formats it correctly. The
 * realistic source is a jsdom component test.
 */
function foreignDate(iso: string): Date {
  return runInNewContext(`new Date(${JSON.stringify(iso)})`) as Date;
}

/** Runs fn and hands back whatever it threw. Fails the test if it threw nothing. */
function thrownBy(fn: () => unknown): unknown {
  try {
    fn();
  } catch (error: unknown) {
    return error;
  }
  throw new Error('expected the call to throw, but it returned normally');
}

describe('fixedClock', () => {
  it('fixedClock, constructed with an instant, returns exactly that instant', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    expect(clock.now().toISOString()).toBe(ANCHOR_ISO);
  });

  it('fixedClock, called repeatedly without advancing, returns the same instant every time', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    const first = clock.now();
    const second = clock.now();
    const third = clock.now();

    expect(first.getTime()).toBe(second.getTime());
    expect(second.getTime()).toBe(third.getTime());
    expect(third.toISOString()).toBe(ANCHOR_ISO);
  });

  it('fixedClock, its returned Date mutated by a caller, still reports the original instant', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    const handed = clock.now();
    handed.setUTCFullYear(1999);

    expect(clock.now().toISOString()).toBe(ANCHOR_ISO);
  });

  it('fixedClock, the Date it was constructed from mutated afterwards, still reports the original instant', () => {
    const seed = new Date(ANCHOR_ISO);
    const clock = fixedClock(seed);

    seed.setUTCFullYear(1999);

    expect(clock.now().toISOString()).toBe(ANCHOR_ISO);
  });

  it('fixedClock, advanced by thirty minutes, returns the later instant', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    clock.advanceBy(30 * MILLIS_PER_MINUTE);

    expect(clock.now().toISOString()).toBe('2026-10-02T06:30:00.000Z');
  });

  it('fixedClock, advanced by one millisecond, returns the instant one millisecond later', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    clock.advanceBy(1);

    expect(clock.now().toISOString()).toBe('2026-10-02T06:00:00.001Z');
  });

  it('fixedClock, advanced twice, accumulates both durations', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    clock.advanceBy(2 * MILLIS_PER_HOUR);
    clock.advanceBy(MILLIS_PER_DAY);

    expect(clock.now().toISOString()).toBe('2026-10-03T08:00:00.000Z');
  });

  it('fixedClock, advanced by zero, stays on the same instant', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    clock.advanceBy(0);

    expect(clock.now().toISOString()).toBe(ANCHOR_ISO);
  });

  it('fixedClock, advanced by a negative duration, moves back to the earlier instant', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    clock.advanceBy(-MILLIS_PER_SECOND);

    expect(clock.now().toISOString()).toBe('2026-10-02T05:59:59.000Z');
  });

  it('fixedClock, two independent clocks, advance without affecting each other', () => {
    const left = fixedClock(new Date(ANCHOR_ISO));
    const right = fixedClock(new Date(ANCHOR_ISO));

    left.advanceBy(MILLIS_PER_DAY);

    expect(left.now().toISOString()).toBe('2026-10-03T06:00:00.000Z');
    expect(right.now().toISOString()).toBe(ANCHOR_ISO);
  });

  it('fixedClock, never advanced, ignores the host clock entirely', () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date('2020-01-01T00:00:00.000Z'));
      const clock = fixedClock(new Date(ANCHOR_ISO));

      vi.advanceTimersByTime(10 * MILLIS_PER_DAY);

      expect(clock.now().toISOString()).toBe(ANCHOR_ISO);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('systemClock', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('systemClock, read once, returns an instant close to the real current time', () => {
    const before = Date.now();
    const reading = systemClock().now().getTime();
    const after = Date.now();

    // Generous tolerance: a slow CI box must not make this flake.
    expect(reading).toBeGreaterThanOrEqual(before - 5000);
    expect(reading).toBeLessThanOrEqual(after + 5000);
  });

  it('systemClock, the host clock ticking forward, reports the later instant on the next read', () => {
    // Pins liveness without sleeping. A clock that captured the instant at
    // construction returns the t0 value here and fails.
    vi.useFakeTimers();
    vi.setSystemTime(new Date(ANCHOR_ISO));

    const clock = systemClock();
    expect(clock.now().toISOString()).toBe(ANCHOR_ISO);

    vi.advanceTimersByTime(5 * MILLIS_PER_MINUTE);

    expect(clock.now().toISOString()).toBe('2026-10-02T06:05:00.000Z');
  });

  it('systemClock, constructed before the host clock is set, reads the clock at call time not at construction', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2020-01-01T00:00:00.000Z'));

    const clock = systemClock();

    vi.setSystemTime(new Date(ANCHOR_ISO));

    expect(clock.now().toISOString()).toBe(ANCHOR_ISO);
  });

  it('systemClock, read twice, does not go backwards', () => {
    const clock = systemClock();

    const first = clock.now().getTime();
    const second = clock.now().getTime();

    expect(second).toBeGreaterThanOrEqual(first);
  });

  it('systemClock, its returned Date mutated by a caller, is unaffected on the next read', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(ANCHOR_ISO));

    const clock = systemClock();
    const handed = clock.now();
    handed.setUTCFullYear(1999);

    expect(clock.now().toISOString()).toBe(ANCHOR_ISO);
  });
});

describe('clock invalid input', () => {
  const INVALID_DATES: readonly Date[] = [
    new Date(Number.NaN),
    new Date('not a date'),
    new Date(undefined as unknown as string),
  ];

  it('fixedClock, an Invalid Date, throws TypeError rather than building a clock that reports NaN', () => {
    for (const invalid of INVALID_DATES) {
      expect(() => fixedClock(invalid)).toThrow(TypeError);
    }
  });

  it('fixedClock, a value that is not a Date, throws TypeError', () => {
    expect(() => fixedClock('2026-10-02T06:00:00.000Z' as unknown as Date)).toThrow(TypeError);
    expect(() => fixedClock(null as unknown as Date)).toThrow(TypeError);
    expect(() => fixedClock(Date.now() as unknown as Date)).toThrow(TypeError);
  });

  it('advanceBy, a duration that is not a safe integer, throws TypeError', () => {
    for (const bad of [
      Number.NaN,
      Number.POSITIVE_INFINITY,
      Number.NEGATIVE_INFINITY,
      1.5,
      Number.MAX_SAFE_INTEGER + 1,
      '1000' as unknown as number,
      null as unknown as number,
      undefined as unknown as number,
    ]) {
      const clock = fixedClock(new Date(ANCHOR_ISO));

      expect(() => clock.advanceBy(bad), `advanceBy(${String(bad)})`).toThrow(TypeError);
    }
  });

  it('advanceBy, a rejected duration, leaves the clock on its previous instant', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    expect(() => clock.advanceBy(Number.NaN)).toThrow(TypeError);

    expect(clock.now().toISOString()).toBe(ANCHOR_ISO);
  });
});

describe('advanceBy result range', () => {
  /** The largest instant a Date can represent: ±8.64e15 ms from the epoch. */
  const MAX_TIME = 8.64e15;

  it('advanceBy, a safe integer whose result leaves the Date range, throws TypeError', () => {
    // The argument passes the safe-integer guard: the RESULT is what is out of
    // range. Without a result check now() silently returns an Invalid Date.
    expect(Number.isSafeInteger(Number.MAX_SAFE_INTEGER)).toBe(true);
    const clock = fixedClock(new Date(ANCHOR_ISO));

    expect(() => clock.advanceBy(Number.MAX_SAFE_INTEGER)).toThrow(TypeError);
  });

  it('advanceBy, an advance rejected for range, leaves the clock on its previous instant', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    expect(() => clock.advanceBy(Number.MAX_SAFE_INTEGER)).toThrow(TypeError);

    expect(clock.now().toISOString()).toBe(ANCHOR_ISO);
  });

  it('advanceBy, an advance rejected for range, leaves the clock usable', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));
    expect(() => clock.advanceBy(Number.MAX_SAFE_INTEGER)).toThrow(TypeError);

    clock.advanceBy(MILLIS_PER_MINUTE);

    expect(clock.now().toISOString()).toBe('2026-10-02T06:01:00.000Z');
  });

  it('advanceBy, landing exactly on the maximum representable instant, is allowed', () => {
    const clock = fixedClock(new Date(0));

    clock.advanceBy(MAX_TIME);

    expect(clock.now().getTime()).toBe(MAX_TIME);
  });

  it('advanceBy, landing one millisecond past the maximum representable instant, throws TypeError', () => {
    const clock = fixedClock(new Date(0));

    expect(() => clock.advanceBy(MAX_TIME + 1)).toThrow(TypeError);

    expect(clock.now().getTime()).toBe(0);
  });

  it('advanceBy, landing exactly on the minimum representable instant, is allowed', () => {
    const clock = fixedClock(new Date(0));

    clock.advanceBy(-MAX_TIME);

    expect(clock.now().getTime()).toBe(-MAX_TIME);
  });

  it('advanceBy, landing one millisecond before the minimum representable instant, throws TypeError', () => {
    const clock = fixedClock(new Date(0));

    expect(() => clock.advanceBy(-MAX_TIME - 1)).toThrow(TypeError);

    expect(clock.now().getTime()).toBe(0);
  });

  it('now, after any accepted advance, never reports an Invalid Date', () => {
    const clock = fixedClock(new Date(ANCHOR_ISO));

    for (const step of [MILLIS_PER_DAY, -MILLIS_PER_HOUR, 0, MILLIS_PER_SECOND]) {
      clock.advanceBy(step);

      expect(Number.isNaN(clock.now().getTime())).toBe(false);
    }
  });
});

describe('clock cross-realm and exotic Dates', () => {
  it('fixedClock, a Date from another realm, is accepted rather than rejected for failing instanceof', () => {
    const foreign = foreignDate(ANCHOR_ISO);

    expect(foreign instanceof Date).toBe(false);
    expect(fixedClock(foreign).now().toISOString()).toBe(ANCHOR_ISO);
  });

  it('fixedClock, a Proxy-wrapped Date, is rejected with the module guard not the engine message', () => {
    const proxied = new Proxy(new Date(ANCHOR_ISO), {}) as Date;
    expect(proxied instanceof Date).toBe(true);

    const error = thrownBy(() => fixedClock(proxied));

    expect(error).toBeInstanceOf(TypeError);
    expect((error as Error).message).not.toMatch(/this is not a Date object/i);
  });
});

describe('duration constants', () => {
  it('duration constants, the millisecond ladder, hold the conventional values', () => {
    expect(MILLIS_PER_SECOND).toBe(1000);
    expect(MILLIS_PER_MINUTE).toBe(60 * MILLIS_PER_SECOND);
    expect(MILLIS_PER_HOUR).toBe(60 * MILLIS_PER_MINUTE);
    expect(MILLIS_PER_DAY).toBe(24 * MILLIS_PER_HOUR);
  });
});
