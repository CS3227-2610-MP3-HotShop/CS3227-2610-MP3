'use client';

import { usePathname, useSearchParams } from 'next/navigation';

/** Path and submitted query identify a page without reading Next.js history internals. */
export function useRouteKey(): string {
  const pathname = usePathname();
  const search = useSearchParams().toString();
  return `${pathname}${search ? `?${search}` : ''}`;
}
