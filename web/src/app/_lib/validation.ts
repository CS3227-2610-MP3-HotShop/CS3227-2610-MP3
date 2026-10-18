export type FieldErrors = Readonly<Record<string, string>>;
export type FormKind = 'login' | 'register' | 'profile' | 'change-password' | 'listing' | 'offer' | 'message';
export type RawFields = Readonly<Record<string, string>>;
export const MAX_MONEY_CENTS = 100_000_000;
export const CATEGORIES = ['ELECTRONICS', 'BOOKS', 'CLOTHING', 'FURNITURE', 'SPORTS', 'OTHER'] as const;
export const CONDITIONS = ['NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR'] as const;

// Character.isWhitespace excludes the three non-breaking spaces and the BOM.
const JAVA_EDGE_WHITESPACE = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/g;

export function stripText(value: string): string {
  return value.replace(JAVA_EDGE_WHITESPACE, '');
}

export function codePointLength(value: string): number {
  return Array.from(value).length;
}

export function textError(value: string, maximum: number, required = true): string | null {
  const text = stripText(value);
  if (required && text === '') return 'This field is required.';
  return codePointLength(text) > maximum ? `Use at most ${maximum} characters.` : null;
}

export type MoneyResult = { readonly ok: true; readonly cents: number | null }
  | { readonly ok: false; readonly error: string };

/** Parse digit strings into cents without decimal floating-point arithmetic. */
export function parseMoney(value: string, optional = false, minimum = 1): MoneyResult {
  const text = stripText(value);
  if (optional && text === '') return { ok: true, cents: null };
  if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(text)) {
    return { ok: false, error: 'Enter an SGD amount with at most two decimal places.' };
  }
  const [whole, fraction = ''] = text.split('.');
  const digits = (whole + fraction.padEnd(2, '0')).replace(/^0+/, '') || '0';
  if (digits.length > 9 || Number(digits) > MAX_MONEY_CENTS || Number(digits) < minimum) {
    return { ok: false, error: minimum === 0 ? 'Maximum permitted amount is S$1,000,000.00.'
      : 'Enter S$0.01 to S$1,000,000.00.' };
  }
  return { ok: true, cents: Number(digits) };
}

export function passwordError(value: string): string | null {
  const length = codePointLength(value);
  return length >= 8 && length <= 128 && /[A-Z]/.test(value) && /[a-z]/.test(value)
    && /[0-9]/.test(value) && /[\x21-\x2f\x3a-\x40\x5b-\x60\x7b-\x7e]/.test(value) ? null
    : 'Password requires 8-128 characters with uppercase, lowercase, digit and ASCII punctuation';
}

/** Password values remain with the calling form. */
export function validateFields(kind: FormKind, fields: RawFields): FieldErrors {
  const errors: Record<string, string> = {};
  const value = (id: string) => fields[id] ?? '';
  const check = (id: string, maximum: number, required = true) => {
    const error = textError(value(id), maximum, required);
    if (error !== null) errors[id] = error;
  };
  const password = () => {
    const error = passwordError(value('password'));
    if (error !== null) errors.password = error;
    if (value('password') !== value('confirm-password')) errors['confirm-password'] = 'Passwords do not match.';
  };
  const money = (id: string) => {
    const result = parseMoney(value(id));
    if (!result.ok) errors[id] = result.error;
  };
  switch (kind) {
    case 'login':
      check('username', 30);
      if (value('password') === '') errors.password = 'This field is required.';
      else if (codePointLength(value('password')) > 128) errors.password = 'Use at most 128 characters.';
      break;
    case 'register':
      if (!/^[A-Za-z0-9_]{3,30}$/.test(stripText(value('username')))) {
        errors.username = 'Use 3–30 letters, digits or underscores.';
      }
      check('display-name', 80);
      password();
      break;
    case 'profile':
      check('display-name', 80);
      check('preferred-location', 200, false);
      break;
    case 'change-password':
      if (value('current-password') === '') errors['current-password'] = 'This field is required.';
      else if (codePointLength(value('current-password')) > 128) errors['current-password'] = 'Use at most 128 characters.';
      password();
      break;
    case 'listing':
      check('title', 120);
      check('description', 5000);
      check('pickup-location', 200);
      money('price');
      if (!CATEGORIES.some(category => category === value('category'))) errors.category = 'Choose a category.';
      if (!CONDITIONS.some(condition => condition === value('condition'))) errors.condition = 'Choose a condition.';
      break;
    case 'offer':
      money('offer-amount');
      check('offer-message', 1000, false);
      break;
    case 'message':
      check('message', 1000);
      break;
  }
  return errors;
}
