// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  InMemoryWebStorage,
  User,
  UserManager,
  WebStorageStateStore,
} from 'oidc-client-ts'
import type { JWTPayload } from 'jose'
import { VerifiedUserManager } from '../src/VerifiedUserManager'
import { validateIdToken } from '../src/idTokenValidation'

vi.mock('../src/idTokenValidation', () => ({
  validateIdToken: vi.fn(),
}))

const verify = vi.mocked(validateIdToken)

let manager: VerifiedUserManager

function makeUser(
  userState: unknown = { expectedNonce: 'saved-nonce' },
) {
  const now = Math.floor(Date.now() / 1000)

  return new User({
    id_token: 'test-id-token',
    access_token: 'test-access-token',
    token_type: 'Bearer',
    scope: 'openid profile email',
    expires_at: now + 300,
    profile: {
      iss: 'http://localhost:8081/realms/evp',
      aud: 'evp-frontend',
      sub: 'test-user-id',
      iat: now - 10,
      exp: now + 300,
    },
    userState,
  })
}

beforeEach(() => {
  verify.mockReset()

  manager = new VerifiedUserManager({
    authority: 'http://localhost:8081/realms/evp',
    client_id: 'evp-frontend',
    redirect_uri: 'http://localhost:5173/auth/callback',
    automaticSilentRenew: false,
    monitorSession: false,

    userStore: new WebStorageStateStore({
      store: new InMemoryWebStorage(),
    }),

    stateStore: new WebStorageStateStore({
      store: new InMemoryWebStorage(),
    }),
  })
})

afterEach(async () => {
  await manager.removeUser()
})

describe('VerifiedUserManager storage protection', () => {
  it('stores the user after successful verification', async () => {
    verify.mockResolvedValue({ sub: 'test-user-id' })

    await manager.storeUser(makeUser())

    expect(verify).toHaveBeenCalledWith(
      'test-id-token',
      'saved-nonce',
    )

    const storedUser = await manager.getUser()
    expect(storedUser?.profile.sub).toBe('test-user-id')
  })

  it('does not store the user while verification is pending', async () => {
    let finishVerification!: (claims: JWTPayload) => void

    verify.mockImplementation(
      () =>
        new Promise<JWTPayload>((resolve) => {
          finishVerification = resolve
        }),
    )

    const saving = manager.storeUser(makeUser())

    expect(verify).toHaveBeenCalledOnce()
    expect(await manager.getUser()).toBeNull()

    finishVerification({ sub: 'test-user-id' })
    await saving

    expect((await manager.getUser())?.profile.sub).toBe(
      'test-user-id',
    )
  })

  it('clears an existing user when verification fails', async () => {
    verify.mockResolvedValueOnce({ sub: 'test-user-id' })

    await manager.storeUser(makeUser())
    expect(await manager.getUser()).not.toBeNull()

    verify.mockRejectedValueOnce(new Error('Invalid signature'))

    await expect(
      manager.storeUser(makeUser()),
    ).rejects.toThrow('ID token verification failed.')

    expect(await manager.getUser()).toBeNull()
  })

  it('rejects a user without the saved nonce', async () => {
    await expect(
      manager.storeUser(makeUser({})),
    ).rejects.toThrow('ID token verification failed.')

    expect(verify).not.toHaveBeenCalled()
    expect(await manager.getUser()).toBeNull()
  })

  it('rejects a verified token belonging to another subject', async () => {
    verify.mockResolvedValue({ sub: 'another-user-id' })

    await expect(
      manager.storeUser(makeUser()),
    ).rejects.toThrow('ID token verification failed.')

    expect(await manager.getUser()).toBeNull()
  })
})
it('sends the server casting nonce and saves it for callback verification', async () => {
  const redirect = vi.spyOn(UserManager.prototype, 'signinRedirect').mockResolvedValue(undefined)
  try {
    await manager.signinRedirect({ nonce: 'server-challenge', prompt: 'login', max_age: 0 })
    expect(redirect).toHaveBeenCalledWith(expect.objectContaining({
      nonce: 'server-challenge', state: { expectedNonce: 'server-challenge' },
      prompt: 'login', max_age: 0,
    }))
  } finally { redirect.mockRestore() }
})
