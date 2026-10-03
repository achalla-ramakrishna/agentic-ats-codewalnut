import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth, useMe } from '../auth/AuthContext'
import { ROLE_LABELS } from '../api/types'
import { Badge, Button } from './ui'
import { useNewResults } from './useNewResults'
import { useWhatsNew } from './useWhatsNew'
import './AppShell.css'

/** Layout for signed-in users. Navigation comes from the server (GET /me). */
export function AppShell({ children }: { children: ReactNode }) {
  const me = useMe()
  const { signOut } = useAuth()
  const { unseen } = useWhatsNew()
  const { results: newResults } = useNewResults(me.capabilities.includes('MANAGE_JOBS') && me.navigation.some((n) => n.key === 'tests'))

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <img src="/favicon.svg" alt="" />
          <span>CodeWalnut ATS</span>
        </div>
        <nav className="nav" aria-label="Main">
          {me.navigation.map((item) => (
            <NavLink key={item.key} to={item.path} end={item.path === '/'} className={item.key === 'tests' ? 'nav-with-count' : undefined}>
              {item.label}
              {item.key === 'tests' && newResults.length > 0 && (
                <span className="nav-count" aria-label={`${newResults.length} new test result${newResults.length === 1 ? '' : 's'}`}>
                  {newResults.length}
                </span>
              )}
            </NavLink>
          ))}
          <NavLink to="/whats-new" className="nav-whats-new">
            What&apos;s new
            {unseen.length > 0 && (
              <span className="nav-count" aria-label={`${unseen.length} new`}>
                {unseen.length}
              </span>
            )}
          </NavLink>
        </nav>
        <div className="sidebar-footer">
          <span className="who">{me.name ?? me.email}</span>
          <div className="row">
            {me.roles.map((role) => (
              <Badge key={role} tone="primary">
                {ROLE_LABELS[role]}
              </Badge>
            ))}
          </div>
          <Button variant="secondary" size="sm" onClick={() => void signOut()}>
            Sign out
          </Button>
        </div>
      </aside>
      <main className="main">
        <div className="main-inner">{children}</div>
      </main>
    </div>
  )
}
