export type Id = string;
export type InstantText = string;
export type MoneyCents = number;
export type Category = 'ELECTRONICS' | 'BOOKS' | 'CLOTHING' | 'FURNITURE' | 'SPORTS' | 'OTHER';
export type Condition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'POOR';
export type ListingStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD' | 'ARCHIVED';
export type OfferStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN';
export type SaleStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type RequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN';
export type MeetupStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';

// BACKEND INTEGRATION: map stored images to these display references; no storage provider is assumed.
export interface ImageRef {
  readonly id: Id;
  readonly path: string;
  readonly alt: string;
  readonly width: number;
  readonly height: number;
  readonly format: 'JPEG' | 'PNG';
}

export interface PublicProfileView {
  readonly id: Id;
  readonly displayName: string;
  readonly profileImage: ImageRef | null;
}

// BACKEND INTEGRATION: supply this private projection only for the authenticated account.
export interface CurrentUser extends PublicProfileView {
  readonly username: string;
  readonly preferredPickupLocation: string | null;
}

export interface ListingDetails {
  readonly title: string;
  readonly description: string;
  readonly pickupLocation: string;
  readonly category: Category;
  readonly condition: Condition;
  readonly priceCents: MoneyCents;
}

export interface Listing {
  readonly id: Id;
  readonly sellerId: Id;
  readonly details: ListingDetails;
  readonly images: readonly ImageRef[];
  readonly status: ListingStatus;
  readonly createdAt: InstantText;
  readonly updatedAt: InstantText;
}

export interface Offer {
  readonly id: Id;
  readonly listingId: Id;
  readonly buyerId: Id;
  readonly amountCents: MoneyCents;
  readonly status: OfferStatus;
  readonly createdAt: InstantText;
  readonly closedAt: InstantText | null;
}

export interface CancellationRequest {
  readonly id: Id;
  readonly transactionId: Id;
  readonly requesterId: Id;
  readonly createdAt: InstantText;
  readonly status: RequestStatus;
  readonly resolvedAt: InstantText | null;
}

export interface Sale {
  readonly id: Id;
  readonly listingId: Id;
  readonly acceptedOfferId: Id;
  readonly buyerId: Id;
  readonly sellerId: Id;
  readonly agreedPriceCents: MoneyCents;
  readonly listingTitle: string;
  readonly listingDescription: string;
  readonly listingCondition: Condition;
  readonly status: SaleStatus;
  readonly createdAt: InstantText;
  readonly lastEventAt: InstantText;
  readonly buyerConfirmedAt: InstantText | null;
  readonly sellerConfirmedAt: InstantText | null;
  readonly cancelledAt: InstantText | null;
  readonly cancelledBy: Id | null;
  readonly cancellationRequests: readonly CancellationRequest[];
}

export interface Message {
  readonly id: Id;
  readonly conversationId: Id;
  readonly senderId: Id;
  readonly sequence: number;
  readonly text: string;
  readonly sentAt: InstantText;
}

export interface ViewerSummary {
  readonly unreadCount: number;
  readonly preview: string;
  readonly lastActivityAt: InstantText;
}

export interface Conversation {
  readonly id: Id;
  readonly listingId: Id;
  readonly buyerId: Id;
  readonly sellerId: Id;
  readonly createdAt: InstantText;
  readonly messages: readonly Message[];
  // BACKEND INTEGRATION: return only the requesting participant's summary.
  readonly viewerSummary: ViewerSummary;
}

export interface MeetupTime {
  readonly startAt: InstantText;
  readonly endAt: InstantText;
  readonly location: string;
}

export interface MeetupSlot {
  readonly id: Id;
  readonly transactionId: Id;
  readonly time: MeetupTime;
  readonly createdAt: InstantText;
}

export interface MoveProposal {
  readonly id: Id;
  readonly meetupId: Id;
  readonly proposerId: Id;
  readonly time: MeetupTime;
  readonly createdAt: InstantText;
  readonly status: RequestStatus;
  readonly resolvedAt: InstantText | null;
}

export interface Meetup {
  readonly id: Id;
  readonly transactionId: Id;
  readonly buyerId: Id;
  readonly sellerId: Id;
  readonly time: MeetupTime;
  readonly status: MeetupStatus;
  readonly createdAt: InstantText;
  readonly lastEventAt: InstantText;
  readonly proposals: readonly MoveProposal[];
}

// These are UI inputs, not a wire protocol or persistence schema.
export interface OwnerListingSummary {
  readonly pendingOfferCount: number;
  readonly meetup: MeetupTime | null;
  readonly offeredTimeCount: number;
}

export interface ListingCardData {
  readonly listing: Listing;
  readonly ownerSummary: OwnerListingSummary | null;
}

export interface ListingActions {
  readonly edit: boolean;
  readonly archive: boolean;
  readonly delete: boolean;
  readonly offer: boolean;
  readonly withdrawOffer: boolean;
  readonly chat: boolean;
}

export interface ListingDetailsData {
  readonly listing: Listing;
  readonly seller: PublicProfileView;
  readonly actions: ListingActions;
  readonly pendingOffer: Pick<Offer, 'id' | 'amountCents'> | null;
  readonly conversationId: Id | null;
  // BACKEND INTEGRATION: owner-only counts must be omitted from non-owner reads.
  readonly ownerMetadata: { readonly pendingOfferCount: number; readonly conversationCount: number } | null;
}
