import type { ReactElement } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { AuditLogPage } from './pages/AuditLogPage'
import { ComingSoonPage } from './pages/ComingSoonPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { NoAccessPage } from './pages/NoAccessPage'
import { UsersPage } from './pages/UsersPage'

/** Screen per navigation key. The server decides which keys a user gets. */
const SCREENS: Record<string, ReactElement> = {
  dashboard: <DashboardPage />,
  jobs: <ComingSoonPage title="Jobs" chunk="chunk 1" spec="jobs-and-careers-page.md" />,
  candidates: <ComingSoonPage title="Candidates" chunk="chunk 2" spec="candidates.md" />,
  clients: <ComingSoonPage title="Clients" chunk="chunk 1" spec="clients.md" />,
  interviews: <ComingSoonPage title="Interviews" chunk="chunk 4" spec="interviews-and-scorecards.md" />,
  approvals: <ComingSoonPage title="Approvals" chunk="chunks 1 and 7" spec="requisitions.md" />,
  reports: <ComingSoonPage title="Reports" chunk="v1" spec="reports.md" />,
  users: <UsersPage />,
  'audit-log': <AuditLogPage />,
}

export function App() {
  const { state, refresh } = useAuth()

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
  if (state.status === 'signed-out') {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    )
  }

  return (
    <AppShell>
      <Routes>
        {state.me.navigation.map((item) => (
          <Route key={item.key} path={item.path} element={SCREENS[item.key] ?? <NoAccessPage />} />
        ))}
        <Route path="/login" element={<Navigate to="/" replace />} />
        <Route path="*" element={<NoAccessPage />} />
      </Routes>
    </AppShell>
  )
}
