---
  layout: default.md
  title: "Developer Guide"
  pageNav: 3
---

# HotShop Developer Guide

## Agreed agent workflow and project evidence

Use one implementer for code, tests and guides, then final independent specification/
coverage and security reviews. Plan one shared correction pass. Further passes may
fix confirmed errors within approved scope without another approval round; record
the defect evidence, scoped change, pass count and affected checks. Changes to scope,
requirements, live data or deployment still need the appropriate owner decision.
Historical experiments and correction counts remain unchanged.

Keep targeted context and compact briefs without full conversation inheritance.
Prefer Sol for coordination and Luna for specialists. Persist every interaction batch
concisely as work progresses: request/approval, agent assignment, decision, result,
findings, corrections and actual verification. Subagents return messages; the
coordinator appends one feature record rather than generating per-task files.
Preserve existing teammate logs. All records and handoffs live in logs/; the current
shared handoff is logs/ExecutionRecord.md. AGENTS.md contains durable guidance only.

For school assessment, link specs and acceptance tests to implementation; record
actual specialized-agent contributions/reviews, AI security controls/evaluations,
SoC use and resource handling, each team member's contribution, quantified MP2 reuse,
and separately managed deployment automation. Do not infer evidence from agent
configuration files, green mocks or plans alone. This agreement supersedes older
workflow/logging/correction wording below; retain historical outcomes as provenance.

---

## Introduction

Welcome to the HotShop Developer Guide. This guide introduces the architecture,
codebase, and development workflow of HotShop, a JavaFX desktop marketplace with
PostgreSQL persistence and local managed image storage.

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

#### PostgreSQL conversion status — 2026-10-19

The owner selected Render hosting, Supabase PostgreSQL and Supabase Storage,
and approved PostgreSQL in all environments, including development and tests.
The retained application uses PostgreSQL in development, production and tests. Fresh
installation starts empty; existing SQLite records are not imported. Supabase Storage
and deployment remain separate integration work. No hosted database has been used
for automated tests. Follow the operator setup below before starting the application.

The jpro-docker workflow uses local specifications/planning, Gradle CI, one implementer
for the approved batch and final specification/security review. The database tests use
local Docker PostgreSQL; missing Docker is a setup failure with no SQLite fallback.

#### JPro and Docker packaging

The current entry point, `hotshop.Main`, extends JavaFX Application. No main-class
change is needed to run locally with `./gradlew run` or in a browser with
`./gradlew jproRun` (port 8080). Both require the configured PostgreSQL database.

For a manual Linux distribution, use `./gradlew jproRelease -PjavafxPlatform=linux`.
The ZIP is generated at `build/distributions/HotShop-jpro.zip` and is not committed.

Build the Docker image from the repository root:

```powershell
docker build -f dockerfile -t hotshop:local .
```

The Dockerfile uses two stages. A Java 25 JDK stage builds the Linux JPro ZIP using
the Gradle Wrapper. `COPY --from=build` transfers that ZIP into a Java 25 JRE stage,
which unpacks it and starts JPro. A fresh source checkout needs no prebuilt local
ZIP. Packaging does not execute automated tests or provision the database.

The `.dockerignore` excludes local environment files, build output and presentation
scratch/workspaces. Only build inputs are copied into the builder; runtime database
credentials and the Supabase CA certificate must not be copied into image layers.
Do not supply secrets as Docker build arguments. Keep checked-in test TLS fixtures
available for tests; they are not hosted credentials.

For local Docker, pass settings using `--env-file .env.local` with plain NAME=value
entries, publish port 8080, mount the CA file read-only at
`/etc/secrets/prod-supabase.cer`, and use that path in `HOTSHOP_DB_URL`.
Preserve local images with a named volume mounted at `/var/lib/hotshop` and set
`JAVA_TOOL_OPTIONS=-Dhotshop.dataDir=/var/lib/hotshop`. Do not paste example database
host placeholders into a real launch command. See local AI Search setup below.

For Render source builds, select Docker, repository-root build context and the
`dockerfile` file. Runtime settings and the CA file are supplied through Render
Environment/Secret Files. Provision the schema separately before startup. Image
build verification is separate from deployment, UI acceptance and JPro browser
session/directory-lock readiness; Supabase Storage integration is still pending.

### Development verification

The full test suite takes more than ten minutes on a typical laptop, mostly
because each test account's password is hashed with 600,000 PBKDF2 iterations.
Run the targeted checks below while developing and the full suite before
committing.

Targeted development checks:

```powershell
.\gradlew.bat test --tests hotshop.ui.AiSearchUiBenchmarkTest
.\gradlew.bat test --tests hotshop.ui.MarketplaceUiTest --tests hotshop.llm.SocSearchClientTest
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

The shared AI-search UI benchmark tests run against disposable local PostgreSQL
and inject a provider function; they never call the live service. Run them with
the first command above. The approved common setup covers the injectable UI seam
and frozen fake-provider contract. Process-environment ownership is not tested
through this seam because doing so would require an additional public API. The
measured AI-search UI implementation remains pending.

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
owners' agreement. One implementer owns production code, tests and guides. Plan one
shared correction pass; further evidenced in-scope error fixes follow the current
owner-approved workflow without repeated approval. Scope changes remain gated.

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

Maintain the current handoff in logs/ExecutionRecord.md. Persist every interaction
batch concisely in the existing feature record as work progresses: request/approval,
assignment, decision, outcome, findings, fixes and actual checks. Preserve teammate
logs; do not create a separate file per task/exchange or include private material.

### Documentation upkeep

Update relevant developer/user guidance with delivered behavior. AGENTS.md changes
only for durable instructions. The coordinator records each interaction batch under
logs/; subagents return concise messages without separate reporting files.

This applies to application changes, agent configuration, documentation changes,
and newly agreed workflow rules. Explanatory conversations without a completed
change do not require edits. User-facing changes also update the User Guide.

### Migration collaboration workflow

The [migration workflow](../workflow/MIGRATION-WORKFLOW.md) records agreed scope,
frontend/backend ownership and approval rules, shared-interface handoffs, and
integration requirements. The agreed policy and remaining implementation gates are tracked in
[issue #3](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/3).
Use its issue links for setup/discovery tracking and its remaining implementation prerequisites
before starting dependent work. Setup issues are published; feature discovery has started with a documentation pass. Worker configuration files are written and four custom types launched in the supplied smoke-test report. Generic-dispatch behaviour was exercised; native loading after instruction edits and migration implementation remain unverified or pending.
The agreed sequence is to review and approve the full feature inventory first,
then develop detailed specifications feature by feature. The inventory establishes
scope; implementation still requires approved details and satisfied prerequisites.
See the [initial feature inventory](../workflow/FEATURE-INVENTORY.md) for documented
feature groups and deferred functionality. The [source evidence](../workflow/FEATURE-EVIDENCE.md)
traces all 16 audited groups to implementation paths and representative existing
assertions at commit 1d812ef; no tests or manual walkthrough were executed in that
inspection. A later guide-derived recheck on 2026-10-17 removed no group and added
INV-17, platform and data foundations, which covers web session and authentication,
concurrent access control, demo data lifecycle, atomic changes and the
refusal-message standard. INV-17 has no source trace because it is not an existing
JavaFX screen feature. Two scope decisions were agreed the same day: the web
application supports concurrent multi-user access instead of one logged-in user at a
time, so the database integrity constraints replace the desktop service worker's
serialisation; and the 960 x 640 minimum window size and fixed layout units are not
target criteria, since the web layout adapts to arbitrary viewport sizes.
Coverage of the 17 groups was approved on 2026-10-17; that approval covers the feature
list only and does not authorise implementation. The migration source baseline is frozen
at commit 1d812ef on the same date. Specifications cite behaviour at that commit, and a
later JavaFX source change requires a proposed re-baseline with a diff and explicit
approval before it affects scope. Resolve recorded listing-access, chat-creation
and meetup UI/service distinctions in the affected detailed specifications.
Superseded on 2026-10-17: the web application has no reset feature and uses a hosted
PostgreSQL database from the start. Reset was a property of the desktop app's local data
folder and has no web equivalent, and interim ephemeral server state would vanish between
deployments and cold starts, which users would experience as an uncontrolled reset. Fresh
demo data still applies, so existing accounts, records and images are not imported. Provisioning creates seeded accounts and a spread of listings and no seeded offers, sales or
meetups, so every deal state a viewer sees was produced by the real flow. The technical
specification will define the database choice, seed provisioning and image handling
before storage-dependent implementation.
The agreed testing policy combines focused unit tests, server/storage integration
tests, and Playwright coverage of critical integrated journeys. Use independent
seeded test state and avoid repeating every edge case in each layer. Feature
completion also requires the agreed reviews, configured checks, and documentation;
see the workflow for the authoritative gates. Tools and commands are not yet configured.

A [workflow consistency audit](agents/WorkflowConsistencyCheck.md)
records the cross-skill/role checks and corrections. Java checks apply to the
JavaFX source; web test/build commands remain target-setup decisions. Document
consistency does not establish agent runtime behaviour or feature correctness.

The implementer may edit production code and explicitly assigned documentation/evidence;
tests, fixtures and test configuration remain tester-owned. Its TOML now matches
the existing role brief. The updated instructions were exercised through generic dispatch; native configuration loading has not been revalidated.
See the agent configuration document for smoke-test provenance and limitations.

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
plan tasks, human approvals, corrections and verification. Consolidate the existing
feature summary after reviews pass; keep only one short current handoff during work.

The [PR template](../.github/PULL_REQUEST_TEMPLATE.md) asks for those links and separates
intermediate task completion from feature completion. Intermediate PRs identify any
outstanding documentation owner/task rather than asserting complete feature delivery.

### Documentation ownership and review

The [common role contract](agents/AgentSpecifications.md#documentation-ownership)
assigns document writers and reviewers. Implementers update usage/design/setup
guidance; the coordinator consolidates instructions, workflow and SDD/decision records.
The coordinator serialises shared-file writes and checks result-message completeness.
The specification reviewer independently assesses final guides, instructions and
SDD traceability, including coordinator-authored content, before feature completion.
Workers return result messages without writing logs, interaction summaries or reports.

The coordinator alone maintains shared plan completion/progress fields from worker
reports and required acceptance/review evidence. Workers return evidence without
editing plan status. Feature issues explicitly collect both guide updates, AGENTS.md,
the final consolidated summary and independent documentation-review evidence before closure.

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

## Owner-approved delivery workflow (2026-10-19)

This instruction supersedes earlier role, skill, client-configuration and plan wording
that requires tester handoffs or independent reviews before implementation.

Assign one implementer the entire approved implementation batch, including production
code, tests, fixtures, build configuration, associated guides and its task evidence.
The implementer may write and update tests against the approved requirements; it must
preserve valid assertions and never weaken checks to obtain a pass. Use test-first
evidence where practical and run relevant tests/styles during implementation.
Normal initial implementation includes bringing the approved behavior to green;
do not route every expected development failure through another agent.

Keep that implementer through the batch. No separate tester or intermediate review
handoffs by default. Prerequisites are working contracts and evidence, rather than
acceptance by an intermediate reviewer. Pause for unresolved requirements, scope
changes or a genuine blocker. Do not invent scope or relabel review corrections
as initial work.

After implementation, tests, documentation and required checks are complete, submit
one stable candidate for the final specification/coverage and security reviews.
Group findings from both into one report and, if needed, one explicitly approved
correction pass followed by focused re-review. There is one shared correction budget,
not one per reviewer. Stop and report unresolved findings after that pass.
The coordinator retains shared records, plan status and final acceptance ownership.

Existing completed entries, historical evidence and exhausted correction budgets
remain unchanged. This workflow change grants no extra PG-CONVERSION correction cycle.
The owner resumed PostgreSQL implementation on 2026-10-19; current execution status
is recorded in workflow/PLAN.md and logs/ExecutionRecord.md.

## Retained Java PostgreSQL conversion

The owner approved the bounded JavaFX/JPro PostgreSQL scope, PG-T1 and PG-P1 with
staged follow-ups. See [technical design](../workflow/TECHNICAL-SPEC.md),
[execution plan](../workflow/PLAN.md) and [feature summary](../logs/2026-10-19-feature-pg-conversion-sdd-summary.md).
Fresh PostgreSQL data needs no SQLite import. Schema provisioning uses a separate
command; normal startup only validates the installed version. Provisioning takes a
transaction advisory lock and applies the pending migration batch atomically.

Current implementation provides DatabaseConfig environment parsing, redacted diagnostics
and separate credential properties for PostgreSQL connections, with 10-second connect
and 30-second socket timeouts. Hosted connections require canonical sslmode=verify-full;
uppercase/encoded option names are rejected. The runtime takes an explicit DatabaseConfig for tests, or reads the server environment
for ordinary startup. Its directory path selects managed images and locking only;
legacy marketplace.db files are left untouched. There is no SQLite runtime dependency.

Disposable PostgreSQL 17 Docker fixtures are verified, including isolation, teardown and
ignoring application database configuration. Docker client/server 29.8.2 are reachable.
Configuration/connection/TLS regressions pass 21 focused tests; both styles passed.
The initial engine passed49 cases and independent readiness review. The final
PostgreSQL-only suite reports746 tests with zero failures/errors/skips. Coordinator
confirmed test, both styles, check, build and shadowJar on 2026-10-20: exit0,
BUILD SUCCESSFUL16s, all15 tasks UP-TO-DATE from completed verification.
Final Luna specification/coverage and security reviews found no unresolved in-scope findings; hosted acceptance is separate. Reviewers inspected source and relied on supplied check results without rerunning tests.
Final provenance and approvals are consolidated in the feature summary linked above.
Old per-task logs were deleted at the owner's request; do not recreate them.

The owner granted one bounded third correction cycle for the TLS fix; cycle 3/3 is complete and independently verified. Final approval/correction state is consolidated in the feature summary and
the decision record. Further unexpected fixes require explicit owner direction.
JPro directory locking, image recovery, two-browser isolation and shared-storage cleanup
remain separately gated integration work; database checks do not prove hosted readiness.

### PostgreSQL operator setup

Local Supabase certificate is stored outside this repository at
C:/Users/jonat/.postgresql/prod-supabase.cer. Use that path as the sslrootcert URL
parameter together with sslmode=verify-full. The certificate-backed connection and separate schema provisioning succeeded on 2026-10-21; this does not establish hosted deployment or multi-user readiness.
For a dedicated JDBC-only project, disabling the unused Data API prevents a separate
access route; leaving it enabled requires appropriate grants/RLS for exposed tables.


Set HOTSHOP_DB_URL to a credential-free jdbc:postgresql URL, and supply
HOTSHOP_DB_USER and HOTSHOP_DB_PASSWORD as separate server environment values.
Remote URLs require canonical sslmode=verify-full and appropriate certificate trust;
local Docker PostgreSQL can use a local connection. Do not put credentials in the URL.

Run .\gradlew.bat migrateDatabase on Windows or ./gradlew migrateDatabase on Unix.
The command creates/upgrades schema without starting the UI. Repeated setup preserves
existing records; it neither imports SQLite data nor seeds/resets the database.
Use operator permissions for schema changes. Runtime credentials need SELECT, INSERT,
UPDATE and DELETE on marketplace tables and SELECT on schema_migrations, without
CREATE/ALTER permissions. Use a distinct database per environment. Restart preserves
marketplace records; no startup seeding, reset or migration occurs.

Failed provisioning exits nonzero with Database provisioning failed; driver exception
text and configuration/credentials are omitted. Check server environment settings,
connectivity, certificate trust and provisioning permissions outside application logs.
The executable-command cases use disposable local PostgreSQL. Hosted provisioning succeeded on 2026-10-21 using operator-supplied settings; automated tests continue to use disposable local databases. Owner reports PostgreSQL 17.11, matching tested major 17. See the existing feature summary
for consolidated checks. The final746-case suite and wrapper checks pass. One earlier
listing-setup failure did not reproduce alone, in the52-case meetup class, or in the
repeat full suite; its cause is unconfirmed. The buyer permission assertion stayed intact.
Proposal and cancellation histories use an internal BIGINT identity history_order
as the tiebreaker when application timestamps coincide. This replaces SQLite rowid
insertion order without generating domain event times or sorting random UUIDs.

### Local development and isolated tests

Use PostgreSQL 17 locally (the test image is postgres:17.6-alpine3.22). For example,
start a disposable development server with a separately supplied local password:

```powershell
docker run --name hotshop-dev -p 127.0.0.1:5432:5432 -e POSTGRES_DB=hotshop -e POSTGRES_USER=hotshop -e POSTGRES_PASSWORD postgres:17.6-alpine3.22
```

Set POSTGRES_PASSWORD in the invoking shell before that command. Supply matching
HOTSHOP_DB_USER/HOTSHOP_DB_PASSWORD and a credential-free local URL such as
jdbc:postgresql://127.0.0.1:5432/hotshop. Run migrateDatabase, then the existing run
or jproRun task. Use a Docker volume when development data must outlive the container.
Never supply hosted/demo credentials to automated tests or print environment values.

Database-backed service/UI tests share one disposable server per class and create a
fresh database per test. Tests inject generated local credentials directly and never
load application environment settings. Runtime close drains workers before fixture
teardown; restart assertions keep the same database. Standalone command tests copy
an isolated build context to avoid recursively locking the active Gradle project.
Non-database unit tests do not start Docker. Full verification remains:
`./gradlew test checkstyleMain checkstyleTest check build shadowJar` (Windows: gradlew.bat).
CI uses xvfb-run for JavaFX and verifies Docker first.

Database conflicts at SQLSTATE 40001/40P01 return safe retry guidance without automatic
callback replay. A username-specific unique violation returns Username is unavailable;
other constraint failures stay storage errors. Preserve the directory lock and shared
image cleanup safety until the separately approved JPro/storage integration replaces
that lifecycle. PostgreSQL test success does not establish two-browser hosting readiness.

## Acknowledgements

The SoC connection benchmark reuses the controlled localhost HTTPS fixture and
synthetic test certificates from teammate branch `llm-feature`, commit `7d028e4`.
Its documented request/response conventions informed AI-T2-C1; unfinished transport
implementation was not imported. Gson 2.14.0 supplies JSON streaming APIs.

See [Acknowledgements](developerGuide/Acknowledgements.html) for the sources of
reused or adapted ideas, code, documentation, tools, and third-party libraries.

The PostgreSQL conversion uses [PostgreSQL JDBC](https://jdbc.postgresql.org/) 42.7.14 and [Testcontainers for Java](https://java.testcontainers.org/) 2.0.5 for disposable database tests.

## Correction policy

Follow the current agreed workflow: one planned pass plus evidenced in-scope error
fixes, recorded by pass. New scope and unresolved decisions remain gated; historical
experiment results and completed correction counts are unchanged.

## Targeted context and handoff rule — owner approved 2026-10-19

Workflow reporting was reduced on 2026-10-20: subagents return concise messages
without Markdown logs/summaries. The coordinator keeps one current handoff and
consolidates approvals/results into existing records after final reviews pass.
See the current AGENTS.md owner-approved workflow; persist each interaction batch as work progresses under logs/.

Coordinators and workers must read only context needed for the current assignment.
Search headings/symbols first; read bounded relevant sections or line ranges. Use the
current handoff plus selected task/acceptance criteria and relevant source/tests.
Do not reread whole chats, long decision records, historical logs or unrelated specs.
For new subagents use fork_turns=none and a compact self-contained brief containing
scope, ownership, current decisions, exact document sections, checks and blockers.
Reuse an active implementer instead of restarting it merely to remove old context.
Mandatory instructions still apply: read applicable instructions once, then consult
only changed/relevant sections. Expand reads only when needed to resolve a concrete
ambiguity or dependency. Keep current handoffs short and link supporting evidence.
Already injected app conversation context cannot be selectively removed by agents;
this rule controls requested history, file/tool reads and future agent handoffs.
The ongoing approved PostgreSQL batch continues; this is not a pause request.

## Owner model preference — 2026-10-19

Use gpt-6.1-sol for the coordinator and gpt-6-luna for specialist subagents.
Repository .codex/config.toml sets the main/default specialist preferences; the four
.codex/agents/hotshop_*.toml roles explicitly select Luna. New specialist launches
use no full-history inheritance. Do not claim an existing session changed model
merely because configuration changed; the coordinator model is selected in the app.
Let an already-running verification finish rather than restart it solely for model
selection. Final specialist reviews use Luna. Settings persistence is verified by
file inspection; a new live session's actual model requires runtime confirmation.

Old standalone interaction logs and verbose histories were deleted on owner request
2026-10-20. Keep only the short current handoff and consolidated acceptance summary;
do not recreate task logs or read removed historical paths by default.

The PostgreSQL/workflow changes are prepared for the owner-requested
postgres-migration branch. Local .env.local files are ignored and must not be
published; supply database credentials through the process/server environment.

### Buyer AI-search benchmark

The owner authorised an early contract/validator comparison on multiagent-search and singleagent-search. AI-P1/AI-T1 and BENCH-P1 are appended to the existing workflow documents. This pure Java boundary accepts decoded proposal maps and returns existing ListingSearch criteria; no provider/database/UI integration or new dependency is part of this slice. Both arms use identical frozen tests, starting code and Luna implementation model. Record actual correctness/security results, timings, interventions and usage only where exposed; preparation is separate and one paired run is a limited case study.

The initial buyer-search validator benchmark is complete locally: both branches pass the same 76 cases, including 42 security-tagged cases, and Checkstyle. Comparison details, the shared fixture correction and timing/coverage limitations are in workflow/PLAN.md under AI search benchmark results; machine-readable metrics are in workflow/benchmark-results.csv. SoC generation and UI integration are not implemented by this slice. No exact token/credit telemetry was available.

The requested larger SoC-client comparison is drafted as AI-T2/BENCH-P2. Each measured run requires a manually recorded credit baseline and a pause for its ending reading; the second run requires a fresh ready signal. Provider contract and seller-client coordination are pending. No SoC implementation or live connection has been verified.

Inspection of llm-feature at 7d028e4 found reusable configuration/request conventions, but its HTTPS transport and response parser are unfinished and its build still uses SQLite. AI-T2 records the supplied SoC endpoint/model and this provenance. Reuse selected LLM components only after shared ownership is settled; preserve this branch's PostgreSQL setup.

The owner clarified that the larger benchmark should independently implement transport, response parsing and the buyer adapter, accepting overlap with the seller branch for later reconciliation. Both arms must start from one common frozen baseline; neither waits for the seller transport. No transport changes or credit measurement have started here. Later reconciliation is outside the benchmark.

SOC-SETUP now prepares the common declarations and offline HTTPS oracle on multiagent-soc-search, using AI-T2-C1. Gson 2.14.0 is selected to match the seller branch (official release: https://github.com/google/gson/releases). Java's standard library supplies HTTP/TLS. These checks target transport and validation; they do not establish live model quality or provider-wide quota compliance.

The common oracle has 73 executable cases, including two independently passing HTTPS fixture checks; 71 failed against the declaration stub before implementation. Independent readiness review found no contradictory required assertions; exact request-byte boundary and network-disconnect coverage remain limited.

The measured candidates are saved as a47df93 (multi) and 560ffcb (single), with 149 focused tests and styles passing on each. Common scoring also executed the same 100 security-tagged cases successfully on both. Independent source evaluation found four multi and one single attributable contract defects, so neither candidate is accepted for production. One shared encoding test/spec conflict is unscorable and excluded from those counts; root audit found the setup reviews had missed it. Initial workflow times were 398 and 271 seconds. Owner-selected dashboard readings were session36% to28% for multi (revised from an earlier30% ending) and28% to25% for single; weekly43% to42% and42% to41%. These are coarse observations, and the revised multi endpoint includes more wrap-up than its timed workflow. Full evidence and limitations are in workflow/PLAN.md and benchmark-results.csv; no corrections or live SoC verification have occurred.

Both standalone shadowJar checks pass; packaged clients include Gson and PostgreSQL with no SQLite classes. Broad check/build regressions were interrupted after approximately 14 minutes in isolated PostgreSQL tests, so no complete regression/build success is claimed. This evaluation ran outside both measured windows.

SOC-CHAT-1 changes the consolidated SoC client to Chat Completions while preserving Java HttpClient, TLS, request/response byte limits, deadlines, and strict JSON/proposal validation. The constructor accepts an HTTPS API base such as `/v1`, optionally with a trailing slash, and appends `/chat/completions`; operation URLs, queries, fragments, and credentials in the URI are rejected. Requests contain only the injected model, a system message with fixed filter instructions, a user message whose content is a JSON `buyerText` object, and `reasoning_effort: none`. The parser requires every choice to be a completed assistant message with nonblank string content and `finish_reason: stop`; it selects the first choice and rejects refusal or tool/function output. The format follows the owner's supplied SoC Python example and the [Chat Completions create reference](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create); the SoC endpoint and model remain operator-supplied, including local model `qwen3.5:9b`.

The optional one-request smoke runner is `tools/benchmark/SocSearchSmoke.java`. With Java 25, `release/HotShop.jar`, and locally supplied `SOCLAAS_BASE_URL`, `SOCLAAS_MODEL`, and `SOCLAAS_API_KEY`, run `java --class-path release/HotShop.jar tools/benchmark/SocSearchSmoke.java`. It prints only a safe result category and suppresses exception details. The current Chat Completions implementation passed 83 focused client cases plus 76 validator cases, and both Checkstyle tasks. The final `check build shadowJar -x test` aggregate passed; it explicitly excluded tests, and the unchanged full PostgreSQL/application suite was not repeated. Final specification/coverage and security reviews of the lifecycle correction passed with no remaining contract finding; their adjacent dispatch-race concern was withdrawn after confirming late HTTP futures are cancelled and raced response bodies are closed. The coordinator made one live synthetic request against the configured `/v1` base with model `qwen3.5:9b`; the smoke runner reported `proposal accepted`. This confirms compatibility for that single request only, not general model quality or reliability. The approved SOC-LIFECYCLE-1 follow-up handles scheduler rejection after close begins by resolving the request as `CLOSED`; its deterministic regression passed, with no transport dispatched or deadline left queued. No additional live request or full database-suite run was performed for this lifecycle correction. UI wiring, quota admission, deployment readiness, and seller integration remain outside this slice.

The explicit AI-search UI benchmark is specified in AI-P3/AI-T3/BENCH-P3. The owner-confirmed UI uses the existing query field and a separate button labelled exactly "AI Search". Both measured implementations are saved on isolated benchmark branches. The delivery branch selects the multi-agent UI and four additional tests; ordinary search stays available without AI calls. Multi candidate3efbeb1 and single candidate05eec8d each pass18 of21 fixed UI cases; three preview-invalidation cases have a common blank-input fixture conflict and remain failed. Equal read-only source assessment found no confirmed implementation/security defects. Multi additionally supplies four configuration/unavailable-feedback tests; single relies on source assessment for that path. Actual process-environment launch and full regression remain unverified in this slice.

Common UI setup used one frozen source/test checkpoint98e6302. Both arms report styles and check/build/shadowJar -x test passing; the common evaluator inspected saved XML/source without rerunning checks. Multi observed workflow493–512seconds versus single472–497seconds; ranges overlap and single includes a restricted-shell Java visibility delay. Dashboard drops were8/4 session points and2/1 weekly points respectively, across different allowance windows; multi reached0%, which may censor usage. These are coarse observations, not exact credits or a general workflow winner. No candidate corrections, live calls or full database suite were performed. PLAN and benchmark-results.csv contain the evidence and limitations; fixture repair and production integration remain separate.

### AI-search consolidation

Owner requested consolidation on codex/ai-search-consolidated. Select multi UI candidate 3efbeb1 including four configuration/unavailable-feedback tests, atop the already repaired common SoC client; retain single candidate search-guide instructions. Original measured branches/results remain unchanged. The original frozen preview-invalidation tests had a blank-input setup conflict, preserved in benchmark history. The owner-approved UI-CI-FIX-1 separately corrects the delivery tests; see its verification below. Product deployment and hosted multi-user acceptance remain separate. No hosted data or live provider calls are required.

Consolidation verification: 163 selected tests passed (83 client, 76 validator, 3 config, 1 unavailable-UI), plus both style tasks and check/build/shadowJar with tests excluded from that second aggregate invocation. The full application/database suite and known-conflicting UI21 suite were not rerun; no full-feature acceptance is claimed.

### Run the consolidated AI Search feature locally

Use JDK 25 and the Gradle Wrapper. PostgreSQL settings are required; AI settings are
optional. The application reads process environment variables and does not load
`.env.local` automatically. Keep this ignored file local, with one NAME=value entry
per line and no `export` prefix:

```dotenv
HOTSHOP_DB_URL=jdbc:postgresql://YOUR_DATABASE_HOST:5432/postgres?sslmode=verify-full&sslrootcert=C:/path/to/database-ca.crt
HOTSHOP_DB_USER=YOUR_DATABASE_USERNAME
HOTSHOP_DB_PASSWORD=YOUR_DATABASE_PASSWORD
SOCLAAS_BASE_URL=https://soclaas-api.comp.nus.edu.sg/v1
SOCLAAS_API_KEY=YOUR_SOCLAAS_KEY
SOCLAAS_MODEL=qwen3.5:9b
```

Use the exact host, port and username from the selected Supabase connection option.
Use the database password rather than a Supabase API key. The SoC base URL is `/v1`;
the client appends `/chat/completions`. A local PostgreSQL server can instead use
`jdbc:postgresql://127.0.0.1:5432/hotshop` with matching local credentials.
Docker Desktop is needed for a Docker database and container tests, not for running
against an existing hosted database. Supabase Storage buckets are not used: images
remain in the local application data directory.

In PowerShell, from the repository root, load the file into the current process:

```powershell
Get-Content .env.local | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable(
            $Matches[1], $Matches[2].Trim().Trim('"').Trim("'"), 'Process'
        )
    }
}
.\gradlew.bat migrateDatabase
```

When provisioning succeeds, launch with `.\gradlew.bat run`. Provision once before
first launch and when applying schema upgrades; normal startup validates the schema
without modifying it. Reload the environment after editing the file. Missing or
invalid AI settings disable AI Search with visible feedback; manual search remains
available. A network/provider failure appears as a safe UI message after a request.
Do not print environment values or enable verbose credential-bearing diagnostics.
Database provisioning intentionally reports a generic failure; authentication
SQLSTATE 28P01 indicates a rejected username/password combination.

The selected implementation sends a request only after **AI Search**, previews all
proposed filters, and requires **Apply filters** before running marketplace search.
Edits, manual actions and navigation invalidate pending proposals; stale page/session
completions are ignored. Main owns and closes the optional SoC client on shutdown.

Presentation exports and build drafts were removed from docs/presentations and
tools/benchmark/presentation-build at the owner's request. Benchmark source/results
remain. Publication preparation does not waive the three known frozen UI fixture
conflicts or constitute final feature acceptance.

DOCKER-PACKAGE-1 packaging verification (2026-10-21): docker build -f dockerfile -t hotshop:render-check . succeeded from source with host build output excluded. The wrapper is normalized to Linux line endings inside the builder. A no-network runtime filesystem check confirmed the executable start script, libraries and Linux JavaFX directory. Independent final specification/security source reviews passed using supplied build evidence; no app launch, database access or hosted deployment was performed in this check. Existing UI fixture acceptance blockers remain.

### UI CI fixture correction

Owner-approved UI-CI-FIX-1 updates delivery tests after the benchmark: valid query/request preconditions for preview and stale-completion checks, bounded observation of asynchronous UI feedback/navigation, and shared JavaFX toolkit startup across test classes. Existing security, invalidation and manual-search assertions are preserved. Original measured snapshots/results are unchanged; this is a separate correction pass, not a benchmark rerun. The combined 84-case UI run and final focused AI21/style checks passed locally. Full Linux CI run39568040457 on5f0c3ea passed test, both styles, check, build and shadowJar (8m33s). Final independent specification/coverage and security reviews passed by source inspection, relying on supplied checks without rerunning. Supplementary Windows full regression was still active at final review; its result must be recorded separately. Runtime behavior is unchanged.
