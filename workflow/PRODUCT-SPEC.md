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

### Third benchmark — explicit buyer AI search, AI-P3 approved, 2026-10-20

Owner selected the separate AI-search button as the next benchmark. Existing search
behavior is verified in SearchPage/SearchState. Owner approved implementation of
the benchmark after confirming exact "AI Search" text, supplying pre-setup readings
5-hour37%/weekly27%. Shared setup is authorised; each measured arm still requires
its own fresh ready/credit checkpoint after the common setup is frozen.

| ID | User-visible acceptance |
| --- | --- |
| AI-UI-01 | One existing query field; typing, Enter, Search, filters, sort, Clear and Refresh never invoke AI. A separate button with exact visible text "AI Search" is the only generation trigger. No additional search bar. |
| AI-UI-02 | Blank or over1,000 Unicode-code-point text is rejected before provider invocation. A valid click issues exactly one request; repeated clicks while pending issue none. Normal search stays usable. |
| AI-UI-03 | Show loading, then a read-only preview of all proposed filters with Apply filters and Dismiss. Loading/success/dismiss do not alter submitted criteria/results or manual filters. |
| AI-UI-04 | Apply replaces the complete manual filter draft and submits exactly that validated proposal once through ordinary listing search. Missing filters clear old values. Display SGD values correctly. |
| AI-UI-05 | Query/filter edits, normal search, leaving the page or changing session invalidate the pending result/preview. Late completion cannot change the current page, session, filters or results. |
| AI-UI-06 | Failure, exceptional/null response or missing configuration shows fixed safe feedback without model/exception/credential contents; current manual search remains intact and retry is explicit. |

Example: buyer enters "used bicycle under S$200", clicks AI Search, reviews Sports /
maximum S$200, then Apply filters. This is illustrative, not a recorded model result.
Preview is read-only; existing manual filters remain the editing surface. AI search
is disabled when not configured, while ordinary search remains functional.

Include real app wiring to the already-verified SoC client using process environment
settings; automated checks always inject a fake provider, never live SoC. Preserve
marketplace availability/session/seller rules and PostgreSQL. No DB schema changes,
seller integration, provider-wide quota system, deployment or JPro session redesign.
Each arm gets the same frozen contract/tests/client and isolated workspace. This is
a new paired case, not pooled with the previous two experiments. No measured run
starts before its own explicit ready/credit checkpoint. The prior close/scheduling
race was resolved and accepted in SOC-LIFECYCLE-1 before this setup.

## Supabase image storage — ST-P1

Status: owner-approved for technical design, 2026-10-21 (explicit chat approval). This is a separate image-storage task; the
completed PostgreSQL conversion and immutable benchmark scopes are unchanged.
Source inspection baseline: delivery source at27cdc2b (current source is unchanged
by the separate uncommitted log-relocation documentation). No Storage code changed.

### Confirmed owner decisions

- Preserve/import existing local listing and profile photos, retaining their
  database references; do not reset marketplace records or silently discard images.
- Use private Supabase Storage buckets named exactly `listingImages` and
  `profileImages`, as supplied by the owner. Actual bucket visibility/configuration
  still needs operator verification; selecting private access is approved intent.
- Preserve/import images from both the Windows app folder and Docker hotshop-images volume. Confirm exact mounted paths during technical design without exposing file contents or secrets.

### Existing behavior to preserve

ImageStorage validates real JPEG/PNG content before copying bounded bytes into
UUID filenames. Profile limits are5MiB and512x512; listing limits are10MiB and
4096x4096, with up to10 ordered listing photos. No automatic crop/resize. Current
service authorization, listing-state restrictions, explicit photo order and
replace/remove behavior remain. PostgreSQL stores image filenames; files are
currently local. Runtime resolves local Paths; UI uses JavaFX file chooser/Path
images. ManagedImages scans for unreferenced files and retries queued deletion,
protected by the current directory lock. These observations are source evidence,
not passing Storage-integration or browser-upload results.

### Acceptance criteria

| ID | Expected behavior and acceptance evidence |
| --- | --- |
| ST-01 | New profile/listing photos use their respective private bucket. Images remain available after app/container replacement without requiring the old local image volume; desktop and hosted browser flows are verified separately. |
| ST-02 | Only already-authorized users may perform existing upload/replace/remove actions. Reads follow app access rules. Storage credentials remain server/operator-side and never appear in browser code, database image references, logs, image URLs or user errors; no public bucket is required. |
| ST-03 | Keep current JPEG/PNG content, byte/dimension and listing-photo-count/order rules. Invalid files are rejected before permanent persistence. Browser selection/upload must reach the server as actual file bytes rather than assume a browser client's file exists on Render. The visible Add Photo/profile-photo workflow stays familiar. |
| ST-04 | Display saved listing/profile images through the app. Missing/unavailable images use safe fallback feedback; an image-read outage does not erase records or unrelated marketplace data. Local paths and credentials are not exposed. |
| ST-05 | Upload failures leave the prior saved image/reference unchanged. Database failure after upload leaves recoverable cleanup work. Replacement/removal retires only the affected unreferenced object after the record change commits; failed cleanup is retryable. |
| ST-06 | Shared-bucket cleanup must not delete referenced images, another namespace's objects or an upload still being committed by any app instance. Test upload-versus-cleanup and interrupted-operation races. Do not transplant the current unreferenced-file scan into a shared bucket without coordination. |
| ST-07 | Provide a separate repeatable operator import/verification step for approved local sources. Retain local originals and bucket objects; preserve existing DB references and image ordering. Missing sources or conflicting bytes are reported without silent overwrites/deletions. Verify all referenced images before switching storage; pause image writes during cutover if needed. No automatic startup import or database reset. |
| ST-08 | Configuration has explicit project URL, credentials and two bucket names. Missing/invalid required remote settings produce safe operator feedback; there is no silent fallback to disposable local storage. Document setup, import, rollback and deployment configuration. |
| ST-09 | Automated tests use isolated local storage/HTTP fixtures and disposable local PostgreSQL, never the real buckets or hosted/demo data. Live bucket smoke verification, if requested later, is separate evidence with named scope and cleanup. Passing mock checks do not establish hosted readiness. |

### Scope boundary and remaining gates

Include listing/profile image storage, display, browser-upload compatibility,
existing-photo import, coordinated cleanup, configuration, tests and existing guides.
Do not remove directory-lock/image-recovery protections as an incidental edit.
Broader JPro authentication/session lifecycle and deployment automation are separate;
this storage task must document any dependency rather than claim general multi-user
readiness. No seller LLM changes, benchmark reruns, bucket reset, public access or
hosted writes are authorized by discovery/specification work.

Before implementation: technical
design resolves backend credentials/private-image delivery, cleanup coordination,
local/browser upload behavior, storage switch/rollback and test seams. The owner
creates/configures private buckets; no secret values belong in specifications.

Provider references: [private buckets](https://supabase.com/docs/guides/storage/buckets/fundamentals),
[storage access control](https://supabase.com/docs/guides/storage/security/access-control).
