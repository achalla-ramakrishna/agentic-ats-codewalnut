import { useEffect, useState } from 'react'
import { getWhatsAppStatus, postMessage } from '../api/messages'
import { fillTemplate } from './emailTemplates'
import { Badge, Button } from './ui'

export interface WhatsAppRecipient {
  applicationId: string
  name: string
  phone: string | null
}

/** Starting text; {firstName}, {jobTitle} and {senderName} are filled per person. */
export const WHATSAPP_DRAFT =
  'Hi {firstName}, this is {senderName} from CodeWalnut. Thank you for sending your résumé for the {jobTitle} role. ' +
  'We would like to set up a short interview on Google Meet. Which of these suits you: [day, time] or [day, time]? ' +
  'I will send the calendar invite with the Meet link once you confirm.'

/**
 * WhatsApp a few chosen candidates, one click each (PIPE-20). Without the WhatsApp Business API each
 * click opens WhatsApp with the message ready and the recruiter presses Send there; nothing is sent
 * in bulk from a personal number. Each message is saved in the candidate's conversation.
 */
export function BulkWhatsApp({
  recipients,
  jobTitle,
  senderName,
  onClose,
}: {
  recipients: WhatsAppRecipient[]
  jobTitle: string
  senderName: string | null
  onClose: () => void
}) {
  const sender = senderName ?? 'CodeWalnut Talent Team'
  const [text, setText] = useState(WHATSAPP_DRAFT)
  const [apiEnabled, setApiEnabled] = useState(false)
  const [done, setDone] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState<string | null>(null)

  useEffect(() => {
    getWhatsAppStatus()
      .then((s) => setApiEnabled(s.apiEnabled))
      .catch(() => setApiEnabled(false))
  }, [])

  async function open(r: WhatsAppRecipient) {
    // Open the tab now, while the click still counts (browsers block pop-ups after a network call).
    const tab = apiEnabled ? null : window.open('about:blank', '_blank')
    setBusy(r.applicationId)
    try {
      const sent = await postMessage(r.applicationId, {
        channel: 'CANDIDATE',
        body: fillTemplate(text, { candidateName: r.name, jobTitle, senderName: sender }),
        sendEmail: false,
        sendWhatsApp: true,
      })
      if (sent.whatsappLink) {
        if (tab) tab.location.href = sent.whatsappLink
        else window.open(sent.whatsappLink, '_blank')
        setDone((d) => ({ ...d, [r.applicationId]: 'Opened' }))
      } else {
        tab?.close()
        setDone((d) => ({ ...d, [r.applicationId]: 'Sent' }))
      }
    } catch (e) {
      tab?.close()
      setDone((d) => ({ ...d, [r.applicationId]: e instanceof Error ? e.message : 'Not sent' }))
    } finally {
      setBusy(null)
    }
  }

  const unfilled = text.includes('[day, time]')
  return (
    <div className="stack" style={{ gap: 10 }}>
      <h3 style={{ margin: 0 }}>WhatsApp the selected candidates</h3>
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        {apiEnabled
          ? 'Each click sends the message from CodeWalnut’s WhatsApp Business number.'
          : 'Each click opens WhatsApp with the message ready for that person; press Send there, then come back for the next one.'}{' '}
        {'{firstName}'} becomes their first name. Best for people who already replied; don’t use this for cold outreach.
      </p>
      <label className="field">
        Message
        <textarea className="input" rows={6} value={text} onChange={(e) => setText(e.target.value)} />
      </label>
      {unfilled && (
        <div className="alert alert-error" role="note">
          Replace both <strong>[day, time]</strong> in the message with real slots (e.g. Tue 14 Oct, 11:00 am), or remove that
          sentence. The WhatsApp buttons switch on once it’s done.
        </div>
      )}
      <table className="send-list" aria-label="WhatsApp recipients">
        <tbody>
          {recipients.map((r) => (
            <tr key={r.applicationId}>
              <td>
                <strong>{r.name}</strong>
              </td>
              <td className="muted">{r.phone ?? 'No mobile number'}</td>
              <td style={{ textAlign: 'right' }}>
                {done[r.applicationId] && (
                  <Badge tone={done[r.applicationId] === 'Opened' || done[r.applicationId] === 'Sent' ? 'primary' : 'danger'}>
                    {done[r.applicationId]}
                  </Badge>
                )}{' '}
                <Button
                  size="sm"
                  variant={done[r.applicationId] ? 'ghost' : 'secondary'}
                  disabled={!r.phone || busy !== null || unfilled || !text.trim()}
                  onClick={() => void open(r)}
                  aria-label={`WhatsApp ${r.name}`}
                  title={!r.phone ? 'No mobile number on file' : unfilled ? 'Fill in the [day, time] slots first' : undefined}
                >
                  {busy === r.applicationId ? 'Opening…' : apiEnabled ? 'Send' : 'Open WhatsApp'}
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="row">
        <Button variant="ghost" onClick={onClose}>
          Done
        </Button>
      </div>
    </div>
  )
}
