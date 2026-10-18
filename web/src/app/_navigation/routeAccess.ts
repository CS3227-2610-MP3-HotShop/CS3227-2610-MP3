export type Screen = 'login' | 'register' | 'search' | 'listings' | 'new-listing'
  | 'listing' | 'edit-listing' | 'offers' | 'purchases' | 'sales' | 'sale'
  | 'dashboard' | 'conversations' | 'conversation' | 'enquiry' | 'profile' | 'public-profile';

export type RouteAccess =
  | { readonly ok: true; readonly title: string; readonly resourceName: string | null }
  | { readonly ok: false; readonly title: 'Not found' | 'Unavailable'; readonly message: string };

export const TITLES: Readonly<Record<Screen, string>> = {
  login: 'Log in', register: 'Register', search: 'Search', listings: 'My Listings',
  'new-listing': 'Create Listing', listing: 'Listing Details', 'edit-listing': 'Edit Listing',
  offers: 'My Offers', purchases: 'My Purchases', sales: 'My Sales', sale: 'Sale Details',
  dashboard: 'Seller Dashboard', conversations: 'Conversations', conversation: 'Conversation',
  enquiry: 'Conversation', profile: 'My Profile', 'public-profile': 'Public Profile',
};

export type ResourceAccess =
  | { readonly status: 'ready'; readonly resourceName: string | null }
  | { readonly status: 'not-found' }
  | { readonly status: 'unavailable'; readonly message: string };

// BACKEND INTEGRATION: map the authorized read's actual not-found/forbidden response to
// ResourceAccess. The server must enforce ownership/participation/state; URL IDs confer no access.
export function inspectRoute(screen: Screen, resource: ResourceAccess): RouteAccess {
  if (resource.status === 'not-found') {
    return { ok: false, title: 'Not found', message: 'This resource was not found.' };
  }
  if (resource.status === 'unavailable') {
    return { ok: false, title: 'Unavailable', message: resource.message };
  }
  return { ok: true, title: TITLES[screen], resourceName: resource.resourceName };
}
