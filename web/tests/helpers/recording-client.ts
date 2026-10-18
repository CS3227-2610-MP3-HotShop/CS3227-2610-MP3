/**
 * A client that remembers the SQL it was asked to run. TEST FIXTURE.
 *
 * WHY THIS EXISTS, and it is worth the paragraph because the first version of
 * the test it serves was WRONG and a mutation proved it.
 *
 * INV17-B9 forbids carrying the actor as session-level state on the connection
 * (`set x = ...`, `set_config(x, v, false)`), because such state survives the
 * commit, stays on the pooled connection, and is there for the next request on
 * a warm serverless instance. The obvious way to test that is to look for the
 * leftover setting afterwards. It does not work: PostgreSQL does not list
 * custom GUCs in `pg_settings` at all — not by name, not by value, on 16 or 17
 * — so the probe returned an empty result whether or not anything leaked.
 *
 *     $ select set_config('hotshop.actor_user_id', 'user-ABCDEF', false);
 *     $ select name, setting from pg_settings where name like 'hotshop%';
 *     (0 rows)
 *     $ select current_setting('hotshop.actor_user_id', true);
 *     user-ABCDEF
 *
 * The value is there; nothing enumerates it. A test built on that query passes
 * against an implementation that leaks, which makes it the same artefact as a
 * guard that never runs, and the mutation sweep for this task is the only
 * reason it was caught before review.
 *
 * So the observation moves to the one place the mechanism cannot hide: the SQL
 * itself. Every way of putting state on a connection is a statement, and every
 * statement passes through the handle. This records them, and the test asserts
 * about what was sent rather than about what survived.
 *
 * Secondary benefit, used by the same test: when the implementation DOES set
 * something transaction-locally, the recorded text names the setting, so the
 * test can look that exact name up afterwards instead of guessing it.
 */

import type postgres from 'postgres';

import type { Db, Tx } from '@/server/db';

export interface RecordingClient {
  /** Pass this as `options.db`. */
  readonly db: Db;
  /** Every statement issued through it, in order, values elided as `?`. */
  readonly statements: readonly string[];
}

type AnyFn = (...args: unknown[]) => unknown;

function isTemplate(value: unknown): value is TemplateStringsArray {
  return Array.isArray(value) && 'raw' in (value as object);
}

export function recordingClient(real: Db): RecordingClient {
  const statements: string[] = [];

  const note = (text: string): void => {
    statements.push(text.replace(/\s+/g, ' ').trim());
  };

  /** Wraps a tagged-template handle so both `` sql`...` `` and `sql.unsafe()` are seen. */
  function wrapHandle<T extends object>(target: T): T {
    return new Proxy(target as unknown as AnyFn, {
      apply(fn, thisArg, args) {
        const [first] = args;
        note(isTemplate(first) ? first.join(' ? ') : String(first));
        return Reflect.apply(fn, thisArg ?? target, args);
      },
      get(fn, property, receiver) {
        const value = Reflect.get(fn, property, receiver) as unknown;
        if (property === 'unsafe' && typeof value === 'function') {
          return (text: string, ...rest: unknown[]) => {
            note(text);
            return (value as AnyFn).apply(target, [text, ...rest]);
          };
        }
        return typeof value === 'function' ? (value as AnyFn).bind(target) : value;
      },
    }) as unknown as T;
  }

  const db = new Proxy(real as unknown as AnyFn, {
    apply(fn, thisArg, args) {
      const [first] = args;
      note(isTemplate(first) ? first.join(' ? ') : String(first));
      return Reflect.apply(fn, thisArg ?? real, args);
    },
    get(fn, property, receiver) {
      if (property === 'begin') {
        return (run: (tx: Tx) => unknown) =>
          real.begin(((tx: postgres.TransactionSql) =>
            run(wrapHandle(tx as unknown as Tx))) as never);
      }
      const value = Reflect.get(fn, property, receiver) as unknown;
      return typeof value === 'function' ? (value as AnyFn).bind(real) : value;
    },
  }) as unknown as Db;

  return { db, statements };
}

/**
 * Statements that put state on the CONNECTION rather than on the transaction.
 *
 * `set local ...` and `set_config(name, value, true)` are transaction-scoped:
 * PostgreSQL reverts them at commit, so they cannot reach the next request.
 * Everything else in this family outlives the transaction and is a finding.
 */
export function sessionLevelStatements(statements: readonly string[]): string[] {
  return statements.filter((statement) => {
    const text = statement.toLowerCase();

    if (/\bset_config\s*\(/.test(text)) {
      // The third argument decides. `true` is transaction-local and allowed;
      // anything else, including a parameter whose value cannot be read here,
      // is not.
      return !/\bset_config\s*\([^;]*,\s*true\s*\)/.test(text);
    }

    if (/^set\s+/.test(text)) return !/^set\s+local\s+/.test(text);

    // `reset` and `discard` do not install an identity, but they are
    // session-level housekeeping on a pooled connection and have no business
    // inside a request. Named so a reviewer sees them rather than guesses.
    return /^(reset|discard)\b/.test(text);
  });
}

/** Every setting name the recorded statements touched, however they spelled it. */
export function settingsTouched(statements: readonly string[]): string[] {
  const names = new Set<string>();

  for (const statement of statements) {
    for (const match of statement.matchAll(/set_config\s*\(\s*'([^']+)'/gi)) {
      names.add(match[1]!);
    }
    for (const match of statement.matchAll(/^set\s+(?:local\s+)?([a-z_][\w.]*)/gi)) {
      names.add(match[1]!);
    }
  }

  return [...names];
}
