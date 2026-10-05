import { api } from './client'
import type { AuthConfig, CandidateMe, Me, Session } from './types'

export const getAuthConfig = () => api<AuthConfig>('/auth/config')
export const getSession = () => api<Session>('/auth/session')
export const getMe = () => api<Me>('/me')
export const getCandidateMe = () => api<CandidateMe>('/candidate/me')
export const devLogin = (email: string, accessCode?: string) =>
  api<void>('/auth/dev-login', { method: 'POST', body: JSON.stringify({ email, accessCode }) })
export const logout = () => api<void>('/auth/logout', { method: 'POST' })

/** Full-page navigation: Google sign-in is a server-side redirect flow. */
export const GOOGLE_SIGN_IN_URL = '/oauth2/authorization/google'

/** What to do about a refused Google sign-in, in words the person can act on. */
export function refusalMessage(refused: NonNullable<AuthConfig['signInRefused']>): string {
  const who = refused.email ?? 'This Google account'
  switch (refused.reason) {
    case 'NOT_PROVISIONED':
      return `${who} isn't a CodeWalnut ATS user yet. Ask an Admin to add exactly this email under Users. Check the spelling, and that it isn't a different address or an alias of the one they added.`
    case 'DEACTIVATED':
      return `${who} has been deactivated. Ask an Admin to reactivate it under Users.`
    case 'ACCOUNT_MISMATCH':
      return `${who} is linked to a different Google account than the one you just used. Sign in with the original Google account, or ask an Admin for help.`
    case 'UNVERIFIED':
      return 'Your Google account email isn’t verified. Verify it with Google, then try again.'
    default:
      return `Sign-in was refused for ${who}. Ask an Admin to check Users and the audit log.`
  }
}
