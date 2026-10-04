import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { HIRING_TYPES, createClient, createJob, listClients, listJobs, type Client, type HiringType, type Job } from '../api/tracker'
import { useMe } from '../auth/AuthContext'
import { StageBar } from '../components/StageBar'
import { Badge, Button, Card, PageHeader } from '../components/ui'
import '../components/tracker.css'

function NewJobForm({ onCreated }: { onCreated: (job: Job) => void }) {
  const [clients, setClients] = useState<Client[]>([])
  const [title, setTitle] = useState('')
  const [hiringType, setHiringType] = useState<HiringType>('CLIENT_DEPLOYED')
  const [clientId, setClientId] = useState('')
  const [newClient, setNewClient] = useState('')
  const [openings, setOpenings] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listClients().then(setClients).catch(() => undefined)
  }, [])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      let cid = clientId || undefined
      if (hiringType !== 'INTERNAL' && !cid && newClient.trim()) {
        cid = (await createClient(newClient.trim())).id
      }
      const job = await createJob({
        title: title.trim(),
        hiringType,
        clientId: hiringType === 'INTERNAL' ? undefined : cid,
        openings: openings ? Number(openings) : undefined,
      })
      onCreated(job)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create the opening')
    }
  }

  const needsClient = hiringType !== 'INTERNAL'
  return (
    <Card>
      <form className="stack" onSubmit={onSubmit} aria-label="New opening">
        <h2>New opening</h2>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field">
            Title
            <input className="input" required value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Blend – Interns" />
          </label>
          <label className="field">
            Type
            <select className="input" value={hiringType} onChange={(e) => setHiringType(e.target.value as HiringType)}>
              {HIRING_TYPES.map((h) => (
                <option key={h.key} value={h.key}>
                  {h.label}
                </option>
              ))}
            </select>
          </label>
          {needsClient && (
            <label className="field">
              Client
              <select className="input" value={clientId} onChange={(e) => setClientId(e.target.value)}>
                <option value="">+ New client…</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          {needsClient && !clientId && (
            <label className="field">
              New client name
              <input className="input" value={newClient} onChange={(e) => setNewClient(e.target.value)} placeholder="e.g. Blend" />
            </label>
          )}
          <label className="field">
            How many needed
            <input className="input" type="number" min={1} value={openings} onChange={(e) => setOpenings(e.target.value)} style={{ width: 110 }} />
          </label>
        </div>
        <div>
          <Button type="submit" disabled={!title.trim() || (needsClient && !clientId && !newClient.trim())}>
            Create opening
          </Button>
        </div>
      </form>
    </Card>
  )
}

export function JobsPage() {
  const me = useMe()
  const canManage = me.capabilities.includes('MANAGE_JOBS')
  const [jobs, setJobs] = useState<Job[] | null>(null)
  const [showNew, setShowNew] = useState(false)

  const load = useCallback(() => {
    listJobs().then(setJobs).catch(() => setJobs([]))
  }, [])
  useEffect(load, [load])

  return (
    <div className="stack">
      <PageHeader
        title="Openings"
        description="Every role you're hiring for, for CodeWalnut or a client."
        actions={canManage && !showNew ? <Button onClick={() => setShowNew(true)}>New opening</Button> : undefined}
      />
      {showNew && (
        <NewJobForm
          onCreated={() => {
            setShowNew(false)
            load()
          }}
        />
      )}
      <Card>
        {!jobs && <p className="muted">Loading…</p>}
        {jobs && jobs.length === 0 && <p className="muted">No openings yet.</p>}
        {jobs && jobs.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Opening</th>
                  <th>Client</th>
                  <th>Type</th>
                  <th>Candidates</th>
                  <th style={{ width: '30%' }}>Pipeline</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <Link to={`/jobs/${job.id}`} style={{ fontWeight: 600 }}>
                        {job.title}
                      </Link>
                    </td>
                    <td>{job.client?.name ?? '—'}</td>
                    <td className="muted">{job.hiringTypeLabel}</td>
                    <td>
                      {job.total}
                      {job.openings ? <span className="muted"> / {job.openings} needed</span> : null}
                    </td>
                    <td>
                      <StageBar job={job} />
                    </td>
                    <td>
                      <Badge tone={job.status === 'OPEN' ? 'primary' : 'neutral'}>{job.status.replace('_', ' ').toLowerCase()}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
