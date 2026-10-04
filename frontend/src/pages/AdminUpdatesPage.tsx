import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listAdminUpdates, type AdminEmailStatus, type AdminUpdatesPage as Data } from '../api/adminUpdates'
import { Badge, Card, PageHeader } from '../components/ui'

const EMAIL_LABEL: Record<AdminEmailStatus, string> = {
  SENT: 'Emailed to admins',
  SKIPPED: 'Not emailed: sender hadn’t connected Gmail',
  FAILED: 'Email failed for some admins',
  NONE: 'No other admin to email',
  PENDING: 'Email pending',
}

/** Candidate summaries for admins when feedback comes in or a key stage is reached (ADM-14…ADM-17). */
export function AdminUpdatesPage() {
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [kind, setKind] = useState<'all' | 'FEEDBACK_SUBMITTED' | 'STAGE_REACHED'>('all')

  useEffect(() => {
    listAdminUpdates()
      .then(setData)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load admin updates'))
  }, [])

  const shown = data?.updates.filter((u) => kind === 'all' || u.kind === kind) ?? []

  return (
    <div className="stack">
      <PageHeader
        title="Admin updates"
        description={`A short candidate summary whenever interview feedback comes in or a candidate reaches ${
          data ? data.keyStages.join(', ') : 'an important stage'
        }. Also emailed to admins from the sender's Gmail when they've connected it.`}
      />
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <div className="row" role="group" aria-label="Show" style={{ gap: 8 }}>
        {(
          [
            ['all', 'All'],
            ['FEEDBACK_SUBMITTED', 'Interview feedback'],
            ['STAGE_REACHED', 'Stage changes'],
          ] as const
        ).map(([k, label]) => (
          <button key={k} type="button" aria-pressed={kind === k} className={`chip${kind === k ? ' chip-on' : ''}`} onClick={() => setKind(k)}>
            {label}
          </button>
        ))}
      </div>
      {!data && !error && <p className="muted">Loading…</p>}
      {data && shown.length === 0 && (
        <Card>
          <p className="muted" style={{ margin: 0 }}>
            No updates yet.
          </p>
        </Card>
      )}
      {shown.map((u) => (
        <Card key={u.id}>
          <div className="stack" style={{ gap: 8 }}>
            <div className="row" style={{ justifyContent: 'space-between', flexWrap: 'wrap' }}>
              <strong>{u.title}</strong>
              <div className="row" style={{ gap: 6 }}>
                <Badge tone={u.kind === 'FEEDBACK_SUBMITTED' ? 'primary' : 'neutral'}>
                  {u.kind === 'FEEDBACK_SUBMITTED' ? 'Feedback' : 'Stage'}
                </Badge>
                <Badge tone={u.emailStatus === 'FAILED' ? 'danger' : 'neutral'}>{EMAIL_LABEL[u.emailStatus]}</Badge>
              </div>
            </div>
            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit', fontSize: 14 }}>{u.body}</pre>
            <div className="row muted" style={{ gap: 12, fontSize: 12, flexWrap: 'wrap' }}>
              <span>
                {new Date(u.createdAt).toLocaleString('en-IN')} · by {u.actorEmail}
              </span>
              {u.jobId && <Link to={`/jobs/${u.jobId}?candidate=${u.applicationId}`}>Open candidate</Link>}
              {u.interviewId && <Link to={`/interviews/${u.interviewId}/feedback`}>Open feedback</Link>}
            </div>
          </div>
        </Card>
      ))}
    </div>
  )
}
