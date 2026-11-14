import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  DECISION_ICON,
  DECISION_LABEL,
  MARK_LABEL,
  getReview,
  reviewQueue,
  saveVerdict,
  type AnswerMark,
  type Decision,
  type Mark,
  type QueueRow,
  type ReviewPage,
} from '../api/aiReviews'
import { TestAnswers } from '../components/TestsSection'
import { Verdicts, verdictTone as tone } from '../components/Verdicts'
import { Badge, Button, Card, PageHeader } from '../components/ui'
import '../components/tracker.css'

const DECISIONS: Decision[] = ['SELECT', 'HOLD', 'REJECT']
const MARKS: Mark[] = ['GOOD', 'OK', 'WEAK']
type Show = 'todo' | 'done' | 'all'

const when = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '–'

/** Completed AI interviews to watch and decide on (AINT-21): the ones you haven't decided on come first. */
export function AiReviewsPage() {
  const [rows, setRows] = useState<QueueRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [show, setShow] = useState<Show>('todo')
  const [job, setJob] = useState('')
  const [search, setSearch] = useState('')

  useEffect(() => {
    reviewQueue()
      .then((q) => setRows(q.interviews))
      .catch((e: Error) => setError(e.message))
  }, [])

  const jobs = useMemo(() => {
    const m = new Map<string, string>()
    rows?.forEach((r) => m.set(r.jobId, r.jobTitle))
    return [...m.entries()].sort((a, b) => a[1].localeCompare(b[1]))
  }, [rows])
  const inJob = (rows ?? []).filter((r) => !job || r.jobId === job)
  const todo = inJob.filter((r) => !r.mine).length
  const shown = inJob
    .filter((r) => (show === 'todo' ? !r.mine : show === 'done' ? !!r.mine : true))
    .filter((r) => r.candidateName.toLowerCase().includes(search.trim().toLowerCase()))

  return (
    <>
      <PageHeader
        title="AI interview reviews"
        description="Watch completed AI interviews, check the answers and the AI’s review, and say whether the candidate goes to the next round."
      />
      <Card className="stack" style={{ gap: 10 }}>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          <div className="row" style={{ gap: 4 }} role="group" aria-label="Show">
            <Button size="sm" variant={show === 'todo' ? 'primary' : 'ghost'} onClick={() => setShow('todo')}>
              To review ({todo})
            </Button>
            <Button size="sm" variant={show === 'done' ? 'primary' : 'ghost'} onClick={() => setShow('done')}>
              Reviewed by me ({inJob.length - todo})
            </Button>
            <Button size="sm" variant={show === 'all' ? 'primary' : 'ghost'} onClick={() => setShow('all')}>
              All ({inJob.length})
            </Button>
          </div>
          {jobs.length > 1 && (
            <select aria-label="Opening" value={job} onChange={(e) => setJob(e.target.value)}>
              <option value="">All openings</option>
              {jobs.map(([id, title]) => (
                <option key={id} value={id}>
                  {title}
                </option>
              ))}
            </select>
          )}
          <input type="search" placeholder="Search by name" aria-label="Search by name" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        {rows === null && !error && <p className="muted">Loading…</p>}
        {rows && shown.length === 0 && (
          <p className="muted" style={{ margin: 0 }}>
            {show === 'todo' ? 'Nothing waiting for you — every completed AI interview has your verdict.' : 'No AI interviews here.'}
          </p>
        )}
        {shown.length > 0 && (
          <div style={{ overflowX: 'auto' }}>
            <table className="bank-grid" aria-label="AI interviews to review" style={{ fontSize: 14, width: '100%' }}>
              <thead>
                <tr>
                  <th style={{ textAlign: 'left' }}>Candidate</th>
                  <th style={{ textAlign: 'left' }}>Opening</th>
                  <th>Finished</th>
                  <th>AI score</th>
                  <th style={{ textAlign: 'left' }}>Verdicts</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {shown.map((r) => (
                  <tr key={r.inviteId}>
                    <td style={{ textAlign: 'left' }}>
                      <Link to={`/ai-reviews/${r.inviteId}`}>{r.candidateName}</Link>
                    </td>
                    <td style={{ textAlign: 'left' }}>{r.jobTitle}</td>
                    <td>{when(r.submittedAt)}</td>
                    <td>{r.aiScore != null ? <strong>{r.aiScore}</strong> : <span className="muted">{r.evaluation === 'PENDING' ? 'reviewing…' : '–'}</span>}</td>
                    <td style={{ textAlign: 'left' }}>
                      <Verdicts verdicts={r.verdicts} />
                    </td>
                    <td>
                      <Link className="btn btn-sm btn-ghost" to={`/ai-reviews/${r.inviteId}`}>
                        {r.mine ? 'Change' : 'Review'}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}

/** One AI interview: watch the answers, mark them, and decide (AINT-21). */
export function AiReviewPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [page, setPage] = useState<ReviewPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [decision, setDecision] = useState<Decision | null>(null)
  const [feedback, setFeedback] = useState('')
  const [marks, setMarks] = useState<Record<number, AnswerMark>>({})
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState<string | null>(null)

  useEffect(() => {
    setPage(null)
    setSaved(null)
    setError(null)
    getReview(id)
      .then((p) => {
        setPage(p)
        const mine = p.verdicts.find((v) => v.mine)
        setDecision(mine?.decision ?? null)
        setFeedback(mine?.feedback ?? '')
        setMarks(Object.fromEntries((mine?.marks ?? []).map((m) => [m.position, m])))
      })
      .catch((e: Error) => setError(e.message))
  }, [id])

  const setMark = (position: number, change: Partial<AnswerMark>) =>
    setMarks((all) => ({ ...all, [position]: { ...(all[position] ?? { position, mark: null, note: null }), ...change } }))

  async function save(e: FormEvent, next: boolean) {
    e.preventDefault()
    if (!decision) return
    setBusy(true)
    setError(null)
    try {
      const p = await saveVerdict(id, { decision, feedback, marks: Object.values(marks) })
      setPage(p)
      if (next) {
        const q = await reviewQueue()
        const nextOne = q.interviews.find((r) => !r.mine && r.inviteId !== id)
        if (nextOne) {
          navigate(`/ai-reviews/${nextOne.inviteId}`)
          window.scrollTo(0, 0)
          return
        }
        setSaved('Saved. That was the last one waiting for you.')
      } else {
        setSaved('Saved. The recruiter sees your verdict in the opening’s AI interview ranking and the candidate’s team notes.')
      }
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (error && !page) {
    return (
      <>
        <PageHeader title="AI interview review" />
        <Card>
          <div role="alert" className="alert alert-error">
            {error}
          </div>
          <Link to="/ai-reviews">Back to AI interview reviews</Link>
        </Card>
      </>
    )
  }
  if (!page) return <p className="muted">Loading…</p>

  const d = page.detail
  const others = page.verdicts.filter((v) => !v.mine)
  const needsReason = decision !== null && decision !== 'SELECT'
  return (
    <>
      <PageHeader title={page.candidateName} description={`AI interview · ${page.jobTitle} · finished ${when(d.invite.submittedAt)}`} />
      <div className="stack" style={{ gap: 12 }}>
        <Link to="/ai-reviews">← All AI interview reviews</Link>
        {others.length > 0 && (
          <Card className="stack" style={{ gap: 6 }} aria-label="Other reviewers">
            <strong>Other reviewers</strong>
            {others.map((v) => (
              <div key={v.reviewerEmail}>
                <Badge tone={tone(v.decision)}>
                  {DECISION_ICON[v.decision]} {v.decisionLabel}
                </Badge>{' '}
                <strong>{v.reviewerName}</strong> <span className="muted">· {when(v.updatedAt)}</span>
                {v.feedback && <div style={{ whiteSpace: 'pre-wrap', fontSize: 14 }}>{v.feedback}</div>}
              </div>
            ))}
          </Card>
        )}
        <Card>
          <p className="muted" style={{ margin: '0 0 6px', fontSize: 13 }}>
            Watch each answer and compare it with “Look for”. Marking answers is optional; it helps the next reviewer and the recruiter.
          </p>
          <TestAnswers
            inviteId={page.inviteId}
            answers={d.answers}
            sections={d.sections}
            activity={d.activity}
            interview={d.review}
            answerExtra={(a) => (
              <div className="row" style={{ gap: 4, flexWrap: 'wrap' }} role="group" aria-label={`Your mark for question ${a.position}`}>
                {MARKS.map((m) => (
                  <Button
                    key={m}
                    size="sm"
                    variant={marks[a.position]?.mark === m ? 'primary' : 'ghost'}
                    aria-pressed={marks[a.position]?.mark === m}
                    onClick={() => setMark(a.position, { mark: marks[a.position]?.mark === m ? null : m })}
                  >
                    {MARK_LABEL[m]}
                  </Button>
                ))}
                <input
                  aria-label={`Note on question ${a.position}`}
                  placeholder="Note (optional)"
                  value={marks[a.position]?.note ?? ''}
                  onChange={(e) => setMark(a.position, { note: e.target.value })}
                  style={{ flex: '1 1 200px', fontSize: 13 }}
                />
              </div>
            )}
          />
        </Card>
        <Card className="stack" style={{ gap: 10 }}>
          <form className="stack" style={{ gap: 10 }} onSubmit={(e) => void save(e, false)} aria-label="Your verdict">
            <strong>Your verdict</strong>
            <div className="row" style={{ gap: 6, flexWrap: 'wrap' }} role="radiogroup" aria-label="Decision">
              {DECISIONS.map((x) => (
                <Button
                  key={x}
                  role="radio"
                  aria-checked={decision === x}
                  variant={decision === x ? 'primary' : 'ghost'}
                  onClick={() => setDecision(x)}
                >
                  {DECISION_ICON[x]} {DECISION_LABEL[x]}
                </Button>
              ))}
            </div>
            <label className="stack" style={{ gap: 4 }}>
              <span>
                Feedback{' '}
                <span className="muted">{needsReason ? '(required — why?)' : '(optional: strengths, what to probe in the next round)'}</span>
              </span>
              <textarea
                value={feedback}
                onChange={(e) => setFeedback(e.target.value)}
                maxLength={4000}
                style={{ fontFamily: 'inherit', fontSize: 14, minHeight: 100 }}
              />
            </label>
            {error && (
              <div role="alert" className="alert alert-error">
                {error}
              </div>
            )}
            {saved && (
              <div role="status" className="alert alert-info">
                {saved}
              </div>
            )}
            <div className="row" style={{ gap: 8 }}>
              <Button type="submit" disabled={busy || !decision || (needsReason && !feedback.trim())}>
                Save verdict
              </Button>
              <Button
                variant="ghost"
                disabled={busy || !decision || (needsReason && !feedback.trim())}
                onClick={(e) => void save(e as unknown as FormEvent, true)}
              >
                Save and review next →
              </Button>
            </div>
            <span className="muted" style={{ fontSize: 12 }}>
              Your verdict is shared with the hiring team, never with the candidate. Moving the candidate is still the recruiter’s call.
            </span>
          </form>
        </Card>
      </div>
    </>
  )
}
