import { useState } from 'react'
import { Link } from 'react-router-dom'
import { markResultsSeen } from '../api/assessments'
import { Badge, Button, Card } from './ui'
import { newResultsChanged, useNewResults } from './useNewResults'

function ago(iso: string | null): string {
  if (!iso) return ''
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  return new Date(iso).toLocaleDateString()
}

/**
 * Tests candidates have finished that nobody has looked at yet (ASMT-32). Open goes to the
 * candidate's Tests tab in their opening; opening the answers there marks the result seen.
 */
export function NewResults() {
  const { results } = useNewResults(true)
  const [error, setError] = useState<string | null>(null)

  async function seen(ids: string[]) {
    setError(null)
    try {
      await markResultsSeen(ids)
      newResultsChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not update')
    }
  }

  if (results.length === 0) return null
  return (
    <Card>
      <section className="stack" aria-label="New test results" style={{ gap: 8 }}>
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h2 style={{ margin: 0 }}>
            New test results <Badge tone="primary">{results.length}</Badge>
          </h2>
          {results.length > 1 && (
            <Button size="sm" variant="ghost" onClick={() => void seen(results.map((r) => r.invite.id))}>
              Mark all seen
            </Button>
          )}
        </div>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <table className="send-list" aria-label="New results">
          <tbody>
            {results.map((r) => (
              <tr key={r.invite.id}>
                <td>
                  <strong>{r.candidateName}</strong>
                  <div className="muted" style={{ fontSize: 12 }}>{r.jobTitle}</div>
                </td>
                <td>{r.invite.title}</td>
                <td>
                  <strong>{r.invite.percent}%</strong>{' '}
                  <Badge tone={r.invite.passed ? 'primary' : 'neutral'}>{r.invite.passed ? 'Passed' : 'Below pass mark'}</Badge>
                  <div className="muted" style={{ fontSize: 12 }}>
                    {r.invite.score}/{r.invite.maxScore} · pass mark {r.invite.passPercent}%
                  </div>
                </td>
                <td className="muted" style={{ fontSize: 13 }}>{ago(r.invite.submittedAt)}</td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <Link className="linklike" to={`/jobs/${r.jobId}?candidate=${r.invite.applicationId}&tab=tests`}>
                    Open
                  </Link>{' '}
                  <Button size="sm" variant="ghost" onClick={() => void seen([r.invite.id])}>
                    Mark seen
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </Card>
  )
}
