'use client';

import { useCallback, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useApplication } from '@/app/_providers/ApplicationProvider';
import { usePageLoad } from '@/app/_lib/loadPage';
import type { PageResult } from '@/app/_lib/loadPage';
import { PageFrame } from '@/app/_components/PageFrame';
import { ImageFrame } from '@/app/_components/ImageFrame';
import { ListingCard } from '@/app/(marketplace)/listings/_components/ListingCard';
import { Feedback } from '@/app/_components/Feedback';
import type { FeedbackValue } from '@/app/_components/Feedback';
import { useSubmission } from '@/app/_components/useSubmission';
import { formatMoney, formatTitle } from '@/app/_lib/format';
import { confirmationFor } from '@/app/_lib/actions';
import type { SubmissionResult } from '@/app/_lib/actions';
import { ownListings } from '@/app/(marketplace)/listings/_components/listingSelectors';
import type { ListingCardData, ListingDetailsData } from '@/app/_lib/types';

function feedbackFor(result: SubmissionResult): FeedbackValue | null {
  if (result.code === 'CANCELLED') return null;
  if (result.code === 'FIELD_ERRORS') return { kind: 'error', message: 'Check the highlighted fields in the form.' };
  return { kind: result.code === 'SUCCESS' ? 'success' : 'error', message: result.message };
}

export function MyListingsPage() {
  const { session, listings: bindings, timeZone, resolveImageUrl } = useApplication();
  const viewerId = session.state === 'ready' ? session.user?.id ?? '' : '';
  const sessionKey = session.state === 'ready' ? session.key : session.state;
  const load = useCallback(async (signal: AbortSignal): Promise<PageResult<readonly ListingCardData[]>> => {
    // BACKEND INTEGRATION: the injected loader must read only this authenticated user's listings.
    const result = await bindings.loadOwnListings(signal);
    if (result.state !== 'ready') return result;
    const data = ownListings(result.data, viewerId);
    return data.length === 0 ? { state: 'empty', message: 'You have no listings yet. Create your first listing.' }
      : { state: 'ready', data };
  }, [bindings.loadOwnListings, viewerId]);
  const { state, retry } = usePageLoad(`${sessionKey}:${viewerId}:listings`, load);
  return <PageFrame title="My Listings" state={state} onRetry={retry}
    actions={<Link className="button-link primary" href="/listings/new">Create Listing</Link>}>
    {state.state === 'ready' && <div className="listing-content">
      <div className="listing-grid">{state.data.map(data => <ListingCard key={data.listing.id} data={data}
        href={`/listings/${encodeURIComponent(data.listing.id)}`} timeZone={timeZone} resolveImageUrl={resolveImageUrl} />)}</div>
    </div>}
  </PageFrame>;
}

export function ListingDetailsPage() {
  const { session, listings: bindings } = useApplication();
  const { listingId } = useParams<{ listingId: string }>();
  const sessionKey = session.state === 'ready' ? session.key : session.state;
  const load = useCallback((signal: AbortSignal): Promise<PageResult<ListingDetailsData>> => {
    if (!listingId) return Promise.resolve({ state: 'empty', message: 'This listing was not found.' });
    // BACKEND INTEGRATION: map not-found/forbidden responses in this resource's injected loader.
    return bindings.loadListing(listingId, signal);
  }, [bindings.loadListing, listingId]);
  const { state, retry } = usePageLoad(`${sessionKey}:listing:${listingId ?? ''}`, load);
  const refresh = useCallback(async () => {
    // BACKEND INTEGRATION: invalidate related backend-backed summaries, then reread this listing.
    try { await bindings.onChanged(); } finally { retry(); }
  }, [bindings.onChanged, retry]);
  return <PageFrame title="Listing Details" state={state} onRetry={retry}>
    {state.state === 'ready' && <ListingDetails key={state.data.listing.id} data={state.data} onChanged={refresh} />}
  </PageFrame>;
}

function ListingDetails({ data, onChanged }: {
  readonly data: ListingDetailsData; readonly onChanged: () => Promise<void>;
}) {
  const { session, resolveImageUrl } = useApplication();
  const { listing, seller } = data;
  const owner = session.state === 'ready' && session.user?.id === listing.sellerId;
  return <div className="listing-content">
    <h2 className="listing-detail-title">{listing.details.title}</h2>
    <p className="price">{formatMoney(listing.details.priceCents)}</p>
    <p><span className="badge">{formatTitle(listing.status)}</span></p>
    <div className="listing-gallery" aria-label="Listing photos">
      {listing.images.length === 0 ? <ImageFrame image={null} resolveImageUrl={resolveImageUrl} detail />
        : listing.images.map(image => <ImageFrame key={image.id} image={image} resolveImageUrl={resolveImageUrl} detail />)}
    </div>
    <p className="listing-description">{listing.details.description}</p>
    <p className="muted">Category: {formatTitle(listing.details.category)}</p>
    <p className="muted">Condition: {formatTitle(listing.details.condition)}</p>
    <p className="muted">Pickup: {listing.details.pickupLocation}</p>
    <p><Link href={owner ? '/profile' : `/profiles/${encodeURIComponent(seller.id)}`}>{seller.displayName}</Link></p>
    {owner ? <OwnerActions data={data} onChanged={onChanged} /> : <BuyerActions data={data} onChanged={onChanged} />}
  </div>;
}

function OwnerActions({ data, onChanged }: {
  readonly data: ListingDetailsData; readonly onChanged: () => Promise<void>;
}) {
  const { session, listings: bindings } = useApplication();
  const [feedback, setFeedback] = useState<FeedbackValue | null>(null);
  const { listing, actions } = data;
  const sessionKey = session.state === 'ready' ? session.key : session.state;
  const details = {
    conversationCount: data.ownerMetadata?.conversationCount,
    pendingOfferCount: data.ownerMetadata?.pendingOfferCount,
  };
  const common = {
    onSuccess: onChanged, onResult: (result: SubmissionResult) => { setFeedback(feedbackFor(result)); },
  };
  const archive = useSubmission({
    ...common, targetKey: `${sessionKey}:${listing.id}:${listing.updatedAt}:archive`,
    available: actions.archive, confirmation: confirmationFor('ARCHIVE_LISTING', details),
    // BACKEND INTEGRATION: persist archive/rejection changes atomically and return their real outcome.
    execute: () => bindings.archiveListing(listing.id),
  });
  const remove = useSubmission({
    ...common, targetKey: `${sessionKey}:${listing.id}:${listing.updatedAt}:delete:${details.conversationCount ?? ''}`,
    available: actions.delete, confirmation: confirmationFor('DELETE_LISTING', details),
    // BACKEND INTEGRATION: authorize deletion and handle managed image/conversation cleanup.
    execute: () => bindings.deleteListing(listing.id),
  });
  const busy = archive.busy || remove.busy;
  return <>
    <div className="form-actions">
      {actions.edit && !busy ? <Link className="button-link" href={`/listings/${encodeURIComponent(listing.id)}/edit`}>Edit Listing</Link>
        : <button type="button" disabled aria-describedby="listing-owner-hint">Edit Listing</button>}
      <button type="button" disabled={!actions.archive || busy} aria-describedby="listing-owner-hint"
        onClick={archive.submit}>Archive Listing</button>
      <button type="button" disabled={!actions.delete || busy} aria-describedby="listing-owner-hint"
        onClick={remove.submit}>Delete Listing</button>
    </div>
    <p id="listing-owner-hint" className="hint">Only available listings can be edited. Reserved listings cannot
      be archived. Deletion requires an available/archived listing without offer history.
      Archived listings cannot be archived again.</p>
    <Feedback value={feedback} />
  </>;
}

function BuyerActions({ data, onChanged }: {
  readonly data: ListingDetailsData; readonly onChanged: () => Promise<void>;
}) {
  const { session, listings: bindings } = useApplication();
  const [feedback, setFeedback] = useState<FeedbackValue | null>(null);
  const { listing, actions, pendingOffer, conversationId } = data;
  const sessionKey = session.state === 'ready' ? session.key : session.state;
  const key = `${sessionKey}:${listing.id}:${listing.updatedAt}`;
  const onResult = (result: SubmissionResult) => { setFeedback(feedbackFor(result)); };
  const offer = useSubmission({
    targetKey: `${key}:offer`, available: actions.offer, onSuccess: onChanged, onResult,
    // BACKEND INTEGRATION: open the actual offer form; that form validates and submits its payload.
    execute: () => bindings.makeOffer(listing.id),
  });
  const withdraw = useSubmission({
    targetKey: `${key}:withdraw:${pendingOffer?.id ?? ''}`,
    available: actions.withdrawOffer && pendingOffer !== null, onSuccess: onChanged, onResult,
    // BACKEND INTEGRATION: withdraw this user's pending offer, then refetch the current state.
    execute: () => pendingOffer === null ? Promise.resolve({ code: 'UNAVAILABLE', message: 'No pending offer.' })
      : bindings.withdrawOffer(pendingOffer.id),
  });
  const chat = useSubmission({
    targetKey: `${key}:chat:${conversationId ?? ''}`, available: actions.chat, onResult,
    // BACKEND INTEGRATION: the handler creates/retrieves the conversation and handles navigation.
    execute: () => bindings.openChat(listing.id, conversationId),
  });
  const busy = offer.busy || withdraw.busy || chat.busy;
  return <>
    <div className="form-actions">
      <button type="button" disabled={!actions.chat || busy} aria-describedby="listing-chat-hint"
        onClick={chat.submit}>Chat with seller</button>
      <button type="button" disabled aria-describedby="listing-wishlist-hint">Save to wishlist</button>
    </div>
    {!actions.chat && <p id="listing-chat-hint" className="hint">A conversation is unavailable for this listing.</p>}
    <p id="listing-wishlist-hint" className="hint">Wishlist: Coming soon.</p>
    {pendingOffer !== null ? <>
      <p className="price">Your pending offer: {formatMoney(pendingOffer.amountCents)}</p>
      <div className="form-actions"><button type="button" disabled={!actions.withdrawOffer || busy}
        onClick={withdraw.submit}>Withdraw Offer</button></div>
    </> : <>
      <div className="form-actions"><button type="button" className="primary" disabled={!actions.offer || busy}
        aria-describedby="listing-offer-hint" onClick={offer.submit}>Make Offer</button></div>
      {!actions.offer && <p id="listing-offer-hint" className="hint">Offers are unavailable for this listing.</p>}
    </>}
    <Feedback value={feedback} />
  </>;
}
