/**
 * THE INVARIANT BEHIND THE BOM BUG, pinned as a property rather than as two
 * special cases.
 *
 * The guard refuses a test run when `DATABASE_URL` points where
 * `DATABASE_URL_TEST` points. For that to mean anything, the guard has to read
 * the same value out of `.env.local` that the APPLICATION reads out of it. The
 * application uses Next's loader; the guard uses `util.parseEnv`. Those are two
 * different parsers, and every place they disagree is a place the guard is
 * blind while the application is not.
 *
 * Two disagreements were found by hand:
 *
 *   BOM        `util.parseEnv` keeps a leading byte-order mark, so the first
 *              key becomes "﻿DATABASE_URL" and the lookup misses. Next
 *              strips it. PowerShell writes one by default.
 *   expansion  Next runs dotenv-expand, so `${DB_HOST}`, `$DB_HOST`,
 *              `${X:-default}` and nested references all resolve. `parseEnv`
 *              leaves them literal.
 *
 * Fixing those two by hand would leave the THIRD one, whatever it turns out to
 * be, just as blind. So this file pins the general property instead:
 *
 *   For any env file, either the guard reads exactly what Next reads, or the
 *   guard marks the value unusable and refuses the run. What it must never do
 *   is read a DIFFERENT usable value, or read nothing while Next reads
 *   something.
 *
 * A future divergence therefore fails here, by name, with both values printed,
 * instead of being found in a security review.
 *
 * Note on `.env` rather than `.env.local`: Next deliberately skips `.env.local`
 * when NODE_ENV is `test`, which it is in here. `.env` is loaded in every mode,
 * and the guard parses both files through the same function, so the corpus uses
 * `.env` to keep Next's own behaviour out of the comparison.
 */

import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import nextEnv from '@next/env';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { hasUnexpandedReference, readEnvFile } from '../helpers/test-database';

const { loadEnvConfig } = nextEnv;

const KEY = 'DATABASE_URL';
const URL_ = 'postgresql://app:pw@prod.example.com:5432/hotshop';
const BOM = '﻿';

/**
 * Every shape a hand-edited file plausibly takes, with the Windows ones first
 * because that is where this project's operator works.
 */
const CORPUS: Readonly<Record<string, string>> = {
  plain: `${KEY}=${URL_}\n`,
  bom: `${BOM}${KEY}=${URL_}\n`,
  'windows line endings': `${KEY}=${URL_}\r\n`,
  'bom and crlf': `${BOM}${KEY}=${URL_}\r\n`,
  'export prefix': `export ${KEY}=${URL_}\n`,
  'inline comment': `${KEY}=${URL_} # production\n`,
  'double quoted': `${KEY}="${URL_}"\n`,
  'single quoted': `${KEY}='${URL_}'\n`,
  'trailing spaces': `${KEY}=${URL_}   \n`,
  'no trailing newline': `${KEY}=${URL_}`,
  'blank lines and comments': `\n# pasted from the dashboard\n\n${KEY}=${URL_}\n`,
  'duplicate keys': `${KEY}=postgresql://app:pw@other.example.com:5432/x\n${KEY}=${URL_}\n`,
  'indented key': `  ${KEY}=${URL_}\n`,
  'dollar in password': `${KEY}=postgresql://app:pa$$@prod.example.com:5432/hotshop\n`,
  'braced reference': `DB_HOST=prod.example.com\n${KEY}=postgresql://app:pw@\${DB_HOST}:5432/hotshop\n`,
  'bare reference': `DB_HOST=prod.example.com\n${KEY}=postgresql://app:pw@$DB_HOST:5432/hotshop\n`,
  'reference with default': `${KEY}=postgresql://app:pw@\${DB_HOST:-fallback.example.com}:5432/hotshop\n`,
  'nested reference': `A=prod.example.com\nB=\${A}\n${KEY}=postgresql://app:pw@\${B}:5432/hotshop\n`,
  'password from reference': `PW=s3cret\n${KEY}=postgresql://app:\${PW}@prod.example.com:5432/hotshop\n`,
};

let root: string;

beforeEach(() => {
  root = mkdtempSync(join(tmpdir(), 'hotshop-envagree-'));
});

afterEach(() => {
  rmSync(root, { recursive: true, force: true });
});

/**
 * What the application would see, via Next's own loader.
 *
 * `loadEnvConfig` assigns into `process.env`, which this suite must not carry
 * between tests, so the environment is snapshotted and put back. Safe here:
 * the unit lane runs each file in its own worker and tests in sequence.
 */
function asNextSees(dir: string): string | undefined {
  const snapshot = { ...process.env };
  delete process.env[KEY];
  try {
    const { combinedEnv } = loadEnvConfig(dir, false, { info() {}, error() {} }, true);
    return combinedEnv[KEY];
  } finally {
    for (const key of Object.keys(process.env)) {
      if (!(key in snapshot)) delete process.env[key];
    }
    Object.assign(process.env, snapshot);
  }
}

describe.each(Object.entries(CORPUS))('env file shape: %s', (_name, contents) => {
  it('is either read identically to Next, or refused as unusable', () => {
    writeFileSync(join(root, '.env'), contents, 'utf8');

    const theirs = asNextSees(root);
    const mine = readEnvFile(join(root, '.env'))[KEY];

    if (theirs === undefined) {
      // The application sees nothing, so there is nothing to be blind to.
      return;
    }

    if (mine === theirs) return;

    // They differ. The only acceptable outcome is that the guard knows it
    // cannot use this value and will refuse the run.
    expect(
      mine !== undefined && hasUnexpandedReference(mine),
      `the guard read ${JSON.stringify(mine)} where the application reads ` +
        `${JSON.stringify(theirs)}, and did not mark it unusable. That is a silent ` +
        'disagreement: the run would be permitted against the database the ' +
        'application is pointed at.',
    ).toBe(true);
  });
});

describe('the corpus is actually exercising the parsers', () => {
  it('includes at least one case where the two genuinely disagree', () => {
    // Guards against the whole file passing because every case is trivial, or
    // because `asNextSees` has quietly stopped returning anything.
    const disagreements = Object.entries(CORPUS).filter(([, contents]) => {
      writeFileSync(join(root, '.env'), contents, 'utf8');
      const theirs = asNextSees(root);
      return theirs !== undefined && readEnvFile(join(root, '.env'))[KEY] !== theirs;
    });

    expect(disagreements.length).toBeGreaterThan(0);
  });

  it('reads the plain case the same way as Next, so agreement is possible at all', () => {
    writeFileSync(join(root, '.env'), CORPUS.plain!, 'utf8');

    expect(readEnvFile(join(root, '.env'))[KEY]).toBe(URL_);
    expect(asNextSees(root)).toBe(URL_);
  });
});
