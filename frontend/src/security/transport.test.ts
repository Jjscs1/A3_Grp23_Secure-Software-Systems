import { describe, expect, it } from 'vitest';
import { apiUrl } from './transport';

describe('apiUrl', () => {
  const base = 'https://vote.example.gov.au';
  it('allows same-origin https paths', () => {
    expect(apiUrl('/api/ballots', base)).toBe('https://vote.example.gov.au/api/ballots');
  });
  it('refuses plain http outside local development', () => {
    expect(() => apiUrl('http://vote.example.gov.au/api', base)).toThrow();
  });
  it('allows http only for localhost', () => {
    expect(apiUrl('/api/x', 'http://localhost:5173')).toBe('http://localhost:5173/api/x');
    expect(() => apiUrl('http://evil.com/x', 'http://localhost:5173')).toThrow();
  });
  it('refuses other origins and odd schemes', () => {
    expect(() => apiUrl('https://evil.com/x', base)).toThrow();
    expect(() => apiUrl('//evil.com/x', base)).toThrow();
    // eslint-disable-next-line no-script-url
    expect(() => apiUrl('javascript:alert(1)', base)).toThrow();
    expect(() => apiUrl('ftp://vote.example.gov.au/x', base)).toThrow();
  });
});
