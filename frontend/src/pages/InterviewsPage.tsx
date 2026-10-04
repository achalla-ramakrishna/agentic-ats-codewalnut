import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { formatWhen, listFeedbackDue, listRecentInterviews, listUpcomingInterviews, type Interview, type RecentInterview } from '../api/interviews'
import { useMe } from '../auth/AuthContext'
import { Badge, Card, PageHeader } from '../components/ui'
import '../components/tracker.css'

/** Upcoming interviews. Interviewers see only the ones they are on (the API decides). */
export function InterviewsPage() {
  const me = useMe()
  const canOpenJobs = me.navigation.some((n) => n.key === 'jobs')
  const [interviews, setInterviews] = useState<Interview[] | null>(null)
  const [due, setDue] = useState<Interview[]>([])
  const [recent, setRecent] = useState<RecentInterview[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listFeedbackDue()
      .then(setDue)
      .catch(() => undefined)
    listRecentInterviews()
      .then(setRecent)
      .catch(() => setRecent([]))
    listUpcomingInterviews()
      .then(setInterviews)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load interviews'))
  }, [])

  return (
    <div className="stack">
      <PageHeader
        title="Interviews"
        description="Give feedback after each interview, and see what's coming up. Schedule (or log) an interview from a candidate's panel in an opening."
        actions={
          <Link className="btn btn-secondary" to="/interview-questions">
            Interview questions
          </Link>
        }
      />
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {due.length > 0 && (
        <Card>
          <h3 style={{ marginTop: 0 }}>Waiting for your feedback</h3>
          <ul className="stack" style={{ margin: 0, paddingLeft: 18, gap: 6 }}>
            {due.map((i) => (
              <li key={i.id}>
                <Link to={`/interviews/${i.id}/feedback`}>{i.candidateName}</Link>{' '}
                <span className="muted">
                  · {i.jobTitle} · {formatWhen(i)}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}
      <Card>
        <h3 style={{ marginTop: 0 }}>Recent interviews and feedback</h3>
        <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
          Interviews from the last 30 days, once they have started. Click Give feedback to fill in the form.
        </p>
        {!recent && <p className="muted">Loading…</p>}
        {recent && recent.length === 0 && (
          <p className="muted" style={{ margin: 0 }}>
            No interviews in the last 30 days. Interviews scheduled in the app appear here once they start; for one set up elsewhere, open
            the candidate and use “Log an interview held elsewhere”.
          </p>
        )}
        {recent && recent.length > 0 && (
          <div className="table-wrap">
            <table className="table" aria-label="Recent interviews">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Candidate</th>
                  <th>Opening</th>
                  <th>Feedback</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {recent.map((r) => (
                  <tr key={r.interview.id}>
                    <td>{formatWhen(r.interview)}</td>
                    <td>{r.interview.candidateName}</td>
                    <td className="muted">{r.interview.jobTitle}</td>
                    <td>
                      {r.submitted} of {r.panelSize}{' '}
                      {r.onPanel && (r.mineSubmitted ? <Badge tone="primary">Yours is in</Badge> : <Badge tone="danger">Yours is due</Badge>)}
                    </td>
                    <td>
                      <Link
                        className={`btn btn-sm ${r.canSubmit && !r.mineSubmitted ? 'btn-primary' : 'btn-secondary'}`}
                        to={`/interviews/${r.interview.id}/feedback`}
                      >
                        {r.canSubmit && !r.mineSubmitted ? 'Give feedback' : 'View feedback'}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      <Card>
        <h3 style={{ marginTop: 0 }}>Upcoming</h3>
        {!interviews && !error && <p className="muted">Loading…</p>}
        {interviews && interviews.length === 0 && (
          <p className="muted" style={{ margin: 0 }}>
            No upcoming interviews.
          </p>
        )}
        {interviews && interviews.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Candidate</th>
                  <th>Opening</th>
                  <th>Interviewers</th>
                  <th>Meet</th>
                  <th>Kit</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {interviews.map((i) => (
                  <tr key={i.id}>
                    <td>{formatWhen(i)}</td>
                    <td>
                      {canOpenJobs ? (
                        <Link to={`/jobs/${i.jobId}?candidate=${i.applicationId}`}>{i.candidateName}</Link>
                      ) : (
                        i.candidateName
                      )}
                    </td>
                    <td className="muted">{i.jobTitle}</td>
                    <td className="muted">{i.interviewers.length ? i.interviewers.join(', ') : '—'}</td>
                    <td>
                      {i.meetLink ? (
                        <a href={i.meetLink} target="_blank" rel="noreferrer">
                          Join
                        </a>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>
                      <Link to={`/interview-kits/${i.jobId}`}>Kit</Link>
                    </td>
                    <td>
                      <Link className="btn btn-sm btn-secondary" to={`/interviews/${i.id}/feedback`}>
                        Feedback
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
