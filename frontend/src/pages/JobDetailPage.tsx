import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  addCandidate,
  getJob,
  listApplications,
  moveStage,
  updateJob,
  type ApplicationRow,
  type Job,
  type Stage,
} from '../api/tracker'
import { askAssistant, getAssistantStatus, type AssistantPlan } from '../api/assistant'
import { getInsights, type InsightSummary, type InsightsResponse } from '../api/insights'
import { listJobTests, testStatusLabel, type InviteView } from '../api/assessments'
import { useMe } from '../auth/AuthContext'
import { AssistantPlanCard } from '../components/AssistantPlanCard'
import { CandidateDrawer, type DrawerTab } from '../components/CandidateDrawer'
import { TEMPLATE_FOR_STAGE } from '../components/emailTemplates'
import { ImportCandidates } from '../components/ImportCandidates'
import { FitBadge, InsightsPanel } from '../components/InsightsPanel'
import { JobDetailsEditor } from '../components/JobDetailsEditor'
import { ResumeUpload } from '../components/ResumeUpload'
import { StageSelect } from '../components/StageSelect'
import { EditOpeningForm } from '../components/EditOpeningForm'
import { Button, Card, PageHeader } from '../components/ui'
import { useOpenFromQuery } from '../components/useOpenFromQuery'
import { useStages } from '../components/useStages'
import '../components/tracker.css'

/**
 * A search made only of digits (and spaces, +, -, brackets) is a phone number: matched on
 * digits, ignoring a +91 prefix. Otherwise every word must appear in the name or email.
 */
export function matchesSearch(row: ApplicationRow, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  if (/^[\d\s+()-]+$/.test(q)) {
    let digits = q.replace(/\D/g, '')
    if (digits.length === 12 && digits.startsWith('91')) digits = digits.slice(2)
    return digits.length >= 3 && (row.phone ?? '').replace(/\D/g, '').includes(digits)
  }
  return q
    .split(/\s+/)
    .every((word) => row.name.toLowerCase().includes(word) || (row.email ?? '').toLowerCase().includes(word))
}

export type Category = 'strong' | 'good' | 'projects' | 'experience' | 'fresher'

export const CATEGORIES: { key: Category; label: string; test: (i: InsightSummary) => boolean }[] = [
  { key: 'strong', label: 'Strong match (75%+)', test: (i) => (i.fitPercent ?? -1) >= 75 },
  { key: 'good', label: 'Good match (50–74%)', test: (i) => (i.fitPercent ?? -1) >= 50 && (i.fitPercent ?? 0) < 75 },
  { key: 'projects', label: 'Has projects', test: (i) => i.projects > 0 },
  { key: 'experience', label: 'Has work / internship experience', test: (i) => i.experienceMonths > 0 },
  { key: 'fresher', label: 'No work experience yet', test: (i) => i.status === 'DONE' && i.experienceMonths === 0 },
]

/** Résumé-based filters: every chosen category, the skill and the graduation year must match. */
export function matchesInsight(
  insight: InsightSummary | undefined,
  categories: Category[],
  skill: string,
  year: string,
): boolean {
  if (!categories.length && !skill && !year) return true
  if (!insight || insight.status !== 'DONE') return false
  if (!categories.every((c) => CATEGORIES.find((x) => x.key === c)!.test(insight))) return false
  if (skill && !insight.skills.some((s) => s.toLowerCase() === skill.toLowerCase())) return false
  if (year && String(insight.graduationYear ?? '') !== year) return false
  return true
}

function AddCandidateForm({ jobId, onAdded, onCancel }: { jobId: string; onAdded: () => void; onCancel: () => void }) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [stage, setStage] = useState<Stage>('SOURCED')
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await addCandidate(jobId, { name, email: email || undefined, phone: phone || undefined, stage })
      onAdded()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add the candidate')
    }
  }

  return (
    <Card>
      <form className="stack" onSubmit={onSubmit} aria-label="Add candidate">
        <h2>Add a candidate</h2>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field">
            Name
            <input className="input" required value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <label className="field">
            Email
            <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label className="field">
            Phone
            <input className="input" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </label>
          <label className="field">
            Stage
            <StageSelect label="Stage" value={stage} onChange={setStage} />
          </label>
        </div>
        <div className="row">
          <Button type="submit" disabled={!name.trim()}>
            Add
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  )
}

export function JobDetailPage() {
  const { id = '' } = useParams()
  const me = useMe()
  const canEdit = me.capabilities.includes('MANAGE_JOBS')
  const stages = useStages()
  const [job, setJob] = useState<Job | null>(null)
  const [rows, setRows] = useState<ApplicationRow[] | null>(null)
  const [filter, setFilter] = useState<Stage | null>(null)
  const [query, setQuery] = useState('')
  const searchRef = useRef<HTMLInputElement>(null)
  const [assistantOn, setAssistantOn] = useState(false)
  const [plan, setPlan] = useState<AssistantPlan | null>(null)
  const [asking, setAsking] = useState(false)
  const [askError, setAskError] = useState<string | null>(null)
  const [panel, setPanel] = useState<'none' | 'add' | 'import' | 'resumes' | 'edit'>('none')
  const [insights, setInsights] = useState<InsightsResponse | null>(null)
  const [sort, setSort] = useState<'name' | 'match' | 'recent'>('name')
  const [categories, setCategories] = useState<Category[]>([])
  const [skill, setSkill] = useState('')
  const [year, setYear] = useState('')
  const [tests, setTests] = useState<InviteView[]>([])
  const [passedOnly, setPassedOnly] = useState(false)
  const [open, setOpen] = useState<ApplicationRow | null>(null)
  const [openTab, setOpenTab] = useState<DrawerTab>('profile')
  const [openTemplate, setOpenTemplate] = useState<string | undefined>()
  const [emailPrompt, setEmailPrompt] = useState<{ row: ApplicationRow; stageLabel: string; template: string } | null>(null)
  const canMessage = me.capabilities.includes('MESSAGE_CANDIDATES')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    getJob(id).then(setJob).catch((e: unknown) => setError(e instanceof Error ? e.message : 'Not found'))
    listApplications(id).then(setRows).catch(() => setRows([]))
    getInsights(id).then(setInsights).catch(() => setInsights(null))
    listJobTests(id).then(setTests).catch(() => setTests([]))
  }, [id])
  useEffect(load, [load])
  const openDrawer = useCallback((row: ApplicationRow, tab: DrawerTab = 'profile', template?: string) => {
    setOpen(row)
    setOpenTab(tab)
    setOpenTemplate(template)
  }, [])
  useOpenFromQuery(rows, openDrawer)

  // While résumés are being read, refresh the scores every few seconds.
  const reading = insights?.pending ?? 0
  useEffect(() => {
    if (!reading) return
    const timer = window.setInterval(() => {
      getInsights(id).then(setInsights).catch(() => undefined)
    }, 4000)
    return () => window.clearInterval(timer)
  }, [id, reading])

  async function onStage(row: ApplicationRow, stage: Stage) {
    const option = stages.find((s) => s.key === stage)
    let note: string | undefined
    if (stage === 'REJECTED' || stage === 'WITHDRAWN') {
      const reason = window.prompt(`Why is ${row.name} ${option?.label.toLowerCase()}?`)
      if (!reason || !reason.trim()) return
      note = reason.trim()
    }
    setError(null)
    try {
      await moveStage(row.id, stage, note)
      load()
      const template = TEMPLATE_FOR_STAGE[stage]
      setEmailPrompt(template && canMessage ? { row, stageLabel: option?.label ?? stage, template } : null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not change the stage')
    }
  }

  useEffect(() => {
    if (!canEdit) return
    getAssistantStatus()
      .then((s) => setAssistantOn(s.available))
      .catch(() => setAssistantOn(false))
  }, [canEdit])

  async function onAsk() {
    const instruction = query.trim()
    if (!instruction) return
    setAsking(true)
    setAskError(null)
    try {
      setPlan(await askAssistant(id, instruction))
      setQuery('')
    } catch (e) {
      setAskError(e instanceof Error ? e.message : 'The assistant could not help with that')
    } finally {
      setAsking(false)
    }
  }

  // Press "/" anywhere on the page to jump to the search box.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null
      const typing = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT' || target.isContentEditable)
      if (e.key === '/' && !typing) {
        e.preventDefault()
        searchRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  if (error && !job) return <p role="alert" className="alert alert-error">{error}</p>
  if (!job) return <p className="muted">Loading…</p>

  const byApplication = new Map((insights?.insights ?? []).map((i) => [i.applicationId, i]))
  // Newest first from the server: keep each candidate's latest test.
  const latestTest = new Map<string, InviteView>()
  for (const t of tests) if (t.status !== 'CANCELLED' && !latestTest.has(t.applicationId)) latestTest.set(t.applicationId, t)
  const passed = new Set(tests.filter((t) => t.passed).map((t) => t.applicationId))
  const visible = (rows ?? [])
    .filter(
      (r) =>
        (!filter || r.stage === filter) &&
        matchesSearch(r, query) &&
        matchesInsight(byApplication.get(r.id), categories, skill, year) &&
        (!passedOnly || passed.has(r.id)),
    )
    .sort((a, b) => {
      if (sort === 'match') {
        const fa = byApplication.get(a.id)?.fitPercent ?? -1
        const fb = byApplication.get(b.id)?.fitPercent ?? -1
        return fb - fa || a.name.localeCompare(b.name)
      }
      if (sort === 'recent') return b.updatedAt.localeCompare(a.updatedAt)
      return 0
    })
  const filtering = query.trim() !== '' || filter !== null || categories.length > 0 || skill !== '' || year !== '' || passedOnly
  const read = (insights?.insights ?? []).filter((i) => i.status === 'DONE')
  const skillOptions = Array.from(
    read.flatMap((i) => i.skills).reduce((m, s) => m.set(s.toLowerCase(), m.get(s.toLowerCase()) ?? s), new Map<string, string>()).values(),
  ).sort((a, b) => a.localeCompare(b))
  const yearOptions = Array.from(new Set(read.map((i) => i.graduationYear).filter((y): y is number => y != null))).sort()
  const showMatch = !!insights && (insights.available || insights.insights.length > 0)
  const openById = (applicationId: string) => {
    const row = rows?.find((r) => r.id === applicationId)
    if (row) openDrawer(row)
  }
  const clearAll = () => {
    setQuery('')
    setFilter(null)
    setCategories([])
    setSkill('')
    setYear('')
    setPassedOnly(false)
  }


  return (
    <div className="stack">
      <div>
        <Link to="/jobs" className="muted">
          ← Openings
        </Link>
      </div>
      <PageHeader
        title={job.title}
        description={`${job.client ? `${job.client.name} · ` : ''}${job.hiringTypeLabel}${job.openings ? ` · ${job.openings} needed` : ''}`}
        actions={
          <>
            {me.navigation.some((n) => n.key === 'workflow') && (
              <Link className="btn btn-secondary" to={`/workflow?job=${job.id}`}>
                Workflow
              </Link>
            )}
            {me.navigation.some((n) => n.key === 'interviews') && (
              <Link className="btn btn-secondary" to={`/interview-kits/${job.id}`}>
                Interview kit
              </Link>
            )}
            {canEdit && (
              <>
                <Button variant="secondary" onClick={() => setPanel(panel === 'edit' ? 'none' : 'edit')}>
                  Edit opening
                </Button>
                {insights?.available && (
                  <Button variant="secondary" onClick={() => setPanel(panel === 'resumes' ? 'none' : 'resumes')}>
                    Upload résumés
                  </Button>
                )}
                <Button variant="secondary" onClick={() => setPanel(panel === 'import' ? 'none' : 'import')}>
                  Import from spreadsheet
                </Button>
                <Button onClick={() => setPanel(panel === 'add' ? 'none' : 'add')}>Add candidate</Button>
                <select
                  className="select"
                  aria-label="Opening status"
                  value={job.status}
                  onChange={async (e) => {
                    await updateJob(job.id, { status: e.target.value as Job['status'] })
                    load()
                  }}
                >
                  <option value="OPEN">Open</option>
                  <option value="ON_HOLD">On hold</option>
                  <option value="CLOSED">Closed</option>
                </select>
              </>
            )}
          </>
        }
      />
      {message && <div className="alert alert-info">{message}</div>}
      {emailPrompt && (
        <div className="alert alert-info row" style={{ justifyContent: 'space-between' }}>
          <span>
            {emailPrompt.row.name} moved to <strong>{emailPrompt.stageLabel}</strong>. Let them know?
          </span>
          <span className="row" style={{ gap: 8 }}>
            <Button
              size="sm"
              onClick={() => {
                openDrawer(emailPrompt.row, 'candidate', emailPrompt.template)
                setEmailPrompt(null)
              }}
            >
              Email {emailPrompt.row.name.split(' ')[0]}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setEmailPrompt(null)}>
              Not now
            </Button>
          </span>
        </div>
      )}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {panel === 'edit' && (
        <EditOpeningForm
          job={job}
          onCancel={() => setPanel('none')}
          onSaved={(saved) => {
            setJob(saved)
            setPanel('none')
            setMessage('Opening saved.')
            load()
          }}
        />
      )}
      {canEdit && <JobDetailsEditor key={`${job.title}|${job.client?.id ?? ''}`} job={job} onSaved={load} />}
      {panel === 'add' && (
        <AddCandidateForm
          jobId={job.id}
          onCancel={() => setPanel('none')}
          onAdded={() => {
            setPanel('none')
            load()
          }}
        />
      )}
      {panel === 'resumes' && (
        <ResumeUpload jobId={job.id} onProgress={load} onClose={() => setPanel('none')} />
      )}
      {insights && showMatch && (rows?.length ?? 0) > 0 && (
        <InsightsPanel jobId={job.id} data={insights} canEdit={canEdit} onOpen={openById} onChanged={load} />
      )}
      {panel === 'import' && (
        <ImportCandidates
          jobId={job.id}
          onCancel={() => setPanel('none')}
          onDone={(added) => {
            setPanel('none')
            setMessage(`Imported ${added} candidate${added === 1 ? '' : 's'}.`)
            load()
          }}
        />
      )}
      <div className="row" style={{ gap: 12, flexWrap: 'wrap' }}>
        <input
          ref={searchRef}
          className="input"
          type="search"
          aria-label="Search candidates in this opening"
          placeholder={
            assistantOn
              ? 'Search, or ask the AI: “who has worked on Spring Boot projects?” · “sagar, amogh are shortlisted”  ( / )'
              : 'Search by name, email or phone  ( / )'
          }
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setQuery('')
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && assistantOn) {
              e.preventDefault()
              void onAsk()
            } else if (e.key === 'Enter' && visible.length === 1) openDrawer(visible[0])
          }}
          style={{ flex: '1 1 320px', maxWidth: 520 }}
        />
        {assistantOn && (
          <Button variant="secondary" disabled={asking || !query.trim()} onClick={() => void onAsk()} title="Ctrl+Enter">
            {asking ? 'Thinking…' : '✨ Ask AI'}
          </Button>
        )}
        {rows && filtering && (
          <span className="muted" role="status" style={{ fontSize: 14 }}>
            Showing {visible.length} of {rows.length}
            {visible.length === 1 && query.trim() ? ' · press Enter to open' : ''}
          </span>
        )}
      </div>
      {askError && (
        <div role="alert" className="alert alert-error">
          {askError}
        </div>
      )}
      {plan && (
        <AssistantPlanCard
          key={plan.instruction}
          plan={plan}
          onApplied={load}
          onClose={() => setPlan(null)}
          onOpen={openById}
        />
      )}
      <div className="stage-chips" role="group" aria-label="Filter by stage">
        <button type="button" className="stage-chip" aria-pressed={filter === null} onClick={() => setFilter(null)}>
          All <strong>{job.total}</strong>
        </button>
        {stages.map((s) => {
          const n = job.stageCounts[s.key] ?? 0
          if (!n && s.exit) return null
          return (
            <button
              type="button"
              key={s.key}
              className={`stage-chip${s.exit ? ' exit' : ''}`}
              aria-pressed={filter === s.key}
              onClick={() => setFilter(filter === s.key ? null : s.key)}
            >
              {s.label} <strong>{n}</strong>
            </button>
          )
        })}
      </div>
      {(read.length > 0 || passed.size > 0) && (
        <div className="row" role="group" aria-label="Filter by résumé" style={{ gap: 8, flexWrap: 'wrap' }}>
          {passed.size > 0 && (
            <button type="button" className="stage-chip" aria-pressed={passedOnly} onClick={() => setPassedOnly(!passedOnly)}>
              Passed a test <strong>{passed.size}</strong>
            </button>
          )}
          {CATEGORIES.map((c) => {
            const n = read.filter(c.test).length
            const on = categories.includes(c.key)
            return (
              <button
                type="button"
                key={c.key}
                className="stage-chip"
                aria-pressed={on}
                onClick={() => setCategories(on ? categories.filter((x) => x !== c.key) : [...categories, c.key])}
              >
                {c.label} <strong>{n}</strong>
              </button>
            )
          })}
          {skillOptions.length > 0 && (
            <select className="select" aria-label="Filter by skill" value={skill} onChange={(e) => setSkill(e.target.value)}>
              <option value="">Any skill</option>
              {skillOptions.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          )}
          {yearOptions.length > 0 && (
            <select className="select" aria-label="Filter by graduation year" value={year} onChange={(e) => setYear(e.target.value)}>
              <option value="">Any graduation year</option>
              {yearOptions.map((y) => (
                <option key={y} value={String(y)}>
                  {y}
                </option>
              ))}
            </select>
          )}
          <select className="select" aria-label="Sort candidates" value={sort} onChange={(e) => setSort(e.target.value as typeof sort)}>
            <option value="name">Sort: name</option>
            <option value="match">Sort: best match (AI)</option>
            <option value="recent">Sort: recently updated</option>
          </select>
        </div>
      )}
      <Card>
        {!rows && <p className="muted">Loading…</p>}
        {rows && rows.length === 0 && (
          <p className="muted" style={{ margin: 0 }}>
            No candidates yet. Use <strong>Import from spreadsheet</strong> to paste your list.
          </p>
        )}
        {rows && rows.length > 0 && visible.length === 0 && (
          <div className="row" style={{ gap: 12 }}>
            <span className="muted">
              No candidates match{query.trim() ? ` “${query.trim()}”` : ''}
              {filter ? ` in ${stages.find((s) => s.key === filter)?.label ?? filter}` : ''}.
            </span>
            <Button size="sm" variant="ghost" onClick={clearAll}>
              Clear search
            </Button>
          </div>
        )}
        {visible.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>Résumés</th>
                  {showMatch && <th title="Share of the opening’s requirements the résumé shows (AI, advisory)">Match</th>}
                  {tests.length > 0 && <th>Test</th>}
                  <th>Stage</th>
                  <th>Latest note</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <button type="button" className="row-link" onClick={() => openDrawer(r)}>
                        {r.name}
                      </button>
                    </td>
                    <td>{r.email ?? <span className="muted">—</span>}</td>
                    <td>{r.phone ?? <span className="muted">—</span>}</td>
                    <td>
                      <span className="doc-dots">
                        <span className={`doc-dot${r.documents.includes('ORIGINAL_RESUME') ? ' has' : ''}`} title="Original résumé">
                          Orig
                        </span>
                        <span className={`doc-dot${r.documents.includes('CODEWALNUT_RESUME') ? ' has' : ''}`} title="CodeWalnut résumé">
                          CW
                        </span>
                      </span>
                    </td>
                    {showMatch && (
                      <td>
                        <FitBadge insight={byApplication.get(r.id)} />
                      </td>
                    )}
                    {tests.length > 0 && (
                      <td className={latestTest.get(r.id)?.needsNudge ? 'test-nudge' : 'muted'} title={latestTest.get(r.id)?.title}>
                        {latestTest.has(r.id) ? testStatusLabel(latestTest.get(r.id)!) : '—'}
                      </td>
                    )}
                    <td>
                      {canEdit ? (
                        <StageSelect label={`Stage for ${r.name}`} value={r.stage} onChange={(s) => void onStage(r, s)} />
                      ) : (
                        r.stageLabel
                      )}
                    </td>
                    <td className="muted" style={{ maxWidth: 260 }}>
                      {r.lastNote ?? ''}
                    </td>
                    <td className="muted">{new Date(r.updatedAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {open && (
        <CandidateDrawer
          key={`${open.id}-${openTab}-${openTemplate ?? ''}`}
          row={open}
          canEdit={canEdit}
          initialTab={openTab}
          initialTemplate={openTemplate}
          onClose={() => setOpen(null)}
          onChanged={load}
        />
      )}
    </div>
  )
}
