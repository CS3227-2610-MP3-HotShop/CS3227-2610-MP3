/**
 * Time: an injectable clock, and one fixed display zone.
 *
 * TD-10 — domain code never calls `Date.now()`, never constructs a bare
 * `new Date()`, and never reads a timestamp from the database. It takes a
 * Clock. `systemClock` is the only place the host clock is read.
 * FR-1718 — every rendered instant is evaluated in APP_TIME_ZONE and carries a
 * zone label, so both parties to a sale read the same string on any device.
 * FR-1719 — a start must be in the future, inside the 60 calendar-day horizon,
 * and a meetup is over once its end is no longer ahead of now.
 *
 * INVALID INPUT THROWS TypeError AT EVERY PUBLIC ENTRY POINT, matching
 * `@/domain/errors`. An Invalid Date is never a legitimate domain value: it
 * arrives from `new Date(row.starts_at)` on a null column. Answering a boolean
 * question about one produces a confidently wrong answer that then propagates,
 * and the worst case is `hasMeetupEnded` reporting that a meetup never ends,
 * which leaves a sale open forever. The explicit TypeError also keeps a
 * deliberate guard distinguishable from a RangeError leaking out of Intl.
 */

export interface Clock {
  /** A fresh Date each call, so a caller cannot mutate the clock through it. */
  now(): Date;
}

export interface FixedClock extends Clock {
  advanceBy(milliseconds: number): void;
}

/** The largest instant a Date can represent, in either direction. */
const MAX_DATE_MILLIS = 8.64e15;

/** Avoids String() on an arbitrary value, which throws for a symbol. */
function describeValue(value: unknown): string {
  return typeof value === 'number' ? String(value) : typeof value;
}

/**
 * The single Date guard, returning the instant as epoch milliseconds so every
 * caller works from the number rather than re-reading the Date.
 *
 * The brand check is `Object.prototype.toString`, not `instanceof Date`, for
 * two reasons that pull in opposite directions. `instanceof` is realm-bound, so
 * it rejects a perfectly good Date from a `node:vm` context or a jsdom realm,
 * which has a real internal date slot and formats correctly. And `instanceof`
 * ACCEPTS `new Proxy(new Date(), {})`, which has no internal slot at all, so
 * reading it throws V8's own "this is not a Date object" — precisely the raw
 * engine message this guard exists to keep out. `Object.prototype.toString`
 * separates the two cleanly: '[object Date]' for the cross-realm Date,
 * '[object Object]' for the Proxy.
 */
function requireInstant(value: Date, label: string): number {
  if (Object.prototype.toString.call(value) !== '[object Date]') {
    throw new TypeError(`${label} must be a Date, received ${describeValue(value)}`);
  }

  // Called off the prototype so a cross-realm Date is read through this
  // realm's method rather than its own.
  const epochMillis = Date.prototype.getTime.call(value);
  if (Number.isNaN(epochMillis)) {
    throw new TypeError(`${label} must be a valid Date, received an Invalid Date`);
  }

  return epochMillis;
}

function requireSafeInteger(value: number, label: string): void {
  if (!Number.isSafeInteger(value)) {
    throw new TypeError(`${label} must be a safe integer, received ${describeValue(value)}`);
  }
}

/** A clock pinned to one instant until advanced. The argument is copied. */
export function fixedClock(instant: Date): FixedClock {
  let epochMillis = requireInstant(instant, 'instant');

  return {
    now(): Date {
      return new Date(epochMillis);
    },
    advanceBy(milliseconds: number): void {
      requireSafeInteger(milliseconds, 'milliseconds');

      // The argument can be a safe integer whose RESULT is not a representable
      // instant: Number.MAX_SAFE_INTEGER passes the guard above and lands past
      // the Date range, where now() would quietly return an Invalid Date. Both
      // checks run before the mutation, so a rejected advance leaves the clock
      // on its previous instant and still usable.
      const advanced = epochMillis + milliseconds;
      if (Math.abs(advanced) > MAX_DATE_MILLIS) {
        throw new TypeError(
          `advancing by ${milliseconds} would leave the representable Date range`,
        );
      }

      epochMillis = advanced;
    },
  };
}

/** The real clock, read afresh on every call rather than at construction. */
export function systemClock(): Clock {
  return {
    now(): Date {
      return new Date();
    },
  };
}

// Written as literals rather than as products of one another so each keeps its
// literal type.
export const MILLIS_PER_SECOND = 1000;
export const MILLIS_PER_MINUTE = 60000;
export const MILLIS_PER_HOUR = 3600000;
export const MILLIS_PER_DAY = 86400000;

export const APP_TIME_ZONE = 'Asia/Singapore';

/** Short display label for APP_TIME_ZONE, not the IANA identifier. */
export function zoneLabel(): string {
  return 'SGT';
}

// Every formatter names its timeZone explicitly. Intl.DateTimeFormat resolves
// and caches the zone at CONSTRUCTION, so a formatter built without this option
// would silently lock onto whatever zone the process happened to have when this
// module was first evaluated. They are fed epoch milliseconds rather than Date
// objects, which Intl accepts directly and which keeps realm questions out.

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: APP_TIME_ZONE,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

const TIME_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: APP_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const CIVIL_DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: APP_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

function partsOf(formatter: Intl.DateTimeFormat, epochMillis: number): Record<string, string> {
  const parts: Record<string, string> = {};
  for (const part of formatter.formatToParts(epochMillis)) {
    parts[part.type] = part.value;
  }
  return parts;
}

/**
 * `en-GB` renders this pattern as 'Fri, 2 Oct 2026'. The house style has no
 * comma after the weekday, so the parts are recomposed by hand.
 */
function renderDate(epochMillis: number): string {
  const { weekday, day, month, year } = partsOf(DATE_FORMAT, epochMillis);

  return `${weekday} ${day} ${month} ${year}`;
}

function renderTime(epochMillis: number): string {
  return TIME_FORMAT.format(epochMillis);
}

export function formatZonedDate(instant: Date): string {
  return renderDate(requireInstant(instant, 'instant'));
}

export function formatZonedTime(instant: Date): string {
  return renderTime(requireInstant(instant, 'instant'));
}

export function formatZonedDateTime(instant: Date): string {
  const epochMillis = requireInstant(instant, 'instant');

  return `${renderDate(epochMillis)}, ${renderTime(epochMillis)} ${zoneLabel()}`;
}

export function formatMeetupDate(start: Date, end: Date): string {
  const startDate = renderDate(requireInstant(start, 'start'));
  const endDate = renderDate(requireInstant(end, 'end'));

  return startDate === endDate ? startDate : `${startDate} to ${endDate}`;
}

export function formatMeetupTimeRange(start: Date, end: Date): string {
  const startTime = renderTime(requireInstant(start, 'start'));
  const endTime = renderTime(requireInstant(end, 'end'));

  return `${startTime} to ${endTime}`;
}

export const MEETUP_HORIZON_DAYS = 60;

/** Strictly after now: an instant equal to now is not in the future. */
export function isStartInFuture(clock: Clock, start: Date): boolean {
  const startMillis = requireInstant(start, 'start');
  const nowMillis = requireInstant(clock.now(), 'clock.now()');

  return startMillis > nowMillis;
}

/**
 * Days since 1970-01-01 for a proleptic Gregorian calendar date.
 *
 * Deliberately NOT `Date.UTC`, which maps years 0 to 99 onto 1900 to 1999 and
 * so lands 693,960 days out for them. The error is invisible while both dates
 * sit inside that range, because both shift equally, and shows up the moment
 * one side crosses into year 100. Four-digit ISO year strings reach those
 * dates through the public API, so the range is not hypothetical.
 */
function daysFromCivil(year: number, month: number, day: number): number {
  // Shift the year so that March is month 0 and the leap day falls last.
  const shiftedYear = month <= 2 ? year - 1 : year;
  const era = Math.floor(shiftedYear / 400);
  const yearOfEra = shiftedYear - era * 400;
  const monthFromMarch = (month + 9) % 12;
  const dayOfYear = Math.floor((153 * monthFromMarch + 2) / 5) + day - 1;
  const dayOfEra =
    yearOfEra * 365 + Math.floor(yearOfEra / 4) - Math.floor(yearOfEra / 100) + dayOfYear;

  // 146097 days per 400-year era; 719468 days from 0000-03-01 to 1970-01-01.
  return era * 146097 + dayOfEra - 719468;
}

/**
 * The day number of an instant's CALENDAR date in APP_TIME_ZONE. Subtracting
 * two of these counts calendar days the way a person does, which a millisecond
 * difference would not: it is unaffected by the time of day.
 */
function zonedDayNumber(epochMillis: number): number {
  const { year, month, day } = partsOf(CIVIL_DATE_FORMAT, epochMillis);

  return daysFromCivil(Number(year), Number(month), Number(day));
}

/**
 * True when start's calendar date in APP_TIME_ZONE is on or before the
 * MEETUP_HORIZON_DAYS-th calendar day after today's. Day 60 is allowed at any
 * time of day; day 61 is refused from its first instant.
 *
 * Deliberately has NO lower bound: a start in the past is within the horizon.
 * isStartInFuture is the separate guard, and callers apply both.
 */
export function isWithinSchedulingHorizon(clock: Clock, start: Date): boolean {
  const startDay = zonedDayNumber(requireInstant(start, 'start'));
  const todayDay = zonedDayNumber(requireInstant(clock.now(), 'clock.now()'));

  return startDay - todayDay <= MEETUP_HORIZON_DAYS;
}

/** True once end is no longer after now, i.e. end <= now. */
export function hasMeetupEnded(clock: Clock, end: Date): boolean {
  const endMillis = requireInstant(end, 'end');
  const nowMillis = requireInstant(clock.now(), 'clock.now()');

  return endMillis <= nowMillis;
}
