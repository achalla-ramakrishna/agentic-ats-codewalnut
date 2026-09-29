import { useCallback, useEffect, useState, type FormEvent } from 'react'
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
import { CandidateDrawer } from '../components/CandidateDrawer'
import { ImportCandidates } from '../components/ImportCandidates'
import { JobDetailsEditor } from '../components/JobDetailsEditor'
import { StageSelect } from '../components/StageSelect'
import { Button, Card, PageHeader } from '../components/ui'
import { useOpenFromQuery } from '../components/useOpenFromQuery'
import { useStages } from '../components/useStages'
import '../components/tracker.css'

function AddCandidateForm({ jobId, onAdded, onCancel }: { jobId: string; onAdded: () => void; onCancel: () => void }) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [stage, setStage] = useState<Stage>('INTERVIEWED')
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
  const [panel, setPanel] = useState<'none' | 'add' | 'import'>('none')
  const [open, setOpen] = useState<ApplicationRow | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    getJob(id).then(setJob).catch((e: unknown) => setError(e instanceof Error ? e.message : 'Not found'))
    listApplications(id).then(setRows).catch(() => setRows([]))
  }, [id])
  useEffect(load, [load])
  useOpenFromQuery(rows, setOpen)

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
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not change the stage')
    }
  }

  if (error && !job) return <p role="alert" className="alert alert-error">{error}</p>
  if (!job) return <p className="muted">Loading…</p>

  const visible = (rows ?? []).filter((r) => !filter || r.stage === filter)

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
                      <button type="button" className="row-link" onClick={() => setOpen(r)}>
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
          key={open.id}
          row={open}
          canEdit={canEdit}
          onClose={() => setOpen(null)}
          onChanged={load}
        />
      )}
    </div>
  )
}
