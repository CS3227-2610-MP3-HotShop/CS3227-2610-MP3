# HotShop web migration product specification

Status: draft. Revision date 2026-10-17 (SGT).
Source baseline: `1d812ef270d780d248c74f7a37c543345df70026` (frozen 2026-10-17).
Scope reference: [feature inventory](FEATURE-INVENTORY.md), coverage approved 2026-10-17.
Evidence for INV-01 to INV-16: [feature evidence](FEATURE-EVIDENCE.md).

This document details features incrementally. A feature is inventory-only until its
detail is drafted here, and drafted until the user approves it. Approval of one feature
does not approve another, and no approval here authorises implementation.

INV-17 was approved by the user on 2026-10-17. Its open items listed below are technical
specification decisions, not unresolved product intent. Its design is drafted in
[`TECHNICAL-SPEC.md`](TECHNICAL-SPEC.md).

## Feature status

| ID | Feature group | Detail status |
| --- | --- | --- |
| INV-01 to INV-16 | Existing JavaFX feature groups | Inventory-only |
| INV-17 | Platform and data foundations | Approved 2026-10-17 |

## Product Description

HotShop is a marketplace where people list second-hand items, negotiate a price through
offers, message each other about a listing, arrange a handover, and record that the sale
completed. One account both buys and sells. The existing implementation is a JavaFX
desktop application; this specification governs its migration to a TypeScript and
Next.js web application deployed on Vercel.

INV-17 covers the layer the desktop application solved through its own runtime and that
a hosted web application must solve differently: how a login is established and ends,
what happens when several people act at once, where data lives and how it gets there,
and the standards for refusals and time display that the rest of the features rely on.
It is not a screen feature. It exists as an inventory group so that this work has an
owner, an issue and a completion gate rather than living only in cross-cutting prose.

Success for INV-17 means every other feature can be specified and built without
rediscovering these answers, and that a visitor to the deployed application finds data
that is still there from last time.

## Users and Roles

- **Marketplace participant.** Every signed-in user. Buys and sells with the same
  account. There is no separate buyer or seller account type, and no administrator role
  inside the application.
- **Operator.** Provisions the database and runs seeding. This is a deployment activity,
  not an in-application role, and no screen exposes it.

Permissions are unchanged from the source: ownership and participation checks govern
every listing, offer, sale, meetup and conversation, and private profile preferences are
never exposed through public profile access. INV-17 adds no new permission.

## Functional Requirements

### Session and identity

- **FR-1701.** A successful login establishes a session for that browser. The session
  persists until the user logs out, and survives closing and reopening the browser.
  There is no inactivity expiry.
- **FR-1702.** The same account may hold sessions in more than one browser at the same
  time. Signing in elsewhere does not end an existing session.
- **FR-1703.** Logging out ends only the session that performed the logout. Other
  sessions for that account remain signed in.
- **FR-1704.** Every operation acts as the session's own user. A request carries no
  user identifier chosen by the client, matching the source rule that screens never pass
  a user ID to a service.
- **FR-1705a.** Every marketplace surface requires a signed-in user, including search and
  listing details, as in the source application. A signed-out visitor reaches only the
  login and registration surfaces. There is no anonymous browsing.

### Data persistence and seed content

- **FR-1705.** Marketplace data persists durably across deployments and restarts. The
  application provides no reset control, on any page, to any user. Provisioning and
  re-provisioning an environment's database happens outside the application and is an
  operator activity, not a feature.
- **FR-1706.** Seeding runs once when an environment is provisioned. It creates demo
  accounts and listings, all in the available status. It creates no offers, sales,
  meetups or conversations. Reserved, sold and archived listings are reached only
  through the application's own operations, so every state a visitor sees was produced
  by a real flow.
- **FR-1706a.** Seeding is a provisioning step, not an application route, and is
  idempotent: running it against an already-seeded environment neither duplicates seed
  content nor removes data created since. It never restores an environment to its seed,
  because that would be a reset.
- **FR-1707.** Demo account credentials are documented in the User Guide and the
  repository README. No page in the application displays them.
- **FR-1708.** Existing MP2 and desktop data is not imported. Accounts, listings,
  images and history in any local `.hotshop` folder are irrelevant to the web
  application.

### Concurrent access and consistency

- **FR-1709.** Marketplace invariants hold when several users act at the same time.
  Specifically: at most one pending offer per buyer and listing; at most one active sale
  per listing; at most one pending cancellation request per sale; at most one pending
  move proposal per meetup; and when two buyers attempt the same offered meetup slot,
  exactly one booking succeeds.
- **FR-1710.** A change affecting several records either applies completely or not at
  all. Accepting an offer reserves the listing, rejects the other pending offers and
  creates the sale as one unit; completing a sale updates the sale, its listing and its
  meetup as one unit; cancelling a sale releases its listing and cancels its meetup as
  one unit; deleting an enquiry-only listing removes its conversations with it.
- **FR-1711.** When two users act at once and one action cannot be applied, the user
  whose action failed receives a refusal naming what changed, using real values, and the
  affected part of the page shows the current state. Nothing is partially applied.

### Refusals

- **FR-1712.** Every refused action explains what is wrong and what to do next, using
  real values from the attempted operation. No refusal exposes internal detail such as
  SQL, stack traces or framework errors.
- **FR-1713.** Refusals distinguish the reason categories the source services already
  separate: invalid input, missing record, not permitted for this user, and not allowed
  in the current state.

### Cross-user update visibility

- **FR-1714.** While a user has a conversation open, a message sent by the other
  participant, and any change to that conversation's offer or meetup state, appears
  without the viewer navigating or activating a control, within a few seconds.
- **FR-1715.** The unread total shown alongside Conversations updates on the same basis,
  without the viewer navigating.
- **FR-1716.** Pages other than an open conversation present the data current at
  navigation, and the existing Refresh controls re-fetch on demand. They do not update
  on their own.
- **FR-1717.** A failed background update does not discard the viewer's unsent draft,
  does not clear the view, and does not interrupt an action in progress.

### Time display and scheduling

- **FR-1718.** All dates and times display in one fixed application timezone, shown with
  a zone label, so both participants of a sale always see the same time string.
- **FR-1719.** Scheduling rules that depend on a date or time are evaluated in that same
  zone rather than in any viewer's zone, and INV-17 owns the evaluation zone and the date
  and time primitives those rules are built from. Within INV-17 that means: a start is in
  the future, the 60-day-ahead cutoff evaluated as calendar days in the application zone,
  and whether a meetup has ended.

  Narrowed on 2026-10-17. The earlier wording also listed the 15-minute start grid,
  durations of 15 minutes to 4 hours and overlap checks. Those are meetup rules and
  belong to INV-11 and INV-12, which consume INV-17's primitives rather than redefining
  the zone. A test-readiness review found them untested under INV-17, and the narrowing
  resolves that rather than widening this group.
- **FR-1720.** Refusal messages about scheduling state times in that same zone and
  label, matching what the screens show.

## UI

INV-17 adds no screen and no navigation entry. Its requirements constrain surfaces that
belong to other features:

- No reset control exists anywhere in the interface (FR-1705).
- No page displays demo credentials (FR-1707).
- Every rendered date or time carries the application zone label (FR-1718).
- An open conversation and the Conversations unread total update without user action;
  the update is silent, with no banner, toast or notification surface, consistent with
  the source application having no notifications (FR-1714, FR-1715).
- Refusals appear where the existing feature shows its errors: inline field errors for
  input, and the feature's own message area for state and permission refusals. INV-17
  sets their content standard, not a new presentation surface (FR-1712).

The login and logout surfaces themselves belong to INV-01 and are specified there.

## Acceptance Criteria

| ID | Requirement | Criterion |
| --- | --- | --- |
| AC-1701 | FR-1701 | Given a signed-in user, when the browser is closed and reopened at the application, then they are still signed in and no login is requested. |
| AC-1702 | FR-1701 | Given a signed-in user who has been idle for several hours, when they act, then the action succeeds and they are not returned to login. |
| AC-1703 | FR-1702 | Given an account signed in in browser A, when the same account signs in in browser B, then browser A remains signed in and can still act. |
| AC-1704 | FR-1703 | Given that account signed in in both browsers, when browser B logs out, then browser A remains signed in. |
| AC-1705 | FR-1705 | Given data created by users, when the application is redeployed, then that data is still present. |
| AC-1706 | FR-1705 | Given any page and any signed-in user, when the interface is inspected, then no control resets, clears or reseeds marketplace data. |
| AC-1707 | FR-1706 | Given a newly provisioned environment, when a visitor opens search, then seeded listings from seeded accounts are present, every seeded listing is available, and My Offers, My Sales, My Purchases and Conversations are empty for every seeded account. |
| AC-1708 | FR-1707 | Given the deployed application, when every page is inspected signed out and signed in, then no demo password appears. |
| AC-1708a | FR-1705a | Given a signed-out visitor, when they request a marketplace address such as search, a listing, a conversation or a sale, then they are not shown marketplace data and are directed to log in. |
| AC-1708b | FR-1706a | Given a seeded environment in which users have since created listings and offers, when seeding runs again, then no seed content is duplicated and no user-created data is removed. |
| AC-1709 | FR-1709 | Given one offered meetup slot and two buyers booking it simultaneously, when both requests are processed, then exactly one booking exists and the other buyer receives a refusal naming the clash. |
| AC-1710 | FR-1709 | Given a buyer with a pending offer on a listing, when two further offers on that listing are submitted at the same time, then no second pending offer is created. |
| AC-1711 | FR-1710 | Given a seller accepting an offer, when the sale record cannot be saved, then the listing remains available and no offer changes status. |
| AC-1712 | FR-1711 | Given a seller accepting offer A while the buyer withdraws it at the same moment, when both are processed, then one succeeds, the other is refused with a message naming the offer's current status, and the listing is in exactly one consistent state. |
| AC-1713 | FR-1712 | Given any refused action, when the message is read, then it states the problem and the next step using values from the attempt, and contains no SQL, stack trace or framework error text. |
| AC-1714 | FR-1714 | Given two participants with the same conversation open, when one sends a message, then it appears for the other within a few seconds without that person navigating or activating a control. |
| AC-1715 | FR-1714 | Given two participants with the same conversation open, when one accepts an offer, then the other's offer and meetup area reflects the new state within a few seconds. |
| AC-1716 | FR-1715 | Given a user on any page, when another participant sends them a message, then the Conversations unread total increases within a few seconds. |
| AC-1717 | FR-1717 | Given a user with an unsent draft in a conversation, when a background update fails, then the draft is intact and the conversation is still readable. |
| AC-1718 | FR-1718 | Given a booked meetup, when the buyer and the seller each view it, then both see the same time string and zone label regardless of their own device settings. |
| AC-1719 | FR-1719 | Given a seller whose device is set to a different zone, when they offer a time at the 60-day boundary, then the cutoff is applied in the application zone, and the refusal or acceptance matches what the buyer would see. |

## Scope of Design and Implementation

Included: session establishment and termination, persistent storage and its one-time
seeding, the integrity constraints that keep marketplace invariants under concurrent
requests, atomic multi-record changes, the refusal content standard, background update
of an open conversation and the unread total, and fixed-zone time display and scheduling
evaluation.

Preserved from the source: all ownership and participation rules, the existing
validation limits, the existing refusal reason categories, and the existing meetup
scheduling bounds.

Verification expectations:

- Automated tests reach reserved, sold and archived listings by performing the operations
  that produce them, not by setting a status value directly in storage. Some existing
  JavaFX listing tests take the direct route; the web tests do not inherit that shortcut.
- Each test run starts from a known database state, through a database created and
  dropped per run, truncation between tests, or a transaction rolled back per test.
  Usernames are globally and case-insensitively unique, so a test that registers a fixed
  username passes once and then fails against a dirty database. Randomised names are not
  the fix; a clean starting state is.

Delegated to technical specification: the managed PostgreSQL provider and its
connection pooling approach, the schema and migration mechanism, how sessions are
represented and secured, image storage location, polling intervals and failure handling,
the configured timezone value, seeding mechanics, and the per-environment database and
test-isolation setup.

The six JavaFX migrations are SQLite SQL executed by a runner that splits on semicolons.
They are a reference for intent, not files to port. The PostgreSQL schema is written
fresh, including the partial unique indexes behind FR-1709 and the image cleanup
constraint. The source rule that a merged migration is never edited or reordered, only
appended to, carries over.

## Out of Scope

- A reset feature of any kind, user-facing, scheduled or operator-triggered from within
  the application. This supersedes the earlier resettable-demo decision.
- Deferring durable storage. PostgreSQL is used from the start, superseding the earlier
  deferral.
- Server-sent events, websockets or any push transport. Background polling is the agreed
  mechanism.
- Per-viewer timezone display or per-viewer evaluation of scheduling rules.
- Administrator screens, account impersonation, and session management surfaces such as
  "sign out my other devices".
- Anonymous browsing of listings or search by signed-out visitors.
- Importing MP2 or desktop data.
- Notifications, wishlist, password recovery and account deletion, which the source
  documents as unavailable or future work.

## Constraints and Quality Requirements

- TypeScript and Next.js, deployed on Vercel, on a migration branch separate from the
  JavaFX source.
- Hosted PostgreSQL from the start. Server state held in memory or on a function's
  filesystem is excluded: on a serverless host it disappears between deployments and
  cold starts, which users experience as an uncontrolled reset.
- Vercel's serverless functions do not host websocket connections, which is one reason
  push was not chosen.
- The desktop minimum window size of 960 x 640 and fixed JavaFX layout units are not
  target criteria; the web layout adapts to arbitrary viewport sizes.
- The single background worker that serialised every desktop operation has no web
  equivalent. Database constraints are the mechanism protecting the invariants in
  FR-1709.
- Each environment has its own database: local development, automated tests, preview
  deployments and the demo. Tests never run against the demo database. Dropping and
  recreating a development or test database is ordinary practice and is not the reset
  that FR-1705 excludes.
- PostgreSQL is used in every environment including tests. A single-writer engine such
  as SQLite cannot produce the write concurrency that FR-1709 exists to guarantee, so
  testing those invariants on one would test the wrong thing.
- Domain timestamps come from an injectable application clock, never from the database
  clock or a column default. The source tests fix time through
  `ApplicationRuntime.open(Path, Clock)`; without the same control, the 60-day cutoff,
  the future-start rule, the meetup-has-ended rule and the dashboard's upcoming-meetup
  boundary cannot be tested without waiting in real time.
- Serverless functions each hold their own database connection and PostgreSQL has a
  hard connection ceiling, so a pooled connection string or an HTTP serverless driver is
  required. This influences provider choice.
- Accepted limitation: because registration is open and there is no reset, the demo
  accumulates whatever visitors leave behind. Archived listings cannot be reopened and
  accounts cannot be deleted, both by parity, so nothing can be tidied from inside the
  application. The only remedy is an operator re-provisioning the database.

## Open Questions and Deferred Decisions

1. Resolved 2026-10-17: seeding creates available listings only. Reserved, sold and
   archived are produced by the application's own operations. Automated tests must reach
   all four statuses by driving those operations rather than by writing a status value
   directly, which is how some existing JavaFX listing tests reach reserved and sold.
   Still open within this item: how many demo accounts, how many listings, and whether
   seeded listings carry photos.
2. Image storage for profile images and listing photos, given that a serverless
   filesystem cannot hold them. Affects INV-02 and INV-05.
3. Resolved 2026-10-17: no anonymous browsing, matching the source. Recorded as
   FR-1705a and AC-1708a.
4. The configured application timezone value.
5. Polling intervals, and whether the unread total polls at a different rate from an
   open conversation.
6. Human owners for frontend and backend areas, which the migration workflow requires
   before implementation.
