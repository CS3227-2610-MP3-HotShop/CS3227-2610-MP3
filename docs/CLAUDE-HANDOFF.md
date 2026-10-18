# Migration handoff

Rewritten 2026-10-17 (SGT) to replace a document that had become layered updates on a
superseded base. This is the current state. Historical narrative lives in
`docs/InteractionRecord.md`, which is append-only.

## Start here

Work in `E:/CS3227-2610-MP3`. The older MP2 checkout is a separate working copy with
unrelated changes; leave it alone.

Read in this order, and no further until you need to:

1. `AGENTS.md`
2. This file
3. `docs/InteractionRecord.md`, the "Current handoff" block plus the last few entries
4. `workflow/PRODUCT-SPEC.md` and `workflow/TECHNICAL-SPEC.md`
5. `workflow/FEATURE-INVENTORY.md`, and `workflow/FEATURE-EVIDENCE.md` only when you need
   a specific group's source trace
6. `workflow/MIGRATION-WORKFLOW.md` and `docs/agents/AgentSpecifications.md` before
   delegating work

Do not repeat the feature audit. It is finished, approved, and its evidence is recorded.

## How the user works

One decision at a time, with the reasoning shown. They push back on recommendations and
are usually right to; when they question a decision, re-examine it properly rather than
defending it. The no-ORM decision came from exactly that, and the first answer was wrong.

Silence is not approval, and neither is moving on to the next topic. Report what actually
happened, including what failed and what you did not verify. Several of the most useful
findings here came from stating a limit plainly instead of rounding a result up.

## Repository state

- Repository: `https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3`
- Checked out: `codex/nextjs-migration`, at `7c16bcf`
- `codex/migration-workflow` is at the same commit and is pushed to origin
- Everything done on 2026-10-17 after that commit is **uncommitted** on the feature branch
- `master` is still at `227ba99`. PR #1 from `codex/agent-specifications` is open, and
  `codex/migration-workflow` contains its two commits, `bf47d26` and `93dbfc6`
- Windows Git needs command-scoped `safe.directory=E:/CS3227-2610-MP3` here. Do not change
  global Git configuration to work around it

The migration source baseline is **frozen at `1d812ef`**. Specifications cite behaviour at
that commit. A later JavaFX source change does not change scope by itself; it needs a
proposed re-baseline with a diff and explicit approval.

## What exists

**Specifications.** `workflow/PRODUCT-SPEC.md` holds the whole migration's product intent.
INV-17 is approved; INV-01 to INV-16 are inventory-only. `workflow/TECHNICAL-SPEC.md` holds
INV-17's design as a draft.

**Code,** in `web/`, uncommitted:

- Next.js 16.4.2, React 19.3.1, TypeScript 5.9.3, Vitest 5.0.5, Tailwind 4.3.3,
  postgres.js 3.4.9, zod 4.6.7, Supabase JS 2.118.0, Supabase CLI 2.121.0. No ORM.
- `src/domain/errors/index.ts`: the refusal taxonomy. Six reason codes, catalogue-only
  authoring behind a private Symbol token, `isRefusal` for refusal-shaped data and
  `isAuthoredRefusal` for values this module made, `asPayload()` for transport, money and
  count formatting that reject malformed input.
- `src/domain/time/index.ts`: injectable clock, fixed-zone formatting for Asia/Singapore
  with an SGT label, and three scheduling predicates including the 60-day horizon counted
  as calendar days in the application zone.
- `tests/unit/`: 168 cases. `npm run test`, `typecheck`, `lint` and `build` all pass.
- `vitest.config.mts` pins `TZ=America/New_York` so the suite discriminates on any
  machine. Do not "fix" that to Singapore; it is deliberate.

The domain layer imports nothing at all, and a lint rule enforces that.

**Nobody has run this on the user's machine.** It was built and verified in an assistant
cloud workspace. `cd web && npm install && npm test` is the user's own check.

## INV-17 is roughly a quarter done

Only the parts needing no database exist. Five of its twenty requirements:

| Requirement | State |
| --- | --- |
| FR-1712, FR-1713 refusal taxonomy and six reason codes | Done |
| FR-1718 fixed-zone display with a label | Done |
| FR-1719 (as narrowed) future start, 60-day horizon, has-ended | Done |
| FR-1720 scheduling refusals state times in that zone | Mechanism done; the refusals themselves belong to INV-11 |
| FR-1701 to FR-1703 session lifetime, concurrent sessions, per-session logout | Not started, needs a database |
| FR-1704 request identity resolved from the cookie | Not started |
| FR-1705, FR-1705a persistence, signed-out requests refused | Not started |
| FR-1706, FR-1706a seeding, idempotent, provisioning-time | Not started |
| FR-1709 invariants under concurrency | Not started. INV-17 owns the pattern and the transaction helper; the partial unique indexes land on each feature's tables |
| FR-1710, FR-1711 atomic multi-record changes, conflict refusals | Not started |
| FR-1714 to FR-1717 polling for an open conversation and the unread total | Not started, and additionally blocked on INV-13 and INV-14, since no conversations exist to poll |

Also unbuilt from the technical spec: the migration mechanism, generated row types,
connection management for the pooled connection, and the authenticated image route.

A sensible build order once any PostgreSQL is available:

1. Connection and the transaction helper, which is what FR-1710 rests on and what every
   service will take as a handle
2. Migration runner and the first migration, the sessions table
3. Session create, destroy and resolve, plus the route handlers and signed-out refusal
4. Seed command
5. Image route, which needs storage and can be stubbed until the Supabase project exists
6. Polling routes last, since they have nothing to return until conversations exist

## Next step

Either of these, user's choice:

- The account rules for INV-01: username normalisation and uniqueness shape, the password
  policy, display name and pickup location limits. Pure domain work, no database needed,
  and it is the largest remaining chunk that is not blocked.
- INV-17's persistence half, per the order above. This needs a PostgreSQL but not
  *the* PostgreSQL: the user's friend is provisioning a Supabase project, and that gates
  deployment and image storage only. A local Postgres, or one installed in the assistant's
  cloud workspace, unblocks the connection helper, migrations, sessions and their
  integration tests immediately.

## Decisions in force

Approved, with superseded ones marked so nobody reinstates them:

- 17 feature groups, coverage approved 2026-10-17. INV-17 added for platform and data
  foundations; it has no JavaFX source trace because it is a target-decision group.
- Concurrent multi-user web access. **Supersedes** the desktop single-logged-in-user
  constraint. The single service worker is gone, so database constraints are what protect
  the marketplace invariants.
- No reset feature, and hosted PostgreSQL from the start. **Supersedes** the earlier
  resettable-demo decision with PostgreSQL deferred. On a serverless host those two could
  not both hold: interim in-memory state disappears between deployments and cold starts,
  which users experience as an uncontrolled reset.
- PostgreSQL in every environment including tests, each with its own database. Tests never
  target the demo database.
- Supabase for database and object storage. Not Supabase Auth, not Row Level Security:
  authorization stays in application code as in the source services.
- No ORM. **Supersedes** an earlier Drizzle decision. Schema is numbered `.sql` migrations
  via the Supabase CLI, row types come from `supabase gen types typescript`, and
  `postgres.js` gives real transactions. `supabase-js` is for storage only, because
  PostgREST commits each HTTP request before replying and four operations need several
  writes to form one unit: accepting an offer, completing a sale, cancelling a sale, and
  deleting an enquiry-only listing.
- Seeding creates available listings only. Reserved, sold and archived are produced by the
  application's own operations, and tests must reach them the same way rather than writing
  a status value into storage.
- Sessions last until explicit logout, survive closing the browser, and several browsers
  can hold sessions for one account. A sessions table with an opaque token in an httpOnly
  cookie, because a stateless cookie cannot revoke one session.
- No anonymous browsing. Login is required everywhere, as in the source.
- One fixed application timezone, `Asia/Singapore`, displayed with a label.
- Background polling for an open conversation and the unread total. Not push: Vercel
  serverless cannot host websockets, and a realtime channel would need participant checks
  written separately from the HTTP routes, which is where they fail quietly.
- Images served through an authenticated application route, not public or signed storage
  URLs.
- The desktop 960 x 640 minimum and fixed JavaFX layout units are **not** target criteria.
- FR-1719 narrowed: INV-17 owns the evaluation zone and the time primitives. The 15-minute
  start grid, duration bounds and overlap checks belong to INV-11 and INV-12.

## Open decisions

- Human owners for frontend and backend. Unassigned, and the workflow requires them before
  implementation assignment. The user is currently the approver for both areas.
- Polling intervals, and whether the unread total polls at a different rate.
- Seed content counts: how many accounts, how many listings, photos or not.
- PostgreSQL major version, pending from the provisioning task.
- Node version and whether to pin the package manager.

## How work is run

The assistant coordinates and delegates to subagents taking the tester, implementer and
reviewer roles, because the workflow separates them and a single agent writing both tests
and code produces self-review. This is not ceremony. On the one task done so far, the
test-readiness review and the implementation review each found defects that would
otherwise have shipped, including a timezone guard that could not catch the implementation
it existed to catch, and `NaN` reaching a user-facing refusal message.

Order that worked: implementer scaffolds, tester writes failing tests, reviewer checks test
readiness, implementer stubs the API to get genuine per-test red, implementer implements to
green, reviewer checks implementation and coverage.

Two things to keep doing. Make the implementer stub the full API before implementing, since
a failure at import collection is a setup failure rather than red by the project's own
policy. And when a reviewer finds something, take it back to the role that owns the file
rather than fixing it across role boundaries.

## Environment traps

These cost real time to discover. A fresh session will hit them again.

- **There is no shell on the user's machine.** `device_bash` fails with "Workspace
  unavailable". That means no Git, no npm, nothing. Read `.git/HEAD`, `.git/refs/**` and
  `.git/logs/HEAD` as files to establish repository state, and say plainly that you did
  not run `git status`.
- **The cloud session has no GitHub credential.** `gh auth status` reports an invalid
  token. Branching, committing, pushing and PR creation are the user's to run. Give exact
  commands and do not record any of it as done until they confirm.
- **`device_commit_files` sometimes reports success without writing.** This happened
  repeatedly, usually around a connection drop. Always verify afterwards with
  `device_list_dir` and compare the file size, then retry the commit if it did not change.
  A silently lost decision record is the worst version of this.
- **Build and test in the cloud workspace, then copy files over.** The container has Node
  22 and npm registry access; the user's machine is unreachable for execution.
  `node_modules` never crosses.
- **The cloud workspace blocks `fonts.googleapis.com`.** The scaffold's Geist fonts were
  replaced with a system font stack for that reason alone. It is an environment limit, not
  a design decision, and the user may want them back.
- TypeScript is pinned to 5.9.3 rather than the registry's 7.0.2, because typescript-eslint
  does not support TS 7 and that reaches the project through `eslint-config-next`.
- `.codex/agents/*.toml` are Codex-specific. Do not assume they load. Read the portable
  Markdown role briefs and map capabilities explicitly; if enforcement differs, say so
  rather than claiming equivalence.

## Verification, and its limits

Performed and recorded:

- 168 unit tests passing, with typecheck, lint and build clean, in the cloud workspace.
- The 60-day horizon differential-tested against an independently written oracle over
  680,000 pairs spanning year 1 to 9999 CE, zero mismatches. Money formatting against
  BigInt truth over roughly 640,000 values, zero mismatches. The refusal catalogue fuzzed
  across 45 hostile invocations, zero leaks.
- The feature inventory traced to source paths and named test assertions at `1d812ef`.
  Those assertions were **read, not executed**.

Not performed: nothing has run on the user's machine, no database exists, no integration or
Playwright test has been written, nothing is deployed, no Java build or test was run, and
no remote issue state was verified from this session.

## Carried follow-ups against INV-17

- An object carrying `Symbol.toStringTag: 'Date'` passes the date brand check and then
  reaches the engine's own error message. It needs a hand-crafted object, but that guard
  exists to keep engine wording out of refusals.
- Whether Next.js preserves a **thrown** refusal across the server and client boundary,
  given it substitutes an opaque digest in production. The returned path through
  `asPayload()` is solid; standardise on returning before features start throwing refusals
  out of server actions.
- Money and message-length range validators do not exist. The formatters reject malformed
  input but not out-of-range input. These belong with the listing, offer and message
  features.
- Four of the six reason codes are declared but unreached until the features that raise
  them land.
- `AGENTS.md` refers to `workflow/PLAN.md`, which has never been created.

A habit worth keeping, recorded because it recurred three times in one task: a claim
written in a code comment reached one step further than the mechanism beneath it, every
time. The mechanism was sound and the sentence was not. Check the sentence as carefully as
the line.

## Scope hygiene

Personal non-project material was separated from this repository and belongs outside the
project documents. Published history is accepted as is; do not rewrite it or restore
removed material. MP2 is a separate older checkout; leave it untouched.
