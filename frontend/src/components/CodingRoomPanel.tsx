import { useEffect, useRef, useState, type FormEvent } from 'react'
import { LANGUAGE_LABEL } from '../api/assessments'
import {
  endCodingRoom,
  getCodingRoom,
  listCodingProblems,
  roomLink,
  runRoomCode,
  saveRoomNotes,
  startCodingRoom,
  type ProblemInput,
  type ProblemOption,
  type RoomPage,
} from '../api/codingRoom'
import { getInterviewKit, type KitCoding } from '../api/interviewKit'
import { CodeEditor } from './CodeEditor'
import { RunResults } from './RunResults'
import { Badge, Button, Card } from './ui'

const POLL_MS = 2000
const OWN = '__own__'

function ago(iso: string | null) {
  if (!iso) return null
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return `${s} s ago`
  if (s < 3600) return `${Math.round(s / 60)} min ago`
  return new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
}

/** Pick a problem: the interview kit's first, then the built-in bank by topic, or type your own. */
function ProblemPicker({
  jobId,
  busy,
  submitLabel,
  onPick,
  onCancel,
}: {
  jobId: string
  busy: boolean
  submitLabel: string
  onPick: (input: ProblemInput) => void
  onCancel?: () => void
}) {
  const [problems, setProblems] = useState<ProblemOption[]>([])
  const [kit, setKit] = useState<KitCoding[]>([])
  const [choice, setChoice] = useState('')
  const [title, setTitle] = useState('')
  const [statement, setStatement] = useState('')
  const [sampleInput, setSampleInput] = useState('')
  const [sampleOutput, setSampleOutput] = useState('')

  useEffect(() => {
    listCodingProblems()
      .then(setProblems)
      .catch(() => setProblems([]))
    getInterviewKit(jobId)
      .then((p) => {
        const seen = new Set<string>()
        const coding = (p.kit?.kit.rounds ?? []).flatMap((r) => r.coding).filter((c) => !seen.has(c.id) && seen.add(c.id))
        setKit(coding)
        if (coding.length > 0) setChoice((c) => c || coding[0].id)
      })
      .catch(() => setKit([]))
  }, [jobId])

  const topics = [...new Set(problems.map((p) => p.topic))]
  const own = choice === OWN

  function submit(event: FormEvent) {
    event.preventDefault()
    if (own) onPick({ title, statement, sampleInput: sampleInput || undefined, sampleOutput: sampleOutput || undefined })
    else if (choice) onPick({ problemId: choice })
  }

  return (
    <form className="stack" style={{ gap: 10 }} aria-label="Choose a problem" onSubmit={submit}>
      <label className="stack" style={{ gap: 4 }}>
        <span>Problem</span>
        <select className="input" value={choice} onChange={(e) => setChoice(e.target.value)} aria-label="Problem">
          <option value="">Choose a problem…</option>
          {kit.length > 0 && (
            <optgroup label="From the interview kit">
              {kit.map((c) => (
                <option key={`kit-${c.id}`} value={c.id}>
                  {c.title} ({c.difficulty.toLowerCase()})
                </option>
              ))}
            </optgroup>
          )}
          {topics.map((t) => (
            <optgroup key={t} label={t}>
              {problems
                .filter((p) => p.topic === t)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.title} ({p.difficulty.toLowerCase()})
                  </option>
                ))}
            </optgroup>
          ))}
          <option value={OWN}>Type my own problem…</option>
        </select>
      </label>
      {own && (
        <>
          <input className="input" aria-label="Problem title" placeholder="Title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <textarea
            className="input"
            aria-label="Problem statement"
            placeholder="What should the candidate build? Say how the input arrives and what to print."
            style={{ fontFamily: 'inherit', minHeight: 100 }}
            value={statement}
            onChange={(e) => setStatement(e.target.value)}
          />
          <div className="run-case-grid">
            <textarea
              className="input test-code"
              aria-label="Sample input"
              placeholder="Sample input (optional)"
              value={sampleInput}
              onChange={(e) => setSampleInput(e.target.value)}
            />
            <textarea
              className="input test-code"
              aria-label="Sample output"
              placeholder="Expected output (optional)"
              value={sampleOutput}
              onChange={(e) => setSampleOutput(e.target.value)}
            />
          </div>
        </>
      )}
      <div className="row" style={{ gap: 8 }}>
        <Button type="submit" disabled={busy || !choice || (own && (!title.trim() || !statement.trim()))}>
          {busy ? 'Opening…' : submitLabel}
        </Button>
        {onCancel && (
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}

/**
 * The live coding room for one interview (INT-36…INT-39): the interviewer picks a problem, sends the
 * candidate the link in the Meet chat, and watches their code as they type. Notes stay with staff.
 */
export function CodingRoomPanel({ interviewId, jobId, cancelled }: { interviewId: string; jobId: string; cancelled: boolean }) {
  const [page, setPage] = useState<RoomPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [picking, setPicking] = useState(false)
  const [copied, setCopied] = useState(false)
  const [running, setRunning] = useState(false)
  const [notes, setNotes] = useState('')
  const [notesState, setNotesState] = useState<'' | 'saving' | 'saved'>('')
  const roomId = useRef<string | null>(null)
  const room = page?.room ?? null
  const open = room?.status === 'OPEN'

  useEffect(() => {
    getCodingRoom(interviewId)
      .then(setPage)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the coding room'))
  }, [interviewId])

  // Notes are typed here; take the server's copy only when a room first appears.
  useEffect(() => {
    if (room && roomId.current !== room.id) {
      roomId.current = room.id
      setNotes(room.notes ?? '')
    }
  }, [room])

  // Watch the candidate's code while the room is open.
  useEffect(() => {
    if (!open) return
    const t = window.setInterval(() => {
      getCodingRoom(interviewId)
        .then(setPage)
        .catch(() => undefined)
    }, POLL_MS)
    return () => window.clearInterval(t)
  }, [open, interviewId])

  async function act(fn: () => Promise<RoomPage>) {
    setBusy(true)
    setError(null)
    try {
      setPage(await fn())
      setPicking(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  async function run() {
    setRunning(true)
    setError(null)
    try {
      await runRoomCode(interviewId)
      setPage(await getCodingRoom(interviewId))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not run the code')
    } finally {
      setRunning(false)
    }
  }

  async function saveNotes() {
    if (!room || notes === (room.notes ?? '')) return
    setNotesState('saving')
    try {
      setPage(await saveRoomNotes(interviewId, notes))
      setNotesState('saved')
    } catch (e) {
      setNotesState('')
      setError(e instanceof Error ? e.message : 'Could not save your notes')
    }
  }

  useEffect(() => {
    if (!room || notes === (room.notes ?? '')) return
    const t = window.setTimeout(() => void saveNotes(), 1500)
    return () => window.clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [notes])

  async function copy(link: string) {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }

  if (!page) {
    return error ? (
      <div role="alert" className="alert alert-error">
        {error}
      </div>
    ) : null
  }

  const link = room ? roomLink(room.linkPath) : ''

  return (
    <Card>
      <section className="stack" aria-label="Coding room" style={{ gap: 12 }}>
        <div className="row" style={{ gap: 8, justifyContent: 'space-between', flexWrap: 'wrap' }}>
          <div className="row" style={{ gap: 8 }}>
            <h3 style={{ margin: 0 }}>Coding room</h3>
            {room && <Badge tone={open ? 'primary' : 'neutral'}>{open ? 'Open' : 'Ended'}</Badge>}
          </div>
          {room && page.canManage && open && (
            <div className="row" style={{ gap: 8 }}>
              <Button size="sm" variant="secondary" disabled={running || !room.code?.trim()} onClick={() => void run()}>
                {running ? 'Running…' : 'Run their code'}
              </Button>
              <Button size="sm" variant="secondary" onClick={() => setPicking((p) => !p)}>
                Next problem
              </Button>
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => void act(() => endCodingRoom(interviewId))}>
                End room
              </Button>
            </div>
          )}
          {room && page.canManage && !open && !cancelled && (
            <Button size="sm" variant="secondary" onClick={() => setPicking((p) => !p)}>
              Reopen with a problem
            </Button>
          )}
        </div>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        {!page.runnerAvailable && (
          <div className="alert alert-info">The code runner isn&apos;t set up, so code can be written and watched here but not run.</div>
        )}

        {!room && (
          <>
            <p className="muted" style={{ margin: 0 }}>
              Give the candidate a problem to solve while you watch. They open a private link, sign in with their email, write and run code
              in their browser, and you see every change here. Ask them to share their screen in Meet as well.
            </p>
            {cancelled ? (
              <p className="muted" style={{ margin: 0 }}>
                This interview was cancelled.
              </p>
            ) : !page.canManage ? (
              <p className="muted" style={{ margin: 0 }}>
                Only the panel can open a coding room.
              </p>
            ) : !page.candidateHasEmail ? (
              <div className="alert alert-info">Add the candidate&apos;s email first. They sign in with it to open the room.</div>
            ) : (
              <ProblemPicker jobId={jobId} busy={busy} submitLabel="Open coding room" onPick={(input) => void act(() => startCodingRoom(interviewId, input))} />
            )}
          </>
        )}

        {room && picking && (
          <ProblemPicker
            jobId={jobId}
            busy={busy}
            submitLabel={open ? 'Switch problem' : 'Reopen room'}
            onPick={(input) => void act(() => startCodingRoom(interviewId, input))}
            onCancel={() => setPicking(false)}
          />
        )}

        {room && (
          <>
            {open && (
              <div className="stack" style={{ gap: 6 }}>
                <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
                  <input className="input" readOnly aria-label="Candidate link" value={link} style={{ flex: 1, minWidth: 240 }} />
                  <Button size="sm" variant="secondary" onClick={() => void copy(link)}>
                    {copied ? 'Copied' : 'Copy link'}
                  </Button>
                </div>
                <span className="muted" style={{ fontSize: 13 }}>
                  Paste this in the Meet chat. {room.candidateName} signs in with their email to open it.{' '}
                  {room.candidateSeenAt ? (
                    <strong>
                      {Date.now() - new Date(room.candidateSeenAt).getTime() < 30_000
                        ? 'They have the room open.'
                        : `They last had it open ${ago(room.candidateSeenAt)}.`}
                    </strong>
                  ) : (
                    <strong>They haven&apos;t opened it yet.</strong>
                  )}
                </span>
              </div>
            )}
            <div className="stack" style={{ gap: 4 }}>
              <strong>{room.problem.title}</strong>
              <details>
                <summary className="muted" style={{ fontSize: 13 }}>
                  Problem statement
                </summary>
                <p style={{ whiteSpace: 'pre-wrap' }}>{room.problem.statement}</p>
              </details>
            </div>
            <div className="row" style={{ gap: 8 }}>
              <Badge tone="neutral">{LANGUAGE_LABEL[room.language] ?? room.language}</Badge>
              <span className="muted" style={{ fontSize: 13 }}>
                {room.codeUpdatedAt ? `Last change ${ago(room.codeUpdatedAt)}` : 'No changes yet'} · {room.runCount} run
                {room.runCount === 1 ? '' : 's'}
              </span>
            </div>
            <CodeEditor label="Candidate's code" value={room.code ?? ''} readOnly minLines={10} />
            {room.lastRun && (
              <RunResults
                cases={room.lastRun.cases}
                compileOutput={room.lastRun.compileOutput}
                passed={room.lastRun.passed}
                total={room.lastRun.total}
                title="Last run"
              />
            )}
            {room.canManage && (
              <label className="stack" style={{ gap: 4 }}>
                <span>
                  Your notes <span className="muted">(staff only, never shown to the candidate)</span>
                </span>
                <textarea
                  className="input"
                  aria-label="Coding room notes"
                  style={{ fontFamily: 'inherit', minHeight: 70 }}
                  value={notes}
                  onChange={(e) => {
                    setNotes(e.target.value)
                    setNotesState('')
                  }}
                  onBlur={() => void saveNotes()}
                />
                {notesState && (
                  <span className="muted" style={{ fontSize: 12 }}>
                    {notesState === 'saving' ? 'Saving…' : 'Saved'}
                  </span>
                )}
              </label>
            )}
            {!room.canManage && room.notes && (
              <div>
                <strong>Notes:</strong> {room.notes}
              </div>
            )}
            {room.history.length > 0 && (
              <details>
                <summary>Earlier problems ({room.history.length})</summary>
                <div className="stack" style={{ gap: 8, marginTop: 8 }}>
                  {room.history.map((h, i) => (
                    <div key={i} className="stack" style={{ gap: 4 }}>
                      <span>
                        <strong>{h.title}</strong>{' '}
                        <span className="muted">
                          · {LANGUAGE_LABEL[h.language as keyof typeof LANGUAGE_LABEL] ?? h.language}
                          {h.total != null ? ` · ${h.passed} of ${h.total} passed` : ''}
                        </span>
                      </span>
                      {h.code && <pre className="test-code">{h.code}</pre>}
                    </div>
                  ))}
                </div>
              </details>
            )}
          </>
        )}
      </section>
    </Card>
  )
}
