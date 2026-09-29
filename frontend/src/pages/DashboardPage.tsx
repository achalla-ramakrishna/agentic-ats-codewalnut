import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { formatWhen, listUpcomingInterviews, type Interview } from '../api/interviews'
import { getInbox, type InboxItem } from '../api/messages'
import { getDashboard, type Dashboard } from '../api/tracker'
import { useMe } from '../auth/AuthContext'
import { StageBar } from '../components/StageBar'
import { Card, PageHeader } from '../components/ui'
import { useStages } from '../components/useStages'
import '../components/tracker.css'

export function DashboardPage() {
  const me = useMe()
  const stages = useStages()
  const label = (s: string | null) => stages.find((x) => x.key === s)?.label ?? s ?? ''
  const [data, setData] = useState<Dashboard | null>(null)
  const [interviews, setInterviews] = useState<Interview[]>([])
  const seesInterviews = me.capabilities.includes('VIEW_INTERVIEWS')
  const canMessage = me.capabilities.includes('MESSAGE_CANDIDATES')
  const [waiting, setWaiting] = useState<InboxItem[]>([])

  useEffect(() => {
    getDashboard().then(setData).catch(() => setData({ openJobs: [], recentActivity: [] }))
    if (seesInterviews) listUpcomingInterviews().then(setInterviews).catch(() => setInterviews([]))
    if (canMessage)
      getInbox()
        .then((items) => setWaiting(items.filter((i) => i.awaitingReply)))
        .catch(() => setWaiting([]))
  }, [seesInterviews, canMessage])

  return (
    <div className="stack">
      <PageHeader title={`Welcome${me.name ? `, ${me.name.split(' ')[0]}` : ''}`} description="Open roles and what changed recently." />
      {data && data.openJobs.length === 0 && (
        <Card>
          <p style={{ margin: 0 }}>
            No open roles yet. {me.capabilities.includes('MANAGE_JOBS') && <Link to="/jobs">Create the first opening</Link>}
          </p>
        </Card>
      )}
      <div className="grid-2">
        {data?.openJobs.map((job) => (
          <Card key={job.id} className="stack" style={{ gap: 10 }}>
            <div>
              <Link to={`/jobs/${job.id}`} style={{ fontWeight: 700, fontSize: 16 }}>
                {job.title}
              </Link>
              <div className="muted" style={{ fontSize: 13 }}>
                {job.client ? `${job.client.name} · ` : ''}
                {job.hiringTypeLabel}
                {job.status === 'ON_HOLD' ? ' · On hold' : ''}
              </div>
            </div>
            <StageBar job={job} />
            <div className="row" style={{ fontSize: 13 }}>
              <strong>{job.total}</strong> candidates
              {job.openings ? <span className="muted">· {job.openings} needed</span> : null}
              {(job.stageCounts.JOINED ?? 0) > 0 && <span className="muted">· {job.stageCounts.JOINED} joined</span>}
            </div>
          </Card>
        ))}
      </div>
      {waiting.length > 0 && (
        <Card className="stack">
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <h2>Candidates waiting for a reply</h2>
            <Link to="/messages">All messages</Link>
          </div>
          <ul className="timeline">
            {waiting.slice(0, 5).map((i) => (
              <li key={i.applicationId}>
                <div>
                  <Link to={`/jobs/${i.jobId}?candidate=${i.applicationId}&tab=candidate`}>{i.candidateName}</Link> ({i.jobTitle}):{' '}
                  {i.preview}
                </div>
                <div className="meta">{new Date(i.lastAt).toLocaleString()}</div>
              </li>
            ))}
          </ul>
        </Card>
      )}
      {interviews.length > 0 && (
        <Card className="stack">
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <h2>Upcoming interviews</h2>
            <Link to="/interviews">All interviews</Link>
          </div>
          <ul className="timeline">
            {interviews.slice(0, 5).map((i) => (
              <li key={i.id}>
                <div>
                  <strong>{i.candidateName}</strong> ({i.jobTitle})
                </div>
                <div className="meta">
                  {formatWhen(i)}
                  {i.meetLink && (
                    <>
                      {' · '}
                      <a href={i.meetLink} target="_blank" rel="noreferrer">
                        Join
                      </a>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
      {data && data.recentActivity.length > 0 && (
        <Card className="stack">
          <h2>Recent activity</h2>
          <ul className="timeline">
            {data.recentActivity.map((e) => (
              <li key={e.id}>
                <div>
                  <strong>{e.candidateName}</strong> ({e.jobTitle}):{' '}
                  {e.type === 'CREATED'
                    ? `added at ${label(e.toStage)}`
                    : e.type === 'STAGE_CHANGED'
                      ? `${label(e.fromStage)} → ${label(e.toStage)}`
                      : e.type === 'INTERVIEW_SCHEDULED'
                        ? 'interview scheduled'
                        : e.type === 'INTERVIEW_CANCELLED'
                          ? 'interview cancelled'
                          : e.type === 'EMAIL_SENT'
                            ? 'email sent'
                            : 'note'}
                  {e.note && e.note !== 'Imported' ? ` — ${e.note}` : ''}
                </div>
                <div className="meta">
                  {e.actorEmail ?? 'system'} · {new Date(e.createdAt).toLocaleString()}
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  )
}
