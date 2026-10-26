/**
 * Small fetch wrapper shared by the SEC-05/08/10/11 screens.
 *
 * The access token comes from the login module (SEC-01, Identity and access member).
 * The login module supplies both the access token and a fresh DPoP proof.
 * Anonymous requests deliberately omit tokens, proofs and cookies.
 */
export type AccessTokenProvider = () => Promise<string | undefined> | string | undefined;

export type DPoPProofProvider = (url: string, method: string) => Promise<string | undefined>;
let proofProvider: DPoPProofProvider | undefined;

export function setDPoPProofProvider(provider: DPoPProofProvider | undefined): void {
  proofProvider = provider;
}

let accessTokenProvider: AccessTokenProvider = () => undefined;

export function setAccessTokenProvider(provider: AccessTokenProvider): void {
  accessTokenProvider = provider;
}

/** Error with the server's fixed error code (never a stack trace, see ApiExceptionHandler). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail?: string,
  ) {
    super(code);
    this.name = 'ApiError';
  }
}

export interface ApiRequest extends Omit<RequestInit, 'body'> {
  /** Send the access token. Default true. Set false for public and anonymous endpoints. */
  auth?: boolean;
  json?: unknown;
}

export async function apiFetch<T>(path: string, request: ApiRequest = {}): Promise<T> {
  const { auth = true, json, headers: extraHeaders, ...init } = request;
  const url = new URL(path, window.location.origin);
  if (url.origin !== window.location.origin || !url.pathname.startsWith('/api/')) {
    throw new Error('Only same-origin API requests are allowed.');
  }
  const headers = new Headers(extraHeaders);
  headers.delete('Authorization');
  headers.delete('DPoP');
  let body: string | undefined;
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(json);
  }
  if (auth) {
    const token = await accessTokenProvider();
    if (token) {
      // RFC 9449: htu excludes query and fragment. The proxy preserves Host.
      const proofUrl = `${url.origin}${url.pathname}`;
      const proof = await proofProvider?.(proofUrl, (init.method ?? 'GET').toUpperCase());
      if (!proof) throw new Error('DPoP proof is required. Please sign in again.');
      headers.set('Authorization', `DPoP ${token}`);
      headers.set('DPoP', proof);
    }
  }

  const response = await fetch(path, { ...init, headers, body, credentials: 'omit' });
  const text = await response.text();
  let parsed: unknown;
  try {
    parsed = text ? JSON.parse(text) : undefined;
  } catch {
    parsed = undefined;
  }

  if (!response.ok) {
    const err = (parsed ?? {}) as { error?: string; detail?: string | null };
    throw new ApiError(response.status, err.error ?? `HTTP_${response.status}`, err.detail ?? undefined);
  }
  return parsed as T;
}
