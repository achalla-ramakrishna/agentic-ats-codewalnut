import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  CATEGORY_LABEL,
  cancelTest,
  getTestResult,
  listApplicationTests,
  listAssessments,
  regradeTest,
  remindTest,
  LANGUAGE_LABEL,
  sendTest,
  testLink,
  testStatusLabel,
  type Activity,
  type AnswerReview,
  type SectionScore,
  type AssessmentSummary,
  type InviteView,
  type SendResult,
} from '../api/assessments'
import { getWhatsAppStatus } from '../api/messages'
import { Figure } from './Figure'
import { RunResults } from './RunResults'
import { newResultsChanged } from './useNewResults'
import { Link } from 'react-router-dom'
import { Badge, Button } from './ui'

/** WhatsApp without the Business API opens a window the browser must allow: open it before the request. */
function prepareWhatsApp(enabled: boolean, apiEnabled: boolean): Window | null {
  return enabled && !apiEnabled ? window.open('about:blank', '_blank') : null
}

function finishWhatsApp(result: SendResult, win: Window | null) {
  const link = result.message.whatsappLink
  if (link) {
    if (win) win.location.href = link
    else window.open(link, '_blank')
  } else {
    win?.close()
  }
}

/** Browser signals while the test was taken: a prompt to look closer, never a verdict. */
function ActivityNote({ activity }: { activity: Activity | null }) {
  if (!activity || (activity.tabSwitches === 0 && activity.pastes === 0 && activity.runs === 0)) return null
  const parts = [
    activity.tabSwitches > 0 && `left the test tab ${activity.tabSwitches}×`,
    activity.pastes > 0 && `pasted ${activity.pastes}× (${activity.pastedChars} characters)`,
    activity.runs > 0 && `ran code on samples ${activity.runs}×`,
  ].filter(Boolean)
  return (
    <p className="muted" style={{ fontSize: 13, margin: '6px 0 0' }}>
      While taking the test: {parts.join(' · ')}. These are signals to ask about, not proof of anything.
    </p>
  )
}

function CodeAnswer({ a }: { a: AnswerReview }) {
  const r = a.codeResult
  if (!r || !r.source) return <div>✗ No code submitted <span className="muted">(0/{a.points})</span></div>
  return (
    <div className="stack" style={{ gap: 4 }}>
      <div>
        {r.passed === r.total && r.total > 0 ? '✓' : r.passed > 0 ? '◐' : '✗'} {r.language ? LANGUAGE_LABEL[r.language] : 'Code'}:{' '}
        <strong>
          {r.passed} of {r.total} tests passed
        </strong>{' '}
        <span className="muted">
          ({a.earned}/{a.points})
        </span>
      </div>
      <details>
        <summary>Code</summary>
        <pre className="test-code">{r.source}</pre>
      </details>
      {r.total > 0 && (
        <details>
          <summary>Test cases</summary>
          <RunResults cases={r.cases} compileOutput={r.compileOutput} passed={r.passed} total={r.total} title="Graded on" />
        </details>
      )}
    </div>
  )
}

function Review({ answers, sections, activity }: { answers: AnswerReview[]; sections: SectionScore[]; activity: Activity | null }) {
  return (
    <>
    <ActivityNote activity={activity} />
    {sections.length > 0 && (
      <table className="bank-grid" aria-label="Score by section" style={{ fontSize: 13, marginTop: 6 }}>
        <tbody>
          {sections.map((s) => (
            <tr key={s.section}>
              <td style={{ textAlign: 'left' }}>{s.label}</td>
              <td>
                <strong>{s.score}</strong> / {s.max}
              </td>
              <td className="muted">{s.max ? Math.round((s.score * 100) / s.max) : 0}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    )}
    <ol style={{ margin: '6px 0 0', paddingLeft: 20, fontSize: 13 }}>
      {answers.map((a) => {
        const given =
          a.kind === 'SHORT_ANSWER'
            ? a.given[0] || '—'
            : a.given.map((g) => a.options[Number(g)] ?? g).join(', ') || '—'
        const right = a.kind === 'SHORT_ANSWER' ? a.acceptedAnswers.join(' / ') : a.correct.map((c) => a.options[c]).join(', ')
        if (a.kind === 'CODING') {
          return (
            <li key={a.position} style={{ marginBottom: 6 }}>
              <span style={{ whiteSpace: 'pre-wrap' }}>{a.prompt.split('\n')[0]}</span>
              <CodeAnswer a={a} />
            </li>
          )
        }
        return (
          <li key={a.position} style={{ marginBottom: 4 }}>
            <span>{a.prompt}</span>
            {a.figure && <Figure figure={a.figure} small />}
            {a.code && <pre className="test-code">{a.code}</pre>}
            <div>
              {a.earned > 0 ? '✓' : '✗'} Answered: <strong>{given}</strong>
              {a.earned === 0 && <span className="muted"> · right answer: {right}</span>}{' '}
              <span className="muted">({a.earned}/{a.points})</span>
            </div>
          </li>
        )
      })}
    </ol>
    </>
  )
}

/** Tests sent to this candidate for this opening, their scores, and sending a new one (ADR-0011). */
export function TestsSection({
  applicationId,
  canSend,
  hasEmail,
  hasPhone,
  startOpen = false,
  onSent,
}: {
  applicationId: string
  canSend: boolean
  hasEmail: boolean
  hasPhone: boolean
  /** Open the send form straight away (the drawer's Send test button). */
  startOpen?: boolean
  onSent?: () => void
}) {
  const [invites, setInvites] = useState<InviteView[] | null>(null)
  const [tests, setTests] = useState<AssessmentSummary[]>([])
  const [sending, setSending] = useState(false)
  const [assessmentId, setAssessmentId] = useState('')
  const [dueDays, setDueDays] = useState(3)
  const [email, setEmail] = useState(true)
  const [whatsApp, setWhatsApp] = useState(false)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [waApi, setWaApi] = useState(false)
  const [review, setReview] = useState<Record<string, { answers: AnswerReview[]; sections: SectionScore[]; activity: Activity | null }>>({})

  const load = useCallback(() => {
    listApplicationTests(applicationId).then(setInvites).catch(() => setInvites([]))
  }, [applicationId])
  useEffect(load, [load])
  useEffect(() => {
    if (startOpen && canSend) openSend()
    // Only on mount: the drawer re-mounts this section for each Send test click.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const [copied, setCopied] = useState<string | null>(null)

  /** The candidate's own test link; they still sign in with Google using their email to open it. */
  async function copyLink(i: InviteView) {
    const link = testLink(i.id)
    try {
      await navigator.clipboard.writeText(link)
      setCopied(i.id)
      window.setTimeout(() => setCopied((c) => (c === i.id ? null : c)), 2000)
    } catch {
      window.prompt('Copy this link (the candidate signs in with Google using their email):', link)
    }
  }

  function openSend() {
    setSending(true)
    setError(null)
    listAssessments()
      .then((all) => {
        const ready = all.filter((t) => t.status === 'READY')
        setTests(ready)
        if (ready.length && !assessmentId) setAssessmentId(ready[0].id)
      })
      .catch(() => setTests([]))
    getWhatsAppStatus()
      .then((s) => setWaApi(s.apiEnabled))
      .catch(() => setWaApi(false))
  }

  async function onSend(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    const win = prepareWhatsApp(whatsApp, waApi)
    try {
      const result = await sendTest(applicationId, { assessmentId, dueDays, sendEmail: email, sendWhatsApp: whatsApp, note: note.trim() || undefined })
      finishWhatsApp(result, win)
      setMessage(`Sent “${result.invite.title}”. The candidate has the link in their messages${email ? ' and email' : ''}.`)
      setSending(false)
      setNote('')
      load()
      onSent?.()
    } catch (e) {
      win?.close()
      setError(e instanceof Error ? e.message : 'Could not send the test')
    } finally {
      setBusy(false)
    }
  }

  async function onRemind(invite: InviteView) {
    setError(null)
    const sendWa = hasPhone && window.confirm('Also remind them on WhatsApp? (OK = email + WhatsApp, Cancel = email only)')
    const win = prepareWhatsApp(sendWa, waApi)
    try {
      const result = await remindTest(invite.id, { sendEmail: hasEmail, sendWhatsApp: sendWa })
      finishWhatsApp(result, win)
      setMessage(`Reminder sent for “${invite.title}”.`)
      load()
    } catch (e) {
      win?.close()
      setError(e instanceof Error ? e.message : 'Could not send the reminder')
    }
  }

  async function onCancel(invite: InviteView) {
    if (!window.confirm(`Withdraw “${invite.title}”? The candidate will no longer be able to take it.`)) return
    try {
      await cancelTest(invite.id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not withdraw the test')
    }
  }

  async function onRegrade(invite: InviteView) {
    setError(null)
    try {
      const updated = await regradeTest(invite.id)
      setInvites((all) => all?.map((i) => (i.id === invite.id ? updated : i)) ?? all)
      setMessage('Grading the code again.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not grade again')
    }
  }

  async function toggleReview(invite: InviteView) {
    if (review[invite.id]) {
      setReview((all) => {
        const rest = { ...all }
        delete rest[invite.id]
        return rest
      })
      return
    }
    const r = await getTestResult(invite.id)
    setReview((all) => ({ ...all, [invite.id]: { answers: r.answers, sections: r.sections, activity: r.activity } }))
    if (invite.newResult) {
      // Opening the answers marks the result as seen on the server.
      setInvites((all) => all?.map((i) => (i.id === invite.id ? { ...i, newResult: false } : i)) ?? all)
      newResultsChanged()
    }
  }

  if (!invites) return null
  return (
    <section className="stack" aria-label="Tests" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h3 style={{ margin: 0 }}>Tests</h3>
        {canSend && !sending && (
          <Button size="sm" variant="secondary" onClick={openSend}>
            Send test
          </Button>
        )}
      </div>
      {message && <div className="alert alert-info">{message}</div>}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {sending && (
        <form className="stack card" onSubmit={onSend} aria-label="Send a test" style={{ gap: 8 }}>
          {tests.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              No tests are ready yet. <Link to="/tests">Build one from the question bank</Link> (aptitude, Java, Python and more), mark it
              ready, then come back here.
            </p>
          ) : (
            <>
              <label className="field">
                Test
                <select className="select" value={assessmentId} onChange={(e) => setAssessmentId(e.target.value)}>
                  {tests.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.title} · {CATEGORY_LABEL[t.category]} · {t.questionCount} questions · {t.durationMinutes} min
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                Take it within
                <select className="select" value={dueDays} onChange={(e) => setDueDays(Number(e.target.value))}>
                  {[1, 2, 3, 5, 7].map((d) => (
                    <option key={d} value={d}>
                      {d} day{d === 1 ? '' : 's'}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                Note to add (optional)
                <textarea className="input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
              </label>
              <div className="row" style={{ gap: 16 }}>
                <label className="row" style={{ gap: 6 }}>
                  <input type="checkbox" checked={email} disabled={!hasEmail} onChange={(e) => setEmail(e.target.checked)} /> Email
                </label>
                <label className="row" style={{ gap: 6 }}>
                  <input type="checkbox" checked={whatsApp} disabled={!hasPhone} onChange={(e) => setWhatsApp(e.target.checked)} /> WhatsApp
                </label>
              </div>
              {!hasEmail && <p className="muted" style={{ margin: 0, fontSize: 13 }}>Add the candidate’s email first: they sign in with it to take the test.</p>}
            </>
          )}
          <div className="row">
            <Button type="submit" disabled={busy || !assessmentId || !hasEmail}>
              {busy ? 'Sending…' : 'Send test'}
            </Button>
            <Button variant="ghost" onClick={() => setSending(false)}>
              Cancel
            </Button>
          </div>
        </form>
      )}
      {invites.length === 0 && !sending && <p className="muted" style={{ margin: 0 }}>No tests sent yet.</p>}
      {invites.length > 0 && (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }} className="stack">
          {invites.map((i) => (
            <li key={i.id} className="stack" style={{ gap: 2 }}>
              <div className="row" style={{ gap: 8, justifyContent: 'space-between' }}>
                <span>
                  <strong>{i.title}</strong>{' '}
                  {i.newResult && <Badge tone="primary">New result</Badge>}{' '}
                  {i.status === 'SUBMITTED' ? (
                    <Badge tone={i.passed ? 'primary' : 'neutral'}>{testStatusLabel(i)}</Badge>
                  ) : (
                    <span className="muted">{testStatusLabel(i)}</span>
                  )}
                </span>
                <span className="row" style={{ gap: 6 }}>
                  {canSend && i.grading === 'FAILED' && (
                    <Button size="sm" variant="secondary" onClick={() => void onRegrade(i)}>
                      Grade again
                    </Button>
                  )}
                  {i.status === 'SUBMITTED' && (
                    <Button size="sm" variant="ghost" onClick={() => void toggleReview(i)}>
                      {review[i.id] ? 'Hide answers' : 'Answers'}
                    </Button>
                  )}
                  {canSend && (i.status === 'SENT' || i.status === 'EXPIRED') && (
                    <Button size="sm" variant={i.needsNudge ? 'primary' : 'ghost'} onClick={() => void onRemind(i)}>
                      {i.status === 'EXPIRED' ? 'Remind (+2 days)' : 'Remind'}
                    </Button>
                  )}
                  {(i.status === 'SENT' || i.status === 'STARTED') && (
                    <Button size="sm" variant="ghost" onClick={() => void copyLink(i)}>
                      {copied === i.id ? 'Copied' : 'Copy link'}
                    </Button>
                  )}
                  {canSend && (i.status === 'SENT' || i.status === 'STARTED' || i.status === 'EXPIRED') && (
                    <Button size="sm" variant="ghost" onClick={() => void onCancel(i)}>
                      Withdraw
                    </Button>
                  )}
                </span>
              </div>
              <span className="muted" style={{ fontSize: 13 }}>
                Sent {new Date(i.sentAt).toLocaleDateString()} · due {new Date(i.dueAt).toLocaleDateString()}
                {i.reminderCount > 0 && ` · reminded ${i.reminderCount}×`}
                {i.status === 'SUBMITTED' && i.grading === 'PENDING' && ' · grading the code (usually under a minute)'}
                {i.status === 'SUBMITTED' && i.grading === 'FAILED' && ' · the code couldn’t be graded — the code runner was unreachable'}
                {i.status === 'SUBMITTED' && i.percent != null && ` · ${i.score}/${i.maxScore} points, pass mark ${i.passPercent}%`}
                {i.needsNudge && i.status === 'SENT' && ' · not started after 2 days — send a nudge?'}
              </span>
              {review[i.id] && <Review answers={review[i.id].answers} sections={review[i.id].sections} activity={review[i.id].activity} />}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
