---
  layout: default.md
  title: "Development Workflow"
  pageNav: 3
---

## Agreed agent workflow and project evidence

Use one implementer for code, tests and guides, then final independent specification/
coverage and security reviews. Plan one shared correction pass. Further passes may
fix confirmed errors within approved scope without another approval round; record
the defect evidence, scoped change, pass count and affected checks. Changes to scope,
requirements, live data or deployment still need the appropriate owner decision.
Historical experiments and correction counts remain unchanged.

Keep targeted context and compact briefs without full conversation inheritance.
Prefer Sol for coordination and Luna for specialists. Persist each interaction batch
concisely as work progresses: request/approval, agent assignment, decision, result,
findings, corrections and actual verification. Subagents return messages; the
coordinator appends one feature record rather than generating per-task files.
Preserve existing teammate logs. All records and handoffs live in `logs/`; the current
shared handoff is `logs/ExecutionRecord.md`. AGENTS.md contains durable guidance only.

For school assessment, link specs and acceptance tests to implementation; record
actual specialized-agent contributions/reviews, AI security controls/evaluations,
SoC use and resource handling, each team member's contribution, quantified MP2 reuse,
and separately managed deployment automation. Do not infer evidence from agent
configuration files, green mocks or plans alone. This agreement supersedes older
workflow/logging/correction wording below; retain historical outcomes as provenance.

## Setup

Use JDK 25 and the included Gradle 9.1.0 Wrapper. Set JAVA_HOME to your JDK if
necessary. Import the root directory as a Gradle project in your IDE.

```powershell
.\gradlew.bat run
.\gradlew.bat test checkstyleMain checkstyleTest
.\gradlew.bat check build shadowJar
```

Use `./gradlew` on macOS/Linux. Initial dependency resolution requires network access.

### Local PostgreSQL development

Use PostgreSQL 17 locally. The disposable test image is `postgres:17.6-alpine3.22`.
For a disposable development server, set `POSTGRES_PASSWORD` in the invoking shell
without printing it, then run:

```powershell
docker run --name hotshop-dev -p 127.0.0.1:5432:5432 -e POSTGRES_DB=hotshop -e POSTGRES_USER=hotshop -e POSTGRES_PASSWORD postgres:17.6-alpine3.22
```

Set matching `HOTSHOP_DB_USER` and `HOTSHOP_DB_PASSWORD`, and a credential-free
`HOTSHOP_DB_URL` such as `jdbc:postgresql://127.0.0.1:5432/hotshop`.
Follow [PostgreSQL operator setup](AccountServiceAndLocalPersistence.html#postgresql-operator-setup)
to provision the schema before running `run` or `jproRun`. Use a Docker volume when
development records must outlive the container. Never supply hosted/demo database
settings to automated tests or print environment values.

### Isolated database tests

Database-backed service and UI tests require Docker with its Linux engine running;
missing Docker is a setup failure, with no SQLite fallback. `PostgresTestSupport`
shares one disposable PostgreSQL server per class and creates a fresh database per
test. Tests inject fixture-owned credentials rather than application environment
settings. Runtime openings within the same test share its database even when their
image/lock directories differ. Restart checks reopen the same database.

Use `openTestRuntime(directory)` for the default clock, or pass an explicit clock
and description owner through the helper overloads when needed. The explicit
database-configuration overload preserves synthetic connection-failure tests.
Close runtimes to drain workers before fixture teardown. Standalone provisioning
command tests copy an isolated build context to avoid locking the active Gradle
project recursively. Tests without database fixtures do not need Docker.

Full verification uses the wrapper:

```powershell
.\gradlew.bat test checkstyleMain checkstyleTest check build shadowJar
```

CI checks Docker availability and uses `xvfb-run` for JavaFX tests. See
[LLM verification](LLMImplementation.html#offline-verification) for controlled
description-generation tests and the separate live evaluation process.

### Diagrams

The seven diagrams of listings, offers, sales, meetups, and chat (the
`*_uml.puml` files) are PlantUML sources in `docs/diagrams/`, committed next to
the PNGs the guide shows. After editing a source, regenerate its PNG with the
PlantUML jar (1.2026.8, from the
[PlantUML releases](https://github.com/plantuml/plantuml/releases)) placed in the
git-ignored `tools/` folder:

```powershell
java -jar tools\plantuml.jar -tpng -charset UTF-8 docs\diagrams\sale_models_uml.puml
```

The class and state diagrams use PlantUML's built-in Smetana layout, so Graphviz
is not needed. Commit the `.puml` and the regenerated `.png` together.

## Dependencies and checks

### Libraries and build configuration

JavaFX 25.0.2 uses controls and FXML through OpenJFX Gradle plugin 0.1.0.
JUnit Jupiter 5.13.4 is configured with the JUnit Platform launcher.
Checkstyle 12.3.1 enforces mechanical SE-EDU conventions; semantic naming and
clarity still require review. Shadow 9.2.2 bundles runtime dependencies.
Native access is enabled in Gradle launch scripts and the JAR manifest for JavaFX.

### Targeted model checks

Model tests cover validation boundaries, lifecycle transitions, immutable
snapshots, and cancellation permissions/history. For a targeted run, use
`.\gradlew.bat test --tests hotshop.model.TransactionTest`.

## Packaging and CI

### Building the distribution

`shadowJar` writes `release/HotShop.jar`. Build separately for each target OS and
architecture because JavaFX native libraries are platform-specific.
The JAR requires a separately installed Java 25 runtime.

To select JavaFX dependencies for another platform, pass `-PjavafxPlatform`.
Supported values are `win`, `linux`, `mac` (Intel), and `mac-aarch64` (Apple Silicon).
For example, either macOS JAR can be packaged from Windows:

```powershell
.\gradlew.bat shadowJar -PjavafxPlatform=mac
.\gradlew.bat shadowJar -PjavafxPlatform=mac-aarch64
```

These commands write `release/HotShop-mac.jar` and `release/HotShop-mac-aarch64.jar`.
Use `./gradlew` on macOS/Linux. Omitting the property preserves the default
host-platform build and filename `HotShop.jar`. An explicit target produces
`HotShop-<target>.jar`; `win` therefore produces `HotShop-win.jar`.
Run tests without this property on the build host. A cross-platform packaging
build does not verify native execution: launch each resulting JAR on its target
OS with a matching Java 25 JVM architecture before publishing it.

### Published JARs

Prebuilt JARs are committed as `release/HotShop-windows.jar` and
`release/HotShop-linux.jar` and
attached to each GitHub Release. To refresh them for a commit on `main`:

1. Build the Windows JAR locally with `.\gradlew.bat shadowJar`, then copy
   `release/HotShop.jar` to `release/HotShop-windows.jar`.
2. Download the Linux JAR that CI built for the same commit, then copy it to
   `release/HotShop-linux.jar`:

   ```powershell
   gh run list --branch main --workflow "Gradle verification" --limit 1
   gh run download <run-id> -n HotShop-linux -D linux-jar
   ```

The repository also permits `release/HotShop-mac.jar` and
`release/HotShop-mac-aarch64.jar`, generated with the target commands above.
Creating these files locally does not attach them to a GitHub Release.
JARs built from the same source should contain identical `hotshop/` classes;
the JavaFX platform dependencies differ.

Database-backed tests require Docker Desktop with its Linux engine running. Tests
provision disposable local PostgreSQL with fixture-owned credentials; never configure
them with hosted/demo settings. See [PostgreSQL operator setup](AccountServiceAndLocalPersistence.html#postgresql-operator-setup)
for development setup and the separate schema command before application launch.

### Continuous integration

GitHub Actions runs tests, Checkstyle, check, build, and shadowJar on pull
requests and pushes to main/master, and uploads a Linux JAR artifact.
The workflow also supports manual dispatch.

## GitHub Pages

The guides are a [MarkBind](https://markbind.org/) site. Every push to `main`
runs `.github/workflows/docs.yml`, which builds the site with MarkBind and
deploys `_site/` to GitHub Pages, at
<https://cs3227-2610-mp3-hotshop.github.io/CS3227-2610-MP3/>. The repository's
Pages source is set to **GitHub Actions**.

To preview the site locally, install Node.js and run:

```powershell
npm ci
npm run docs:serve
```

`npm run docs:build` writes the site to the git-ignored `_site/` folder instead.
The site is served from the repository's subpath, so `baseUrl` in `site.json`
must stay `/CS3227-2610-MP3`. Links to files outside `docs/`, such as `logs/`,
must be full GitHub links, because only `docs/` pages are published.

## Engineering skills

### Agent configuration

Agent skill configuration lives in [docs/agents](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/tree/main/docs/agents). It defines the
[team GitHub issue tracker](../agents/issue-tracker.md),
[triage labels](../agents/triage-labels.md), and
[domain documentation rules](../agents/domain.md). `AGENTS.md` directs agents
to read these files when needed.

Edit these configuration files directly to adjust the workflow. Re-run
`setup-matt-pocock-skills` when switching trackers or restarting setup.
Domain documentation uses a root `CONTEXT.md` and `docs/adr/`, created by
`domain-modeling` as terminology and decisions are resolved.

### Sharing skills between coding agents

The skills are installed once in `.agents/skills/`, where Codex reads them.
Claude Code reads `.claude/skills/` instead, so link that path to the same
folder rather than copying it. On Windows (no administrator rights needed):

```powershell
New-Item -ItemType Directory -Force .claude
cmd /c mklink /J .claude\skills .agents\skills
Add-Content .git\info\exclude ".claude/skills"
```

On macOS/Linux, use `ln -s ../.agents/skills .claude/skills`. The link is
excluded locally rather than committed because the repository does not enable
Git symlinks. Restart Claude Code if the skills do not appear.

## JPro and Docker packaging

Run `.\gradlew.bat jproRun` (or `./gradlew jproRun` on macOS/Linux) for local
JPro development. It uses the host's JDK and JavaFX dependencies. To create the
JPro ZIP directly, run `jproRelease` with the wrapper.

The repository Dockerfile builds a Linux JPro ZIP from source in a JDK 25 image,
then copies that artifact into a JRE 25 runtime image with the Linux libraries
required by JavaFX/JPro. Build it from a clean checkout with Docker:

```powershell
docker build -t hotshop:local .
```

The image build does not provision PostgreSQL or establish hosted multi-user
readiness. Configure the runtime database and private provider settings outside
the image, and provision the database separately before starting the application.

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

[Agent configuration](../agents/AgentConfiguration.md) maps four worker
TOML files to their role briefs and provides main-chat startup instructions for
the coordinator. The user-supplied smoke-test report confirms all four custom types launched; isolated instruction loading remains unverified. Scoped generic-dispatch behavioural
validation is recorded in the exercise report. Models and permissions inherit from the
parent; review instructions do not create a separate sandbox.

Maintain project decisions in feature summaries and task logs under `logs/`. Repository records contain project-relevant decisions and outcomes; personal conversation records stay outside the repository.

Keep current feature handoffs in their summaries, with completed and unverified work, blockers and the next authorised step. Preserve earlier decisions and explicitly mark replacements.

### Documentation upkeep

Every completed feature or change includes an update to the relevant Developer
Guide topic before completion is reported. Keep the main DeveloperGuide.md as a
brief landing page. AGENTS.md retains durable instructions and documentation
pointers for future agents; update it when those instructions change.
The guide explains the resulting behaviour, design, setup, and workflow, with
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
The [feature issue template](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/.github/ISSUE_TEMPLATE/feature.md) supports incremental
planning: known outcomes, draft criteria, open questions, and later task discovery.
Owners/specs/task links may be pending when an issue is created. Before implementation,
the selected task must have approved detail, ownership, relevant contracts, and resolved
prerequisites. Newly discovered changes to agreed scope or interfaces retain human
approval gates. The template is local; no GitHub issues were created by adding it.

The [implementation task template](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/.github/ISSUE_TEMPLATE/task.md) is shared across
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
its [SDD interaction summary](../agents/SDDInteractionSummary.md) are updated, alongside
required acceptance and review evidence. The summary links specifications, decisions,
plan tasks, human approvals, corrections, verification and per-task logs. Maintain one
summary per feature under logs/; keep the running decision record for cross-task handoff.

The [PR template](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/.github/PULL_REQUEST_TEMPLATE.md) asks for those links and separates
intermediate task completion from feature completion. Intermediate PRs identify any
outstanding documentation owner/task rather than asserting complete feature delivery.

### Documentation ownership and review

The [common role contract](../agents/AgentSpecifications.md#documentation-ownership)
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

The [exercise report](../agents/WorkflowExercise.md) records the isolated cart-total
exercise, including test-first handoffs, independent documentation/security reviews,
a deliberately introduced failure, explicit human approval and one repair cycle.
The final suite passed 24/24 tests; no application source/tests were added to MP3.

Stale current counters in the plan/SDD summary were caught and corrected. Keep those
current values aligned at handoffs while preserving historical stage evidence.
Workers used generic dispatch with saved role instructions; native loading and
broader application/runtime properties remain outside this exercise's evidence.

### Assistant handoff

Use feature summaries and task logs for the current stopping point, local changes,
approvals still needed and verification limits. Markdown workflow roles remain the
reference; client-specific configuration loading must be checked in the receiving environment.

### MP3 tracker operation

Use [tracker instructions](../agents/issue-tracker.md) and [triage labels](../agents/triage-labels.md)
for the MP3 repository, stage/flag meanings, parent completion gates and branches.
Workflow approvals and smoke-test evidence are already recorded. The user selected issues and labels on 2026-10-17 and deferred the board.
No additional Projects permission is needed. Issue #6 records publication evidence for the
tracker documentation.

### Guide page organisation

Keep UserGuide.md and DeveloperGuide.md as brief landing pages. Put feature details
in their topic pages and register new pages in `_markbind/layouts/default.md`.
Write guides around how the application works and how to develop or test it; keep
per-task commands, outcomes, reviews and acceptance history in `logs/`.
The [LLM implementation guide](LLMImplementation.html) follows this structure.
