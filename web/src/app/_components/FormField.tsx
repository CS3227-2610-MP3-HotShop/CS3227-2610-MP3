'use client';

import { useId, useState } from 'react';
import type { ReactNode } from 'react';

interface FieldProps {
  readonly id: string;
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly error?: string;
  readonly hint?: string;
  readonly required?: boolean;
  readonly disabled?: boolean;
  readonly readOnly?: boolean;
}

type ControlProps = {
  id: string; value: string; onChange: (event: { target: { value: string } }) => void;
  'aria-invalid': boolean; 'aria-describedby': string | undefined;
  required: boolean | undefined; disabled: boolean | undefined; readOnly: boolean | undefined;
};

function Field({ children, ...props }: FieldProps & { readonly children: (control: ControlProps) => ReactNode }) {
  const prefix = useId();
  const id = `${prefix}-${props.id}`;
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const descriptions = [props.hint && hintId, props.error && errorId].filter(Boolean).join(' ');
  return <div className="form-field">
    <label htmlFor={id}>{props.label}</label>
    {children({ id, value: props.value, onChange: event => props.onChange(event.target.value),
      'aria-invalid': Boolean(props.error), 'aria-describedby': descriptions || undefined,
      required: props.required, disabled: props.disabled, readOnly: props.readOnly })}
    {props.hint && <p className="hint" id={hintId}>{props.hint}</p>}
    <p className="error field-error" id={errorId} aria-live="polite">{props.error ?? ''}</p>
  </div>;
}

export function TextField(props: FieldProps & { readonly inputMode?: 'text' | 'decimal'; readonly multiline?: boolean }) {
  return <Field {...props}>{control => props.multiline
    ? <textarea {...control} rows={4} /> : <input {...control} type="text" inputMode={props.inputMode} />}</Field>;
}

export function PasswordField(props: FieldProps) {
  const [visible, setVisible] = useState(false);
  return <Field {...props}>{control => <>
    <input {...control} type={visible ? 'text' : 'password'} autoComplete="off" spellCheck={false} />
    <label className="checkbox-label"><input type="checkbox" checked={visible} disabled={props.disabled}
      onChange={event => { setVisible(event.target.checked); }} aria-controls={control.id} />
      Show {props.label.toLowerCase()}</label>
  </>}</Field>;
}

export function SelectField(props: FieldProps & { readonly options: readonly { value: string; label: string }[] }) {
  return <Field {...props}>{control => <select {...control}>
    <option value="">Choose…</option>
    {props.options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
  </select>}</Field>;
}
