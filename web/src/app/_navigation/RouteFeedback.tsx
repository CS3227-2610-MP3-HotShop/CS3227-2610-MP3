'use client';

import Link from 'next/link';
import { PageHeading } from '@/app/_components/PageHeading';

export function NotFoundPage() {
  return <section className="page standalone-page">
    <PageHeading title="Not found" />
    <p role="alert">This page was not found.</p>
    <Link href="/search">Search</Link>
  </section>;
}

/** Safe router/renderer failure presentation; never expose the caught exception. */
export function RouteFailurePage() {
  return <main className="page standalone-page">
    <h1>Page unavailable</h1>
    <p role="alert">This page could not be displayed.</p>
    <Link href="/search">Search</Link>
  </main>;
}
