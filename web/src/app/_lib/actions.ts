import type { FieldErrors } from '@/app/_lib/validation';
import { formatMoney } from '@/app/_lib/format';

export const ACTION_IDS = [
  'REGISTER', 'LOGIN', 'SAVE_PROFILE', 'CHANGE_PASSWORD', 'REPLACE_PROFILE_IMAGE', 'REMOVE_PROFILE_IMAGE',
  'SAVE_LISTING', 'ARCHIVE_LISTING', 'DELETE_LISTING', 'SUBMIT_OFFER', 'WITHDRAW_OFFER', 'ACCEPT_OFFER',
  'REJECT_OFFER', 'CONFIRM_COMPLETION', 'CANCEL_SALE', 'REQUEST_CANCELLATION', 'ACCEPT_CANCELLATION',
  'REJECT_CANCELLATION', 'WITHDRAW_CANCELLATION', 'SEND_MESSAGE', 'OFFER_TIME', 'WITHDRAW_TIME', 'BOOK_TIME',
  'PROPOSE_MOVE', 'ACCEPT_MOVE', 'REJECT_MOVE', 'WITHDRAW_MOVE', 'CANCEL_MEETUP',
] as const;
export type ActionId = typeof ACTION_IDS[number];

export interface Confirmation {
  readonly title: string;
  readonly consequence: string;
}

export type SubmissionResult =
  | { readonly code: 'SUCCESS'; readonly message: string }
  | { readonly code: 'CANCELLED' }
  | { readonly code: 'FIELD_ERRORS'; readonly errors: FieldErrors }
  | { readonly code: 'UNAVAILABLE' | 'ERROR'; readonly message: string };

export interface ConfirmationDetails {
  readonly conversationCount?: number;
  readonly pendingOfferCount?: number;
  readonly listingChanged?: boolean;
  readonly editingListing?: boolean;
  readonly offerAmountCents?: number;
}

/** Confirmation copy uses only the metadata for the current authorized resource. */
export function confirmationFor(actionId: ActionId, details: ConfirmationDetails = {}): Confirmation | null {
  switch (actionId) {
    case 'ARCHIVE_LISTING': return { title: 'Archive Listing', consequence:
      'This hides the listing from search and rejects pending offers. Archiving cannot be undone.' };
    case 'DELETE_LISTING': {
      const count = details.conversationCount;
      return { title: 'Delete Listing', consequence: 'Permanently remove this listing and its managed photos?'
        + (count === undefined ? ' Any conversations about this listing will also be deleted, for you and the buyers.'
          : count > 0 ? ` ${count} conversation${count === 1 ? '' : 's'} about this listing will also be deleted, for you and the buyers.` : '') };
    }
    case 'SAVE_LISTING': return details.editingListing && details.listingChanged
      && (details.pendingOfferCount === undefined || details.pendingOfferCount > 0)
      ? { title: 'Save Listing', consequence: 'These changes will reject all pending offers on this listing.' } : null;
    case 'ACCEPT_OFFER': return { title: 'Accept Offer', consequence:
      `Accept ${details.offerAmountCents === undefined ? 'this offer' : formatMoney(details.offerAmountCents)}? This reserves the listing, creates a sale and rejects all other pending offers.` };
    case 'CONFIRM_COMPLETION': return { title: 'Confirm Completion', consequence:
      'This records your completion confirmation. If the other participant has confirmed, the sale completes and the listing is marked sold.' };
    case 'CANCEL_SALE': case 'ACCEPT_CANCELLATION': return {
      title: actionId === 'CANCEL_SALE' ? 'Cancel Sale' : 'Accept Cancellation',
      consequence: 'This cancels the sale and makes the listing available again. Previous offers remain closed.',
    };
    case 'CANCEL_MEETUP': return { title: 'Cancel Meetup', consequence: 'The sale stays active and the seller can offer new times.' };
    default: return null;
  }
}

// BACKEND INTEGRATION: feature callbacks return SubmissionResult only after the real operation.
// Authorization, payload validation and atomic domain transitions belong on the backend;
// this module deliberately does not implement a generic dispatcher or trust UI availability.
