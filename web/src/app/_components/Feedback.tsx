'use client';

export interface FeedbackValue {
  readonly kind: 'info' | 'error' | 'success';
  readonly message: string;
}

export function Feedback({ value }: { readonly value: FeedbackValue | null }) {
  return <div aria-live="polite" aria-atomic="true" className="feedback-status">
    {value && <p className={value.kind === 'info' ? 'muted' : value.kind}>
      {value.message}
    </p>}
  </div>;
}
