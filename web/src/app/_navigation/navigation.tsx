'use client';

import { createContext, useCallback, useContext, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { DialogHost } from '@/app/_components/DialogHost';
import { useRouteKey } from '@/app/_navigation/useRouteKey';

interface AppHistory {
  readonly keys: readonly string[];
  readonly position: number;
}

const ENTRY_KEY = 'hotshopNavigationEntry';
const BackContext = createContext(false);
const NavigationActionContext = createContext<(action: 'push' | 'replace') => void>(() => {});

/** Track only history entries observed while this boundary is mounted. */
export function NavigationBoundary({ children }: { readonly children: ReactNode }) {
  const routeKey = useRouteKey();
  const lastRoute = useRef<string | null>(null);
  const pendingAction = useRef<'push' | 'replace'>('push');
  const current = useRef<AppHistory>({ keys: [], position: -1 });
  const [history, setHistory] = useState(current.current);
  const markAction = useCallback((action: 'push' | 'replace') => { pendingAction.current = action; }, []);

  useLayoutEffect(() => {
    const onPop = () => {
      const marker: unknown = window.history.state?.[ENTRY_KEY];
      const known = typeof marker === 'string' ? current.current.keys.indexOf(marker) : -1;
      let next: AppHistory;
      if (known >= 0) {
        next = { keys: current.current.keys, position: known };
      } else {
        const key = crypto.randomUUID();
        window.history.replaceState({ ...window.history.state, [ENTRY_KEY]: key }, '');
        next = { keys: [key], position: 0 };
      }
      current.current = next;
      const search = new URLSearchParams(window.location.search).toString();
      lastRoute.current = `${window.location.pathname}${search ? `?${search}` : ''}`;
      pendingAction.current = 'push';
      setHistory(next);
    };
    window.addEventListener('popstate', onPop);
    return () => { window.removeEventListener('popstate', onPop); };
  }, []);

  useLayoutEffect(() => {
    if (lastRoute.current === routeKey) return;
    const key = crypto.randomUUID();
    const previous = current.current;
    const next: AppHistory = previous.position < 0 ? { keys: [key], position: 0 }
      : pendingAction.current === 'replace'
        ? { keys: previous.keys.map((entry, index) => index === previous.position ? key : entry),
          position: previous.position }
        : { keys: [...previous.keys.slice(0, previous.position + 1), key], position: previous.position + 1 };
    // Preserve Next.js's own history state. Store no form fields, session data or resource data.
    window.history.replaceState({ ...window.history.state, [ENTRY_KEY]: key }, '');
    lastRoute.current = routeKey;
    pendingAction.current = 'push';
    current.current = next;
    setHistory(next);
  }, [routeKey]);

  return <NavigationActionContext value={markAction}>
    <BackContext value={history.position > 0}><DialogHost>{children}</DialogHost></BackContext>
  </NavigationActionContext>;
}

/** Use for imperative navigation so replacements do not create an application Back entry. */
export function useApplicationRouter() {
  const router = useRouter();
  const mark = useContext(NavigationActionContext);
  return useMemo(() => ({
    push: (href: string) => { mark('push'); router.push(href); },
    replace: (href: string) => { mark('replace'); router.replace(href); },
  }), [mark, router]);
}

export function BackButton() {
  const canGoBack = useContext(BackContext);
  const router = useRouter();
  return canGoBack ? <button type="button" onClick={() => { router.back(); }}>Back</button> : null;
}
