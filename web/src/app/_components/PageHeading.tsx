'use client';

import { useEffect, useRef } from 'react';
import { useRouteKey } from '@/app/_navigation/useRouteKey';
import { BackButton } from '@/app/_navigation/navigation';

export function PageHeading({ title, id }: { readonly title: string; readonly id?: string }) {
  const heading = useRef<HTMLHeadingElement>(null);
  const routeKey = useRouteKey();
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
  }, [routeKey]);
  return <header className="page-heading">
    <div><BackButton /></div>
    <h1 id={id} ref={heading} tabIndex={-1}>{title}</h1>
  </header>;
}
