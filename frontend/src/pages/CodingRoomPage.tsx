import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { LANGUAGE_LABEL, type CodeLanguage } from '../api/assessments'
import { getMyCodingRoom, runMyCode, saveMyCode, type CandidateRoom, type RoomProblem } from '../api/codingRoom'
import { CodeEditor } from '../components/CodeEditor'
import { RunResults } from '../components/RunResults'
import { Button, Card } from '../components/ui'
import '../components/tracker.css'
import './CandidateHomePage.css'

const POLL_MS = 4000
const SAVE_MS = 1000

/** Changes when the interviewer switches problem; the candidate's typing is kept otherwise. */
const problemKey = (p: RoomProblem) => `${p.id ?? ''}|${p.title}|${p.statement}`

/**
 * A live coding room, opened from the link the interviewer shares (INT-37). The candidate writes
 * and runs code here; the panel watches it as they type.
 */
export function CodingRoomPage() {
  const { token = '' } = useParams()
  const [room, setRoom] = useState<CandidateRoom | null>(null)
  const [language, setLanguage] = useState<CodeLanguage>('java')
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState<'' | 'saving' | 'saved' | 'offline'>('')
  const [running, setRunning] = useState(false)
  const [switched, setSwitched] = useState(false)
  const key = useRef<string | null>(null)
  const dirty = useRef(false)

  function apply(next: CandidateRoom) {
    setRoom(next)
    const k = problemKey(next.problem)
    if (key.current !== k) {
      if (key.current !== null) setSwitched(true)
      key.current = k
      dirty.current = false
      setLanguage(next.language)
      setCode(next.code ?? next.problem.starter[next.language] ?? '')
    }
  }

  useEffect(() => {
    getMyCodingRoom(token)
      .then(apply)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not open the coding room'))
    const t = window.setInterval(() => {
      getMyCodingRoom(token)
        .then(apply)
        .catch(() => undefined)
    }, POLL_MS)
    return () => window.clearInterval(t)
  }, [token])

  const open = room?.status === 'OPEN'

  // Save a moment after each change so the panel sees it.
  useEffect(() => {
    if (!dirty.current || !open) return
    const t = window.setTimeout(() => {
      setSaving('saving')
      saveMyCode(token, language, code)
        .then(() => {
          dirty.current = false
          setSaving('saved')
        })
        .catch(() => setSaving('offline'))
    }, SAVE_MS)
    return () => window.clearTimeout(t)
  }, [code, language, open, token])

  function edit(next: string) {
    dirty.current = true
    setCode(next)
  }

  function switchTo(next: CodeLanguage) {
    if (!room) return
    const starter = room.problem.starter
    if (!code.trim() || code === starter[language]) setCode(starter[next] ?? '')
    dirty.current = true
    setLanguage(next)
  }

  async function run() {
    setRunning(true)
    setError(null)
    try {
      const next = await runMyCode(token, language, code)
      dirty.current = false
      setRoom(next)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not run your code')
    } finally {
      setRunning(false)
    }
  }

  const p = room?.problem

  return (
    <div className="candidate">
      <header className="candidate-bar">
        <div className="brand">
          <img src="/favicon.svg" alt="" />
          <span>CodeWalnut Careers</span>
        </div>
        {open && (
          <span className="muted" style={{ fontSize: 13 }}>
            {saving === 'saving' ? 'Saving…' : saving === 'saved' ? 'Saved' : saving === 'offline' ? 'Not saved — check your connection' : ''}
          </span>
        )}
      </header>
      <main className="candidate-main stack">
        {error && !room && (
          <div role="alert" className="alert alert-error">
            {error} <Link to="/">Back to my applications</Link>
          </div>
        )}
        {!room && !error && <p className="muted">Opening the coding room…</p>}
        {room && p && (
          <>
            <div className="stack" style={{ gap: 4 }}>
              <h1 style={{ margin: 0, fontSize: 22 }}>Coding round · {room.jobTitle}</h1>
              <span className="muted">Your interviewer can see your code as you type. Talk them through your thinking.</span>
            </div>
            {!open && (
              <div className="alert alert-info">The interviewer has ended this coding round. Thanks! Your code is saved.</div>
            )}
            {switched && open && (
              <div className="alert alert-info">
                Your interviewer gave you a new problem.{' '}
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setSwitched(false)}>
                  OK
                </button>
              </div>
            )}
            <Card>
              <div className="stack" style={{ gap: 10 }}>
                <h2 style={{ margin: 0, fontSize: 18 }}>{p.title}</h2>
                <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{p.statement}</p>
                {(p.inputFormat || p.outputFormat) && (
                  <dl className="coding-spec">
                    {p.inputFormat && (
                      <>
                        <dt>Input</dt>
                        <dd>{p.inputFormat}</dd>
                      </>
                    )}
                    {p.outputFormat && (
                      <>
                        <dt>Output</dt>
                        <dd>{p.outputFormat}</dd>
                      </>
                    )}
                  </dl>
                )}
                {p.samples.map((s, i) => (
                  <div key={i} className="run-case-grid">
                    <div>
                      <span className="muted">Sample input {p.samples.length > 1 ? i + 1 : ''}</span>
                      <pre className="test-code">{s.input || '(empty)'}</pre>
                    </div>
                    <div>
                      <span className="muted">Expected output</span>
                      <pre className="test-code">{s.output}</pre>
                    </div>
                  </div>
                ))}
                {p.samples.length > 0 && (
                  <p className="muted" style={{ margin: 0, fontSize: 13 }}>
                    Read the input from standard input and print the answer to standard output.
                  </p>
                )}
                <div className="row" style={{ gap: 8 }}>
                  <label className="row" style={{ gap: 6 }}>
                    <span className="muted" style={{ fontSize: 13 }}>
                      Language
                    </span>
                    <select
                      className="input"
                      style={{ width: 'auto' }}
                      aria-label="Language"
                      value={language}
                      disabled={!open}
                      onChange={(e) => switchTo(e.target.value as CodeLanguage)}
                    >
                      {p.languages.map((l) => (
                        <option key={l} value={l}>
                          {LANGUAGE_LABEL[l]}
                        </option>
                      ))}
                    </select>
                  </label>
                  {language === 'java' && (
                    <span className="muted" style={{ fontSize: 12 }}>
                      Keep the class name Main.
                    </span>
                  )}
                </div>
                <CodeEditor label="Your code" value={code} onChange={edit} readOnly={!open} />
                {open && (
                  <div className="row" style={{ gap: 8 }}>
                    <Button
                      variant="secondary"
                      disabled={running || !code.trim() || !room.runnerAvailable || p.samples.length === 0}
                      onClick={() => void run()}
                    >
                      {running ? 'Running…' : 'Run on samples'}
                    </Button>
                    <span className="muted" style={{ fontSize: 13 }}>
                      {!room.runnerAvailable
                        ? 'Running code isn’t available right now; just write it.'
                        : p.samples.length === 0
                          ? 'No samples for this problem; your interviewer will review the code with you.'
                          : `${room.runsLeft} run${room.runsLeft === 1 ? '' : 's'} left`}
                    </span>
                  </div>
                )}
                {error && room && (
                  <div role="alert" className="alert alert-error">
                    {error}
                  </div>
                )}
                {room.lastRun && (
                  <RunResults
                    cases={room.lastRun.cases}
                    compileOutput={room.lastRun.compileOutput}
                    passed={room.lastRun.passed}
                    total={room.lastRun.total}
                    title="Sample results"
                  />
                )}
              </div>
            </Card>
          </>
        )}
      </main>
    </div>
  )
}
