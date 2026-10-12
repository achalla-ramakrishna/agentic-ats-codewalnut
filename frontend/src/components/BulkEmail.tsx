import { useEffect, useState } from 'react'
import { getGoogleStatus, type GoogleStatus } from '../api/interviews'
import { postMessage } from '../api/messages'
import { ConnectGoogle } from './ConnectGoogle'
import { EMAIL_TEMPLATES, fillTemplate } from './emailTemplates'
import { Button } from './ui'

export interface BulkRecipient {
  applicationId: string
  name: string
  hasEmail: boolean
  /** Their opening, when recipients come from different openings (the Workflow page). */
  jobTitle?: string
}

/**
 * One email to each chosen candidate, from the sender's Gmail, one at a time ({firstName} is filled
 * per person). Used after a Naukri import and from an opening's list (PIPE-16, PIPE-19).
 */
export function BulkEmail({
  recipients,
  jobTitle,
  senderName,
  initialTemplate = 'resume_request',
  title = 'Email the selected candidates',
  onSent,
  onClose,
}: {
  recipients: BulkRecipient[]
  jobTitle: string
  senderName: string | null
  initialTemplate?: string
  title?: string
  onSent?: () => void
  onClose: () => void
}) {
  const sender = senderName ?? 'CodeWalnut Talent Team'
  const withEmail = recipients.filter((r) => r.hasEmail)
  // One opening for everyone: show its title in the text. Several: keep {jobTitle}, filled per person.
  const titles = new Set(recipients.map((r) => r.jobTitle ?? jobTitle))
  const oneTitle = titles.size === 1 ? [...titles][0] : null
  const prepare = (text: string) => (oneTitle ? text.replaceAll('{jobTitle}', oneTitle) : text).replaceAll('{senderName}', sender)
  const start = EMAIL_TEMPLATES.find((t) => t.key === initialTemplate) ?? EMAIL_TEMPLATES[0]
  const [template, setTemplate] = useState(start.key)
  const [subject, setSubject] = useState(prepare(start.subject))
  const [body, setBody] = useState(prepare(start.body))
  const [google, setGoogle] = useState<GoogleStatus | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [outcome, setOutcome] = useState<{ sent: number; failed: string[] } | null>(null)

  useEffect(() => {
    getGoogleStatus()
      .then(setGoogle)
      .catch(() => setGoogle(null))
  }, [])
  const needsGoogle = google !== null && (!google.available || !google.mailConnected)

  function pick(key: string) {
    const t = EMAIL_TEMPLATES.find((x) => x.key === key)
    if (!t) return
    setTemplate(key)
    setSubject(prepare(t.subject))
    setBody(prepare(t.body))
  }

  async function send() {
    let sent = 0
    const failed: string[] = []
    for (const [n, r] of withEmail.entries()) {
      setBusy(`Sending ${n + 1} of ${withEmail.length}…`)
      try {
        const values = { candidateName: r.name, jobTitle: r.jobTitle ?? jobTitle, senderName: sender }
        await postMessage(r.applicationId, {
          channel: 'CANDIDATE',
          subject: fillTemplate(subject, values),
          body: fillTemplate(body, values),
          sendEmail: true,
        })
        sent++
      } catch (e) {
        failed.push(`${r.name}: ${e instanceof Error ? e.message : 'not sent'}`)
      }
    }
    setBusy(null)
    setOutcome({ sent, failed })
    if (sent > 0) onSent?.()
  }

  if (outcome) {
    return (
      <div className="stack" style={{ gap: 10 }}>
        <div className={`alert ${outcome.failed.length ? 'alert-error' : 'alert-info'}`} role="status">
          Emailed {outcome.sent} candidate{outcome.sent === 1 ? '' : 's'}.
          {outcome.failed.length > 0 && ` Not sent: ${outcome.failed.join('; ')}.`} Replies come to your Gmail and show under Messages
          and Workflow → Waiting for our reply. When someone sends their résumé, open them and Schedule interview.
        </div>
        <div className="row">
          <Button onClick={onClose}>Done</Button>
        </div>
      </div>
    )
  }

  return (
    <div className="stack" style={{ gap: 10 }}>
      <h3 style={{ margin: 0 }}>{title}</h3>
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        {withEmail.length} candidate{withEmail.length === 1 ? '' : 's'} each get their own email from your Gmail ({'{firstName}'} becomes
        their first name{oneTitle ? '' : ', {jobTitle} their opening'}). Check the text, then send.
        {withEmail.length < recipients.length && ` ${recipients.length - withEmail.length} without an email won’t get it.`}
      </p>
      <label className="field" style={{ maxWidth: 420 }}>
        Template
        <select className="select" value={template} onChange={(e) => pick(e.target.value)}>
          {EMAIL_TEMPLATES.map((t) => (
            <option key={t.key} value={t.key}>
              {t.label}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        Subject
        <input className="input" value={subject} onChange={(e) => setSubject(e.target.value)} />
      </label>
      <label className="field">
        Message
        <textarea className="input" rows={12} value={body} onChange={(e) => setBody(e.target.value)} />
      </label>
      {needsGoogle && (
        <ConnectGoogle status={google} purpose="Emails are sent from your own Gmail, so replies come back to your inbox." returnTo={window.location.pathname} />
      )}
      <div className="row" style={{ gap: 8 }}>
        <Button onClick={send} disabled={!!busy || withEmail.length === 0 || needsGoogle || !subject.trim() || !body.trim()}>
          {busy ?? `Send email to ${withEmail.length}`}
        </Button>
        <Button variant="ghost" onClick={onClose} disabled={!!busy}>
          Not now
        </Button>
      </div>
    </div>
  )
}
