import { useEffect, type ReactElement } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { AuditLogPage } from './pages/AuditLogPage'
import { CandidatesPage } from './pages/CandidatesPage'
import { ClientsPage } from './pages/ClientsPage'
import { JobDetailPage } from './pages/JobDetailPage'
import { JobsPage } from './pages/JobsPage'
import { CandidateHomePage } from './pages/CandidateHomePage'
import { ComingSoonPage } from './pages/ComingSoonPage'
import { InterviewsPage } from './pages/InterviewsPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { NoAccessPage } from './pages/NoAccessPage'
import { PublicJobPage, RETURN_TO_KEY } from './pages/PublicJobPage'
import { UsersPage } from './pages/UsersPage'

/** Screen per navigation key. The server decides which keys a user gets. */
const SCREENS: Record<string, ReactElement> = {
  dashboard: <DashboardPage />,
  jobs: <JobsPage />,
  candidates: <CandidatesPage />,
  clients: <ClientsPage />,
  interviews: <InterviewsPage />,
  approvals: <ComingSoonPage title="Approvals" chunk="chunks 1 and 7" spec="requisitions.md" />,
  reports: <ComingSoonPage title="Reports" chunk="v1" spec="reports.md" />,
  users: <UsersPage />,
  'audit-log': <AuditLogPage />,
}

/** After signing in from a job link, go back to that job page. */
function useReturnAfterSignIn(ready: boolean) {
  const navigate = useNavigate()
  useEffect(() => {
    if (!ready) return
    let target: string | null = null
    try {
      target = sessionStorage.getItem(RETURN_TO_KEY)
      sessionStorage.removeItem(RETURN_TO_KEY)
    } catch {
      target = null
    }
    if (target && target.startsWith('/apply/')) navigate(target, { replace: true })
  }, [ready, navigate])
}

export function App() {
  const { state, refresh } = useAuth()
  useReturnAfterSignIn(state.status === 'candidate' || state.status === 'signed-in')

  if (state.status === 'loading') {
    return <p className="muted" style={{ padding: 24 }}>Loading…</p>
  }
  if (state.status === 'error') {
    return (
      <div style={{ padding: 24 }}>
        <p role="alert" className="alert alert-error">{state.message}</p>
        <button className="btn btn-secondary" onClick={() => void refresh()}>Try again</button>
      </div>
    )
  }
  if (state.status === 'candidate') {
    return (
      <Routes>
        <Route path="/apply/:slug" element={<PublicJobPage />} />
        <Route path="*" element={<CandidateHomePage candidate={state.candidate} />} />
      </Routes>
    )
  }
  if (state.status === 'signed-out') {
    return (
      <Routes>
        <Route path="/apply/:slug" element={<PublicJobPage />} />
        <Route path="*" element={<LoginPage />} />
      </Routes>
    )
  }

  return (
    <Routes>
      <Route path="/apply/:slug" element={<PublicJobPage />} />
      <Route path="*" element={<StaffApp />} />
    </Routes>
  )
}

function StaffApp() {
  const { state } = useAuth()
  if (state.status !== 'signed-in') return null
  return (
    <AppShell>
      <Routes>
        {state.me.navigation.map((item) => (
          <Route key={item.key} path={item.path} element={SCREENS[item.key] ?? <NoAccessPage />} />
        ))}
        {state.me.navigation.some((item) => item.key === 'jobs') && <Route path="/jobs/:id" element={<JobDetailPage />} />}
        <Route path="/login" element={<Navigate to="/" replace />} />
        <Route path="*" element={<NoAccessPage />} />
      </Routes>
    </AppShell>
  )
}
