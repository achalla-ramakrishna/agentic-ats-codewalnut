import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useWhatsNew } from '../components/useWhatsNew'
import { Badge, Card, PageHeader } from '../components/ui'

/** Release notes with how to use each feature (src/whatsNew.ts). Opening the page marks them seen. */
export function WhatsNewPage() {
  const { entries, unseen, markAllSeen } = useWhatsNew()
  // Remember which were new when the page opened, so the "New" badges stay while reading.
  const [newIds] = useState(() => new Set(unseen.map((e) => e.id)))

  useEffect(() => {
    markAllSeen()
  }, [markAllSeen])

  return (
    <div className="stack">
      <PageHeader title="What's new" description="New features in the CodeWalnut ATS and how to use them." />
      {entries.map((e) => (
        <Card key={e.id} className="stack" role="article" aria-label={e.title} style={{ gap: 8 }}>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <h2 style={{ margin: 0 }}>{e.title}</h2>
            <span className="row" style={{ gap: 8 }}>
              {newIds.has(e.id) && <Badge tone="primary">New</Badge>}
              <span className="muted" style={{ fontSize: 13 }}>
                {new Date(e.date).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}
              </span>
            </span>
          </div>
          <p style={{ margin: 0 }}>{e.summary}</p>
          {e.steps.length > 0 && (
            <ol style={{ margin: 0, paddingLeft: 20 }}>
              {e.steps.map((s) => (
                <li key={s}>{s}</li>
              ))}
            </ol>
          )}
          {e.link && (
            <div>
              <Link to={e.link.to}>{e.link.label} →</Link>
            </div>
          )}
        </Card>
      ))}
    </div>
  )
}
