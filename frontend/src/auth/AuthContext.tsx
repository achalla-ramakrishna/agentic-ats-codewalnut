import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { getCandidateMe, getMe, getSession, logout as apiLogout } from '../api/auth'
import { getClientMe, type ClientMe } from '../api/clients'
import { stopViewAs } from '../api/viewAs'
import type { CandidateMe, Me, ViewAsInfo } from '../api/types'

type AuthState =
  | { status: 'loading' }
  | { status: 'signed-out' }
  | { status: 'signed-in'; me: Me }
  | { status: 'candidate'; candidate: CandidateMe }
  | { status: 'client'; client: ClientMe }
  | { status: 'error'; message: string }

interface AuthContextValue {
  state: AuthState
  /** Set while an admin views the app as someone else. */
  viewAs: ViewAsInfo | null
  refresh: () => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })
  const [viewAs, setViewAs] = useState<ViewAsInfo | null>(null)

  const refresh = useCallback(async () => {
    try {
      const session = await getSession()
      setViewAs(session.viewAs ?? null)
      if (session.type === 'STAFF') {
        setState({ status: 'signed-in', me: await getMe() })
      } else if (session.type === 'CANDIDATE') {
        setState({ status: 'candidate', candidate: await getCandidateMe() })
      } else if (session.type === 'CLIENT') {
        setState({ status: 'client', client: await getClientMe() })
      } else {
        setState({ status: 'signed-out' })
      }
    } catch (error) {
      setState({ status: 'error', message: error instanceof Error ? error.message : 'Something went wrong' })
    }
  }, [])

  const signOut = useCallback(async () => {
    if (viewAs) {
      // "Sign out" while viewing as someone just goes back to the admin's own view.
      await stopViewAs()
      window.location.assign('/admin/view-as')
      return
    }
    try {
      await apiLogout()
    } finally {
      setState({ status: 'signed-out' })
    }
  }, [viewAs])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return <AuthContext.Provider value={{ state, viewAs, refresh, signOut }}>{children}</AuthContext.Provider>
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
