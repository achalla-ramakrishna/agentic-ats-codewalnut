import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { getCandidateMe, getMe, getSession, logout as apiLogout } from '../api/auth'
import type { CandidateMe, Me } from '../api/types'

type AuthState =
  | { status: 'loading' }
  | { status: 'signed-out' }
  | { status: 'signed-in'; me: Me }
  | { status: 'candidate'; candidate: CandidateMe }
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
      const session = await getSession()
      if (session.type === 'STAFF') {
        setState({ status: 'signed-in', me: await getMe() })
      } else if (session.type === 'CANDIDATE') {
        setState({ status: 'candidate', candidate: await getCandidateMe() })
      } else {
        setState({ status: 'signed-out' })
      }
    } catch (error) {
      setState({ status: 'error', message: error instanceof Error ? error.message : 'Something went wrong' })
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

/** For staff screens rendered only when a staff member is signed in. */
export function useMe(): Me {
  const { state } = useAuth()
  if (state.status !== 'signed-in') throw new Error('useMe used while no staff member is signed in')
  return state.me
}
