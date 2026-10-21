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
PostgreSQL persistence and local managed image storage.

Start with [Development Workflow](developerGuide/DevelopmentWorkflow.html) to set
up your environment, run checks, and build the application, including selecting
the JavaFX platform for Intel and Apple Silicon macOS JARs. Then read
[Architecture Overview](developerGuide/ArchitectureOverview.html) to understand
how the application is organised. Use the sidebar to explore each topic in detail.

`ApplicationRuntime.open(directory, clock, config, descriptions)` owns runtime
initialization. Both three-argument overloads delegate to it: supplying a description
owner reads PostgreSQL settings from the server environment; supplying a database
configuration uses the offline description owner. The two-argument overload supplies
both defaults. The one-argument overload supplies the system clock. Runtime initialization
validates the PostgreSQL schema and registers the session with the shared LLM owner.
DescriptionRuntimeTest seeds its shared account in the disposable PostgreSQL database
instead of copying SQLite files. Its two shared-quota tests open separate runtime
directories against that database, using the same account and description owner.
All test runtime openings use PostgresTestSupport.openTestRuntime, preserving each
directory, explicit clock and supplied description owner. Its explicit database
configuration overload preserves synthetic connection-failure tests. Successful
JUnit openings use the disposable per-test database; the live LLM adapter explicitly
owns, provisions and closes its own disposable PostgreSQL fixture outside JUnit.
These paths do not read application database environment settings. The two-session
server shutdown test uses separate accounts so both requests can be pending.
Failed session login closes the newly opened runtime. Storage-preservation tests
compare PostgreSQL public table rows and sequence state as well as local files.
Docker is required for LLM runtime tests too.

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

See [LLM Implementation](developerGuide/LLMImplementation.html) for description
generation setup, design and testing, and [Acknowledgements](developerGuide/Acknowledgements.html)
for third-party resources.

### Project Deployment Process

#### PostgreSQL scope

The retained application uses PostgreSQL in development, production and tests. Fresh
installation starts empty; existing SQLite records are not imported. Supabase Storage
and deployment remain separate integration work. Follow the operator setup below
before starting the application.

The jpro-docker workflow uses local specifications/planning, Gradle CI, one implementer
for the approved batch and final specification/security review. The database tests use
local Docker PostgreSQL; missing Docker is a setup failure with no SQLite fallback.

#### Existing JPro packaging

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
to implement.

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
integration gates.

### Agent configuration and interaction records

[Agent configuration](agents/AgentConfiguration.md) maps four worker
TOML files to their role briefs and provides main-chat startup instructions for
the coordinator. Models and permissions inherit from the parent; review instructions
do not create a separate sandbox. Markdown workflow roles remain the reference;
client-specific configuration loading must be checked in the receiving environment.

Maintain one short [current handoff](InteractionRecord.md). Personal conversation records stay outside the repository; do not create per-task logs.

Keep the current handoff limited to workspace/branch, completed and unverified work, blockers and next authorised step. Consolidate final approvals and outcomes once after reviews pass.
Keep current counters in the plan and feature summary aligned at handoffs.

### Documentation upkeep

Every completed feature or change includes updates to both
[AGENTS.md](../AGENTS.md) and this Developer Guide before completion is reported.
AGENTS.md retains the instructions and documentation pointers future agents need;
this guide explains the resulting behaviour, design, setup, and workflow, with
relevant verification and limitations. Update the existing sections so they
reflect the current implementation. Subagents return concise messages; no separate
interaction history or task log is required.

This applies to application changes, agent configuration, documentation changes,
and newly agreed workflow rules. Explanatory conversations without a completed
change do not require edits. User-facing changes also update the User Guide.

### Feature and task issues

Use a feature parent issue for behaviour, specification links, acceptance criteria,
and linked task issues. Split frontend/backend tasks are separate GitHub issues
with owners, dependencies, status, acceptance criteria, and linked PRs. Substantial
integration work may have its own issue; small features can remain one issue.
Close the parent only when required tasks and feature-level acceptance checks are
complete.
The [feature issue template](../.github/ISSUE_TEMPLATE/feature.md) supports incremental
planning: known outcomes, draft criteria, open questions, and later task discovery.
Owners/specs/task links may be pending when an issue is created. Before implementation,
the selected task must have approved detail, ownership, relevant contracts, and resolved
prerequisites. Newly discovered changes to agreed scope or interfaces retain human
approval gates.

The [implementation task template](../.github/ISSUE_TEMPLATE/task.md) is shared across
frontend, backend, and integration issues. It links the parent, specs and plan IDs;
records ownership, acceptance, dependencies, verification and approval; and collects
actual completion evidence. Fields can be pending at creation. Readiness is checked
before implementation, and correction cycles remain shared across related tasks.
Task stages are Backlog, Ready, In progress, Review, and Done. Paused denotes a
deliberate hold; Blocked denotes an unresolved dependency or decision. Both are
flags preserving the task stage, with a reason and resumption condition recorded.
See the migration workflow for stage gates. Issues and labels provide current tracking;
GitHub Projects is deferred.
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

### MP3 tracker operation

Use [tracker instructions](agents/issue-tracker.md) and [triage labels](agents/triage-labels.md)
for the MP3 repository, stage/flag meanings, parent completion gates and branches.

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

## Retained Java PostgreSQL conversion

See [technical design](../workflow/TECHNICAL-SPEC.md),
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

JPro directory locking, image recovery, two-browser isolation and shared-storage cleanup
remain separately gated integration work; database checks do not prove hosted readiness.

### PostgreSQL operator setup

For a custom certificate file, use its local path as the sslrootcert URL parameter
together with sslmode=verify-full.
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

See [Acknowledgements](developerGuide/Acknowledgements.html) for the sources of
reused or adapted ideas, code, documentation, tools, and third-party libraries.

The PostgreSQL conversion uses [PostgreSQL JDBC](https://jdbc.postgresql.org/) 42.7.14 and [Testcontainers for Java](https://java.testcontainers.org/) 2.0.5 for disposable database tests.

## Owner-approved correction limit (2026-10-19)

Use one initial implementation pass and required initial reviews, then at most one
shared correction pass and re-review per task/batch. Group findings into that pass.
Corrections still require an explicitly approved plan. If findings remain, stop
and report them; no further loop without explicit owner direction. Required initial
readiness, coverage and security checkpoints remain. Avoid repeating unchanged checks.
This owner instruction supersedes older two-cycle wording in saved skills and client
configurations.

## Targeted context and handoff rule — owner approved 2026-10-19

Subagents return concise messages without Markdown logs/summaries.
The coordinator keeps one current handoff and
consolidates approvals/results into existing records after final reviews pass.
See AGENTS.md "Minimal workflow records"; older per-task log requirements are superseded.

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

## Owner model preference — 2026-10-19

Use gpt-6.1-sol for the coordinator and gpt-6-luna for specialist subagents.
Repository .codex/config.toml sets the main/default specialist preferences; the four
.codex/agents/hotshop_*.toml roles explicitly select Luna. New specialist launches
use no full-history inheritance. Do not claim an existing session changed model
merely because configuration changed; the coordinator model is selected in the app.
Let an already-running verification finish rather than restart it solely for model
selection. Final specialist reviews use Luna.

Keep only the short current handoff and consolidated acceptance summary;
do not recreate task logs or read removed historical paths by default.

Local .env.local files are ignored and must not be
published; supply database credentials through the process/server environment.
