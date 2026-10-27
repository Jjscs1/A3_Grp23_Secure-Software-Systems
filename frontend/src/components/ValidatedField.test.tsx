import { useState } from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { ValidatedField } from './ValidatedField';

afterEach(cleanup);

function Harness({ initial = '' }: { initial?: string }) {
  const [v, setV] = useState(initial);
  return <ValidatedField label="Full name" kind="NAME" value={v} onChange={setV} max={20} />;
}

describe('ValidatedField', () => {
  it('limits input length in the browser', () => {
    render(<Harness />);
    expect((screen.getByLabelText('Full name') as HTMLInputElement).maxLength).toBe(20);
  });

  it('shows no error before the user leaves the field', () => {
    render(<Harness initial="<script>" />);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('flags a script payload with a generic message and renders it as text only', () => {
    const { container } = render(<Harness />);
    const input = screen.getByLabelText('Full name');
    fireEvent.change(input, { target: { value: '<svg/onload=1>' } });
    fireEvent.blur(input);
    const alert = screen.getByRole('alert');
    expect(alert.textContent).toBe('Contains characters that are not allowed.');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(container.querySelector('svg')).toBeNull(); // nothing was injected into the DOM
  });

  it('accepts a real name', () => {
    render(<Harness />);
    const input = screen.getByLabelText('Full name');
    fireEvent.change(input, { target: { value: "Sean O'Brien" } });
    fireEvent.blur(input);
    expect(screen.queryByRole('alert')).toBeNull();
    expect(input.getAttribute('aria-invalid')).toBe('false');
  });

  it('requires a value', () => {
    render(<Harness />);
    const input = screen.getByLabelText('Full name');
    fireEvent.blur(input);
    expect(screen.getByRole('alert').textContent).toBe('This field is required.');
  });
});
