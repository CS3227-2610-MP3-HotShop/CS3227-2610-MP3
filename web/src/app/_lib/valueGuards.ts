import { MAX_MONEY_CENTS, codePointLength, stripText } from '@/app/_lib/validation';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;

export function isId(value: unknown): value is string {
  return typeof value === 'string' && UUID.test(value);
}

export function isInstant(value: unknown): value is string {
  return typeof value === 'string' && INSTANT.test(value) && Number.isFinite(Date.parse(value))
    && new Date(value).toISOString().slice(0, 19) === value.slice(0, 19);
}

export function isMoneyCents(value: unknown, minimum = 1): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= minimum && value <= MAX_MONEY_CENTS;
}

export function isNormalizedText(value: unknown, maximum: number, minimum = 1): value is string {
  return typeof value === 'string' && value === stripText(value)
    && codePointLength(value) >= minimum && codePointLength(value) <= maximum;
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && Object.getPrototypeOf(value) === Object.prototype;
}

// BACKEND INTEGRATION: compose these primitives with endpoint-specific runtime validation
// once request/response contracts are agreed. These helpers do not validate a complete
// domain object, authorization, meetup duration/overlaps, or cross-record invariants.
