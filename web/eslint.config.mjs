import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    // src/domain is pure TypeScript: values, rules and state machines only.
    // It must stay free of framework, React and database dependencies.
    files: ["src/domain/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: [
                "@/server/*",
                "next",
                "next/*",
                "react",
                "react/*",
                "react-dom",
                "react-dom/*",
                "postgres",
                "postgres/*",
                "@supabase/*",
                "server-only",
              ],
              message:
                "src/domain must stay pure: no next, react, postgres, @supabase or @/server imports.",
            },
          ],
        },
      ],
    },
  },
  /* ======================================================================
   * INV17-B9 / TD-11: the actor seam, the half no type and no runtime check
   * can carry. The contract is the header of
   * `tests/integration/actor-seam.test.ts`; both rules below are driven
   * through ESLint's own API by `tests/unit/actor-seam-boundaries.test.ts`,
   * because a lint rule that nothing executes is the same artefact as a guard
   * that never runs, and this project has already paid for one of those.
   *
   * THE ALLOW-LIST IS `src/server/db/**`, the seam itself, and nothing else
   * under `src/`. `scripts/**` is outside `src/` and outside both rules:
   * `scripts/db-check.ts` connects on purpose and has no actor to carry.
   * ====================================================================== */
  {
    // DECISION 4. `unscoped()` is the one door out of the actor scope, in one
    // fixed spelling, so the set of deliberate exceptions is a `grep` rather
    // than a reading of every repository. The type system cannot express
    // "this method may be called from these files", and a convention nobody
    // enforces is what TD-11 was written to replace.
    //
    // The seam is exempt because it DEFINES `unscoped`. A rule that rejected
    // it everywhere would make the feature unimplementable, which is the
    // direction a guard swings when it is corrected once too often.
    files: ["src/**/*.{ts,tsx}"],
    ignores: ["src/server/db/**"],
    rules: {
      "no-restricted-syntax": [
        "error",
        {
          selector: "CallExpression[callee.property.name='unscoped']",
          message:
            "unscoped() leaves the actor scope, so it is allowed only inside src/server/db. " +
            "Scope this query to the actor with ${tx.actorId} instead. If the query genuinely " +
            "is not actor-scoped (TD-11 names public listing search), it belongs behind the " +
            "seam with a reason, not in a repository.",
        },
      ],
    },
  },
  {
    /*
     * DECISION 8, added at the readiness gate by B9-R1 finding F1, and it is
     * the finding that mattered. Everything else gates the door marked
     * `withTransaction`. Nobody has to walk through it:
     *
     *     import { getDb } from '@/server/db';
     *     export async function everyListing() {
     *       return getDb()`select * from listings`;
     *     }
     *
     * Every tenant's rows, zero `tsc` diagnostics, zero ESLint messages, suite
     * unchanged. Without this rule, "unscoped() is REQUIRED for a deliberately
     * unscoped query" is required only to get past a function nobody has to
     * call.
     *
     * BOTH IMPORT SPELLINGS, because a namespace import is the obvious way
     * around a named-import rule. `importNames` makes core `no-restricted-
     * imports` reject `import * as db` as well as `import { getDb }`.
     *
     * The `Db` and `Tx` TYPES stay reachable: a repository signature naming
     * `Tx` is how `unscoped()` is consumed. The rule is about reaching a
     * client, not about naming its type.
     *
     * `src/domain/**` is excluded here only because it carries its own
     * `no-restricted-imports` entry above, which a second one would replace
     * rather than add to; that entry already forbids `@/server/*` outright,
     * which is strictly stronger than this rule.
     */
    files: ["src/**/*.{ts,tsx}"],
    ignores: ["src/server/db/**", "src/domain/**"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [
            {
              name: "@/server/db",
              importNames: ["getDb", "createDb"],
              message:
                "getDb and createDb hand out a raw, actor-less client, and TD-08 puts " +
                "authorization entirely in application code with no RLS behind it. Use " +
                "withTransaction(actor, ...) so every query carries an actor. Only " +
                "src/server/db may reach the client directly.",
            },
          ],
        },
      ],
    },
  },
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
]);

export default eslintConfig;
