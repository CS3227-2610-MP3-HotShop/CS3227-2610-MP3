'use client';

import { Suspense } from 'react';
import type { ReactNode } from 'react';
import { ApplicationProvider } from '@/app/_providers/ApplicationProvider';
import type { ApplicationBindings } from '@/app/_providers/ApplicationProvider';
import { NavigationBoundary } from '@/app/_navigation/navigation';

/** Compose inside a client adapter; callback-bearing bindings cannot be passed from a server layout. */
export function ApplicationBoundary({ value, children }: {
  readonly value: ApplicationBindings;
  readonly children: ReactNode;
}) {
  return <ApplicationProvider value={value}>
    <Suspense fallback={null}><NavigationBoundary>{children}</NavigationBoundary></Suspense>
  </ApplicationProvider>;
}
