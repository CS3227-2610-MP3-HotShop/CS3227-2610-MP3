'use client';

import { useId } from 'react';
import type { ReactNode } from 'react';
import type { PageLoadState } from '@/app/_lib/loadPage';
import { PageHeading } from '@/app/_components/PageHeading';

export function PageFrame({ title, state, onRetry, actions, children }: {
  readonly title: string;
  readonly state: PageLoadState<unknown>;
  // BACKEND INTEGRATION: rerun the actual loader; do not switch a synthetic state to ready.
  readonly onRetry: () => void;
  readonly actions?: ReactNode;
  readonly children?: ReactNode;
}) {
  const headingId = useId();
  return <section className="page" aria-labelledby={headingId}>
    <PageHeading title={title} id={headingId} />
    {actions && <div className="form-actions">{actions}</div>}
    <div aria-busy={state.state === 'loading' || state.state === 'idle'}>
      {(state.state === 'loading' || state.state === 'idle') && <p role="status">Loading…</p>}
      {state.state === 'error' && <div className="feedback">
        <p role="alert" className="error">{state.message}</p>
        <button type="button" onClick={onRetry}>Retry</button>
      </div>}
      {state.state === 'empty' && <p role="status" className="muted">{state.message}</p>}
      {state.state === 'ready' && children}
    </div>
  </section>;
}
