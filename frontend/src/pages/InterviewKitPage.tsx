import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { CATEGORY_LABEL } from '../api/assessments'
import {
  generateInterviewKit,
  getInterviewKit,
  primaryArea,
  type KitCoding,
  type KitPage,
  type KitQuestion,
} from '../api/interviewKit'
import { buildFromBank, sectionName } from '../api/questionBank'
import { useMe } from '../auth/AuthContext'
import { Badge, Button, Card, PageHeader } from '../components/ui'
import '../components/tracker.css'

const DIFFICULTY: Record<KitCoding['difficulty'], string> = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' }

function Question({ q, n, guides }: { q: KitQuestion; n: number; guides: boolean }) {
  return (
    <li style={{ marginBottom: 12 }}>
      <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
        <Badge tone={q.source === 'job' ? 'primary' : 'neutral'}>{q.topic}</Badge>
        <span className="muted" style={{ fontSize: 12 }}>
          {q.source === 'job' ? 'From the job description' : q.source}
        </span>
      </div>
      <div style={{ marginTop: 4 }}>
        <strong>Q{n}.</strong> {q.question}
      </div>
      {guides && q.strong && (
        <div style={{ fontSize: 14, marginTop: 4 }}>
          <span style={{ color: 'var(--color-primary)' }}>Strong answer:</span> {q.strong}
        </div>
      )}
      {guides && q.redFlags && (
        <div style={{ fontSize: 14 }}>
          <span style={{ color: 'var(--color-danger, #b42318)' }}>Red flags:</span> {q.redFlags}
        </div>
      )}
    </li>
  )
}

function Problem({ p, n, guides }: { p: KitCoding; n: number; guides: boolean }) {
  return (
    <div className="stack" style={{ gap: 6, borderTop: '1px solid var(--color-border)', paddingTop: 12 }}>
      <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
        <strong>
          {n === 1 ? 'Warm-up' : 'Main problem'}: {p.title}
        </strong>
        <Badge tone={p.difficulty === 'HARD' ? 'danger' : p.difficulty === 'MEDIUM' ? 'primary' : 'neutral'}>{DIFFICULTY[p.difficulty]}</Badge>
        <span className="muted" style={{ fontSize: 13 }}>
          {p.topic}
        </span>
      </div>
      <div style={{ whiteSpace: 'pre-wrap' }}>{p.statement}</div>
      {p.input && (
        <div style={{ fontSize: 14 }}>
          <strong>Input:</strong> <span style={{ whiteSpace: 'pre-wrap' }}>{p.input.trim()}</span>
        </div>
      )}
      {p.output && (
        <div style={{ fontSize: 14 }}>
          <strong>Output:</strong> <span style={{ whiteSpace: 'pre-wrap' }}>{p.output.trim()}</span>
        </div>
      )}
      {p.sampleInput !== null && (
        <div className="row" style={{ gap: 12, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <div>
            <div className="muted" style={{ fontSize: 12 }}>
              Sample input
            </div>
            <pre className="test-code" style={{ margin: 0 }}>
              {p.sampleInput}
            </pre>
          </div>
          <div>
            <div className="muted" style={{ fontSize: 12 }}>
              Sample output
            </div>
            <pre className="test-code" style={{ margin: 0 }}>
              {p.sampleOutput}
            </pre>
          </div>
        </div>
      )}
      {guides && p.approach && (
        <div style={{ fontSize: 14 }}>
          <span style={{ color: 'var(--color-primary)' }}>Constraints and expected approach:</span> {p.approach.trim()}
        </div>
      )}
      {guides && (
        <div style={{ fontSize: 14 }}>
          <span style={{ color: 'var(--color-primary)' }}>What to look for:</span> {p.lookFor}
        </div>
      )}
    </div>
  )
}

/** An opening's interview kit (INT-28…INT-32): what to test and ask, built from its job description. Staff only. */
export function InterviewKitPage() {
  const { jobId = '' } = useParams()
  const me = useMe()
  const navigate = useNavigate()
  const canOpenJobs = me.navigation.some((n) => n.key === 'jobs')
  const [page, setPage] = useState<KitPage | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [guides, setGuides] = useState(true)
  const [level, setLevel] = useState('')
  const [roleId, setRoleId] = useState('')

  useEffect(() => {
    getInterviewKit(jobId)
      .then((p) => {
        setPage(p)
        setLevel(p.kit?.kit.level ?? '')
        setRoleId(p.kit?.kit.roleId ?? '')
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the interview kit'))
  }, [jobId])

  async function generate(overrides: { level?: string; roleId?: string } = {}) {
    setBusy(true)
    setError(null)
    try {
      const p = await generateInterviewKit(jobId, overrides)
      setPage(p)
      setLevel(p.kit?.kit.level ?? '')
      setRoleId(p.kit?.kit.roleId ?? '')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not generate the kit')
    } finally {
      setBusy(false)
    }
  }

  async function createTest() {
    if (!page?.kit) return
    const t = page.kit.kit.test
    setBusy(true)
    setError(null)
    try {
      const built = await buildFromBank({
        title: `${page.jobTitle} — ${t.levelLabel} test`,
        area: primaryArea(t.preset),
        durationMinutes: t.preset.durationMinutes,
        passPercent: t.preset.passPercent,
        sections: t.preset.sections,
        order: 'BY_SECTION',
      })
      navigate(`/tests?test=${built.summary.id}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create the test')
      setBusy(false)
    }
  }

  if (error && !page) {
    return (
      <div role="alert" className="alert alert-error">
        {error}
      </div>
    )
  }
  if (!page) return <p className="muted">Loading…</p>
  const view = page.kit
  const kit = view?.kit
  let questionNo = 0

  return (
    <div className="stack">
      <PageHeader
        title={`Interview kit: ${page.jobTitle}`}
        description={[page.clientName, kit ? kit.levelLabel : null, kit ? kit.roleName : null].filter(Boolean).join(' · ') ||
          'Tests, questions and coding problems for this opening, based on its job description.'}
        actions={
          <div className="row">
            {canOpenJobs && (
              <Link className="btn btn-secondary" to={`/jobs/${page.jobId}`}>
                Open the opening
              </Link>
            )}
            {kit && (
              <Button variant="secondary" onClick={() => window.print()}>
                Print
              </Button>
            )}
          </div>
        }
      />
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {!kit && (
        <Card>
          <div className="stack" style={{ gap: 8 }}>
            <p style={{ margin: 0 }}>
              An interview kit reads the job description and puts together what the panel needs: the online test, interview rounds with
              questions and answer guides, live coding problems and a scorecard.
            </p>
            {!page.hasDescription && (
              <div className="alert alert-info">
                This opening has no job description yet, so the kit can only use the title. Add the description in the opening first for a
                better kit.
              </div>
            )}
            {page.canGenerate ? (
              <div>
                <Button onClick={() => void generate()} disabled={busy}>
                  {busy ? 'Generating…' : 'Generate interview kit'}
                </Button>
              </div>
            ) : (
              <p className="muted" style={{ margin: 0 }}>
                No kit yet. A recruiter can generate one from the opening.
              </p>
            )}
          </div>
        </Card>
      )}
      {view && kit && (
        <>
          {view.outdated && (
            <div className="alert alert-info">
              The job description has changed since this kit was made.{' '}
              {page.canGenerate && (
                <Button size="sm" variant="secondary" onClick={() => void generate({ level, roleId })} disabled={busy}>
                  Regenerate
                </Button>
              )}
            </div>
          )}
          {kit.notes.map((n) => (
            <div key={n} className="alert alert-info">
              {n}
            </div>
          ))}

          <Card>
            <div className="stack" style={{ gap: 10 }}>
              <h3 style={{ margin: 0 }}>What the job asks for</h3>
              {kit.skills.length > 0 ? (
                <div className="row" style={{ gap: 6, flexWrap: 'wrap' }} aria-label="Skills from the job description">
                  {kit.skills.map((s) => (
                    <span key={s.name} className={`chip${s.mustHave ? ' chip-on' : ''}`} title={s.area ?? undefined}>
                      {s.name}
                      {s.mustHave ? '' : ' (nice to have)'}
                    </span>
                  ))}
                </div>
              ) : (
                <span className="muted">No known skills found.</span>
              )}
              {page.canGenerate ? (
                <div className="row" style={{ alignItems: 'flex-end', flexWrap: 'wrap' }}>
                  <label className="field">
                    Level
                    <select className="select" value={level} onChange={(e) => setLevel(e.target.value)}>
                      {page.levels.map((l) => (
                        <option key={l.id} value={l.id}>
                          {l.label}
                          {l.id === kit.detectedLevel ? ' — from the job description' : ''}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="field">
                    Role
                    <select className="select" value={roleId} onChange={(e) => setRoleId(e.target.value)}>
                      {page.roles.map((r) => (
                        <option key={r.id} value={r.id}>
                          {r.label}
                          {r.id === kit.detectedRoleId ? ' — best match' : ''}
                        </option>
                      ))}
                    </select>
                  </label>
                  <Button variant="secondary" onClick={() => void generate({ level, roleId })} disabled={busy}>
                    {busy ? 'Generating…' : 'Regenerate'}
                  </Button>
                </div>
              ) : (
                <span className="muted" style={{ fontSize: 14 }}>
                  Level: {kit.levelLabel} · Role: {kit.roleName}
                </span>
              )}
              <span className="muted" style={{ fontSize: 12 }}>
                Made by {view.generatedBy} on {new Date(view.generatedAt).toLocaleString('en-IN')}. Regenerating picks fresh questions and
                problems. The kit suggests; the panel decides what to ask.
              </span>
            </div>
          </Card>

          <Card>
            <div className="stack" style={{ gap: 8 }}>
              <h3 style={{ margin: 0 }}>1. Online test, before the interviews</h3>
              <p style={{ margin: 0 }}>
                <strong>{kit.test.preset.name}</strong>: {kit.test.preset.description} Pass mark {kit.test.preset.passPercent}%.
              </p>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Area</th>
                      <th>Level</th>
                      <th>Easy</th>
                      <th>Medium</th>
                      <th>Hard</th>
                    </tr>
                  </thead>
                  <tbody>
                    {kit.test.preset.sections.map((s, i) => (
                      <tr key={i}>
                        <td>{s.area ? CATEGORY_LABEL[s.area] : ''}</td>
                        <td>{sectionName(s.section)}</td>
                        <td>{s.easy}</td>
                        <td>{s.medium}</td>
                        <td>{s.hard}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {page.canGenerate && (
                <div>
                  <Button onClick={() => void createTest()} disabled={busy}>
                    Create this test
                  </Button>{' '}
                  <span className="muted" style={{ fontSize: 13 }}>
                    Makes a draft test you can check, then send to candidates.
                  </span>
                </div>
              )}
            </div>
          </Card>

          <label className="row" style={{ gap: 6 }}>
            <input type="checkbox" checked={guides} onChange={(e) => setGuides(e.target.checked)} />
            Show answer guides (untick before sharing your screen)
          </label>

          {kit.rounds.map((r, i) => (
            <Card key={r.name}>
              <div className="stack" style={{ gap: 8 }}>
                <h3 style={{ margin: 0 }}>
                  {i + 2}. {r.name}
                </h3>
                <span className="muted" style={{ fontSize: 14 }}>
                  {r.minutes} minutes · {r.who} · {r.purpose}
                </span>
                {r.coding.map((p, j) => (
                  <Problem key={p.id} p={p} n={j + 1} guides={guides} />
                ))}
                {r.questions.length > 0 && (
                  <>
                    {r.coding.length > 0 && <strong style={{ marginTop: 8 }}>Discussion while they code</strong>}
                    <ol style={{ margin: 0, paddingLeft: 18, listStyle: 'none' }}>
                      {r.questions.map((q) => (
                        <Question key={q.question} q={q} n={++questionNo} guides={guides} />
                      ))}
                    </ol>
                  </>
                )}
              </div>
            </Card>
          ))}

          <Card>
            <div className="stack" style={{ gap: 8 }}>
              <h3 style={{ margin: 0 }}>Scorecard</h3>
              <span className="muted" style={{ fontSize: 14 }}>
                The feedback form after each interview uses these areas: 1 Weak · 2 Below the bar · 3 Meets the bar · 4 Strong.
              </span>
              <div className="table-wrap">
                <table className="table">
                  <tbody>
                    {kit.scorecard.map((s) => (
                      <tr key={s.name}>
                        <td style={{ width: '30%' }}>
                          <strong>{s.name}</strong>
                        </td>
                        <td className="muted">{s.guidance}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p style={{ margin: 0 }}>{kit.hireBar}</p>
            </div>
          </Card>
        </>
      )}
    </div>
  )
}
