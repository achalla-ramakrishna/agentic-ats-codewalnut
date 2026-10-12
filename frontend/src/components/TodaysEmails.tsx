import { useEffect, useState } from 'react'
import { sendSlotInvites } from '../api/booking'
import { ApiError } from '../api/client'
import { getGoogleStatus, type GoogleStatus } from '../api/interviews'
import type { WorkflowRow } from '../api/workflow'
import { sendEach } from './bulkSend'
import { ConnectGoogle } from './ConnectGoogle'
import { EMAIL_TEMPLATES } from './emailTemplates'
import { Button } from './ui'

/** What is due, warmest first (PIPE-25). */
export const DUE_SECTIONS: { due: string; label: string; template: string | null }[] = [
  { due: 'SEND_INVITE', label: 'Booked a slot → send their Google Meet invite', template: null },
  { due: 'BOOKING_LINK', label: 'Résumé received → send the booking link for a 30-minute call', template: 'book_slot' },
  { due: 'BOOKING_REMINDER', label: 'Booking link sent 3+ days ago, not booked → one reminder', template: 'booking_reminder' },
  { due: 'RESUME_REQUEST', label: 'New → ask for their résumé', template: 'resume_request' },
  { due: 'RESUME_FOLLOW_UP', label: 'Résumé asked 3+ days ago, no reply → one follow-up', template: 'follow_up' },
]

const CAP_KEY = 'ats.dailyEmailCap'
function loadCap() {
  try {
    const n = Number(window.localStorage.getItem(CAP_KEY))
    if (Number.isInteger(n) && n >= 1 && n <= 500) return n
  } catch {
    // private window or blocked storage
  }
  return 150
}

/**
 * "Today's emails" (PIPE-25): the system works out each candidate's next outreach email from what has
 * happened, and sends everything due in one click from the recruiter's Gmail, within a daily cap.
 * People who wrote to us are left for a person to answer.
 */
export function TodaysEmails({ rows, senderName, onDone }: { rows: WorkflowRow[]; senderName: string | null; onDone: () => void }) {
  const [include, setInclude] = useState<Record<string, boolean>>(() => Object.fromEntries(DUE_SECTIONS.map((s) => [s.due, true])))
  const [cap, setCap] = useState(loadCap)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<string[] | null>(null)
  const [google, setGoogle] = useState<GoogleStatus | null>(null)

  useEffect(() => {
    if (open) getGoogleStatus().then(setGoogle).catch(() => setGoogle(null))
  }, [open])

  const due = (key: string) => rows.filter((r) => r.due === key)
  const waiting = rows.filter((r) => r.awaitingReply && !r.closed).length
  const emailsDue = DUE_SECTIONS.filter((s) => s.template && include[s.due]).reduce((n, s) => n + due(s.due).length, 0)
  const invitesDue = include.SEND_INVITE ? due('SEND_INVITE').length : 0
  const total = DUE_SECTIONS.reduce((n, s) => n + due(s.due).length, 0)
  const needsGoogle = google !== null && (!google.available || !google.mailConnected || !google.calendarConnected)

  function saveCap(n: number) {
    const clean = Math.max(1, Math.min(500, Math.round(n) || 150))
    setCap(clean)
    try {
      window.localStorage.setItem(CAP_KEY, String(clean))
    } catch {
      // not remembered
    }
  }

  async function run() {
    const lines: string[] = []
    let left = cap
    try {
      if (invitesDue) {
        let scheduled = 0
        for (const job of new Set(due('SEND_INVITE').map((r) => r.jobId))) {
          setBusy('Sending calendar invites…')
          const r = await sendSlotInvites(job)
          scheduled += r.scheduled
          r.failed.forEach((f) => lines.push(`Invite not sent – ${f}`))
        }
        lines.unshift(`Calendar invites sent: ${scheduled}`)
      }
      for (const s of DUE_SECTIONS) {
        if (!s.template || !include[s.due] || left <= 0) continue
        const t = EMAIL_TEMPLATES.find((x) => x.key === s.template)!
        const targets = due(s.due)
          .slice(0, left)
          .map((r) => ({ applicationId: r.applicationId, name: r.candidateName, jobTitle: r.jobTitle }))
        if (!targets.length) continue
        const r = await sendEach(targets, { subject: t.subject, body: t.body, templateKey: t.key, senderName: senderName ?? 'CodeWalnut Talent Team' }, setBusy)
        left -= r.sent.length
        lines.push(`${t.label}: ${r.sent.length} sent`)
        r.failed.forEach((f) => lines.push(`Not sent – ${f}`))
      }
      if (left <= 0) lines.push(`Stopped at today’s limit of ${cap} emails; the rest stay due for tomorrow.`)
    } catch (e) {
      lines.push(
        e instanceof ApiError && e.status === 428
          ? 'Connect Google first (button below), then click Send again.'
          : `Stopped: ${e instanceof Error ? e.message : 'something went wrong'}`,
      )
      if (e instanceof ApiError && e.status === 428) setGoogle(await getGoogleStatus().catch(() => null))
    }
    setBusy(null)
    setResult(lines)
    onDone()
  }

  return (
    <div className="card stack todays-emails" style={{ gap: 10 }}>
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
        <div>
          <strong>Today’s emails</strong>{' '}
          <span className="muted">
            {total === 0 ? 'nothing due right now' : `${total} due`}
            {waiting > 0 && ` · ${waiting} wrote to you – reply personally (Waiting for our reply)`}
          </span>
        </div>
        <Button size="sm" variant={open ? 'ghost' : 'primary'} disabled={total === 0 && !open} onClick={() => setOpen(!open)}>
          {open ? 'Hide' : 'Review & send'}
        </Button>
      </div>
      {open && (
        <>
          <p className="muted" style={{ margin: 0, fontSize: 13 }}>
            Worked out from what has happened with each candidate: who is new, who sent a résumé, who booked. Each person gets their own
            email from your Gmail, one at a time. Nobody gets the same email twice; reminders go once.
          </p>
          {DUE_SECTIONS.map((s) => {
            const n = due(s.due).length
            const t = s.template ? EMAIL_TEMPLATES.find((x) => x.key === s.template) : null
            return (
              <div key={s.due} className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
                <input
                  type="checkbox"
                  aria-label={s.label}
                  checked={include[s.due]}
                  disabled={n === 0}
                  onChange={(e) => setInclude({ ...include, [s.due]: e.target.checked })}
                />
                <div style={{ flex: 1 }}>
                  <strong>{n}</strong> · {s.label}
                  {t && n > 0 && (
                    <details className="muted" style={{ fontSize: 12 }}>
                      <summary style={{ cursor: 'pointer' }}>Email: “{t.subject}”</summary>
                      <pre style={{ whiteSpace: 'pre-wrap', fontFamily: 'inherit', margin: '4px 0 0' }}>{t.body}</pre>
                    </details>
                  )}
                </div>
              </div>
            )
          })}
          {needsGoogle && (
            <ConnectGoogle status={google} purpose="Emails and invites go from your own Gmail and Calendar." returnTo={window.location.pathname + window.location.search} />
          )}
          <div className="row" style={{ gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
            <label className="row" style={{ gap: 6, fontSize: 13 }}>
              Up to
              <input className="input" type="number" min={1} max={500} style={{ width: 80 }} value={cap} onChange={(e) => saveCap(Number(e.target.value))} />
              emails today
            </label>
            <Button disabled={!!busy || needsGoogle || emailsDue + invitesDue === 0} onClick={() => void run()}>
              {busy ?? `Send ${Math.min(emailsDue, cap)} email${Math.min(emailsDue, cap) === 1 ? '' : 's'}${invitesDue ? ` and ${invitesDue} invite${invitesDue === 1 ? '' : 's'}` : ''}`}
            </Button>
          </div>
          {result && (
            <div className="alert alert-info" role="status">
              {result.map((l) => (
                <div key={l}>{l}</div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
