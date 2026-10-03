import { useCallback, useEffect, useState } from 'react'
import { CATEGORY_LABEL, type Category, type QuestionInput } from '../api/assessments'
import {
  BANK_AREAS,
  DIFFICULTY_LABEL,
  sectionName,
  sectionsFor,
  approveBankQuestion,
  archiveBankQuestion,
  createBankQuestion,
  draftBankQuestions,
  getBankOverview,
  listBank,
  updateBankQuestion,
  type BankOverview,
  type BankQuestion,
  type BankStatus,
  type Difficulty,
  type Section,
} from '../api/questionBank'
import { QuestionForm } from './QuestionForm'
import { QuestionPreview } from './QuestionPreview'
import { Badge, Button, Card } from './ui'

const DIFFICULTIES: Difficulty[] = ['EASY', 'MEDIUM', 'HARD']
const SOURCE_LABEL = { BUILT_IN: 'Built-in', AI: 'AI draft', MANUAL: 'Added by CodeWalnut' }
const PAGE = 20

function TagFields({
  section,
  topic,
  difficulty,
  onChange,
  topics,
  sections,
}: {
  section: Section
  topic: string
  difficulty: Difficulty
  onChange: (v: { section: Section; topic: string; difficulty: Difficulty }) => void
  topics: string[]
  sections: Section[]
}) {
  return (
    <div className="row" style={{ alignItems: 'flex-end' }}>
      <label className="field">
        Section
        <select className="select" value={section} onChange={(e) => onChange({ section: e.target.value as Section, topic, difficulty })}>
          {sections.map((s) => (
            <option key={s} value={s}>
              {sectionName(s)}
            </option>
          ))}
        </select>
      </label>
      <label className="field" style={{ flex: '1 1 200px' }}>
        Topic
        <input className="input" list="bank-topics" required value={topic} onChange={(e) => onChange({ section, topic: e.target.value, difficulty })} />
        <datalist id="bank-topics">
          {topics.map((t) => (
            <option key={t} value={t} />
          ))}
        </datalist>
      </label>
      <label className="field">
        Difficulty
        <select className="select" value={difficulty} onChange={(e) => onChange({ section, topic, difficulty: e.target.value as Difficulty })}>
          {DIFFICULTIES.map((d) => (
            <option key={d} value={d}>
              {DIFFICULTY_LABEL[d]}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}

/**
 * The question bank (ADR-0014): browse by section, topic and difficulty, add your own, have the
 * AI draft more (held for review), and approve or archive. Pickable mode adds selected questions
 * to a draft test.
 */
export function QuestionBankPanel({
  onPick,
  pickLabel,
  initialArea = 'APTITUDE',
}: {
  onPick?: (ids: string[]) => Promise<void>
  pickLabel?: string
  initialArea?: Category
}) {
  const [area, setArea] = useState<Category>(BANK_AREAS.includes(initialArea) ? initialArea : 'APTITUDE')
  const [overview, setOverview] = useState<BankOverview | null>(null)
  const [section, setSection] = useState<Section | ''>('')
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('')
  const [topic, setTopic] = useState('')
  const [pictures, setPictures] = useState(false)
  const [status, setStatus] = useState<BankStatus>('ACTIVE')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<{ items: BankQuestion[]; total: number } | null>(null)
  const [showAnswers, setShowAnswers] = useState(false)
  const [editing, setEditing] = useState<string | 'new' | null>(null)
  const [tags, setTags] = useState<{ section: Section; topic: string; difficulty: Difficulty }>({
    section: sectionsFor(area)[0],
    topic: '',
    difficulty: 'EASY',
  })
  const [drafting, setDrafting] = useState(false)
  const [draftCount, setDraftCount] = useState(5)
  const [selected, setSelected] = useState<string[]>([])
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    listBank({ area, section: section || undefined, difficulty: difficulty || undefined, topic: topic || undefined, pictures, status, q: q.trim() || undefined, page, size: PAGE })
      .then(setResult)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the bank'))
  }, [area, section, difficulty, topic, pictures, status, q, page])
  useEffect(() => {
    const timer = window.setTimeout(load, 200)
    return () => window.clearTimeout(timer)
  }, [load])
  const loadOverview = useCallback(() => {
    getBankOverview(area).then(setOverview).catch(() => undefined)
  }, [area])
  useEffect(loadOverview, [loadOverview])

  const topics = (overview?.topics ?? []).filter((t) => !section || t.section === section)
  const count = (s: Section, d: Difficulty) => overview?.counts.find((c) => c.area === area && c.section === s && c.difficulty === d)?.count ?? 0

  function switchArea(next: Category) {
    setArea(next)
    setSection('')
    setTopic('')
    setPage(0)
    setSelected([])
    setTags({ section: sectionsFor(next)[0], topic: '', difficulty: 'EASY' })
  }

  async function act(action: () => Promise<unknown>, done: string) {
    setError(null)
    try {
      await action()
      setMessage(done)
      load()
      loadOverview()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  async function draft() {
    if (!tags.topic.trim()) {
      setError('Enter a topic for the AI to draft, e.g. "Percentages" or "Data interpretation".')
      return
    }
    setDrafting(true)
    await act(async () => {
      const r = await draftBankQuestions({ area, ...tags, topic: tags.topic.trim(), count: draftCount })
      setStatus('REVIEW')
      setMessage(`Drafted ${r.added}. They're under “Waiting for review” — check each one and approve it before it can be used.`)
    }, '')
    setDrafting(false)
  }

  return (
    <div className="stack">
      <div className="row" style={{ gap: 6, flexWrap: 'wrap' }} role="group" aria-label="Question bank area">
        {BANK_AREAS.map((a) => (
          <button key={a} type="button" className={`chip${a === area ? ' chip-on' : ''}`} aria-pressed={a === area} onClick={() => switchArea(a)}>
            {CATEGORY_LABEL[a]}
          </button>
        ))}
      </div>
      {!onPick && overview && (
        <Card>
          <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <table className="bank-grid" aria-label="Questions in the bank">
              <thead>
                <tr>
                  <th style={{ textAlign: 'left' }}>{CATEGORY_LABEL[area]}</th>
                  {DIFFICULTIES.map((d) => (
                    <th key={d} className={`diff-${d}`}>
                      {DIFFICULTY_LABEL[d]}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {sectionsFor(area).map((s) => (
                  <tr key={s}>
                    <td style={{ textAlign: 'left' }}>{sectionName(s)}</td>
                    {DIFFICULTIES.map((d) => (
                      <td key={d}>{count(s, d)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="muted" style={{ maxWidth: 380, fontSize: 13, margin: 0 }}>
              {area === 'APTITUDE'
                ? 'Modelled on campus tests (TCS NQT, Infosys, Wipro, Cognizant, Accenture): numerical ability with charts and tables, logical reasoning with picture puzzles, and verbal ability.'
                : `${CATEGORY_LABEL[area]} questions in three experience bands: fundamentals for freshers, applied for 1–3 years, and advanced for 3+ years. Many include code to read.`}
            </p>
          </div>
          {overview.guide.length > 0 && (
            <details className="topic-guide">
              <summary>
                Topic guide — what each of the {overview.guide.length} {area === 'APTITUDE' ? '' : `${CATEGORY_LABEL[area]} `}topics covers
              </summary>
              <table className="topic-guide-table" aria-label="Topic guide">
                <thead>
                  <tr>
                    <th>Topic</th>
                    <th>What it covers</th>
                    <th>Example</th>
                    <th>Easy / Medium / Hard</th>
                  </tr>
                </thead>
                <tbody>
                  {overview.guide.map((t) => (
                    <tr key={t.id}>
                      <td>
                        <button type="button" className="linklike" onClick={() => { setSection(t.section); setTopic(t.name); setPage(0) }}>
                          {t.name}
                        </button>
                        <div className="muted" style={{ fontSize: 11 }}>{t.level ? `${t.sectionLabel} · ${t.level}` : t.sectionLabel}</div>
                      </td>
                      <td>{t.covers || '—'}</td>
                      <td className="muted">{t.example || '—'}</td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        {t.easy} / {t.medium} / {t.hard}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          )}
        </Card>
      )}
      <Card className="stack">
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          <select className="select" aria-label="Section" value={section} onChange={(e) => { setSection(e.target.value as Section | ''); setTopic(''); setPage(0) }}>
            <option value="">All sections</option>
            {sectionsFor(area).map((s) => (
              <option key={s} value={s}>
                {sectionName(s)}
              </option>
            ))}
          </select>
          <select className="select" aria-label="Topic" value={topic} onChange={(e) => { setTopic(e.target.value); setPage(0) }}>
            <option value="">All topics</option>
            {topics.map((t) => (
              <option key={`${t.section}-${t.topic}`} value={t.topic}>
                {t.topic} ({t.count})
              </option>
            ))}
          </select>
          <select className="select" aria-label="Difficulty" value={difficulty} onChange={(e) => { setDifficulty(e.target.value as Difficulty | ''); setPage(0) }}>
            <option value="">Any difficulty</option>
            {DIFFICULTIES.map((d) => (
              <option key={d} value={d}>
                {DIFFICULTY_LABEL[d]}
              </option>
            ))}
          </select>
          {!onPick && (
            <select className="select" aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value as BankStatus); setPage(0) }}>
              <option value="ACTIVE">Approved</option>
              <option value="REVIEW">Waiting for review</option>
              <option value="ARCHIVED">Archived</option>
            </select>
          )}
          <label className="row" style={{ gap: 6 }}>
            <input type="checkbox" checked={pictures} onChange={(e) => { setPictures(e.target.checked); setPage(0) }} /> With pictures only
          </label>
          <input className="input" type="search" aria-label="Search questions" placeholder="Search" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} style={{ maxWidth: 200 }} />
          <label className="row" style={{ gap: 6 }}>
            <input type="checkbox" checked={showAnswers} onChange={(e) => setShowAnswers(e.target.checked)} /> Show answers
          </label>
        </div>
        {!onPick && (
          <div className="row" style={{ gap: 8 }}>
            <Button size="sm" variant="secondary" onClick={() => setEditing(editing === 'new' ? null : 'new')}>
              + Add a question
            </Button>
            <span className="muted" style={{ fontSize: 13 }}>or ✨ draft with AI:</span>
            <Button size="sm" variant="secondary" disabled={drafting} onClick={() => void draft()}>
              {drafting ? 'Drafting…' : `Draft ${draftCount} with AI`}
            </Button>
            <input className="input" type="number" min={1} max={10} aria-label="How many to draft" value={draftCount} onChange={(e) => setDraftCount(Number(e.target.value))} style={{ width: 70 }} />
            <span className="muted" style={{ fontSize: 13 }}>using the section, topic and difficulty below</span>
          </div>
        )}
        {!onPick && <TagFields {...tags} sections={sectionsFor(area)} topics={(overview?.topics ?? []).map((t) => t.topic)} onChange={setTags} />}
        {message && <div className="alert alert-info">{message}</div>}
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        {editing === 'new' && (
          <QuestionForm
            onCancel={() => setEditing(null)}
            onSave={async (question: QuestionInput) => {
              if (!tags.topic.trim()) throw new Error('Enter a topic above (e.g. Percentages).')
              await createBankQuestion({ area, ...tags, topic: tags.topic.trim(), question })
              setEditing(null)
              setMessage('Added to the bank.')
              load()
              loadOverview()
            }}
          />
        )}
        {onPick && selected.length > 0 && (
          <div className="row">
            <Button onClick={async () => { await onPick(selected); setSelected([]) }}>{pickLabel ?? 'Add'} {selected.length} selected</Button>
          </div>
        )}
        {result && (
          <span className="muted" style={{ fontSize: 13 }} role="status">
            {result.total} question{result.total === 1 ? '' : 's'}
          </span>
        )}
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {result?.items.map((b) => (
            <li key={b.id} className="stack" style={{ gap: 6, padding: '10px 0', borderTop: '1px solid var(--color-border, #e2e8f0)' }}>
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <label className="row" style={{ gap: 8 }}>
                  {onPick && (
                    <input
                      type="checkbox"
                      aria-label={`Select: ${b.prompt.slice(0, 40)}`}
                      checked={selected.includes(b.id)}
                      onChange={(e) => setSelected((s) => (e.target.checked ? [...s, b.id] : s.filter((x) => x !== b.id)))}
                    />
                  )}
                  <span className="muted" style={{ fontSize: 13 }}>
                    {b.sectionLabel} · {b.topic} · <span className={`diff-${b.difficulty}`}>{DIFFICULTY_LABEL[b.difficulty]}</span> · {b.points} pt
                    {b.timesUsed > 0 ? ` · used ${b.timesUsed}×` : ''}
                  </span>
                  {b.source !== 'BUILT_IN' && <Badge tone={b.source === 'AI' ? 'primary' : 'neutral'}>{SOURCE_LABEL[b.source]}</Badge>}
                </label>
                {!onPick && (
                  <span className="row" style={{ gap: 6 }}>
                    {b.status === 'REVIEW' && (
                      <Button size="sm" onClick={() => void act(() => approveBankQuestion(b.id), 'Approved — it can now be used in tests.')}>
                        Approve
                      </Button>
                    )}
                    <Button size="sm" variant="ghost" onClick={() => setEditing(editing === b.id ? null : b.id)}>
                      Edit
                    </Button>
                    {b.status !== 'ARCHIVED' && (
                      <Button size="sm" variant="ghost" onClick={() => void act(() => archiveBankQuestion(b.id), 'Archived.')}>
                        Archive
                      </Button>
                    )}
                  </span>
                )}
              </div>
              {editing === b.id ? (
                <QuestionForm
                  initial={{ ...b, id: b.id, position: 0, aiDrafted: b.source === 'AI', section: b.section, topic: b.topic, difficulty: b.difficulty }}
                  onCancel={() => setEditing(null)}
                  onSave={async (question) => {
                    await updateBankQuestion(b.id, { area: b.area, section: b.section, topic: b.topic, difficulty: b.difficulty, question })
                    setEditing(null)
                    setMessage(b.status === 'REVIEW' ? 'Saved and approved.' : 'Saved.')
                    load()
                    loadOverview()
                  }}
                />
              ) : (
                <QuestionPreview question={b} showAnswer={showAnswers || b.status === 'REVIEW'} />
              )}
            </li>
          ))}
        </ul>
        {result && result.total > PAGE && (
          <div className="row" style={{ gap: 8 }}>
            <Button size="sm" variant="ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>
              ← Previous
            </Button>
            <span className="muted" style={{ fontSize: 13 }}>
              Page {page + 1} of {Math.ceil(result.total / PAGE)}
            </span>
            <Button size="sm" variant="ghost" disabled={(page + 1) * PAGE >= result.total} onClick={() => setPage(page + 1)}>
              Next →
            </Button>
          </div>
        )}
      </Card>
    </div>
  )
}
