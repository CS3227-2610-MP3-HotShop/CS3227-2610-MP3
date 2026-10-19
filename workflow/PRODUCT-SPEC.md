# Java/JPro PostgreSQL conversion

Revision: 2026-10-19 draft, baseline jpro-docker at f15b076.
This is a bounded change to the existing application, not a renewed Next.js migration.
Bounded conversion scope approved by the owner on 2026-10-19 for technical design. Technical design and implementation readiness remain separate gates.

## Product description and users

Retain the JavaFX/JPro marketplace and its existing buyer/seller workflows while
moving application records to shared PostgreSQL. The owner selected Render,
Supabase PostgreSQL and Supabase Storage in this conversation. This task concerns
the database portion; it does not establish that hosted multi-user operation works.

## Requirements and acceptance

| ID | Requirement | Status | Intended acceptance evidence |
| --- | --- | --- | --- |
| PG-01 | Use PostgreSQL for hosted use, local development and database tests; no SQLite fallback. | Explicitly approved in user reply, 2026-10-19 | Application and integration tests use PostgreSQL; missing configuration fails clearly rather than creating SQLite. |
| PG-02 | Preserve existing marketplace persistence semantics, including binary password material, timestamp precision, ownership references and transaction rollback. | Approved 2026-10-19 | PostgreSQL-backed regression tests exercise relevant existing services, constraints and rollback. |
| PG-03 | Persist records across application restarts without destructive startup reset. | Approved 2026-10-19 | Restart against the same disposable database preserves written records. |
| PG-04 | Keep database credentials server-side and use only isolated disposable local databases for automated tests. | Standing safety constraint | Configuration/error checks do not expose secrets; test setup refuses hosted destinations. |

## UI

No marketplace screen redesign is proposed. Database startup failure feedback
must reflect the configured database rather than claim a SQLite directory issue.
Exact messages remain design work. User/session isolation is an integration
dependency to inspect, not something proved by changing database engines.

## Scope and exclusions

Approved scope: connection configuration, JDBC dependency, schema compatibility,
repository SQL, runtime integration, PostgreSQL-backed tests and setup documentation.
Preserve Java 25 and Gradle 9.1.0. Keep tester, implementer and reviewer ownership.
Supabase Storage implementation, deployment, Git publication and unrelated UI work
are excluded from this database task. Existing files and staged changes are preserved.

## Open decisions and readiness

1. Resolved 2026-10-19: create and upgrade the schema through a separate setup command before deployment (explicit user choice). Normal application startup does not apply schema migrations.
2. Resolved 2026-10-19: start with fresh PostgreSQL data; no SQLite data import is required (explicit user choice).
3. Resolved 2026-10-19: automated database tests use disposable local Docker PostgreSQL containers (explicit user choice). Docker is a test prerequisite; no hosted test target or SQLite fallback.
4. Specify safe concurrent transaction/migration behavior and the interface with JPro startup.

PG-01 through PG-04, PG-T1 and PG-P1 execution/staged follow-ups are approved. Narrow configuration/connection/TLS work is independently accepted; full schema/runtime readiness remains gated. Docker fixtures and real PostgreSQL tests have run; full application/build/deployment acceptance has not. Current handoff and evidence are in workflow/PLAN.md and the feature SDD summary.

## Buyer AI search — AI-P1, 2026-10-20

Status: owner approved the buyer-search direction and explicitly authorised the
contract+validator benchmark in multiagent-search and singleagent-search, sequentially.
The wider SoC/UI feature remains draft; the executable benchmark slice below is fixed
for both arms before either implementation. Deadline:21October2026,14:00Asia/Singapore.
Baseline application:aac5548. Existing PG requirements/acceptance remain unchanged.

### Product, users and workflow

Authenticated buyers describe desired items; SoC LLM proposes existing search filters.
The buyer previews and explicitly applies valid suggestions through ordinary search.
Existing availability, session and seller restrictions remain authoritative. There
are no AI database tools, purchases, seller messages or automatic actions. Existing
manual search remains available. SoC integration/UI are later slices, not this benchmark.

| Requirement | Observable acceptance |
| --- | --- |
| AI-01 Supported suggestions | Return only existing title/category/condition/minimum/maximum/sort criteria; no identifiers, SQL, hidden status or ownership controls. |
| AI-02 Deterministic validation | A response proposal either becomes a valid existing ListingSearch or is rejected completely, with no partial updates. |
| AI-03 Safe failure | Invalid proposals produce a fixed safe error without echoing content; input and existing UI/database state are not mutated. |
| AI-04 Existing search semantics | Optional missing/null fields mean no filter; default sort is NEWEST. Prices are SGD integer cents,0..100000000 inclusive; minimum<=maximum. |
| AI-05 Isolation | Result conditions are immutable/copied, so changing the input cannot alter a previously validated search. |
| AI-06 SoC-only runtime (deferred) | Actual generation must use SoC LLM, with configured quotas/timeouts and manual fallback; no live provider calls in the benchmark. |
| AI-07 Preview (deferred) | Loading/error/preview/apply states preserve current manual filters until buyer confirms; failed generation never applies a partial proposal. |

### Benchmark input and acceptance partitions

The benchmark receives an already-decoded map, not raw JSON. The future provider
adapter is responsible for transport, raw JSON parsing and duplicate-key rejection.
Allowed exact keys:titleText,category,conditions,minPriceCents,maxPriceCents,sort.
Unknown/non-string keys are invalid even when their value is null.

- Missing/null values normalise to null title/category/prices, empty conditions,
  and NEWEST sort. Empty proposal is valid unfiltered criteria, still subject to
  existing search permissions when integrated.
- Title is a String, stripped; blank becomes null. Maximum120 Unicode code points
  after stripping, matching existing listing-title capacity; interior text stays literal.
- Category/condition/sort strings are exact existing enum names, without coercion.
  Conditions must be a List of valid non-null strings; duplicates deduplicate.
- Prices accept only Byte,Short,Integer,Long or BigInteger in range. Floating-point,
  BigDecimal, strings, booleans and overflow are invalid; equal bounds are valid.
- Null proposal, wrong types, invalid enums/elements, excessive title and reversed
  bounds are rejected. SQL/prompt-like title text remains ordinary literal search
  text; model-supplied SQL/action/identity fields cannot become executable controls.
- All invalid cases throw IllegalArgumentException with exactly
  Invalid AI search proposal and no raw-content cause. Caller collections unchanged.

AC-AI-01:valid/default/enum/boundary proposals produce the exact specified criteria.
AC-AI-02:each invalid partition fails safely, including hostile/privileged fields.
AC-AI-03:Unicode boundaries, duplicate conditions and literal title text are preserved.
AC-AI-04:input nonmutation and immutable/copied output are observable.
AC-AI-05:both workflows face identical frozen tests and the same starting code.
These are planned checks, not passing-test claims; benchmark results are recorded later.

### Exclusions and remaining questions

No UI, raw JSON parser, live LLM/network/database, new dependencies, migration,
persistence or deployment in the benchmark. This slice alone is not a finished AI
feature. SoC endpoint/model/quota documentation is inaccessible to this session;
provider integration and the shared client agreement with the seller-feature owner
remain open and block only later integration. No credentials enter prompts, fixtures
or repository. Team manages deployment separately; no build-and-host service used.

### Second benchmark scope — requested 2026-10-20

The owner requested a larger follow-up: SoC connectivity for buyer AI search, with
separate measured runs and manual credit readings between them. This extends the
earlier validator-only benchmark; its completed results and frozen tests remain
unchanged. Proposed slice: buyer request to SoC, strict response decoding into the
existing validator, and safe transport/quota failures. UI integration remains later
work. Provider contract and shared seller-client boundary must be resolved before
finalising acceptance criteria or starting measured implementation. A larger slice
does not guarantee a measurable advantage for either workflow.

Owner decision, 2026-10-20: leave shared transport/parser implementation to the
seller owner. Benchmark the buyer adapter after that dependency is ready. This
supersedes the proposal to include shared transport implementation in our measured
slice; manual credit checkpoints and sequential runs remain required.

Superseding owner clarification, 2026-10-20: independently implement the connection
layer now for the larger benchmark, accepting overlap with llm-feature for later
reconciliation. Waiting for the seller transport is no longer a prerequisite.
Include transport, response parsing and buyer proposal adaptation in the measured
slice. Reconciliation with the seller branch is separate, subsequent work; it must
not change either measured candidate. Credit checkpoints remain in force.

The connection benchmark stops at a validated ListingSearch result, with no UI or
database changes. AI-T2-C1 defines deterministic transport/response safety checks
using the existing validator. Provider-wide quota admission and actual model search
quality remain integration gates; this slice handles 429 without automatic retry.
