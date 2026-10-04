import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { addToOpening, listCandidateOpenings, listJobs, type CandidateOpening, type Job, type Stage } from '../api/tracker'
import { Badge, Button } from './ui'
import { useStages } from './useStages'

/**
 * The openings this candidate is in, across clients, and "Add to another opening" (PIPE-13, PIPE-14).
 * Same candidate record, so profile, résumés and documents carry over; stage and notes stay per opening.
 */
export function OpeningsSection({
  applicationId,
  candidateName,
  canEdit,
  canOpenJobs,
  onAdded,
}: {
  applicationId: string
  candidateName: string
  canEdit: boolean
  canOpenJobs: boolean
  onAdded: () => void
}) {
  const stages = useStages()
  const [openings, setOpenings] = useState<CandidateOpening[]>([])
  const [jobs, setJobs] = useState<Job[] | null>(null)
  const [adding, setAdding] = useState(false)
  const [jobId, setJobId] = useState('')
  const [stage, setStage] = useState<Stage>('SOURCED')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<{ text: string; jobId: string; applicationId: string } | null>(null)

  const load = useCallback(() => {
    listCandidateOpenings(applicationId).then(setOpenings).catch(() => undefined)
  }, [applicationId])
  useEffect(load, [load])

  function startAdding() {
    setAdding(true)
    setError(null)
    if (!jobs) listJobs().then(setJobs).catch(() => setJobs([]))
  }

  /** Open openings the candidate isn't in yet, grouped by client. */
  const groups = useMemo(() => {
    const taken = new Set(openings.map((o) => o.jobId))
    const byClient = new Map<string, Job[]>()
    for (const j of jobs ?? []) {
      if (j.status === 'CLOSED' || taken.has(j.id)) continue
      const key = j.client?.name ?? 'CodeWalnut (internal)'
      byClient.set(key, [...(byClient.get(key) ?? []), j])
    }
    return [...byClient.entries()].sort(([a], [b]) => a.localeCompare(b))
  }, [jobs, openings])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!jobId) return
    setBusy(true)
    setError(null)
    try {
      const added = await addToOpening(applicationId, { jobId, stage, note: note.trim() })
      const job = jobs?.find((j) => j.id === jobId)
      setNotice({
        text: `${candidateName} added to ${added.jobTitle}${job?.client ? ` (${job.client.name})` : ''}.`,
        jobId: added.jobId,
        applicationId: added.id,
      })
      setAdding(false)
      setJobId('')
      setNote('')
      setStage('SOURCED')
      load()
      onAdded()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add to that opening')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="stack" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>Openings</strong>
        {canEdit && !adding && (
          <Button size="sm" variant="secondary" onClick={startAdding}>
            Add to another opening
          </Button>
        )}
      </div>
      {notice && (
        <div className="alert alert-info">
          {notice.text}{' '}
          {canOpenJobs && <Link to={`/jobs/${notice.jobId}?candidate=${notice.applicationId}`}>Open it</Link>}
        </div>
      )}
      {openings.length > 0 && (
        <ul style={{ margin: 0, paddingLeft: 18 }} aria-label="Openings for this candidate">
          {openings.map((o) => (
            <li key={o.applicationId}>
              {o.current || !canOpenJobs ? (
                <span>{o.jobTitle}</span>
              ) : (
                <Link to={`/jobs/${o.jobId}?candidate=${o.applicationId}`}>{o.jobTitle}</Link>
              )}
              <span className="muted"> · {o.clientName ?? 'Internal'} · {o.stageLabel}</span>{' '}
              {o.current && <Badge tone="primary">This one</Badge>}
              {o.jobStatus === 'CLOSED' && <Badge>Closed</Badge>}
            </li>
          ))}
        </ul>
      )}
      {adding && (
        <form className="stack" aria-label="Add to another opening" onSubmit={onSubmit} style={{ gap: 8 }}>
          {error && (
            <div role="alert" className="alert alert-error">
              {error}
            </div>
          )}
          {jobs && groups.length === 0 && <span className="muted">No other open openings. Create one under Openings first.</span>}
          <label className="field">
            Opening
            <select className="select" required value={jobId} onChange={(e) => setJobId(e.target.value)} disabled={!jobs}>
              <option value="">{jobs ? 'Choose an opening…' : 'Loading…'}</option>
              {groups.map(([client, list]) => (
                <optgroup key={client} label={client}>
                  {list.map((j) => (
                    <option key={j.id} value={j.id}>
                      {j.title}
                      {j.status === 'ON_HOLD' ? ' (on hold)' : ''}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </label>
          <label className="field">
            Starting stage
            <select className="select" value={stage} onChange={(e) => setStage(e.target.value as Stage)}>
              {stages
                .filter((s) => !s.exit)
                .map((s) => (
                  <option key={s.key} value={s.key}>
                    {s.label}
                  </option>
                ))}
            </select>
          </label>
          <label className="field">
            Why they fit (optional, internal)
            <input className="input" value={note} maxLength={5000} onChange={(e) => setNote(e.target.value)} placeholder="e.g. Strong Spring Boot, open to Pune" />
          </label>
          <span className="muted" style={{ fontSize: 12 }}>
            Same candidate: profile, résumés and documents carry over. Stage, notes, tests and interviews are kept per opening.
          </span>
          <div className="row">
            <Button type="submit" size="sm" disabled={busy || !jobId}>
              {busy ? 'Adding…' : 'Add to opening'}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setAdding(false)}>
              Cancel
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}
