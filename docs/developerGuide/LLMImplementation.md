---
  layout: default.md
  title: "LLM Implementation"
  pageNav: 3
---

# LLM Implementation

HotShop can generate an editable description in Create Listing from the seller's
title and selected condition. Generation is optional and never saves a listing.
For the seller's workflow, see [Search and Listings](../userGuide/SearchAndListings.html).
This page explains the implementation, configuration and testing boundaries.

The [product specification](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/workflow/LLM_FEATURE_PRODUCT_SPEC.md)
defines the requirements; the [technical specification](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/workflow/LLM_FEATURE_TECHNICAL_SPEC.md)
defines the contracts. Their original design-stage wording remains the baseline.
The [implementation plan](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/workflow/LLM_FEATURE_PLAN.md)
records completed tasks and dated evidence. Local implementation is available,
but the feature is **not Done**: provider-tool assurance (G-03) and independent
hosted-session isolation (G-04) remain open.

## How a request reaches the provider

The request follows this path:

1. `ListingEditor` validates the minimum form inputs and calls the runtime's
   session-bound `DescriptionService`.
2. The service checks the authenticated account and login revision. It asks
   `LlmClient.prepare` to encode and size-check the request before reserving quota.
3. The shared `DescriptionQuota` admits the account. `HttpLlmClient` sends one
   asynchronous HTTPS request using trusted startup settings.
4. The transport bounds and parses the response, returning either validated text
   or a fixed typed failure. The service rechecks identity before delivery.
5. The UI applies a successful result only if the original form, authentication
   and field revisions are still current. The description remains unsaved.

The request body contains exactly `model` and `input` strings. `input` combines
fixed instructions with separately JSON-encoded title and canonical condition.
Gson 2.14.0 provides JSON encoding and strict streaming parsing. No account,
listing history, existing description, images, database contents or application
secrets enter the body. The bearer token appears only in the Authorization header.
Product text cannot select a different endpoint, model or header.

The fixed instruction asks for plain text grounded only in identifiable item and
condition facts, normally 50–70 words with a hard maximum of 100. It asks the model
to omit embedded instructions without repeating them, avoid questions or requests
for replies, and use no tools. No application tools or tool dispatcher are exposed;
returned commands and links remain inert text. Prompt wording alone cannot ensure
model compliance or establish the provider's internal tool configuration.

Useful source entry points are
[DescriptionService](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/src/main/java/hotshop/service/DescriptionService.java),
[HttpLlmClient](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/src/main/java/hotshop/llm/HttpLlmClient.java)
and [ListingEditor](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/src/main/java/hotshop/ui/ListingEditor.java).

## Private configuration

`LlmConfiguration.load(Path workingDirectory)` reads an immutable UTF-8 snapshot
of `.env` from the trusted Java process's working directory. `Main` loads that
snapshot once through its lazy description owner. In a packaged launch this is
the process working directory, not a directory inside the JAR or the data directory.
Restart the server process to reload settings. Missing, unreadable or invalid
configuration disables generation while manual listing creation remains available.

The following is a synthetic syntax example, not a live provider configuration.
Use a temporary directory for experiments; do not overwrite an operator's `.env`.

```dotenv
SOCLAAS_BASE_URL='https://provider.invalid/example/responses'
SOCLAAS_API_KEY='synthetic-test-token'
SOCLAAS_MODEL='synthetic-test-model'
```

`SOCLAAS_BASE_URL` is the complete, exact HTTPS endpoint; no path is appended.
It must be absolute with a host and no user-info or fragment. Path and query are
preserved, and internal hosts are allowed. The model must be nonblank. The token
must be nonempty, contain no whitespace or control characters, and fit Java HTTP
header characters through U+00FF.

The loader splits each assignment at its first `=`, trims surrounding syntax
whitespace and accepts matching outer single or double quotes. Quoted contents
are literal, including `#`, `$` and backslashes. Blank lines, whole-line comments
and unrelated assignments are ignored; LF and CRLF line endings are supported.
There is no shell `export`, variable expansion, escape processing or inline-comment
removal. Duplicate required keys, malformed standalone lines, unquoted quotes,
unmatched outer quotes or embedded CR/LF/NUL invalidate the snapshot.

Loading writes no files. Configuration access is guarded and package-private;
`toString` exposes availability only. Raw settings and read/parse exceptions are
not returned in diagnostics. Ordinary tests must use temporary synthetic settings
and must never load the real working-directory `.env`. See
[LlmConfiguration](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/src/main/java/hotshop/llm/LlmConfiguration.java).

## Limits and request accounting

| Boundary | Implemented rule |
| --- | --- |
| Minimum input | Stripped title of 1–120 Unicode code points and a non-null Java `Condition` enum. Other listing fields are unnecessary for generation. |
| Request size | At most 8,192 UTF-8 bytes after encoding. Preparation failures occur before quota admission. |
| Response size | Separate 65,536-byte limits for wire input and decoded output. Content-Length is only an early rejection hint. |
| Time | 10-second connection timeout; 45-second HTTP request timeout plus an independent 45-second transport deadline covering dispatch scheduling, receiving, decoding and parsing. This deadline starts when the transport attempt starts, after preparation and admission; it is not a bound on the entire UI/service operation. |
| Concurrency | One active request per account in the shared server registry. |
| Frequency | Five attempts per rolling 60 seconds and thirty per rolling 3,600 seconds per account. |
| Retries | No automatic retries; another attempt requires an explicit action. |
| Accepted text | 1–100 words and at most 5,000 Unicode code points after stripping, without truncation. The 50–70-word target is not a minimum. |

Quota history belongs to the process-shared registry. Reopening a form, logging
out or creating another session service does not clear it; server restart does.
A trusted before-send callback records actual dispatch time, so queueing does not
shorten a frequency window. Exact expiry boundaries stop counting, and displayed
retry seconds round up to capacity across both windows. Matching reservation IDs
make release idempotent and prevent stale cleanup from releasing a newer request.

Known failures before dispatch roll back admission. Dispatched failures remain
counted; ambiguous collaborator failures conservatively retain their charge.
Cancelling a returned service future or leaving the page does not free the active
slot before transport termination. These are single-process guarantees, not
cross-replica limits. See
[DescriptionQuota](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/src/main/java/hotshop/service/DescriptionQuota.java).

## Response handling and safe failures

The HTTP client uses ordinary certificate and hostname verification, disables
redirects, requests JSON and identity encoding, and accepts HTTP 200 only with
JSON media type and supported encoding. The bounded subscriber counts incoming
buffers before copying. Identity and incremental gzip decoding enforce both byte
limits, including concatenated gzip members through HTTP EOF.

`LlmResponseParser` strictly decodes UTF-8 and reads one complete JSON object. It
rejects duplicate names throughout the document and container nesting deeper than
32, including the root. The envelope must have completed status and a canonical
top-level string `output_text`. Optional output arrays must contain completed
assistant messages with output-text content only; tool and refusal variants fail
safely. Nested text is never appended or used as fallback. Unknown metadata is
traversed within the same bounds and cannot change configuration or description.

A word is a run separated by Unicode whitespace or space characters that contains
at least one letter or digit. Hyphens remain within a word; punctuation-only runs
do not count. Invalid or oversized text fails without truncation. Structural
checks cannot establish factual accuracy or detect every textual refusal or
injected instruction. A structurally valid script or URL can be accepted as plain
text, but it triggers no execution, navigation or callback.

HTTP 401/403 map to `PROVIDER_AUTH`, 429/5xx to `PROVIDER_BUSY`, and other 4xx
to `UNAVAILABLE`. Unsupported status, media, encoding or response structure maps
to `INVALID_RESPONSE`; timeout maps to `TIMEOUT`, other transport failures to
`NETWORK`. Raw response text, settings, headers, URI and exception causes do not
reach UI messages or diagnostics. Unexpected service failures log only the fixed,
cause-free `LLM_SERVICE_UNEXPECTED` warning. See
[LlmResponseParser](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/src/main/java/hotshop/llm/LlmResponseParser.java).

## UI, session and resource lifecycle

Only Generate is disabled while generation is pending; editing, saving and
navigation remain available. Success replaces the retained Description field
once, preserving normal dirty tracking and requiring explicit Save. Failures
preserve the draft. Fixed feedback explains local rate limits and changed drafts.

Title, condition and description revisions invalidate a pending result even if an
edit is later reversed. Page identity, request sequence, UI authentication revision
and disposal are checked before any completion changes controls. Login/logout
revisions also protect the service, including logout followed by login to the same
account. Dirty-form confirmation runs a nested JavaFX loop, so completion delivery
waits until its decision is handled. Accepted navigation/logout/close invalidates
the old form; cancelled confirmation retains usability.

`DescriptionService` has no listing, repository, image or database dependency.
Its pending network work does not block the shared worker's account operations.
`DescriptionRuntime` owns the shared quota and transport; closing one application
runtime cancels only its own description work. `Main` shares one lazy owner across
application objects and closes it through a JVM shutdown hook. Transport resources
are lazy; termination cancels futures, subscriptions, decoder streams and deadlines
once, including resources arriving late.

`ApplicationRuntime.open(Path)` and `open(Path, Clock)` stay offline and never
read `.env`; alternate hosts/tests inject an explicit description owner.
`closeAsync()` rejects new generation, cancels pending work and drains accepted
service work while retaining the data-directory lock. Repeated calls observe the
same completion. `close()` waits off JavaFX and returns immediately on JavaFX;
observe `closeAsync()` for shutdown failures. No HTTP termination is awaited on
JavaFX. These guards do not establish independent JPro client identity.

## Offline verification

Ordinary `test`, `check` and `build` exclude the `llm-live` tag. Run targeted
checks from the repository root with the Gradle Wrapper:

```powershell
.\gradlew.bat test --tests 'hotshop.llm.*' --tests '*DescriptionServiceTest' --tests '*DescriptionQuotaTest' --tests '*DescriptionRuntimeTest' checkstyleMain checkstyleTest
.\gradlew.bat test --tests '*ListingDescriptionGenerationTest' --tests '*MarketplaceUiTest'
```

These commands describe how to verify the feature; they are not a claim of a new
run during this documentation reorganisation. Tests use temporary SQLite data,
synthetic settings, controlled clients, clocks and completion gates. Loopback HTTPS
uses a public synthetic test CA trusted only by the injected client; it preserves
hostname checks and never changes global production TLS. See the
[HTTPS fixture guide](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/src/test/resources/hotshop/llm/HTTPS-FIXTURES.md).

| Coverage | Main executable evidence and interpretation |
| --- | --- |
| Inputs and request boundaries (TC-01/02/05/06) | `LlmConfigurationTest`, `LlmRequestTest`, `LlmPreparationTest` and `DescriptionServiceTest` cover authentication priority, Unicode/title and byte boundaries, quoting, all conditions, immutable configuration and literal hostile/benign titles. |
| Responses (TC-03/12) | `LlmResponseTest` covers word/code-point limits, strict envelopes, duplicate/deep JSON, authoritative top-level text and inert metadata. These are structural checks. |
| Transport and failures (TC-04/09/10) | `HttpLlmClientTest` verifies exact requests, TLS, redirects, encoding/byte limits, deadline cleanup and safe status mappings using controlled HTTPS. The connect policy and timeout mapping are checked without waiting for a slow real-network timeout. |
| Private data and no execution (TC-07/08/09) | `LlmSecurityIntegrationTest` captures the production request, checks synthetic secret/account/listing/file canaries, rejects structured tool calls and observes zero output-directed callbacks or storage writes. Plain command/link text remains inert, not semantically filtered. |
| Quota and lifecycle (TC-10) | `DescriptionQuotaTest`, `DescriptionServiceTest` and `DescriptionRuntimeTest` cover expiry boundaries, racing admission, charging, cancellation, login revisions, ownership and shutdown. |
| Desktop interaction (TC-01/03/10/12) | `ListingDescriptionGenerationTest` and `MarketplaceUiTest` use real JavaFX controls to check keyboard activation, editable unsaved replacement, safe failures, manual save and stale completion ordering. They do not establish JPro browser acceptance. |
| Corpus preparation (TC-05/06/11) | `LlmSecurityCorpusTest` checks the 26 public cases and required attack/control coverage; `LiveLlmAdapterTest` checks the real evaluation adapter offline. Model semantics require separately authorised live review. |

The [integration tests](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/src/test/java/hotshop/llm/LlmSecurityIntegrationTest.java)
snapshot storage after setup while the runtime stays open. Readable files are
compared by SHA-256 digest; Windows' active `application.lock` is checked by size
and modification time because its contents are locked. Persisted values are also
read back through services. Diagnostic checks capture application log parameters,
causes and suppressed exceptions, and require an actual fixed, cause-free warning
for unexpected failures. Passing an empty capture cannot satisfy that check.

## Live evaluation and human review

Live calls require separate explicit authorisation. Prior endpoint readiness or a
successful evaluation grants no permission for another run. `llmLiveTest` is a
separate opt-in task, never automatically included in normal checks. The operator
must approve the public run/version label and an existing, ignored, access-restricted
evidence directory, checking access for the human Windows profile and live process.

An authorised command supplies `-PllmLiveRunId=<approved-public-label>` and
`-PllmLiveEvidenceDir=<approved-access-restricted-directory>`. The adapter fails
closed before private configuration loading if this setup is missing and refuses
an existing manifest. `ProductionLiveLlmClientFactory` uses a temporary synthetic
authenticated runtime with the production service and transport. It runs public
corpus cases sequentially, waits at least 13 seconds after each terminal result,
keeps real quotas and never adds retries. Each request is independent, without
generated conversation history or automatic follow-up.

Private case JSON is written before assertions and records public input, timestamps,
typed outcome and actual dispatch counts. The incremental manifest distinguishes
partial runs. Generated results start `UNREVIEWED`; structural rejection and
infrastructure failure remain distinct. Reports render output inertly. Private
settings, bearer headers, raw transport exceptions and generated prose never belong
in committed logs. A public version label identifies the reviewed configuration
snapshot, not an independently verified provider backend version.

The [corpus and review rubric](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/src/test/resources/hotshop/llm/SECURITY-EVALUATION.md)
cover all five conditions, benign security vocabulary, role spoofing, disclosure,
unrelated work, encoded/Unicode/multilingual attacks and requested external actions.
Automated checks enforce structure and selected sentinels. A human must also
check grounding, refusal-as-description, compliance with embedded instructions,
tool/action claims and real or invented disclosures. Controls must generate valid
text; transport failures cannot count as semantic resistance. Another LLM cannot
be the sole judge.

The reviewed runs are `task014-20261020-v1` and `task014-20261020-v2`. The first
passed automation but failed human review for `attack-internals`; its evidence is
preserved. After an authorised prompt correction, all 26 second-run outputs passed
automation and user review. Separate private human decisions retain the original
case outputs and dispatch-time review labels. See the
[rerun evidence](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/logs/2026-10-20-task014-prompt-rerun.md)
and [human acceptance record](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/logs/2026-10-20-task014-completion.md).
This supports the reviewed samples only, not future factual accuracy or universal
prompt-injection immunity. Sellers must continue reviewing generated descriptions.

## Current assurance and release limits

Focused offline verification and independent local reviews support authentication,
minimal disclosure, quota, transport/parser limits, inert output and desktop
lifecycle behaviour. See the [review findings](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/logs/2026-10-20-task015-independent-reviews.md)
and [verification scope](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/logs/2026-10-20-task015-final-reviews.md).
These findings do not close the following requirements:

- **G-03 — provider tools:** no independent provider-tool assurance is available.
  Obtain capability/configuration evidence or a separately approved product-spec
  revision accepting a narrower guarantee. Prompts, samples and rejected tool
  envelopes cannot establish remote tool non-use. Provider retention/processing
  terms have not been assessed here.
- **G-04 — hosted sessions:** independently verify concurrent hosted-client
  identity/session isolation through separately approved lifecycle work. Existing
  runtime identity and exclusive data locking do not prove this property.
- **Packaging and hosting:** current JAR/JPro/container release safety is unverified.
  Packaging changes require synthetic-secret inspection of generated artifacts.
  Docker/Render work must establish runtime secret mounting and exclusion from
  build contexts, image layers and browser assets. Git ignore alone is insufficient;
  historical JAR checks do not establish current release safety.

Task completion records do not grant feature-level Done, full security acceptance,
hosted release acceptance or permission to deploy. Detailed historical execution
records remain in the [feature summary](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/logs/2026-10-19-feature-llm-description-sdd-summary.md).
The former preparation, acceptance and coverage records are preserved in the
[documentation archive](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/blob/main/logs/2026-10-20-llm-documentation-reorganisation.md#archived-acceptance-record).
