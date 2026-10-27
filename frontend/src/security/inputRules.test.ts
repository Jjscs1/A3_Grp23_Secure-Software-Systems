import { describe, expect, it } from 'vitest';
import { checkText, hasControlChars, hasSqlCommentToken, isIsoPastDate, isPostcode, validateField } from './inputRules';

describe('inputRules (mirrors the Java InputRules)', () => {
  it.each(['Jane Citizen', "Sean O'Brien", 'José Núñez-Li', 'Mary-Anne Smith Jr.', '张伟'])('accepts real name %s', (n) => {
    expect(checkText('NAME', n)).toBe(true);
  });

  it.each([
    '<script>alert(1)</script>',
    '<img src=x onerror=alert(1)>',
    // eslint-disable-next-line no-script-url
    'javascript:alert(1)',
    "'; DROP TABLE voter;--",
    "admin'--",
    "' OR '1'='1",
    'Jane\u0000Citizen',
    'Jane\r\nCitizen',
    'a/*b*/c',
    '${jndi:ldap://x}',
    '{{7*7}}',
  ])('rejects injection payload %s in names', (p) => {
    expect(checkText('NAME', p)).toBe(false);
  });

  it('addresses', () => {
    expect(checkText('ADDRESS', '42 Wallaby Way, Melbourne', 5, 200)).toBe(true);
    expect(checkText('ADDRESS', 'Unit 3/12 3-5 High St #2', 5, 200)).toBe(true);
    expect(checkText('ADDRESS', '1 Main St; DROP TABLE x', 5, 200)).toBe(false);
    expect(checkText('ADDRESS', '<b>x</b> street', 5, 200)).toBe(false);
  });

  it('organisations and references', () => {
    expect(checkText('ORGANISATION', 'Green & Co. Party')).toBe(true);
    expect(checkText('ORGANISATION', '<b>Party</b>')).toBe(false);
    expect(checkText('REFERENCE', 'HCA 12/2026', 3, 40)).toBe(true);
    expect(checkText('REFERENCE', "HCA';--", 3, 40)).toBe(false);
  });

  it('length bounds apply after trimming', () => {
    expect(checkText('NAME', 'A', 2, 100)).toBe(false);
    expect(checkText('NAME', '  Al  ', 2, 100)).toBe(true);
    expect(checkText('NAME', 'A'.repeat(101))).toBe(false);
  });

  it('control chars and SQL comment tokens', () => {
    expect(hasControlChars('a\u0000b')).toBe(true);
    expect(hasControlChars('a\u007fb')).toBe(true);
    expect(hasControlChars('plain')).toBe(false);
    expect(hasSqlCommentToken('x--y')).toBe(true);
    expect(hasSqlCommentToken('Smith-Jones')).toBe(false);
  });

  it('dates must be real and not in the future', () => {
    const today = new Date(Date.UTC(2026, 9, 27));
    expect(isIsoPastDate('1990-01-01', today)).toBe(true);
    expect(isIsoPastDate('2026-10-27', today)).toBe(true);
    expect(isIsoPastDate('2026-10-28', today)).toBe(false);
    expect(isIsoPastDate('2020-02-31', today)).toBe(false);
    expect(isIsoPastDate('1850-01-01', today)).toBe(false);
    expect(isIsoPastDate('01/01/1990', today)).toBe(false);
    expect(isIsoPastDate("1990-01-01' OR 1=1", today)).toBe(false);
  });

  it('postcodes', () => {
    expect(isPostcode('3000')).toBe(true);
    ['300', '30000', '3O00', '3000 '].forEach((p) => expect(isPostcode(p)).toBe(false));
  });

  it('validateField returns generic messages that never echo the input', () => {
    const msg = validateField({ kind: 'NAME' }, '<script>alert(1)</script>');
    expect(msg).toBe('Contains characters that are not allowed.');
    expect(msg).not.toContain('script');
    expect(validateField({ kind: 'NAME' }, '')).toBe('This field is required.');
    expect(validateField({ kind: 'NAME' }, '', false)).toBeNull();
    expect(validateField({ kind: 'NAME' }, 'Jane')).toBeNull();
    expect(validateField({ kind: 'NAME', max: 5 }, 'Jonathan')).toBe('Use at most 5 characters.');
  });
});
