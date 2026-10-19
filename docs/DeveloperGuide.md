---
  layout: default.md
  title: "Developer Guide"
  pageNav: 3
---

# HotShop Developer Guide

---

## Introduction

Welcome to the HotShop Developer Guide. This guide introduces the architecture,
codebase, and development workflow of HotShop, a JavaFX desktop marketplace with
local SQLite storage.

Start with [Development Workflow](developerGuide/DevelopmentWorkflow.html) to set
up your environment, run checks, and build the application, including selecting
the JavaFX platform for Intel and Apple Silicon macOS JARs. Then read
[Architecture Overview](developerGuide/ArchitectureOverview.html) to understand
how the application is organised. Use the sidebar to explore each topic in detail.

## Purpose of this Guide

The main objectives of this guide are to:

1. Orient developers to the codebase structure, application lifecycle, and
   responsibilities of each architectural layer.
2. Explain the [JavaFX UI](developerGuide/JavaFXUI.html),
   [Service Worker](developerGuide/ServiceWorker.html),
   and feature services.
3. Provide a reference for environment setup, dependencies, development checks,
   packaging, and engineering tools.
4. Record the product's [requirements](developerGuide/Requirements.html) and the
   steps for [manual testing](developerGuide/ManualTesting.html).

Proposed features and enhancements are documented separately in
[Future Work](developerGuide/FutureWork.html).

### Project Deployment Process

This application uses [JPro](https://www.jpro.one) to run existing Java and JavaFX files in a browser.

For local manual testing purposes, run the following command to start JPro on your local machine:
```
./gradlew jproRun
```
The app should start at `localhost:8080`

Once everything is verified to be working properly, create the JPro distribution `.zip` file with the following command:
```
./gradlew jproRelease
```
The resultant `.zip` file will be located at `/build/distributions/HotShop-jpro.zip`

**NOTE:** Deployment onto JPro requires that the application's entry point extend `javafx.application.Application`, if you wish to run the app without JPro locally, change the mainClass of your `build.gradle` to `hotshop.Launcher` as such:
```build.gradle
application {
    mainClass = 'hotshop.Launcher'
    applicationDefaultJvmArgs = ['--enable-native-access=ALL-UNNAMED']
}
```

For deployment onto [Render](https://render.com), a Docker Image is required. Follow the steps to create the image:

1. Install [Docker Desktop](https://docs.docker.com/desktop/) for your operating system.
2. Run the following command in the project root directory:
```
docker build -t <image-name> .
```
3. Once the build has completed, test that it works by using the following command:
```
docker run -p 8080:8080 <image-name>
```

### Development verification

The full test suite takes more than ten minutes on a typical laptop, mostly
because each test account's password is hashed with 600,000 PBKDF2 iterations.
Run the targeted checks below while developing and the full suite before
committing.

Targeted development checks:

```powershell
.\gradlew.bat test --tests hotshop.service.AccountServiceTest
.\gradlew.bat test --tests hotshop.service.ProfileImageTest
.\gradlew.bat test --tests "hotshop.service.Listing*" --tests hotshop.service.PublicListingsTest
.\gradlew.bat test --tests hotshop.service.OfferServiceTest
.\gradlew.bat test --tests hotshop.service.TransactionServiceTest
.\gradlew.bat test --tests hotshop.service.MeetupServiceTest --tests "hotshop.model.Meetup*"
.\gradlew.bat test --tests hotshop.service.ChatServiceTest --tests hotshop.model.ConversationTest --tests hotshop.model.MessageTest
.\gradlew.bat test --tests hotshop.storage.ImageStorageTest
.\gradlew.bat test --tests hotshop.ApplicationRuntimeTest --tests hotshop.database.DatabaseTest
```

### Product specification workflow

Use the `manage-product-spec` agent skill in
`.agents/skills/manage-product-spec/SKILL.md` to clarify a new product or proposed
change before design and implementation. It interviews the user, resolves
material ambiguities, and creates or updates `workflow/PRODUCT-SPEC.md` with
agreed requirements, UI expectations, acceptance criteria, and scope. Unresolved
product decisions are recorded in a draft. Using the skill does not implement
the feature or authorize later workflow stages.

For migration work, approve the complete evidence-backed feature inventory first,
then detail features individually. The product spec records per-feature status,
source references, acceptance IDs, and approval evidence. A partially drafted
document may contain approved features; no draft section silently becomes approved.

### Technical specification workflow

Use the `manage-technical-spec` agent skill in
`.agents/skills/manage-technical-spec/SKILL.md` after establishing the product
specification. It reads `workflow/PRODUCT-SPEC.md` and creates or updates
`workflow/TECHNICAL-SPEC.md` with requirement traceability, data and interface
contracts, security mechanisms, failure handling, and planned verification.
Ambiguities and discrepancies are referred to the user before dependent design
decisions are finalized. Unresolved blockers keep the document in draft status;
the skill does not implement the design.

The technical spec records per-feature design readiness, shared-contract versions,
frontend/backend responsibilities, and both owners' agreement to interface changes.
It defines the persistent store's lifecycle, seed provisioning and deployment
assumptions. PostgreSQL is no longer deferred; see the migration collaboration section. Verification plans include the agreed unit,
integration, and Playwright layers. Unresolved shared foundations block dependents;
independent approved designs can proceed to planning. Readiness is not permission
to implement. These skills are updated instructions, not completed specifications.

### Implementation planning workflow

Use the `plan-implementation` agent skill in
`.agents/skills/plan-implementation/SKILL.md` to turn the technical specification
into `workflow/PLAN.md`. The plan lists ordered tasks with IDs, prerequisites,
descriptions, steps, and acceptance criteria, traced to the specification.
Tests normally come first, followed by implementation and verification for each
behavior slice. Necessary test setup and manual-check exceptions are explained
explicitly. Unresolved design decisions keep the plan in draft status; planning
does not execute the tasks.

Each task has a `Completed:` section for progress across sessions. New tasks
start at `No`; execution changes this to `Yes` after the task's acceptance
criteria are satisfied. Subsequent planning preserves completed entries
unchanged. Specification changes requiring edits to their code or tests receive
new follow-up tasks with fresh IDs.

Planning now records task-level readiness, human approvers, role/file ownership,
issue/contract references, and bounded authorisation separately. Frontend and
backend tasks may proceed against an approved shared interface; final integration
depends on both. The plan includes test-readiness, implementation/coverage, and
final security reviews plus the agreed test layers. Corrective follow-up tasks
retain the original shared correction budget and require approval before execution.
These are instruction changes; no implementation plan or runtime verification has
been produced by editing the planning skill.

### Task implementation workflow

Use the `implement-task` skill at `.agents/skills/implement-task/SKILL.md` for
selected plan tasks or coordinator assignments inside an explicitly approved
bounded scope. Eligibility is checked per task: incomplete prerequisites block
affected work, while independent approved work may continue. Dependent tasks are
dispatched only after prerequisite completion evidence is available.

The skill permits minor local choices within approved behaviour and design.
Material decisions go to the human owner; shared-interface changes require both
owners' agreement. Testing agents own tests and implementers own production code.
Failed-loop edits require an approved correction plan and share the two-cycle
limit across all review stages.

Completed plan entries remain immutable; corrections to completed work require
authorised follow-up tasks. Task completion requires actual acceptance evidence,
and final feature completion additionally requires its specified review and
integration gates. Role/skill alignment is documented; the scoped disposable
exercise and remaining validation limits are recorded below.

### Agent configuration and interaction records

[Agent configuration](agents/AgentConfiguration.md) maps four worker
TOML files to their role briefs and provides main-chat startup instructions for
the coordinator. The user-supplied smoke-test report confirms all four custom types launched; isolated instruction loading remains unverified. Scoped generic-dispatch behavioural
validation is recorded in the exercise report. Models and permissions inherit from the
parent; review instructions do not create a separate sandbox.

Maintain the [project decision record](InteractionRecord.md) alongside task logs. Repository records contain project-relevant decisions and outcomes; personal conversation records stay outside the repository.

Update the project decision record whenever a decision is agreed, including discussion-only turns. Maintain its current handoff with the workspace/branch, completed and unverified work, blockers, and next authorised step. Preserve earlier decisions and explicitly mark replacements.

### Documentation upkeep

Every completed feature or change includes updates to both
[AGENTS.md](../AGENTS.md) and this Developer Guide before completion is reported.
AGENTS.md retains the instructions and documentation pointers future agents need;
this guide explains the resulting behaviour, design, setup, and workflow, with
relevant verification and limitations. Update the existing sections so they
reflect the current implementation. Record detailed interaction history in a
separate task log under `logs/`.

This applies to application changes, agent configuration, documentation changes,
and newly agreed workflow rules. Explanatory conversations without a completed
change do not require edits. User-facing changes also update the User Guide.

### Feature and task issues

Use a feature parent issue for behaviour, specification links, acceptance criteria,
and linked task issues. Split frontend/backend tasks are separate GitHub issues
with owners, dependencies, status, acceptance criteria, and linked PRs. Substantial
integration work may have its own issue; small features can remain one issue.
Close the parent only when required tasks and feature-level acceptance checks are
complete. This structure is agreed; feature and task templates are written locally. Task stage conventions are agreed; the PR template is also written locally.
The [feature issue template](../.github/ISSUE_TEMPLATE/feature.md) supports incremental
planning: known outcomes, draft criteria, open questions, and later task discovery.
Owners/specs/task links may be pending when an issue is created. Before implementation,
the selected task must have approved detail, ownership, relevant contracts, and resolved
prerequisites. Newly discovered changes to agreed scope or interfaces retain human
approval gates. The template is local; no GitHub issues were created by adding it.

The [implementation task template](../.github/ISSUE_TEMPLATE/task.md) is shared across
frontend, backend, and integration issues. It links the parent, specs and plan IDs;
records ownership, acceptance, dependencies, verification and approval; and collects
actual completion evidence. Fields can be pending at creation. Readiness is checked
before implementation, and correction cycles remain shared across related tasks.
Task stages are Backlog, Ready, In progress, Review, and Done. Paused denotes a
deliberate hold; Blocked denotes an unresolved dependency or decision. Both are
flags preserving the task stage, with a reason and resumption condition recorded.
See the migration workflow for stage gates. The task template contains these fields;
Stage, flag and triage labels are now configured. GitHub Projects is explicitly deferred; issues and labels provide current tracking.
### Feature completion documentation

A feature is Done only after the User Guide, this Developer Guide, AGENTS.md, and
its [SDD interaction summary](agents/SDDInteractionSummary.md) are updated, alongside
required acceptance and review evidence. The summary links specifications, decisions,
plan tasks, human approvals, corrections, verification and per-task logs. Maintain one
summary per feature under logs/; keep the running decision record for cross-task handoff.

The [PR template](../.github/PULL_REQUEST_TEMPLATE.md) asks for those links and separates
intermediate task completion from feature completion. Intermediate PRs identify any
outstanding documentation owner/task rather than asserting complete feature delivery.

### Documentation ownership and review

The [common role contract](agents/AgentSpecifications.md#documentation-ownership)
assigns document writers and reviewers. Implementers update usage/design/setup
guidance; the coordinator consolidates instructions, workflow and SDD/decision records.
The coordinator serialises shared-file writes and checks task-log completeness.
The specification reviewer independently assesses final guides, instructions and
SDD traceability, including coordinator-authored content, before feature completion.
Workers may write only their assigned logs/reports beyond normal role scope;
explicitly read-only work returns report content without writing files.

The coordinator alone maintains shared plan completion/progress fields from worker
reports and required acceptance/review evidence. Workers return evidence without
editing plan status. Feature issues explicitly collect both guide updates, AGENTS.md,
the SDD summary, task logs and independent documentation-review evidence before closure.

### Test-first evidence and existing behaviour

Tests establish approved behaviour, not an artificial red-green ceremony. Missing or
incorrect behaviour requires a relevant behavioural failure before production edits.
Already-correct behaviour can use passing tests, but the specification reviewer must
confirm the implementation path, meaningful assertions and applicable edge cases.
Record baseline, criterion IDs, actual commands and outcomes. Unexpected passing tests
require investigation; setup failures are unverified. Classify mixed cases separately.
Legacy JavaFX tests do not establish that the web implementation works. Existing
review, approval, integration, documentation and task-completion gates still apply.

### Disposable workflow validation

The [exercise report](agents/WorkflowExercise.md) records the isolated cart-total
exercise, including test-first handoffs, independent documentation/security reviews,
a deliberately introduced failure, explicit human approval and one repair cycle.
The final suite passed 24/24 tests; no application source/tests were added to MP3.

Stale current counters in the plan/SDD summary were caught and corrected. Keep those
current values aligned at handoffs while preserving historical stage evidence.
Workers used generic dispatch with saved role instructions; native loading and
broader application/runtime properties remain outside this exercise's evidence.

### Assistant handoff

The [Claude handoff](CLAUDE-HANDOFF.md) records the migration stopping point,
local changes, approvals still needed and verification limits. Markdown workflow
roles remain the reference; client-specific configuration loading must be checked
in the receiving environment.

### MP3 tracker operation

Use [tracker instructions](agents/issue-tracker.md) and [triage labels](agents/triage-labels.md)
for the MP3 repository, stage/flag meanings, parent completion gates and branches.
Workflow approvals and smoke-test evidence are already recorded. The user selected issues and labels on 2026-10-17 and deferred the board.
No additional Projects permission is needed. Issue #6 records publication evidence for the
tracker documentation.

### LLM description preparation and specification

[LLM Feature Product Specification](../workflow/LLM_FEATURE_PRODUCT_SPEC.md)
defines proposed optional description generation for Create Listing. A valid result
immediately replaces existing description text. Provider endpoint, bearer token and
model will be supplied privately through configuration; no real values belong in
source control. The LLM receives title and condition only, alongside fixed task
instructions, with no tools, secrets or internal application context.

Generation is not yet available in the application. The user selected TASK-001–004
and confirmed endpoint/key readiness on 2026-10-20, permitting offline tests without
reading real settings. Live calls require separate explicit authorisation. Generated
descriptions have a hard maximum of 100 words and normally target 50–70 words;
the target is not a minimum, and manual description limits remain unchanged.
The user supplied a JSON response example containing completed assistant output
and duplicate top-level/nested text; see FR-003. Insert description text once,
without metadata, using confirmed top-level `output_text` without nested fallback.
Target the current JavaFX/JPro application, later Docker on Render. Load private
configuration from untracked project-root `.env` on the trusted Java side; never
include it in container images or browser assets. Existing ignore rules exclude
`.env` from Git, but container secret provisioning/exclusion is not implemented.
The user requests a no-tool prompt; expose no application tools and execute no
returned calls. Provider-side tools cannot currently be confirmed disabled; a
prompt cannot establish that guarantee. OQ-04 contains user-approved limits:
10-second connect / 45-second total timeout, 8 KiB request / 64 KiB response,
one active request/account, 5/minute and 30/hour per account, no automatic retries.
Technical design must record the approved settings before implementation;
finite adversarial testing cannot establish immunity to all prompt injections.
The proposed [LLM technical specification](../workflow/LLM_FEATURE_TECHNICAL_SPEC.md)
defines the session-bound service, asynchronous Java HTTP transport, strict bounded
JSON handling, configuration loader and stale-result guards. TASK-002 added Gson
2.14.0 as the sole JSON dependency and a typed `hotshop.llm.LlmClient` seam;
TASK-008 now implements the transport, with application wiring still pending.
Upstream release/security review
and local Java 25 strict-streaming tests support this dependency choice.
The user approved one server and in-memory quota reset on restart. Current runtime
session/locking behaviour does not establish independent JPro browser-client
identity; multi-user hosting needs a separate lifecycle review. The design can feed
planning for its limited scope; provider tool assurance/live semantic security
evaluation and future container release checks remain unverified.
The [LLM implementation plan](../workflow/LLM_FEATURE_PLAN.md) contains 17 ordered
tasks, all initially incomplete. Behaviour tasks require runnable tests and verified
behavioural failures before production changes, then passing checks and refactoring.
Testing and implementation owners coordinate shared-file edits; the coordinator
maintains completion evidence. The configuration-readiness gate is resolved; live
evaluation still requires explicit authorisation. The plan itself authorises
no execution. Provider-side tool assurance and independent JPro client identity
remain acceptance gaps; later Docker/Render work is outside the local task scope.
See the [feature summary](../logs/2026-10-19-feature-llm-description-sdd-summary.md)
for decisions and verification limits. User Guide changes wait for implemented
behaviour so proposed controls are not described as existing functionality.

Offline LLM tests and synthetic fixtures live under `src/test/java/hotshop/llm/`
and `src/test/resources/hotshop/llm/`. Run them with
`.\gradlew.bat test --tests 'hotshop.llm.*'`. Ordinary `test`, `check` and `build`
exclude the `llm-live` tag. The separate `.\gradlew.bat llmLiveTest` task is never
automatically included; do not run it without live-call authorisation. Its tagged
26-case corpus compiles, but TASK-014 must first register a production
`LiveLlmClientFactory` adapter. Missing adapters fail explicitly rather than skip.
See the [evaluation rubric](../src/test/resources/hotshop/llm/SECURITY-EVALUATION.md)
for mandatory human review and secure evidence requirements. No passing live
evaluation or provider-side tool assurance is claimed.

The HTTPS fixture trusts a synthetic CA only in its injected test client, binds
loopback, retains hostname verification and uses synthetic tokens. Its public
test keystore is not an application credential and stays in test resources.
Manual clocks, scheduler and completion gates control timing without provider
calls or changing global TLS settings. See the
[fixture guide](../src/test/resources/hotshop/llm/HTTPS-FIXTURES.md).

`hotshop.llm.LlmConfiguration.load(Path workingDirectory)` now reads one immutable
UTF-8 snapshot from that directory's `.env`. Required keys are
`SOCLAAS_BASE_URL`, `SOCLAAS_API_KEY` and `SOCLAAS_MODEL`.
The user confirmed these names on 2026-10-20, replacing the earlier HOTSHOP_LLM_*
names. BASE_URL is still the exact full HTTPS endpoint; no path is appended.
It splits at the first `=`, trims surrounding syntax whitespace, accepts matching
outer single/double quotes, preserves quoted contents literally and ignores blank
lines, whole-line comments and unrelated assignments. There is no shell expansion,
escape processing or inline-comment removal. Duplicate required keys, malformed
standalone lines, unquoted quote characters, unmatched outer quotes and embedded
CR/LF/NUL invalidate the snapshot. CRLF and LF line endings are supported.

The endpoint must be absolute HTTPS with a host and no user-info or fragment;
its path/query remain exact and internal hosts are allowed. The model must be
nonblank. Tokens must be nonempty, contain no whitespace/control characters and
fit Java HTTP header characters (through U+00FF). Invalid/missing/unreadable files
return a safe unavailable snapshot with no raw exception or settings in diagnostics.
Settings access is package-private and guarded; `toString` exposes availability
only. Loading writes no files. Rewriting/deleting `.env` does not change an existing
snapshot; startup wiring that loads once belongs to TASK-010 and is not implemented.
Manual listing creation/persistence still works with missing configuration.

`hotshop.service.DescriptionQuota` now provides the account admission primitive.
An explicitly owned registry can be shared across session services; it has no
global singleton or persistent state. It reserves one active slot per account
and counts at most five attempts in rolling 60 seconds and thirty in rolling
3,600 seconds. Exact expiry boundaries stop counting; retry seconds round up
from the earliest capacity in each full window, taking the larger window delay.
Monotonic elapsed-time arithmetic supports signed/wrapped `System.nanoTime` values.

Matching reservation IDs make terminal release idempotent and keep dispatched
failures counted. A still-active reservation can instead roll back if dispatch
never started locally; stale rollback cannot erase charged history or release a
newer request. Registry operations prune expired inactive accounts while retaining
active ownership until completion. A new server registry starts empty. TASK-010
must supply one server-owned instance across services; this primitive does not
establish runtime wiring or independent hosted session identity. Its deterministic
suite is `.\gradlew.bat test --tests '*DescriptionQuotaTest'`.

`hotshop.llm.HttpLlmClient` builds bounded requests and implements asynchronous
`LlmClient` transport; it is not wired to the application. Its internal
`buildRequest` checks a stripped title of 1–120 Unicode code points, a non-null
condition and available immutable settings. Gson `JsonWriter` encodes the title
and canonical enum condition as product facts, then encodes a separate outer JSON
object containing exactly `model` and `input` strings. The fixed instruction
requests grounded plain text, a 50–70-word target, a 100-word maximum and no tools.
These instructions do not prove model compliance or provider tool disabling.

The builder returns fresh UTF-8 bytes, accepts exactly 8,192 bytes and rejects
larger bodies with fixed safe diagnostics. Endpoint/token, previous descriptions,
identity, history and application data do not enter the body. Product text cannot
alter configuration, and legitimate titles containing words such as “system” are
accepted. Validation and byte-size checks occur without any quota admission;
later orchestration must prepare successfully before reserving a dispatch slot.
Request encoding tests use `.\gradlew.bat test --tests '*LlmRequestTest'` with
synthetic configuration only. No new dependencies were added in TASK-005–006.
The selected batch passed 818 full-suite tests, Checkstyle and `check build` on
2026-10-20; see the
[integration/review evidence](../logs/2026-10-20-llm-task005-006-integration.md).

TASK-007 adds the package-private `LlmResponseParser`, reached through
`HttpLlmClient.parseResponse(byte[])`. It strictly decodes the complete bounded
UTF-8 body and traverses one JSON object, checking duplicate names in all objects
and a maximum container depth of 32 including the root. It requires completed
status and canonical top-level string `output_text`. Optional output arrays must
contain completed assistant messages with only output-text content; tool/refusal
variants fail safely. Nested text never replaces or supplements canonical text.

Descriptions are stripped, then checked for 1–100 words and at most 5,000 Unicode
code points without truncation. Words are maximal runs separated by Unicode
whitespace or space characters that contain at least one letter/digit; hyphens
remain within one word and punctuation-only runs do not count. Unknown metadata
is traversed within the same bounds. Parser failures expose only a fixed typed
`INVALID_RESPONSE` result. Structural validation cannot identify every textual
refusal, fabricated fact or injected instruction; live semantic evaluation remains
TASK-014. Focused response tests use
`.\gradlew.bat test --tests '*LlmResponseTest'` with synthetic inputs.

TASK-008 creates the reusable Java HTTP client, dedicated virtual-thread executor
and deadline scheduler lazily on first generation. Request-only construction
starts no transport resources. The package-private test constructor transfers
ownership of its injected client, worker and scheduler; close cancels pending work
and shuts resources down without waiting for HTTP-client close or termination.
Production uses standard certificate/hostname verification, redirects disabled,
a 10-second connection timeout and exact configured POST URI. Only the bearer
header carries the token. Requests send JSON content/accept headers and request
identity encoding; there is one application dispatch and no automatic retry.

Each operation has a 45-second request timeout plus an independent deadline that
covers receiving, decoding and parsing the body. The internal
`BoundedLlmBodySubscriber` counts every incoming buffer before copying, retains
one bounded chunk and requests further data as the worker consumes it. Identity
or incremental gzip decoding enforces separate 65,536-byte limits for wire input
and decoded output. Content-Length is only an early rejection hint. Java 25 gzip
member handling was checked against
[OpenJDK source](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/java.base/share/classes/java/util/zip/GZIPInputStream.java)
and verified with split/coalesced members and a JSON document
spanning members; remaining bounded wire data is consumed through HTTP EOF.

Timeout, cancellation and close terminate once and cancel HTTP futures,
subscriptions, decoder streams and deadlines, including late resource arrivals.
Only HTTP 200 with JSON media type and valid response structure succeeds.
401/403 map to `PROVIDER_AUTH`, 429/5xx to `PROVIDER_BUSY`, other 4xx to
`UNAVAILABLE`, unsupported status/media/encoding or invalid bodies to
`INVALID_RESPONSE`, timeout to `TIMEOUT`, and other transport failures to
`NETWORK`. Raw provider text, headers, URI and exception causes are not returned
or logged. Run `.\gradlew.bat test --tests '*HttpLlmClientTest'` for synthetic
transport verification. No new dependencies were added in TASK-007–008.
TASK-009 adds session authorisation and shared quota admission through
`DescriptionService`; runtime/UI integration remains TASK-010 onward. The transport
must be reached through the session-bound service when wired for users.
Live evaluation and provider/hosting assurance
remain unverified. See the
[selected-task integration record](../logs/2026-10-20-llm-task007-008-integration.md).
The selected TASK-007–008 batch passed
`.\gradlew.bat test checkstyleMain checkstyleTest check build` on 2026-10-20:
1,014 tests across 35 suites, zero failures/errors/skips, both Checkstyle checks
and JAR/distribution packaging. This includes 127 response and 69 transport cases.
Independent review has no outstanding material findings. These results do not
establish live model semantics, provider tool disabling or hosted session isolation.

TASK-009 adds `DescriptionService(worker, session, quota, client)` with nested
typed `GenerationResult`, `Generated`, `Failed` and `FailureKind` outcomes.
`generateDescription(title, condition)` authorises from the worker-owned session,
validates minimum inputs and captures both account and login revision. Every
accepted login/logout increments the revision, so logging back into the same
account cannot receive a result from its previous session. The service rechecks
identity before admission and before result delivery; it has no listing,
repository, image or database dependency.

`LlmClient.prepare` performs encoding and the 8,192-byte check on the transport
executor before quota admission. It returns an opaque single-use prepared request.
The service reserves the shared account slot and starts a nonblocking attempt.
A trusted before-send callback records actual HTTP start time once for the matching
reservation, so time spent queued does not shorten the rolling frequency window.
The attempt reports whether HTTP dispatch was attempted after reaching a terminal
state. Known pre-dispatch failures roll back; dispatched failures retain their
charge. Unknown failures from malformed injected collaborators conservatively
retain charges and return fixed safe outcomes.

The shared worker remains available for account/logout operations while generation
is pending. Cancelling the returned service future alone retains the transport's
active quota slot until transport termination. Closing the service cancels its
own pending work and rejects new calls without closing the injected worker/client
or clearing shared quota history. The transport settles its dispatch marker and
cleanup before publishing cancellation, preventing a late dispatch after refund.
Unexpected failures log only the fixed `LLM_SERVICE_UNEXPECTED` code. The numeric
retry delay is nonzero only for local rate-limit feedback. Runtime ownership and
startup wiring remain TASK-010; no user-facing generation control is available yet.
See [TASK-009 evidence](../logs/2026-10-20-llm-task009-integration.md).
Final TASK-009 verification on 2026-10-20:
`.\gradlew.bat test checkstyleMain checkstyleTest check build` passed, with 1,106
tests across 37 suites and zero failures/errors/skips. Includes 61 service,
22 preparation and 37 quota cases. Independent review has no outstanding material
findings; no live provider calls or runtime/UI acceptance are claimed.

TASK-007–009 changes are grouped into exactly three task commits. The shared
transport file is split across parser, HTTPS transport and prepared-dispatch
orchestration revisions. Final shared completion records accompany TASK-009;
see the [grouping record](../logs/2026-10-20-llm-task007-009-commit-grouping.md).

TASK-001–006 changes are grouped into one commit per task, followed by a shared
documentation commit for completion records and integration evidence. Unrelated
short-workflow skill directories are excluded. During commit preparation,
`.\gradlew.bat check build` succeeded with all 15 actionable tasks up-to-date;
this reused existing verification outputs rather than executing tests again.
See the [commit-grouping log](../logs/2026-10-20-llm-task-commit-grouping.md).

## Acknowledgements

The LLM specification's distinction between prompt instructions and enforced
capability boundaries is informed by
[OWASP prompt injection guidance](https://genai.owasp.org/llmrisk/llm01-prompt-injection/)
and [system prompt leakage guidance](https://genai.owasp.org/llmrisk/llm072025-system-prompt-leakage/).
The technical design also consults the official
[Java HTTP APIs](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html),
[Gson project](https://github.com/google/gson),
[Docker secret guidance](https://docs.docker.com/build/building/secrets/) and
[Render secret-file documentation](https://render.com/docs/configure-environment-variables).

See [Acknowledgements](developerGuide/Acknowledgements.html) for the sources of
reused or adapted ideas, code, documentation, tools, and third-party libraries.
