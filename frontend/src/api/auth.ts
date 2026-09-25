import { api } from './client'
import type { AuthConfig, Me } from './types'

export const getAuthConfig = () => api<AuthConfig>('/auth/config')
export const getMe = () => api<Me>('/me')
export const devLogin = (email: string) =>
  api<void>('/auth/dev-login', { method: 'POST', body: JSON.stringify({ email }) })
export const logout = () => api<void>('/auth/logout', { method: 'POST' })

/** Full-page navigation: Google sign-in is a server-side redirect flow. */
export const GOOGLE_SIGN_IN_URL = '/oauth2/authorization/google'
