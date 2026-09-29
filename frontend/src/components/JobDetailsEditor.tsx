import { useState, type FormEvent } from 'react'
import { jobLink, updateJob, WORK_MODES, type Job, type WorkMode } from '../api/tracker'
import { Badge, Button, Card } from './ui'

/** Job details shown on the public page, and the on/off switch + copy button for the shareable link. */
export function JobDetailsEditor({ job, onSaved }: { job: Job; onSaved: () => void }) {
  const [editing, setEditing] = useState(!job.description)
  const [title, setTitle] = useState(job.title)
  const [location, setLocation] = useState(job.location ?? '')
  const [workMode, setWorkMode] = useState<WorkMode | ''>(job.workMode ?? '')
  const [employmentType, setEmploymentType] = useState(job.employmentType ?? '')
  const [description, setDescription] = useState(job.description ?? '')
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  async function save(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await updateJob(job.id, {
        title,
        location,
        workMode: workMode || undefined,
        employmentType,
        description,
      })
      setEditing(false)
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
    }
  }

  async function togglePublished() {
    setError(null)
    try {
      await updateJob(job.id, { published: !job.published })
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not change the link')
    }
  }

  const link = job.publicSlug ? jobLink(job.publicSlug) : null

  return (
    <Card className="stack">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2>Job details &amp; share link</h2>
        {!editing && (
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
            Edit details
          </Button>
        )}
      </div>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}

      <div className="row">
        <Badge tone={job.published ? 'primary' : 'neutral'}>{job.published ? 'Link is live' : 'Link is off'}</Badge>
        <Button size="sm" variant={job.published ? 'secondary' : 'primary'} onClick={() => void togglePublished()}>
          {job.published ? 'Turn link off' : 'Turn link on'}
        </Button>
        {job.published && link && (
          <>
            <input className="input" readOnly value={link} aria-label="Job link" style={{ minWidth: 320, flex: 1 }} onFocus={(e) => e.target.select()} />
            <Button
              size="sm"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(link)
                  setCopied(true)
                  setTimeout(() => setCopied(false), 2000)
                } catch {
                  setCopied(false)
                }
              }}
            >
              {copied ? 'Copied' : 'Copy link'}
            </Button>
            <a className="btn btn-ghost btn-sm" href={link} target="_blank" rel="noreferrer">
              Preview
            </a>
          </>
        )}
      </div>
      {job.published && job.status !== 'OPEN' && (
        <div className="alert alert-info">This opening isn't Open, so the page says it's no longer accepting applications.</div>
      )}

      {editing ? (
        <form className="stack" onSubmit={save} aria-label="Job details">
          <div className="row" style={{ alignItems: 'flex-end' }}>
            <label className="field" style={{ flex: 2, minWidth: 220 }}>
              Title (shown to candidates)
              <input className="input" required value={title} onChange={(e) => setTitle(e.target.value)} />
            </label>
            <label className="field">
              Location
              <input className="input" value={location} onChange={(e) => setLocation(e.target.value)} placeholder="e.g. Bengaluru" />
            </label>
            <label className="field">
              Work mode
              <select className="input" value={workMode} onChange={(e) => setWorkMode(e.target.value as WorkMode | '')}>
                <option value="">—</option>
                {WORK_MODES.map((w) => (
                  <option key={w.key} value={w.key}>
                    {w.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Type
              <input className="input" value={employmentType} onChange={(e) => setEmploymentType(e.target.value)} placeholder="e.g. Internship · 6 months" />
            </label>
          </div>
          <label className="field">
            Job description
            <textarea
              className="textarea"
              style={{ minHeight: 220, fontFamily: 'inherit', fontSize: 14 }}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={'About the role…\n\nWhat you will do\n- Build features with React\n- …\n\nWho we are looking for\n- …'}
            />
            <span className="muted" style={{ fontWeight: 400, fontSize: 12 }}>
              Leave a blank line between paragraphs; start lines with "-" for bullet points. The client's name is not shown
              unless you write it here.
            </span>
          </label>
          <div className="row">
            <Button type="submit" disabled={!title.trim()}>
              Save details
            </Button>
            {job.description && (
              <Button variant="ghost" onClick={() => setEditing(false)}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      ) : (
        <div className="muted" style={{ fontSize: 13 }}>
          {[job.location, WORK_MODES.find((w) => w.key === job.workMode)?.label, job.employmentType].filter(Boolean).join(' · ') ||
            'No location or type yet'}
          {' · '}
          {job.description ? `${job.description.length} characters of description` : 'no description yet'}
        </div>
      )}
    </Card>
  )
}
