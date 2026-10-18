/**
 * `npm run db:check` — the "are my Supabase credentials right" script.
 *
 * Contract and the honest limits of what it can prove: the header of
 * `tests/integration/db-check-script.test.ts`.
 *
 * It imports `postgres` DIRECTLY rather than going through `@/server/db`. Node
 * runs this file without the `@/` alias, which only Vite and tsc know about, so
 * the whole module graph behind that alias would have to resolve before the
 * script could start. A credentials checker that cannot run until the
 * application compiles is useless at exactly the moment it is wanted. The cost
 * is one duplicated `prepare: false`.
 *
 * ---------------------------------------------------------------------------
 * THE RULE THIS FILE IS BUILT AROUND: NEVER ECHO THE VALUE
 * ---------------------------------------------------------------------------
 *
 * DATABASE_URL is a secret that a user hand-edits in `.env.local`, and this is
 * the command they run when it does not work, whose output they paste into a
 * chat window. A typo in that file is the ORDINARY case.
 *
 * So nothing derived from the raw string is ever printed. The only things that
 * reach the terminal are:
 *
 *   - fixed prose;
 *   - host, port and database name taken from a URL that PARSED and passed
 *     validation, which structurally excludes the userinfo where the password
 *     lives;
 *   - the driver's error message, run through the redactor.
 *
 * Two leak paths that an earlier version had, both now closed by construction:
 *
 *   1. The client was built outside the `try`, so a throw from `postgres()`
 *      went straight out of the process and Node printed the whole error, with
 *      the connection string in its `input:` field, plus a driver stack.
 *
 *      THE TRY IS WHAT CLOSES THIS, not the validation below, and the
 *      distinction is load-bearing rather than pedantic. Validation only
 *      catches what `new URL` rejects. `postgresql://user:SE%GGCRET@host/db`
 *      parses cleanly and passes every check here, and then `postgres()` throws
 *      `URIError: URI malformed` from `decodeURIComponent` at `index.js:550` —
 *      a bad percent-escape in a password is an ordinary typo, and only the
 *      try/catch stands between it and a crash dump containing the password.
 *   2. `postgresql:user:pw@host/db` (slashes dropped) PARSES, as a scheme plus
 *      an opaque path, leaving `password` empty. A redactor keyed on
 *      `parsed.password` therefore stood down, and the credentials rode out
 *      inside the description of the target. What closes this is validation
 *      plus describing the target from parsed FIELDS instead of the path:
 *      scrubbing harder would not have, since there was no password to find.
 *
 * Node strips the types natively, so there is no build step. Keep the syntax
 * erasable: no enums, no namespaces, no parameter properties.
 */

import { fileURLToPath } from 'node:url';

import postgres from 'postgres';

const APP_URL_VAR = 'DATABASE_URL';
const ENV_FILE_VAR = 'DB_CHECK_ENV_FILE';

/** The file the credentials were most likely just pasted into. */
const DEFAULT_ENV_FILE = fileURLToPath(new URL('../.env.local', import.meta.url));

function configuredUrl(): string {
  return process.env[APP_URL_VAR]?.trim() ?? '';
}

/**
 * Falls back to an env file when the environment has no usable DATABASE_URL.
 * An existing environment variable always wins, so CI stays authoritative, and
 * a missing file is not an error.
 *
 * THE GOTCHA: `process.loadEnvFile` will not overwrite a variable that already
 * exists, and an exported-but-empty `DATABASE_URL=` counts as existing. Without
 * the delete below, the file is silently ignored and the script reports
 * "nothing is configured" while staring straight at the credentials.
 */
function loadEnvFileFallback(): void {
  if (configuredUrl() !== '') return;

  delete process.env[APP_URL_VAR];

  const override = process.env[ENV_FILE_VAR]?.trim() ?? '';
  const path = override === '' ? DEFAULT_ENV_FILE : override;

  try {
    process.loadEnvFile(path);
  } catch {
    // No file there: the environment is simply not configured that way.
  }
}

/**
 * The PASSWORD of a connection string, found TEXTUALLY rather than through
 * `new URL`: the segment after the first `:` of the userinfo, where the
 * userinfo is everything between the scheme and the last `@` of the authority.
 *
 * ONLY the post-colon segment. An earlier version also returned the whole
 * userinfo when there was no colon, which made a bare USERNAME a redaction
 * target — and a passwordless URL is the project's own local shape
 * (`postgresql://hotshop@127.0.0.1:5433/postgres`), so the ordinary local
 * failure came out as `role "***" does not exist` and told the developer
 * nothing. A username is not a secret, and scrubbing it destroys exactly the
 * diagnostic the script exists to give.
 *
 * SCOPE, stated carefully because the previous note had this backwards. This
 * is reached whenever a password is present, and it is the half that works when
 * `new URL` exposes something different from what the user meant, as it does
 * for an unencoded `@` in a password.
 */
function passwordIn(raw: string): string[] {
  const afterScheme = /^[^:]*:(?:\/\/)?(.*)$/.exec(raw);
  if (afterScheme === null) return [];

  const rest = afterScheme[1] ?? '';
  const authority = rest.split(/[/?#]/, 1)[0] ?? '';

  const lastAt = authority.lastIndexOf('@');
  if (lastAt < 0) return [];

  const userinfo = authority.slice(0, lastAt);
  const colon = userinfo.indexOf(':');

  return colon < 0 ? [] : [userinfo.slice(colon + 1)];
}

/**
 * Scrubs the password out of driver text.
 *
 * Applied ONLY to the driver's error message, never to the description of the
 * target. The target is built from host, port and database name, which cannot
 * contain the userinfo, and running a blunt substring replace over it is how a
 * one-character password turns `127.0.0.1` into `***27.0.0.***`. Over-scrubbing
 * driver text is the safe side of that trade; over-scrubbing the one line that
 * tells the user WHICH database they reached is not.
 */
function makeRedactor(rawUrl: string): (text: string) => string {
  const secrets = new Set<string>();

  const add = (secret: string): void => {
    if (secret !== '') secrets.add(secret);
  };

  try {
    const parsed = new URL(rawUrl);
    add(parsed.password);
    add(safeDecode(parsed.password));
  } catch {
    // Unparseable, which is precisely why the textual pass below exists.
  }

  for (const secret of passwordIn(rawUrl)) {
    add(secret);
    add(safeDecode(secret));
  }

  if (secrets.size === 0) return (text) => text;

  // Longest first, so a password that contains another secret as a prefix is
  // replaced whole rather than leaving a readable tail behind.
  const ordered = [...secrets].sort((a, b) => b.length - a.length);

  return (text) => {
    let scrubbed = text;
    for (const secret of ordered) {
      scrubbed = scrubbed.split(secret).join('***');
    }
    return scrubbed;
  };
}

function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

/**
 * A connection string this script is willing to act on, or `undefined`.
 *
 * The host check is what rejects `postgresql:user:pw@host/db`: dropping the
 * slashes makes the whole thing an opaque path with no host, which is both
 * unusable and the shape that used to carry the password into the output.
 */
function parseConnectionString(raw: string): URL | undefined {
  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    return undefined;
  }

  if (parsed.protocol !== 'postgres:' && parsed.protocol !== 'postgresql:') return undefined;
  if (parsed.hostname === '') return undefined;

  return parsed;
}

/**
 * Host spellings that mean "this machine", where there is no wire to intercept.
 */
const LOOPBACK = new Set(['localhost', '127.0.0.1', '::1', '[::1]', '0.0.0.0']);

/**
 * Does this URL carry a TLS instruction postgres.js will ACT ON?
 *
 * Asking `searchParams.has(...)` instead is a route to plaintext: it is true
 * for a valueless `?sslmode=`, so the script steps aside for a query string
 * that says nothing and the driver falls through to `ssl: false`. `?ssl=`
 * does the same one step later, surviving as `''`. The question has to be
 * about the VALUE, because the value is what the driver reads
 * (`index.js:443`, `:446`, `:473`).
 *
 * Deferring only on a non-empty value is what makes this safe in general
 * rather than for those two spellings: a non-empty value cannot resolve to a
 * falsy `ssl`, so there is no path from "we stepped aside" to "cleartext".
 * The full argument is in `@/server/db`, beside the copy of this rule.
 */
function urlSpecifiesTls(parsed: URL): boolean {
  const params = parsed.searchParams;

  for (const key of ['sslmode', 'ssl']) {
    if ((params.get(key) ?? '').trim() !== '') return true;
  }

  return params.get('sslrootcert') === 'system';
}

/**
 * The same TLS rule as `@/server/db`, duplicated for the same reason as
 * `prepare: false`: this script must start without the `@/` alias resolving.
 *
 * Duplicating it is not optional. This is THE command a user runs against real
 * Supabase credentials, so a plaintext default here would send the password
 * they are trying to verify across the internet in the clear — the exact
 * failure the check is supposed to rule out. An explicit `?sslmode=` in the URL
 * still wins, and loopback stays plaintext.
 */
function sslModeFor(parsed: URL): 'verify-full' | undefined {
  if (urlSpecifiesTls(parsed)) return undefined;

  const host = parsed.hostname.toLowerCase();
  if (host === '' || host.startsWith('/') || host.startsWith('%2f')) return undefined;
  if (LOOPBACK.has(host)) return undefined;
  if (/^127\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(host)) return undefined;

  return 'verify-full';
}

/**
 * host:port/database, with the credentials structurally absent: these three
 * fields of a parsed URL cannot contain the userinfo.
 */
function describeTarget(parsed: URL): string {
  const port = parsed.port === '' ? '5432' : parsed.port;
  return `${parsed.hostname}:${port}${parsed.pathname}`;
}

function describeError(error: unknown): string {
  if (error instanceof Error) {
    const code = (error as { code?: unknown }).code;
    return typeof code === 'string' ? `${error.message} (${code})` : error.message;
  }
  return String(error);
}

async function main(): Promise<void> {
  loadEnvFileFallback();

  const url = configuredUrl();
  if (url === '') {
    console.error(
      `${APP_URL_VAR} is not set, so there is nothing to check.\n` +
        'Put it in .env.local at the project root, for example:\n' +
        `  ${APP_URL_VAR}=postgresql://user:password@host:5432/database\n` +
        `Set ${ENV_FILE_VAR} to check a different file.`,
    );
    process.exitCode = 1;
    return;
  }

  const parsed = parseConnectionString(url);
  if (parsed === undefined) {
    // Deliberately says nothing about the value itself, not even a fragment of
    // it: this is the typo case, and the typo is sitting next to the password.
    console.error(
      `${APP_URL_VAR} is set, but it is not a connection string this can use.\n` +
        'Expected postgresql://user:password@host:5432/database, with a host.\n' +
        'The value is not shown here because it contains your password; ' +
        'check it in .env.local for a stray space, a missing //, or an unescaped ' +
        '@ or / inside the password (those must be percent-encoded as %40 and %2F).',
    );
    process.exitCode = 1;
    return;
  }

  const redact = makeRedactor(url);
  const target = describeTarget(parsed);
  const sslMode = sslModeFor(parsed);
  let sql: postgres.Sql | undefined;

  try {
    // Constructed INSIDE the try: postgres.js parses the URL itself, and
    // anything it throws — including `URIError` from a bad percent-escape in
    // the password — must be caught and redacted, never printed raw.
    //
    // The pool settings DIFFER from `@/server/db` on purpose. That module sets
    // `max: 1` with a short `idle_timeout` because a frozen Vercel instance
    // holds its sockets between invocations. This is a one-shot command that
    // runs one query and ends the client explicitly, so there is no idle period
    // to reap and an `idle_timeout` here would only add a way for the script to
    // race itself. `max: 1` is kept, since one query needs exactly one
    // connection, and a short `connect_timeout` so a wrong host fails fast
    // rather than appearing to work slowly.
    sql = postgres(url, {
      prepare: false,
      max: 1,
      connect_timeout: 10,
      onnotice: () => {},
      ...(sslMode === undefined ? {} : { ssl: sslMode }),
    });

    const rows = await sql<{ version: string }[]>`select version()`;
    console.log(`Connected to ${target}`);
    console.log(rows[0]?.version ?? 'Connected, but the server reported no version.');
  } catch (error) {
    console.error(
      `Could not connect to ${target} using ${APP_URL_VAR}.\n${redact(describeError(error))}`,
    );
    process.exitCode = 1;
  } finally {
    // Either way, so the command does not hang on an open handle.
    await sql?.end({ timeout: 5 });
  }
}

await main();
