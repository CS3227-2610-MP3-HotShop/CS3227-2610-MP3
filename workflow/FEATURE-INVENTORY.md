# JavaFX feature inventory — documentation pass

Status: initial documented inventory; not yet approved as complete or verified
against code/tests/runtime. Date: 2026-10-17 (SGT).
Tracking: [issue #8](https://github.com/CS3227-2610-MP3-HotShop/CS3227-2610-MP3/issues/8).

## Source and evidence status

The user confirmed MP3 contains the MP2 work and has no outstanding missing
JavaFX features. This is a user confirmation, not an independently performed
MP2-to-MP3 diff. Local reference HEAD inspected:
`93dbfc63126a69874dc53f8c59d822aa58ac8c72`.
The local migration-workflow documentation is uncommitted and is separate from
this application reference. Remote freshness has not been checked in this task.

Start with [User Guide](../docs/UserGuide.md), its linked feature chapters,
[Developer Guide](../docs/DeveloperGuide.md), and
[Requirements](../docs/developerGuide/Requirements.md). All rows below describe
what the guides say exists; code/test tracing and runtime checks remain pending.
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

## Cross-cutting requirements to retain or adapt explicitly

- Login, ownership and participant-access checks; private profile preferences,
  offers, conversations, sales and meetups must not leak to unrelated users.
- Atomic multi-entity operations, consistent state transitions, validated images,
  understandable errors, and preservation of previous data when an operation fails.
- Existing local persistence and image storage survive application restart.
  The user approved resettable web demo data on 2026-10-17, deferring durable storage to PostgreSQL. The demo-state mechanism and reset lifecycle still need technical specification.
- Desktop-only constraints include JavaFX/JAR packaging, local data-folder locking,
  a single background worker, offline operation, and one logged-in user at a time.
  Treat these as source implementation constraints needing explicit web decisions,
  rather than silently copying them or claiming replacements already exist.
- Decide session lifetime, multi-user concurrency, shared storage, timezone/time
  handling, and browser equivalents of unsaved-window-close protection during
  technical specification. The inventory does not select providers or protocols.

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

## Remaining inventory work

1. Trace each feature group to actual source and tests, including service permissions,
   state transitions, boundary conditions, and integrations.
2. Reconcile documentation discrepancies and inspect manual-testing coverage.
3. Run appropriate source-app checks where feasible; record unavailable checks honestly.
4. Present the reconciled full inventory for user approval before detailed feature specs.
5. Derive feature issues and frontend/backend/integration tasks only from approved scope.

No feature issues, detailed feature specifications, or implementation tasks were
created in this documentation pass. Issue #8 is not complete.
