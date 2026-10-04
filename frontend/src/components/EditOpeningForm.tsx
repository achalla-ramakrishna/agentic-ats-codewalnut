import { useEffect, useState, type FormEvent } from 'react'
import { HIRING_TYPES, listClients, updateJob, type Client, type HiringType, type Job } from '../api/tracker'
import { Button, Card } from './ui'

/**
 * Rename an opening and change its client, hiring type or number of openings (TRK-15).
 * The server refuses a client change while candidates are still shared with the current client.
 */
export function EditOpeningForm({ job, onSaved, onCancel }: { job: Job; onSaved: (job: Job) => void; onCancel: () => void }) {
  const [clients, setClients] = useState<Client[]>([])
  const [title, setTitle] = useState(job.title)
  const [hiringType, setHiringType] = useState<HiringType>(job.hiringType)
  const [clientId, setClientId] = useState(job.client?.id ?? '')
  const [openings, setOpenings] = useState(job.openings ? String(job.openings) : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const internal = hiringType === 'INTERNAL'

  useEffect(() => {
    listClients()
      .then(setClients)
      .catch(() => setClients([]))
  }, [])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (!internal && !clientId) {
      setError('Choose the client for this opening.')
      return
    }
    setBusy(true)
    try {
      const saved = await updateJob(job.id, {
        title: title.trim(),
        hiringType,
        ...(internal ? {} : { clientId }),
        ...(openings.trim() ? { openings: Number(openings) } : {}),
      })
      onSaved(saved)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save the opening')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <form className="stack" aria-label="Edit opening" onSubmit={onSubmit} style={{ gap: 12 }}>
        <strong>Edit opening</strong>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <label className="field">
          Name of the opening (candidates see this)
          <input className="input" required maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <div className="row" style={{ alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <label className="field">
            Hiring type
            <select className="select" value={hiringType} onChange={(e) => setHiringType(e.target.value as HiringType)}>
              {HIRING_TYPES.map((h) => (
                <option key={h.key} value={h.key}>
                  {h.label}
                </option>
              ))}
            </select>
          </label>
          {!internal && (
            <label className="field">
              Client
              <select className="select" value={clientId} onChange={(e) => setClientId(e.target.value)}>
                <option value="">Choose a client…</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label className="field">
            People needed
            <input className="input" type="number" min={1} value={openings} onChange={(e) => setOpenings(e.target.value)} style={{ width: 120 }} />
          </label>
        </div>
        {!internal && job.client && clientId && clientId !== job.client.id && (
          <span className="muted" style={{ fontSize: 13 }}>
            Moving to another client works only once no candidates in this opening are shared with {job.client.name}.
          </span>
        )}
        <div className="row">
          <Button type="submit" disabled={busy || !title.trim()}>
            {busy ? 'Saving…' : 'Save'}
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  )
}
