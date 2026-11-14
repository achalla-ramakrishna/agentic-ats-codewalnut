import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AREA_SHORT, createAiInterview, jobInterviews, type JobInterviews, type RankedCandidate } from '../api/aiInterviews'
import { moveStage } from '../api/tracker'
import { Dots } from './InterviewReviewCard'
import { Verdicts } from './Verdicts'
import { QuestionSetPreview } from './QuestionSetPreview'
import { VideoSpace } from './VideoSpace'
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
  canReview = false,
  onOpen,
  onChanged,
  onClose,
}: {
  jobId: string
  canEdit: boolean
  /** May give a verdict on finished interviews (AINT-21): shows a Review link. */
  canReview?: boolean
  onOpen: (applicationId: string) => void
  onChanged: () => void
  /** Shows a Close button (not needed when the panel is a tab). */
  onClose?: () => void
}) {
  const navigate = useNavigate()
  const stages = useStages()
  const [data, setData] = useState<JobInterviews | null>(null)
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [onlyReviewed, setOnlyReviewed] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [questionSet, setQuestionSet] = useState<'1' | '2' | '3' | '4' | 'all'>('1')
  const [previewing, setPreviewing] = useState(false)
  const [previewOf, setPreviewOf] = useState(1)

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
      if (questionSet === 'all') {
        // One draft per set; pick which to send from Tests.
        for (const n of [1, 2, 3, 4]) await createAiInterview(jobId, undefined, n)
        window.alert('Sets A, B, C and D were created as drafts under Tests. Check each, Mark ready, and send whichever you like.')
        navigate('/tests')
        return
      }
      const r = await createAiInterview(jobId, undefined, Number(questionSet))
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
        {onClose && (
          <Button size="sm" variant="ghost" onClick={onClose}>
            Close
          </Button>
        )}
      </div>
      <ol className="ai-steps" aria-label="How it works">
        <li>
          <strong>1. Pick a question set</strong>Read it first with Preview questions.
        </li>
        <li>
          <strong>2. Create and check</strong>It opens under Tests; edit anything, then Mark ready.
        </li>
        <li>
          <strong>3. Send</strong>Send to candidates, 30 at a time. They record answers on camera.
        </li>
        <li>
          <strong>4. Shortlist</strong>The AI reviews each one; the ranking appears below.
        </li>
      </ol>
      <VideoSpace jobId={jobId} canEdit={canEdit} />
      {canEdit && (
        <div className="stack" style={{ gap: 8 }}>
          <div className="row" style={{ gap: 8, alignItems: 'flex-end' }}>
            <label className="field" style={{ maxWidth: 200 }}>
              Question set
              <select className="select" value={questionSet} onChange={(e) => setQuestionSet(e.target.value as typeof questionSet)}>
                <option value="1">Set A</option>
                <option value="2">Set B</option>
                <option value="3">Set C</option>
                <option value="4">Set D</option>
                <option value="all">All four sets</option>
              </select>
            </label>
            <Button variant="secondary" onClick={() => setPreviewing(!previewing)}>
              {previewing ? 'Hide questions' : 'Preview questions'}
            </Button>
            <Button disabled={busy} onClick={() => void create()}>
              {busy ? 'Creating…' : questionSet === 'all' ? '✨ Create all four sets' : '✨ Create this interview'}
            </Button>
          </div>
          <span className="muted" style={{ fontSize: 13 }}>
            Senior Java openings have four hand-written sets (A–D): every set has the senior architect’s core questions plus different
            concept and code questions, so you can give different candidates different papers. Other openings get questions drafted from
            the job description.
          </span>
          {previewing && questionSet === 'all' && (
            <div className="row" style={{ gap: 6 }} role="group" aria-label="Preview set">
              {[1, 2, 3, 4].map((n) => (
                <Button key={n} size="sm" variant={previewOf === n ? 'primary' : 'ghost'} onClick={() => setPreviewOf(n)}>
                  Set {'ABCD'[n - 1]}
                </Button>
              ))}
            </div>
          )}
          {previewing && <QuestionSetPreview set={questionSet === 'all' ? previewOf : Number(questionSet)} />}
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
                  <th style={{ textAlign: 'left' }}>Reviewers</th>
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
                    <td style={{ textAlign: 'left' }}>
                      <Verdicts verdicts={r.verdicts ?? []} />
                      {canReview && r.status === 'SUBMITTED' && (
                        <div>
                          <Link to={`/ai-reviews/${r.inviteId}`} style={{ fontSize: 12 }}>
                            {r.verdicts?.length ? 'Open review' : 'Review'}
                          </Link>
                        </div>
                      )}
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
