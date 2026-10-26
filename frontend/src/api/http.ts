/**
 * Small fetch wrapper shared by the SEC-05/08/10/11 screens.
 *
 * The access token comes from the login module (SEC-01, Identity and access member).
 * Once their OIDC client exists, they call setAccessTokenProvider(() => user?.access_token).
 */
export type AccessTokenProvider = () => Promise<string | undefined> | string | undefined;

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
  const headers = new Headers(extraHeaders);
  let body: string | undefined;
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(json);
  }
  if (auth) {
    const token = await accessTokenProvider();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
  }

  const response = await fetch(path, { ...init, headers, body });
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
