/**
 * CONTRACT FOR THE IMPLEMENTER — module `@/domain/time` (formatting + scheduling half)
 * ====================================================================================
 * Every date and time in HotShop renders in ONE configured zone with a zone
 * label, so a buyer in London and a seller in Singapore read the same string
 * for the same meetup. The clock is 24-hour.
 *
 * THE HOST ZONE IS NEVER CONSULTED. Formatters must pass `timeZone:
 * APP_TIME_ZONE` explicitly. Note that `Intl.DateTimeFormat` resolves and
 * caches its zone at CONSTRUCTION, so a module-scope formatter built without
 * that option silently locks onto whatever zone the process had at import. The
 * suite runs under a baseline TZ of America/New_York (set in
 * vitest.config.mts), and the re-import suite below evaluates the module again
 * under Pacific/Kiritimati, so either mistake renders visibly wrong output.
 *
 * INVALID INPUT THROWS TypeError AT EVERY PUBLIC ENTRY POINT. One policy for
 * the whole module, matching `@/domain/errors`. Previously an Invalid Date was
 * handled three different ways: isStartInFuture returned false, hasMeetupEnded
 * returned false, and isWithinSchedulingHorizon threw a raw RangeError out of
 * formatToParts. The hasMeetupEnded case is the dangerous one: an invalid end
 * means the meetup never ends and the sale stays open forever. The realistic
 * trigger is `new Date(row.starts_at)` on a null column. An explicit TypeError
 * also distinguishes a deliberate guard from a RangeError leaking out of Intl.
 *
 * THE DATE GUARD MUST NOT USE `instanceof Date`. It is realm-bound, so a valid
 * Date from a jsdom or node:vm realm is rejected even though it has a real
 * internal date slot. Conversely `new Proxy(new Date(), {})` PASSES instanceof
 * but has no internal slot, so calling getTime() on it throws V8's own
 * "this is not a Date object" — the raw engine message this guard exists to
 * prevent. `Object.prototype.toString.call(value) === '[object Date]'`
 * separates the two correctly: '[object Date]' cross-realm, '[object Object]'
 * for the Proxy. Both directions are pinned below.
 *
 *   export const APP_TIME_ZONE: 'Asia/Singapore';
 *
 *   // A SHORT label for display next to a rendered time: 'SGT', not the IANA
 *   // identifier. Stable regardless of the host zone.
 *   export function zoneLabel(): string;
 *
 *   // Single-instant renderers, evaluated in APP_TIME_ZONE.
 *   export function formatZonedDate(instant: Date): string;      // 'Fri 2 Oct 2026'
 *   export function formatZonedTime(instant: Date): string;      // '14:00', '00:00'
 *
 *   // The composed, user-visible string. This is what a page, a notification
 *   // or a scheduling refusal message embeds when it needs to name an instant,
 *   // and it is the only renderer that carries the zone label.
 *   export function formatZonedDateTime(instant: Date): string;  // 'Fri 2 Oct 2026, 14:00 SGT'
 *
 *   // Meetup-window renderers, evaluated in APP_TIME_ZONE. Both join their two
 *   // halves with the same ' to ' separator.
 *   export function formatMeetupDate(start: Date, end: Date): string;
 *       // same Singapore day:  'Fri 2 Oct 2026'
 *       // crossing midnight:   'Fri 2 Oct 2026 to Sat 3 Oct 2026'
 *   export function formatMeetupTimeRange(start: Date, end: Date): string;  // '14:00 to 14:30'
 *
 *   // Scheduling boundaries. The Clock comes first: it is the ambient
 *   // dependency, the instant under test is the subject.
 *   export const MEETUP_HORIZON_DAYS: 60;
 *
 *   // Strictly after now. Equal to now is NOT in the future.
 *   export function isStartInFuture(clock: Clock, start: Date): boolean;
 *
 *   // True when start's calendar date in APP_TIME_ZONE is on or before the
 *   // MEETUP_HORIZON_DAYS-th calendar day after today in APP_TIME_ZONE.
 *   // Day 60 is allowed at any time of day; day 61 never is.
 *   // THIS HELPER HAS NO LOWER BOUND BY DESIGN: a start in the past is within
 *   // the horizon. isStartInFuture is the separate guard for that, and callers
 *   // are expected to apply both.
 *   //
 *   // DO NOT COMPUTE CALENDAR DAY NUMBERS WITH Date.UTC. `Date.UTC(50, 5, 15)`
 *   // means 1950, not year 50, which puts the helper 693,960 days out for
 *   // years 0 to 99. Use an explicit proleptic Gregorian day count from the
 *   // year/month/day that formatToParts reports in APP_TIME_ZONE.
 *   export function isWithinSchedulingHorizon(clock: Clock, start: Date): boolean;
 *
 *   // True once end is no longer after now, i.e. end <= now.
 *   export function hasMeetupEnded(clock: Clock, end: Date): boolean;
 */

import { runInNewContext } from 'node:vm';

import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  APP_TIME_ZONE,
  MEETUP_HORIZON_DAYS,
  fixedClock,
  formatMeetupDate,
  formatMeetupTimeRange,
  formatZonedDate,
  formatZonedDateTime,
  formatZonedTime,
  hasMeetupEnded,
  isStartInFuture,
  isWithinSchedulingHorizon,
  zoneLabel,
} from '@/domain/time';

// All instants are written in UTC so that nothing in this file depends on how
// the host machine parses a local-looking date string.

/** 2 Oct 2026, 14:00 SGT. */
const MEETUP_START = new Date('2026-10-02T06:00:00.000Z');
/** 2 Oct 2026, 14:30 SGT. */
const MEETUP_END = new Date('2026-10-02T06:30:00.000Z');

/** 2 Oct 2026, 23:45 SGT -> 3 Oct 2026, 00:15 SGT. */
const OVERNIGHT_START = new Date('2026-10-02T15:45:00.000Z');
const OVERNIGHT_END = new Date('2026-10-02T16:15:00.000Z');

/** 2 Oct 2026, 00:00 SGT. */
const SINGAPORE_MIDNIGHT = new Date('2026-10-01T16:00:00.000Z');

describe('APP_TIME_ZONE', () => {
  it('APP_TIME_ZONE, the configured display zone, is Asia/Singapore', () => {
    expect(APP_TIME_ZONE).toBe('Asia/Singapore');
  });
});

describe('zoneLabel', () => {
  it('zoneLabel, the configured zone, is the short display label SGT', () => {
    expect(zoneLabel()).toBe('SGT');
  });

  it('zoneLabel, the configured zone, is a short label rather than the IANA identifier', () => {
    const label = zoneLabel();

    expect(label).not.toBe(APP_TIME_ZONE);
    expect(label).not.toContain('/');
    expect(label).not.toMatch(/\s/);
    expect(label.length).toBeLessThanOrEqual(6);
  });
});

describe('formatZonedDate', () => {
  it('formatZonedDate, 2 October 2026 in Singapore, renders Fri 2 Oct 2026', () => {
    expect(formatZonedDate(MEETUP_START)).toBe('Fri 2 Oct 2026');
  });

  it('formatZonedDate, an instant just past Singapore midnight, renders the next calendar day', () => {
    expect(formatZonedDate(OVERNIGHT_END)).toBe('Sat 3 Oct 2026');
  });

  it('formatZonedDate, an instant just before Singapore midnight, renders the earlier calendar day', () => {
    expect(formatZonedDate(OVERNIGHT_START)).toBe('Fri 2 Oct 2026');
  });

  it('formatZonedDate, any instant, renders without a comma after the weekday', () => {
    expect(formatZonedDate(MEETUP_START)).not.toContain(',');
  });
});

describe('formatZonedTime', () => {
  it('formatZonedTime, 14:00 Singapore time, renders 14:00 on a 24-hour clock', () => {
    expect(formatZonedTime(MEETUP_START)).toBe('14:00');
  });

  it('formatZonedTime, 14:30 Singapore time, renders 14:30', () => {
    expect(formatZonedTime(MEETUP_END)).toBe('14:30');
  });

  it('formatZonedTime, Singapore midnight, renders 00:00 and never 24:00 or 12:00 AM', () => {
    const rendered = formatZonedTime(SINGAPORE_MIDNIGHT);

    expect(rendered).toBe('00:00');
    expect(rendered).not.toBe('24:00');
    expect(rendered).not.toMatch(/am|pm/i);
  });

  it('formatZonedTime, Singapore noon, renders 12:00 without a meridiem', () => {
    const noon = new Date('2026-10-02T04:00:00.000Z');

    const rendered = formatZonedTime(noon);

    expect(rendered).toBe('12:00');
    expect(rendered).not.toMatch(/am|pm/i);
  });

  it('formatZonedTime, the last minute of a Singapore day, renders 23:59', () => {
    expect(formatZonedTime(new Date('2026-10-02T15:59:00.000Z'))).toBe('23:59');
  });
});

describe('formatZonedDateTime', () => {
  it('formatZonedDateTime, 2 October 2026 at 14:00 Singapore time, renders the date time and zone label', () => {
    expect(formatZonedDateTime(MEETUP_START)).toBe('Fri 2 Oct 2026, 14:00 SGT');
  });

  it('formatZonedDateTime, Singapore midnight, renders 00:00 with the zone label', () => {
    expect(formatZonedDateTime(SINGAPORE_MIDNIGHT)).toBe('Fri 2 Oct 2026, 00:00 SGT');
  });

  it('formatZonedDateTime, any instant, ends with the same label zoneLabel reports', () => {
    expect(formatZonedDateTime(MEETUP_START).endsWith(zoneLabel())).toBe(true);
  });

  it('formatZonedDateTime, any instant, composes the same date and time its parts render', () => {
    const composed = formatZonedDateTime(OVERNIGHT_START);

    expect(composed).toContain(formatZonedDate(OVERNIGHT_START));
    expect(composed).toContain(formatZonedTime(OVERNIGHT_START));
  });

  it('formatZonedDateTime, the string a scheduling refusal embeds, always states the zone alongside the time', () => {
    // FR-1720: a refusal that names a time must state it in the application
    // zone WITH the label, so the two parties read the same instant. The
    // scheduling refusals themselves belong to INV-11; this pins the renderer
    // they are required to build their messages from.
    for (const instant of [MEETUP_START, MEETUP_END, SINGAPORE_MIDNIGHT, OVERNIGHT_END]) {
      const rendered = formatZonedDateTime(instant);

      expect(rendered).toContain(zoneLabel());
      expect(rendered).toMatch(/\b\d{2}:\d{2}\b/);
      expect(rendered).not.toMatch(/am|pm/i);
    }
  });
});

describe('formatMeetupTimeRange', () => {
  it('formatMeetupTimeRange, 14:00 to 14:30 Singapore time, renders 14:00 to 14:30', () => {
    expect(formatMeetupTimeRange(MEETUP_START, MEETUP_END)).toBe('14:00 to 14:30');
  });

  it('formatMeetupTimeRange, a window crossing Singapore midnight, renders 23:45 to 00:15', () => {
    expect(formatMeetupTimeRange(OVERNIGHT_START, OVERNIGHT_END)).toBe('23:45 to 00:15');
  });
});

describe('formatMeetupDate', () => {
  it('formatMeetupDate, a meetup entirely within one Singapore day, renders one date', () => {
    expect(formatMeetupDate(MEETUP_START, MEETUP_END)).toBe('Fri 2 Oct 2026');
  });

  it('formatMeetupDate, a meetup crossing Singapore midnight, renders both dates joined by to', () => {
    expect(formatMeetupDate(OVERNIGHT_START, OVERNIGHT_END)).toBe('Fri 2 Oct 2026 to Sat 3 Oct 2026');
  });

  it('formatMeetupDate, a meetup ending at the last instant of its own Singapore day, still renders one date', () => {
    const endOfDay = new Date('2026-10-02T15:59:59.999Z'); // 23:59:59.999 SGT

    expect(formatMeetupDate(MEETUP_START, endOfDay)).toBe('Fri 2 Oct 2026');
  });
});

describe('formatting with the module evaluated under a foreign host zone', () => {
  // Intl.DateTimeFormat caches its zone at construction, so stubbing TZ around
  // an ALREADY-IMPORTED module proves nothing: a module-scope formatter built
  // without an explicit timeZone would keep whatever zone it captured at
  // import and sail through. resetModules() forces a fresh evaluation under
  // the stubbed zone, which is the only arrangement that catches that bug.
  //
  // Pacific/Kiritimati is UTC+14, ahead of Singapore, so a leaked host zone
  // shifts both the wall clock AND the calendar date. The suite's baseline
  // zone is America/New_York, behind Singapore, covering the other direction.

  async function importTimeUnderZone(zone: string): Promise<typeof import('@/domain/time')> {
    vi.stubEnv('TZ', zone);
    vi.resetModules();
    return import('@/domain/time');
  }

  afterEach(() => {
    // unstubAllEnvs restores the baseline TZ from vitest.config.mts. Deleting
    // process.env.TZ would NOT restore the host zone, it would reset the
    // process to UTC and quietly weaken every later suite in this file.
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it('formatZonedDate, the module evaluated under Pacific/Kiritimati, renders the Singapore date', async () => {
    const time = await importTimeUnderZone('Pacific/Kiritimati');

    // Under UTC+14 this instant is already Sat 3 Oct.
    expect(time.formatZonedDate(OVERNIGHT_START)).toBe('Fri 2 Oct 2026');
    expect(time.formatZonedDate(MEETUP_START)).toBe('Fri 2 Oct 2026');
  });

  it('formatZonedTime, the module evaluated under Pacific/Kiritimati, renders the Singapore time', async () => {
    const time = await importTimeUnderZone('Pacific/Kiritimati');

    // Under UTC+14 this instant is 20:00, not 14:00.
    expect(time.formatZonedTime(MEETUP_START)).toBe('14:00');
    expect(time.formatZonedTime(SINGAPORE_MIDNIGHT)).toBe('00:00');
  });

  it('formatZonedDateTime, the module evaluated under Pacific/Kiritimati, renders the Singapore instant and label', async () => {
    const time = await importTimeUnderZone('Pacific/Kiritimati');

    expect(time.formatZonedDateTime(MEETUP_START)).toBe('Fri 2 Oct 2026, 14:00 SGT');
  });

  it('formatMeetupDate, the module evaluated under Pacific/Kiritimati, still renders one date for one Singapore day', async () => {
    const time = await importTimeUnderZone('Pacific/Kiritimati');

    expect(time.formatMeetupDate(MEETUP_START, MEETUP_END)).toBe('Fri 2 Oct 2026');
    expect(time.formatMeetupDate(OVERNIGHT_START, OVERNIGHT_END)).toBe('Fri 2 Oct 2026 to Sat 3 Oct 2026');
  });

  it('formatMeetupTimeRange, the module evaluated under Pacific/Kiritimati, renders the Singapore range', async () => {
    const time = await importTimeUnderZone('Pacific/Kiritimati');

    expect(time.formatMeetupTimeRange(MEETUP_START, MEETUP_END)).toBe('14:00 to 14:30');
  });

  it('zoneLabel, the module evaluated under Pacific/Kiritimati, still reports the configured label', async () => {
    const time = await importTimeUnderZone('Pacific/Kiritimati');

    expect(time.zoneLabel()).toBe('SGT');
  });

  it('isWithinSchedulingHorizon, the module evaluated under Pacific/Kiritimati, judges the boundary in Singapore', async () => {
    const time = await importTimeUnderZone('Pacific/Kiritimati');
    const today = time.fixedClock(new Date('2026-10-02T06:00:00.000Z'));

    expect(time.isWithinSchedulingHorizon(today, new Date('2026-12-01T15:59:59.999Z'))).toBe(true);
    expect(time.isWithinSchedulingHorizon(today, new Date('2026-12-01T16:00:00.000Z'))).toBe(false);
  });

  it('formatZonedTime, the module evaluated under UTC, renders the Singapore time', async () => {
    // UTC is the zone a careless `delete process.env.TZ` leaves behind, and the
    // one a container with no TZ set defaults to. Eight hours off Singapore.
    const time = await importTimeUnderZone('UTC');

    expect(time.formatZonedTime(MEETUP_START)).toBe('14:00');
    expect(time.formatZonedDate(SINGAPORE_MIDNIGHT)).toBe('Fri 2 Oct 2026');
  });
});

describe('isStartInFuture', () => {
  it('isStartInFuture, a start one millisecond after now, reports true', () => {
    const clock = fixedClock(new Date('2026-10-02T05:59:59.999Z'));

    expect(isStartInFuture(clock, MEETUP_START)).toBe(true);
  });

  it('isStartInFuture, a start exactly at now, reports false', () => {
    const clock = fixedClock(new Date('2026-10-02T06:00:00.000Z'));

    expect(isStartInFuture(clock, MEETUP_START)).toBe(false);
  });

  it('isStartInFuture, a start one millisecond before now, reports false', () => {
    const clock = fixedClock(new Date('2026-10-02T06:00:00.001Z'));

    expect(isStartInFuture(clock, MEETUP_START)).toBe(false);
  });

  it('isStartInFuture, a clock advanced past the start, flips from true to false', () => {
    const clock = fixedClock(new Date('2026-10-02T05:00:00.000Z'));
    expect(isStartInFuture(clock, MEETUP_START)).toBe(true);

    clock.advanceBy(60 * 60 * 1000);

    expect(isStartInFuture(clock, MEETUP_START)).toBe(false);
  });
});

describe('isWithinSchedulingHorizon', () => {
  // Today in Singapore is Fri 2 Oct 2026 (14:00 SGT).
  // Day 60 after today is Tue 1 Dec 2026. Day 61 is Wed 2 Dec 2026.
  const today = () => fixedClock(new Date('2026-10-02T06:00:00.000Z'));

  it('MEETUP_HORIZON_DAYS, the configured booking window, is 60 days', () => {
    expect(MEETUP_HORIZON_DAYS).toBe(60);
  });

  it('isWithinSchedulingHorizon, a start later today, reports true', () => {
    expect(isWithinSchedulingHorizon(today(), new Date('2026-10-02T10:00:00.000Z'))).toBe(true);
  });

  it('isWithinSchedulingHorizon, a start on day 59, reports true', () => {
    expect(isWithinSchedulingHorizon(today(), new Date('2026-11-30T04:00:00.000Z'))).toBe(true);
  });

  it('isWithinSchedulingHorizon, a start at the very start of day 60, reports true', () => {
    // 1 Dec 2026, 00:00 SGT
    expect(isWithinSchedulingHorizon(today(), new Date('2026-11-30T16:00:00.000Z'))).toBe(true);
  });

  it('isWithinSchedulingHorizon, a start at the very end of day 60, reports true', () => {
    // 1 Dec 2026, 23:59:59.999 SGT
    expect(isWithinSchedulingHorizon(today(), new Date('2026-12-01T15:59:59.999Z'))).toBe(true);
  });

  it('isWithinSchedulingHorizon, a start at the very start of day 61, reports false', () => {
    // 2 Dec 2026, 00:00 SGT
    expect(isWithinSchedulingHorizon(today(), new Date('2026-12-01T16:00:00.000Z'))).toBe(false);
  });

  it('isWithinSchedulingHorizon, a start on day 61 later in the day, reports false', () => {
    expect(isWithinSchedulingHorizon(today(), new Date('2026-12-02T04:00:00.000Z'))).toBe(false);
  });

  it('isWithinSchedulingHorizon, a start in the past, reports true because the helper has no lower bound', () => {
    // Deliberate: isStartInFuture is the separate guard. Pinned so the
    // implementer does not invent a lower bound here.
    expect(isWithinSchedulingHorizon(today(), new Date('2025-01-01T00:00:00.000Z'))).toBe(true);
    expect(isWithinSchedulingHorizon(today(), new Date('2026-10-01T06:00:00.000Z'))).toBe(true);
  });

  it('isWithinSchedulingHorizon, a clock late on a Singapore evening, still counts today as day zero', () => {
    // 2 Oct 2026, 23:59:59 SGT -> horizon is still 1 Dec 2026.
    const lateTonight = fixedClock(new Date('2026-10-02T15:59:59.000Z'));

    expect(isWithinSchedulingHorizon(lateTonight, new Date('2026-12-01T15:59:59.999Z'))).toBe(true);
    expect(isWithinSchedulingHorizon(lateTonight, new Date('2026-12-01T16:00:00.000Z'))).toBe(false);
  });

  it('isWithinSchedulingHorizon, a clock just past Singapore midnight, rolls the horizon forward a day', () => {
    // 3 Oct 2026, 00:00 SGT -> horizon becomes 2 Dec 2026.
    const justPastMidnight = fixedClock(new Date('2026-10-02T16:00:00.000Z'));

    expect(isWithinSchedulingHorizon(justPastMidnight, new Date('2026-12-01T16:00:00.000Z'))).toBe(true);
    expect(isWithinSchedulingHorizon(justPastMidnight, new Date('2026-12-02T16:00:00.000Z'))).toBe(false);
  });
});

describe('time module invalid input', () => {
  const INVALID_DATES: readonly Date[] = [
    new Date(Number.NaN),
    new Date('not a date'),
    new Date(undefined as unknown as string),
  ];

  /**
   * Every public entry point that takes a Date, each called with an Invalid
   * Date in one argument position. Built lazily inside the `it` so that a
   * module which throws fails these tests rather than zeroing the file at
   * collection time.
   */
  function entryPointsWithInvalid(invalid: Date): readonly { name: string; call: () => unknown }[] {
    const valid = MEETUP_START;
    const clock = () => fixedClock(valid);

    return [
      { name: 'formatZonedDate', call: () => formatZonedDate(invalid) },
      { name: 'formatZonedTime', call: () => formatZonedTime(invalid) },
      { name: 'formatZonedDateTime', call: () => formatZonedDateTime(invalid) },
      { name: 'formatMeetupDate start', call: () => formatMeetupDate(invalid, valid) },
      { name: 'formatMeetupDate end', call: () => formatMeetupDate(valid, invalid) },
      { name: 'formatMeetupTimeRange start', call: () => formatMeetupTimeRange(invalid, valid) },
      { name: 'formatMeetupTimeRange end', call: () => formatMeetupTimeRange(valid, invalid) },
      { name: 'isStartInFuture', call: () => isStartInFuture(clock(), invalid) },
      { name: 'isWithinSchedulingHorizon', call: () => isWithinSchedulingHorizon(clock(), invalid) },
      { name: 'hasMeetupEnded', call: () => hasMeetupEnded(clock(), invalid) },
    ];
  }

  it('every public entry point, an Invalid Date, throws TypeError under one shared policy', () => {
    for (const invalid of INVALID_DATES) {
      for (const entry of entryPointsWithInvalid(invalid)) {
        expect(entry.call, entry.name).toThrow(TypeError);
      }
    }
  });

  it('every public entry point, an Invalid Date, throws a deliberate guard rather than leaking a RangeError from Intl', () => {
    for (const entry of entryPointsWithInvalid(new Date(Number.NaN))) {
      let thrown: unknown;
      try {
        entry.call();
      } catch (error: unknown) {
        thrown = error;
      }

      expect(thrown, entry.name).toBeInstanceOf(TypeError);
      expect(thrown, entry.name).not.toBeInstanceOf(RangeError);
    }
  });

  it('hasMeetupEnded, an Invalid Date for the end, throws rather than reporting that the meetup never ends', () => {
    // The dangerous case. Returning false here leaves a sale open forever.
    const clock = fixedClock(new Date('2030-01-01T00:00:00.000Z'));

    expect(() => hasMeetupEnded(clock, new Date(Number.NaN))).toThrow(TypeError);
  });

  it('every public entry point, a value that is not a Date, throws TypeError', () => {
    const notADate = '2026-10-02T06:00:00.000Z' as unknown as Date;
    const clock = fixedClock(MEETUP_START);

    expect(() => formatZonedDate(notADate)).toThrow(TypeError);
    expect(() => formatZonedTime(notADate)).toThrow(TypeError);
    expect(() => formatZonedDateTime(notADate)).toThrow(TypeError);
    expect(() => formatMeetupDate(notADate, MEETUP_END)).toThrow(TypeError);
    expect(() => formatMeetupTimeRange(MEETUP_START, notADate)).toThrow(TypeError);
    expect(() => isStartInFuture(clock, notADate)).toThrow(TypeError);
    expect(() => isWithinSchedulingHorizon(clock, notADate)).toThrow(TypeError);
    expect(() => hasMeetupEnded(clock, notADate)).toThrow(TypeError);
  });

  it('every public entry point, valid input, still returns normally', () => {
    // Guards against an over-eager implementation that rejects good input too.
    const clock = fixedClock(MEETUP_START);

    expect(() => formatZonedDate(MEETUP_START)).not.toThrow();
    expect(() => formatZonedTime(MEETUP_START)).not.toThrow();
    expect(() => formatZonedDateTime(MEETUP_START)).not.toThrow();
    expect(() => formatMeetupDate(MEETUP_START, MEETUP_END)).not.toThrow();
    expect(() => formatMeetupTimeRange(MEETUP_START, MEETUP_END)).not.toThrow();
    expect(() => isStartInFuture(clock, MEETUP_END)).not.toThrow();
    expect(() => isWithinSchedulingHorizon(clock, MEETUP_END)).not.toThrow();
    expect(() => hasMeetupEnded(clock, MEETUP_END)).not.toThrow();
  });
});

describe('time module cross-realm and exotic Dates', () => {
  /**
   * A real Date built in another realm. `instanceof Date` is realm-bound and
   * reports false for it, but it has a genuine internal date slot, so
   * `Date.prototype.getTime.call(it)` works and Intl formats it correctly.
   * `Object.prototype.toString.call` reports '[object Date]' for it. The
   * realistic source is a jsdom component test, where it fails safe today but
   * would reject perfectly valid input.
   */
  function foreignDate(iso: string): Date {
    return runInNewContext(`new Date(${JSON.stringify(iso)})`) as Date;
  }

  it('every public entry point, a Date from another realm, accepts it and renders the same output', () => {
    const start = foreignDate('2026-10-02T06:00:00.000Z');
    const end = foreignDate('2026-10-02T06:30:00.000Z');
    expect(start instanceof Date).toBe(false);

    expect(formatZonedDate(start)).toBe('Fri 2 Oct 2026');
    expect(formatZonedTime(start)).toBe('14:00');
    expect(formatZonedDateTime(start)).toBe('Fri 2 Oct 2026, 14:00 SGT');
    expect(formatMeetupDate(start, end)).toBe('Fri 2 Oct 2026');
    expect(formatMeetupTimeRange(start, end)).toBe('14:00 to 14:30');

    const clock = fixedClock(new Date('2026-10-02T05:00:00.000Z'));
    expect(isStartInFuture(clock, start)).toBe(true);
    expect(isWithinSchedulingHorizon(clock, start)).toBe(true);
    expect(hasMeetupEnded(clock, end)).toBe(false);
  });

  it('every public entry point, a Proxy-wrapped Date, rejects it with the module guard not the engine message', () => {
    // A Proxy passes `instanceof Date` but has no internal date slot, so
    // getTime() throws V8's own "this is not a Date object", which is exactly
    // the raw engine message the deliberate guard exists to prevent.
    const proxied = new Proxy(new Date('2026-10-02T06:00:00.000Z'), {}) as Date;
    expect(proxied instanceof Date).toBe(true);

    const valid = MEETUP_END;
    const clock = fixedClock(MEETUP_START);
    const calls: readonly { name: string; call: () => unknown }[] = [
      { name: 'formatZonedDate', call: () => formatZonedDate(proxied) },
      { name: 'formatZonedTime', call: () => formatZonedTime(proxied) },
      { name: 'formatZonedDateTime', call: () => formatZonedDateTime(proxied) },
      { name: 'formatMeetupDate', call: () => formatMeetupDate(proxied, valid) },
      { name: 'formatMeetupTimeRange', call: () => formatMeetupTimeRange(valid, proxied) },
      { name: 'isStartInFuture', call: () => isStartInFuture(clock, proxied) },
      { name: 'isWithinSchedulingHorizon', call: () => isWithinSchedulingHorizon(clock, proxied) },
      { name: 'hasMeetupEnded', call: () => hasMeetupEnded(clock, proxied) },
    ];

    for (const entry of calls) {
      let thrown: unknown;
      try {
        entry.call();
      } catch (error: unknown) {
        thrown = error;
      }

      expect(thrown, entry.name).toBeInstanceOf(TypeError);
      expect((thrown as Error | undefined)?.message ?? '', entry.name).not.toMatch(
        /this is not a Date object/i,
      );
    }
  });
});

describe('isWithinSchedulingHorizon across the year 99 to 100 boundary', () => {
  // `Date.UTC(50, 5, 15)` means 1950, not year 50, so any day-number helper
  // built on Date.UTC is off by 693,960 days for years 0 to 99. The bug is
  // INVISIBLE when both dates sit inside 0-99, because both shift equally and
  // the difference survives. It shows when one side is in 0-99 and the other
  // in 100+: the difference collapses to about -693,778 days, which is <= 60,
  // so a start half a year out is wrongly reported as within the horizon.
  //
  // Four-digit ISO year strings reach these dates through the public API;
  // Date.UTC cannot express them. Asia/Singapore ran at LMT +06:55:25 then,
  // which does not shift any of the calendar dates used here (all are 00:00Z,
  // so 06:55 the same day). Verified: day 60 is 30 Jan 100, day 61 is 31 Jan.

  const clockInYear99 = () => fixedClock(new Date('0099-12-01T00:00:00Z'));

  it('isWithinSchedulingHorizon, a start on day 60 across the year 100 boundary, reports true', () => {
    expect(isWithinSchedulingHorizon(clockInYear99(), new Date('0100-01-30T00:00:00Z'))).toBe(true);
  });

  it('isWithinSchedulingHorizon, a start on day 61 across the year 100 boundary, reports false', () => {
    // The discriminating case: a Date.UTC-based helper reports true here.
    expect(isWithinSchedulingHorizon(clockInYear99(), new Date('0100-01-31T00:00:00Z'))).toBe(false);
  });

  it('isWithinSchedulingHorizon, a start 182 days out across the year 100 boundary, reports false', () => {
    expect(isWithinSchedulingHorizon(clockInYear99(), new Date('0100-06-01T00:00:00Z'))).toBe(false);
  });

  it('isWithinSchedulingHorizon, a start 45 days out across the year 100 boundary, reports true', () => {
    // Not discriminating on its own, since the buggy helper also says true.
    // Kept so a fix cannot simply reject everything near the boundary.
    expect(isWithinSchedulingHorizon(clockInYear99(), new Date('0100-01-15T00:00:00Z'))).toBe(true);
  });

  it('isWithinSchedulingHorizon, a clock and start both inside years 0 to 99, still honours day 60 and 61', () => {
    const clockInYear50 = fixedClock(new Date('0050-06-15T00:00:00Z'));

    expect(isWithinSchedulingHorizon(clockInYear50, new Date('0050-08-14T00:00:00Z'))).toBe(true);
    expect(isWithinSchedulingHorizon(clockInYear50, new Date('0050-08-15T00:00:00Z'))).toBe(false);
  });
});

describe('hasMeetupEnded', () => {
  it('hasMeetupEnded, an end one millisecond after now, reports false', () => {
    const clock = fixedClock(new Date('2026-10-02T06:29:59.999Z'));

    expect(hasMeetupEnded(clock, MEETUP_END)).toBe(false);
  });

  it('hasMeetupEnded, an end exactly at now, reports true', () => {
    const clock = fixedClock(new Date('2026-10-02T06:30:00.000Z'));

    expect(hasMeetupEnded(clock, MEETUP_END)).toBe(true);
  });

  it('hasMeetupEnded, an end one millisecond before now, reports true', () => {
    const clock = fixedClock(new Date('2026-10-02T06:30:00.001Z'));

    expect(hasMeetupEnded(clock, MEETUP_END)).toBe(true);
  });

  it('hasMeetupEnded, a clock advanced across the end instant, flips from false to true', () => {
    const clock = fixedClock(new Date('2026-10-02T06:29:59.999Z'));
    expect(hasMeetupEnded(clock, MEETUP_END)).toBe(false);

    clock.advanceBy(1);

    expect(hasMeetupEnded(clock, MEETUP_END)).toBe(true);
  });

  it('hasMeetupEnded, a meetup still days away, reports false', () => {
    const clock = fixedClock(new Date('2026-09-28T06:00:00.000Z'));

    expect(hasMeetupEnded(clock, MEETUP_END)).toBe(false);
  });
});
