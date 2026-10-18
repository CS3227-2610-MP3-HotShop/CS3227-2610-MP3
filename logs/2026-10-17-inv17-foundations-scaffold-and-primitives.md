# Agent Interaction Log

## User Prompt

"ok whats next can we start on the first feature?", then "Approve spec, start technical
spec", "In TypeScript, direct connection", "no ORM" (via "we are using typescript and
supabase why the need for an orm"), "In a web/ subdirectory", "Subagents as separate
roles", "Go", "Run the review", and "Approve as a review cycle".

## Steps Taken

1. Recorded the INV-17 product specification as approved and drafted its technical
   specification, then corrected it when the user challenged the ORM decision.
2. Scaffolded the Next.js project through an implementer subagent, verified, and placed
   it under `web/` so it does not collide with the Java `src/main/java` tree.
3. Tester subagent wrote 102 failing unit tests for the refusal taxonomy, the injectable
   clock and fixed-zone formatting.
4. Specification reviewer performed the test readiness review: NOT READY, five blocking
   findings. Tester corrected them.
5. Implementer established a behavioural red baseline, then implemented to 102 green.
6. Specification reviewer performed the implementation and coverage review: three
   blocking defects, four improvements.
7. Human approved a correction cycle. Tester extended the suite to 142 cases pinning
   every finding, 33 failing. Implementer made all 142 pass.
8. Specification reviewer re-reviewed: nothing blocking, task can close, five follow-ups.

## Reasoning Summary

The role separation was not ceremonial. Each review gate found defects the previous role
had missed, and two of them would have shipped silently:

- The test-readiness review proved, by running code, that the suite's host-timezone guard
  could not catch the implementation it existed to catch, because `Intl.DateTimeFormat`
  caches its timezone at construction. A module-level formatter without an explicit
  `timeZone` would have passed every test on any machine set to Singapore time.
- The implementation review found `formatMoney(4000.004)` producing
  `S$40.0.0039999999999054126` and `duplicatePendingOffer(NaN)` producing
  `S$NaN.NaN` inside a user-facing message, both direct FR-1712 breaches in territory the
  tests never visited.

The red baseline was handled deliberately rather than loosely. The project's policy
states that a setup failure is unverified rather than red, and the first run died at
import collection with zero tests executing. The implementer was therefore instructed to
stub the full API surface first so the cases ran and failed individually. The honest
result was 101 failed and 1 passed, not 102 failed, because one assertion cannot
distinguish a throwing stub from a real implementation; that was reported rather than
rounded up.

The catalogue-only constructor was chosen over a lint rule on the reasoning that making
a breach unrepresentable is stronger evidence than forbidding it by convention, and the
review had already demonstrated that a raw `Date` interpolated into a refusal message
typechecked, linted clean and passed all 102 tests.

## Changes Made

- `web/`: Next.js 16.4.2 project. React 19.3.1, TypeScript 5.9.3, Vitest 5.0.5,
  Tailwind 4.3.3, postgres.js 3.4.9, zod 4.6.7, Supabase JS 2.118.0, Supabase CLI
  2.121.0. No ORM.
- `web/src/domain/errors/index.ts`: refusal taxonomy, six reason codes, catalogue-only
  authoring, brand-based `isRefusal`, `asPayload`, money and count formatting.
- `web/src/domain/time/index.ts`: injectable clock, fixed-zone formatting for
  Asia/Singapore, and the three scheduling predicates.
- `web/tests/unit/`: 142 cases across three suites.
- `web/vitest.config.mts`: node environment, and a baseline `TZ` of `America/New_York`
  so the suite discriminates on any machine.
- `workflow/PRODUCT-SPEC.md`: FR-1719 narrowed.
- `workflow/TECHNICAL-SPEC.md`: TD-02 replaced, TD-02a and TD-02b added when the ORM
  decision was reversed.

Deviations recorded at the time: TypeScript is 5.9.3 rather than the registry's 7.0.2,
because typescript-eslint does not support TS 7 and that reaches the project through
eslint-config-next. The Geist fonts were replaced with a system font stack because the
assistant's sandbox blocks fonts.googleapis.com, which is an environment limitation and
not a design decision; the user may restore them.

## Verification

Executed in the assistant's cloud workspace, not on the user's machine:

- `npm run test`: 142 passed, 142 total.
- `npm run typecheck`: exit 0.
- `npm run lint`: exit 0.
- `npm run build`: exit 0.

Review evidence, all produced by running code rather than asserted:

- Money formatting differential against BigInt truth over roughly 640,000 values: zero
  mismatches.
- Catalogue fuzz across 15 hostile argument types in every parameter position: 45
  invocations, 45 TypeErrors, zero messages rendered, zero leaks.
- Scheduling horizon differential against an independently written oracle over 680,000
  pairs spanning year 1 to 9999 CE plus the full legal Date range: zero mismatches. A
  200-day walk across the year 99 to 100 boundary that broke the previous arithmetic
  produced exactly one transition, at day 61.
- Property descriptors read directly: `Object.keys` is exactly message, brand, code;
  spread, `Object.assign`, JSON round trip and `structuredClone(asPayload())` are all
  deep-equal to `asPayload()`.
- Domain purity: zero imports of anything in all of `src/domain`, no `process.env`, no
  `Date.now()`, one sanctioned `new Date()` inside `systemClock`.

Not performed: no test was run on the user's machine, no database exists, no integration
or Playwright test was written, and nothing was deployed. No Git command was executed by
this session.

## Final Output and Conclusion

INV-17's purely logical foundations are implemented and green: the refusal taxonomy, the
injectable clock, and fixed-zone formatting with the three scheduling predicates. These
are what every later feature imports.

The re-review found nothing blocking and five follow-ups to carry against INV-17:

1. The comment claiming a refusal carrying a stack or SQL is "not expressible from
   outside" is false as written. Two routes remain: `Object.getPrototypeOf(r).constructor`
   reaches the internal class, and a plain `RefusalPayload` literal passes `isRefusal`
   because the brand and codes are exported. Both require deliberate effort, so
   FR-1712's accidental-leakage concern is met, but an overstated invariant in a
   spec-driven codebase is itself a defect.
2. `advanceBy` validates its argument but not the resulting instant, so an in-range
   argument can produce an out-of-range clock. This contradicts the module's own comment
   and was introduced by the correction cycle.
3. `requireValidDate` uses `instanceof Date`, which rejects a valid cross-realm Date, and
   a Proxy-wrapped Date leaks a raw engine message rather than the deliberate guard.
4. `isRefusal` accepts a branded array and a prototype-inherited brand, and its
   justification comment is incorrect.
5. Cosmetic: duplicated guard helpers across the two domain modules, own-key ordering,
   and an inconsistently used return value.

Also carried forward unresolved: whether Next.js preserves a thrown refusal across the
server and client boundary, given it replaces thrown server errors with an opaque digest
in production. The returned path through `asPayload()` is solid and should be the
standard before the pattern spreads.

Known coverage gaps, reported rather than papered over: four of the six reason codes are
declared but unreached until the features that raise them land, and money and
message-length range validators do not exist, so `formatMoney` and `formatCount` reject
malformed input but not out-of-range input. Those belong with the listing, offer and
message features.

The user has not run any of this on their own machine, and nothing is committed.
