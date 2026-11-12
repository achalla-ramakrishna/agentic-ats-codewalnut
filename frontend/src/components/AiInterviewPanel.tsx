import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AREA_SHORT, createAiInterview, jobInterviews, type JobInterviews, type RankedCandidate } from '../api/aiInterviews'
import { moveStage } from '../api/tracker'
import { Dots } from './InterviewReviewCard'
import { useStages } from './useStages'
import { Badge, Button, Card } from './ui'

const AREAS = ['TECHNICAL', 'ARCHITECTURE', 'COMMUNICATION', 'EXPERIENCE', 'READINESS']

function status(r: RankedCandidate) {
  switch (r.status) {
    case 'SUBMITTED':
      return r.evaluation === 'PENDING' ? 'Reviewing…' : r.evaluation === 'FAILED' ? 'Review failed' : 'Reviewed'
    case 'STARTED':
      return 'In progress'
    case 'SENT':
      return 'Not started'
    case 'EXPIRED':
      return 'Not taken'
    default:
      return r.status
  }
}

/**
 * The opening's AI interviews (ADR-0030): create one, then the candidates ranked by the review's
 * score with a rating per area, notice period and salary. Shortlisting is a person's choice.
 */
export function AiInterviewPanel({
  jobId,
  canEdit,
  onOpen,
  onChanged,
  onClose,
}: {
  jobId: string
  canEdit: boolean
  onOpen: (applicationId: string) => void
  onChanged: () => void
  onClose: () => void
}) {
  const navigate = useNavigate()
  const stages = useStages()
  const [data, setData] = useState<JobInterviews | null>(null)
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [onlyReviewed, setOnlyReviewed] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback(() => {
    jobInterviews(jobId)
      .then(setData)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the interviews'))
  }, [jobId])
  useEffect(load, [load])
  // Reviews finish in the background: refresh while any are running.
  useEffect(() => {
    if (!data?.candidates.some((c) => c.evaluation === 'PENDING')) return
    const timer = window.setTimeout(load, 5000)
    return () => window.clearTimeout(timer)
  }, [data, load])

  async function create() {
    setBusy(true)
    setError(null)
    try {
      const r = await createAiInterview(jobId)
      if (r.notes.length) window.alert(r.notes.join('\n'))
      navigate(`/tests?test=${r.interview.summary.id}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create the interview')
      setBusy(false)
    }
  }

  async function shortlist() {
    const chosen = (data?.candidates ?? []).filter((c) => picked.has(c.applicationId))
    if (!chosen.length || !window.confirm(`Move ${chosen.length} candidate(s) to Shortlisted?`)) return
    setBusy(true)
    setError(null)
    let moved = 0
    for (const c of chosen) {
      try {
        await moveStage(c.applicationId, 'SHORTLISTED')
        moved++
      } catch (e) {
        setError(`${c.candidateName}: ${e instanceof Error ? e.message : 'could not move'}`)
      }
    }
    setBusy(false)
    setPicked(new Set())
    setMessage(`${moved} moved to Shortlisted. Next: send them the booking link for a live interview (Workflow → Today’s emails, or Interview slots).`)
    load()
    onChanged()
  }

  const rows = (data?.candidates ?? []).filter((c) => !onlyReviewed || c.status === 'SUBMITTED')
  const stageLabel = (key: string) => stages.find((s) => s.key === key)?.label ?? key
  return (
    <Card className="stack" aria-label="AI interview">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>🎥 AI interview</strong>
        <Button size="sm" variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
      <p className="muted" style={{ margin: 0, fontSize: 14 }}>
        Candidates record answers on camera in their own time, phase by phase (introduction → experience → skills → projects → architecture →
        technical → soft skills → readiness). The interview agent reviews each one and ranks them here. It’s advice: watch the answers of the
        top people, then shortlist.
      </p>
      {canEdit && (
        <div className="row" style={{ gap: 8 }}>
          <Button variant="secondary" disabled={busy} onClick={() => void create()}>
            {busy ? 'Drafting…' : '✨ Create an AI interview for this opening'}
          </Button>
          <span className="muted" style={{ fontSize: 13 }}>
            Drafts the questions from the job description; you check them under Tests, mark it ready and use Send to candidates (30 at a time).
          </span>
        </div>
      )}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {message && (
        <div role="status" className="alert alert-info">
          {message}
        </div>
      )}
      {data && data.sent === 0 && <p className="muted" style={{ margin: 0 }}>No AI interviews sent for this opening yet.</p>}
      {data && data.sent > 0 && (
        <>
          <div className="row" style={{ gap: 12, fontSize: 14 }}>
            <span>
              <strong>{data.sent}</strong> sent
            </span>
            <span>
              <strong>{data.started}</strong> in progress
            </span>
            <span>
              <strong>{data.submitted}</strong> submitted
            </span>
            <span>
              <strong>{data.reviewed}</strong> reviewed
            </span>
            <label className="row" style={{ gap: 4 }}>
              <input type="checkbox" checked={onlyReviewed} onChange={(e) => setOnlyReviewed(e.target.checked)} />
              Only submitted
            </label>
            {canEdit && (
              <Button size="sm" disabled={busy || picked.size === 0} onClick={() => void shortlist()}>
                Shortlist selected ({picked.size})
              </Button>
            )}
          </div>
          <div style={{ overflowX: 'auto' }}>
            <table className="bank-grid" aria-label="AI interview ranking" style={{ fontSize: 13, width: '100%' }}>
              <thead>
                <tr>
                  {canEdit && <th aria-label="Select" />}
                  <th>#</th>
                  <th style={{ textAlign: 'left' }}>Candidate</th>
                  <th>Score</th>
                  {AREAS.map((a) => (
                    <th key={a}>{AREA_SHORT[a]}</th>
                  ))}
                  <th style={{ textAlign: 'left' }}>Notice · salary</th>
                  <th style={{ textAlign: 'left' }}>Summary</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r, i) => (
                  <tr key={r.inviteId}>
                    {canEdit && (
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`Select ${r.candidateName}`}
                          checked={picked.has(r.applicationId)}
                          onChange={(e) =>
                            setPicked((all) => {
                              const next = new Set(all)
                              if (e.target.checked) next.add(r.applicationId)
                              else next.delete(r.applicationId)
                              return next
                            })
                          }
                        />
                      </td>
                    )}
                    <td>{r.status === 'SUBMITTED' ? i + 1 : ''}</td>
                    <td style={{ textAlign: 'left' }}>
                      <button type="button" className="link-button" onClick={() => onOpen(r.applicationId)}>
                        {r.candidateName}
                      </button>
                      <div className="muted">
                        {stageLabel(r.stage)} · {status(r)}
                        {r.videos > 0 && ` · ${r.videos} video${r.videos === 1 ? '' : 's'}`}
                      </div>
                    </td>
                    <td>
                      {r.aiScore != null ? <strong>{r.aiScore}</strong> : <span className="muted">–</span>}
                      {r.evaluation === 'DONE' && !r.usesAi && (
                        <div>
                          <Badge tone="neutral">basic</Badge>
                        </div>
                      )}
                    </td>
                    {AREAS.map((a) => (
                      <td key={a}>
                        <Dots rating={r.areas[a] ?? 0} />
                      </td>
                    ))}
                    <td style={{ textAlign: 'left' }}>
                      {[r.noticePeriod, r.expectedSalary].filter(Boolean).join(' · ') || <span className="muted">–</span>}
                    </td>
                    <td style={{ textAlign: 'left', maxWidth: 320 }}>{r.summary ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <span className="muted" style={{ fontSize: 12 }}>
            Score: worked out by the app from the review’s ratings (technical 30%, design 20%, communication 20%, experience 15%, readiness 15%) and
            the marked answers. “basic” = reviewed by rules because no AI key is set. Click a name to watch the answers.
          </span>
        </>
      )}
    </Card>
  )
}
