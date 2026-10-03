import { useEffect, useState, type FormEvent } from 'react'
import { CATEGORY_LABEL, type Category } from '../api/assessments'
import {
  BANK_AREAS,
  DIFFICULTY_LABEL,
  MIXES,
  SECTION_LABEL,
  sectionName,
  sectionsFor,
  buildFromBank,
  getBankOverview,
  listRoles,
  planTopics,
  type BankOverview,
  type Difficulty,
  type Order,
  type RoleLevel,
  type RoleView,
  type SectionPlan,
} from '../api/questionBank'
import { Button, Card } from './ui'

const ORDER_LABEL: Record<Order, string> = {
  EASY_FIRST: 'Easy → hard (sections mixed)',
  HARD_FIRST: 'Hard → easy (sections mixed)',
  BY_SECTION: 'Section by section, easy → hard in each',
  SHUFFLED: 'Shuffled',
}

const TOPIC_ORDER_LABEL: Record<Order, string> = {
  EASY_FIRST: 'Easy → hard (topics mixed)',
  HARD_FIRST: 'Hard → easy (topics mixed)',
  BY_SECTION: 'Topic by topic, easy → hard in each',
  SHUFFLED: 'Shuffled',
}

const LEVELS: { key: 'easy' | 'medium' | 'hard'; difficulty: Difficulty }[] = [
  { key: 'easy', difficulty: 'EASY' },
  { key: 'medium', difficulty: 'MEDIUM' },
  { key: 'hard', difficulty: 'HARD' },
]

interface BuilderProps {
  onBuilt: (assessmentId: string) => void
  onCancel: () => void
}

interface AreaProps extends BuilderProps {
  area: Category
}

const defaultTitle = (area: Category) => (area === 'APTITUDE' ? 'Aptitude test for freshers' : `${CATEGORY_LABEL[area]} test`)

/** Build a test paper from the bank: by topics and a difficulty mix, or by a pattern / section counts (ADR-0014). */
export function BuildFromBank(props: BuilderProps) {
  const [mode, setMode] = useState<'role' | 'topics' | 'pattern'>('role')
  const [area, setArea] = useState<Category>('APTITUDE')
  return (
    <Card>
      <div className="stack">
        <h2 style={{ margin: 0 }}>Build a test from the question bank</h2>
        <div className="tabs" role="tablist" aria-label="How to build">
          <button type="button" role="tab" aria-selected={mode === 'role'} onClick={() => setMode('role')}>
            By role
          </button>
          <button type="button" role="tab" aria-selected={mode === 'topics'} onClick={() => setMode('topics')}>
            By topics
          </button>
          <button type="button" role="tab" aria-selected={mode === 'pattern'} onClick={() => setMode('pattern')}>
            By pattern or section
          </button>
        </div>
        {mode !== 'role' && (
          <div className="row" style={{ gap: 6, flexWrap: 'wrap' }} role="group" aria-label="Test area">
            {BANK_AREAS.map((a) => (
              <button key={a} type="button" className={`chip${a === area ? ' chip-on' : ''}`} aria-pressed={a === area} onClick={() => setArea(a)}>
                {CATEGORY_LABEL[a]}
              </button>
            ))}
          </div>
        )}
        {mode === 'role' && <ByRole {...props} />}
        {mode === 'topics' && <ByTopics key={area} area={area} {...props} />}
        {mode === 'pattern' && <ByPattern key={area} area={area} {...props} />}
      </div>
    </Card>
  )
}

/** Pick a role and a level; the right mix of areas and bands is filled in (ADR-0015). */
function ByRole({ onBuilt, onCancel }: BuilderProps) {
  const [roles, setRoles] = useState<RoleView[] | null>(null)
  const [roleId, setRoleId] = useState('')
  const [level, setLevel] = useState<RoleLevel['level']>('JUNIOR')
  const [title, setTitle] = useState('')
  const [duration, setDuration] = useState(35)
  const [pass, setPass] = useState(60)
  const [order, setOrder] = useState<Order>('BY_SECTION')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listRoles()
      .then((r) => {
        setRoles(r)
        if (r.length) choose(r[0], 'JUNIOR')
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load roles'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const role = roles?.find((r) => r.id === roleId)
  const current = role?.levels.find((l) => l.level === level) ?? role?.levels[0]

  function choose(r: RoleView, wanted: RoleLevel['level']) {
    const l = r.levels.find((x) => x.level === wanted) ?? r.levels[0]
    setRoleId(r.id)
    setLevel(l.level)
    setTitle(l.preset.name)
    setDuration(l.preset.durationMinutes)
    setPass(l.preset.passPercent)
  }

  const total = current ? current.preset.sections.reduce((n, p) => n + p.easy + p.medium + p.hard, 0) : 0

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!role || !current) return
    setBusy(true)
    setError(null)
    try {
      const built = await buildFromBank({
        title,
        area: role.primary,
        durationMinutes: duration,
        passPercent: pass,
        sections: current.preset.sections,
        order,
      })
      onBuilt(built.summary.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not build the test')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={submit} aria-label="Build a test for a role">
      <p className="muted" style={{ margin: 0, fontSize: 14 }}>
        Pick the role and the candidate's level. The test mixes the right areas — the main stack, what it works with, CS
        fundamentals, system design for seniors and aptitude for freshers.
      </p>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <div className="row" style={{ gap: 6, flexWrap: 'wrap' }} role="group" aria-label="Role">
        {roles?.map((r) => (
          <button key={r.id} type="button" className={`chip${r.id === roleId ? ' chip-on' : ''}`} aria-pressed={r.id === roleId}
            onClick={() => choose(r, level)}>
            {r.name}
          </button>
        ))}
      </div>
      {role && (
        <>
          <p className="muted" style={{ margin: 0, fontSize: 13 }}>{role.summary}</p>
          <div className="row" style={{ gap: 6, flexWrap: 'wrap' }} role="group" aria-label="Level">
            {role.levels.map((l) => (
              <button key={l.level} type="button" className={`chip${l.level === current?.level ? ' chip-on' : ''}`}
                aria-pressed={l.level === current?.level} onClick={() => choose(role, l.level)}>
                {l.label} <span style={{ opacity: 0.75, fontSize: 12 }}>· {l.years}</span>
              </button>
            ))}
          </div>
        </>
      )}
      {current && (
        <table className="bank-grid" aria-label="What the test covers">
          <thead>
            <tr>
              <th style={{ textAlign: 'left' }}>Area</th>
              {LEVELS.map((l) => (
                <th key={l.key} className={`diff-${l.difficulty}`}>
                  {DIFFICULTY_LABEL[l.difficulty]}
                </th>
              ))}
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            {current.preset.sections.map((p) => (
              <tr key={`${p.area}-${p.section}`}>
                <td style={{ textAlign: 'left' }}>
                  {p.area ? CATEGORY_LABEL[p.area] : ''} · {SECTION_LABEL[p.section]}
                </td>
                <td>{p.easy}</td>
                <td>{p.medium}</td>
                <td>{p.hard}</td>
                <td>{p.easy + p.medium + p.hard}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field" style={{ flex: '1 1 260px' }}>
          Title
          <input className="input" required value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <label className="field" style={{ maxWidth: 120 }}>
          Time (min)
          <input className="input" type="number" min={5} max={180} value={duration} onChange={(e) => setDuration(Number(e.target.value))} />
        </label>
        <label className="field" style={{ maxWidth: 120 }}>
          Pass mark (%)
          <input className="input" type="number" min={0} max={100} value={pass} onChange={(e) => setPass(Number(e.target.value))} />
        </label>
        <label className="field" style={{ flex: '1 1 220px' }}>
          Order of questions
          <select className="select" value={order} onChange={(e) => setOrder(e.target.value as Order)}>
            {(Object.keys(ORDER_LABEL) as Order[]).map((o) => (
              <option key={o} value={o}>
                {o === 'BY_SECTION' ? 'Area by area, easy → hard in each' : ORDER_LABEL[o].replace('sections', 'areas')}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="row">
        <Button type="submit" disabled={busy || !current || !title.trim()}>
          {busy ? 'Creating…' : `Create test (${total} questions)`}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

/** Tick the topics, say how many questions each, pick a difficulty mix and an order, and build. */
function ByTopics({ area, onBuilt, onCancel }: AreaProps) {
  const [overview, setOverview] = useState<BankOverview | null>(null)
  const [chosen, setChosen] = useState<string[]>([])
  const [perTopic, setPerTopic] = useState(2)
  const [mix, setMix] = useState(MIXES[0].id)
  const [order, setOrder] = useState<Order>('EASY_FIRST')
  const [title, setTitle] = useState(defaultTitle(area))
  const [duration, setDuration] = useState<number | null>(null)
  const [pass, setPass] = useState(50)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getBankOverview(area)
      .then((o) => setOverview(o))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the bank'))
  }, [area])

  const guide = (overview?.guide ?? []).filter((t) => t.easy + t.medium + t.hard > 0)
  const picked = guide.filter((t) => chosen.includes(t.id))
  const split = MIXES.find((m) => m.id === mix)?.split ?? MIXES[0].split
  const plans = picked.length ? planTopics(picked, perTopic, split) : []
  const total = picked.length * perTopic
  const levels = plans.reduce((n, p) => [n[0] + p.easy, n[1] + p.medium, n[2] + p.hard], [0, 0, 0])
  const minutes = duration ?? Math.max(5, Math.min(180, Math.ceil(total * 1.2)))
  const short = plans.flatMap((p) => {
    const t = picked.find((x) => x.name === p.topic)
    if (!t) return []
    const gaps = (['easy', 'medium', 'hard'] as const).filter((k) => p[k] > t[k])
    return gaps.length ? [`${p.topic} (${gaps.join(', ')})`] : []
  })

  const toggle = (id: string) => setChosen((all) => (all.includes(id) ? all.filter((x) => x !== id) : [...all, id]))
  const setSection = (section: string, on: boolean) => {
    const ids = guide.filter((t) => t.section === section).map((t) => t.id)
    setChosen((all) => (on ? [...new Set([...all, ...ids])] : all.filter((x) => !ids.includes(x))))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const built = await buildFromBank({ title, area, durationMinutes: minutes, passPercent: pass, topics: plans, order })
      onBuilt(built.summary.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not build the test')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={submit} aria-label="Build a test by topics">
      <p className="muted" style={{ margin: 0, fontSize: 14 }}>
        Tick the topics, choose how many questions from each and how hard. Questions are picked for you (least used first) into
        a draft test you can check before sending.
      </p>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {sectionsFor(area).map((section) => {
        const inSection = guide.filter((t) => t.section === section)
        if (!inSection.length) return null
        const all = inSection.every((t) => chosen.includes(t.id))
        return (
          <fieldset key={section} className="topic-pick">
            <legend>
              <label className="topic-pick-all">
                <input type="checkbox" checked={all} onChange={(e) => setSection(section, e.target.checked)} />{' '}
                {sectionName(section)}
              </label>
            </legend>
            <div className="topic-pick-grid">
              {inSection.map((t) => (
                <label key={t.id} className="topic-pick-item" title={t.covers || undefined}>
                  <input type="checkbox" checked={chosen.includes(t.id)} onChange={() => toggle(t.id)} />
                  <span>
                    {t.name}
                    <span className="muted" style={{ fontSize: 11, display: 'block' }}>
                      {t.easy + t.medium + t.hard} questions
                    </span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
        )
      })}
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field" style={{ maxWidth: 160 }}>
          Questions per topic
          <input className="input" type="number" min={1} max={10} value={perTopic}
            onChange={(e) => setPerTopic(Math.max(1, Math.min(10, Number(e.target.value) || 1)))} />
        </label>
        <label className="field" style={{ flex: '1 1 260px' }}>
          Difficulty
          <select className="select" value={mix} onChange={(e) => setMix(e.target.value)}>
            {MIXES.map((m) => (
              <option key={m.id} value={m.id}>
                {m.label}
              </option>
            ))}
          </select>
        </label>
        <label className="field" style={{ flex: '1 1 220px' }}>
          Order of questions
          <select className="select" value={order} onChange={(e) => setOrder(e.target.value as Order)}>
            {(Object.keys(TOPIC_ORDER_LABEL) as Order[]).map((o) => (
              <option key={o} value={o}>
                {TOPIC_ORDER_LABEL[o]}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field" style={{ flex: '1 1 260px' }}>
          Title
          <input className="input" required value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <label className="field" style={{ maxWidth: 120 }}>
          Time (min)
          <input className="input" type="number" min={5} max={180} value={minutes} onChange={(e) => setDuration(Number(e.target.value))} />
        </label>
        <label className="field" style={{ maxWidth: 120 }}>
          Pass mark (%)
          <input className="input" type="number" min={0} max={100} value={pass} onChange={(e) => setPass(Number(e.target.value))} />
        </label>
      </div>
      <p className="muted" style={{ margin: 0, fontSize: 13 }} aria-live="polite">
        {total === 0
          ? 'Tick at least one topic.'
          : `${total} questions from ${picked.length} topic${picked.length === 1 ? '' : 's'}: ${levels[0]} easy, ${levels[1]} medium, ${levels[2]} hard.`}
        {total > 100 && ' A test can have up to 100 questions.'}
      </p>
      {short.length > 0 && (
        <div className="alert alert-info">Not enough questions at some levels for: {short.join('; ')}. Ask for fewer or pick another mix.</div>
      )}
      <div className="row">
        <Button type="submit" disabled={busy || total === 0 || total > 100 || short.length > 0 || !title.trim()}>
          {busy ? 'Creating…' : `Create test (${total} questions)`}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

/** A preset pattern (TCS NQT, Wipro …) or your own easy / medium / hard counts per section. */
function ByPattern({ area, onBuilt, onCancel }: AreaProps) {
  const [overview, setOverview] = useState<BankOverview | null>(null)
  const [title, setTitle] = useState(defaultTitle(area))
  const [duration, setDuration] = useState(25)
  const [pass, setPass] = useState(50)
  const [order, setOrder] = useState<Order>('BY_SECTION')
  const [plans, setPlans] = useState<SectionPlan[]>(sectionsFor(area).map((section) => ({ section, easy: 0, medium: 0, hard: 0 })))
  const [preset, setPreset] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getBankOverview(area)
      .then((o) => {
        setOverview(o)
        const first = o.presets.find((p) => p.id === 'quick') ?? o.presets[0]
        if (first) choose(first.id, o)
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the bank'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function choose(id: string, o = overview) {
    setPreset(id)
    const p = o?.presets.find((x) => x.id === id)
    if (!p) return
    setTitle(p.id === 'quick' ? 'Aptitude screening for freshers' : area === 'APTITUDE' ? `Aptitude test — ${p.name}` : `${p.name} test`)
    setDuration(p.durationMinutes)
    setPass(p.passPercent)
    // Keep the pattern's own section order (e.g. TCS: numerical, verbal, reasoning), then any others.
    setPlans([
      ...p.sections,
      ...sectionsFor(area).filter((section) => !p.sections.some((s) => s.section === section)).map((section) => ({ section, easy: 0, medium: 0, hard: 0 })),
    ])
  }

  const available = (section: string, difficulty: Difficulty) =>
    overview?.counts.find((c) => c.area === area && c.section === section && c.difficulty === difficulty)?.count ?? 0
  const total = plans.reduce((n, p) => n + p.easy + p.medium + p.hard, 0)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const built = await buildFromBank({
        title,
        area,
        durationMinutes: duration,
        passPercent: pass,
        sections: plans.filter((p) => p.easy + p.medium + p.hard > 0),
        order,
      })
      onBuilt(built.summary.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not build the test')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <form className="stack" onSubmit={submit} aria-label="Build a test from the bank">
        <p className="muted" style={{ margin: 0, fontSize: 14 }}>
          Pick a pattern or set your own mix. Questions are chosen at random from the bank (least used first), copied into a
          new draft test you can check before sending.
        </p>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <label className="field">
          Pattern
          <select className="select" value={preset} onChange={(e) => choose(e.target.value)}>
            <option value="">Custom</option>
            {overview?.presets.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        {preset && <p className="muted" style={{ margin: 0, fontSize: 13 }}>{overview?.presets.find((p) => p.id === preset)?.description}</p>}
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field" style={{ flex: '1 1 260px' }}>
            Title
            <input className="input" required value={title} onChange={(e) => setTitle(e.target.value)} />
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
        <table className="bank-grid" aria-label="Questions per section">
          <thead>
            <tr>
              <th style={{ textAlign: 'left' }}>Section</th>
              {LEVELS.map((l) => (
                <th key={l.key} className={`diff-${l.difficulty}`}>
                  {DIFFICULTY_LABEL[l.difficulty]}
                </th>
              ))}
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            {plans.map((p, i) => (
              <tr key={p.section}>
                <td style={{ textAlign: 'left' }}>{sectionName(p.section)}</td>
                {LEVELS.map((l) => (
                  <td key={l.key}>
                    <input
                      className="input"
                      type="number"
                      min={0}
                      max={available(p.section, l.difficulty)}
                      aria-label={`${sectionName(p.section)} ${l.key}`}
                      style={{ width: 70 }}
                      value={p[l.key]}
                      onChange={(e) => {
                        setPreset('')
                        setPlans((all) => all.map((x, j) => (j === i ? { ...x, [l.key]: Math.max(0, Number(e.target.value)) } : x)))
                      }}
                    />
                    <div className="muted" style={{ fontSize: 11 }}>of {available(p.section, l.difficulty)}</div>
                  </td>
                ))}
                <td>{p.easy + p.medium + p.hard}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <label className="field" style={{ maxWidth: 360 }}>
          Order of questions
          <select className="select" value={order} onChange={(e) => setOrder(e.target.value as Order)}>
            {(Object.keys(ORDER_LABEL) as Order[]).map((o) => (
              <option key={o} value={o}>
                {ORDER_LABEL[o]}
              </option>
            ))}
          </select>
        </label>
        <div className="row">
          <Button type="submit" disabled={busy || total === 0 || !title.trim()}>
            {busy ? 'Building…' : `Build test (${total} questions)`}
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </>
  )
}
