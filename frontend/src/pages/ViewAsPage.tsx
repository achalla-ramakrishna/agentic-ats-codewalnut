import { useEffect, useState } from 'react'
import { getViewAsOptions, startViewAs, type ViewAsOptions } from '../api/viewAs'
import { Button, Card, PageHeader } from '../components/ui'

/**
 * Admins see the app exactly as a candidate, a client contact or a staff role sees it, before
 * inviting people (ADR-0013). Read-only and time-limited; every start and stop is audited.
 */
export function ViewAsPage() {
  const [options, setOptions] = useState<ViewAsOptions | null>(null)
  const [q, setQ] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      getViewAsOptions(q.trim() || undefined)
        .then(setOptions)
        .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load'))
    }, 250)
    return () => window.clearTimeout(timer)
  }, [q])

  async function go(input: Parameters<typeof startViewAs>[0]) {
    setError(null)
    try {
      await startViewAs(input)
      window.location.assign('/')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not start')
    }
  }

  return (
    <div className="stack">
      <PageHeader
        title="View as"
        description={`See exactly what a candidate, a client’s hiring manager or a CodeWalnut role sees — before you invite them. Read-only, for ${options?.minutes ?? 30} minutes; recorded in the audit log.`}
      />
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <div className="suggestions">
        <Card className="stack">
          <h2 style={{ margin: 0 }}>CodeWalnut roles</h2>
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>
            Your own account with only that role’s access: menu, pages and buttons.
          </p>
          <div className="row" style={{ gap: 8 }}>
            {options?.roles.map((r) => (
              <Button key={r.role} variant="secondary" onClick={() => void go({ kind: 'ROLE', role: r.role })}>
                {r.label}
              </Button>
            ))}
          </div>
        </Card>
        <Card className="stack">
          <h2 style={{ margin: 0 }}>Client contacts</h2>
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>
            What a client’s hiring manager sees when they sign in: only the candidates shared with them.
          </p>
          {options && options.clients.length === 0 && (
            <p className="muted" style={{ margin: 0 }}>No client contacts yet. Add one under Clients.</p>
          )}
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }} className="stack">
            {options?.clients.map((c) => (
              <li key={c.email} className="row" style={{ justifyContent: 'space-between' }}>
                <span>
                  <strong>{c.name || c.email}</strong> <span className="muted">· {c.clientName}</span>
                </span>
                <Button size="sm" variant="secondary" onClick={() => void go({ kind: 'CLIENT', email: c.email })}>
                  View as
                </Button>
              </li>
            ))}
          </ul>
        </Card>
      </div>
      <Card className="stack">
        <h2 style={{ margin: 0 }}>Candidates</h2>
        <p className="muted" style={{ margin: 0, fontSize: 14 }}>
          Their candidate page: applications, messages, tests, documents to upload. Only candidates with an email can sign in.
        </p>
        <input
          className="input"
          type="search"
          aria-label="Search candidates"
          placeholder="Search by name or email"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          style={{ maxWidth: 420 }}
        />
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }} className="stack">
          {options?.candidates.map((c) => (
            <li key={c.email} className="row" style={{ justifyContent: 'space-between' }}>
              <span>
                <strong>{c.name}</strong> <span className="muted">· {c.email}</span>
                {c.openings.length > 0 && <span className="muted"> · {c.openings.join(', ')}</span>}
              </span>
              <Button size="sm" variant="secondary" onClick={() => void go({ kind: 'CANDIDATE', email: c.email })}>
                View as
              </Button>
            </li>
          ))}
        </ul>
        {options && options.candidates.length === 0 && <p className="muted" style={{ margin: 0 }}>No candidates match.</p>}
      </Card>
    </div>
  )
}
