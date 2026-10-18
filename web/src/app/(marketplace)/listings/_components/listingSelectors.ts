import type { Listing, ListingCardData } from '@/app/_lib/types';

const OWNER_ORDER = { RESERVED: 0, AVAILABLE: 1, SOLD: 2, ARCHIVED: 3 } as const;

function newestFirst(left: Listing, right: Listing): number {
  return Date.parse(right.createdAt) - Date.parse(left.createdAt) || left.id.localeCompare(right.id);
}

/** Sort only the current user's authorized collection; this filter is not authorization. */
export function ownListings(cards: readonly ListingCardData[], viewerId: string): readonly ListingCardData[] {
  return cards.filter(card => card.listing.sellerId === viewerId)
    .sort((left, right) => OWNER_ORDER[left.listing.status] - OWNER_ORDER[right.listing.status]
      || newestFirst(left.listing, right.listing));
}

export function availableListings(listings: readonly Listing[], options: {
  readonly sellerId?: string; readonly excludeSellerId?: string;
} = {}): readonly Listing[] {
  return listings.filter(item => item.status === 'AVAILABLE'
    && (options.sellerId === undefined || item.sellerId === options.sellerId)
    && item.sellerId !== options.excludeSellerId).sort(newestFirst);
}

// BACKEND INTEGRATION: supply pending counts, meetup summaries and available actions with
// authorized feature reads. The client must not reconstruct them from a global record graph.
