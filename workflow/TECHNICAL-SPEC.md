# Java/JPro PostgreSQL conversion — technical design

Revision: PG-T1, 2026-10-19. Baseline: jpro-docker, f15b076.
Status: PG-T1 approved by owner for task planning on 2026-10-19; implementation approval remains separate.
Source: approved bounded scope in PRODUCT-SPEC.md, PG-01–PG-04.

## Scope and architecture

Retain Java 25, Gradle 9.1.0, JavaFX/JPro, repositories and services. Replace
SQLite persistence in production and tests. Fresh database; no record import.
Supabase Storage, deployment and hosted multi-user acceptance are separate work.
Coordinator owns this spec/plan. Under the owner-approved consolidated delivery
workflow, one implementer owns production, tests/fixtures/configuration and guides;
specification and security reviewers independently assess the completed candidate.

## Connections and configuration — PG-01, PG-04

Proposed dependencies: org.postgresql:postgresql:42.7.14 and Testcontainers
2.0.5 PostgreSQL/JUnit Jupiter modules. Remove sqlite-jdbc after conversion.
Versions are documented upstream; Java 25 compatibility still requires actual builds.
Use PostgreSQL 17 as proposed local/test major; confirm the target Supabase major
before final image pinning and hosted compatibility acceptance.

HOTSHOP_DB_URL is a credential-free jdbc:postgresql URL; HOTSHOP_DB_USER and
HOTSHOP_DB_PASSWORD are separate server environment values. Reject missing values,
non-PostgreSQL URLs and credentials embedded in URLs. Do not print configuration,
raw driver errors or credential-bearing exception chains in startup logs.
Hosted connections use verify-full and configured provider trust; isolated local
containers may disable TLS. Use JDBC connectTimeout=10 seconds and socketTimeout=30 seconds (approved correction2); close connections after
work. Retain DriverManager initially, avoiding an unnecessary pool dependency.
Connection capacity/load testing remains a hosted acceptance gate.

## Schema and provisioning — PG-01, PG-02, PG-03

Create a PostgreSQL-specific initial schema from the final state of all six SQLite
scripts. Preserve historical SQLite scripts without loading them at runtime.
Fresh initialization omits SQLite randomblob/hex conversation backfill: no old rows
exist to backfill. No startup seeding or reset; initial data is empty.

Keep textual UUIDs, enums and normalized usernames to preserve current bindings.
Use BYTEA for password salt/hash. Use BIGINT for epoch-millisecond timestamp columns,
message sequences and read counters: PostgreSQL INTEGER is too narrow for Java long.
Keep bounded price/order fields INTEGER. Preserve nullability, foreign keys, checks,
conversation uniqueness and all existing partial unique indexes. Preserve application
Clock and current millisecond precision, with no database-generated domain timestamps.

SQLite rowid supplies an insertion-order tiebreaker in cancellation-request and
meetup-rescheduling history queries. Preserve that existing behavior in PostgreSQL
with an internal BIGINT GENERATED ALWAYS AS IDENTITY history_order column in each
of those two tables, ordered after created_at. This is storage ordering only;
domain timestamps and message/read counters remain application-owned. Cover tied
application timestamps in regression tests. Initial-conversion discovery recorded
2026-10-19; no new user-facing behavior or legacy-data import.

Command contract: ./gradlew migrateDatabase, using operator configuration. Failed provisioning exits nonzero with the sanitised diagnostic "Database provisioning failed" and no raw driver exception or connection/credential values (PG019 acceptance contract; implementation pending). An explicit
provisioning entry point acquires a transaction advisory lock before creating/reading
schema history. Apply pending migrations and history atomically; rollback on failure.
Repeated setup is a no-op; reject newer schema versions. Keep scripts simple enough
for the existing SQL execution mechanism, or parse them deliberately before extending
SQL syntax; do not silently split function bodies on semicolons.

Normal startup only validates schema/version with read-only queries. Missing, older
or newer schema fails with sanitised guidance to run compatible provisioning. Never
create tables, migrate, reset or seed during ApplicationRuntime.open.

## Transaction semantics — PG-02

Retain one caller-owned connection per service transaction, rollback on any failure,
and close on exit. Service transaction isolation: SERIALIZABLE. Migration control uses READ_COMMITTED under its exclusive transaction advisory lock so waiting provisioners read the committed history after acquiring the lock (owner-approved PG018 stage). Preserve existing database
constraints as independent integrity protection. Do not automatically replay callbacks:
image operations and mutable objects make retries unsafe without a separate audit.
Serialization/deadlock failures return a sanitised recoverable storage error instructing
the user to retry. Concurrent username uniqueness violations must retain the existing
username-taken result through SQLSTATE 23505 and the specific constraint, not classify
all constraint errors as duplicate names. Test failures and unrelated SQL errors separately.

Acceptance requires separate-connection competing offer acceptance, booking and chat
sequence tests; at most one conflicting winner, no partial loser state. This does not
claim full hosted concurrency acceptance without the JPro/storage integration gate.

## Runtime interface and integration boundary — PG-01, PG-03

Keep open(Path, Clock) as environment-configured convenience; add explicit database
configuration injection for deterministic tests. Path now selects local image storage,
not the database. Replace startup migrate with version validation. Main must distinguish
database setup/connectivity failure from local folder/lock failure without leaking secrets.

Source evidence: Main owns an instance runtime; runtime owns its session and worker.
Runtime also takes an exclusive directory lock and runs image recovery. ManagedImages
scans unreferenced files and deletes them, which can race another runtime's upload.
Owner approved per-browser authentication/UI state and server-owned shared storage with
coordinated cleanup. Do not remove the lock or invent session-specific image directories
as part of JDBC conversion. Preserve the existing single-runtime safety until a separate
approved lifecycle/storage task replaces it. Hosted multi-user readiness stays blocked
on two-browser isolation, shared-image visibility and upload-versus-cleanup tests.

## Tests and verification — PG-01–PG-04

Use Testcontainers fixture-generated local credentials, never environment/hosted URLs.
One container per class may host a fresh database per test; disable persistent reuse.
Provision before service tests. Keep non-database tests independent of Docker.
Missing Docker is a setup failure, never skipped coverage or a SQLite fallback.
Replace direct SQLite accesses and sqlite_master checks with PostgreSQL equivalents.

Cover fresh/repeated/failed/concurrent provisioning; supported/older/newer/missing
schema startup; credential redaction; binary credentials; millisecond timestamps,
nullable values and long counters; username uniqueness; foreign keys and partial
indexes; transaction rollback; restart persistence; competing operations. Retain
service regression assertions and test independent fixtures/cleanup.

Required wrapper checks: test, checkstyleMain, checkstyleTest, check, build, shadowJar.
CI already runs these on ubuntu-latest; add explicit Docker readiness before container
tests. Under the owner-approved workflow amendment, test readiness and final
specification/coverage assessment are combined at the stable candidate, together
with final security review. Do not weaken valid tests.

## Executed checks and readiness

Source/schema/repository/CI inspection and Git baseline check completed. Docker CLI
located in the per-user installation. Client/server 29.8.2 are now reachable. PG-001 fixture verification passed: two smoke tests, plus rerun with unreachable application settings and container cleanup. No production conversion or full application verification is claimed.

Database design is owner-approved for planning. Target PostgreSQL major/image pin and
Docker readiness remain explicit acceptance prerequisites. JPro/storage is a separate
integration dependency, not evidence against independent PostgreSQL implementation.
PG-P1 execution was approved on 2026-10-19; no production worker
may start without approved plan assignments and working prerequisite contracts.
The owner-approved delivery override in PLAN.md supersedes intermediate review gates.

## Primary references checked

- https://jdbc.postgresql.org/download/ and https://jdbc.postgresql.org/ — JDBC version.
- https://jdbc.postgresql.org/documentation/ssl/ — certificate/hostname verification.
- https://www.postgresql.org/docs/16/transaction-iso.html — serializable failures.
- https://www.postgresql.org/docs/17/explicit-locking.html — advisory locking.
- https://java.testcontainers.org/quickstart/junit_5_quickstart/ — JUnit fixtures/version.

## Buyer AI search — AI-T1, 2026-10-20

Source:PRODUCT-SPEC.md AI-P1/AI-01..07 and AC-AI-01..05. Owner explicitly requested
contract+validator benchmark now on two sequential branches. Current implementation
baseline:aac5548. Contract/test scaffold checkpoint will be committed before runs.
PostgreSQL design above is unaffected. Live SoC/UI design remains provisional.

### First slice: pure proposal validation

New public final hotshop.service.AiSearchProposalValidator with private constructor:
public static ListingSearch validate(Map<?, ?> proposal).
Caller:future SoC response adapter; output:existing ListingSearch. No DB/session/env,
clock, filesystem, network, logging, UI, mutable global state or dependency additions.
Unknown keys and invalid values fail with the fixed exception contract in AI-P1;
never stringify untrusted values into diagnostics. No partial output or input mutation.
Use existing Category,Condition,ListingSort and ListingDetails.MAX_PRICE_CENTS.
Copy conditions into an immutable set; do not coerce types or truncate values.
The exact map/default/Unicode/numeric rules are normative in AI-P1, not duplicated here.

### Trust boundary and traceability

| Source criteria | Mechanism and check |
| --- | --- |
| AI-01,AC-AI-02 | Exact key/enum/type allowlists; reject SQL/action/identity/status fields. |
| AI-02..04,AC-AI-01..03 | Pure all-or-nothing conversion, safe error, integral range checks and Unicode code-point length after strip. |
| AI-05,AC-AI-04 | Defensive condition copy, immutable result and unchanged input collections. |
| AC-AI-05 | Identical common checkpoint, read-only evaluation suite and same Luna implementation model for both arms. |
| AI-06..07 | Deferred SoC adapter and preview UI; benchmark does not establish runtime LLM safety or end-to-end authorization. |

A literal title is data. The validator does not execute SQL/prompts or authorize
search access. Existing ListingService.searchListings remains the authorization
boundary in later integration, unchanged by this slice. No schema/data changes.

### Verification and benchmark protocol

Independent common setup author writes a behavior-free API stub and fixed JUnit5
suite before either run. Expected red must reach UnsupportedOperationException,
not compiler/setup failure. Shared setup time is excluded from measured arms.
Freeze contract, test content/hashes and source checkpoint, then create both branches
from that same checkpoint. Root stays in multiagent-search; singleagent-search uses
an isolated ignored worktree under tools/benchmark, with its own build outputs.
Run multi-agent first, single-agent second. Never transfer implementation code,
review findings, failure hints or results between arms. New workers inherit no chat.

Both implementation agents use Luna and identical assignment/allowed files. Each
owns only the validator; fixed test/spec edits are prohibited. They may inspect/run
those tests and self-verify. Multi-agent adds coordinator plus independent final
specification/coverage and security reviewers; single-agent has no development review.
The common evaluator scores both submitted candidates against the unchanged suite.
First-submission scores are primary. One correction pass per arm is a ceiling, not
a requirement or implicit authorization; post-review fixes need owner-approved plan.
Preserve initial outcomes even if a correction is separately authorised.

Checks for this small CPU-only slice:wrapper test filtered to
hotshop.service.AiSearchProposalValidatorTest, checkstyleMain and checkstyleTest.
Reset per-worktree build outputs consistently; Gradle dependency cache may remain warm.
No20-minute unrelated PostgreSQL/full-app suite is needed for this isolated benchmark;
full feature integration will retain normal repository completion gates later.

Metrics:full acceptance pass count/total; tagged security pass count/total; build/style
status; first-candidate and end-to-end workflow elapsed seconds; implementation,
verification and review phase times where separable; agent count, human interventions
and correction rounds. Record token/credit usage only if runtime exposes actual data,
otherwise N/A. Record order/cache/model limitations. No statistical performance claim
from one task and one run per workflow. Total preparation is reported separately.
No artificial defects, hand-picked reruns or unmeasured claims of agent improvement.

### Later integration and blockers

Future sequence:SoC client (reuse agreed seller-client boundary), JSON/response adapter,
async buyer preview/apply UI, then service/UI/security integration checks. Endpoint,
model, request budget shared with seller feature, timeout and quota policies require
SoC guide and owner agreement before that slice. No Supabase/demo automated tests.

### AI-T2 — SoC connection benchmark draft, 2026-10-20

Owner requested this next slice; design is not yet ready for implementation.
Repository inspection found no SoC client in current application sources and no
declared JSON dependency in build.gradle. Recheck the seller owner's branch before
adding a shared client. Java 25 provides HTTP transport; JSON dependency choice
requires provider-format inspection and compatibility verification.

Proposed coverage: configured server-side credentials and endpoint/model, bounded
requests and responses, transport timeouts, quota admission and rate-limit handling,
strict response/JSON decoding including duplicate keys, and delegation to the
existing AiSearchProposalValidator. No database tools or automatic marketplace
actions. Exact limits, retry policy and response semantics remain open; they must
not be inferred from an unrelated provider's API. Errors must not expose credentials
or raw provider responses. Buyer UI and seller-feature implementation are excluded.

Use a deterministic local HTTP fixture for common acceptance/security scoring;
never call the live provider in automated tests. An optional, separately reported
manual SoC smoke check follows both runs, outside comparative timing/credit windows.
Both arms must start from the same validator implementation, approved provider
contract, dependencies and frozen evaluation suite, rather than their different
Task 1 implementations. New branches preserve both completed Task 1 candidates.

Blocking inputs: non-secret SoC request/response example, endpoint, model, auth
header format and quota rules; seller shared-client agreement. Official guide fetch
failed in this session. No provider contract, numeric limits or live success claimed.

Preparation evidence, 2026-10-20: owner supplied the full endpoint
`https://soclaas-api.comp.nus.edu.sg/v1/responses` and model `default`.
`SOCLAAS_BASE_URL` in that example is a full operation URI; never append another
`/responses`. Fetch/inspection of origin/llm-feature at 7d028e4 found existing
LlmConfiguration, description-specific LlmClient, request-only HttpLlmClient and
DescriptionQuota. No HTTP dispatch or response parser is implemented there yet;
seller plan TASK-007/008 reserves that work. This is source inspection, not executed
test evidence. The branch declares Gson 2.14.0 and still SQLite; do not merge its
build/database/workflow changes wholesale into PostgreSQL branches.

Seller technical contract specifies Bearer auth, exact model/input request strings,
completed status and authoritative top-level output_text. Seller configuration uses
HOTSHOP_LLM_ENDPOINT/HOTSHOP_LLM_BEARER_TOKEN/HOTSHOP_LLM_MODEL rather than the
owner's SOCLAAS aliases. Reconcile names in the shared design before implementation.
Its 10-second connect/45-second total deadline, 8 KiB request/64 KiB response and
5/minute, 30/hour per-account limits are seller application policies, not verified
provider quotas. Shared aggregate provider budget remains unresolved. Reuse and
adapt selected components with attribution rather than treating description-specific
generate(title, condition) as a generic buyer interface. Shared transport ownership
has been put to the owner; no import or measured run has begun.

Owner resolution, 2026-10-20: do not implement the seller's shared transport or
parser. AI-T2 now targets only the buyer adapter after those components are ready:
encode buyer intent using the agreed transport contract, decode returned proposal
JSON strictly, invoke AiSearchProposalValidator and map failures to safe buyer
outcomes. Exact interface and acceptance tests await the completed transport.
Use the identical pinned transport snapshot in both benchmark arms; transport code
and its development cost belong to common preparation, not either measured arm.
Existing transport security checks still require integration verification; their
implementation is excluded from adapter timing and scoring attribution.

Superseding owner clarification, 2026-10-20: implement the connection layer
independently despite overlapping seller work; reconcile duplicates later. Restore
transport, response-envelope parsing and buyer adapter to the measured scope.
Use the inspected seller request/response conventions as documented input, not its
unfinished transport as a prerequisite or finished code to credit to either arm.
Both arms receive the same existing validator and selected configuration/JSON
scaffolding. Pin reused source and dependencies before measurement; specify strict
buyer-proposal parsing separately from provider-envelope parsing. Do not merge or
edit llm-feature. Existing seller deadline/size policies are design references, not
provider quota evidence. Final acceptance details remain to be frozen before runs.

### AI-T2-C1 — common setup contract

Bounded implementation target: new `hotshop.llm.SocSearchClient`, AutoCloseable.
Public constructor `(URI endpoint, String bearerToken, String model)` uses Java
HttpClient with normal TLS, no redirects and 10-second connection timeout. The
endpoint is the full trusted operator-supplied HTTPS URI; reject credentials in
URI, fragment, missing host, invalid token/header controls and blank model using a
fixed `IllegalArgumentException("Invalid SoC configuration")`, no cause or values.
No .env reading in this slice; application startup injection is later integration.
Package-private test constructor adds `(HttpClient client, Duration deadline)` to
those three parameters. Injected client must use Redirect.NEVER; deadline positive.
The caller owns an injected HTTP client; closing SocSearchClient must not close it.

`CompletableFuture<Result> propose(String buyerText)` is nonblocking and returns
only `Proposed(ListingSearch search)` or `Failed(Failure kind)` nested records of
sealed Result. Failure enum: INVALID_INPUT, TIMEOUT, PROVIDER_AUTH, PROVIDER_BUSY,
NETWORK, INVALID_RESPONSE, CLOSED. Invalid buyer text is null/blank or over 1,000
Unicode code points after strip; reject before dispatch. Strip outside whitespace,
preserve interior text. Encode exactly model/input string fields; fixed instruction
describes the existing filter keys/enums, SGD integer cents and JSON-object-only
output. Encode buyer text as untrusted JSON data within input, not raw JSON/string
interpolation. No tool fields, extra messages, app credentials or listing records.
Request maximum 8,192 UTF-8 bytes; oversize is INVALID_INPUT before dispatch.

POST once to the configured URI; Bearer auth, application/json Content-Type/Accept,
Accept-Encoding identity. Never redirect, retry, repoll, execute tools or fetch URLs.
200 only is success; 401/403 PROVIDER_AUTH; 429/5xx PROVIDER_BUSY; all other HTTP
statuses INVALID_RESPONSE. Transport failures NETWORK; connect/total deadlines
TIMEOUT. Total default deadline 45 seconds includes dispatch/body collection/parsing.
Cancel underlying HTTP/body work on deadline or close; ignore late completion and
finish pending outcomes exactly once. Close is idempotent; subsequent calls CLOSED.
Concurrent calls must remain isolated; no global mutable state or credential logs.

Accept identity/no Content-Encoding only; reject compressed/unsupported encoding.
Collect at most 65,536 response bytes, enforce on actual streamed bytes, not only
Content-Length; reject oversize and invalid UTF-8 as INVALID_RESPONSE. Envelope
must be one strict JSON object: no comments/trailing JSON/duplicate keys, nesting
at most 32 containers (root counts 1). Require status="completed" and nonblank
string output_text. Metadata may be skipped only after structural validation.
If output exists, require an array of completed assistant message objects whose
content items have type output_text and string text. Empty arrays allowed; no
fallback to nested text. Reject tool/refusal/other item types, wrong role/status.
Top-level output_text is authoritative even if nested text differs.

output_text itself must be one strict JSON object under the same duplicate/depth
rules; no Markdown fences/trailing JSON. Convert JSON integers to BigInteger,
reject fractional/exponent numeric tokens rather than silently rounding; pass the
decoded map to unchanged AiSearchProposalValidator. Invalid proposals/envelopes
become INVALID_RESPONSE with no raw response, request, token or nested exception
in public results. Valid proposals return the validator's immutable search object.

Reuse Gson 2.14.0 (same as seller; official release confirmed) for JSON streaming;
new dependency is common setup, not arm implementation. Use owned local HTTPS
fixtures, no production trust override. Tests cover request/response boundaries,
JSON/Unicode/security partitions, redirects, streaming limits, failures, lifecycle
and concurrent isolation. Keep fixture faults distinct from meaningful stub red.
These are application bounds, not verified SoC provider limits. Per-account/global
quota admission, UI/session wiring, live model quality and deployment are excluded;
429 handling and no automatic retry do not establish provider quota compliance.

### AI-T2-C1 readiness correction — owner approved 2026-10-20

SOC-FIX-1 preserves the initial benchmark snapshots and resolves the inner-data
oracle conflict for integration: input contains fixed instructions followed by a
separate JSON object with a buyerText string; decoding that object must recover
the stripped buyer text exactly, including quotes, backslashes and interior space.
No raw-literal substring requirement applies to encoded input. Deadline/closure
must coordinate request registration so a completed call cannot leave a newly
dispatched future uncancelled. A single controlled live smoke request is authorised
if credentials are available locally; absence is an explicit verification blocker,
not permission to expose or invent credentials. Automated tests remain offline.

### AI-T2-C2 — documented Chat Completions correction, 2026-10-20

Owner supplied the SoC Python Chat Completions example and explicitly requested
its use. This supersedes C1's Responses wire schema for the integration client,
not the historical benchmark snapshots. Keep Java HttpClient and existing bounds.
Constructor URI now represents the trusted API base (normally ending /v1), with
optional trailing slash; append /chat/completions exactly once. Reject a base that
already ends /responses or /chat/completions and reject query/fragment/user info
to prevent accidental operation-path duplication. Never redirect or retry.

POST fields are exactly model, messages, reasoning_effort. reasoning_effort is
"none"; model is injected, with documented local smoke model qwen3.5:9b. messages
has one system message with fixed filter instructions and one user message whose
content is a JSON object containing buyerText. Preserve stripped buyer text via
JSON round-trip. Bearer authentication and prior size/UTF-8/deadline limits remain.

Parse the non-streamed Chat Completions choices array. Require at least one choice;
every choice must be an object with assistant message, nonblank string content,
finish_reason="stop", and absent/null refusal/function_call or absent/empty
tool_calls (null also means no tool calls). Reject incomplete, filtered,
tool/function output and malformed choices;
use choices[0].message.content only. Do not require Responses status/output_text.
Apply unchanged strict JSON parsing and proposal validation to the selected text.
Structurally validate all metadata under the existing duplicate/depth/integer rules.

One synthetic live check after offline checks is authorised by this connection
request. Local config may replace the former /v1/responses base with /v1 and set
the documented model; preserve the API key without displaying it. No live retries,
UI work, quota system or fix to the outstanding close/scheduler race is included.

### AI-T3 — UI benchmark design approved for setup, 2026-10-20

Source AI-P3 / AI-UI-01..06. Owner approved benchmark implementation; retain JavaFX/
JPro/PostgreSQL. SOC-LIFECYCLE-1 already resolved the known race with final reviews.
Freeze that working client and minimal injectable UI constructor seam, common tests
and dependency versions. Historical readiness correction2/2 remains unchanged;
no extra lifecycle repair is authorised. New UI setup corrections have their own
one explicitly approved shared correction pass, with no automatic review-fix loop.

Shared seam: MarketplaceUi(Stage, ApplicationRuntime,
Function<String,CompletableFuture<SocSearchClient.Result>>); existing two-argument
constructor delegates with absent provider. Store the borrowed provider for SearchPage;
UI does not own/close it. This is a concrete test seam, not a new framework.
Shared setup declares/stores the seam only; no measured UI implementation in setup.

Measured implementation owns SearchPage and any small UI helper, necessary
SearchState changes, Main provider wiring, arm-local tests and associated guides.
Main constructs/owns SocSearchClient from SOCLAAS_BASE_URL/API_KEY/MODEL process
environment, passes client::propose and closes it on stop. Missing/invalid optional
AI config yields unavailable AI controls without blocking app/manual search. No
implicit .env parser; operator launch supplies environment as for the smoke check.

SearchPage adds IDs ai-search, ai-search-status, ai-search-preview, ai-search-apply,
ai-search-dismiss.
The ai-search button's exact visible text is "AI Search" (owner confirmed); keep
the existing query field and Search button. Fixed UI tests assert this text.
Preview lists title/category/conditions/minimum/maximum/sort using plain JavaFX
text only. A validated Proposed search is the only apply candidate;
Failed/null/exception clears preview and shows fixed safe feedback. Proposed data
must remain unchanged and separate from SearchState until explicit Apply. Apply
sets all draft/raw control values, submits once and refreshes via existing service,
preserving its access rules. Use UiPage isCurrent/page/session and request identity
guards plus invalidation on edits/manual action; never render late stale results.
Callbacks mutate controls only on JavaFX thread. Generation uses its own busy state
so normal search remains available; no automatic retry/AI call on navigation.

Common frozen tests exercise observable real JavaFX controls and provider call
counts with controlled CompletableFutures. Local disposable PostgreSQL integration
proves Apply reaches normal search; no hosted data or model calls. Include fast
state-level cases when useful, plus selected existing manual search regressions.
No Playwright: retained JavaFX target. Record framework/DB startup overhead separately
where observable; no broad comparison from one run. Tests get independent oracle
review against product intent before freezing, avoiding prior shared-contract gaps.

Frozen packet coverage limit: common source assessment after BOTH arms separately
checks Main environment/client ownership and visible fixed safe unavailable-config
feedback. The fake-provider UI suite verifies disabled/manual-search behavior but
does not prove that actual config-feedback path. Preserve AI-UI-06; the same source
and visible-feedback evidence requirement applies to both arms, never treat a
disabled button alone as satisfying the feedback requirement.

### SOC-LIFECYCLE-1 — approved close/scheduler repair, 2026-10-20

Owner approved fixing the previously reported lifecycle race only. A concurrent
close between the last open check and timer.schedule must return a future resolved
as CLOSED, never leak RejectedExecutionException to the caller. Coordinate call/
deadline registration with shutdown or safely handle shutdown rejection, ensure
registered deadlines/transports cannot survive a finished call, and preserve
normal TIMEOUT/idempotent close. No lifecycle lock may wait for network or execute
arbitrary future-completion callbacks. A narrow package-private scheduler seam may
support a deterministic interleaving regression; keep public constructors/wire
contract unchanged. No extra retry/live request or UI implementation is included.

### AI-MERGE-1 — seller/buyer configuration coexistence,2026-10-22

Owner requested PR11 conflict integration after seller PR18 merged, then selected
integration only; transport deduplication is deferred. Preserve buyer AI-T2-C2 /
AI-T3 and seller LLM generation protocols and application behavior.

Configuration amendment: SOCLAAS_BASE_URL retains buyer API-base semantics (/v1;
SocSearchClient appends /chat/completions). Seller LlmConfiguration now selects
SOCLAAS_DESCRIPTION_URL as the exact complete HTTPS generation endpoint; no path
is inferred/appended and it must not fall back to the buyer base. API key and model
may be shared via SOCLAAS_API_KEY / SOCLAAS_MODEL. Seller file/environment source
precedence, validation, safe failures and startup snapshots remain; missing seller
settings disable only seller generation. Update current setup and synthetic fixtures
for the distinct key; do not rewrite historical evaluations or modify private files.

Main borrows the shared seller DescriptionRuntime and owns its buyer client; preserve
session/quota checks, stale UI guards and nonblocking runtime close. Merge shared TLS
fixture capabilities and JavaFX toolkit initialization without weakening assertions.
Keep one Gson dependency and PostgreSQL-only isolated tests. No shared transport
refactor, Storage import, live calls, deployment or public behavior redesign.

Verify distinct URLs can coexist, misconfiguration stays isolated, ordinary manual
flows invoke no AI, buyer preview/Apply and seller replace-before-save retain their
contracts, and session/disposal/resource ownership survives the merge. Use focused
fixtures then full final-source CI and final independent reviews.
