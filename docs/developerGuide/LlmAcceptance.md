# LLM description: scoped acceptance record

Date: 2026-10-20. Source/test/build candidate:
`81f71ee578e91a62a7e30be74ecd4d4ecd3ddd9e`. TASK-016/017 update documentation only.
The [plan](../../workflow/LLM_FEATURE_PLAN.md) records task completion separately
from feature acceptance. **Feature not Done; full security and hosted acceptance
remain blocked by G-03/G-04.** No release or deployment is approved by this record.

The [product](../../workflow/LLM_FEATURE_PRODUCT_SPEC.md) and
[technical](../../workflow/LLM_FEATURE_TECHNICAL_SPEC.md) specifications retain
their original design-stage wording. Their requirements remain the baseline;
this record, the guides and dated task entries describe current implementation
and executed checks. The plan's TASK-014 Completed Yes record is supported by
explicit user acceptance, despite its historical unchecked acceptance boxes.
Completed prerequisite entries were preserved rather than reopened.

## Requirement acceptance

The [TC-01–12 coverage table](LlmSecurityCoverage.md) links individual test
methods and their limitations. The following groups cover every FR/NFR ID;
passing application tests and reviewed samples do not establish universal model
immunity or remote capability settings.

| Requirement | Source and evidence | Scoped outcome |
| --- | --- | --- |
| FR-001, FR-002 | [ListingEditor](../../src/main/java/hotshop/ui/ListingEditor.java), [DescriptionService](../../src/main/java/hotshop/service/DescriptionService.java); TC-01/02, TASK-011/013. | Local explicit generation, authentication, minimum inputs and manual save verified. |
| FR-003 | [HttpLlmClient](../../src/main/java/hotshop/llm/HttpLlmClient.java), [LlmConfiguration](../../src/main/java/hotshop/llm/LlmConfiguration.java); TC-04/07/09/12. | Exact configured HTTPS destination, permitted JSON, bearer isolation and safe failure verified with synthetic settings. |
| FR-004 | [LlmResponseParser](../../src/main/java/hotshop/llm/LlmResponseParser.java); TC-03/05/06/11/12. | Structural limits verified; v2 live samples accepted by user. Grounding of future outputs still requires seller review. |
| FR-005, FR-006 | ListingEditor and [MarketplaceUi](../../src/main/java/hotshop/ui/MarketplaceUi.java); TC-01/03/10/12, TASK-012. | Immediate unsaved replacement, failure preservation and stale session/form/lifecycle guards verified in desktop JavaFX tests. |
| NFR-001 | Fixed prompt and TC-05/06/11; TASK-014 v1 failure, approved prompt correction and v2 acceptance. | Application boundaries verified; empirical acceptance limited to 26 reviewed v2 samples. |
| NFR-002 | Configuration/transport, TC-07/09 and synthetic storage/canary checks. | Local isolation verified. Container/image secret exclusion remains future release work. |
| NFR-003 | No application tool dispatcher; TC-08/12 rejects structured calls and observes inert plain text. | Application-side isolation verified; end-to-end no-tool acceptance **blocked by G-03**. |
| NFR-004 | Only title/condition plus fixed instruction/model transmitted; fixed typed feedback/diagnostic tests, TC-04/07. | Local minimum disclosure verified. Provider retention/processing terms were not assessed here. |
| NFR-005 | [DescriptionQuota](../../src/main/java/hotshop/service/DescriptionQuota.java), transport deadlines/byte limits, TC-10 and keyboard UI tests. | Single-process bounds and desktop accessibility checks verified; no concurrent hosted-user or cross-replica guarantee. |
| NFR-006 | [Corpus/rubric](../../src/test/resources/hotshop/llm/SECURITY-EVALUATION.md), offline suites and separately authorized TASK-014. | Deterministic checks and finite live/human review recorded separately; full-regression deferral remains explicit. |
| NFR-007 | [DescriptionRuntime](../../src/main/java/hotshop/DescriptionRuntime.java), [ApplicationRuntime](../../src/main/java/hotshop/ApplicationRuntime.java), runtime/UI tests and unchanged listing persistence. | Local JavaFX integration verified; independent JPro client isolation **blocked by G-04**. |

UC-001 maps to TC-01/03 and explicit-save UI tests; UC-002 to TC-05–09/11;
UC-003 to TC-10 and stale completion tests. See
[usage](../UserGuide.md#generating-a-listing-description),
[setup/design](../DeveloperGuide.md#llm-description-preparation-and-specification)
and the [SDD summary](../../logs/2026-10-19-feature-llm-description-sdd-summary.md).

## Executed evidence and limits

These are historical runs documented by their owners, not new runs for TASK-016/017.
Overlapping test totals must not be added together.

| Evidence | Actual outcome | Limits |
| --- | --- | --- |
| [TASK-010/011](../../logs/2026-10-20-llm-task010-011-integration.md): `.\gradlew.bat test checkstyleMain checkstyleTest check build` | 1,149 tests across 39 suites; zero failures/errors/skips; styles/check/build passed. | Predates later UI/integration/prompt changes; not a current full regression result. |
| [TASK-012](../../logs/2026-10-20-llm-task012-integration.md): focused UI suites | 115 invocations (53 generation, 62 marketplace); both styles passed. | Desktop JavaFX automation, not manual JPro/browser acceptance. |
| [TASK-013](../../logs/2026-10-20-llm-task013-integration.md): focused boundaries and final integration rerun | 442 related invocations; final 13-case integration rerun and both styles passed. | Different overlapping sets; full regression delegated to user. |
| [TASK-014 rerun](../../logs/2026-10-20-task014-prompt-rerun.md) and [acceptance](../../logs/2026-10-20-task014-completion.md) | 118 focused plus 127 parser cases; styles passed. v2 live: 26 automated passes, 26 dispatches/generated results, all 26 user-reviewed PASS. | Private evidence stays restricted/ignored. v1 semantic failure preserved; no remote tool assurance. No live rerun here. |
| [TASK-015](../../logs/2026-10-20-task015-final-reviews.md) and [independent reviews](../../logs/2026-10-20-task015-independent-reviews.md) | 586 focused offline tests across 15 suites; zero failures/errors/skips; styles up to date. Three independent reviewers found no new material local defect. | Stable source candidate above; excludes full regression/build, packaging and hosted identity. |

TASK-016/017 checks and independent documentation review are recorded in their
[documentation log](../../logs/2026-10-20-task016-documentation.md) and
[acceptance log](../../logs/2026-10-20-task017-scoped-acceptance.md).
No Java, tests, resources or packaging configuration changed, so earlier scoped
runtime evidence remains applicable. No new Gradle tests/check/build were run.
Full regression/build after TASK-012 onward remains unverified in these records.

The conditional `.\gradlew.bat shadowJar jproRelease` check was not triggered:
packaging inputs are unchanged. Neither command, a new synthetic-secret artifact
inspection, nor `.\gradlew.bat jproRun` was performed. Manual keyboard, form,
save and error behaviour was not observed here. Earlier historical JAR inspection
does not establish current JPro/container release safety. No tracked release
artifact was changed or staged.

## Outstanding release gates

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

TASK-016 documentation is complete; TASK-017 consolidates this scoped acceptance
record separately, with the exclusions above remaining explicit.
They do not grant feature-level Done, full security acceptance or hosted release
acceptance, and do not authorize subsequent implementation.
