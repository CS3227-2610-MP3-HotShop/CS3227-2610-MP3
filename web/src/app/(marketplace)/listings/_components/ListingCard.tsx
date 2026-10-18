'use client';

import Link from 'next/link';
import type { ImageRef, ListingCardData } from '@/app/_lib/types';
import { formatMoney, formatTitle } from '@/app/_lib/format';
import { meetupClockRange, meetupDateLines } from '@/app/_lib/meetupTime';
import { ImageFrame } from '@/app/_components/ImageFrame';

export function ListingCard({ data, href, timeZone, resolveImageUrl }: {
  // BACKEND INTEGRATION: supply authorized owner summary only for the listing owner.
  readonly data: ListingCardData;
  readonly href: string;
  readonly timeZone: string;
  readonly resolveImageUrl: (image: ImageRef) => string | null;
}) {
  const { listing, ownerSummary: summary } = data;
  return <Link className={`listing-card${summary === null ? '' : ' owner-card'}`} href={href}
    aria-label={`${listing.details.title}, ${formatMoney(listing.details.priceCents)}`}>
    <ImageFrame image={listing.images[0] ?? null} resolveImageUrl={resolveImageUrl} />
    <span className="listing-card-title" title={listing.details.title}>{listing.details.title}</span>
    <span className="price">{formatMoney(listing.details.priceCents)}</span>
    <span className="listing-card-footer">{summary === null ? formatTitle(listing.details.condition)
      : <><span className="badge">{formatTitle(listing.status)}</span>
        <span>{summary.pendingOfferCount} pending offer{summary.pendingOfferCount === 1 ? '' : 's'}</span></>}</span>
    {summary !== null && <span className="listing-meetup muted">
      {listing.status === 'RESERVED' && (summary.meetup !== null ? <>
        <span className="meetup-dates">{meetupDateLines(summary.meetup, timeZone)}</span>
        <span>{meetupClockRange(summary.meetup, timeZone)}</span>
        <span className="meetup-place" title={summary.meetup.location}>{summary.meetup.location}</span>
      </> : <span>{summary.offeredTimeCount === 0 ? 'No meetup times yet'
        : `${summary.offeredTimeCount} time${summary.offeredTimeCount === 1 ? '' : 's'} offered`}</span>)}
    </span>}
  </Link>;
}
