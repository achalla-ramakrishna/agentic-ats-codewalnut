import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  CATEGORY_LABEL,
  KIND_LABEL,
  addQuestion,
  archiveAssessment,
  codingStatus,
  createAssessment,
  deleteAssessment,
  deleteQuestion,
  draftQuestions,
  duplicateAssessment,
  getAssessment,
  listAssessments,
  publishAssessment,
  unpublishAssessment,
  updateAssessment,
  updateQuestion,
  type AssessmentDetail,
  type AssessmentSummary,
  type Category,
  type QuestionView,
} from '../api/assessments'
import { Badge, Button, Card, PageHeader } from '../components/ui'
import { QuestionForm } from '../components/QuestionForm'
import { QuestionPreview } from '../components/QuestionPreview'
import { TryCoding } from '../components/TryCoding'
import { BuildFromBank } from '../components/BuildFromBank'
import { NewResults } from '../components/NewResults'
import { QuestionBankPanel } from '../components/QuestionBankPanel'
import { SendToCandidates } from '../components/SendToCandidates'
import { addFromBank } from '../api/questionBank'
import { DIFFICULTY_LABEL, sectionTitle } from '../api/questionBank'
import '../components/tracker.css'

const CATEGORIES = Object.keys(CATEGORY_LABEL) as Category[]
const STATUS_LABEL = { DRAFT: 'Draft', READY: 'Ready to send', ARCHIVED: 'Archived' }

function QuestionCard({
  q,
  assessmentId,
  editable,
  onEdit,
  onDelete,
}: {
  q: QuestionView
  assessmentId: string
  editable: boolean
  onEdit: () => void
  onDelete: () => void
}) {
  return (
    <li className="stack" style={{ gap: 4, padding: '10px 0', borderTop: '1px solid var(--color-border, #e2e8f0)' }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="row" style={{ gap: 8 }}>
          <strong>{q.position}.</strong>
          <span className="muted" style={{ fontSize: 13 }}>
            {KIND_LABEL[q.kind]} · {q.points} point{q.points === 1 ? '' : 's'}
            {q.section ? ` · ${sectionTitle(q.section, (c) => CATEGORY_LABEL[c] ?? c)}` : ''}
            {q.topic ? ` · ${q.topic}` : ''}
          </span>
          {q.difficulty && <span className={`diff-${q.difficulty}`} style={{ fontSize: 13 }}>{DIFFICULTY_LABEL[q.difficulty]}</span>}
          {q.aiDrafted && <Badge tone="primary">AI draft — check it</Badge>}
        </span>
        {editable && (
          <span className="row" style={{ gap: 6 }}>
            <Button size="sm" variant="ghost" onClick={onEdit}>
              Edit
            </Button>
            <Button size="sm" variant="ghost" onClick={onDelete}>
              Delete
            </Button>
          </span>
        )}
      </div>
      <QuestionPreview question={q} showAnswer />
      {q.kind === 'CODING' && q.coding && <TryCoding assessmentId={assessmentId} questionId={q.id} coding={q.coding} />}
    </li>
  )
}

function Editor({
  id,
  onChanged,
  onOpen,
  onDeleted,
}: {
  id: string
  onChanged: () => void
  onOpen: (id: string) => void
  onDeleted: (message: string) => void
}) {
  const [detail, setDetail] = useState<AssessmentDetail | null>(null)
  const [editing, setEditing] = useState<string | 'new' | null>(null)
  const [picking, setPicking] = useState(false)
  const [sending, setSending] = useState(false)
  const [topic, setTopic] = useState('')
  const [level, setLevel] = useState('Fresher')
  const [count, setCount] = useState(5)
  const [drafting, setDrafting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [runner, setRunner] = useState<boolean | null>(null)

  useEffect(() => {
    getAssessment(id).then(setDetail).catch((e: unknown) => setError(e instanceof Error ? e.message : 'Not found'))
  }, [id])

  const hasCoding = detail?.questions.some((q) => q.kind === 'CODING') ?? false
  useEffect(() => {
    if (hasCoding && runner === null) codingStatus().then((s) => setRunner(s.available)).catch(() => setRunner(null))
  }, [hasCoding, runner])

  async function run(action: () => Promise<AssessmentDetail>, done?: string) {
    setError(null)
    setMessage(null)
    try {
      setDetail(await action())
      if (done) setMessage(done)
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  async function draft() {
    setDrafting(true)
    setError(null)
    setMessage(null)
    try {
      const r = await draftQuestions(id, { topic: topic.trim() || undefined, level, count })
      setDetail(r.assessment)
      setMessage(`Added ${r.added} AI-drafted question${r.added === 1 ? '' : 's'}. Check each one (answers too) before marking the test ready.${r.notes.length ? ' ' + r.notes.join(' ') : ''}`)
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not draft questions')
    } finally {
      setDrafting(false)
    }
  }

  if (error && !detail) return <p role="alert" className="alert alert-error">{error}</p>
  if (!detail) return <p className="muted">Loading…</p>
  const s = detail.summary
  const draftMode = s.status === 'DRAFT'
  const unchecked = detail.questions.filter((q) => q.aiDrafted).length

  return (
    <Card>
      <div className="stack">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <span className="row" style={{ gap: 8 }}>
            <h2 style={{ margin: 0 }}>{s.title}</h2>
            <Badge tone={s.status === 'READY' ? 'primary' : 'neutral'}>{STATUS_LABEL[s.status]}</Badge>
          </span>
          <span className="row" style={{ gap: 6 }}>
            {s.status === 'READY' && (
              <Button onClick={() => setSending(!sending)}>{sending ? 'Close sending' : 'Send to candidates'}</Button>
            )}
            {draftMode && (
              <Button
                disabled={detail.questions.length === 0}
                onClick={() => {
                  if (unchecked && !window.confirm(`${unchecked} AI-drafted question(s) haven’t been edited. Have you checked them all?`)) return
                  void run(() => publishAssessment(id), 'Ready to send. Questions are now locked.')
                }}
              >
                Mark ready
              </Button>
            )}
            {s.status === 'READY' && s.invites === 0 && (
              <Button variant="ghost" onClick={() => void run(() => unpublishAssessment(id))}>
                Back to draft
              </Button>
            )}
            <Button
              variant="secondary"
              onClick={async () => {
                const copy = await duplicateAssessment(id)
                onChanged()
                onOpen(copy.summary.id)
              }}
            >
              Duplicate
            </Button>
            {s.status !== 'ARCHIVED' && (
              <Button variant="ghost" onClick={() => void run(() => archiveAssessment(id))}>
                Archive
              </Button>
            )}
            {s.taken === 0 && (
              <Button
                variant="ghost"
                onClick={async () => {
                  const open = s.invites
                  const warning =
                    open > 0
                      ? `Delete “${s.title}”? ${open} candidate${open === 1 ? ' was' : 's were'} sent this test but ${open === 1 ? "hasn't" : "haven't"} started; ${open === 1 ? 'that link' : 'those links'} will stop working. This can't be undone.`
                      : `Delete “${s.title}”? This can't be undone.`
                  if (!window.confirm(warning)) return
                  try {
                    const r = await deleteAssessment(id)
                    onDeleted(`Deleted “${r.title}”.`)
                  } catch (e) {
                    setError(e instanceof Error ? e.message : 'Could not delete the test')
                  }
                }}
              >
                Delete
              </Button>
            )}
          </span>
        </div>
        <p className="muted" style={{ margin: 0 }}>
          {CATEGORY_LABEL[s.category]} · {s.questionCount} questions · {s.totalPoints} points · {s.durationMinutes} minutes · pass mark{' '}
          {s.passPercent}% · sent {s.invites} time{s.invites === 1 ? '' : 's'}
        </p>
        {draftMode && (
          <div className="row" style={{ alignItems: 'flex-end' }}>
            <label className="field" style={{ maxWidth: 120 }}>
              Time (min)
              <input
                className="input"
                type="number"
                min={5}
                max={180}
                defaultValue={s.durationMinutes}
                onBlur={(e) => Number(e.target.value) !== s.durationMinutes && void run(() => updateAssessment(id, { durationMinutes: Number(e.target.value) }))}
              />
            </label>
            <label className="field" style={{ maxWidth: 120 }}>
              Pass mark (%)
              <input
                className="input"
                type="number"
                min={0}
                max={100}
                defaultValue={s.passPercent}
                onBlur={(e) => Number(e.target.value) !== s.passPercent && void run(() => updateAssessment(id, { passPercent: Number(e.target.value) }))}
              />
            </label>
          </div>
        )}
        {!draftMode && (
          <p className="muted" style={{ margin: 0, fontSize: 13 }}>
            Questions are locked so every candidate gets the same test. Use Duplicate to make a changed version.
            {s.taken > 0 && ` ${s.taken} candidate${s.taken === 1 ? ' has' : 's have'} taken it, so it can be archived but not deleted.`}
          </p>
        )}
        {message && <div className="alert alert-info">{message}</div>}
        {hasCoding && runner === false && (
          <div className="alert alert-error">
            The code runner isn’t connected yet, so coding questions can be written but candidates can’t run their code and it won’t be graded.
            An admin needs to set up Judge0 (see docs/deploy-judge0.md).
          </div>
        )}
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        {s.status === 'READY' && sending && (
          <SendToCandidates
            test={s}
            onClose={() => setSending(false)}
            onSent={() => {
              getAssessment(id).then(setDetail).catch(() => undefined)
              onChanged()
            }}
          />
        )}
        {draftMode && (
          <div className="card stack" role="region" aria-label="Draft questions with AI" style={{ gap: 8 }}>
            <strong>✨ Draft questions with AI</strong>
            <div className="row" style={{ alignItems: 'flex-end' }}>
              <label className="field" style={{ flex: '1 1 260px' }}>
                Topic or focus (optional)
                <input className="input" placeholder="e.g. OOP, collections, Spring Boot basics" value={topic} onChange={(e) => setTopic(e.target.value)} />
              </label>
              <label className="field">
                Level
                <select className="select" value={level} onChange={(e) => setLevel(e.target.value)}>
                  <option>Fresher</option>
                  <option>Intern</option>
                  <option>1–3 years</option>
                  <option>3+ years</option>
                </select>
              </label>
              <label className="field" style={{ maxWidth: 100 }}>
                How many
                <input className="input" type="number" min={1} max={15} value={count} onChange={(e) => setCount(Number(e.target.value))} />
              </label>
              <Button variant="secondary" disabled={drafting} onClick={() => void draft()}>
                {drafting ? 'Drafting…' : 'Draft'}
              </Button>
            </div>
            <span className="muted" style={{ fontSize: 13 }}>
              The AI suggests questions and answers; you check and edit them. Nothing is sent to candidates until you mark the test ready.
            </span>
          </div>
        )}
        <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {detail.questions.map((q) =>
            editing === q.id ? (
              <li key={q.id}>
                <QuestionForm
                  initial={q}
                  onCancel={() => setEditing(null)}
                  onSave={async (input) => {
                    setDetail(await updateQuestion(id, q.id, input))
                    setEditing(null)
                  }}
                />
              </li>
            ) : (
              <QuestionCard
                key={q.id}
                q={q}
                assessmentId={id}
                editable={draftMode}
                onEdit={() => setEditing(q.id)}
                onDelete={() => window.confirm('Delete this question?') && void run(() => deleteQuestion(id, q.id))}
              />
            ),
          )}
        </ol>
        {detail.questions.length === 0 && <p className="muted">No questions yet. Draft some with AI or add your own.</p>}
        {draftMode &&
          (editing === 'new' ? (
            <QuestionForm
              onCancel={() => setEditing(null)}
              onSave={async (input) => {
                setDetail(await addQuestion(id, input))
                setEditing(null)
                onChanged()
              }}
            />
          ) : (
            <div className="row" style={{ gap: 8 }}>
              <Button variant="secondary" onClick={() => setEditing('new')}>
                + Add a question
              </Button>
              <Button variant="secondary" onClick={() => setPicking(!picking)}>
                {picking ? 'Close the bank' : '+ Add from the question bank'}
              </Button>
            </div>
          ))}
        {draftMode && picking && (
          <QuestionBankPanel
            pickLabel="Add"
            initialArea={s.category}
            onPick={async (ids) => {
              await run(() => addFromBank(id, ids), `Added ${ids.length} question${ids.length === 1 ? '' : 's'} from the bank.`)
            }}
          />
        )}
      </div>
    </Card>
  )
}

function CreateForm({ onCreated, onCancel }: { onCreated: (id: string) => void; onCancel: () => void }) {
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState<Category>('APTITUDE')
  const [duration, setDuration] = useState(30)
  const [pass, setPass] = useState(60)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    try {
      const d = await createAssessment({ title, category, durationMinutes: duration, passPercent: pass })
      onCreated(d.summary.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create the test')
    }
  }

  return (
    <Card>
      <form className="stack" onSubmit={submit} aria-label="New test">
        <h2>New test</h2>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field" style={{ flex: '1 1 260px' }}>
            Title
            <input className="input" required placeholder="e.g. Java basics for interns" value={title} onChange={(e) => setTitle(e.target.value)} />
          </label>
          <label className="field">
            Kind
            <select className="select" value={category} onChange={(e) => setCategory(e.target.value as Category)}>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {CATEGORY_LABEL[c]}
                </option>
              ))}
            </select>
          </label>
          <label className="field" style={{ maxWidth: 120 }}>
            Time (min)
            <input className="input" type="number" min={5} max={180} value={duration} onChange={(e) => setDuration(Number(e.target.value))} />
          </label>
          <label className="field" style={{ maxWidth: 120 }}>
            Pass mark (%)
            <input className="input" type="number" min={0} max={100} value={pass} onChange={(e) => setPass(Number(e.target.value))} />
          </label>
        </div>
        <div className="row">
          <Button type="submit" disabled={!title.trim()}>
            Create
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  )
}

/** The test library: create tests, draft questions with AI, check them and mark them ready (ADR-0011). */
export function TestsPage() {
  const [tests, setTests] = useState<AssessmentSummary[] | null>(null)
  const [searchParams] = useSearchParams()
  /** ?test=<id> opens that test, e.g. after "Create this test" in an interview kit. */
  const [selected, setSelected] = useState<string | null>(searchParams.get('test'))
  const editorRef = useRef<HTMLDivElement>(null)
  // Arriving with ?test=<id>: bring the test into view once it has rendered.
  useEffect(() => {
    if (!searchParams.get('test')) return
    const t = window.setTimeout(() => editorRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }), 300)
    return () => window.clearTimeout(t)
  }, [searchParams])
  const [creating, setCreating] = useState(false)
  const [building, setBuilding] = useState(false)
  const [tab, setTab] = useState<'tests' | 'bank'>('tests')
  const [notice, setNotice] = useState<string | null>(null)
  const [showArchived, setShowArchived] = useState(false)

  const load = useCallback(() => {
    listAssessments().then(setTests).catch(() => setTests([]))
  }, [])
  useEffect(load, [load])

  const visible = (tests ?? []).filter((t) => showArchived || t.status !== 'ARCHIVED')
  return (
    <div className="stack">
      <PageHeader
        title="Tests"
        description="Aptitude, Java, Python and other tests to send to candidates. Build them from the question bank or write your own. Scored automatically; you decide."
        actions={
          <>
            <Button variant="secondary" onClick={() => { setTab('tests'); setBuilding(true); setCreating(false) }}>
              Build from bank
            </Button>
            <Button onClick={() => { setTab('tests'); setCreating(true); setBuilding(false) }}>New test</Button>
          </>
        }
      />
      <div className="tabs" role="tablist" aria-label="Tests sections">
        <button type="button" role="tab" aria-selected={tab === 'tests'} onClick={() => setTab('tests')}>
          Tests
        </button>
        <button type="button" role="tab" aria-selected={tab === 'bank'} onClick={() => setTab('bank')}>
          Question bank
        </button>
      </div>
      {notice && <div className="alert alert-info">{notice}</div>}
      {tab === 'tests' && <NewResults />}
      {tab === 'bank' && <QuestionBankPanel />}
      {tab === 'tests' && building && (
        <BuildFromBank
          onCancel={() => setBuilding(false)}
          onBuilt={(id) => {
            setBuilding(false)
            load()
            setSelected(id)
          }}
        />
      )}
      {tab === 'tests' && creating && (
        <CreateForm
          onCancel={() => setCreating(false)}
          onCreated={(id) => {
            setCreating(false)
            load()
            setSelected(id)
          }}
        />
      )}
      {tab === 'tests' && (
      <Card>
        {!tests && <p className="muted">Loading…</p>}
        {tests && visible.length === 0 && <p className="muted" style={{ margin: 0 }}>No tests yet. Click New test to make one.</p>}
        {visible.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Test</th>
                  <th>Kind</th>
                  <th>Questions</th>
                  <th>Time</th>
                  <th>Pass mark</th>
                  <th>Status</th>
                  <th>Sent</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((t) => (
                  <tr key={t.id} aria-selected={selected === t.id}>
                    <td>
                      <button type="button" className="row-link" onClick={() => setSelected(t.id)}>
                        {t.title}
                      </button>
                    </td>
                    <td>{CATEGORY_LABEL[t.category]}</td>
                    <td>{t.questionCount}</td>
                    <td>{t.durationMinutes} min</td>
                    <td>{t.passPercent}%</td>
                    <td>{STATUS_LABEL[t.status]}</td>
                    <td>{t.invites}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {tests && tests.some((t) => t.status === 'ARCHIVED') && (
          <label className="row muted" style={{ gap: 6, fontSize: 13, marginTop: 8 }}>
            <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> Show archived
          </label>
        )}
      </Card>
      )}
      {tab === 'tests' && selected && (
        <div ref={editorRef}>
          <Editor
            key={selected}
            id={selected}
            onChanged={load}
            onOpen={setSelected}
            onDeleted={(message) => {
              setSelected(null)
              setNotice(message)
              load()
            }}
          />
        </div>
      )}
    </div>
  )
}
