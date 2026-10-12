import { Fragment, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  HOW_LABEL,
  getTimeline,
  getWorkflow,
  logContact,
  type ContactHow,
  type Timeline,
  type WorkflowBoard,
  type WorkflowFilter,
  type WorkflowRow,
} from '../api/workflow'
import { getWhatsAppStatus, postMessage } from '../api/messages'
import { useMe } from '../auth/AuthContext'
import { BatchSizeInput, useBatchSize } from '../components/BatchSize'
import { BulkEmail, type BulkRecipient } from '../components/BulkEmail'
import { EMAIL_TEMPLATES, TEMPLATE_SHORT } from '../components/emailTemplates'
import { Badge, Button, PageHeader } from '../components/ui'
import '../components/tracker.css'
import './WorkflowPage.css'

const FILTERS: { key: WorkflowFilter; label: string }[] = [
  { key: 'active', label: 'Everyone in progress' },
  { key: 'urgent', label: 'Needs action' },
  { key: 'reply', label: 'Waiting for our reply' },
  { key: 'never', label: 'Never contacted' },
  { key: 'quiet', label: 'No contact for 7+ days' },
  { key: 'closed', label: 'Joined / rejected / withdrawn' },
]

/** The email that fits each tab, ready in the bulk email panel (PIPE-22). */
const TEMPLATE_FOR: Record<WorkflowFilter, string> = {
  active: 'custom',
  urgent: 'custom',
  reply: 'interview_slots',
  never: 'resume_request',
  quiet: 'follow_up',
  closed: 'custom',
}

/** Stages in pipeline order, for the stage chips (PIPE-23). */
const STAGES: { key: string; label: string }[] = [
  { key: 'SOURCED', label: 'Applied / Sourced' },
  { key: 'INTERVIEWED', label: 'Interviewed' },
  { key: 'SHORTLISTED', label: 'Shortlisted' },
  { key: 'OFFER_SENT', label: 'Offer sent' },
  { key: 'JOINED', label: 'Joined' },
  { key: 'ON_HOLD', label: 'On hold' },
  { key: 'REJECTED', label: 'Rejected' },
]

/** The email a stage usually needs next, when the tab doesn't say. */
const TEMPLATE_FOR_STAGE_TAB: Record<string, string> = {
  SOURCED: 'resume_request',
  INTERVIEWED: 'custom',
  SHORTLISTED: 'shortlisted',
  OFFER_SENT: 'selected',
  REJECTED: 'rejected',
}

/** Templates a recruiter can filter on ("got it" / "hasn't got it yet"). */
const TRACKED = EMAIL_TEMPLATES.filter((t) => TEMPLATE_SHORT[t.key]).map((t) => t.key).concat(['test_invite'])

function shortDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

/** One chip per template (latest send), then free-form emails as "Email". */
function emailChips(row: WorkflowRow) {
  const seen = new Set<string>()
  const chips: { key: string; label: string; at: string; title: string }[] = []
  for (const e of row.emails ?? []) {
    const k = e.templateKey ?? 'free'
    if (seen.has(k)) continue
    seen.add(k)
    chips.push({ key: k, label: e.templateKey ? (TEMPLATE_SHORT[e.templateKey] ?? e.templateKey) : 'Email', at: e.at, title: e.subject ?? '' })
  }
  return chips
}

const QUIET_MS = 7 * 24 * 3600 * 1000
/** Past screening someone has clearly been in touch, even if it isn't recorded in the app. */
const EARLY = new Set(['SOURCED', 'SCREENING'])

function matches(row: WorkflowRow, filter: WorkflowFilter, now: number) {
  switch (filter) {
    case 'active':
      return !row.closed
    case 'urgent':
      return !row.closed && !!row.nextStep?.urgent
    case 'reply':
      return row.awaitingReply
    case 'never':
      return !row.closed && row.contacts === 0 && EARLY.has(row.stage)
    case 'quiet':
      return !row.closed && !!row.lastContact && now - new Date(row.lastContact.at).getTime() > QUIET_MS
    case 'closed':
      return row.closed
  }
}

function ago(iso: string, now = Date.now()) {
  const minutes = Math.round((now - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.round(hours / 24)
  if (days < 31) return `${days} day${days === 1 ? '' : 's'} ago`
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

function days(iso: string, now: number) {
  return Math.max(0, Math.floor((now - new Date(iso).getTime()) / (24 * 3600 * 1000)))
}

const who = (email: string | null) => (email ? email.split('@')[0] : '')

function when(iso: string) {
  return new Date(iso).toLocaleString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
}

/** Where to go for the suggested step: the candidate's chat, tests, the feedback form, or their panel. */
function actionLink(row: WorkflowRow): string {
  const base = `/jobs/${row.jobId}?candidate=${row.applicationId}`
  switch (row.nextStep?.code) {
    case 'REPLY':
    case 'FIRST_CONTACT':
    case 'OFFER_FOLLOW_UP':
    case 'ON_HOLD':
      return `${base}&tab=candidate`
    case 'REVIEW_TEST':
    case 'REMIND_TEST':
    case 'WAIT_TEST':
      return `${base}&tab=tests`
    case 'CLIENT_FOLLOW_UP':
    case 'CLIENT_DECISION':
      return row.clientName ? `${base}&tab=client` : base
    case 'FEEDBACK':
      return row.interview ? `/interviews/${row.interview.interviewId}/feedback` : base
    default:
      return base
  }
}

const firstName = (name: string) => {
  const first = name.trim().split(/\s+/)[0] ?? ''
  return first.charAt(0).toUpperCase() + first.slice(1).toLowerCase()
}

/** A starting WhatsApp message for the suggested step; the recruiter can change it before sending. */
export function suggestedWhatsApp(row: WorkflowRow, sender: string): string {
  const hi = `Hi ${firstName(row.candidateName)}, this is ${sender} from CodeWalnut`
  const role = `the ${row.jobTitle} role`
  switch (row.nextStep?.code) {
    case 'FIRST_CONTACT':
      return `${hi}. We came across your profile for ${role}. Are you open to a quick call? Please share a convenient time.`
    case 'REMIND_TEST':
    case 'WAIT_TEST':
      return `${hi}. A gentle reminder to take the online test for ${role}; the link is in your email. Let me know if you face any issue.`
    case 'CHASE_DOCS':
      return `${hi}. Could you please upload the documents we requested for ${role} on your candidate page?`
    case 'OFFER_FOLLOW_UP':
      return `${hi}. Just checking whether you've had a chance to look at the offer for ${role}. Happy to answer any questions.`
    case 'JOINING':
      return `${hi}. Congratulations again! Could you confirm your joining date for ${role}?`
    case 'REPLY':
      return `${hi}. Thanks for your message about ${role}. `
    default:
      return `${hi}, regarding ${role}. `
  }
}

function TimelinePanel({ row, canLog, onLogged }: { row: WorkflowRow; canLog: boolean; onLogged: () => void }) {
  const [timeline, setTimeline] = useState<Timeline | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [how, setHow] = useState<ContactHow>('CALL')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    getTimeline(row.applicationId)
      .then(setTimeline)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the history'))
  }, [row.applicationId])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setTimeline(await logContact(row.applicationId, how, note))
      setNote('')
      onLogged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not log it')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="wf-timeline stack" style={{ gap: 12 }}>
      {canLog && (
        <form className="row wf-log" onSubmit={submit} aria-label={`Log contact with ${row.candidateName}`}>
          <strong>Log a call or message made outside the app:</strong>
          <select className="input" aria-label="How" value={how} onChange={(e) => setHow(e.target.value as ContactHow)} style={{ width: 'auto' }}>
            {(Object.keys(HOW_LABEL) as ContactHow[]).map((h) => (
              <option key={h} value={h}>
                {HOW_LABEL[h]}
              </option>
            ))}
          </select>
          <input
            className="input"
            aria-label="What was said"
            placeholder="What was said (optional), e.g. interested, call back Monday"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            style={{ flex: '1 1 260px' }}
          />
          <Button size="sm" type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Log it'}
          </Button>
        </form>
      )}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {!timeline && !error && <p className="muted">Loading…</p>}
      {timeline && (
        <ol className="wf-events" aria-label={`What happened with ${row.candidateName}`}>
          {timeline.items.map((it, i) => (
            <li key={i} className={`wf-event wf-${it.kind.toLowerCase()}`}>
              <span className="wf-when muted">{when(it.at)}</span>
              <span className="wf-what">
                <strong>{it.title}</strong>
                {it.detail ? <span className="wf-detail"> — {it.detail}</span> : null}
                {it.by && <span className="muted"> · {it.kind === 'MESSAGE_IN' || it.title === 'Client wrote' ? it.by : who(it.by)}</span>}
              </span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

/**
 * Workflow (docs/features/workflow.md, WF-01…WF-06): every candidate's last contact, whether
 * they're waiting on us, test, interview and client status, and a suggested next step, with the
 * full history a click away.
 */
export function WorkflowPage() {
  const [params, setParams] = useSearchParams()
  const jobId = params.get('job') ?? ''
  const filter = (params.get('show') as WorkflowFilter | null) ?? 'active'
  const stage = params.get('stage') ?? ''
  // "got:shortlisted" = received that email; "not:shortlisted" = hasn't yet.
  const emailFilter = params.get('email') ?? ''
  const [board, setBoard] = useState<WorkflowBoard | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [waApi, setWaApi] = useState(false)
  const [compose, setCompose] = useState<{ applicationId: string; text: string } | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [waError, setWaError] = useState<string | null>(null)
  const [waBusy, setWaBusy] = useState<string | null>(null)
  const me = useMe()
  const sender = firstName(me.name ?? me.email.split('@')[0])
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [emailing, setEmailing] = useState<BulkRecipient[] | null>(null)
  const [batch, setBatch] = useBatchSize()
  const now = Date.now()

  useEffect(() => {
    if (!board?.canLog) return
    getWhatsAppStatus()
      .then((s) => setWaApi(s.apiEnabled))
      .catch(() => setWaApi(false))
  }, [board?.canLog])

  /**
   * Without the WhatsApp Business API, WhatsApp opens with the message ready and the recruiter
   * presses Send there. With the API it would send at once, so the message is shown first.
   */
  async function whatsApp(row: WorkflowRow, text: string) {
    setWaError(null)
    setNotice(null)
    // Open the tab now, while the click still counts (browsers block pop-ups after a network call).
    const tab = waApi ? null : window.open('about:blank', '_blank')
    setWaBusy(row.applicationId)
    try {
      const sent = await postMessage(row.applicationId, { channel: 'CANDIDATE', body: text, sendEmail: false, sendWhatsApp: true })
      if (sent.whatsappLink) {
        if (tab) tab.location.href = sent.whatsappLink
        else window.open(sent.whatsappLink, '_blank')
        setNotice(`WhatsApp opened for ${row.candidateName} with the message ready: press Send there.`)
      } else {
        tab?.close()
        setNotice(`Sent to ${row.candidateName} on WhatsApp.`)
      }
      setCompose(null)
      setReload((n) => n + 1)
    } catch (e) {
      tab?.close()
      setWaError(e instanceof Error ? e.message : 'Could not open WhatsApp')
    } finally {
      setWaBusy(null)
    }
  }

  useEffect(() => {
    getWorkflow(jobId || undefined)
      .then(setBoard)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the workflow'))
  }, [jobId, reload])

  const [emailMode, emailKey] = emailFilter.split(':')
  const received = (r: WorkflowRow, key: string) => (r.emails ?? []).some((e) => e.templateKey === key)
  const inStage = (r: WorkflowRow) => !stage || r.stage === stage
  const byEmail = (r: WorkflowRow) =>
    !emailKey || (emailMode === 'got' ? received(r, emailKey) : emailMode === 'not' ? !received(r, emailKey) : true)
  const rows = useMemo(() => {
    if (!board) return []
    const q = query.trim().toLowerCase()
    return board.rows.filter(
      (r) =>
        inStage(r) &&
        byEmail(r) &&
        matches(r, filter, now) &&
        (!q || r.candidateName.toLowerCase().includes(q) || r.jobTitle.toLowerCase().includes(q)),
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [board, filter, query, stage, emailFilter])
  // Counts follow the other choices: stage chips count within the tab, tabs within the stage.
  const stageCount = (key: string) =>
    (board?.rows ?? []).filter((r) => r.stage === key && byEmail(r) && matches(r, filter, now)).length
  const tabCount = (f: WorkflowFilter) => (board?.rows ?? []).filter((r) => inStage(r) && byEmail(r) && matches(r, f, now)).length
  const suggestedTemplate =
    emailMode === 'not' && emailKey && emailKey !== 'test_invite'
      ? emailKey
      : TEMPLATE_FOR[filter] !== 'custom'
        ? TEMPLATE_FOR[filter]
        : (TEMPLATE_FOR_STAGE_TAB[stage] ?? 'custom')

  // Bulk email: people on this tab with an email who are still in the running.
  const emailable = rows.filter((r) => r.hasEmail && !r.closed)
  const pickedRows = rows.filter((r) => picked.has(r.applicationId))
  const allPicked = emailable.length > 0 && emailable.every((r) => picked.has(r.applicationId))
  const togglePick = (id: string, on: boolean) =>
    setPicked((p) => {
      const next = new Set(p)
      if (on) next.add(id)
      else next.delete(id)
      return next
    })

  function set(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
    setPicked(new Set())
    setEmailing(null)
  }

  if (error) {
    return (
      <div role="alert" className="alert alert-error">
        {error}
      </div>
    )
  }

  return (
    <div className="stack">
      <PageHeader
        title="Workflow"
        description="Who you've contacted and how, who is waiting on you, and what to do next, for every candidate."
      />
      <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
        <select className="input" aria-label="Opening" value={jobId} onChange={(e) => set('job', e.target.value)} style={{ width: 'auto', maxWidth: 360 }}>
          <option value="">All open openings</option>
          {board?.openings.map((o) => (
            <option key={o.id} value={o.id}>
              {o.title}
              {o.clientName ? ` (${o.clientName})` : ''}
              {o.status === 'CLOSED' ? ' — closed' : ''}
            </option>
          ))}
        </select>
        <input
          className="input"
          aria-label="Search by name"
          placeholder="Search by name or opening"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{ width: 260 }}
        />
        <select className="input" aria-label="Filter by email sent" value={emailFilter} onChange={(e) => set('email', e.target.value)} style={{ width: 'auto' }}>
          <option value="">Any emails</option>
          <optgroup label="Hasn't received yet">
            {TRACKED.map((k) => (
              <option key={`not:${k}`} value={`not:${k}`}>
                No “{TEMPLATE_SHORT[k]}” yet
              </option>
            ))}
          </optgroup>
          <optgroup label="Has received">
            {TRACKED.map((k) => (
              <option key={`got:${k}`} value={`got:${k}`}>
                Got “{TEMPLATE_SHORT[k]}”
              </option>
            ))}
          </optgroup>
        </select>
      </div>
      <div className="row wf-filters" role="group" aria-label="Stage">
        <button type="button" className={`chip${!stage ? ' chip-on' : ''}`} aria-pressed={!stage} onClick={() => set('stage', '')}>
          All stages
        </button>
        {STAGES.map((st) => (
          <button
            key={st.key}
            type="button"
            className={`chip${stage === st.key ? ' chip-on' : ''}`}
            aria-pressed={stage === st.key}
            onClick={() => set('stage', stage === st.key ? '' : st.key)}
          >
            {st.label} {board ? <span className="wf-count">{stageCount(st.key)}</span> : null}
          </button>
        ))}
      </div>
      <div className="row wf-filters" role="tablist" aria-label="Show">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            role="tab"
            aria-selected={filter === f.key}
            className={`chip${filter === f.key ? ' chip-on' : ''}`}
            onClick={() => set('show', f.key === 'active' ? '' : f.key)}
          >
            {f.label} {board ? <span className="wf-count">{tabCount(f.key)}</span> : null}
          </button>
        ))}
      </div>

      {board?.canLog && emailable.length > 0 && (
        <div className="row" role="group" aria-label="Email candidates" style={{ gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <BatchSizeInput value={batch} onChange={setBatch} />
          <Button size="sm" variant="secondary" onClick={() => setPicked(new Set(emailable.slice(0, batch).map((r) => r.applicationId)))}>
            Select first {Math.min(batch, emailable.length)}
          </Button>
          {picked.size > 0 && (
            <>
              <Button
                size="sm"
                onClick={() =>
                  setEmailing(pickedRows.map((r) => ({ applicationId: r.applicationId, name: r.candidateName, hasEmail: r.hasEmail, jobTitle: r.jobTitle })))
                }
              >
                Email selected ({picked.size})
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setPicked(new Set())}>
                Clear
              </Button>
            </>
          )}
        </div>
      )}
      {emailing && (
        <div className="card">
          <BulkEmail
            key={`${filter}-${stage}-${emailFilter}`}
            recipients={emailing}
            jobTitle={emailing[0]?.jobTitle ?? ''}
            senderName={me.name}
            initialTemplate={suggestedTemplate}
            onSent={() => {
              setPicked(new Set())
              setReload((n) => n + 1)
            }}
            onClose={() => setEmailing(null)}
          />
        </div>
      )}
      {notice && <div className="alert alert-info">{notice}</div>}
      {waError && (
        <div role="alert" className="alert alert-error">
          {waError}
        </div>
      )}
      {!board && <p className="muted">Loading…</p>}
      {board && rows.length === 0 && <p className="muted">Nobody here.</p>}
      {board && rows.length > 0 && (
        <div className="table-wrap">
          <table className="table wf-table">
            <thead>
              <tr>
                {board.canLog && (
                  <th>
                    <input
                      type="checkbox"
                      aria-label="Select everyone shown"
                      disabled={emailable.length === 0}
                      checked={allPicked}
                      onChange={(e) => setPicked(e.target.checked ? new Set(emailable.map((r) => r.applicationId)) : new Set())}
                    />
                  </th>
                )}
                <th>Candidate</th>
                <th>Stage</th>
                <th>Last contact</th>
                <th>Emails sent</th>
                <th>Test</th>
                <th>Interview</th>
                <th>Client</th>
                <th>Next step</th>
                <th aria-label="History" />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <Fragment key={r.applicationId}>
                  <tr className={r.awaitingReply ? 'wf-waiting' : undefined}>
                    {board.canLog && (
                      <td>
                        <input
                          type="checkbox"
                          aria-label={`Select ${r.candidateName}`}
                          disabled={!r.hasEmail || r.closed}
                          title={!r.hasEmail ? 'No email on file' : undefined}
                          checked={picked.has(r.applicationId)}
                          onChange={(e) => togglePick(r.applicationId, e.target.checked)}
                        />
                      </td>
                    )}
                    <td>
                      <Link to={`/jobs/${r.jobId}?candidate=${r.applicationId}`}>
                        <strong>{r.candidateName}</strong>
                      </Link>
                      <div className="muted wf-sub">
                        {r.jobTitle}
                        {r.clientName ? ` · ${r.clientName}` : ''}
                      </div>
                    </td>
                    <td>
                      {r.stageLabel}
                      <div className="muted wf-sub">{days(r.inStageSince, now)} days</div>
                    </td>
                    <td>
                      {r.lastContact ? (
                        <>
                          {ago(r.lastContact.at, now)}
                          <div className="muted wf-sub">
                            {r.lastContact.how}
                            {r.lastContact.by ? ` · ${who(r.lastContact.by)}` : ''}
                            {r.contacts > 1 ? ` · ${r.contacts} in all` : ''}
                          </div>
                        </>
                      ) : EARLY.has(r.stage) ? (
                        <Badge tone="danger">Never contacted</Badge>
                      ) : (
                        <span className="muted">No contact recorded</span>
                      )}
                      {r.awaitingReply && r.candidateWroteAt && (
                        <div>
                          <Badge tone="danger">They wrote {ago(r.candidateWroteAt, now)}</Badge>
                        </div>
                      )}
                      {board.canLog && (
                        <div className="wf-wa">
                          <button
                            type="button"
                            className="wf-wa-btn"
                            disabled={!r.hasPhone || waBusy === r.applicationId}
                            title={r.hasPhone ? 'Open WhatsApp with a message ready' : 'No mobile number on file'}
                            aria-label={`WhatsApp ${r.candidateName}`}
                            onClick={() => {
                              const text = suggestedWhatsApp(r, sender)
                              if (waApi) setCompose({ applicationId: r.applicationId, text })
                              else void whatsApp(r, text)
                            }}
                          >
                            {waBusy === r.applicationId ? 'Opening…' : 'WhatsApp'}
                          </button>
                        </div>
                      )}
                    </td>
                    <td className="wf-emails">
                      {emailChips(r).length === 0 ? (
                        <span className="muted">—</span>
                      ) : (
                        emailChips(r).map((c) => (
                          <span key={c.key} className="wf-email-chip" title={c.title}>
                            {c.label} <span className="muted">{shortDate(c.at)}</span>
                          </span>
                        ))
                      )}
                    </td>
                    <td>
                      {r.test ? (
                        <>
                          {r.test.status}
                          {r.test.percent != null && ` · ${r.test.percent}%`}
                          <div className="muted wf-sub">{r.test.title}</div>
                        </>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                    <td>
                      {r.interview ? (
                        <>
                          {r.interview.status === 'UPCOMING' ? when(r.interview.startAt) : r.interview.status === 'DONE' ? 'Held' : 'Cancelled'}
                          {r.interview.status === 'DONE' && (
                            <div className="muted wf-sub">
                              Feedback {r.interview.feedbackGiven} of {Math.max(1, r.interview.panel)}
                            </div>
                          )}
                        </>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                    <td>
                      {r.client ? (
                        <>
                          Shared {ago(r.client.sharedAt, now)}
                          <div className="muted wf-sub">{r.client.viewedAt ? `Viewed ${ago(r.client.viewedAt, now)}` : 'Not viewed yet'}</div>
                        </>
                      ) : (
                        <span className="muted">—</span>
                      )}
                      {r.documentsPending > 0 && <div className="muted wf-sub">{r.documentsPending} document(s) awaited</div>}
                    </td>
                    <td>
                      {r.nextStep ? (
                        <Link to={actionLink(r)} className={`wf-step${r.nextStep.urgent ? ' urgent' : ''}`}>
                          {r.nextStep.label}
                        </Link>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                    <td>
                      <Button
                        size="sm"
                        variant="ghost"
                        aria-expanded={open === r.applicationId}
                        aria-label={`History of ${r.candidateName}`}
                        onClick={() => setOpen(open === r.applicationId ? null : r.applicationId)}
                      >
                        {open === r.applicationId ? 'Hide' : 'History'}
                      </Button>
                    </td>
                  </tr>
                  {compose?.applicationId === r.applicationId && (
                    <tr className="wf-expanded">
                      <td colSpan={board.canLog ? 10 : 9}>
                        <form
                          className="stack"
                          style={{ gap: 8 }}
                          aria-label={`WhatsApp message to ${r.candidateName}`}
                          onSubmit={(e) => {
                            e.preventDefault()
                            void whatsApp(r, compose.text)
                          }}
                        >
                          <textarea
                            className="input"
                            aria-label="WhatsApp message"
                            rows={3}
                            style={{ fontFamily: 'inherit' }}
                            value={compose.text}
                            onChange={(e) => setCompose({ ...compose, text: e.target.value })}
                          />
                          <div className="row" style={{ gap: 8 }}>
                            <Button size="sm" type="submit" disabled={!compose.text.trim() || waBusy === r.applicationId}>
                              Send on WhatsApp
                            </Button>
                            <Button size="sm" variant="ghost" onClick={() => setCompose(null)}>
                              Cancel
                            </Button>
                          </div>
                        </form>
                      </td>
                    </tr>
                  )}
                  {open === r.applicationId && (
                    <tr className="wf-expanded">
                      <td colSpan={board.canLog ? 10 : 9}>
                        <TimelinePanel row={r} canLog={board.canLog} onLogged={() => setReload((n) => n + 1)} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="muted" style={{ fontSize: 12, margin: 0 }}>
        Contacts counted: emails, WhatsApps and messages sent from the app, tests, interview invites, document requests, and calls
        or messages you log. Next steps are simple suggestions; you decide.
      </p>
    </div>
  )
}
