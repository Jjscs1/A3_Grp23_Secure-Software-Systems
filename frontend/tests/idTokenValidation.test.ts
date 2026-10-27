import { beforeAll, describe, expect, it } from 'vitest'
import {
  createLocalJWKSet,
  exportJWK,
  generateKeyPair,
  SignJWT,
} from 'jose'
import type { JWTPayload } from 'jose'
import {
  OIDC_CLIENT_ID,
  OIDC_ISSUER,
  validateIdToken,
} from '../src/idTokenValidation'

const expectedNonce = 'test-sign-in-nonce'

let trustedKeys: Awaited<ReturnType<typeof generateKeyPair>>
let wrongKeys: Awaited<ReturnType<typeof generateKeyPair>>
let signingKeys: ReturnType<typeof createLocalJWKSet>

beforeAll(async () => {
  trustedKeys = await generateKeyPair('RS256')
  wrongKeys = await generateKeyPair('RS256')

  const publicKey = await exportJWK(trustedKeys.publicKey)

  signingKeys = createLocalJWKSet({
    keys: [
      {
        ...publicKey,
        kid: 'test-key',
        alg: 'RS256',
        use: 'sig',
      },
    ],
  })
})

async function makeToken(
  changes: JWTPayload = {},
  useWrongKey = false,
) {
  const now = Math.floor(Date.now() / 1000)

  const payload: JWTPayload = {
    iss: OIDC_ISSUER,
    aud: OIDC_CLIENT_ID,
    sub: 'test-user-id',
    iat: now - 600,
    exp: now + 300,
    nonce: expectedNonce,
    ...changes,
  }

  return new SignJWT(payload)
    .setProtectedHeader({
      alg: 'RS256',
      kid: 'test-key',
      typ: 'JWT',
    })
    .sign(
      useWrongKey
        ? wrongKeys.privateKey
        : trustedKeys.privateKey,
    )
}

describe('ID token validation', () => {
  it('accepts a correctly signed token with valid claims', async () => {
    const token = await makeToken()

    const claims = await validateIdToken(
      token,
      expectedNonce,
      signingKeys,
    )

    expect(claims.sub).toBe('test-user-id')
    expect(claims.nonce).toBe(expectedNonce)
  })

  it('rejects a token signed with the wrong key', async () => {
    const token = await makeToken({}, true)

    await expect(
      validateIdToken(token, expectedNonce, signingKeys),
    ).rejects.toThrow()
  })

  it('rejects the wrong issuer', async () => {
    const token = await makeToken({
      iss: 'https://wrong-issuer.example',
    })

    await expect(
      validateIdToken(token, expectedNonce, signingKeys),
    ).rejects.toThrow()
  })

  it('rejects the wrong audience', async () => {
    const token = await makeToken({
      aud: 'another-client',
    })

    await expect(
      validateIdToken(token, expectedNonce, signingKeys),
    ).rejects.toThrow()
  })

  it('rejects a nonce from another sign-in attempt', async () => {
    const token = await makeToken({
      nonce: 'another-sign-in-nonce',
    })

    await expect(
      validateIdToken(token, expectedNonce, signingKeys),
    ).rejects.toThrow('ID token nonce does not match.')
  })

  it('rejects an expired token', async () => {
    const token = await makeToken({
      exp: Math.floor(Date.now() / 1000) - 300,
    })

    await expect(
      validateIdToken(token, expectedNonce, signingKeys),
    ).rejects.toThrow()
  })

  it('rejects a token without an expiration claim', async () => {
    const token = await makeToken({
      exp: undefined,
    })

    await expect(
      validateIdToken(token, expectedNonce, signingKeys),
    ).rejects.toThrow()
  })
})