import { UserManager } from 'oidc-client-ts'
import type { SigninRedirectArgs, User } from 'oidc-client-ts'
import { validateIdToken } from './idTokenValidation'

export class VerifiedUserManager extends UserManager {
  override async signinRedirect(
    args: SigninRedirectArgs = {},
  ): Promise<void> {
    const nonce = args.nonce ?? crypto.randomUUID()

    await super.signinRedirect({
      ...args,
      nonce,
      state: {
        expectedNonce: nonce,
      },
    })
  }

  override async storeUser(user: User | null): Promise<void> {
    if (user === null) {
      await super.storeUser(null)
      return
    }

    try {
      const savedState: unknown = user.state

      if (
        typeof savedState !== 'object' ||
        savedState === null ||
        !('expectedNonce' in savedState) ||
        typeof savedState.expectedNonce !== 'string'
      ) {
        throw new Error('Missing sign-in verification state.')
      }

      const claims = await validateIdToken(
        user.id_token ?? '',
        savedState.expectedNonce,
      )

      if (claims.sub !== user.profile.sub) {
        throw new Error('ID token subject does not match the user.')
      }

      await super.storeUser(user)
    } catch {
      await this.removeUser()
      throw new Error('ID token verification failed. Please sign in again.')
    }
  }
}