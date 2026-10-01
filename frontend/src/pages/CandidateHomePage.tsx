import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { formatWhen, listMyInterviews, type CandidateInterview } from '../api/interviews'
import { listMyApplications, type MyApplication } from '../api/tracker'
import type { CandidateMe } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CandidateThread } from '../components/CandidateThread'
import { MyProfileCard } from '../components/MyProfileCard'
import { Badge, Button, Card, PageHeader } from '../components/ui'
import './CandidateHomePage.css'

/** Candidate area: their own applications with a simple status (docs/features/job-links.md). */
export function CandidateHomePage({ candidate }: { candidate: CandidateMe }) {
  const { signOut } = useAuth()
  const location = useLocation()
  const applied = (location.state as { applied?: string } | null)?.applied
  const [applications, setApplications] = useState<MyApplication[] | null>(null)
  const [interviews, setInterviews] = useState<CandidateInterview[]>([])
  const [chatWith, setChatWith] = useState<MyApplication | null>(null)
  const markRead = useCallback(() => {
    setApplications((apps) => {
      if (!apps || !chatWith) return apps
      const current = apps.find((a) => a.id === chatWith.id)
      if (!current || current.newMessages === 0) return apps
      return apps.map((a) => (a.id === chatWith.id ? { ...a, newMessages: 0 } : a))
    })
  }, [chatWith])

  useEffect(() => {
    listMyApplications().then(setApplications).catch(() => setApplications([]))
    listMyInterviews().then(setInterviews).catch(() => setInterviews([]))
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
        {interviews.length > 0 && (
          <Card className="stack" style={{ marginBottom: 16 }}>
            <h2>Upcoming interviews</h2>
            {interviews.map((i, n) => (
              <div key={`${i.startAt}-${n}`} className="stack" style={{ gap: 4 }}>
                <strong>{i.title}</strong>
                <span>{formatWhen(i)}</span>
                {i.meetLink && (
                  <div>
                    <a className="btn btn-primary btn-sm" href={i.meetLink} target="_blank" rel="noreferrer">
                      Join Google Meet
                    </a>
                  </div>
                )}
              </div>
            ))}
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>
              The invitation was also emailed to you. To reschedule, reply to that email.
            </p>
          </Card>
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
                  <th>Messages</th>
                </tr>
              </thead>
              <tbody>
                {applications.map((a, i) => (
                  <tr key={a.id ?? i}>
                    <td>{a.slug ? <Link to={`/apply/${a.slug}`}>{a.jobTitle}</Link> : a.jobTitle}</td>
                    <td className="muted">{new Date(a.appliedAt).toLocaleDateString()}</td>
                    <td>
                      <Badge tone={a.status === 'Not progressing' ? 'neutral' : 'primary'}>{a.status}</Badge>
                    </td>
                    <td>
                      <Button size="sm" variant={a.newMessages > 0 ? 'primary' : 'secondary'} onClick={() => setChatWith(a)}>
                        {a.newMessages > 0 ? `${a.newMessages} new` : 'Open'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
        {chatWith && (
          <Card className="stack" style={{ marginTop: 16 }}>
            <CandidateThread key={chatWith.id} applicationId={chatWith.id} jobTitle={chatWith.jobTitle} onRead={markRead} />
          </Card>
        )}
        <MyProfileCard />
      </main>
    </div>
  )
}
