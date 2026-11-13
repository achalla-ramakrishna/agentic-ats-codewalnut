import { useEffect, useState } from 'react'
import { listAssessments, sendTest, testLink, type AssessmentSummary } from '../api/assessments'
import { addCandidate, type ApplicationRow } from '../api/tracker'
import { getGoogleStatus, type GoogleStatus } from '../api/interviews'
import { ConnectGoogle } from './ConnectGoogle'
import { Button } from './ui'

const EMAIL = /^[^\s@<>,;]+@[^\s@<>,;]+\.[^\s@<>,;]+$/

/** "Asha Rao <asha@x.com>", "Asha Rao, asha@x.com" or just "asha.rao@x.com" (name made from the address). */
export function parseExtraPeople(text: string): { people: { name: string; email: string }[]; invalid: string[] } {
  const people: { name: string; email: string }[] = []
  const invalid: string[] = []
  for (const raw of text.split(/\n|;/).map((l) => l.trim()).filter(Boolean)) {
    const angle = raw.match(/^(.*)<\s*([^>]+)\s*>$/)
    const comma = raw.match(/^(.+?)[,\t]\s*(\S+@\S+)$/)
    const name = (angle?.[1] ?? comma?.[1] ?? '').trim().replace(/^"|"$/g, '')
    const email = (angle?.[2] ?? comma?.[2] ?? raw).trim().toLowerCase()
    if (!EMAIL.test(email)) {
      invalid.push(raw)
      continue
    }
    const fromAddress = email
      .split('@')[0]
      .replace(/[0-9]+/g, ' ')
      .split(/[._\-+\s]+/)
      .filter(Boolean)
      .map((w) => w[0].toUpperCase() + w.slice(1))
      .join(' ')
    if (!people.some((p) => p.email === email)) people.push({ name: name || fromAddress || email, email })
  }
  return { people, invalid }
}

/**
 * Send this opening's AI interview to the candidates selected in its list (ADR-0030): pick the set,
 * the days to finish, and whether to email the link; without Gmail, copy the links instead.
 */
export function SendAiInterview({
  jobId,
  recipients,
  allRows,
  onConnectGoogle,
  onSent,
  onCreate,
}: {
  jobId: string
  recipients: ApplicationRow[]
  /** Everyone in the opening, so an extra address that's already a candidate isn't added twice. */
  allRows: ApplicationRow[]
  onConnectGoogle: () => void
  onSent: () => void
  /** Go to the AI interview tab to create one. */
  onCreate: () => void
}) {
  const [interviews, setInterviews] = useState<AssessmentSummary[] | null>(null)
  const [chosen, setChosen] = useState('')
  const [days, setDays] = useState(3)
  const [google, setGoogle] = useState<GoogleStatus | null>(null)
  const [email, setEmail] = useState(true)
  const [busy, setBusy] = useState<string | null>(null)
  const [outcome, setOutcome] = useState<{ sent: { name: string; link: string }[]; failed: string[] } | null>(null)
  const [copied, setCopied] = useState(false)
  const [extraText, setExtraText] = useState('')

  useEffect(() => {
    listAssessments()
      .then((all) => {
        const ready = all.filter((a) => a.category === 'INTERVIEW' && a.status === 'READY')
        // This opening's interviews first; older ones made before interviews remembered their opening after.
        const mine = ready.filter((a) => a.jobId === jobId)
        const list = mine.length ? mine : ready.filter((a) => !a.jobId)
        setInterviews(list)
        setChosen(list[0]?.id ?? '')
      })
      .catch(() => setInterviews([]))
    getGoogleStatus()
      .then((g) => {
        setGoogle(g)
        if (!g.mailConnected) setEmail(false)
      })
      .catch(() => setGoogle(null))
  }, [jobId])

  const withEmail = recipients.filter((r) => r.email)
  const noEmail = recipients.length - withEmail.length
  const extras = parseExtraPeople(extraText)
  const picked = new Set(withEmail.map((r) => r.email!.toLowerCase()))
  const extraPeople = extras.people.filter((p) => !picked.has(p.email))
  const total = withEmail.length + extraPeople.length

  async function send() {
    setOutcome(null)
    const sent: { name: string; link: string }[] = []
    const failed: string[] = []
    // People typed in: use them if they're already in this opening, otherwise add them first.
    const targets: { id: string; name: string }[] = withEmail.map((r) => ({ id: r.id, name: r.name }))
    for (const p of extraPeople) {
      const existing = allRows.find((r) => r.email?.toLowerCase() === p.email)
      if (existing) {
        targets.push({ id: existing.id, name: existing.name })
        continue
      }
      try {
        const added = await addCandidate(jobId, { name: p.name, email: p.email, stage: 'SOURCED', note: 'Added to send the AI interview' })
        targets.push({ id: added.id, name: added.name })
      } catch (e) {
        failed.push(`${p.email}: ${e instanceof Error ? e.message : 'could not be added'}`)
      }
    }
    for (const [i, t] of targets.entries()) {
      setBusy(`Sending ${i + 1} of ${targets.length}…`)
      try {
        const res = await sendTest(t.id, { assessmentId: chosen, dueDays: days, sendEmail: email, sendWhatsApp: false })
        sent.push({ name: t.name, link: testLink(res.invite.id) })
      } catch (e) {
        failed.push(`${t.name}: ${e instanceof Error ? e.message : 'not sent'}`)
      }
    }
    if (sent.length) setExtraText('')
    setBusy(null)
    setOutcome({ sent, failed })
    if (sent.length) onSent()
  }

  if (!interviews) return <p className="muted">Loading…</p>
  if (interviews.length === 0) {
    return (
      <div className="stack" style={{ gap: 8 }}>
        <p style={{ margin: 0 }}>This opening has no AI interview ready to send yet.</p>
        <div>
          <Button onClick={onCreate}>Create one in the AI interview tab</Button>
        </div>
      </div>
    )
  }
  const needsGoogle = email && google !== null && !google.mailConnected
  return (
    <div className="stack" style={{ gap: 10 }}>
      <p className="muted" style={{ margin: 0 }}>
        {withEmail.length > 0
          ? `${withEmail.length} selected candidate${withEmail.length === 1 ? '' : 's'} will get their own link and sign in with Google using their email.`
          : 'No one selected from the list — add people by email below.'}
        {noEmail > 0 && ` ${noEmail} without an email can't take it and are skipped.`}
      </p>
      <div className="row" style={{ gap: 12, alignItems: 'flex-end' }}>
        <label className="field" style={{ flex: '1 1 320px' }}>
          Interview
          <select className="select" value={chosen} onChange={(e) => setChosen(e.target.value)}>
            {interviews.map((a) => (
              <option key={a.id} value={a.id}>
                {a.title} ({a.questionCount} questions, {a.durationMinutes} min)
              </option>
            ))}
          </select>
        </label>
        <label className="field" style={{ maxWidth: 140 }}>
          Days to finish
          <input className="input" type="number" min={1} max={14} value={days} onChange={(e) => setDays(Math.max(1, Math.min(14, Number(e.target.value) || 1)))} />
        </label>
      </div>
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
      <label className="row" style={{ gap: 6 }}>
        <input type="checkbox" checked={email} onChange={(e) => setEmail(e.target.checked)} /> Email them the link from my Gmail
      </label>
      {!email && <span className="muted" style={{ fontSize: 13 }}>Their links will be listed here after sending, to copy into WhatsApp or your own email.</span>}
      {needsGoogle && (
        <ConnectGoogle
          status={google}
          purpose="The links are emailed from your own Gmail. Your selection is kept while you connect."
          returnTo={window.location.pathname}
          onConnect={onConnectGoogle}
        />
      )}
      <div>
        <Button disabled={!!busy || !chosen || total === 0 || extras.invalid.length > 0 || needsGoogle} onClick={() => void send()}>
          {busy ?? `Send AI interview to ${total}`}
        </Button>
      </div>
      {outcome && (
        <div className={`alert ${outcome.failed.length ? 'alert-error' : 'alert-info'} stack`} role="status" style={{ gap: 6 }}>
          {outcome.sent.length > 0 && (
            <div>
              Sent to {outcome.sent.length}
              {email ? ' (emailed)' : ''}: {outcome.sent.map((s) => s.name).join(', ')}.
            </div>
          )}
          {outcome.sent.length > 0 && !email && (
            <div className="stack" style={{ gap: 4 }}>
              <textarea
                className="input"
                readOnly
                rows={Math.min(8, outcome.sent.length + 1)}
                aria-label="Interview links"
                value={outcome.sent.map((s) => `${s.name}: ${s.link}`).join('\n')}
              />
              <div>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => {
                    void navigator.clipboard?.writeText(outcome.sent.map((s) => `${s.name}: ${s.link}`).join('\n'))
                    setCopied(true)
                  }}
                >
                  {copied ? 'Copied' : 'Copy all links'}
                </Button>
              </div>
            </div>
          )}
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
    </div>
  )
}
