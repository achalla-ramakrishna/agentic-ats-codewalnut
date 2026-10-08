import { useEffect, useState, type FormEvent } from 'react'
import { listJobTests, sendTest, testStatusLabel, type AssessmentSummary, type InviteView } from '../api/assessments'
import { getBackgrounds, TRACK_LABEL, type Background, type Track } from '../api/insights'
import { getWhatsAppStatus } from '../api/messages'
import { listApplications, listJobs, type ApplicationRow, type Job } from '../api/tracker'
import { Badge, Button } from './ui'

const OPEN = new Set(['SENT', 'STARTED', 'SUBMITTED'])

/** The résumé background a test is meant for (ASMT-39); aptitude and other tests suit everyone. */
function trackFor(category: AssessmentSummary['category']): Track | null {
  if (category === 'JAVA') return 'JAVA'
  if (category === 'PYTHON') return 'PYTHON'
  if (category === 'JAVASCRIPT' || category === 'NODEJS' || category === 'REACT') return 'MERN'
  return null
}

/**
 * Send a ready test to several candidates of one opening at once. Each candidate gets the usual
 * message with their own link; they sign in with Google using their email to take it (ADR-0011).
 */
export function SendToCandidates({ test, onSent, onClose }: { test: AssessmentSummary; onSent: () => void; onClose: () => void }) {
  const [jobs, setJobs] = useState<Job[]>([])
  const [jobId, setJobId] = useState('')
  const [rows, setRows] = useState<ApplicationRow[] | null>(null)
  const [invites, setInvites] = useState<InviteView[]>([])
  const [backgrounds, setBackgrounds] = useState<Background[]>([])
  const [chosen, setChosen] = useState<string[]>([])
  const [dueDays, setDueDays] = useState(3)
  const [email, setEmail] = useState(true)
  const [whatsApp, setWhatsApp] = useState(false)
  const [waApi, setWaApi] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [outcome, setOutcome] = useState<{ sent: string[]; failed: string[] } | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listJobs()
      .then((all) => setJobs(all.filter((j) => j.status !== 'CLOSED')))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load openings'))
    getWhatsAppStatus()
      .then((s) => setWaApi(s.apiEnabled))
      .catch(() => setWaApi(false))
  }, [])

  useEffect(() => {
    setRows(null)
    setChosen([])
    setOutcome(null)
    if (!jobId) return
    Promise.all([listApplications(jobId), listJobTests(jobId)])
      .then(([apps, tests]) => {
        setRows(apps)
        setInvites(tests)
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load candidates'))
    // The background badges are a help, not a must: without them the list still works.
    getBackgrounds(jobId)
      .then(setBackgrounds)
      .catch(() => setBackgrounds([]))
  }, [jobId])

  const existing = (applicationId: string) =>
    invites.find((i) => i.applicationId === applicationId && i.assessmentId === test.id && OPEN.has(i.status))
  const canSend = (r: ApplicationRow) => !!r.email && !existing(r.id) && r.stage !== 'REJECTED' && r.stage !== 'WITHDRAWN'
  const sendable = (rows ?? []).filter(canSend)
  const track = trackFor(test.category)
  const background = (applicationId: string) => backgrounds.find((b) => b.applicationId === applicationId)
  const matching = track ? sendable.filter((r) => background(r.id)?.track === track) : []
  const unread = backgrounds.filter((b) => !b.read).length

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const targets = (rows ?? []).filter((r) => chosen.includes(r.id))
    const sent: string[] = []
    const failed: string[] = []
    for (const [n, r] of targets.entries()) {
      setBusy(`Sending ${n + 1} of ${targets.length}…`)
      try {
        await sendTest(r.id, { assessmentId: test.id, dueDays, sendEmail: email, sendWhatsApp: whatsApp })
        sent.push(r.name)
      } catch (e) {
        failed.push(`${r.name}: ${e instanceof Error ? e.message : 'could not send'}`)
      }
    }
    setBusy(null)
    setOutcome({ sent, failed })
    setChosen([])
    if (jobId) listJobTests(jobId).then(setInvites).catch(() => undefined)
    if (sent.length) onSent()
  }

  return (
    <form className="stack card" onSubmit={submit} aria-label="Send to candidates" style={{ gap: 10 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h3 style={{ margin: 0 }}>Send “{test.title}” to candidates</h3>
        <Button variant="ghost" size="sm" onClick={onClose}>
          Close
        </Button>
      </div>
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        Each candidate gets a message with their own link. They sign in with Google using the email shown to take the test.
      </p>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <label className="field" style={{ maxWidth: 420 }}>
        Opening
        <select className="select" value={jobId} onChange={(e) => setJobId(e.target.value)}>
          <option value="">Choose an opening…</option>
          {jobs.map((j) => (
            <option key={j.id} value={j.id}>
              {j.title}
              {j.client ? ` · ${j.client.name}` : ''} ({j.total})
            </option>
          ))}
        </select>
      </label>
      {rows && rows.length === 0 && <p className="muted">No candidates in this opening yet.</p>}
      {rows && rows.length > 0 && (
        <>
          <div className="row" style={{ gap: 8 }}>
            <Button size="sm" variant="secondary" disabled={sendable.length === 0} onClick={() => setChosen(sendable.map((r) => r.id))}>
              Select all who can get it ({sendable.length})
            </Button>
            {track && (
              <Button size="sm" variant="secondary" disabled={matching.length === 0} onClick={() => setChosen(matching.map((r) => r.id))}>
                Select everyone with a {TRACK_LABEL[track]} background ({matching.length})
              </Button>
            )}
            {chosen.length > 0 && (
              <Button size="sm" variant="ghost" onClick={() => setChosen([])}>
                Clear
              </Button>
            )}
          </div>
          {track && unread > 0 && (
            <p className="muted" style={{ margin: 0, fontSize: 12 }}>
              {unread} résumé{unread === 1 ? ' hasn’t' : 's haven’t'} been read by the AI yet, so their background is unknown. Open the
              opening → AI suggestions → Analyze résumés to read them.
            </p>
          )}
          <table className="send-list" aria-label="Candidates">
            <tbody>
              {rows.map((r) => {
                const open = existing(r.id)
                const reason = open ? `Already sent · ${testStatusLabel(open)}` : !r.email ? 'No email' : canSend(r) ? null : r.stageLabel
                const bg = background(r.id)
                return (
                  <tr key={r.id}>
                    <td>
                      <label className="row" style={{ gap: 8 }}>
                        <input
                          type="checkbox"
                          aria-label={`Send to ${r.name}`}
                          disabled={!canSend(r)}
                          checked={chosen.includes(r.id)}
                          onChange={(e) => setChosen((c) => (e.target.checked ? [...c, r.id] : c.filter((x) => x !== r.id)))}
                        />
                        <strong>{r.name}</strong>
                      </label>
                    </td>
                    <td className="muted">{r.email ?? '—'}</td>
                    <td>{r.stageLabel}</td>
                    <td>
                      {bg?.track && (
                        <span title={bg.evidence.join(', ')}>
                          <Badge tone={track && bg.track === track ? 'primary' : 'neutral'}>{TRACK_LABEL[bg.track]}</Badge>
                        </span>
                      )}
                    </td>
                    <td>{reason && <Badge tone="neutral">{reason}</Badge>}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          <div className="row" style={{ alignItems: 'flex-end' }}>
            <label className="field" style={{ maxWidth: 140 }}>
              Days to finish
              <input className="input" type="number" min={1} max={30} value={dueDays} onChange={(e) => setDueDays(Math.max(1, Math.min(30, Number(e.target.value) || 1)))} />
            </label>
            <label className="row" style={{ gap: 6 }}>
              <input type="checkbox" checked={email} onChange={(e) => setEmail(e.target.checked)} /> Email
            </label>
            {waApi && (
              <label className="row" style={{ gap: 6 }}>
                <input type="checkbox" checked={whatsApp} onChange={(e) => setWhatsApp(e.target.checked)} /> WhatsApp
              </label>
            )}
          </div>
          {!waApi && (
            <p className="muted" style={{ margin: 0, fontSize: 12 }}>
              To send on WhatsApp without the WhatsApp Business API, use Send test in each candidate’s panel (it opens WhatsApp for you).
            </p>
          )}
          <div className="row">
            <Button type="submit" disabled={!!busy || chosen.length === 0}>
              {busy ?? `Send to ${chosen.length} candidate${chosen.length === 1 ? '' : 's'}`}
            </Button>
          </div>
        </>
      )}
      {outcome && (
        <div className={`alert ${outcome.failed.length ? 'alert-error' : 'alert-info'}`} role="status">
          {outcome.sent.length > 0 && <div>Sent to {outcome.sent.length}: {outcome.sent.join(', ')}.</div>}
          {outcome.failed.length > 0 && (
            <div>
              Not sent:
              <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
                {outcome.failed.map((f) => (
                  <li key={f}>{f}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </form>
  )
}
