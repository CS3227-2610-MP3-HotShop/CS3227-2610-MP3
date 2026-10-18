'use client';

import { createContext, useCallback, useContext, useLayoutEffect, useRef, useState, useId } from 'react';
import type { ReactNode } from 'react';
import { useRouteKey } from '@/app/_navigation/useRouteKey';

export interface DialogRequest {
  readonly title: string;
  readonly description: string;
  readonly content?: ReactNode;
  readonly confirmLabel?: string;
  readonly onConfirm?: () => void;
  readonly onCancel?: () => void;
}

interface DialogApi {
  readonly openDialog: (request: DialogRequest) => boolean;
  readonly closeDialog: () => void;
}

const DialogContext = createContext<DialogApi | null>(null);

export function useDialog(): DialogApi {
  const api = useContext(DialogContext);
  if (api === null) throw new Error('Dialog host is unavailable.');
  return api;
}

function Modal({ request, trigger, finish }: { readonly request: DialogRequest;
  readonly trigger: HTMLElement | null; readonly finish: (confirm: boolean) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  useLayoutEffect(() => {
    const node = dialog.current;
    node?.showModal();
    const field = node?.querySelector<HTMLElement>('input:not([disabled]), textarea:not([disabled]), select:not([disabled])');
    (field ?? cancel.current)?.focus();
    return () => {
      node?.close();
      // Wait for a replacement route's heading to exist when the original trigger was removed.
      queueMicrotask(() => {
        if (document.querySelector('dialog[open]')) return;
        const target = trigger?.isConnected && !trigger.matches(':disabled') ? trigger
          : document.querySelector<HTMLElement>('main h1');
        target?.focus({ preventScroll: true });
      });
    };
  }, []);
  return <dialog ref={dialog} className="app-dialog" aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); finish(false); }}>
    <h2 id={titleId}>{request.title}</h2>
    <p id={descriptionId}>{request.description}</p>
    {request.content && <div className="dialog-body">{request.content}</div>}
    <div className="form-actions dialog-actions">
      <button ref={cancel} type="button" onClick={() => { finish(false); }}>Cancel</button>
      {request.confirmLabel && <button type="button" className="primary" onClick={() => { finish(true); }}>
        {request.confirmLabel}
      </button>}
    </div>
  </dialog>;
}

/** One native modal for the current document; requests and drafts never enter history. */
export function DialogHost({ children }: { readonly children: ReactNode }) {
  const [request, setRequest] = useState<DialogRequest | null>(null);
  const active = useRef<DialogRequest | null>(null);
  const trigger = useRef<HTMLElement | null>(null);
  const routeKey = useRouteKey();
  const finish = useCallback((confirm: boolean) => {
    const current = active.current;
    if (current === null) return;
    active.current = null;
    setRequest(null);
    // Close the native modal before any submission response is produced.
    document.querySelector<HTMLDialogElement>('dialog.app-dialog[open]')?.close();
    if (confirm) current.onConfirm?.();
    else current.onCancel?.();
  }, []);
  const openDialog = useCallback((next: DialogRequest) => {
    if (active.current !== null) return false;
    trigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    active.current = next;
    setRequest(next);
    return true;
  }, []);
  useLayoutEffect(() => { finish(false); }, [routeKey, finish]);
  return <DialogContext value={{ openDialog, closeDialog: () => { finish(false); } }}>
    {children}
    {request && <Modal request={request} trigger={trigger.current} finish={finish} />}
  </DialogContext>;
}
