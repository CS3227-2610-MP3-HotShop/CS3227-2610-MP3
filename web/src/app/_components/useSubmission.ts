'use client';

import { useEffect, useRef, useState } from 'react';
import { useDialog } from '@/app/_components/DialogHost';
import type { FieldErrors } from '@/app/_lib/validation';
import type { Confirmation, SubmissionResult } from '@/app/_lib/actions';

export interface SubmissionOptions {
  /** Include session identity, resource identity/version and any confirmation-relevant draft revision. */
  readonly targetKey: string;
  readonly available: boolean;
  readonly fieldErrors?: FieldErrors;
  readonly confirmation?: Confirmation | null;
  // BACKEND INTEGRATION: capture the validated payload in this callback and await the real operation.
  readonly execute: () => Promise<SubmissionResult>;
  readonly onSuccess?: () => Promise<void>;
  readonly onResult?: (result: SubmissionResult) => void;
}

/** Confirmation and duplicate protection; UI availability is not backend authorization. */
export function useSubmission(options: SubmissionOptions) {
  const { openDialog } = useDialog();
  const current = useRef(options);
  current.current = options;
  const mounted = useRef(false);
  const locked = useRef(false);
  const generation = useRef(0);
  const [busy, setBusy] = useState(false);
  const [stored, setStored] = useState<{ key: string; result: SubmissionResult } | null>(null);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; generation.current++; };
  }, []);
  useEffect(() => {
    generation.current++;
    locked.current = false;
    setBusy(false);
    setStored(null);
  }, [options.targetKey]);

  const submit = () => {
    if (locked.current || !mounted.current) return;
    locked.current = true;
    setBusy(true);
    setStored(null);
    const initial = current.current;
    const token = generation.current;
    const active = () => mounted.current && generation.current === token
      && current.current.targetKey === initial.targetKey;
    const release = () => {
      if (!active()) return;
      locked.current = false;
      setBusy(false);
    };
    const publish = (result: SubmissionResult) => {
      if (!active()) return;
      setStored({ key: initial.targetKey, result });
      current.current.onResult?.(result);
    };
    const invalid = (): SubmissionResult | null => {
      if (!current.current.available) return { code: 'UNAVAILABLE', message: 'This action is unavailable.' };
      const errors = current.current.fieldErrors ?? {};
      return Object.keys(errors).length === 0 ? null : { code: 'FIELD_ERRORS', errors: { ...errors } };
    };
    const complete = async () => {
      if (!active()) return;
      try {
        const failure = invalid();
        if (failure !== null) { publish(failure); return; }
        // BACKEND INTEGRATION: execute must return actual success/validation/conflict outcomes.
        const result = await current.current.execute();
        if (!active()) return;
        publish(result);
        if (result.code === 'SUCCESS') {
          try { await current.current.onSuccess?.(); }
          catch {
            publish({ code: 'ERROR', message: 'The change was saved, but the page could not be refreshed. Refresh before trying again.' });
          }
        }
      } catch {
        publish({ code: 'ERROR', message: 'The request could not be completed. Check the latest state before trying again.' });
      } finally { release(); }
    };
    const failure = invalid();
    if (failure !== null) { publish(failure); release(); return; }
    const confirmation = initial.confirmation;
    if (confirmation == null) {
      void complete();
    } else if (!openDialog({
      title: `${confirmation.title}?`, description: confirmation.consequence, confirmLabel: confirmation.title,
      onConfirm: () => { void complete(); }, onCancel: release,
    })) release();
  };
  return { busy, result: stored?.key === options.targetKey ? stored.result : null, submit };
}
