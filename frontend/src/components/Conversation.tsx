import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { getGoogleStatus, type GoogleStatus } from '../api/interviews'
import { getWhatsAppStatus, listMessages, postMessage, whatsappLabel, type Message, type MessageChannel } from '../api/messages'
import { ConnectGoogle } from './ConnectGoogle'
import { EMAIL_TEMPLATES, fillTemplate } from './emailTemplates'
import { Button } from './ui'
import './chat.css'

const REFRESH_MS = 20_000

/**
 * One conversation for an application. CANDIDATE: staff and the candidate, optionally emailed
 * from the sender's Gmail. TEAM: internal only. (docs/features/communication.md)
 */
export function Conversation({
  applicationId,
  channel,
  candidateName,
  candidateEmail,
  candidatePhone = null,
  clientName,
  jobTitle,
  me,
  initialTemplate,
  onSent,
}: {
  applicationId: string
  channel: MessageChannel
  candidateName: string
  candidateEmail: string | null
  candidatePhone?: string | null
  clientName?: string | null
  jobTitle: string
  me: { email: string; name: string | null }
  initialTemplate?: string
  onSent?: () => void
}) {
  const isCandidate = channel === 'CANDIDATE'
  const fill = useCallback(
    (text: string) => fillTemplate(text, { candidateName, jobTitle, senderName: me.name ?? 'CodeWalnut Talent Team' }),
    [candidateName, jobTitle, me.name],
  )
  const start = EMAIL_TEMPLATES.find((t) => t.key === initialTemplate)
  const defaultSubject = fill(EMAIL_TEMPLATES[0].subject)
  const [messages, setMessages] = useState<Message[] | null>(null)
  const [template, setTemplate] = useState(start?.key ?? '')
  const [subject, setSubject] = useState(start ? fill(start.subject) : defaultSubject)
  const [body, setBody] = useState(start ? fill(start.body) : '')
  const [sendEmail, setSendEmail] = useState(isCandidate && !!candidateEmail)
  const [sendWhatsApp, setSendWhatsApp] = useState(isCandidate && !!candidatePhone)
  const [whatsAppApi, setWhatsAppApi] = useState<{ apiEnabled: boolean; repliesEnabled: boolean } | null>(null)
  const [google, setGoogle] = useState<GoogleStatus | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const listRef = useRef<HTMLUListElement>(null)

  const load = useCallback(() => {
    listMessages(applicationId, channel)
      .then(setMessages)
      .catch(() => setMessages((m) => m ?? []))
  }, [applicationId, channel])

  useEffect(() => {
    load()
    const timer = window.setInterval(load, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [load])

  useEffect(() => {
    if (!isCandidate) return
    getGoogleStatus().then(setGoogle).catch(() => setGoogle(null))
    getWhatsAppStatus().then(setWhatsAppApi).catch(() => setWhatsAppApi(null))
  }, [isCandidate])

  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [messages])

  function applyTemplate(key: string) {
    setTemplate(key)
    const t = EMAIL_TEMPLATES.find((x) => x.key === key)
    if (!t) return
    setSubject(fill(t.subject))
    setBody(fill(t.body))
  }

  const needsGoogle = sendEmail && google !== null && (!google.available || !google.mailConnected)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!body.trim()) return
    setError(null)
    setNotice(null)
    setBusy(true)
    const whatsApp = isCandidate && sendWhatsApp
    // Without the Business API, WhatsApp opens in a new tab. Open it now, while the click still
    // counts as the user's action (browsers block pop-ups opened after a network call).
    const waWindow = whatsApp && !whatsAppApi?.apiEnabled ? window.open('about:blank', '_blank') : null
    try {
      const sent = await postMessage(applicationId, {
        channel,
        body,
        subject: sendEmail ? subject : undefined,
        sendEmail: isCandidate && sendEmail,
        sendWhatsApp: whatsApp,
      })
      if (sent.whatsappLink) {
        if (waWindow) waWindow.location.href = sent.whatsappLink
        else window.open(sent.whatsappLink, '_blank')
      } else {
        waWindow?.close()
      }
      setBody('')
      setSubject(defaultSubject)
      setTemplate('')
      const parts = [
        sent.emailed ? `Emailed to ${candidateEmail} from your Gmail.` : null,
        sent.whatsapp === 'OPENED' ? 'WhatsApp opened in a new tab with your message ready: press Send there.' : null,
        sent.whatsapp === 'SENT' ? "Sent on WhatsApp from CodeWalnut's number." : null,
        ...(sent.warnings ?? []),
      ].filter(Boolean)
      setNotice(parts.length ? parts.join(' ') : null)
      load()
      onSent?.()
    } catch (e) {
      waWindow?.close()
      if (e instanceof ApiError && e.status === 428) {
        setGoogle((g) => ({ available: true, calendarConnected: g?.calendarConnected ?? false, mailConnected: false, redirectUri: g?.redirectUri ?? '' }))
      } else {
        setError(e instanceof Error ? e.message : 'Could not send')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="chat">
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        {isCandidate
          ? `Messages with ${candidateName}. They read and reply in their CodeWalnut candidate page${candidateEmail ? '' : ' (add their email so they can sign in)'}.`
          : channel === 'CLIENT'
            ? `Messages with ${clientName ?? 'the client'}'s team about ${candidateName}. They see this once you share the candidate with them; the candidate never does.`
            : 'Internal discussion. Only CodeWalnut staff see this, never the candidate.'}
      </p>
      {messages && messages.length === 0 && <span className="muted">No messages yet.</span>}
      {messages && messages.length > 0 && (
        <ul
          className="chat-list"
          ref={listRef}
          aria-label={isCandidate ? 'Candidate conversation' : channel === 'CLIENT' ? 'Client conversation' : 'Team conversation'}
        >
          {messages.map((m) => {
            const mine = m.authorType === 'STAFF' && m.authorEmail === me.email
            const who = m.authorType === 'CANDIDATE' ? candidateName : (m.authorName ?? m.authorEmail)
            return (
              <li key={m.id} className={`chat-msg${mine ? ' mine' : ''}`}>
                {m.subject && <div className="chat-subject">{m.subject}</div>}
                <div className="chat-body">{m.body}</div>
                <div className="chat-meta">
                  {mine ? 'You' : who} · {new Date(m.createdAt).toLocaleString()}
                  {m.emailed ? ' · emailed' : ''}
                  {whatsappLabel(m.whatsapp) ? ` · ${whatsappLabel(m.whatsapp)}` : ''}
                </div>
              </li>
            )
          })}
        </ul>
      )}
      {notice && <div className="alert alert-info">{notice}</div>}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <form
        className="stack"
        onSubmit={onSubmit}
        aria-label={isCandidate ? 'Message candidate' : channel === 'CLIENT' ? 'Message client' : 'Message team'}
        style={{ gap: 8 }}
      >
        {isCandidate && (
          <label className="field">
            Start from a template
            <select className="select" value={template} onChange={(e) => applyTemplate(e.target.value)}>
              <option value="">Choose…</option>
              {EMAIL_TEMPLATES.map((t) => (
                <option key={t.key} value={t.key}>
                  {t.label}
                </option>
              ))}
            </select>
          </label>
        )}
        {isCandidate && sendEmail && (
          <label className="field">
            Email subject
            <input className="input" value={subject} maxLength={300} onChange={(e) => setSubject(e.target.value)} />
          </label>
        )}
        <label className="field">
          {isCandidate ? 'Message' : channel === 'CLIENT' ? `Message to ${clientName ?? 'the client'}` : 'Message to the team'}
          <textarea
            className="textarea"
            style={{ minHeight: isCandidate ? 140 : 70, fontFamily: 'inherit', fontSize: 14 }}
            value={body}
            maxLength={10_000}
            onChange={(e) => setBody(e.target.value)}
            placeholder={
              isCandidate
                ? 'Write to the candidate…'
                : channel === 'CLIENT'
                  ? 'e.g. The Aadhaar and CodeWalnut résumé are now shared with you.'
                  : 'e.g. @Priya can you take the technical round on Friday?'
            }
          />
        </label>
        {isCandidate && (
          <div className="stack" style={{ gap: 4 }} role="group" aria-label="Also send by">
            <span className="muted" style={{ fontSize: 13 }}>
              Always saved here and shown on their candidate page. Also send by:
            </span>
            <label className="row" style={{ gap: 8, fontSize: 14, flexWrap: 'nowrap', alignItems: 'flex-start' }}>
              <input type="checkbox" checked={sendEmail} disabled={!candidateEmail} onChange={(e) => setSendEmail(e.target.checked)} />
              {candidateEmail ? `Email to ${candidateEmail} (from my Gmail)` : 'Email: no email address on file'}
            </label>
            <label className="row" style={{ gap: 8, fontSize: 14, flexWrap: 'nowrap', alignItems: 'flex-start' }}>
              <input type="checkbox" checked={sendWhatsApp} disabled={!candidatePhone} onChange={(e) => setSendWhatsApp(e.target.checked)} />
              {candidatePhone ? `WhatsApp to ${candidatePhone}` : 'WhatsApp: no mobile number on file'}
            </label>
            {sendWhatsApp && (
              <span className="muted" style={{ fontSize: 12, paddingLeft: 24 }}>
                {whatsAppApi?.apiEnabled
                  ? `Sent from CodeWalnut's WhatsApp Business number.${whatsAppApi.repliesEnabled ? ' Replies appear here.' : ''}`
                  : 'Opens WhatsApp (app or web) with your message ready; press Send there. Replies arrive in your WhatsApp.'}
              </span>
            )}
          </div>
        )}
        {needsGoogle && (
          <ConnectGoogle
            status={google}
            purpose="Emails are sent from your own Gmail, so replies come back to your inbox. Or untick email to send in-app only."
            returnTo={`${window.location.pathname}?candidate=${applicationId}&tab=candidate`}
          />
        )}
        <div>
          <Button type="submit" size="sm" disabled={busy || !body.trim() || needsGoogle || (sendEmail && !subject.trim())}>
            {busy
              ? 'Sending…'
              : isCandidate && sendEmail && sendWhatsApp
                ? 'Send email & WhatsApp'
                : isCandidate && sendEmail
                  ? 'Send email'
                  : isCandidate && sendWhatsApp
                    ? 'Send WhatsApp'
                    : 'Send'}
          </Button>
        </div>
      </form>
    </div>
  )
}
