import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/**
 * Two projects, because they have genuinely different needs.
 *
 *   unit        — pure logic. No database, no setup, full file parallelism.
 *                 This is where the refusal taxonomy and the clock live, and
 *                 it must stay in the hundreds of milliseconds. The guard
 *                 tests for the test-database bootstrap live here too: they
 *                 exercise pure functions and must be provable with no server
 *                 running at all.
 *
 *   integration — real PostgreSQL. Refuses to start without DATABASE_URL_TEST,
 *                 provisions a database for the run, and drops it afterwards.
 *
 * Splitting them is what lets the bootstrap fail HARD on a missing or unsafe
 * DATABASE_URL_TEST without taking the unit lane down with it.
 */
export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    coverage: {
      // Adopted 2026-10-18. A floor AND a ceiling: below 80 the slice is not done,
      // above it nobody may ask for more tests, reviewers included. INV-17 wrote
      // 9,293 lines of tests for 1,772 lines of production code to reach 89%.
      // scripts/ and tests/helpers/ are excluded: chasing an operator script to a
      // branch target is how that ratio happened.
      provider: 'v8',
      include: ['src/**/*.ts'],
      exclude: ['src/app/**', 'scripts/**', 'tests/**'],
      thresholds: { branches: 80 },
    },
    // The domain modules are pure logic and touch no DOM. React and component
    // tests that come later opt back in per file with the docblock:
    //   // @vitest-environment jsdom
    environment: "node",
    globals: true,
    passWithNoTests: true,
    env: {
      // Baseline host zone for the whole suite. Deliberately NEITHER
      // Asia/Singapore (the application zone) NOR UTC, so a formatter that
      // silently falls back to the host zone renders visibly wrong output on
      // every machine, including CI and this container.
      TZ: "America/New_York",
    },
    projects: [
      {
        extends: true,
        test: {
          name: "unit",
          include: ["tests/unit/**/*.{test,spec}.{ts,tsx}", "tests/config/**/*.{test,spec}.ts"],
          // INV17-B9 put two tests in this lane that are not "pure logic in
          // hundreds of milliseconds", and they are in it on purpose: both
          // prove a criterion no database can prove, so neither belongs in the
          // integration lane.
          //
          //   actor-seam-compile      builds one TypeScript program over the
          //                           project in `beforeAll`.
          //   actor-seam-boundaries   drives ESLint's own API against the real
          //                           `eslint.config.mjs`; the first lint call
          //                           pays for loading the whole config.
          //
          // Measured in this container: ~1.6s for the first ESLint call and
          // ~2s for the program build, against defaults of 5s per test and 10s
          // per hook. That is a margin, not a budget, and a lane that goes red
          // on a loaded CI machine teaches people to re-run rather than read.
          // Raised to a number a genuine hang still exceeds, rather than
          // removed: an unbounded timeout would turn the deadlock these seams
          // can produce into a job that never finishes.
          testTimeout: 15_000,
          hookTimeout: 30_000,
        },
      },
      {
        extends: true,
        test: {
          name: "integration",
          include: ["tests/integration/**/*.{test,spec}.ts"],
          globalSetup: ["tests/setup/integration-global-setup.ts"],
          setupFiles: ["tests/setup/integration-setup.ts"],
          // One database, shared by every integration file. Truncating between
          // tests is only safe if no other file is mid-test, so the integration
          // lane runs one file at a time. Tests inside a file are sequential by
          // default, and the two that need real concurrency create it
          // explicitly with two clients rather than relying on the runner.
          fileParallelism: false,
          // A blocked INSERT waiting on a unique index is the point of this
          // lane, so the budget is wider than the unit default.
          testTimeout: 20_000,
          hookTimeout: 30_000,
        },
      },
    ],
  },
});
