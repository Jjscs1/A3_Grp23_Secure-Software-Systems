/**
 * SEC-19 client side: all API traffic must be https (TLS 1.3 + HSTS are enforced by the server).
 * apiUrl() refuses any non-https absolute URL outside local development, so a mistyped or injected
 * http:// endpoint can never carry personal details or ballots in clear.
 */
export function isLocalDev(hostname: string): boolean {
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]';
}

export function apiUrl(path: string, base: string = window.location.origin): string {
  const u = new URL(path, base);
  const b = new URL(base);
  if (u.protocol !== 'https:' && !(u.protocol === 'http:' && isLocalDev(u.hostname))) {
    throw new Error('Refusing to send data over an insecure connection');
  }
  if (u.origin !== b.origin) {
    throw new Error('Refusing to call a different origin');
  }
  return u.toString();
}

export async function secureFetch(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(apiUrl(path), { ...init, credentials: 'same-origin', referrerPolicy: 'no-referrer' });
}
