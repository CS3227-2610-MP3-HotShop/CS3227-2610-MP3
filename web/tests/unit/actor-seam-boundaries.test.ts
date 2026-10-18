/**
 * INV17-B9: the two criteria that no type and no runtime check can carry.
 *
 *   1. `unscoped()` is greppable by a single fixed token and is REJECTED BY
 *      LINT outside an allow-list.
 *   2. DECISION 8 — the RAW CLIENT (`getDb`, `createDb`) is unreachable from
 *      `src/` outside the same allow-list. Added by B9-R1 finding F1: without
 *      it, criterion 2 is not satisfied at all, because `unscoped()` is only
 *      required to get past a function nobody has to call.
 *   3. FR-1704 — nothing in `src/` mints an `Actor` except the one producer.
 *
 * The contract is in the header of `tests/integration/actor-seam.test.ts`.
 *
 * WHY THIS FILE EXISTS AT ALL, rather than a line in a guide. A lint rule that
 * nothing executes is the same artefact as a guard that never runs, and this
 * project has already paid for one of those. So the rule is driven here,
 * through ESLint's own API, against the real `eslint.config.mjs`, at paths
 * shaped like the files Slice C will actually write.
 *
 * SCOPE NOTE. Both rules belong in `eslint.config.mjs`, which is owned by row
 * B9-I2. This file says what they must do; it does not write them.
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

import { UNSCOPED_TOKEN } from '@/server/db';

const PROJECT_ROOT = fileURLToPath(new URL('../../', import.meta.url));

const eslint = new ESLint({ cwd: PROJECT_ROOT });

/**
 * A repository-shaped call. Deliberately minimal and import-free: the question
 * is whether the TOKEN is rejected at this path, not whether this snippet is a
 * well-formed module.
 */
const CALLS_UNSCOPED = `
export async function findAll(tx) {
  const sql = tx.${UNSCOPED_TOKEN}('because I said so');
  return sql\`select id from listings\`;
}
`;

/** The same function, scoped. Nothing here may be rejected anywhere. */
const CALLS_NOTHING = `
export async function findMine(tx) {
  return tx\`select id from listings where owner = \${tx.actorId}\`;
}
`;

async function unscopedComplaints(code: string, filePath: string): Promise<string[]> {
  const results = await eslint.lintText(code, { filePath: join(PROJECT_ROOT, filePath) });

  return results
    .flatMap((result) => result.messages)
    .filter(
      (message) =>
        message.severity === 2 &&
        `${message.ruleId ?? ''} ${message.message}`.toLowerCase().includes(UNSCOPED_TOKEN),
    )
    .map((message) => message.message);
}

/** A repository reaching for the client directly, named import. */
const IMPORTS_RAW_CLIENT = `
import { getDb } from '@/server/db';
export async function everyListing() {
  return getDb()\`select * from listings\`;
}
`;

/** The obvious way around a named-import rule. */
const IMPORTS_RAW_CLIENT_AS_NAMESPACE = `
import * as db from '@/server/db';
export async function everyListing() {
  return db.getDb()\`select * from listings\`;
}
`;

/** `createDb` is the same door with a different handle on it. */
const IMPORTS_FACTORY = `
import { createDb } from '@/server/db';
export function client(url) {
  return createDb(url);
}
`;

/** What a repository is SUPPOSED to import. Must be clean everywhere. */
const IMPORTS_THE_SEAM = `
import { withTransaction } from '@/server/db';
export async function findMine(actor) {
  return withTransaction(actor, async (tx) => tx\`select id from listings where owner = \${tx.actorId}\`);
}
`;

async function rawClientComplaints(code: string, filePath: string): Promise<string[]> {
  const results = await eslint.lintText(code, { filePath: join(PROJECT_ROOT, filePath) });

  return results
    .flatMap((result) => result.messages)
    .filter((message) => {
      if (message.severity !== 2) return false;
      const text = `${message.ruleId ?? ''} ${message.message}`;
      return /getDb|createDb/.test(text) || /restricted-import/.test(text);
    })
    .map((message) => message.message);
}

/**
 * Paths that must NEVER be on the allow-list. Chosen to cover each layer that
 * issues SQL, so an allow-list that grew a directory glob fails here rather
 * than in a review six months from now.
 */
const FORBIDDEN_PATHS = [
  'src/server/repositories/offers.ts',
  'src/server/repositories/listings.ts',
  'src/server/services/offer-service.ts',
  'src/app/api/listings/route.ts',
];

/**
 * The seam's own implementation. `unscoped` is DEFINED here, so a rule that
 * rejected it everywhere would make the feature unimplementable, which is the
 * failure direction a guard swings to when it is corrected once too often.
 */
const SEAM_PATH = 'src/server/db/index.ts';

describe('unscoped() is rejected by lint outside the allow-list', () => {
  it.each(FORBIDDEN_PATHS)('rejects it in %s', async (path) => {
    expect(await unscopedComplaints(CALLS_UNSCOPED, path)).not.toEqual([]);
  });

  it('allows it in the seam that implements it', async () => {
    expect(await unscopedComplaints(CALLS_UNSCOPED, SEAM_PATH)).toEqual([]);
  });

  it('says what to do instead, not merely that this is forbidden', async () => {
    const [message] = await unscopedComplaints(CALLS_UNSCOPED, FORBIDDEN_PATHS[0]!);

    expect(message ?? '').toMatch(/actor/i);
  });

  it('leaves an ordinary scoped query alone everywhere', async () => {
    for (const path of [...FORBIDDEN_PATHS, SEAM_PATH]) {
      expect(await unscopedComplaints(CALLS_NOTHING, path), path).toEqual([]);
    }
  });

  it('is configured against the one token, in the config file, where it is greppable', () => {
    const config = readFileSync(join(PROJECT_ROOT, 'eslint.config.mjs'), 'utf8');

    expect(config).toContain(UNSCOPED_TOKEN);
  });
});

describe('DECISION 8: the raw client is unreachable from src outside the seam', () => {
  /**
   * B9-R1 wrote this exact import in `src/`, ran it against the live server,
   * and got every tenant's rows with no diagnostic and no lint message. The
   * seam gated `withTransaction`; `getDb` was the door beside it.
   */
  it.each(FORBIDDEN_PATHS)('rejects a named getDb import in %s', async (path) => {
    expect(await rawClientComplaints(IMPORTS_RAW_CLIENT, path)).not.toEqual([]);
  });

  it.each(FORBIDDEN_PATHS)('rejects a namespace import in %s', async (path) => {
    expect(await rawClientComplaints(IMPORTS_RAW_CLIENT_AS_NAMESPACE, path)).not.toEqual([]);
  });

  it.each(FORBIDDEN_PATHS)('rejects createDb in %s', async (path) => {
    expect(await rawClientComplaints(IMPORTS_FACTORY, path)).not.toEqual([]);
  });

  it('allows the seam itself, which has to use the client it owns', async () => {
    expect(await rawClientComplaints(IMPORTS_RAW_CLIENT, SEAM_PATH)).toEqual([]);
  });

  it('leaves the supported import alone everywhere', async () => {
    for (const path of [...FORBIDDEN_PATHS, SEAM_PATH]) {
      expect(await rawClientComplaints(IMPORTS_THE_SEAM, path), path).toEqual([]);
    }
  });

  it('names both exports in the config, where they are greppable', () => {
    const config = readFileSync(join(PROJECT_ROOT, 'eslint.config.mjs'), 'utf8');

    expect(config).toContain('getDb');
    expect(config).toContain('createDb');
  });
});

/* ------------------------------------------------------------------ */
/* FR-1704, read off the source                                        */
/* ------------------------------------------------------------------ */

function sourceFiles(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) out.push(...sourceFiles(path));
    else if (/\.tsx?$/.test(entry)) out.push(path);
  }
  return out;
}

const ACTOR_MODULE = join(PROJECT_ROOT, 'src/server/actor');

describe('FR-1704: one producer of an Actor, and it is not reachable from a user id', () => {
  it('has an actor module at all', () => {
    // Without this, every scan below passes against an empty `src/` and proves
    // nothing. Red until B9-I lands, on purpose.
    expect(() => statSync(join(ACTOR_MODULE, 'index.ts'))).not.toThrow();
  });

  it('casts nothing to Actor outside the actor module', () => {
    const offenders = sourceFiles(join(PROJECT_ROOT, 'src'))
      .filter((path) => !path.startsWith(ACTOR_MODULE))
      .filter((path) => /\bas\s+(?:unknown\s+as\s+)?Actor\b/.test(readFileSync(path, 'utf8')));

    expect(offenders).toEqual([]);
  });

  it('exports exactly one function that returns an Actor', () => {
    const declarations = sourceFiles(join(PROJECT_ROOT, 'src')).flatMap((path) => {
      const text = readFileSync(path, 'utf8');
      return [...text.matchAll(/export\s+function\s+(\w+)\s*\([^)]*\)\s*:\s*Actor\b/g)].map(
        (match) => `${path}:${match[1]}`,
      );
    });

    expect(declarations).toHaveLength(1);
    expect(declarations[0]).toMatch(/actorFromSession$/);
  });
});
