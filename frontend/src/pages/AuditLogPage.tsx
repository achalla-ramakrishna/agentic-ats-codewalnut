import { useEffect, useState } from 'react'
import { listAuditLog } from '../api/auditLog'
import type { AuditEntry, Page } from '../api/types'
import { Badge, Button, Card, PageHeader } from '../components/ui'

export function AuditLogPage() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<AuditEntry> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listAuditLog(page)
      .then(setData)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the audit log'))
  }, [page])

  return (
    <div className="stack">
      <PageHeader title="Audit log" description="Append-only record of sign-ins, permission changes and denied access." />
      <Card>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        {!data && !error && <p className="muted">Loading…</p>}
        {data && (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Who</th>
                    <th>Action</th>
                    <th>Record</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((entry) => (
                    <tr key={entry.id}>
                      <td className="muted">{new Date(entry.createdAt).toLocaleString()}</td>
                      <td>{entry.actorEmail ?? '—'}</td>
                      <td>
                        <Badge tone={entry.action.includes('DENIED') || entry.action.includes('REJECTED') ? 'danger' : 'neutral'}>
                          {entry.action}
                        </Badge>
                      </td>
                      <td className="muted">{entry.entityType ? `${entry.entityType} ${entry.entityId?.slice(0, 8) ?? ''}` : '—'}</td>
                      <td>
                        <code>{entry.details ?? ''}</code>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="row" style={{ marginTop: 16 }}>
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                Newer
              </Button>
              <span className="muted">
                Page {data.page + 1} of {Math.max(data.totalPages, 1)}
              </span>
              <Button size="sm" variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
                Older
              </Button>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}
