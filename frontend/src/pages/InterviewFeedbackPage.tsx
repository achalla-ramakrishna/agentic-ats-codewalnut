import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ATTENDANCE_LABEL,
  RATING_LABEL,
  RECOMMENDATION_LABEL,
  formatWhen,
  getFeedback,
  feedbackOpen,
  submitFeedback,
  type Attendance,
  type Feedback,
  type FeedbackPage,
  type Rating,
  type Recommendation,
} from '../api/interviews'
import { useMe } from '../auth/AuthContext'
import { Badge, Button, Card, PageHeader } from '../components/ui'
import '../components/tracker.css'

const ATTENDANCES: Attendance[] = ['HELD', 'ENDED_EARLY', 'CANDIDATE_NO_SHOW', 'INTERVIEWER_COULD_NOT_JOIN']
const RECOMMENDATIONS: Recommendation[] = ['STRONG_NO', 'NO', 'YES', 'STRONG_YES']
const SCORES = [1, 2, 3, 4]
const TEXTAREA_STYLE = { fontFamily: 'inherit', fontSize: 14, minHeight: 90 }

/** Scores and notes count only when the interview took place (fully or partly). */
const tookPlace = (a: Attendance) => a === 'HELD' || a === 'ENDED_EARLY'

function recommendationTone(r: Recommendation | null) {
  return r === 'YES' || r === 'STRONG_YES' ? 'primary' : r ? 'danger' : 'neutral'
}

function FeedbackForm({ page, onSaved }: { page: FeedbackPage; onSaved: (p: FeedbackPage) => void }) {
  const mine = page.mine
  const [attendance, setAttendance] = useState<Attendance>(mine?.attendance ?? 'HELD')
  const [ratings, setRatings] = useState<Rating[]>(() =>
    page.competencies.map(
      (c) => mine?.ratings.find((r) => r.competency === c.name) ?? { competency: c.name, rating: null, note: null },
    ),
  )
  const [strengths, setStrengths] = useState(mine?.strengths ?? '')
  const [concerns, setConcerns] = useState(mine?.concerns ?? '')
  const [questionsAsked, setQuestionsAsked] = useState(mine?.questionsAsked ?? '')
  const [recommendation, setRecommendation] = useState<Recommendation | null>(mine?.recommendation ?? null)
  const [notes, setNotes] = useState(mine?.notes ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submitted = mine !== null && !mine.draft
  const [draftSavedAt, setDraftSavedAt] = useState<string | null>(mine?.draft ? mine.updatedAt : null)
  const scored = tookPlace(attendance)
  const firstRender = useRef(true)

  function input(draft: boolean) {
    return {
      attendance,
      ratings: scored ? ratings : [],
      strengths,
      concerns,
      questionsAsked,
      recommendation: scored ? recommendation : null,
      notes,
      draft,
    }
  }

  async function saveDraft() {
    try {
      const p = await submitFeedback(page.interview.id, input(true))
      setDraftSavedAt(p.mine?.updatedAt ?? new Date().toISOString())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save your draft')
    }
  }

  // Until it's submitted, save a private draft a couple of seconds after each change, so notes
  // taken during the interview are never lost.
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    if (submitted) return
    const t = window.setTimeout(() => void saveDraft(), 2000)
    return () => window.clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attendance, ratings, strengths, concerns, questionsAsked, recommendation, notes])

  function setRating(competency: string, patch: Partial<Rating>) {
    setRatings((rs) => rs.map((r) => (r.competency === competency ? { ...r, ...patch } : r)))
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (scored && !recommendation) {
      setError('Choose an overall recommendation.')
      return
    }
    if (scored && ratings.every((r) => r.rating == null)) {
      setError('Rate at least one area.')
      return
    }
    setBusy(true)
    try {
      onSaved(await submitFeedback(page.interview.id, input(false)))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save your feedback')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stack" aria-label="Interview feedback" onSubmit={onSubmit} style={{ gap: 16 }}>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <fieldset className="stack" style={{ gap: 8, border: 0, padding: 0, margin: 0 }}>
        <legend style={{ fontWeight: 600, marginBottom: 8 }}>How did it go?</legend>
        <div className="row" style={{ flexWrap: 'wrap', gap: 8 }}>
          {ATTENDANCES.map((a) => (
            <button
              key={a}
              type="button"
              aria-pressed={attendance === a}
              className={`chip${attendance === a ? ' chip-on' : ''}`}
              onClick={() => setAttendance(a)}
            >
              {ATTENDANCE_LABEL[a]}
            </button>
          ))}
        </div>
      </fieldset>

      {scored && (
        <>
          <div className="stack" style={{ gap: 4 }}>
            <strong>Rate each area</strong>
            <span className="muted" style={{ fontSize: 13 }}>
              1 Weak · 2 Below the bar · 3 Meets the bar · 4 Strong. Leave an area blank if you didn't cover it.
            </span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <tbody>
                {page.competencies.map((c) => {
                  const r = ratings.find((x) => x.competency === c.name)
                  return (
                    <tr key={c.name}>
                      <td style={{ width: '38%' }}>
                        <strong>{c.name}</strong>
                        <div className="muted" style={{ fontSize: 12 }}>
                          {c.guidance}
                        </div>
                      </td>
                      <td>
                        <div className="row" role="group" aria-label={c.name} style={{ gap: 6, flexWrap: 'wrap' }}>
                          {SCORES.map((s) => (
                            <button
                              key={s}
                              type="button"
                              title={RATING_LABEL[s]}
                              aria-label={`${c.name}: ${s} ${RATING_LABEL[s]}`}
                              aria-pressed={r?.rating === s}
                              className={`chip${r?.rating === s ? ' chip-on' : ''}`}
                              onClick={() => setRating(c.name, { rating: r?.rating === s ? null : s })}
                            >
                              {s}
                            </button>
                          ))}
                        </div>
                        <input
                          className="input"
                          style={{ marginTop: 6 }}
                          aria-label={`${c.name} note`}
                          placeholder="Evidence (optional)"
                          maxLength={2000}
                          value={r?.note ?? ''}
                          onChange={(e) => setRating(c.name, { note: e.target.value })}
                        />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="grid-2">
            <label className="field">
              Strengths
              <textarea className="textarea" style={TEXTAREA_STYLE} maxLength={5000} value={strengths} onChange={(e) => setStrengths(e.target.value)} />
            </label>
            <label className="field">
              Concerns
              <textarea className="textarea" style={TEXTAREA_STYLE} maxLength={5000} value={concerns} onChange={(e) => setConcerns(e.target.value)} />
            </label>
          </div>
          <label className="field">
            Questions you asked (so the next round doesn't repeat them)
            <textarea className="textarea" style={TEXTAREA_STYLE} maxLength={5000} value={questionsAsked} onChange={(e) => setQuestionsAsked(e.target.value)} />
          </label>
          <fieldset className="stack" style={{ gap: 8, border: 0, padding: 0, margin: 0 }}>
            <legend style={{ fontWeight: 600, marginBottom: 8 }}>Overall recommendation</legend>
            <div className="row" style={{ flexWrap: 'wrap', gap: 8 }}>
              {RECOMMENDATIONS.map((rec) => (
                <button
                  key={rec}
                  type="button"
                  aria-pressed={recommendation === rec}
                  className={`chip${recommendation === rec ? ' chip-on' : ''}`}
                  onClick={() => setRecommendation(rec)}
                >
                  {RECOMMENDATION_LABEL[rec]}
                </button>
              ))}
            </div>
          </fieldset>
        </>
      )}

      <label className="field">
        {scored ? 'Anything else (audio, setup, logistics)' : 'What happened'}
        <textarea className="textarea" style={TEXTAREA_STYLE} maxLength={5000} value={notes} onChange={(e) => setNotes(e.target.value)} />
      </label>
      <span className="muted" style={{ fontSize: 12 }}>
        Only CodeWalnut staff see feedback. The candidate and client contacts never do. Other panel members see yours once
        they've given their own.
      </span>
      <div className="row" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving…' : submitted ? 'Update feedback' : 'Submit feedback'}
        </Button>
        {!submitted && (
          <Button variant="secondary" onClick={() => void saveDraft()} disabled={busy}>
            Save draft
          </Button>
        )}
        {!submitted && (
          <span className="muted" role="status" style={{ fontSize: 13 }}>
            {draftSavedAt
              ? `Draft saved at ${new Date(draftSavedAt).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })} · only you can see it`
              : 'Your notes save automatically as a private draft while you type.'}
          </span>
        )}
      </div>
    </form>
  )
}

function FeedbackCard({ feedback, title }: { feedback: Feedback; title: string }) {
  const scored = feedback.ratings.filter((r) => r.rating != null)
  return (
    <Card>
      <div className="stack" style={{ gap: 8 }}>
        <div className="row" style={{ justifyContent: 'space-between', flexWrap: 'wrap' }}>
          <strong>{title}</strong>
          <div className="row" style={{ gap: 6 }}>
            {feedback.attendance !== 'HELD' && <Badge>{ATTENDANCE_LABEL[feedback.attendance]}</Badge>}
            {feedback.recommendation && (
              <Badge tone={recommendationTone(feedback.recommendation)}>{RECOMMENDATION_LABEL[feedback.recommendation]}</Badge>
            )}
            {feedback.averageRating != null && <Badge>Average {feedback.averageRating} / 4</Badge>}
          </div>
        </div>
        {scored.length > 0 && (
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {scored.map((r) => (
              <li key={r.competency}>
                {r.competency}: <strong>{r.rating}</strong> {RATING_LABEL[r.rating ?? 0]}
                {r.note ? <span className="muted"> · {r.note}</span> : null}
              </li>
            ))}
          </ul>
        )}
        {feedback.strengths && (
          <div>
            <strong>Strengths:</strong> {feedback.strengths}
          </div>
        )}
        {feedback.concerns && (
          <div>
            <strong>Concerns:</strong> {feedback.concerns}
          </div>
        )}
        {feedback.questionsAsked && (
          <div>
            <strong>Questions asked:</strong> {feedback.questionsAsked}
          </div>
        )}
        {feedback.notes && (
          <div>
            <strong>Notes:</strong> {feedback.notes}
          </div>
        )}
        <span className="muted" style={{ fontSize: 12 }}>
          {new Date(feedback.updatedAt).toLocaleString('en-IN')}
        </span>
      </div>
    </Card>
  )
}

/** Feedback for one interview (docs/features/interviews-and-scorecards.md, INT-24…INT-27). Staff only. */
export function InterviewFeedbackPage() {
  const { id = '' } = useParams()
  const me = useMe()
  const canOpenJobs = me.navigation.some((n) => n.key === 'jobs')
  const [page, setPage] = useState<FeedbackPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    getFeedback(id)
      .then(setPage)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the interview'))
  }, [id])

  if (error) {
    return (
      <div role="alert" className="alert alert-error">
        {error}
      </div>
    )
  }
  if (!page) return <p className="muted">Loading…</p>
  const i = page.interview
  const now = Date.now()
  const open = feedbackOpen(i)
  const inProgress = i.status === 'SCHEDULED' && new Date(i.startAt).getTime() <= now && now < new Date(i.endAt).getTime()

  return (
    <div className="stack">
      <PageHeader
        title={`Feedback: ${i.candidateName}`}
        description={`${i.jobTitle} · ${formatWhen(i)}`}
        actions={
          <div className="row">
            <Link className="btn btn-secondary" to={`/interview-kits/${i.jobId}`}>
              Interview kit
            </Link>
            <Link className="btn btn-secondary" to="/interview-questions">
              Interview questions
            </Link>
            {canOpenJobs && (
              <Link className="btn btn-secondary" to={`/jobs/${i.jobId}?candidate=${i.applicationId}`}>
                Open candidate
              </Link>
            )}
          </div>
        }
      />
      {saved && <div className="alert alert-info">Thanks, your feedback is saved.</div>}
      {i.status === 'CANCELLED' && <div className="alert alert-info">This interview was cancelled.</div>}
      {open && new Date(i.startAt).getTime() - now > 30 * 60 * 1000 && (
        <div className="alert alert-info">
          <strong>This interview is scheduled for {formatWhen(i)}.</strong> If it already happened, fill in the form anyway. Then
          cancel or reschedule the interview from the candidate&apos;s panel, so they don&apos;t expect another call at the
          scheduled time.
        </div>
      )}
      {open && (inProgress || (new Date(i.startAt).getTime() > now && new Date(i.startAt).getTime() - now <= 30 * 60 * 1000)) && (
        <div className="alert alert-info">
          <strong>{inProgress ? 'Interview in progress.' : 'Starting soon.'}</strong> Rate things as you notice them, such as
          communication, and add notes. Your form saves itself as a private draft; submit it when the interview ends.{' '}
          {i.meetLink && (
            <a href={i.meetLink} target="_blank" rel="noreferrer">
              Join Google Meet
            </a>
          )}{' '}
          · <Link to={`/interview-kits/${i.jobId}`}>Interview kit</Link>
        </div>
      )}
      {page.canSubmit && open && (
        <Card>
          <FeedbackForm
            key={page.mine && !page.mine.draft ? page.mine.updatedAt : 'form'}
            page={page}
            onSaved={(p) => {
              setPage(p)
              setSaved(true)
            }}
          />
        </Card>
      )}
      {!page.canSubmit && page.mine && <FeedbackCard feedback={page.mine} title="Your feedback" />}
      <h3 style={{ margin: '8px 0 0' }}>{page.onPanel ? "The rest of the panel's feedback" : 'Panel feedback'}</h3>
      {page.hiddenCount > 0 && (
        <p className="muted" style={{ margin: 0 }}>
          {page.hiddenCount} other {page.hiddenCount === 1 ? 'person has' : 'people have'} given feedback. You'll see it after you
          submit yours, so everyone's view stays independent.
        </p>
      )}
      {page.hiddenCount === 0 && page.others.length === 0 && (
        <p className="muted" style={{ margin: 0 }}>
          No feedback from others yet.
        </p>
      )}
      {page.others.map((f) => (
        <FeedbackCard key={f.authorEmail} feedback={f} title={f.authorName ? `${f.authorName} (${f.authorEmail})` : f.authorEmail} />
      ))}
    </div>
  )
}
