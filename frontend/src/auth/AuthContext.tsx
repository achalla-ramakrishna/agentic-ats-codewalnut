import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { getMe, logout as apiLogout } from '../api/auth'
import { ApiError } from '../api/client'
import type { Me } from '../api/types'

type AuthState =
  | { status: 'loading' }
  | { status: 'signed-out' }
  | { status: 'signed-in'; me: Me }
  | { status: 'error'; message: string }

interface AuthContextValue {
  state: AuthState
  refresh: () => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  const refresh = useCallback(async () => {
    try {
      setState({ status: 'signed-in', me: await getMe() })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setState({ status: 'signed-out' })
      } else {
        setState({ status: 'error', message: error instanceof Error ? error.message : 'Something went wrong' })
      }
    }
  }, [])

  const signOut = useCallback(async () => {
    try {
      await apiLogout()
    } finally {
      setState({ status: 'signed-out' })
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return <AuthContext.Provider value={{ state, refresh, signOut }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>')
  return value
}

/** For screens rendered only when signed in. */
export function useMe(): Me {
  const { state } = useAuth()
  if (state.status !== 'signed-in') throw new Error('useMe used while not signed in')
  return state.me
}
