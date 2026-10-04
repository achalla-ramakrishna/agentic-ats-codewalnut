import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getInterviewGuide, LEVEL_LABEL, type InterviewGuide, type InterviewQuestion, type Level } from '../api/interviewGuide'
import { Badge, Button, Card, PageHeader } from '../components/ui'
import '../components/tracker.css'

const LEVELS: Level[] = ['F', 'J', 'S']
const PICKS_KEY = 'ats.interview-picks'

function loadPicks(): string[] {
  try {
    return JSON.parse(localStorage.getItem(PICKS_KEY) ?? '[]') as string[]
  } catch {
    return []
  }
}

function savePicks(picks: string[]) {
  try {
    localStorage.setItem(PICKS_KEY, JSON.stringify(picks))
  } catch {
    // Picks are a convenience; without storage they last for this visit.
  }
}

const keyOf = (categoryId: string, q: InterviewQuestion) => `${categoryId}:${q.question}`

/**
 * Interview questions for staff who interview (INT-21, INT-22): by role, level, language and
 * category, each with what a strong answer covers and the red flags. Pick questions for an
 * interview, hide the answer guides while sharing a screen, or print.
 */
export function InterviewQuestionsPage() {
  const [params, setParams] = useSearchParams()
  const [guide, setGuide] = useState<InterviewGuide | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState<string | null>(null)
  const [language, setLanguage] = useState('')
  const [showAnswers, setShowAnswers] = useState(true)
  const [onlyPicked, setOnlyPicked] = useState(false)
  const [picks, setPicks] = useState<string[]>(loadPicks)
  const role = params.get('role') ?? ''
  const level = (params.get('level') ?? '') as Level | ''

  useEffect(() => {
    getInterviewGuide()
      .then(setGuide)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the interview questions'))
  }, [])

  function setParam(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
  }

  function togglePick(key: string) {
    setPicks((all) => {
      const next = all.includes(key) ? all.filter((k) => k !== key) : [...all, key]
      savePicks(next)
      return next
    })
  }

  const roleCategories = useMemo(() => guide?.roles.find((r) => r.id === role)?.categories ?? null, [guide, role])
  const languages = useMemo(
    () => [...new Set(guide?.categories.flatMap((c) => c.questions.map((q) => q.language).filter((l): l is string => !!l)) ?? [])],
    [guide],
  )
  const needle = search.trim().toLowerCase()
  const sections = (guide?.categories ?? [])
    .filter((c) => !roleCategories || roleCategories.includes(c.id))
    .filter((c) => !category || c.id === category)
    .map((c) => ({
      ...c,
      questions: c.questions.filter(
        (q) =>
          (!level || q.level === level) &&
          (!language || !q.language || q.language === language) &&
          (!onlyPicked || picks.includes(keyOf(c.id, q))) &&
          (!needle || `${q.question} ${q.topic ?? ''} ${q.strong} ${q.redFlags}`.toLowerCase().includes(needle)),
      ),
    }))
    .filter((c) => c.questions.length > 0)
  const shown = sections.reduce((n, c) => n + c.questions.length, 0)
  const visibleCategories = (guide?.categories ?? []).filter((c) => !roleCategories || roleCategories.includes(c.id))

  return (
    <div className="stack interview-guide">
      <PageHeader
        title="Interview questions"
        description="Questions by role and level, with what a strong answer covers and the red flags. For CodeWalnut interviewers only: candidates and clients never see this page."
        actions={
          <Button variant="secondary" onClick={() => window.print()}>
            Print
          </Button>
        }
      />
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {!guide && !error && <p className="muted">Loading…</p>}
      {guide && (
        <>
          <Card className="stack no-print">
            <details>
              <summary>
                <strong>How to run and score the interview</strong>
              </summary>
              <ol style={{ margin: '8px 0', paddingLeft: 20 }}>
                {guide.howTo.map((h) => (
                  <li key={h}>{h}</li>
                ))}
              </ol>
              <table className="bank-grid" aria-label="Scoring scale" style={{ fontSize: 14 }}>
                <tbody>
                  {guide.scale.map((s) => (
                    <tr key={s.score}>
                      <td>
                        <strong>{s.score}</strong>
                      </td>
                      <td style={{ textAlign: 'left' }}>{s.label}</td>
                      <td style={{ textAlign: 'left' }} className="muted">
                        {s.evidence}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          </Card>
          <Card className="stack no-print" style={{ gap: 10 }}>
            <div className="row" style={{ gap: 10, flexWrap: 'wrap', alignItems: 'flex-end' }}>
              <label className="field" style={{ flex: '1 1 220px' }}>
                Role
                <select
                  className="select"
                  value={role}
                  onChange={(e) => {
                    setParam('role', e.target.value)
                    setCategory(null)
                  }}
                >
                  <option value="">All roles</option>
                  {guide.roles.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field" style={{ flex: '0 1 160px' }}>
                Language
                <select className="select" value={language} onChange={(e) => setLanguage(e.target.value)}>
                  <option value="">Any</option>
                  {languages.map((l) => (
                    <option key={l} value={l}>
                      {l}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field" style={{ flex: '1 1 220px' }}>
                Search
                <input className="input" value={search} placeholder="e.g. hash map, Kubernetes" onChange={(e) => setSearch(e.target.value)} />
              </label>
            </div>
            <div className="row" style={{ gap: 6, flexWrap: 'wrap' }} role="group" aria-label="Level">
              <button type="button" className={`chip${!level ? ' chip-on' : ''}`} onClick={() => setParam('level', '')}>
                All levels
              </button>
              {LEVELS.map((l) => (
                <button key={l} type="button" className={`chip${level === l ? ' chip-on' : ''}`} onClick={() => setParam('level', l)}>
                  {LEVEL_LABEL[l]}
                </button>
              ))}
            </div>
            <div className="row" style={{ gap: 6, flexWrap: 'wrap' }} role="group" aria-label="Category">
              <button type="button" className={`chip${!category ? ' chip-on' : ''}`} onClick={() => setCategory(null)}>
                All categories
              </button>
              {visibleCategories.map((c) => (
                <button key={c.id} type="button" className={`chip${category === c.id ? ' chip-on' : ''}`} onClick={() => setCategory(c.id)}>
                  {c.name}
                </button>
              ))}
            </div>
            <div className="row" style={{ gap: 16, flexWrap: 'wrap' }}>
              <label className="row" style={{ gap: 6 }}>
                <input type="checkbox" checked={showAnswers} onChange={(e) => setShowAnswers(e.target.checked)} />
                Show answer guides (untick while sharing your screen)
              </label>
              <label className="row" style={{ gap: 6 }}>
                <input type="checkbox" checked={onlyPicked} onChange={(e) => setOnlyPicked(e.target.checked)} />
                Only my picks ({picks.length})
              </label>
              {picks.length > 0 && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    setPicks([])
                    savePicks([])
                  }}
                >
                  Clear picks
                </Button>
              )}
              <span className="muted" style={{ fontSize: 13 }}>
                {shown} question{shown === 1 ? '' : 's'}
              </span>
            </div>
          </Card>
          {sections.length === 0 && <p className="muted">No questions match these filters.</p>}
          {sections.map((c) => (
            <section key={c.id} className="stack" style={{ gap: 8 }} aria-label={c.name}>
              <h2 className="section-heading">{c.name}</h2>
              {c.intro && (
                <p className="muted" style={{ margin: 0, fontSize: 14 }}>
                  {c.intro}
                </p>
              )}
              {c.questions.map((q) => {
                const key = keyOf(c.id, q)
                const picked = picks.includes(key)
                return (
                  <Card key={key} className="stack interview-q" style={{ gap: 6 }}>
                    <div className="row" style={{ justifyContent: 'space-between', gap: 8 }}>
                      <span className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
                        <Badge tone="primary">{LEVEL_LABEL[q.level]}</Badge>
                        {q.topic && <span className="muted" style={{ fontSize: 13 }}>{q.topic}</span>}
                        {q.language && <Badge>{q.language}</Badge>}
                      </span>
                      <label className="row no-print" style={{ gap: 4, fontSize: 13 }}>
                        <input type="checkbox" checked={picked} onChange={() => togglePick(key)} aria-label={`Pick: ${q.question}`} />
                        Pick
                      </label>
                    </div>
                    <strong style={{ fontWeight: 600 }}>{q.question}</strong>
                    {showAnswers && (
                      <div className="interview-answers">
                        <div>
                          <span className="muted">Strong answer: </span>
                          {q.strong}
                        </div>
                        <div>
                          <span className="muted">Red flags: </span>
                          {q.redFlags}
                        </div>
                      </div>
                    )}
                  </Card>
                )
              })}
            </section>
          ))}
        </>
      )}
    </div>
  )
}
