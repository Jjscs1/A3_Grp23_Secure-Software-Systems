import { createRemoteJWKSet, jwtVerify } from 'jose'
import type { JWTVerifyGetKey } from 'jose'

export const OIDC_ISSUER = 'http://localhost:8081/realms/evp'
export const OIDC_CLIENT_ID = 'evp-frontend'

const keycloakSigningKeys = createRemoteJWKSet(
  new URL(`${OIDC_ISSUER}/protocol/openid-connect/certs`),
)

export async function validateIdToken(
  idToken: string,
  expectedNonce: string,
  signingKeys: JWTVerifyGetKey = keycloakSigningKeys,
) {
  if (!idToken || !expectedNonce) {
    throw new Error('ID token and expected nonce are required.')
  }

  const { payload } = await jwtVerify(idToken, signingKeys, {
    algorithms: ['RS256'],
    issuer: OIDC_ISSUER,
    audience: OIDC_CLIENT_ID,
    requiredClaims: ['iss', 'sub', 'aud', 'exp', 'iat', 'nonce'],
    clockTolerance: 0,
  })

  if (typeof payload.sub !== 'string' || payload.sub.trim() === '') {
    throw new Error('ID token subject is invalid.')
  }

  if (
    typeof payload.nonce !== 'string' ||
    payload.nonce !== expectedNonce
  ) {
    throw new Error('ID token nonce does not match.')
  }

  const now = Math.floor(Date.now() / 1000)

  if (
    typeof payload.iat !== 'number' ||
    !Number.isFinite(payload.iat) ||
    payload.iat > now
  ) {
    throw new Error('ID token issue time is invalid.')
  }

  if (
    typeof payload.exp !== 'number' ||
    !Number.isFinite(payload.exp) ||
    payload.exp <= payload.iat
  ) {
    throw new Error('ID token lifetime is invalid.')
  }

  const hasMultipleAudiences =
    Array.isArray(payload.aud) && payload.aud.length > 1

  if (
    (hasMultipleAudiences || payload.azp !== undefined) &&
    payload.azp !== OIDC_CLIENT_ID
  ) {
    throw new Error('ID token authorized party is invalid.')
  }

  return payload
}