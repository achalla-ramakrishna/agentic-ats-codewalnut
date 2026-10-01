import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getInbox, type InboxItem } from '../api/messages'
import { Badge, Card, PageHeader } from '../components/ui'
import '../components/tracker.css'

const REFRESH_MS = 30_000

/** Conversations with candidates and clients, those waiting for our reply first (docs/features/communication.md). */
export function MessagesPage() {
  const [items, setItems] = useState<InboxItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const load = () =>
      getInbox()
        .then((data) => {
          setItems(data)
          setError(null)
        })
        .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load messages'))
    load()
    const timer = window.setInterval(load, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [])

  const waiting = items?.filter((i) => i.awaitingReply).length ?? 0

  return (
    <div className="stack">
      <PageHeader
        title="Messages"
        description={
          waiting > 0
            ? `${waiting} conversation${waiting === 1 ? ' is' : 's are'} waiting for your reply.`
            : 'Conversations with candidates and clients. Start one from a candidate’s chat tabs.'
        }
      />
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <Card>
        {!items && !error && <p className="muted">Loading…</p>}
        {items && items.length === 0 && (
          <p className="muted" style={{ margin: 0 }}>
            No conversations yet.
          </p>
        )}
        {items && items.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Candidate</th>
                  <th>With</th>
                  <th>Opening</th>
                  <th>Last message</th>
                  <th>When</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((i) => (
                  <tr key={`${i.applicationId}-${i.channel}`}>
                    <td>
                      <Link to={`/jobs/${i.jobId}?candidate=${i.applicationId}&tab=${i.channel === 'CLIENT' ? 'client' : 'candidate'}`}>
                        {i.candidateName}
                      </Link>
                    </td>
                    <td className="muted">{i.channel === 'CLIENT' ? (i.clientName ?? 'Client') : 'Candidate'}</td>
                    <td className="muted">{i.jobTitle}</td>
                    <td style={{ maxWidth: 360 }}>
                      <span className="muted">{`${i.lastAuthorName}: `}</span>
                      {i.preview}
                    </td>
                    <td className="muted">{new Date(i.lastAt).toLocaleString()}</td>
                    <td>{i.awaitingReply && <Badge tone="primary">Awaiting reply</Badge>}</td>
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
