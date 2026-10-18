'use client';

import { createContext, Fragment, useContext } from 'react';
import type { ReactNode } from 'react';
import type { CurrentUser, ImageRef, ListingCardData, ListingDetailsData } from '@/app/_lib/types';
import type { PageResult } from '@/app/_lib/loadPage';
import type { SubmissionResult } from '@/app/_lib/actions';

export type SessionState =
  | { readonly state: 'loading' }
  | { readonly state: 'error'; readonly message: string; readonly retry: () => void }
  | { readonly state: 'ready'; readonly user: CurrentUser | null; readonly key: string; readonly unreadCount: number };

export interface ListingBindings {
  // BACKEND INTEGRATION: perform current-user authorized reads; honor AbortSignal and
  // map actual empty/not-found/forbidden responses without returning a global record graph.
  readonly loadOwnListings: (signal: AbortSignal) => Promise<PageResult<readonly ListingCardData[]>>;
  readonly loadListing: (listingId: string, signal: AbortSignal) => Promise<PageResult<ListingDetailsData>>;
  // BACKEND INTEGRATION: implement these authenticated mutations and return their actual outcomes.
  readonly archiveListing: (listingId: string) => Promise<SubmissionResult>;
  readonly deleteListing: (listingId: string) => Promise<SubmissionResult>;
  readonly withdrawOffer: (offerId: string) => Promise<SubmissionResult>;
  // BACKEND INTEGRATION: open the real offer form and submit its validated payload there.
  // Return CANCELLED for a dismissed form; return SUCCESS only after the offer is persisted.
  readonly makeOffer: (listingId: string) => Promise<SubmissionResult>;
  // BACKEND INTEGRATION: create/retrieve an authorized conversation, then navigate to it.
  readonly openChat: (listingId: string, conversationId: string | null) => Promise<SubmissionResult>;
  // BACKEND INTEGRATION: invalidate/refetch affected listing, offer, and unread summaries after mutation.
  readonly onChanged: () => Promise<void>;
}

export interface ApplicationBindings {
  // BACKEND INTEGRATION: derive session from server authentication, refresh/expiry and unread reads.
  // Rotate a ready session's key after login/logout/account changes; never use a URL-selected identity.
  readonly session: SessionState;
  readonly listings: ListingBindings;
  readonly timeZone: string;
  // BACKEND INTEGRATION: resolve an authorized image reference to an actual display URL, or null.
  // Validate allowed URLs/storage references in the adapter; never construct them from route strings.
  readonly resolveImageUrl: (image: ImageRef) => string | null;
  // BACKEND INTEGRATION: terminate the server session; reject on failure.
  readonly logout: () => Promise<void>;
  // BACKEND INTEGRATION: clear account-specific caches and update the supplied session after logout.
  readonly onSessionEnded: () => void;
}

const ApplicationContext = createContext<ApplicationBindings | null>(null);

export function useApplication(): ApplicationBindings {
  const value = useContext(ApplicationContext);
  if (value === null) throw new Error('Application bindings are unavailable.');
  return value;
}

export function ApplicationProvider({ value, children }: {
  readonly value: ApplicationBindings; readonly children: ReactNode;
}) {
  const session = value.session;
  const key = session.state === 'ready' ? `${session.key}:${session.user?.id ?? 'logged-out'}` : session.state;
  // Changing identity discards page/dialog/draft state before another account can see it.
  return <ApplicationContext value={value}><Fragment key={key}>{children}</Fragment></ApplicationContext>;
}
