import { useCallback, useEffect, useState } from 'react'
import { getInsight, reanalyze, type InsightDetail } from '../api/insights'
import { Badge, Button } from './ui'

const MARK: Record<string, string> = { MET: '✓', PARTIAL: '◐', NOT_EVIDENT: '·' }

/** The AI's reading of this candidate's résumé against the opening, in the drawer (ADR-0010). */
export function ResumeInsightSection({ applicationId, canEdit }: { applicationId: string; canEdit: boolean }) {
  const [detail, setDetail] = useState<InsightDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    getInsight(applicationId).then(setDetail).catch(() => setDetail(null))
  }, [applicationId])
  useEffect(load, [load])

  const pending = detail?.status === 'PENDING'
  useEffect(() => {
    if (!pending) return
    const timer = window.setInterval(load, 3000)
    return () => window.clearInterval(timer)
  }, [pending, load])

  async function again() {
    setError(null)
    try {
      setDetail(await reanalyze(applicationId))
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not re-analyze')
    }
  }

  if (!detail) return null
  const p = detail.profile
  return (
    <section className="stack" aria-label="AI résumé insights" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="row" style={{ gap: 8 }}>
          <h3 style={{ margin: 0 }}>AI résumé insights</h3>
          <Badge tone="primary">Advisory</Badge>
          {detail.fitPercent != null && <strong>{detail.fitPercent}% match</strong>}
        </span>
        {canEdit && detail.status !== 'PENDING' && detail.status !== 'NO_RESUME' && (
          <Button size="sm" variant="ghost" onClick={() => void again()}>
            {detail.status === 'NONE' ? 'Analyze résumé' : 'Re-analyze'}
          </Button>
        )}
      </div>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {detail.status === 'NONE' && <p className="muted" style={{ margin: 0 }}>Not analyzed yet.</p>}
      {detail.status === 'NO_RESUME' && <p className="muted" style={{ margin: 0 }}>Upload the original résumé to get insights.</p>}
      {pending && <p className="muted" style={{ margin: 0 }}>Reading the résumé…</p>}
      {detail.status === 'FAILED' && <p className="alert alert-error" style={{ margin: 0 }}>{detail.error}</p>}
      {detail.stale && <p className="muted" style={{ margin: 0 }}>This reading is out of date (the opening changed, or it was made before profile filling); re-analyze to refresh.</p>}
      {p && (
        <>
          {p.headline && <p style={{ margin: 0 }}>{p.headline}</p>}
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>
            {[
              p.currentRole,
              p.experienceMonths ? `${p.experienceMonths} months experience` : null,
              p.education,
              p.graduationYear ? `graduation ${p.graduationYear}` : null,
              p.location,
            ]
              .filter(Boolean)
              .join(' · ')}
          </p>
          {!!p.requirements?.length && (
            <ul className="req-list" aria-label="Requirements">
              {p.requirements.map((r) => (
                <li key={r.requirement}>
                  <span className="req-mark" title={r.assessment}>
                    {MARK[r.assessment] ?? '·'}
                  </span>
                  <span>
                    {r.requirement} <span className="muted">— {r.evidence}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
          {!!p.skills?.length && (
            <div style={{ fontSize: 14 }}>
              <span className="muted">Skills:</span> {p.skills.join(', ')}
            </div>
          )}
          {!!p.projects?.length && (
            <div style={{ fontSize: 14 }}>
              <span className="muted">Projects:</span>
              <ul style={{ margin: '2px 0 0', paddingLeft: 20 }}>
                {p.projects.map((pr) => (
                  <li key={pr.name}>
                    <strong>{pr.name}</strong> {pr.summary && <span className="muted">— {pr.summary}</span>}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {!!p.experience?.length && (
            <div style={{ fontSize: 14 }}>
              <span className="muted">Experience:</span>
              <ul style={{ margin: '2px 0 0', paddingLeft: 20 }}>
                {p.experience.map((x) => (
                  <li key={`${x.role}-${x.organisation}-${x.period}`}>
                    {x.role}
                    {x.organisation ? `, ${x.organisation}` : ''} <span className="muted">{x.period}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {!!p.strengths?.length && (
            <div style={{ fontSize: 14 }}>
              <span className="muted">Strengths:</span> {p.strengths.join(' · ')}
            </div>
          )}
          {!!p.gaps?.length && (
            <div style={{ fontSize: 14 }}>
              <span className="muted">Not evident in résumé:</span> {p.gaps.join(' · ')}
            </div>
          )}
          {!!p.questionsToAsk?.length && (
            <div style={{ fontSize: 14 }}>
              <span className="muted">Ask in the first call:</span>
              <ul style={{ margin: '2px 0 0', paddingLeft: 20 }}>
                {p.questionsToAsk.map((q) => (
                  <li key={q}>{q}</li>
                ))}
              </ul>
            </div>
          )}
          <p className="muted" style={{ margin: 0, fontSize: 12 }}>
            Read from {detail.documentFileName ?? 'the résumé'}
            {detail.analyzedAt ? ` on ${new Date(detail.analyzedAt).toLocaleDateString()}` : ''}. AI can make mistakes;
            check the résumé before deciding.
          </p>
        </>
      )}
    </section>
  )
}
