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
import { useMe } from '../auth/AuthContext'
import { CandidateDrawer, type DrawerTab } from '../components/CandidateDrawer'
import { TEMPLATE_FOR_STAGE } from '../components/emailTemplates'
import { ImportCandidates } from '../components/ImportCandidates'
import { JobDetailsEditor } from '../components/JobDetailsEditor'
import { StageSelect } from '../components/StageSelect'
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
  const [panel, setPanel] = useState<'none' | 'add' | 'import'>('none')
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
  }, [id])
  useEffect(load, [load])
  const openDrawer = useCallback((row: ApplicationRow, tab: DrawerTab = 'profile', template?: string) => {
    setOpen(row)
    setOpenTab(tab)
    setOpenTemplate(template)
  }, [])
  useOpenFromQuery(rows, openDrawer)

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

  const visible = (rows ?? []).filter((r) => (!filter || r.stage === filter) && matchesSearch(r, query))
  const filtering = query.trim() !== '' || filter !== null


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
          canEdit ? (
            <>
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
          ) : undefined
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
      {canEdit && <JobDetailsEditor job={job} onSaved={load} />}
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
          placeholder="Search by name, email or phone  ( / )"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setQuery('')
            if (e.key === 'Enter' && visible.length === 1) openDrawer(visible[0])
          }}
          style={{ flex: '1 1 320px', maxWidth: 480 }}
        />
        {rows && filtering && (
          <span className="muted" role="status" style={{ fontSize: 14 }}>
            Showing {visible.length} of {rows.length}
            {visible.length === 1 && query.trim() ? ' · press Enter to open' : ''}
          </span>
        )}
      </div>
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
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                setQuery('')
                setFilter(null)
              }}
            >
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
