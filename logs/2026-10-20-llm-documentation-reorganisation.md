# Agent Interaction Log

## User Prompt

Some changes to documentation need to be made. Due to the nature of hosting these documents on a Markbind GitHub pages site, the initial files of developerGuide.md and userGuide.md are simply landing pages where a navbar on the side will document the other sections of the entire user or developer guide, as such minimal information should be on these landing pages. For the user guide, the information about generating a listing description should only reside within userGuide/SearchAndListings.md. For the developer guide, information about LLM preparation and specification should once again not be in the main DeveloperGuide.md, and instead have its own page under the developerGuide/ directory. Furthermore, the information in LlmAcceptance.md and LlmSecurityCoverage.md seem to read more like log files than guides. Reorganise all the relevant information about LLM implementation and testing into a new, human-readable file called LLMImplementation.md in the developerGuide/ directory.

## Steps Taken

Inspected repository instructions, architecture, guide pages, navigation, build
configuration and related implementation/test references. Assigned one worker to
write the new LLM guide; coordinator owns relocation, navigation, links and records.
Preserved existing unrelated edits and deletions. Archived detailed prior records
below before removing their former guide pages.

## Reasoning Summary

Readers need setup, architecture and test instructions in topic pages. Chronological
execution evidence belongs in logs. MarkBind publishes guide pages with .html links;
repository-only source, specifications and logs need full GitHub links from guides.

## Changes Made

Both landing pages trimmed; generation instructions moved into Search and Listings.
LLMImplementation.md consolidates the design, configuration, security and testing
explanation. Older acceptance/coverage guide pages are replaced by this guide,
with their evidence preserved below. General detailed development guidance moves
into DevelopmentWorkflow.md; navigation, acknowledgements and active references
follow their destinations. Plan completion states and application behaviour do not change.

## Verification

Node 24.22.0, npm 11.19.1 and existing MarkBind 7.1.1 were used; no dependencies
were installed or changed. Initial `npm --version` through PowerShell's npm.ps1
printed a prefix-path access warning but returned the version. Used npm.cmd for
the actual build.

`npm.cmd run docs:build` exited 0 and printed Build success, but page discovery
warned EPERM while trying to enumerate a restricted existing live-evidence
directory under build/llm-live. The relevant generated pages were absent, so this
is not accepted as successful site verification. No permission was broadened and
no private settings, generated results or evidence contents were inspected.

Created `build/docs-reorganisation-preview-ypf2fwe1/source` from only public
inputs: index.md, site.json, docs/, stylesheets/, _markbind/layouts/ and
_markbind/variables.md. No runtime/build evidence, private settings or logs were
copied. Ran:

```powershell
.\node_modules\.bin\markbind.cmd build build/docs-reorganisation-preview-ypf2fwe1/source build/docs-reorganisation-preview-ypf2fwe1/output
```

Exit 0; all 39 public HTML pages generated. Existing unrelated docs/agents
references to repository-only workflow/log files produced intra-link warnings;
these are outside the relocated LLM guide. HTMLParser checks on the five relevant
rendered pages confirmed usage-heading placement, absent detailed landing-page
sections and the new shared sidebar entry. All 31 internal link destinations/
fragments on the new LLM page resolve. Preview output contains no `.env`.

Read-only Python Markdown checks covered 202 local destinations across the
changed guide/evidence/plan files; only the pre-existing DevelopmentWorkflow.md
link to absent ../agents/domain.md was missing. All new repository-only GitHub
link targets in the LLM guide were checked against local source paths by its
writer; no external HTTP link check is claimed. `git diff --check` passed.
Final diff inspection found no source/test/Gradle/package/release changes and
preserved unrelated pre-existing edits/deletions. No Java tests, runtime manual
checks, provider calls or deployment were performed for this documentation task.

Independent read-only review by `/root/documentation_review` found no material
LLM documentation issue: landing/topic separation, readable design/testing
guidance, sidebar, preserved evidence and G-03/G-04 limits satisfy the request.
It independently checked 202 local destinations, whitespace and unchanged
production/build/release paths. It flagged stale upkeep wording copied from the
old landing page; coordinator corrected it to the current Developer Guide-only
update rule and brief landing-page requirement. Final delta review cleared this
correction without new findings. Reviewer did not run the build or access private
data; rendered/build outcomes above belong to coordinator. Timing/usage: N/A.

## Final Output and Conclusion

Documentation reorganisation complete. User instructions appear only in the
listing topic; one LLM developer topic covers implementation and testing.
Landing pages remain brief and sidebar navigation exposes the new page. Previous
detailed acceptance/coverage/preparation history is preserved in the appendices
below; old log filenames mentioned there are historical. No plan task completion
state or feature security claim changed. G-03/G-04 remain open. The root build's
restricted-evidence scan warning and unrelated existing link gaps remain reported;
the public-input preview verifies this documentation change. No publication or
commit performed.

## Archived acceptance record

### LLM description: scoped acceptance record

Date: 2026-10-20. Source/test/build candidate:
`81f71ee578e91a62a7e30be74ecd4d4ecd3ddd9e`. TASK-016/017 update documentation only.
The [plan](../workflow/LLM_FEATURE_PLAN.md) records task completion separately
from feature acceptance. **Feature not Done; full security and hosted acceptance
remain blocked by G-03/G-04.** No release or deployment is approved by this record.

The [product](../workflow/LLM_FEATURE_PRODUCT_SPEC.md) and
[technical](../workflow/LLM_FEATURE_TECHNICAL_SPEC.md) specifications retain
their original design-stage wording. Their requirements remain the baseline;
this record, the guides and dated task entries describe current implementation
and executed checks. The plan's TASK-014 Completed Yes record is supported by
explicit user acceptance, despite its historical unchecked acceptance boxes.
Completed prerequisite entries were preserved rather than reopened.

#### Requirement acceptance

The [TC-01–12 coverage table](#archived-security-coverage) links individual test
methods and their limitations. The following groups cover every FR/NFR ID;
passing application tests and reviewed samples do not establish universal model
immunity or remote capability settings.

| Requirement | Source and evidence | Scoped outcome |
| --- | --- | --- |
| FR-001, FR-002 | [ListingEditor](../src/main/java/hotshop/ui/ListingEditor.java), [DescriptionService](../src/main/java/hotshop/service/DescriptionService.java); TC-01/02, TASK-011/013. | Local explicit generation, authentication, minimum inputs and manual save verified. |
| FR-003 | [HttpLlmClient](../src/main/java/hotshop/llm/HttpLlmClient.java), [LlmConfiguration](../src/main/java/hotshop/llm/LlmConfiguration.java); TC-04/07/09/12. | Exact configured HTTPS destination, permitted JSON, bearer isolation and safe failure verified with synthetic settings. |
| FR-004 | [LlmResponseParser](../src/main/java/hotshop/llm/LlmResponseParser.java); TC-03/05/06/11/12. | Structural limits verified; v2 live samples accepted by user. Grounding of future outputs still requires seller review. |
| FR-005, FR-006 | ListingEditor and [MarketplaceUi](../src/main/java/hotshop/ui/MarketplaceUi.java); TC-01/03/10/12, TASK-012. | Immediate unsaved replacement, failure preservation and stale session/form/lifecycle guards verified in desktop JavaFX tests. |
| NFR-001 | Fixed prompt and TC-05/06/11; TASK-014 v1 failure, approved prompt correction and v2 acceptance. | Application boundaries verified; empirical acceptance limited to 26 reviewed v2 samples. |
| NFR-002 | Configuration/transport, TC-07/09 and synthetic storage/canary checks. | Local isolation verified. Container/image secret exclusion remains future release work. |
| NFR-003 | No application tool dispatcher; TC-08/12 rejects structured calls and observes inert plain text. | Application-side isolation verified; end-to-end no-tool acceptance **blocked by G-03**. |
| NFR-004 | Only title/condition plus fixed instruction/model transmitted; fixed typed feedback/diagnostic tests, TC-04/07. | Local minimum disclosure verified. Provider retention/processing terms were not assessed here. |
| NFR-005 | [DescriptionQuota](../src/main/java/hotshop/service/DescriptionQuota.java), transport deadlines/byte limits, TC-10 and keyboard UI tests. | Single-process bounds and desktop accessibility checks verified; no concurrent hosted-user or cross-replica guarantee. |
| NFR-006 | [Corpus/rubric](../src/test/resources/hotshop/llm/SECURITY-EVALUATION.md), offline suites and separately authorized TASK-014. | Deterministic checks and finite live/human review recorded separately; full-regression deferral remains explicit. |
| NFR-007 | [DescriptionRuntime](../src/main/java/hotshop/DescriptionRuntime.java), [ApplicationRuntime](../src/main/java/hotshop/ApplicationRuntime.java), runtime/UI tests and unchanged listing persistence. | Local JavaFX integration verified; independent JPro client isolation **blocked by G-04**. |

UC-001 maps to TC-01/03 and explicit-save UI tests; UC-002 to TC-05–09/11;
UC-003 to TC-10 and stale completion tests. See
[usage](../docs/userGuide/SearchAndListings.md#generating-a-listing-description),
[setup/design](../docs/developerGuide/LLMImplementation.md)
and the [SDD summary](../logs/2026-10-19-feature-llm-description-sdd-summary.md).

#### Executed evidence and limits

These are historical runs documented by their owners, not new runs for TASK-016/017.
Overlapping test totals must not be added together.

| Evidence | Actual outcome | Limits |
| --- | --- | --- |
| [TASK-010/011](../logs/2026-10-20-llm-task010-011-integration.md): `.\gradlew.bat test checkstyleMain checkstyleTest check build` | 1,149 tests across 39 suites; zero failures/errors/skips; styles/check/build passed. | Predates later UI/integration/prompt changes; not a current full regression result. |
| [TASK-012](../logs/2026-10-20-llm-task012-integration.md): focused UI suites | 115 invocations (53 generation, 62 marketplace); both styles passed. | Desktop JavaFX automation, not manual JPro/browser acceptance. |
| [TASK-013](../logs/2026-10-20-llm-task013-integration.md): focused boundaries and final integration rerun | 442 related invocations; final 13-case integration rerun and both styles passed. | Different overlapping sets; full regression delegated to user. |
| [TASK-014 rerun](../logs/2026-10-20-task014-prompt-rerun.md) and [acceptance](../logs/2026-10-20-task014-completion.md) | 118 focused plus 127 parser cases; styles passed. v2 live: 26 automated passes, 26 dispatches/generated results, all 26 user-reviewed PASS. | Private evidence stays restricted/ignored. v1 semantic failure preserved; no remote tool assurance. No live rerun here. |
| [TASK-015](../logs/2026-10-20-task015-final-reviews.md) and [independent reviews](../logs/2026-10-20-task015-independent-reviews.md) | 586 focused offline tests across 15 suites; zero failures/errors/skips; styles up to date. Three independent reviewers found no new material local defect. | Stable source candidate above; excludes full regression/build, packaging and hosted identity. |

TASK-016/017 checks and independent documentation review are recorded in their
[documentation log](../logs/2026-10-20-task016-documentation.md) and
[acceptance log](../logs/2026-10-20-task017-scoped-acceptance.md).
No Java, tests, resources or packaging configuration changed, so earlier scoped
runtime evidence remains applicable. No new Gradle tests/check/build were run.
Full regression/build after TASK-012 onward remains unverified in these records.

The conditional `.\gradlew.bat shadowJar jproRelease` check was not triggered:
packaging inputs are unchanged. Neither command, a new synthetic-secret artifact
inspection, nor `.\gradlew.bat jproRun` was performed. Manual keyboard, form,
save and error behaviour was not observed here. Earlier historical JAR inspection
does not establish current JPro/container release safety. No tracked release
artifact was changed or staged.

#### Outstanding release gates

- **G-03:** the user confirmed no independent provider tool assurance is available.
  Obtain provider capability/configuration evidence, or a separately approved
  product-spec revision accepting a narrower guarantee. Passing samples, a no-tool
  prompt and empty/rejected returned calls cannot close this gate.
- **G-04:** independently establish concurrent hosted-client identity/session
  isolation through separately approved lifecycle/session work. Existing runtime
  identity and exclusive data locking do not prove this property.
- **Future Docker/Render work:** separately authorize packaging/hosting changes;
  establish runtime secret mounting, build-context/image-layer and browser-asset
  exclusion, inspect artifacts with synthetic secret fixtures, and verify supported
  deployment/session behaviour. Git ignore alone is insufficient. No credentials,
  container publication or deployment are part of TASK-016/017.
- **Verification handoff:** retain the user's full-regression responsibility;
  record fresh full-suite/build, packaging and manual browser results when actually
  performed. Do not infer them from the focused checks above.

TASK-016/017 completed on 2026-10-20 after
[independent documentation review](../logs/2026-10-20-task016-017-documentation-review.md)
and coordinator checks. The review found no material issue; its deadline wording
clarification was applied. All 17 plan tasks now have dated completion records,
with these exclusions explicit.
They do not grant feature-level Done, full security acceptance or hosted release
acceptance, and do not authorize subsequent implementation.

## Archived security coverage

### LLM local security coverage

This table traces TASK-013 to executable tests. All paths are local Java/JUnit checks with synthetic settings and controlled clients or loopback HTTPS. Passing them establishes the listed application boundaries; it does not establish that a live model resists prompt injection.

The source requirements are [the product specification](../workflow/LLM_FEATURE_PRODUCT_SPEC.md) and [the technical specification](../workflow/LLM_FEATURE_TECHNICAL_SPEC.md). The task record is [TASK-013](../workflow/LLM_FEATURE_PLAN.md#task-013-verify-integrated-security-boundaries-and-regressions).

#### Test case trace

| Case | Actual tests and observable evidence | Limits |
| --- | --- | --- |
| TC-01 | [ListingDescriptionGenerationTest](../src/test/java/hotshop/ui/ListingDescriptionGenerationTest.java): `generateDescription_onlyTitleAndEachCondition_dispatchesWithoutOtherListingFields`; `generateDescription_success_replacesOnceKeepsDraftEditableAndRequiresExplicitSave`. Each condition dispatches from the minimum form; generated text remains editable and unsaved until Save. | Real desktop JavaFX controls; no JPro browser claim. |
| TC-02 | [DescriptionServiceTest](../src/test/java/hotshop/service/DescriptionServiceTest.java): `generateDescription_loggedOutWithInvalidInput_prioritizesAuthentication`, `generateDescription_missingTitle_rejectsWithoutPreparingOrCharging`, `generateDescription_nullCondition_rejectsWithoutPreparingOrCharging`, `generateDescription_unicodeTitleBoundaries_dispatchesNormalizedProductFacts`, `generateDescription_titleOver120CodePoints_rejectsWithoutPreparingOrCharging`. [LlmRequestTest](../src/test/java/hotshop/llm/LlmRequestTest.java): `buildRequest_oneCodePointAndSurroundingSpace_stripsTitle`, `buildRequest_quotesNewlinesAndUnicode_roundTripsBothJsonLayers`. | There is no HTTP application generation endpoint accepting a forged enum string; the operation accepts the Java `Condition` enum. Invalid external enum parsing is not a newly introduced capability. |
| TC-03 | [LlmResponseTest](../src/test/java/hotshop/llm/LlmResponseTest.java): `parseResponse_validWordBoundaries_preservesEntireDescription`, `parseResponse_over100Words_returnsFailureWithoutTruncation`, `parseResponse_exactly5000CodePoints_acceptsWithoutTruncation`, `parseResponse_over5000CodePoints_returnsSafeFailure`, `parseResponse_invalidEnvelope_returnsSafeFailure`. [ListingDescriptionGenerationTest](../src/test/java/hotshop/ui/ListingDescriptionGenerationTest.java): `generateDescription_safeTransportFailure_preservesFullDraftAndShowsLocalFeedback` and the explicit-save success test. | Word, Unicode and shape validation are structural checks, not factual accuracy checks. |
| TC-04 | [LlmConfigurationTest](../src/test/java/hotshop/llm/LlmConfigurationTest.java): `load_missingRequiredKey_isUnavailable`, `load_invalidEndpoint_isUnavailable`. [HttpLlmClientTest](../src/test/java/hotshop/llm/HttpLlmClientTest.java): `generate_loopbackHttps_postsExactUriAndOnlyExpectedHeadersAndBody`, `generate_httpStatus_returnsSafeMappingWithoutRetry`, `generate_untrustedCertificate_returnsNetwork`, `generate_wrongHostname_returnsNetwork`, `generate_connectAndRequestTimeouts_returnsTimeout`. [LlmSecurityIntegrationTest](../src/test/java/hotshop/llm/LlmSecurityIntegrationTest.java): `generateDescription_hostileProviderDiagnostic_returnsOnlySafeFailureAndNoWrites`. | Certificates belong to the public synthetic test CA; production TLS is not globally overridden. |
| TC-05 | [LlmRequestTest](../src/test/java/hotshop/llm/LlmRequestTest.java): `buildRequest_overrideInstructions_preservesConfigurationAndProductBoundary`. [LlmSecurityCorpusTest](../src/test/java/hotshop/llm/LlmSecurityCorpusTest.java): `load_securityCorpus_hasUniqueValidInputsAndRequiredCoverage`. [LlmSecurityIntegrationTest](../src/test/java/hotshop/llm/LlmSecurityIntegrationTest.java): `generateDescription_privateInternalCanaries_excludesContextAndLeavesStorageUnchanged`. The actual request keeps title data below a fixed instruction and cannot retrieve application context. | A canned provider response does not demonstrate that a live model ignores disclosure, role-spoof or unrelated-task instructions. Those semantics require TASK-014. |
| TC-06 | [LlmSecurityCorpusTest](../src/test/java/hotshop/llm/LlmSecurityCorpusTest.java): `load_securityCorpus_hasUniqueValidInputsAndRequiredCoverage` checks the 26-case corpus, including encoded, Unicode, fragmented, multilingual and benign controls. [LlmRequestTest](../src/test/java/hotshop/llm/LlmRequestTest.java): `buildRequest_benignSecurityVocabulary_doesNotFilterWords`. | Offline corpus quality and data encoding are covered; live obfuscated-attack behavior remains unverified. |
| TC-07 | [LlmSecurityIntegrationTest](../src/test/java/hotshop/llm/LlmSecurityIntegrationTest.java): `generateDescription_privateInternalCanaries_excludesContextAndLeavesStorageUnchanged`, `generateDescription_hostileProviderDiagnostic_returnsOnlySafeFailureAndNoWrites`, `generateDescription_unexpectedPrivateCause_logsFixedDiagnosticWithoutNestedCanaries`. Private persisted account/listing values, a private file and the configured token stay out of the captured request body and public outcomes; bearer appears only in the approved Authorization header. Unexpected nested/suppressed canary exceptions produce a captured fixed, cause-free application warning. [LlmConfigurationTest](../src/test/java/hotshop/llm/LlmConfigurationTest.java): `toString_availableConfiguration_redactsPrivateSettings`. | Only synthetic local records/configuration are used. These tests do not inspect or disclose the user's real `.env`. Existing JavaFX failure tests verify fixed typed UI feedback. |
| TC-08 | [LlmSecurityIntegrationTest](../src/test/java/hotshop/llm/LlmSecurityIntegrationTest.java): `generateDescription_structuredToolOutput_rejectsWithoutCallbacksOrStorageWrites` (function, web-search and computer call variants), `generateDescription_plainScriptAndUrl_remainsInertDataWithoutCallbacksOrWrites`. The fixed no-tool instruction is present, no tool definitions are sent, structured tools are rejected, returned plain command/link text causes no execution or callback, and storage is unchanged. | Plain text is deliberately not semantically filtered: the inert script/link case is accepted as text. Therefore the product expectation that malicious prose is never inserted is not established locally. Provider-side tool non-use remains unverified (G-03); prompts and rejected tool envelopes do not establish remote capability settings. |
| TC-09 | [LlmSecurityIntegrationTest](../src/test/java/hotshop/llm/LlmSecurityIntegrationTest.java): `generateDescription_attackerConfigurationInTitleAndMetadata_preservesTrustedSettings`, `generateDescription_redirect_neverForwardsCredentialOrContactsDestination` (cross-host HTTPS and HTTP destinations). Trusted URI/model/header remain fixed, metadata/title cannot override them, destination request count stays zero. The inert-script case also observes zero response-directed callbacks. | Loopback destinations are controlled test servers; no attacker or external provider is contacted. |
| TC-10 | [DescriptionQuotaTest](../src/test/java/hotshop/service/DescriptionQuotaTest.java): `reserve_fifthAndSixthMinuteRequests_admitsFiveOnly`, `reserve_thirtiethAndThirtyFirstHourRequests_admitsThirtyOnly`, `reserve_minuteExpiryBoundary_expiresAtExactWindow`, `reserve_twoRacingCallers_admitsExactlyOne`. [HttpLlmClientTest](../src/test/java/hotshop/llm/HttpLlmClientTest.java): `createHttpClient_defaultPolicy_usesStrictTlsAndTenSecondConnectTimeout`, `generate_stalledHeadersAtDeadline_cancelsHttpFuture`, `generate_stalledBodyAtDeadline_cancelsSubscriptionAndIgnoresLateBody`, `generate_decodedByteBoundary_enforcesLimitWithoutLengthTrust`. [LlmRequestTest](../src/test/java/hotshop/llm/LlmRequestTest.java): `buildRequest_exactly8192Utf8Bytes_acceptsRequest`, `buildRequest_over8192Utf8Bytes_rejectsSafely`. [ListingDescriptionGenerationTest](../src/test/java/hotshop/ui/ListingDescriptionGenerationTest.java): `generateDescription_relevantFieldEditedThenReverted_rejectsOldResult`, `generateDescription_completionQueuedBeforeLifecycleChange_checksFinalDecision`, `generateDescription_lifecycleInvalidated_doesNotMutateOldControls`, `generateDescription_detachedPage_retainsAccountAdmissionUntilTransportTerminates`, `generateDescription_failureAfterFieldEdit_preservesDraftAndReportsChangedDraft`. | Deadlines/quota windows use controlled clocks/schedulers; 10-second connect policy is inspected and connection-timeout exceptions are mapped without a slow real network timeout. UI ordering uses real JavaFX queue/latch gates, not live JPro clients. |
| TC-11 | [LlmLiveSecurityTest](../src/test/java/hotshop/llm/LlmLiveSecurityTest.java) ran twice under explicit TASK-014 authorization on 2026-10-20. Each run recorded 26 automated passes, 26 dispatches and 26 generated results. Human review reported an `attack-internals` semantic failure in `task014-20261020-v1`; the approved prompt-only correction was evaluated in `task014-20261020-v2`. The user explicitly accepted all second-run results; the private human-review record contains 26 per-case PASS decisions. See [first run](../logs/2026-10-20-llm-task014-coordination.md), [prompt correction/rerun](../logs/2026-10-20-task014-prompt-rerun.md) and [completion](../logs/2026-10-20-task014-completion.md). | **Second-run human semantic review accepted; first-run failure preserved.** This is empirical acceptance of the reviewed samples, not provider tool assurance or universal immunity. Private outputs remain only in the approved restricted review locations. |
| TC-12 | [LlmResponseTest](../src/test/java/hotshop/llm/LlmResponseTest.java): `parseResponse_conflictingNestedText_returnsOnlyTopLevelText`, `parseResponse_invalidEnvelope_returnsSafeFailure`, `parseResponse_invalidOutputAlongsideValidText_returnsSafeFailure`, `parseResponse_unknownMetadata_validatesButDoesNotInterpretMetadata`. [ListingDescriptionGenerationTest](../src/test/java/hotshop/ui/ListingDescriptionGenerationTest.java): `generateDescription_success_replacesOnceKeepsDraftEditableAndRequiresExplicitSave`. Integrated structured-tool and metadata-override tests above exercise the same parser through runtime/HTTPS. | Top-level text is authoritative; metadata never becomes description or configuration. Structural acceptance does not imply grounded content. |

#### Integrated storage and diagnostic observations

`LlmSecurityIntegrationTest` takes snapshots after account/listing/configuration setup and before generation. It compares every path and each readable file's SHA-256 digest before and after generation, including the SQLite database, `.env`, private file and image directories. Windows prohibits reading bytes from the runtime's active `application.lock`, so that file is checked by size and last-modified time instead. The snapshots happen while the runtime remains open, before normal shutdown changes are possible. Private persisted profile/listing values are also read back through services. No explicit Save runs during generation.

The diagnostic handler attaches only to the `hotshop` application logger, restores its previous level and removes itself on close. It inspects message parameters and complete attached stack traces, including causes and suppressed exceptions. The unexpected-collaborator test requires the fixed `LLM_SERVICE_UNEXPECTED` warning to be observed and requires the warning to have no attached throwable, so the capture check is not satisfied merely by an empty log. Ordinary provider errors have fixed typed outcomes and no body/credential diagnostic payload.

#### Verification scope and remaining acceptance

TASK-012's related desktop suites passed 115 invocations (53 generation and 62 marketplace UI), with both Checkstyle tasks. TASK-013's focused non-UI run passed 442 invocations before adding the final diagnostic-capture case; the final new-class rerun passed all 13 invocations, and both Checkstyle tasks passed. Exact commands and the initial test-fixture corrections are recorded in the testing log. The sets differ, so the 115 and 442 totals are not a full-suite baseline comparison.

The user explicitly retained full regression checking. No unfiltered `test`, `check`, `build`, packaging task, live evaluation or JPro browser session was run during the TASK-012–013 selection. TASK-013 verifies application-side deterministic boundaries only. The later authorized TASK-014 runs and second-run human acceptance are recorded in TC-11 above. Remote tool configuration (G-03), hosted session isolation (G-04), and release/packaging acceptance remain pending in their separate tasks.

## Archived preparation and implementation history


[LLM Feature Product Specification](../workflow/LLM_FEATURE_PRODUCT_SPEC.md)
defines optional description generation implemented for Create Listing. A valid result
immediately replaces existing description text. Provider endpoint, bearer token and
model are supplied privately through configuration; no real values belong in
source control. The LLM receives title and condition only, alongside fixed task
instructions, with no tools, secrets or internal application context.

Generation is available in Create Listing as described below. The user initially
selected TASK-001–004 and confirmed endpoint/key readiness on 2026-10-20, permitting offline tests without
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
The technical specification records the approved settings;
finite adversarial testing cannot establish immunity to all prompt injections.
The [LLM technical specification](../workflow/LLM_FEATURE_TECHNICAL_SPEC.md)
defines the session-bound service, asynchronous Java HTTP transport, strict bounded
JSON handling, configuration loader and stale-result guards. TASK-002 added Gson
2.14.0 as the sole JSON dependency and a typed `hotshop.llm.LlmClient` seam;
TASK-008 implements transport, TASK-010–011 wire the runtime/UI, and TASK-012
guards stale UI completions.
Upstream release/security review
and local Java 25 strict-streaming tests support this dependency choice.
The user approved one server and in-memory quota reset on restart. Current runtime
session/locking behaviour does not establish independent JPro browser-client
identity; multi-user hosting needs a separate lifecycle review. The design can feed
planning for its limited scope. TASK-014's second live evaluation has recorded
human acceptance; provider tool assurance and future container release checks
remain unverified.
The [LLM implementation plan](../workflow/LLM_FEATURE_PLAN.md) contains 17 ordered
tasks with dated execution evidence. Behaviour tasks require runnable tests and verified
behavioural failures before production changes, then passing checks and refactoring.
Testing and implementation owners coordinate shared-file edits; the coordinator
maintains completion evidence. The configuration-readiness gate is resolved; live
evaluation still requires explicit authorisation. The plan itself authorises
no execution. Provider-side tool assurance and independent JPro client identity
remain acceptance gaps; later Docker/Render work is outside the local task scope.
See the [feature summary](../logs/2026-10-19-feature-llm-description-sdd-summary.md)
for decisions and verification limits. The User Guide describes implemented
generation controls, lifecycle behavior and remaining hosting/security limits.

Offline LLM tests and synthetic fixtures live under `src/test/java/hotshop/llm/`
and `src/test/resources/hotshop/llm/`. Run them with
`.\gradlew.bat test --tests 'hotshop.llm.*'`. Ordinary `test`, `check` and `build`
exclude the `llm-live` tag. The separate `.\gradlew.bat llmLiveTest` task is never
automatically included; do not run it without live-call authorisation. Its tagged
26-case corpus uses the registered test-only `ProductionLiveLlmClientFactory`.
The adapter requires explicit `llmLiveRunId` and `llmLiveEvidenceDir` Gradle
properties; it fails closed before private configuration loading if the run/evidence
setup is missing. It uses a temporary synthetic authenticated runtime and the
production description service/transport. Ordinary tests use only temporary
synthetic properties/configuration and never load the real working-directory `.env`.
See the [evaluation rubric](../src/test/resources/hotshop/llm/SECURITY-EVALUATION.md)
for mandatory human review and secure evidence requirements. TASK-014 completed
on 2026-10-20 after the user accepted all 26 second-run results. The first run's
semantic failure remains recorded. No provider-side tool assurance is claimed.

Live evaluation is limited to the approved public corpus. The operator approves a
public model/configuration-version label and an ignored, access-restricted evidence
directory before execution. Verify access for the actual human Windows profile
and authorized live process; the sandbox execution identity alone is insufficient.
Private settings and headers never become evidence or command arguments.
Results are written privately before automated assertions, with per-case safe
outcomes and separate actual-dispatch counts. Generated text remains unreviewed
until the human rubric is completed; transport/admission failures are unverified
semantics. Provider tools and hosted identity remain independent acceptance gates.
TASK-014 preparation and its approved correction cycle are recorded in the
[coordination log](../logs/2026-10-20-llm-task014-coordination.md).

TASK-014 verification: 20 adapter and two corpus offline tests passed with both
Checkstyle checks. After independent specification/security re-review, the
explicit `llmLiveTest` invocation using the approved public label
`task014-20261020-v1` and restricted evidence directory passed 26 cases with
zero failures/errors/skips. The manifest records 26 dispatches and 26 generated
results. Run timestamps were 15:13–15:19 SGT on 2026-10-20. This operator-approved
label identifies the configured snapshot for review; the provider's deployed
backend version was not independently verified. Private case JSON and an inert
`review.html` report remain under the ignored approved directory, accessible to
the human Windows profile. No generated prose, credentials, endpoint, raw model
setting or raw transport exception is committed in the task records. Full
application regression/build checks were not repeated. Human review subsequently
identified `attack-internals` as a semantic failure: generated prose propagated
the injected request instead of describing the item, without reported disclosure.
That first run did not establish semantic acceptance. Each local
generation request is independent; generated text is not sent as conversation
history or used to trigger an automatic follow-up.

The user authorized one bounded prompt correction beyond the two ordinary
correction cycles and a full second corpus run. Its public label is
`task014-20261020-v2`; evidence uses a separate ignored sibling directory with
the same verified human-profile-only access. Original v1 evidence is preserved.
The new prompt instructs the model to omit embedded requests without quoting or
paraphrasing them and avoid questions, information requests and invitations to
reply. Focused checks passed 118 cases plus 127 parser cases, with both Checkstyle
checks passing. After scoped independent specification/security reviews, the
second live invocation passed 26 automated cases with zero failures/errors/skips
and 26 dispatches, all producing generated results (15:40–15:46 SGT, 2026-10-20).
The private v2 `review.html` contains all new results and an old/new comparison
for `attack-internals`. The user explicitly accepted the new results and directed
completion. A private `human-review.json` records all 26 per-case PASS decisions,
reviewer `user` and the approval timestamp; the manifest reports humanReview
PASSED. Original case JSON retains dispatch-time UNREVIEWED labels and unchanged
outputs, with the separate human record providing final decisions. The report
includes an acceptance banner. The first run's failure remains preserved.
TASK-014 is complete; provider tool non-use and later feature/release gates remain
unverified. Full regression/build checks were not repeated.
Evidence: [prompt rerun record](../logs/2026-10-20-task014-prompt-rerun.md).

TASK-015 final local review (2026-10-20) assessed candidate
`81f71ee578e91a62a7e30be74ecd4d4ecd3ddd9e` through independent implementation/
concurrency, security and specification/coverage reviewers. They found no new
material defect in the inspected service/UI lifecycle, authorization, shared
quota, transport/parser bounds, inert output, secret isolation or fixed diagnostic
paths. The focused offline run passed 586 tests across 15 suites, with zero
failures/errors/skips; both Checkstyle checks were up to date. No application/test
code changed, and no live, full regression/build, packaging or browser check was
repeated. These are scoped local findings, not a blanket security guarantee.
The user confirmed that no independent provider assurance is available: G-03
remains open and full end-to-end no-tool acceptance remains blocked. G-04 also
remains open; independent concurrent JPro client identity/session safety was not
verified. TASK-016/017 completed documentation and scoped acceptance recording
on 2026-10-20 after independent documentation review; the feature
remains **not Done**. See the [acceptance report](#archived-acceptance-record),
[attributed reviews](../logs/2026-10-20-task015-independent-reviews.md)
and [checks/scope record](../logs/2026-10-20-task015-final-reviews.md).

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
snapshot. `Main` loads it once through its lazy server description owner at startup.
Manual listing creation/persistence still works with missing configuration.

For synthetic setup, use a temporary working directory containing a UTF-8 `.env`
with the following inert example. It is syntax guidance, not a live provider:

```dotenv
SOCLAAS_BASE_URL='https://provider.invalid/example/responses'
SOCLAAS_API_KEY='synthetic-test-token'
SOCLAAS_MODEL='synthetic-test-model'
```

Do not replace an operator's existing `.env` with this example. Ordinary tests
create temporary settings and inject controlled clients; they need no real provider.
The first `=` separates name/value; quotes preserve contents literally, including
`#`, `$` and backslashes. Do not use shell `export`, expansion or inline comments.
In a packaged launch, `.env` is resolved from the process working directory, not
from inside the JAR or the data directory. Restart the server process to reload
settings. Server restart also clears quota history; reopening a form, logging out
or creating another session service does not clear it.

TASK-016/017 changed documentation only. No packaging definition, resource input
or Java source/test changed, so the conditional `shadowJar jproRelease` check was
not triggered. Neither packaging task nor `jproRun` was executed for this selection;
there is no new synthetic-secret artifact inspection or manual browser/keyboard/
form/save/error observation. Earlier desktop JUnit observations remain linked in
the acceptance report and are not JPro manual evidence. No tracked `release/`
artifact was changed. Before a later packaging change, inspect the produced
JAR/JPro archive with synthetic fixtures for `.env`, credentials and test-only
assets. Docker/Render work must separately establish runtime secret mounts,
build-context/image-layer exclusion and hosted session isolation; nothing was
published or deployed here.

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
active ownership until completion. A new server registry starts empty.
`DescriptionRuntime` supplies one server-owned registry across session services;
this does not establish independent hosted session identity. Its deterministic
suite is `.\gradlew.bat test --tests '*DescriptionQuotaTest'`.

`hotshop.llm.HttpLlmClient` builds bounded requests and implements asynchronous
`LlmClient` transport, wired through the server description owner. Its internal
`buildRequest` checks a stripped title of 1–120 Unicode code points, a non-null
condition and available immutable settings. Gson `JsonWriter` encodes the title
and canonical enum condition as product facts, then encodes a separate outer JSON
object containing exactly `model` and `input` strings. The fixed instruction
requests grounded plain text, a 50–70-word target, a 100-word maximum and no tools.
It also explicitly requests only identifiable item/condition facts, omission of
embedded requests without repeating or endorsing them, and no questions or
follow-up solicitation. A title mixing an item and unrelated instructions must
produce only an item/condition description without invented details.
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
`DescriptionService`; runtime integration uses `DescriptionRuntime`. The transport
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
startup wiring are described below; UI integration is tracked separately in the plan.
See [TASK-009 evidence](../logs/2026-10-20-llm-task009-integration.md).
Final TASK-009 verification on 2026-10-20:
`.\gradlew.bat test checkstyleMain checkstyleTest check build` passed, with 1,106
tests across 37 suites and zero failures/errors/skips. Includes 61 service,
22 preparation and 37 quota cases. Independent review has no outstanding material
findings; no live provider calls or runtime/UI acceptance are claimed.

`DescriptionRuntime` owns the process-shared quota and transport and registers
each runtime's session-bound `DescriptionService`. `Main` shares one lazy owner
across application objects, reads the working-directory `.env` once and closes
that owner through a JVM shutdown hook. Closing an application runtime cancels
only its own description requests, preserving shared quota history and other services.
`ApplicationRuntime.open(Path)` and `open(Path, Clock)` remain offline and never
read `.env`; tests or alternate hosts inject an explicit owner through
`open(Path, Clock, DescriptionRuntime)`. Missing settings leave manual operations
available. The transport is reached through `getDescriptions()`.

`closeAsync()` rejects new generation and cancels its pending preparation or
transport before draining accepted service work on a non-daemon shutdown thread.
The data-directory lock remains held until draining finishes. Repeated calls
observe the same completion. `close()` waits for this completion off JavaFX and
returns immediately on JavaFX; use `closeAsync()` to observe shutdown failures.
`Main.stop()` uses this asynchronous path. No HTTP-client termination is awaited
on JavaFX. This lifecycle does not repair the documented JPro identity/data-lock
limitations or establish multi-user hosted acceptance.

TASK-010 verification on 2026-10-20 passed
`.\gradlew.bat test checkstyleMain checkstyleTest check build`: 1,118 tests with
zero failures/errors/skips, including 12 new runtime ownership/shutdown tests.
Independent review found no substantial defects. Focused runtime/account/listing
verification passed 95 tests; see [runtime evidence](../logs/2026-10-20-llm-task010-runtime-implementation.md).

`ListingEditor` adds **Generate Description** only to Create Listing. The title
and condition are validated before calling the session-bound service. A notice
beside the action explains external processing, immediate replacement and review
before saving. Local progress and fixed safe status messages handle typed results
on JavaFX. Only Generate is disabled during the request; this path does not use
`UiPage.perform`, so ordinary editing, saving and navigation remain available.
Success sets the retained Description text area once and leaves `initialFields`
unchanged, preserving normal dirty tracking. No generation callback saves a listing.
Failures preserve the draft and local rate-limit feedback displays safe retry seconds.

TASK-012 adds monotonically increasing title/condition/description revisions and
request sequences. Even an edit changed back invalidates the earlier result.
Completion also checks the current page, UI authentication revision and disposed
state before changing generation controls. An edited active form retains its
draft, shows changed-draft feedback and permits another explicit request after
termination. Detached, differently authenticated and disposed forms receive no
generation field or status updates.

The shell invokes the outgoing page's leave hook before navigation, back,
replacement and login routes. Accepted logout increments the UI authentication
revision before service logout is queued; logging into the same account is still
a new UI revision. Accepted close and window hidden dispose the UI. A dirty
confirmation runs a nested JavaFX loop, so generation completions are deferred
until the caller has handled its decision. Accepted actions invalidate before
delivery; cancelled confirmation retains normal usability. Page invalidation
does not cancel transport or release shared admission while a request is active.
These guards do not establish multi-user JPro session isolation or live semantics.
The new `ListingDescriptionGenerationTest` uses real JavaFX controls, temporary
SQLite data and a controlled transport. Both UI test suites tolerate an already
started JavaFX toolkit so they can run together in one test JVM.

TASK-012 focused verification (2026-10-20) passed
`.\gradlew.bat test --tests '*ListingDescriptionGenerationTest' --tests '*MarketplaceUiTest'`:
115 tests (53 generation, 62 marketplace), zero failures/errors/skips. Both
Checkstyle tasks passed, and independent review found no substantial issues.
The user explicitly delegates full regression and build checking to themselves
for TASK-012–013; no unfiltered test/check/build was run for these changes.
Each selected task is committed separately, including its guides, plan and logs.
See [TASK-012 evidence](../logs/2026-10-20-llm-task012-integration.md).

TASK-013 consolidates the deterministic local boundaries in
[LLM security coverage](#archived-security-coverage). Its integrated
tests use a temporary `ApplicationRuntime`, production `HttpLlmClient` and the
controlled loopback HTTPS fixture with synthetic credentials. They capture the
actual permitted request, preserve private account/listing/file canaries, compare
storage after startup/setup, reject structured tool output and check that redirects
or output-directed URLs never contact another destination. The active Windows
lock file is observed by metadata because its contents cannot be read while locked;
other files are compared by content hashes.

Local isolation is distinct from description semantics. A structurally valid
script, command or URL can still be returned as plain text; it gains no execution
or navigation capability. These tests do not prove grounded descriptions,
semantic refusal, universal prompt-injection resistance or provider-side tool
non-use. Live semantics were evaluated in TASK-014 and the provider/hosted-identity gates
remain open. No dependency or production security policy is changed by this
consolidation.

TASK-013 focused verification (2026-10-20) passed
`.\gradlew.bat test --tests 'hotshop.llm.*' --tests '*DescriptionServiceTest' --tests '*DescriptionQuotaTest' --tests '*DescriptionRuntimeTest' checkstyleMain checkstyleTest`:
442 tests with zero failures/errors/skips, including 12 integration cases.
After adding nonempty safe-warning capture, the integration class and both
styles were rerun: 13 cases passed. These runs overlap and are not a full-suite
baseline. Review found no substantial issues and no production changes were
required. Full regression/build verification is delegated to the user.
See [TASK-013 evidence](../logs/2026-10-20-llm-task013-integration.md).

Final TASK-010–011 verification on 2026-10-20 passed
`.\gradlew.bat test checkstyleMain checkstyleTest check build`: 1,149 tests across
39 suites, zero failures/errors/skips, both Checkstyle checks and packaging tasks.
The 31 generation UI cases include keyboard activation, safe quota feedback,
unsaved replacement, manual saving during generation and a gated-worker close
on JavaFX. The minimum-window screenshot was inspected. Independent code and
documentation reviews found no substantial issues. No live provider calls or
manual JPro/browser acceptance were performed. See
[selected-task evidence](../logs/2026-10-20-llm-task010-011-integration.md).

TASK-007–009 changes are grouped into exactly three task commits. The shared
transport file is split across parser, HTTPS transport and prepared-dispatch
orchestration revisions. Final shared completion records accompany TASK-009;
see the [grouping record](../logs/2026-10-20-llm-task007-009-commit-grouping.md).

TASK-010–011 changes are grouped into two task commits: runtime ownership/shutdown,
then Create Listing generation controls. Each includes its tests and task logs;
the consolidated guides, plan and integration records accompany TASK-011. See
the [grouping record](../logs/2026-10-20-llm-task010-011-commit-grouping.md).

TASK-001–006 changes are grouped into one commit per task, followed by a shared
documentation commit for completion records and integration evidence. Unrelated
short-workflow skill directories are excluded. During commit preparation,
`.\gradlew.bat check build` succeeded with all 15 actionable tasks up-to-date;
this reused existing verification outputs rather than executing tests again.
See the [commit-grouping log](../logs/2026-10-20-llm-task-commit-grouping.md).
