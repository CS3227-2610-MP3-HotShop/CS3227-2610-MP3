# Feature evidence — static source inspection

Baseline: `1d812ef270d780d248c74f7a37c543345df70026`, 2026-10-17.
Supports [the inventory](FEATURE-INVENTORY.md). Inventory coverage was approved on
2026-10-17. This document traces INV-01 to INV-16 only; INV-17 is a target-decision
group with no JavaFX source trace.

Three read-only workers traced accounts/listings/interface (INV-01–06/16),
offers/sales/dashboard (INV-07–10/15), and meetups/chat (INV-11–14).
The coordinator inspected shared runtime, transaction, identity and future-feature
boundaries and consolidated their reports. Source and representative assertion
bodies were inspected; no tests, application launch or manual walkthrough were run.
These examples establish traceability, not exhaustive test coverage or passing results.

## INV-01 — Accounts

Register/login/logout, normalised unique usernames, exact passwords, logged-out registration, password visibility and confirmation. One account buys and sells.

- Source: [service/AccountService.java](../src/main/java/hotshop/service/AccountService.java): `register, login, logout`.
- Existing assertions: [service/AccountServiceTest.java](../src/test/java/hotshop/service/AccountServiceTest.java): `register_duplicateNormalizedUsername_preservesOriginalAccount`, `login_loggedIn_rejectsSwitchUntilLogout`.

## INV-02 — Profiles

Private pickup preference, public name/image, current-password verification, separate image/text saves, available public listings. Image failure/cleanup assertions also in ProfileImageTest.

- Source: [service/AccountService.java](../src/main/java/hotshop/service/AccountService.java): `updateProfile, changePassword, getPublicProfile`.
- Existing assertions: [service/AccountServiceTest.java](../src/test/java/hotshop/service/AccountServiceTest.java): `updateProfile_invalidLocation_leavesBothFieldsUnchanged`, `changePassword_correctCurrentPassword_keepsSessionAndReplacesCredential`.

## INV-03 — Search

Others’ available listings only; title substring, category, multiple conditions, inclusive price range and sort. SearchState/SearchPage preserve submitted criteria separately from drafts; no pagination.

- Source: [service/ListingSearch.java](../src/main/java/hotshop/service/ListingSearch.java): `matches, order`.
- Existing assertions: [service/ListingSearchTest.java](../src/test/java/hotshop/service/ListingSearchTest.java): `searchListings_priceRange_includesBothBounds`, `searchListings_priceSorts_orderByPriceThenNewest`.

## INV-04 — Listing display

ListingPages/ListingCards show details, seller links, status, ordered gallery and placeholders; images scale without cropping. Direct detail lookup is authenticated but not participant-restricted.

- Source: [service/ListingService.java](../src/main/java/hotshop/service/ListingService.java): `getListing`.
- Existing assertions: [ui/MarketplaceUiTest.java](../src/test/java/hotshop/ui/MarketplaceUiTest.java): `listingCards_portraitAndLandscapePhotos_fitWholeImagesWithinCards`, `listingCards_owner_showStatusAndPendingOfferCountInsteadOfCondition`.

## INV-05 — Create/edit listings

Available listings only; prefilled pickup, ordered photos, atomic validation/import, owned photo references. No-op edit preserves timestamp and offers; real edit rejects pending offers.

- Source: [service/ListingService.java](../src/main/java/hotshop/service/ListingService.java): `createListing, updateListing, resolvePhotos`.
- Existing assertions: [service/ListingPhotoTest.java](../src/test/java/hotshop/service/ListingPhotoTest.java): `createListing_oneInvalidPhoto_savesNothingAndLeavesNoFiles`, `updateListing_keepReorderAddAndRemove_savesOrderAndRetiresRemovedPhoto`.

## INV-06 — Manage listings

Own status ordering/counts/meetup summaries; archive preserves history and cannot be undone. Delete only eligible available/archived listings without any offer history; delete enquiries/photos too.

- Source: [service/ListingService.java](../src/main/java/hotshop/service/ListingService.java): `getMyListings, archiveListing, deleteListing`.
- Existing assertions: [service/ListingServiceTest.java](../src/test/java/hotshop/service/ListingServiceTest.java): `getMyListings_everyStatus_ordersReservedAvailableSoldArchived`, `deleteListing_withdrawnOfferHistory_reportsInvalidStateSuggestingArchive`.

## INV-07 — Buyer offers

One pending offer per buyer/listing; no self/unavailable offers; above asking price allowed. Change amount by withdrawal/new offer; optional message creates/reuses conversation.

- Source: [service/OfferService.java](../src/main/java/hotshop/service/OfferService.java): `submitOffer, withdrawOffer, getMyOffers`.
- Existing assertions: [service/OfferServiceTest.java](../src/test/java/hotshop/service/OfferServiceTest.java): `submitOffer_existingPendingOffer_reportsInvalidStateWithAmount`, `submitOffer_afterWithdrawal_savesNewOfferAndKeepsHistory`.

## INV-08 — Seller offers

Seller-only incoming offers; accept/reject with atomic reserve/reject-competitors/create-sale effects. Live/completed accepted offers precede cancelled-sale accepted offers, then others.

- Source: [service/OfferService.java](../src/main/java/hotshop/service/OfferService.java): `getOffersForListing, acceptOffer, rejectOffer`.
- Existing assertions: [service/OfferServiceTest.java](../src/test/java/hotshop/service/OfferServiceTest.java): `acceptOffer_pendingOffer_reservesListingRejectsOthersAndSavesSale`, `acceptOffer_saleSaveFailure_rollsBackListingAndOffers`.

## INV-09 — Sales and completion

Participant-only history, snapshots, counterpart links and next actions. Two confirmations complete sale/listing/meetup; completion does not require a meetup. Pending cancellation ranks first for both participants.

- Source: [service/TransactionService.java](../src/main/java/hotshop/service/TransactionService.java): `getMySales, getMyPurchases, confirmCompletion`.
- Existing assertions: [service/TransactionServiceTest.java](../src/test/java/hotshop/service/TransactionServiceTest.java): `confirmCompletion_bothParticipants_completesSaleAndMarksListingSold`, `confirmCompletion_nonParticipant_reportsPermission`.

## INV-10 — Sale cancellation

Direct before confirmation; agreement afterward, one pending request, requester withdrawal. Release listing without reviving offers; retain confirmations on rejection/withdrawal and cancellation history.

- Source: [service/TransactionService.java](../src/main/java/hotshop/service/TransactionService.java): `cancelSale, requestCancellation, acceptCancellation, rejectCancellation, withdrawCancellation`.
- Existing assertions: [service/TransactionServiceTest.java](../src/test/java/hotshop/service/TransactionServiceTest.java): `requestCancellation_afterConfirmation_blocksConfirmationAndShowsBothSides`, `rejectCancellation_otherParticipant_keepsConfirmationAndAllowsCompletion`.

## INV-11 — Meetup slots/booking

Up to three future slots per sale; expired slots hidden and not counted. Buyer books; alternatives deleted; both participants checked for conflicts across buying/selling roles.

- Source: [service/MeetupService.java](../src/main/java/hotshop/service/MeetupService.java): `offerSlot, withdrawSlot, bookSlot, toFutureTime, requireFree`.
- Existing assertions: [service/MeetupServiceTest.java](../src/test/java/hotshop/service/MeetupServiceTest.java): `bookSlot_buyer_schedulesMeetupAndDeletesOtherSlots`, `bookSlot_sameTimeOfferedToTwoBuyers_firstBookingWins`.

## INV-12 — Meetup changes

One move proposal, other participant responds, proposer withdraws. Recheck overlap on acceptance. Cancel meetup keeps sale active; sale closure closes meetup. Summaries and expired-time guidance.

- Source: [service/MeetupService.java](../src/main/java/hotshop/service/MeetupService.java): `proposeMove, acceptMove, rejectMove, withdrawMove, cancelMeetup`.
- Existing assertions: [service/MeetupServiceTest.java](../src/test/java/hotshop/service/MeetupServiceTest.java): `acceptMove_otherParticipant_movesMeetup`, `acceptMove_proposedTimeNowOverlapsAnotherMeetup_reportsInvalidState`.

## INV-13 — Messaging

Buyer initiates through first message/offer; one conversation per buyer/listing. Plain text, participant access, no message edit/delete; sold/archived history read-only. Draft retention on retry/error/cancelled navigation, not durable drafts.

- Source: [service/ChatService.java](../src/main/java/hotshop/service/ChatService.java): `messageSeller, sendMessage, openChatWithSeller, openConversation`.
- Existing assertions: [service/ChatServiceTest.java](../src/test/java/hotshop/service/ChatServiceTest.java): `openChatWithSeller_noConversation_returnsEmpty`, `sendMessage_cancelledSale_allowsSendingAgain`.

## INV-14 — Conversation list/actions

Pending-offer/active-sale group first, then unread/recent. Unread messages plus relevant offer events; open/send mark read. Refresh, previews and embedded offer/sale/meetup controls; no realtime or sale/meetup notification count.

- Source: [service/ChatService.java](../src/main/java/hotshop/service/ChatService.java): `summary, unreadOfferEvents, preview`.
- Existing assertions: [service/ChatServiceTest.java](../src/test/java/hotshop/service/ChatServiceTest.java): `getConversations_sameGroup_listsUnreadThenLatestActivity`, `openConversation_unreadMessages_marksEverythingRead`.

## INV-15 — Seller dashboard

Own pending offers, active/completed sales, completed agreed value and upcoming seller meetups, with listing/sales links. Upcoming excludes exact start time (source-traced boundary).

- Source: [service/TransactionService.java](../src/main/java/hotshop/service/TransactionService.java): `getSalesDashboard`.
- Existing assertions: [service/TransactionServiceTest.java](../src/test/java/hotshop/service/TransactionServiceTest.java): `getSalesDashboard_mixedSales_countsSalesAndTotalsCompletedOnly`, `getSalesDashboard_buyer_countsOnlyTheirOwnListings`.

## INV-16 — Shared interface behaviour

Back, dirty-form/draft prompts, loading and duplicate-submit guards, retained input, errors/empty/retry states, keyboard targets and wrapping layout. Desktop minimum size is not a mobile-web acceptance criterion.

- Source: [ui/MarketplaceUi.java](../src/main/java/hotshop/ui/MarketplaceUi.java): `navigate, back, canLeave, logout`.
- Existing assertions: [ui/MarketplaceUiTest.java](../src/test/java/hotshop/ui/MarketplaceUiTest.java): `navigate_dirtyProfile_cancelKeepsInputAndDiscardLeavesWithoutSaving`, `sidebar_shortViewport_scrollsToLogoutWithWhiteBackground`.

## Boundaries to retain during detailed specification

- Usernames: 3–30 ASCII letters/digits/underscore, case-insensitively unique.
  Display names: 1–80 Unicode code points. Passwords: 8–128 code points with
  uppercase, lowercase, digit and printable ASCII symbol; preserve case/whitespace.
- Listing text: trimmed title 1–120, description 1–5000, pickup 1–200 Unicode
  code points. Six categories and five conditions. Listing/offer amounts are SGD
  integer cents, 1–100,000,000; search price bounds can include zero.
- Profile image: readable JPEG/PNG, at most 5 MiB and 512 pixels per dimension.
  Listing: 0–10 ordered readable JPEG/PNG images, each at most 10 MiB and 4096
  pixels per dimension. Stored imports are not automatically cropped/resized.
- Meetup: strictly future start through local calendar day +60; end can reach day61.
  Duration 15 minutes–4 hours; trimmed location 1–200 code points. Touching intervals
  do not overlap. UI has quarter-hour starts and eight fixed durations; services
  enforce broader interval bounds. A pending move hides UI cancellation, but the
  service permits cancelling and withdraws the proposal. UI hides move/cancel after
  the meetup ends; the service uses scheduled status rather than the end-time guard.
  Accepting a move does not recheck that its proposed start is still future.
  These differences require an explicit target rule, not accidental copying.
- Messages: trimmed 1–1000 Unicode code points, plain text; Enter sends and
  Shift+Enter inserts a newline. Unread offer news is at most one relevant latest
  event per offer, rather than a full event-notification feed.
- Sale snapshot includes title, description, condition and agreed price only.
  Counterpart profiles and other listing fields are not all frozen snapshots.

## Shared infrastructure and excluded future work

[AuthenticatedSession](../src/main/java/hotshop/service/AuthenticatedSession.java)
requires an identity; individual services enforce ownership and participation.
[PublicProfile](../src/main/java/hotshop/service/PublicProfile.java) excludes private
pickup preferences and credentials. The existing global session is desktop state,
not a selected web authentication mechanism.

[ServiceWorker](../src/main/java/hotshop/service/ServiceWorker.java) serialises operations.
[Database.executeTransaction](../src/main/java/hotshop/database/Database.java) commits
or rolls back a business operation. [DatabaseTest](../src/test/java/hotshop/database/DatabaseTest.java)
asserts uniqueness for pending offers, active sales and pending requests/proposals.
[ApplicationRuntimeTest](../src/test/java/hotshop/ApplicationRuntimeTest.java) covers
exclusive data-folder locking, schema rejection and queued shutdown work. These are
existing assertions, not executed checks. Web concurrent requests need their own
technical design; resettable data does not waive consistent state or permissions.

[Future Work](../docs/developerGuide/FutureWork.md) remains excluded from parity:
wishlist, notifications and the four documented enhancements. UiControls.future
creates disabled controls; SearchPage uses a null category prompt and resets filter
expansion; MeetupPages uses fixed durations. Password recovery and account deletion
are unavailable. No cart, checkout/payment, shipping, quantity tracking or separate
buyer/seller account type is inferred from the marketplace name.

## Documentation discrepancies and verification limits

The inventory records the direct-listing access and UI/service meetup distinctions.
HotShop_Architecture incorrectly says opening chat creates a conversation and still
qualifies meetup cancellation integration as future work. The current service code,
assertions and feature guides establish first-message/offer creation and implemented
sale/meetup closure. Historical architecture links to absent design documents should
not be treated as implementation evidence; use the current Developer Guide chapters.
These findings are recorded here; source code and old architecture text were not edited.

[Manual testing](../docs/developerGuide/ManualTesting.md) describes the integrated
listing→chat→offer→sale→meetup→completion flow and refusal scenarios. It was read,
not executed. Existing MarketplaceUiTest/MeetupBarTest/SearchStateTest assertions
cover real UI paths, but full accessibility, every window/keyboard path, all failure
combinations and target browser behaviour are not established by this inspection.
Separate MP3 assignment requirements remain outside this existing-feature inventory.
