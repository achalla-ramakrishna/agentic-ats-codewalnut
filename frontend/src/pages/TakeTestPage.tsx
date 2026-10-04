import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  CATEGORY_LABEL,
  resumeTest,
  saveAnswers,
  startTest,
  submitTest,
  listMyTests,
  reportActivity,
  type MyTest,
  type TakeTest,
} from '../api/assessments'
import { CodingQuestion } from '../components/CodingQuestion'
import { Figure } from '../components/Figure'
import { Button, Card } from '../components/ui'
import { sectionTitle } from '../api/questionBank'
import '../components/tracker.css'
import './CandidateHomePage.css'

function clock(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

/** A candidate taking a test: start screen, then the questions against a timer, saved as they go. */
export function TakeTestPage() {
  const { id = '' } = useParams()
  const [intro, setIntro] = useState<MyTest | null>(null)
  const [taking, setTaking] = useState<TakeTest | null>(null)
  const [answers, setAnswers] = useState<Record<string, string[]>>({})
  const [left, setLeft] = useState(0)
  const [done, setDone] = useState<MyTest | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState<'idle' | 'saving' | 'saved' | 'offline'>('idle')
  const dirty = useRef<Record<string, string[]>>({})
  const submitting = useRef(false)
  // Advisory signals for staff (ADR-0016): totals only, sent with the regular save.
  const activity = useRef({ tabSwitches: 0, pastes: 0, pastedChars: 0 })
  const reported = useRef('')

  useEffect(() => {
    listMyTests()
      .then((tests) => {
        const t = tests.find((x) => x.id === id)
        if (!t) {
          setError('Test not found. Make sure you signed in with the email address the test was sent to.')
          return
        }
        if (t.status === 'STARTED') {
          resumeTest(id).then(begin).catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not open the test'))
        } else if (t.status === 'SUBMITTED') {
          setDone(t)
        } else {
          setIntro(t)
        }
      })
      .catch(() => setError('Could not load your tests'))
  }, [id])

  function begin(t: TakeTest) {
    setTaking(t)
    setAnswers(t.answers)
    setLeft(t.secondsLeft)
  }

  const flush = useCallback(async () => {
    const signals = JSON.stringify(activity.current)
    if (signals !== reported.current) {
      reported.current = signals
      reportActivity(id, activity.current).catch(() => (reported.current = ''))
    }
    const pending = dirty.current
    if (!Object.keys(pending).length) return
    dirty.current = {}
    setSaving('saving')
    try {
      await saveAnswers(id, pending)
      setSaving('saved')
    } catch {
      dirty.current = { ...pending, ...dirty.current }
      setSaving('offline')
    }
  }, [id])

  const submit = useCallback(async () => {
    if (submitting.current) return
    submitting.current = true
    try {
      setDone(await submitTest(id, { ...answers, ...dirty.current }))
      setTaking(null)
    } catch (e) {
      submitting.current = false
      setError(e instanceof Error ? e.message : 'Could not submit. Check your connection and try again.')
    }
  }, [id, answers])

  // Save every few seconds while answering.
  useEffect(() => {
    if (!taking) return
    const timer = window.setInterval(() => void flush(), 5000)
    return () => window.clearInterval(timer)
  }, [taking, flush])

  // Leaving the tab while the test is open is noted (a signal for people, not an automatic penalty).
  useEffect(() => {
    if (!taking) return
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') activity.current = { ...activity.current, tabSwitches: activity.current.tabSwitches + 1 }
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => document.removeEventListener('visibilitychange', onVisibility)
  }, [taking])

  function pasted(chars: number) {
    activity.current = { ...activity.current, pastes: activity.current.pastes + 1, pastedChars: activity.current.pastedChars + chars }
  }

  // The countdown; at zero the test is submitted with what's answered.
  useEffect(() => {
    if (!taking) return
    const timer = window.setInterval(() => setLeft((s) => Math.max(0, s - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [taking])
  useEffect(() => {
    if (taking && left === 0) void submit()
  }, [taking, left, submit])

  function answer(questionId: string, value: string[]) {
    setAnswers((a) => ({ ...a, [questionId]: value }))
    dirty.current = { ...dirty.current, [questionId]: value }
  }

  async function onStart() {
    setError(null)
    try {
      begin(await startTest(id))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not start the test')
    }
  }

  const answered = taking
    ? taking.questions.filter((q) =>
        q.kind === 'CODING' ? (answers[q.id]?.[1] ?? '').trim() !== '' : (answers[q.id] ?? []).some((v) => v.trim() !== ''),
      ).length
    : 0
  const hasCoding = (intro?.category === 'CODING') || (taking?.questions.some((q) => q.kind === 'CODING') ?? false)

  return (
    <div className="candidate">
      <header className="candidate-bar">
        <div className="brand">
          <img src="/favicon.svg" alt="" />
          <span>CodeWalnut Careers</span>
        </div>
        {taking && (
          <div className="row" style={{ gap: 12 }}>
            <span className="muted" style={{ fontSize: 13 }}>
              {saving === 'saving' ? 'Saving…' : saving === 'saved' ? 'Saved' : saving === 'offline' ? 'Not saved — check your connection' : ''}
            </span>
            <strong role="timer" aria-label="Time left" className={left < 60 ? 'test-timer low' : 'test-timer'}>
              {clock(left)}
            </strong>
          </div>
        )}
      </header>
      <main className="candidate-main stack">
        {error && (
          <div role="alert" className="alert alert-error">
            {error} <Link to="/">Back to my applications</Link>
          </div>
        )}
        {intro && (
          <Card className="stack">
            <h1 style={{ margin: 0 }}>{intro.title}</h1>
            <p className="muted" style={{ margin: 0 }}>
              {CATEGORY_LABEL[intro.category]} test for {intro.jobTitle}
            </p>
            {intro.description && <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{intro.description}</p>}
            <ul>
              <li>
                {intro.questionCount} questions, <strong>{intro.durationMinutes} minutes</strong>.
              </li>
              <li>The timer starts when you click Start and keeps running if you close the page.</li>
              <li>Your answers are saved as you go. When time runs out, what you’ve answered is submitted.</li>
              <li>Please work on your own, without help or AI tools.</li>
              {hasCoding && (
                <li>
                  Coding questions: write a program that reads the input and prints the answer. Use “Run on samples” to check your code (a
                  limited number of times); after you submit it’s also checked against hidden tests.
                </li>
              )}
              <li>Due by {new Date(intro.dueAt).toLocaleString()}.</li>
            </ul>
            {intro.status === 'SENT' ? (
              <div>
                <Button onClick={() => void onStart()}>Start the test</Button>
              </div>
            ) : (
              <p className="alert alert-info">
                {intro.status === 'EXPIRED'
                  ? 'This test is past its due date. Message the CodeWalnut team from your applications page if you need more time.'
                  : 'This test is no longer open.'}
              </p>
            )}
          </Card>
        )}
        {taking && (
          <>
            <Card>
              <strong>{taking.test.title}</strong>{' '}
              <span className="muted">
                · {answered} of {taking.questions.length} answered
              </span>
            </Card>
            {taking.questions.map((q, index) => {
              const value = answers[q.id] ?? []
              const newSection = q.section && q.section !== taking.questions[index - 1]?.section
              return (
                <div key={q.id} className="stack" style={{ gap: 8 }}>
                {newSection && <h2 className="section-heading">{sectionTitle(q.section, (c) => CATEGORY_LABEL[c] ?? c)}</h2>}
                <Card className="stack" aria-label={`Question ${q.position}`} role="group">
                  <div className="row" style={{ justifyContent: 'space-between' }}>
                    <strong>Question {q.position}</strong>
                    <span className="muted" style={{ fontSize: 13 }}>
                      {q.points} point{q.points === 1 ? '' : 's'}
                      {q.kind === 'MULTI_CHOICE' ? ' · choose all that apply' : ''}
                    </span>
                  </div>
                  <div style={{ whiteSpace: 'pre-wrap' }}>{q.prompt}</div>
                  {q.figure && <Figure figure={q.figure} />}
                  {q.code && <pre className="test-code">{q.code}</pre>}
                  {q.kind === 'CODING' && q.coding ? (
                    <CodingQuestion
                      testId={id}
                      questionId={q.id}
                      position={q.position}
                      spec={q.coding}
                      value={value}
                      onChange={(v) => answer(q.id, v)}
                      onPaste={pasted}
                    />
                  ) : q.kind === 'SHORT_ANSWER' ? (
                    <input
                      className="input"
                      aria-label={`Answer to question ${q.position}`}
                      value={value[0] ?? ''}
                      maxLength={500}
                      onChange={(e) => answer(q.id, [e.target.value])}
                    />
                  ) : q.optionFigures ? (
                    <div className="option-figures" role="radiogroup" aria-label={`Options for question ${q.position}`}>
                      {q.optionFigures.map((f, i) => (
                        <label key={i} className={`option-figure${value.includes(String(i)) ? ' selected' : ''}`}>
                          <Figure figure={f} small alt={q.options[i]} />
                          <span className="row" style={{ gap: 6 }}>
                            <input
                              type={q.kind === 'SINGLE_CHOICE' ? 'radio' : 'checkbox'}
                              name={q.id}
                              aria-label={q.options[i]}
                              checked={value.includes(String(i))}
                              onChange={(e) =>
                                answer(
                                  q.id,
                                  q.kind === 'SINGLE_CHOICE'
                                    ? [String(i)]
                                    : e.target.checked
                                      ? [...value, String(i)]
                                      : value.filter((v) => v !== String(i)),
                                )
                              }
                            />
                            {String.fromCharCode(65 + i)}
                          </span>
                        </label>
                      ))}
                    </div>
                  ) : (
                    <div className="stack" style={{ gap: 6 }}>
                      {q.options.map((o, i) => (
                        <label key={i} className="row" style={{ gap: 8, flexWrap: 'nowrap', alignItems: 'flex-start' }}>
                          <input
                            type={q.kind === 'SINGLE_CHOICE' ? 'radio' : 'checkbox'}
                            name={q.id}
                            checked={value.includes(String(i))}
                            onChange={(e) =>
                              answer(
                                q.id,
                                q.kind === 'SINGLE_CHOICE'
                                  ? [String(i)]
                                  : e.target.checked
                                    ? [...value, String(i)]
                                    : value.filter((v) => v !== String(i)),
                              )
                            }
                          />
                          <span className="test-option">{o}</span>
                        </label>
                      ))}
                    </div>
                  )}
                </Card>
                </div>
              )
            })}
            <div className="row">
              <Button
                onClick={() => {
                  const missing = taking.questions.length - answered
                  if (window.confirm(missing ? `${missing} question(s) unanswered. Submit anyway?` : 'Submit your answers? You can’t change them after.')) void submit()
                }}
              >
                Submit
              </Button>
            </div>
          </>
        )}
        {done && (
          <Card className="stack">
            <h1 style={{ margin: 0 }}>Thank you!</h1>
            <p style={{ margin: 0 }}>
              Your <strong>{done.title}</strong> test for {done.jobTitle} has been submitted. The CodeWalnut team will get back to you.
            </p>
            <div>
              <Link to="/" className="btn btn-secondary">
                Back to my applications
              </Link>
            </div>
          </Card>
        )}
      </main>
    </div>
  )
}
