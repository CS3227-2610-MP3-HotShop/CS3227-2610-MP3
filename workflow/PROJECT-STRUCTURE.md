# React / Next.js application structure

Approved by the user on 2026-10-19. Scope: copy the reusable frontend sources
into `web/src/app/` and adapt the copies for Next.js App Router. Preserve every
original under `workflow/migrations/` until the user confirms the result.
The application need not be operational; missing pages and backend integration
remain unwired. This is structural integration, not feature acceptance or approval
to implement additional marketplace behaviour. No functionality tests are requested.

## Folder responsibilities

```text
web/src/app/
├── layout.tsx, page.tsx, globals.css, favicon.ico  # Existing Next.js scaffold
├── (account)/
│   ├── login/
│   └── register/
├── (marketplace)/
│   ├── search/
│   ├── listings/
│   │   ├── _components/                          # Listing rendering and selectors
│   │   ├── new/
│   │   └── [listingId]/
│   │       ├── edit/
│   │       └── chat/
│   ├── offers/
│   ├── purchases/
│   ├── sales/[saleId]/
│   ├── dashboard/
│   ├── conversations/[conversationId]/
│   ├── profile/
│   └── profiles/[userId]/
├── _components/                                 # Shared UI and submission hook
├── _navigation/                                 # Shell, navigation, route metadata
├── _providers/                                  # Explicit application bindings
├── _lib/                                        # Frontend types and helpers
└── _styles/                                     # Copied theme and component CSS
```

Parentheses group pages without adding a URL segment. `(account)` holds the
sidebar-free login and registration locations (INV-01). `(marketplace)` groups
authenticated marketplace locations. These groups separate layout responsibilities;
they do not enforce authorization. Private underscore folders are excluded from
routing. Square-bracket folders represent resource IDs, replacing React Router's
`:listingId`, `:saleId`, `:conversationId` and `:userId` segments.

The route directories contain README files reserving their purpose, rather than
invented page implementations. A directory is not an active route until it has a
Next.js `page.tsx` or `route.ts`. The existing root scaffold is retained. The copied
listing page components are available for future thin route entry points after real
application bindings exist. There are no fake sessions, requests or demo records.

| Folder | Contents and justification |
| --- | --- |
| `(account)/login`, `register` | Reserved authentication pages (INV-01), separate from the marketplace shell. |
| `(marketplace)/search` | Search/filter/sort page location (INV-03); no search page was supplied. |
| `(marketplace)/listings` | My Listings and resource details (INV-04/06); `new` and `[listingId]/edit` reserve listing editing (INV-05). Listing-only UI and selectors stay in `_components` to keep shared UI independent of this feature. |
| `(marketplace)/listings/[listingId]/chat` | Listing enquiry entry location (INV-13); it is not evidence of an implemented messaging workflow. |
| `(marketplace)/offers` | Buyer offer history location (INV-07). Seller offer handling (INV-08) remains part of future feature design; no additional URL is invented. |
| `(marketplace)/purchases`, `sales/[saleId]` | Purchase/sale history and sale details locations (INV-09/10), including future meetup integration (INV-11/12). Meetups have no standalone route in the supplied route inventory. |
| `(marketplace)/dashboard` | Seller summary location (INV-15). |
| `(marketplace)/conversations/[conversationId]` | Conversation list and detail locations (INV-13/14). |
| `(marketplace)/profile`, `profiles/[userId]` | Own-profile editing and another participant's public profile locations (INV-02). |
| `_components` | Forms, dialogs, headings, loading/feedback presentation, image fallback and submission mechanics used across features (INV-16). |
| `_navigation` | Next.js links/history, account/application shells, safe route feedback, and screen/title/access-result metadata. Access-result helpers are presentation, not server authorization. |
| `_providers` | ApplicationProvider contracts and ApplicationBoundary composition. A future client adapter supplies real bindings; callback-bearing bindings must not be serialized across a server/client boundary. Session changes remount navigation, dialogs and drafts. |
| `_lib` | Frontend read types, validation, formatting, confirmation/result vocabulary and loading hook. These remain presentation contracts, not database schemas or replacements for `web/src/domain` and `web/src/server`. |
| `_styles` | Theme tokens, global control/shell rules and listing styles. Keep the incoming visual assets; no Tailwind rewrite is included. Styles are staged for import at the application layout when that layout is wired. |

## Incoming source mapping

Paths on the left are relative to `workflow/migrations/`; destinations are relative
to `web/src/app/`. Both files named `validation.ts` are retained with distinct names
because one contains field validators and the other contains runtime value guards.

| Incoming source | Destination / treatment |
| --- | --- |
| `unchanged/app/navigation.tsx` | `_navigation/navigation.tsx`; adapt document-local Back tracking to Next.js navigation. |
| `unchanged/components/DialogHost.tsx` | `_components/DialogHost.tsx`; adapt route-change observation. |
| `unchanged/components/FormField.tsx` | `_components/FormField.tsx`; mark interactive client boundary. |
| `unchanged/components/PageHeading.tsx` | `_components/PageHeading.tsx`; adapt route-change focus and Back import. |
| `unchanged/presentation/format.ts` | `_lib/format.ts`. |
| `unchanged/presentation/validation.ts` | `_lib/validation.ts` (field validation). |
| `unchanged/styles/tokens.css` | `_styles/tokens.css`. |
| `unchanged/styles/listings.css` | `_styles/listings.css`. |
| `edited/app/ApplicationProvider.tsx` | `_providers/ApplicationProvider.tsx`; retain explicit backend seams. |
| `edited/app/AppShell.tsx` | `_navigation/AppShell.tsx`; use children, Next.js links and redirects instead of outlets. |
| `edited/app/RouteFeedback.tsx` | `_navigation/RouteFeedback.tsx`; use Next.js links; not installed as route error/not-found files yet. |
| `edited/app/routeAccess.ts` | `_navigation/routeAccess.ts`. |
| `edited/app/loadPage.ts` | `_lib/loadPage.ts`. |
| `edited/app/routes.tsx` | `_navigation/routes.ts`; retain URL/screen metadata, replace executable React Router registration with filesystem locations documented here. |
| `edited/components/Feedback.tsx` | `_components/Feedback.tsx`. |
| `edited/components/ImageFrame.tsx` | `_components/ImageFrame.tsx`; preserve authorized URL resolver and fallback, without selecting storage/image optimization policy. |
| `edited/components/ListingCard.tsx` | `(marketplace)/listings/_components/ListingCard.tsx`; Next.js link. |
| `edited/components/PageFrame.tsx` | `_components/PageFrame.tsx`. |
| `edited/components/useSubmission.ts` | `_components/useSubmission.ts`. |
| `edited/features/listings/ListingPages.tsx` | `(marketplace)/listings/_components/ListingPages.tsx`; Next.js links and route params. |
| `edited/presentation/actions.ts` | `_lib/actions.ts`; cross-feature confirmation vocabulary. |
| `edited/presentation/listingSelectors.ts` | `(marketplace)/listings/_components/listingSelectors.ts`. |
| `edited/presentation/meetupTime.ts` | `_lib/meetupTime.ts`; shared meetup presentation. |
| `edited/shared/types.ts` | `_lib/types.ts`; frontend projections only. |
| `edited/shared/validation.ts` | `_lib/valueGuards.ts` (runtime value guards). |
| `edited/styles/global.css` | `_styles/global.css`; remove obsolete React-root selector. |
| `edited/main.tsx` | Replaced by `_providers/ApplicationBoundary.tsx` composition; Next.js owns DOM mounting, StrictMode configuration and routing. No `createRoot`/BrowserRouter entry point is copied. |
| `edited/tsconfig.json` | Not copied: `web/tsconfig.json` already configures this Next.js project. |
| `edited/README.md` | Not copied as application source: this document records the target mapping and integration limits. |

## Integration boundaries and verification

Use `next/link` and `next/navigation`; do not add React Router. Interactive copies
have explicit client boundaries; pure types and helpers remain framework independent.
Use the existing `@/*` alias for relocated imports rather than depending on staging
folder paths. Next.js layouts pass children instead of rendering React Router outlets.

ApplicationBoundary composes ApplicationProvider and NavigationBoundary only when a
real client adapter supplies ApplicationBindings. The provider has no default data.
Do not mount the copied pages or RequireSession without this boundary. Backend access
checks, authentication, durable mutations, image routing and missing page implementations
remain separate work. An unavailable backend must not be replaced by preview data.

For future mounting, a client adapter wraps account children in ApplicationBoundary
and AccountLayout, and marketplace children in ApplicationBoundary, RequireSession
and AppShell. Keep the boundary mounted across navigation within its layout so known
Back entries can be tracked. ApplicationBoundary supplies Suspense for query-aware
navigation hooks. Imperative navigation uses `useApplicationRouter()` so replacements
are identified by the Back tracker; ordinary links use `next/link`. This is a UI
composition pattern, not an authentication boundary on the server. Import styles in
the order `_styles/tokens.css`, `_styles/global.css`, `_styles/listings.css` when the
application layout is wired; the existing root scaffold stylesheet remains active
until then. Neither the old `main.tsx` nor its React Router route factory is a future
entry point.

The old route inventory determines the reserved paths; it does not expand the approved
feature inventory. No route is created for reset, wishlist, notifications, account
deletion or password recovery. No INV group is declared Done by this task.

For this task, inspect copied-file coverage, import targets, Next.js conventions,
source preservation and the final diff. Do not run functionality tests, launch the
application, connect a database or claim runtime acceptance. Verification results belong
in the task log, not implied by the directory structure.
