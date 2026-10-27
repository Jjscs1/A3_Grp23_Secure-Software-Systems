// SEC-06: client-side input rules. They MIRROR backend/.../validation/InputRules.java for fast feedback only.
// The server is the authority: it re-validates everything, so never rely on this file for security.

export type TextKind = 'NAME' | 'ADDRESS' | 'DIVISION' | 'ORGANISATION' | 'REFERENCE';

const PATTERNS: Record<TextKind, RegExp> = {
  NAME: /^[\p{L}\p{M}][\p{L}\p{M}\p{Zs}'\u2019.-]*$/u,
  DIVISION: /^[\p{L}\p{M}][\p{L}\p{M}\p{Zs}'\u2019.-]*$/u,
  ADDRESS: /^[\p{L}\p{M}\p{N}\p{Zs},.'\u2019/#-]+$/u,
  ORGANISATION: /^[\p{L}\p{M}\p{N}\p{Zs}'\u2019.&-]+$/u,
  REFERENCE: /^[A-Za-z0-9][A-Za-z0-9 ._/-]*$/,
};

// C0 controls (incl. NUL, CR, LF), DEL and C1 controls
// eslint-disable-next-line no-control-regex
const CONTROL_CHARS = /[\u0000-\u001f\u007f-\u009f]/;
const SQL_COMMENT = /--|\/\*|\*\//;

export const hasControlChars = (s: string): boolean => CONTROL_CHARS.test(s);
export const hasSqlCommentToken = (s: string): boolean => SQL_COMMENT.test(s);

export function checkText(kind: TextKind, raw: string, min = 1, max = 100): boolean {
  const s = raw.trim();
  if (s.length < min || s.length > max) return false;
  if (hasControlChars(raw) || hasSqlCommentToken(s)) return false;
  return PATTERNS[kind].test(s);
}

export const isPostcode = (s: string): boolean => /^\d{4}$/.test(s);

/** Real calendar date YYYY-MM-DD from 1900-01-01 up to `today` (UTC). */
export function isIsoPastDate(s: string, today: Date = new Date()): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (!m) return false;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const dt = new Date(Date.UTC(y, mo - 1, d));
  const real = dt.getUTCFullYear() === y && dt.getUTCMonth() === mo - 1 && dt.getUTCDate() === d;
  return real && y >= 1900 && dt.getTime() <= today.getTime();
}

export interface FieldRule {
  kind: TextKind;
  min?: number;
  max?: number;
}

/** Returns a generic message (never echoing the input) or null when the value is acceptable. */
export function validateField(rule: FieldRule, value: string, required = true): string | null {
  if (value.trim() === '') return required ? 'This field is required.' : null;
  const { kind, min = 1, max = 100 } = rule;
  if (value.trim().length > max) return `Use at most ${max} characters.`;
  if (value.trim().length < min) return `Use at least ${min} characters.`;
  return checkText(kind, value, min, max) ? null : 'Contains characters that are not allowed.';
}
