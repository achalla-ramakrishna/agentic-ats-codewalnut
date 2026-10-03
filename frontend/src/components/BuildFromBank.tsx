import { useEffect, useState, type FormEvent } from 'react'
import {
  DIFFICULTY_LABEL,
  SECTION_LABEL,
  SECTIONS,
  buildFromBank,
  getBankOverview,
  type BankOverview,
  type Difficulty,
  type Order,
  type SectionPlan,
} from '../api/questionBank'
import { Button, Card } from './ui'

const ORDER_LABEL: Record<Order, string> = {
  EASY_FIRST: 'Easy → hard (sections mixed)',
  BY_SECTION: 'Section by section, easy → hard in each',
  SHUFFLED: 'Shuffled',
}

const LEVELS: { key: keyof Omit<SectionPlan, 'section'>; difficulty: Difficulty }[] = [
  { key: 'easy', difficulty: 'EASY' },
  { key: 'medium', difficulty: 'MEDIUM' },
  { key: 'hard', difficulty: 'HARD' },
]

/** Build a test paper from the bank: a preset or your own mix of sections and difficulty (ADR-0014). */
export function BuildFromBank({ onBuilt, onCancel }: { onBuilt: (assessmentId: string) => void; onCancel: () => void }) {
  const [overview, setOverview] = useState<BankOverview | null>(null)
  const [title, setTitle] = useState('Aptitude test for freshers')
  const [duration, setDuration] = useState(25)
  const [pass, setPass] = useState(50)
  const [order, setOrder] = useState<Order>('BY_SECTION')
  const [plans, setPlans] = useState<SectionPlan[]>(SECTIONS.map((section) => ({ section, easy: 0, medium: 0, hard: 0 })))
  const [preset, setPreset] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getBankOverview()
      .then((o) => {
        setOverview(o)
        const quick = o.presets.find((p) => p.id === 'quick')
        if (quick) choose(quick.id, o)
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the bank'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function choose(id: string, o = overview) {
    setPreset(id)
    const p = o?.presets.find((x) => x.id === id)
    if (!p) return
    setTitle(p.id === 'quick' ? 'Aptitude screening for freshers' : `Aptitude test — ${p.name}`)
    setDuration(p.durationMinutes)
    setPass(p.passPercent)
    // Keep the pattern's own section order (e.g. TCS: numerical, verbal, reasoning), then any others.
    setPlans([
      ...p.sections,
      ...SECTIONS.filter((section) => !p.sections.some((s) => s.section === section)).map((section) => ({ section, easy: 0, medium: 0, hard: 0 })),
    ])
  }

  const available = (section: string, difficulty: Difficulty) =>
    overview?.counts.find((c) => c.area === 'APTITUDE' && c.section === section && c.difficulty === difficulty)?.count ?? 0
  const total = plans.reduce((n, p) => n + p.easy + p.medium + p.hard, 0)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const built = await buildFromBank({
        title,
        area: 'APTITUDE',
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
    <Card>
      <form className="stack" onSubmit={submit} aria-label="Build a test from the bank">
        <h2 style={{ margin: 0 }}>Build an aptitude test from the bank</h2>
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
                <td style={{ textAlign: 'left' }}>{SECTION_LABEL[p.section]}</td>
                {LEVELS.map((l) => (
                  <td key={l.key}>
                    <input
                      className="input"
                      type="number"
                      min={0}
                      max={available(p.section, l.difficulty)}
                      aria-label={`${SECTION_LABEL[p.section]} ${l.key}`}
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
    </Card>
  )
}
