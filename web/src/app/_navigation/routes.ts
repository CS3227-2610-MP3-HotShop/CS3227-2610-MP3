import type { Screen } from '@/app/_navigation/routeAccess';

/** Reserved filesystem routes, not executable route registration or completed pages. */
export const ROUTES = {
  login: '/login',
  register: '/register',
  search: '/search',
  listings: '/listings',
  'new-listing': '/listings/new',
  listing: '/listings/[listingId]',
  'edit-listing': '/listings/[listingId]/edit',
  enquiry: '/listings/[listingId]/chat',
  offers: '/offers',
  purchases: '/purchases',
  sales: '/sales',
  sale: '/sales/[saleId]',
  dashboard: '/dashboard',
  conversations: '/conversations',
  conversation: '/conversations/[conversationId]',
  profile: '/profile',
  'public-profile': '/profiles/[userId]',
} as const satisfies Readonly<Record<Screen, string>>;
