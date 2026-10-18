/**
 * INV17-B9, criterion 1: the compile-time half of the actor seam.
 *
 * The contract these fixtures are written against lives in the header of
 * `tests/integration/actor-seam.test.ts`. Read that first; this file only
 * proves which of its claims the TYPE SYSTEM carries, as opposed to the ones
 * the runtime carries.
 *
 * WHY THE FIXTURES ARE `.ts.txt` AND NOT `.ts`. The root `tsconfig.json`
 * includes every `.ts` file under the project, so one that is supposed not to
 * compile would break
 * `npm run typecheck` for everyone. The extension keeps them off that glob;
 * the compiler host below serves each one at a virtual `.ts` path inside the
 * project, so `@/...` and `postgres` resolve exactly as they would in a real
 * file. Nothing is written to disk and `tsconfig.json` is not touched, which
 * matters because B9-T may not edit it.
 *
 * WHY A POSITIVE CONTROL EXISTS AND IS CHECKED FIRST. A harness that silently
 * failed to resolve anything would report "does not compile" for every
 * negative fixture and look like a passing control test. That is the exact
 * shape of the INV-17 isolation guard: correct logic, never reached. So
 * `compiles.ts.txt` must produce ZERO diagnostics, and every negative
 * assertion below ignores module-resolution errors (TS2307) when deciding
 * whether a line failed for the RIGHT reason. Before `@/server/actor` exists,
 * that makes these tests red rather than accidentally green.
 *
 * This file is in the unit lane because it needs no database. It is also the
 * slowest thing in that lane by a wide margin — one TypeScript program — so it
 * builds exactly one, shared by every case.
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';
import { beforeAll, describe, expect, it } from 'vitest';

// TypeScript normalises compiler-host paths to forward slashes on Windows too.
const PROJECT_ROOT = fileURLToPath(new URL('../../', import.meta.url)).replace(/\\/g, '/');
const FIXTURE_DIR = new URL('./fixtures/actor-seam/', import.meta.url);

/** Where each fixture pretends to live, so relative and `@/` resolution work. */
const VIRTUAL_DIR = `${PROJECT_ROOT}tests/unit/fixtures/actor-seam/`;

const FIXTURES = ['compiles', 'no-actor', 'escape-hatches', 'forged-actor'] as const;
type FixtureName = (typeof FIXTURES)[number];

/** `Cannot find module`. Reported separately: it means the harness, or the */
/** implementation, is missing — never that a negative case was caught. */
const MODULE_NOT_FOUND = 2307;

function fixtureSource(name: FixtureName): string {
  return readFileSync(new URL(`${name}.ts.txt`, FIXTURE_DIR), 'utf8');
}

function virtualPath(name: FixtureName): string {
  return `${VIRTUAL_DIR}${name}.ts`;
}

interface Reported {
  readonly line: number;
  readonly code: number;
  readonly message: string;
}

const reports = new Map<FixtureName, Reported[]>();

function compilerOptions(): ts.CompilerOptions {
  const configPath = `${PROJECT_ROOT}tsconfig.json`;
  const read = ts.readConfigFile(configPath, ts.sys.readFile);
  if (read.error !== undefined) {
    throw new Error(ts.flattenDiagnosticMessageText(read.error.messageText, '\n'));
  }
  const parsed = ts.parseJsonConfigFileContent(read.config, ts.sys, PROJECT_ROOT);
  return { ...parsed.options, noEmit: true, skipLibCheck: true, types: ['node'] };
}

beforeAll(() => {
  const sources = new Map(FIXTURES.map((name) => [virtualPath(name), fixtureSource(name)]));
  const options = compilerOptions();
  const host = ts.createCompilerHost(options, true);

  const readFile = host.readFile.bind(host);
  const fileExists = host.fileExists.bind(host);
  const getSourceFile = host.getSourceFile.bind(host);

  host.readFile = (path) => sources.get(path) ?? readFile(path);
  host.fileExists = (path) => sources.has(path) || fileExists(path);
  host.getSourceFile = (path, languageVersion, onError, shouldCreate) => {
    const text = sources.get(path);
    if (text === undefined) return getSourceFile(path, languageVersion, onError, shouldCreate);
    return ts.createSourceFile(path, text, languageVersion, true);
  };

  const program = ts.createProgram({
    rootNames: FIXTURES.map(virtualPath),
    options,
    host,
  });

  for (const name of FIXTURES) {
    const file = program.getSourceFile(virtualPath(name));
    if (file === undefined) throw new Error(`fixture ${name} was not part of the program`);

    reports.set(
      name,
      [
        ...program.getSemanticDiagnostics(file),
        ...program.getSyntacticDiagnostics(file),
      ].map((diagnostic) => ({
        line:
          diagnostic.start === undefined
            ? -1
            : file.getLineAndCharacterOfPosition(diagnostic.start).line + 1,
        code: diagnostic.code,
        message: ts.flattenDiagnosticMessageText(diagnostic.messageText, ' '),
      })),
    );
  }
});

function describeReport(name: FixtureName): string {
  return (reports.get(name) ?? [])
    .map((entry) => `  ${name}.ts:${entry.line} TS${entry.code} ${entry.message}`)
    .join('\n');
}

/** The 1-based line holding `marker`. Throws if it is absent or ambiguous. */
function lineOf(name: FixtureName, marker: string): number {
  const lines = fixtureSource(name).split('\n');
  const hits = lines.flatMap((text, index) => (text.includes(marker) ? [index + 1] : []));
  if (hits.length !== 1) {
    throw new Error(`marker ${JSON.stringify(marker)} matched ${hits.length} lines in ${name}`);
  }
  return hits[0]!;
}

/**
 * Asserts the marked line failed, and failed for a reason other than "the
 * module is missing". Without that filter every case below would pass against
 * an empty `src/`.
 */
function expectRejected(name: FixtureName, marker: string): void {
  const line = lineOf(name, marker);
  const onLine = (reports.get(name) ?? []).filter(
    (entry) => entry.line === line && entry.code !== MODULE_NOT_FOUND,
  );

  expect(
    onLine,
    `expected a type error on ${name}.ts:${line} (${marker}).\nDiagnostics:\n${describeReport(name)}`,
  ).not.toEqual([]);
}

describe('the harness itself', () => {
  it('compiles the positive control with no diagnostics at all', () => {
    expect(reports.get('compiles'), describeReport('compiles')).toEqual([]);
  });
});

describe('criterion 1: a query without an actor does not compile', () => {
  it('rejects the two-argument withTransaction', () => {
    expectRejected('no-actor', 'await withTransaction(async (tx) => {');
  });

  it('reports the missing argument or the mistyped callback, not something incidental', () => {
    const line = lineOf('no-actor', 'await withTransaction(async (tx) => {');
    const codes = (reports.get('no-actor') ?? [])
      .filter((entry) => entry.line === line)
      .map((entry) => entry.code);

    // 2554 Expected N arguments; 2345 Argument of type X is not assignable.
    expect(codes.some((code) => code === 2554 || code === 2345), describeReport('no-actor')).toBe(
      true,
    );
  });
});

describe('the scoped handle has no escape hatch but unscoped()', () => {
  it.each([
    ['unsafe', 'await tx.unsafe('],
    ['begin', 'await tx.begin('],
    ['savepoint', 'await tx.savepoint('],
    ['widening to Tx', 'const raw: Tx = tx;'],
    ['end', 'await tx.end();'],
  ])('rejects %s', (_label, marker) => {
    expectRejected('escape-hatches', marker);
  });
});

describe('FR-1704: an Actor cannot be constructed by the caller', () => {
  it.each([
    ['an object literal', 'export const literal'],
    ['a bare user id', 'export const fromUserId'],
    ['a partial session record', 'export const fromPartial'],
    ['a client-chosen user id at the call site', 'await withTransaction({ userId, sessionId'],
  ])('rejects %s', (_label, marker) => {
    expectRejected('forged-actor', marker);
  });
});
