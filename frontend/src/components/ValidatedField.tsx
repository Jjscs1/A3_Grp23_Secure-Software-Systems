import { useId, useState } from 'react';
import type { ChangeEvent } from 'react';
import { validateField } from '../security/inputRules';
import type { TextKind } from '../security/inputRules';

interface Props {
  label: string;
  kind: TextKind;
  value: string;
  onChange: (value: string) => void;
  min?: number;
  max?: number;
  required?: boolean;
}

/**
 * Text input with allow-list validation (SEC-06). The browser limits length and shows a generic error;
 * the server re-validates everything. React escapes all rendered text (no dangerouslySetInnerHTML).
 */
export function ValidatedField({ label, kind, value, onChange, min = 1, max = 100, required = true }: Props) {
  const id = useId();
  const [touched, setTouched] = useState(false);
  const error = touched ? validateField({ kind, min, max }, value, required) : null;

  return (
    <div>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="text"
        value={value}
        maxLength={max}
        autoComplete="off"
        aria-invalid={error ? 'true' : 'false'}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
        onBlur={() => setTouched(true)}
      />
      {error && (
        <p id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
