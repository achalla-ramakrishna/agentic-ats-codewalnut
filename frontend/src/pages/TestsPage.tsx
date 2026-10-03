import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  CATEGORY_LABEL,
  KIND_LABEL,
  addQuestion,
  archiveAssessment,
  createAssessment,
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
import { BuildFromBank } from '../components/BuildFromBank'
import { QuestionBankPanel } from '../components/QuestionBankPanel'
import { addFromBank } from '../api/questionBank'
import { DIFFICULTY_LABEL, SECTION_LABEL, type Section } from '../api/questionBank'
import '../components/tracker.css'

const CATEGORIES = Object.keys(CATEGORY_LABEL) as Category[]
const STATUS_LABEL = { DRAFT: 'Draft', READY: 'Ready to send', ARCHIVED: 'Archived' }

function QuestionCard({ q, editable, onEdit, onDelete }: { q: QuestionView; editable: boolean; onEdit: () => void; onDelete: () => void }) {
  return (
    <li className="stack" style={{ gap: 4, padding: '10px 0', borderTop: '1px solid var(--color-border, #e2e8f0)' }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="row" style={{ gap: 8 }}>
          <strong>{q.position}.</strong>
          <span className="muted" style={{ fontSize: 13 }}>
            {KIND_LABEL[q.kind]} · {q.points} point{q.points === 1 ? '' : 's'}
            {q.section ? ` · ${SECTION_LABEL[q.section as Section] ?? q.section}` : ''}
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
    </li>
  )
}

function Editor({ id, onChanged, onOpen }: { id: string; onChanged: () => void; onOpen: (id: string) => void }) {
  const [detail, setDetail] = useState<AssessmentDetail | null>(null)
  const [editing, setEditing] = useState<string | 'new' | null>(null)
  const [picking, setPicking] = useState(false)
  const [topic, setTopic] = useState('')
  const [level, setLevel] = useState('Fresher')
  const [count, setCount] = useState(5)
  const [drafting, setDrafting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getAssessment(id).then(setDetail).catch((e: unknown) => setError(e instanceof Error ? e.message : 'Not found'))
  }, [id])

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
          </p>
        )}
        {message && <div className="alert alert-info">{message}</div>}
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
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
  const [selected, setSelected] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [building, setBuilding] = useState(false)
  const [tab, setTab] = useState<'tests' | 'bank'>('tests')
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
      {tab === 'tests' && selected && <Editor key={selected} id={selected} onChanged={load} onOpen={setSelected} />}
    </div>
  )
}
