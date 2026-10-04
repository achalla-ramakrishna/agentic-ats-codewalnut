import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { formatWhen, listFeedbackDue, listUpcomingInterviews, type Interview } from '../api/interviews'
import { useMe } from '../auth/AuthContext'
import { Card, PageHeader } from '../components/ui'
import '../components/tracker.css'

/** Upcoming interviews. Interviewers see only the ones they are on (the API decides). */
export function InterviewsPage() {
  const me = useMe()
  const canOpenJobs = me.navigation.some((n) => n.key === 'jobs')
  const [interviews, setInterviews] = useState<Interview[] | null>(null)
  const [due, setDue] = useState<Interview[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listFeedbackDue()
      .then(setDue)
      .catch(() => undefined)
    listUpcomingInterviews()
      .then(setInterviews)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load interviews'))
  }, [])

  return (
    <div className="stack">
      <PageHeader
        title="Interviews"
        description="Upcoming interviews and feedback to give. Schedule one from a candidate's panel in an opening."
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
