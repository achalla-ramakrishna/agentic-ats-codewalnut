import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { listMyApplications, type MyApplication } from '../api/tracker'
import type { CandidateMe } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Badge, Button, Card, PageHeader } from '../components/ui'
import './CandidateHomePage.css'

/** Candidate area: their own applications with a simple status (docs/features/job-links.md). */
export function CandidateHomePage({ candidate }: { candidate: CandidateMe }) {
  const { signOut } = useAuth()
  const location = useLocation()
  const applied = (location.state as { applied?: string } | null)?.applied
  const [applications, setApplications] = useState<MyApplication[] | null>(null)

  useEffect(() => {
    listMyApplications().then(setApplications).catch(() => setApplications([]))
  }, [])

  return (
    <div className="candidate">
      <header className="candidate-bar">
        <div className="brand">
          <img src="/favicon.svg" alt="" />
          <span>CodeWalnut Careers</span>
        </div>
        <div className="row">
          <span className="muted">{candidate.email}</span>
          <Button variant="secondary" size="sm" onClick={() => void signOut()}>
            Sign out
          </Button>
        </div>
      </header>
      <main className="candidate-main">
        <PageHeader
          title={`Hi${candidate.name ? ` ${candidate.name.split(' ')[0]}` : ''}`}
          description="Track your applications with CodeWalnut here."
        />
        {applied && (
          <div className="alert alert-info" style={{ marginBottom: 16 }}>
            Thanks — your application for <strong>{applied}</strong> was received. We'll be in touch.
          </div>
        )}
        <Card className="stack">
          <h2>My applications</h2>
          {!applications && <p className="muted">Loading…</p>}
          {applications && applications.length === 0 && (
            <p className="muted" style={{ margin: 0 }}>You haven't applied to any roles yet. Use the job link you were sent to apply.</p>
          )}
          {applications && applications.length > 0 && (
            <table className="table">
              <thead>
                <tr>
                  <th>Role</th>
                  <th>Applied</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {applications.map((a, i) => (
                  <tr key={`${a.jobTitle}-${i}`}>
                    <td>{a.slug ? <Link to={`/apply/${a.slug}`}>{a.jobTitle}</Link> : a.jobTitle}</td>
                    <td className="muted">{new Date(a.appliedAt).toLocaleDateString()}</td>
                    <td>
                      <Badge tone={a.status === 'Not progressing' ? 'neutral' : 'primary'}>{a.status}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      </main>
    </div>
  )
}
