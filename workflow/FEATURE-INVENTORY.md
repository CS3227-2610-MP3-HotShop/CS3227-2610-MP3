# JavaFX feature inventory — source and test inspection

Status: INV-01 to INV-16 traced to source and representative test assertions.
INV-17 was added on 2026-10-17 for platform and data foundations; it has no single
source path, and its basis is the system-wide requirements and infrastructure
chapters cited in its row together with the agreed target decisions below.
Coverage of the 17 groups was approved by the user on 2026-10-17. That approval covers
the feature list only. It does not approve detailed specifications, does not resolve the
source ambiguities below, and does not authorise implementation.
Runtime checks are not claimed. Date: 2026-10-17 (SGT).
Tracking: [issue #8](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/8).

## Source and evidence status

The user confirmed MP3 contains the MP2 work and has no outstanding missing
JavaFX features. This is a user confirmation, not an independently performed
MP2-to-MP3 diff. Local reference HEAD inspected:
`93dbfc63126a69874dc53f8c59d822aa58ac8c72`.
Current inspection baseline: `1d812ef270d780d248c74f7a37c543345df70026`
on `codex/migration-workflow`, clean at inspection start. A Git comparison confirms
no changes to src/, HotShop_Architecture.md, or the user/developer guide chapters
between that earlier reference and this baseline.

Frozen on 2026-10-17: `1d812ef270d780d248c74f7a37c543345df70026` is the migration source
reference. Specifications cite behaviour at that commit. A later change to the JavaFX
source does not change migration scope by itself; it requires a proposed re-baseline
with a diff of the affected paths and explicit user approval before any specification or
task scope follows it. Discovering such a change is a reason to raise a re-baseline, not
to silently specify against newer source.
Remote freshness was not checked.

Start with [User Guide](../docs/UserGuide.md), its linked feature chapters,
[Developer Guide](../docs/DeveloperGuide.md), and
[Requirements](../docs/developerGuide/Requirements.md). All rows below describe
the feature scope extracted from the guides. Source/test tracing is recorded below;
reading assertions does not establish that tests pass. No runtime checks were run.
IDs are inventory references, not approved requirements or implementation tasks.

## Documented feature groups

| ID | Feature group and included behaviour | Primary documentation |
| --- | --- | --- |
| INV-01 | Account registration, login/logout, password visibility, username normalisation and uniqueness, password rules, authenticated access | [Accounts](../docs/userGuide/AccountsAndInterface.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-02 | Edit display name and private preferred pickup location; replace/remove profile image; change password with current-password verification; view other participants' public profiles and available listings | [Accounts](../docs/userGuide/AccountsAndInterface.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-03 | Search others' available listings by title, category, conditions, inclusive price range; sort, clear filters, refresh, preserve submitted search and draft state on Back | [Search and listings](../docs/userGuide/SearchAndListings.md) |
| INV-04 | Listing cards/details with status, seller link, description, price, category, condition, pickup location, ordered photo display and placeholders | [Search and listings](../docs/userGuide/SearchAndListings.md) |
| INV-05 | Create/edit available listings; prefill pickup location; add, reorder, and remove photos; validate media and listing fields; atomic rejection of invalid saves; unchanged-save handling | [Search and listings](../docs/userGuide/SearchAndListings.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-06 | My Listings with status ordering and pending-offer counts; archive with history retention; restricted permanent deletion; confirmation of consequences; edit/archive rejection of pending offers; enquiry deletion rules | [Search and listings](../docs/userGuide/SearchAndListings.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-07 | Make an offer with optional message; withdraw pending offer; My Offers history; prevent self-offers and duplicate pending offers; enforce amount and listing-state rules | [Offers and sales](../docs/userGuide/OffersAndSales.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-08 | Seller incoming-offer list; accept/reject; accepting reserves listing, rejects competing offers, and creates an active sale; conversation integration and participant privacy | [Offers and sales](../docs/userGuide/OffersAndSales.md), [requirements](../docs/developerGuide/Requirements.md) |
| INV-09 | My Sales/My Purchases; agreed item snapshot, price, counterpart, status, timestamps, history, and next-action guidance; two-party completion marks listing sold | [Offers and sales](../docs/userGuide/OffersAndSales.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-10 | Direct sale cancellation before confirmations; cancellation request/accept/reject/withdraw after confirmation; block completion while request is pending; release listing without reviving rejected offers | [Offers and sales](../docs/userGuide/OffersAndSales.md), [requirements](../docs/developerGuide/Requirements.md) |
| INV-11 | Seller offers/withdraws up to three meetup slots; buyer books one; validate dates, duration, location, availability and overlaps; booking removes alternative slots | [Meetups](../docs/userGuide/Meetups.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-12 | Propose/accept/reject/withdraw meetup move; cancel meetup while retaining sale; integrate sale completion/cancellation; show summaries and expired-time next steps | [Meetups](../docs/userGuide/Meetups.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-13 | Buyer-initiated listing conversations and offer-created conversations; plain-text messages; participant-only access; draft retention; read-only sold/archived history; enquiry deletion on eligible listing deletion | [Conversations](../docs/userGuide/Conversations.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-14 | Conversation groups/order, previews, unread message and offer-event counts, mark-as-read, refresh, and embedded offer/sale/meetup actions | [Conversations](../docs/userGuide/Conversations.md), [rules](../docs/userGuide/MarketplaceRules.md) |
| INV-15 | Seller dashboard: pending offers, active/completed sales, completed sales value, upcoming seller meetups, and navigation shortcuts | [Offers and sales](../docs/userGuide/OffersAndSales.md) |
| INV-16 | Shared navigation/back behaviour, unsaved-change prompts, loading and repeated-submit protection, field errors, retained input, empty/error/retry states, keyboard access and adaptable layout | [Accounts](../docs/userGuide/AccountsAndInterface.md), [search](../docs/userGuide/SearchAndListings.md), [conversations](../docs/userGuide/Conversations.md) |
| INV-17 | Platform and data foundations: web session and authentication lifecycle, concurrent multi-user access and the integrity constraints replacing desktop operation serialisation, persistent storage and seed provisioning, atomic multi-entity changes, and the refusal-message standard | [Requirements](../docs/developerGuide/Requirements.md), [architecture](../docs/developerGuide/ArchitectureOverview.md), [persistence](../docs/developerGuide/AccountServiceAndLocalPersistence.md), [service worker](../docs/developerGuide/ServiceWorker.md) |

INV-17 is different in kind from INV-01 to INV-16. It is not a newly discovered JavaFX
screen feature. It groups behaviour the guides state as system-wide requirements and
desktop infrastructure, together with the target decisions that replace them, so that
this work has an owner, an issue and a completion gate instead of living only in the
cross-cutting prose below.

## Cross-cutting requirements to retain or adapt explicitly

- Login, ownership and participant-access checks; private profile preferences,
  offers, conversations, sales and meetups must not leak to unrelated users.
- Atomic multi-entity operations, consistent state transitions, validated images,
  understandable errors, and preservation of previous data when an operation fails.
- Existing local persistence and image storage survive application restart.
- Superseded on 2026-10-17: resettable demo data with PostgreSQL deferred. The web
  application has no reset feature, and a hosted PostgreSQL database is used from the
  start. Reset existed on the desktop only because the data sat on one person's disk.
  Data persists and seeding runs once at provisioning. Fresh demo data still applies, so
  existing accounts, records and images are not imported. Seed content, the provisioning
  path and image storage need technical specification, tracked under INV-17.
- Desktop-only constraints include JavaFX/JAR packaging, local data-folder locking,
  a single background worker, offline operation, and one logged-in user at a time.
  Treat these as source implementation constraints needing explicit web decisions,
  rather than silently copying them or claiming replacements already exist.
- Superseded on 2026-10-17: the single-logged-in-user constraint. The web application
  supports concurrent multi-user access, so guide statements such as seeing the other
  person's message at the next login are source behaviour, not target behaviour, and
  the affected User Guide wording changes with its feature. The single background
  worker no longer provides serialisation, so the database integrity constraints become
  the mechanism that protects one pending offer per buyer and listing, one active sale
  per listing, one pending cancellation request, one pending move proposal, and
  first-booker-wins on a shared meetup slot. Tracked under INV-17.
- Superseded on 2026-10-17: the 960 x 640 minimum window size and the fixed JavaFX card
  and layout units. The web layout adapts to arbitrary viewport sizes; breakpoints and
  the viewport sizes used for acceptance remain a specification detail under INV-16.
- Decide session lifetime, concurrency control, shared storage, cross-user update
  visibility (refresh versus live updates), read and unread behaviour with simultaneous
  viewers, and the browser equivalent of unsaved-change protection during technical
  specification. These belong to INV-17, except unsaved-change protection, which stays
  with INV-16. Timezone and time handling affects INV-11 and INV-12 as well. The
  inventory does not select providers or protocols.

Sources: [Getting Started](../docs/userGuide/GettingStarted.md),
[Requirements](../docs/developerGuide/Requirements.md), and the feature rules above.

## Documented unavailable or future work

[Future Work](../docs/developerGuide/FutureWork.md) and the User Guide distinguish:

- Wishlist is disabled/Coming soon; its proposed stories are not implemented features.
- Notifications are not provided; current indicators are counts, ordering, and next steps.
- Password recovery and account deletion are unavailable.
- Four proposed enhancements are not implemented: latest-offer status on conversation
  cards, arbitrary validated meetup duration rather than fixed UI choices, a directly
  selectable All categories option, and retaining the Filters panel expansion state.

These items are not automatically added to existing-feature parity. Any addition
needs an explicit scope decision. Separately specified MP3 requirements still need
their own review; this pass does not claim to cover new assignment requirements.

## Evidence and interpretation

Source and test inspection means the implementation path and representative assertions
were read. It does not mean every sub-behaviour has an automated test or that tests
passed on this snapshot. Manual-testing instructions were inspected but not executed.
The 16 audited IDs remain stable, and the audit inferred no additional JavaFX feature
group. INV-17 was added afterwards from the guides' system-wide requirements and the
agreed web decisions; it is not a claim that a seventeenth existing screen feature was
found, and it has no source trace in the evidence document.

Detailed trace: [feature evidence](FEATURE-EVIDENCE.md). This is supporting evidence,
not a second specification or permission to implement.

## Cross-feature behaviour to preserve

- Offer acceptance reserves the listing, rejects competing offers and creates one sale
  atomically; the accepted offer also connects to the buyer/seller conversation.
- Editing or archiving a listing rejects pending offers; saving unchanged details does not.
- A sale snapshot freezes title, description, condition and agreed price. It does not
  freeze every listing field, photo or counterpart profile.
- Both participants confirm completion; no meetup is required to confirm. Cancellation
  after a confirmation requires agreement and blocks completion while pending.
- Cancelling a sale releases its listing and cancels its meetup/slots, but does not
  revive rejected offers. Its original accepted offer remains in history.
- Enquiry-only listing deletion removes conversations too; any offer history prevents
  permanent deletion. Sold/archived conversation history is readable but not writable.
- The dashboard sums completed agreed sale values, not asking prices or active sales.

## Source ambiguities for detailed specification

These do not remove feature groups or authorise source fixes:

1. **Listing visibility:** search/public-profile lists exclude unavailable items, but
   ListingService.getListing permits any authenticated user to retrieve any existing
   listing by ID in any status. Decide the intended web detail-access policy explicitly;
   do not infer participant-only access from normal navigation.
2. **Meetup controls versus service rules:** the desktop UI offers fixed durations and
   quarter-hour start choices. MeetupTime enforces duration bounds of 15 minutes–4 hours,
   not that fixed menu or a quarter-hour start grid. MarketplaceRules attributes the
   combined constraints to the service; keep UI and server requirements separate.
3. **Conversation creation:** HotShop_Architecture says opening Chat with seller creates
   a conversation. Actual code/tests create it on the first message or offer; merely
   opening the composer does not persist an empty enquiry. The user-facing guide agrees
   with this implementation. Record the discrepancy rather than treating the architecture
   sentence as a new requirement.
4. **Web adaptation:** concurrent multi-user access and the dropped window-size minimum
   were decided on 2026-10-17. Session mechanism, concurrency control, shared demo state
   and seed provisioning, cross-user update visibility, simultaneous-viewer read state, image
   handling, timezones and the browser equivalent of unsaved-change protection still need
   decisions, mostly under INV-17. Desktop locking, JAR packaging and local database
   upgrades are source infrastructure; define target equivalents only where the approved
   web behaviour requires them.

## Remaining inventory work

1. Done on 2026-10-17: coverage of the 17 groups is approved, and the source baseline is
   frozen at 1d812ef with re-baselining by explicit approval. INV-01 to INV-16 were
   measured against that baseline; INV-17 against the documented system-wide requirements
   and the agreed target decisions.
2. Resolve flagged source ambiguities when detailing the affected feature; approval
   of the feature list does not silently approve a web access or scheduling policy.
3. Define and execute appropriate source/runtime checks if runtime proof is needed;
   this discovery pass is static, and does not claim a passing source-app baseline.
4. Derive feature issues and incremental frontend/backend/integration tasks from
   approved scope, then discuss detailed specifications one feature at a time.

No feature issues, detailed feature specifications, or implementation tasks were
created in this documentation pass. Issue #8 is not complete.
