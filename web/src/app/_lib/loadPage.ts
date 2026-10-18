'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

export type PageLoadState<T> =
  | { readonly state: 'idle' | 'loading' }
  | { readonly state: 'ready'; readonly data: T }
  | { readonly state: 'empty'; readonly message: string }
  | { readonly state: 'error'; readonly message: string };

export type PageResult<T> = Extract<PageLoadState<T>, { state: 'ready' | 'empty' }>;
export type PageLoader<T> = (signal: AbortSignal) => Promise<PageResult<T>>;

/** Callers keep load stable with useCallback; changing key discards previous data immediately. */
export function usePageLoad<T>(key: string, load: PageLoader<T>) {
  const [value, setValue] = useState<{ key: string; state: PageLoadState<T> }>({
    key, state: { state: 'loading' },
  });
  const [attempt, setAttempt] = useState(0);
  const generation = useRef(0);
  useEffect(() => {
    const current = ++generation.current;
    const controller = new AbortController();
    setValue({ key, state: { state: 'loading' } });
    // BACKEND INTEGRATION: load must perform an authorized read and map its actual response.
    void Promise.resolve().then(() => load(controller.signal)).then(result => {
      if (!controller.signal.aborted && generation.current === current) {
        setValue({ key, state: result });
      }
    }).catch(() => {
      if (!controller.signal.aborted && generation.current === current) {
        setValue({ key, state: { state: 'error', message: 'Unable to load this page. Please try again.' } });
      }
    });
    return () => { controller.abort(); generation.current++; };
  }, [key, load, attempt]);
  const retry = useCallback(() => { setAttempt(previous => previous + 1); }, []);
  return { state: value.key === key ? value.state : { state: 'loading' } as PageLoadState<T>, retry };
}
