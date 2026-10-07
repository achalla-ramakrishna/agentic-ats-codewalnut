import { useState } from 'react'
import { analyzeAll, type InsightSummary, type InsightsResponse, type Suggestion } from '../api/insights'
import { Badge, Button, Card } from './ui'

export function FitBadge({ insight }: { insight?: InsightSummary }) {
  if (!insight) return <span className="muted">—</span>
  if (insight.status === 'PENDING') return <span className="muted">reading…</span>
  if (insight.status === 'NO_RESUME') return <span className="muted" title="No résumé uploaded">no résumé</span>
  if (insight.status === 'FAILED')
    return (
      <span className="muted" title={insight.error ?? ''}>
        couldn’t read
      </span>
    )
  if (insight.fitPercent == null) return <span className="muted">read</span>
  const tone = insight.fitPercent >= 75 ? 'fit-high' : insight.fitPercent >= 50 ? 'fit-mid' : 'fit-low'
  return (
    <span
      className={`fit ${tone}`}
      title={`Meets ${insight.met} of ${insight.total} requirements${insight.partial ? ` (${insight.partial} partly)` : ''}${insight.stale ? ' · out of date; re-analyze to refresh' : ''}`}
    >
      {insight.fitPercent}%{insight.stale ? '*' : ''}
    </span>
  )
}

function SuggestionList({ items, onOpen, empty }: { items: Suggestion[]; onOpen: (id: string) => void; empty: string }) {
  if (!items.length) return <p className="muted" style={{ margin: 0, fontSize: 14 }}>{empty}</p>
  return (
    <ol>
      {items.map((s) => (
        <li key={s.applicationId}>
          <button type="button" className="row-link" onClick={() => onOpen(s.applicationId)}>
            {s.candidateName}
          </button>
          {s.readiness != null ? (
            <span className="muted" title="From interview feedback, test score and résumé match">
              {' '}
              · readiness {s.readiness}%
            </span>
          ) : (
            s.fitPercent != null && <span className="muted"> · {s.fitPercent}%</span>
          )}
          <div className="muted" style={{ fontSize: 13 }}>
            {s.reason}
          </div>
        </li>
      ))}
    </ol>
  )
}

/**
 * Advisory suggestions from the AI résumé readings (ADR-0010): whom to contact next and who is
 * closest to selection, each with the reason. People decide.
 */
export function InsightsPanel({
  jobId,
  data,
  canEdit,
  onOpen,
  onChanged,
}: {
  jobId: string
  data: InsightsResponse
  canEdit: boolean
  onOpen: (applicationId: string) => void
  onChanged: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const stale = data.insights.filter((i) => i.stale).length
  const toRead = data.notAnalyzed + data.failed + stale

  async function analyze() {
    setBusy(true)
    setMessage(null)
    try {
      const r = await analyzeAll(jobId)
      setMessage(
        `Reading ${r.queued} résumé${r.queued === 1 ? '' : 's'}${r.noResume ? ` · ${r.noResume} without a résumé` : ''}${r.upToDate ? ` · ${r.upToDate} already up to date` : ''}.`,
      )
      onChanged()
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Could not start')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <div className="stack" role="region" aria-label="AI suggestions">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <span className="row" style={{ gap: 8 }}>
            <h2 style={{ margin: 0 }}>AI suggestions</h2>
            <Badge tone="primary">Advisory</Badge>
          </span>
          <span className="row" style={{ gap: 8 }}>
            <span className="muted" style={{ fontSize: 13 }}>
              {data.analyzed} read{data.pending ? ` · ${data.pending} reading…` : ''}
              {data.noResume ? ` · ${data.noResume} without résumé` : ''}
            </span>
            {canEdit && data.available && toRead > 0 && (
              <Button size="sm" variant="secondary" disabled={busy} onClick={() => void analyze()}>
                {busy ? 'Starting…' : `Analyze résumés (${toRead})`}
              </Button>
            )}
          </span>
        </div>
        {!data.hasDescription && (
          <div className="alert alert-info">
            Add a job description to this opening to get match scores — the AI matches résumés against it.
          </div>
        )}
        {message && <div className="alert alert-info">{message}</div>}
        <div className="suggestions">
          <div>
            <h3 style={{ margin: '0 0 4px' }}>Contact next</h3>
            <p className="muted" style={{ margin: '0 0 6px', fontSize: 13 }}>
              Applied, best résumé match to the opening first.
            </p>
            <SuggestionList
              items={data.contactNext}
              onOpen={onOpen}
              empty="No strong matches waiting. Upload or analyze résumés to get suggestions."
            />
          </div>
          <div>
            <h3 style={{ margin: '0 0 4px' }}>Closest to selection</h3>
            <p className="muted" style={{ margin: '0 0 6px', fontSize: 13 }}>
              Interviewed or shortlisted, ranked by interview feedback, test score and résumé match. Anyone the panel said no to is left out.
            </p>
            <SuggestionList items={data.closestToSelection} onOpen={onOpen} empty="Nobody has been interviewed yet." />
          </div>
        </div>
        <p className="muted" style={{ margin: 0, fontSize: 12 }}>
          Match = share of the opening’s requirements the résumé shows (met counts fully, partly counts half). It is based on
          the résumé only and ignores personal details; use it to decide whom to talk to first, not to rule anyone out.
        </p>
      </div>
    </Card>
  )
}
