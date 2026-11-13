import { useEffect, useState, type FormEvent } from 'react'
import { listJobTests, sendTest, testStatusLabel, type AssessmentSummary, type InviteView } from '../api/assessments'
import { getBackgrounds, TRACK_LABEL, type Background, type Track } from '../api/insights'
import { getGoogleStatus, type GoogleStatus } from '../api/interviews'
import { getWhatsAppStatus } from '../api/messages'
import { ConnectGoogle } from './ConnectGoogle'
import { connectGoogleUrl } from '../api/interviews'
import { addCandidate, listApplications, listJobs, type ApplicationRow, type Job } from '../api/tracker'
import { parseExtraPeople } from './SendAiInterview'
import { BatchSizeInput, useBatchSize } from './BatchSize'
import { Badge, Button } from './ui'

const OPEN = new Set(['SENT', 'STARTED', 'SUBMITTED'])
/** The Opening choice that lists candidates from every open opening. */
const ALL_OPENINGS = '__all__'

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
  // AI interviews get the simple version: everyone listed, search, tick, send.
  const simple = test.category === 'INTERVIEW'
  // An AI interview starts at the opening it was made for (or everyone); other tests ask which opening.
  const [jobId, setJobId] = useState(test.jobId ?? (simple ? ALL_OPENINGS : ''))
  const [rows, setRows] = useState<ApplicationRow[] | null>(null)
  const [invites, setInvites] = useState<InviteView[]>([])
  const [backgrounds, setBackgrounds] = useState<Background[]>([])
  const [chosen, setChosen] = useState<string[]>([])
  const [dueDays, setDueDays] = useState(3)
  const [batch, setBatch] = useBatchSize()
  const [email, setEmail] = useState(true)
  const [whatsApp, setWhatsApp] = useState(false)
  const [waApi, setWaApi] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [outcome, setOutcome] = useState<{ sent: string[]; failed: string[] } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [resumeOnly, setResumeOnly] = useState(false)
  const [hideSent, setHideSent] = useState(!simple)
  const [selectedOnly, setSelectedOnly] = useState(false)
  const [extraText, setExtraText] = useState('')
  // Emails go from the sender's own Gmail, so it must be connected in this sign-in.
  const [google, setGoogle] = useState<GoogleStatus | null>(null)
  useEffect(() => {
    getGoogleStatus()
      .then(setGoogle)
      .catch(() => setGoogle(null))
  }, [])
  const needsGoogle = email && google !== null && !google.mailConnected

  useEffect(() => {
    listJobs()
      .then((all) => setJobs(all.filter((j) => j.status !== 'CLOSED')))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load openings'))
    getWhatsAppStatus()
      .then((s) => setWaApi(s.apiEnabled))
      .catch(() => setWaApi(false))
  }, [])

  // Only "All openings" depends on the list of openings; one opening mustn't reload when it arrives.
  const allJobIds = jobId === ALL_OPENINGS ? jobs.map((j) => j.id).join(',') : ''
  useEffect(() => {
    setRows(null)
    setChosen([])
    setOutcome(null)
    if (!jobId) return
    // "All openings": every open opening's candidates in one list (each row keeps its own opening).
    const ids = jobId === ALL_OPENINGS ? jobs.map((j) => j.id) : [jobId]
    Promise.all(ids.map((id) => Promise.all([listApplications(id), listJobTests(id)])))
      .then((each) => {
        setRows(each.flatMap(([apps]) => apps))
        setInvites(each.flatMap(([, tests]) => tests))
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load candidates'))
    // Back from connecting Gmail: bring back what was ticked.
    try {
      const saved = JSON.parse(window.sessionStorage.getItem(`ats.send.${test.id}`) ?? 'null') as { jobId: string; chosen: string[] } | null
      if (saved && saved.jobId === jobId) {
        window.sessionStorage.removeItem(`ats.send.${test.id}`)
        setChosen(saved.chosen)
      }
    } catch {
      // nothing to restore
    }
    // The background badges are a help, not a must: without them the list still works.
    if (jobId === ALL_OPENINGS) {
      setBackgrounds([])
      return
    }
    getBackgrounds(jobId)
      .then(setBackgrounds)
      .catch(() => setBackgrounds([]))
  }, [jobId, allJobIds]) // eslint-disable-line react-hooks/exhaustive-deps

  const existing = (applicationId: string) =>
    invites.find((i) => i.applicationId === applicationId && i.assessmentId === test.id && OPEN.has(i.status))
  const canSend = (r: ApplicationRow) => !!r.email && !existing(r.id) && r.stage !== 'REJECTED' && r.stage !== 'WITHDRAWN'
  const sendable = (rows ?? []).filter(canSend)
  const track = trackFor(test.category)
  const background = (applicationId: string) => backgrounds.find((b) => b.applicationId === applicationId)
  const matching = track ? sendable.filter((r) => background(r.id)?.track === track) : []
  const unread = backgrounds.filter((b) => !b.read).length
  const allOpenings = jobId === ALL_OPENINGS
  const match = (r: ApplicationRow) => r.portal?.matchPercent ?? -1
  const hasMatch = (rows ?? []).some((r) => r.portal?.matchPercent != null)
  const withResume = (rows ?? []).filter((r) => r.documents.includes('ORIGINAL_RESUME')).length
  const q = search.trim().toLowerCase()
  // What the list shows: search and filters, best match first.
  const shown = (rows ?? [])
    .filter(
      (r) =>
        (!q || r.name.toLowerCase().includes(q) || (r.email ?? '').toLowerCase().includes(q) || (allOpenings && r.jobTitle.toLowerCase().includes(q))) &&
        (!resumeOnly || r.documents.includes('ORIGINAL_RESUME')) &&
        (!hideSent || !existing(r.id)) &&
        (!selectedOnly || chosen.includes(r.id)),
    )
    .sort((a, b) => match(b) - match(a) || a.name.localeCompare(b.name))
  const shownSendable = shown.filter(canSend)
  const allShownPicked = shownSendable.length > 0 && shownSendable.every((r) => chosen.includes(r.id))
  const extras = parseExtraPeople(allOpenings ? '' : extraText)
  const chosenEmails = new Set((rows ?? []).filter((r) => chosen.includes(r.id)).map((r) => r.email?.toLowerCase()))
  const extraPeople = extras.people.filter((p) => !chosenEmails.has(p.email))
  const total = chosen.length + extraPeople.length

  function connectGmail() {
    try {
      window.sessionStorage.setItem(`ats.send.${test.id}`, JSON.stringify({ jobId, chosen }))
    } catch {
      // the selection just won't come back
    }
    const back = new URLSearchParams(window.location.search)
    back.set('test', test.id)
    back.set('send', '1')
    window.location.assign(connectGoogleUrl(`${window.location.pathname}?${back.toString()}`))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const targets: { id: string; name: string }[] = (rows ?? []).filter((r) => chosen.includes(r.id)).map((r) => ({ id: r.id, name: r.name }))
    const sent: string[] = []
    const failed: string[] = []
    // People typed in: reuse them if they're in the opening, otherwise add them first.
    for (const p of extraPeople) {
      const known = (rows ?? []).find((r) => r.email?.toLowerCase() === p.email)
      if (known) {
        targets.push({ id: known.id, name: known.name })
        continue
      }
      try {
        const added = await addCandidate(jobId, { name: p.name, email: p.email, stage: 'SOURCED', note: 'Added to send a test' })
        targets.push({ id: added.id, name: added.name })
      } catch (e) {
        failed.push(`${p.email}: ${e instanceof Error ? e.message : 'could not be added'}`)
      }
    }
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
    if (sent.length) setExtraText('')
    if (jobId) {
      const ids = jobId === ALL_OPENINGS ? jobs.map((j) => j.id) : [jobId]
      Promise.all(ids.map((id) => Promise.all([listApplications(id), listJobTests(id)])))
        .then((each) => {
          setRows(each.flatMap(([apps]) => apps))
          setInvites(each.flatMap(([, tests]) => tests))
        })
        .catch(() => undefined)
    }
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
          {jobs.length > 0 && <option value={ALL_OPENINGS}>All openings ({jobs.reduce((n, j) => n + j.total, 0)} candidates)</option>}
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
          <div className="row" style={{ gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <input
              className="input"
              type="search"
              aria-label="Find candidates"
              placeholder={allOpenings ? 'Find by name, email or opening' : 'Find by name or email'}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ flex: '1 1 240px', maxWidth: 340 }}
            />
            {withResume > 0 && (
              <button type="button" className="stage-chip" aria-pressed={resumeOnly} onClick={() => setResumeOnly(!resumeOnly)}>
                Résumé uploaded <strong>{withResume}</strong>
              </button>
            )}
            {!simple && (
              <label className="row" style={{ gap: 4, fontSize: 13 }}>
                <input type="checkbox" checked={hideSent} onChange={(e) => setHideSent(e.target.checked)} /> Hide people already sent it
              </label>
            )}
            {chosen.length > 0 && (
              <Button size="sm" variant="ghost" onClick={() => setChosen([])}>
                Clear selection
              </Button>
            )}
            <label className="row" style={{ gap: 4, fontSize: 13 }}>
              <input type="checkbox" checked={selectedOnly} onChange={(e) => setSelectedOnly(e.target.checked)} /> Show selected only
            </label>
          </div>
          <p className="muted" style={{ margin: 0, fontSize: 13 }}>
            {simple
              ? `${shown.length} candidate${shown.length === 1 ? '' : 's'}. Search a name, tick one or more people — ticks stay while you search — then Send.`
              : 'Search for each person and tick them — ticks are kept while you search for the next one.'}
          </p>
          {!simple && (
          <div className="row" style={{ gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <BatchSizeInput value={batch} onChange={setBatch} />
            <Button size="sm" variant="secondary" disabled={shownSendable.length === 0} onClick={() => setChosen(shownSendable.slice(0, batch).map((r) => r.id))}>
              Select next {Math.min(batch, shownSendable.length)}
            </Button>
            {track && (
              <Button size="sm" variant="secondary" disabled={matching.length === 0} onClick={() => setChosen(matching.slice(0, batch).map((r) => r.id))}>
                Select next {Math.min(batch, matching.length)} with a {TRACK_LABEL[track]} background
              </Button>
            )}
            <Button size="sm" variant="ghost" disabled={sendable.length === 0} onClick={() => setChosen(sendable.map((r) => r.id))}>
              Select all who can get it ({sendable.length})
            </Button>
            {chosen.length > 0 && (
              <Button size="sm" variant="ghost" onClick={() => setChosen([])}>
                Clear
              </Button>
            )}
          </div>
          )}
          {track && unread > 0 && !simple && (
            <p className="muted" style={{ margin: 0, fontSize: 12 }}>
              {unread} résumé{unread === 1 ? ' hasn’t' : 's haven’t'} been read by the AI yet, so their background is unknown. Open the
              opening → AI suggestions → Analyze résumés to read them.
            </p>
          )}
          {/* The list scrolls on its own so Send stays in view with hundreds of candidates. */}
          <div className="send-list-scroll" role="region" aria-label="Candidate list">
          <table className="send-list" aria-label="Candidates">
            <thead>
              <tr>
                <th>
                  <label className="row" style={{ gap: 8 }}>
                    <input
                      type="checkbox"
                      aria-label="Select everyone shown"
                      disabled={shownSendable.length === 0}
                      checked={allShownPicked}
                      onChange={(e) =>
                        setChosen((c) =>
                          e.target.checked
                            ? [...new Set([...c, ...shownSendable.map((r) => r.id)])]
                            : c.filter((x) => !shownSendable.some((r) => r.id === x)),
                        )
                      }
                    />
                    Name
                  </label>
                </th>
                <th>Email</th>
                {allOpenings && <th>Opening</th>}
                {hasMatch && <th>Match</th>}
                <th>Stage</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {shown.length === 0 && (
                <tr>
                  <td colSpan={6} className="muted">
                    Nobody matches. {hideSent && 'People who were already sent it are hidden. '}
                  </td>
                </tr>
              )}
              {shown.map((r) => {
                const open = existing(r.id)
                const reason = open ? `Already sent · ${testStatusLabel(open)}` : !r.email ? 'No email' : canSend(r) ? null : r.stageLabel
                const bg = background(r.id)
                return (
                  <tr key={r.id} className={chosen.includes(r.id) ? 'picked' : undefined}>
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
                        {r.documents.includes('ORIGINAL_RESUME') && <span className="muted" title="Résumé uploaded">📄</span>}
                      </label>
                    </td>
                    <td className="muted">{r.email ?? '—'}</td>
                    {allOpenings && <td className="muted">{r.jobTitle}</td>}
                    {hasMatch && <td>{r.portal?.matchPercent != null ? `${r.portal.matchPercent}%` : <span className="muted">—</span>}</td>}
                    <td>{r.stageLabel}</td>
                    <td>
                      {bg?.track && (
                        <span title={bg.evidence.join(', ')}>
                          <Badge tone={track && bg.track === track ? 'primary' : 'neutral'}>{TRACK_LABEL[bg.track]}</Badge>
                        </span>
                      )}{' '}
                      {reason && <Badge tone="neutral">{reason}</Badge>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          </div>
          {allOpenings && (
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>To add new people by email, choose their opening above.</p>
          )}
          {!allOpenings && (
          <div>
            <label className="field">
              Also send to (one per line; they're added to this opening if they're new)
              <textarea
                className="input"
                rows={3}
                placeholder={'asha.rao@example.com\nRavi Kumar <ravi@example.com>'}
                value={extraText}
                onChange={(e) => setExtraText(e.target.value)}
              />
            </label>
            {(extraPeople.length > 0 || extras.invalid.length > 0) && (
              <span className="muted" style={{ fontSize: 13 }}>
                {extraPeople.length > 0 && `Adding: ${extraPeople.map((p) => `${p.name} (${p.email})`).join(', ')}. `}
                {extras.invalid.length > 0 && <span style={{ color: 'var(--color-danger, #b91c1c)' }}>Not an email address: {extras.invalid.join(', ')}</span>}
              </span>
            )}
          </div>
          )}
          {needsGoogle && (
            <ConnectGoogle
              status={google}
              purpose="The test link is emailed from your own Gmail, so replies come back to you. Your selection is kept while you connect. Or untick Email and use Copy link in the candidate’s Tests tab."
              returnTo={window.location.pathname + window.location.search}
              onConnect={connectGmail}
            />
          )}
          {total > 0 && (
            <div className="picked-chips" role="list" aria-label="Selected people">
              {(rows ?? [])
                .filter((r) => chosen.includes(r.id))
                .map((r) => (
                  <span key={r.id} className="picked-chip" role="listitem">
                    {r.name}
                    <button type="button" aria-label={`Remove ${r.name}`} onClick={() => setChosen((c) => c.filter((x) => x !== r.id))}>
                      ×
                    </button>
                  </span>
                ))}
              {extraPeople.map((p) => (
                <span key={p.email} className="picked-chip new" role="listitem" title="Will be added to the opening">
                  {p.name} · new
                </span>
              ))}
            </div>
          )}
          <div className="send-footer">
            <strong>{total} selected</strong>
            <label className="row" style={{ gap: 6 }}>
              Days to finish
              <input
                className="input"
                type="number"
                min={1}
                max={30}
                aria-label="Days to finish"
                value={dueDays}
                onChange={(e) => setDueDays(Math.max(1, Math.min(30, Number(e.target.value) || 1)))}
                style={{ width: 72 }}
              />
            </label>
            <label className="row" style={{ gap: 6 }}>
              <input type="checkbox" checked={email} onChange={(e) => setEmail(e.target.checked)} /> Email
            </label>
            {waApi && (
              <label className="row" style={{ gap: 6 }}>
                <input type="checkbox" checked={whatsApp} onChange={(e) => setWhatsApp(e.target.checked)} /> WhatsApp
              </label>
            )}
            <Button type="submit" disabled={!!busy || total === 0 || needsGoogle || extras.invalid.length > 0}>
              {busy ?? `Send to ${total} candidate${total === 1 ? '' : 's'}`}
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
