import { useEffect, useState } from 'react'
import { whatsAppTest } from '../api/assessments'
import { getWhatsAppStatus } from '../api/messages'
import { Button } from './ui'

export interface WhatsAppPerson {
  inviteId: string
  name: string
}

type State = { kind: 'sent' } | { kind: 'failed'; error: string }

/**
 * Sends each person their AI interview (or test) link on WhatsApp (ASMT-43). With the Business API: one click sends
 * to everyone. Without it, each click opens WhatsApp with the message and their link typed in; press Send there.
 */
export function WhatsAppLinks({ people, onClose }: { people: WhatsAppPerson[]; onClose: () => void }) {
  const [api, setApi] = useState<boolean | null>(null)
  const [state, setState] = useState<Record<string, State>>({})
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    getWhatsAppStatus()
      .then((s) => setApi(s.apiEnabled))
      .catch(() => setApi(false))
  }, [])

  async function one(p: WhatsAppPerson) {
    // The browser only allows a new window straight from the click, so open it before asking the server.
    const win = api ? null : window.open('about:blank', '_blank')
    try {
      const r = await whatsAppTest(p.inviteId)
      const link = r.message.whatsappLink
      if (link) {
        if (win) win.location.href = link
        else window.open(link, '_blank')
      } else {
        win?.close()
      }
      setState((s) => ({ ...s, [p.inviteId]: { kind: 'sent' } }))
    } catch (e) {
      win?.close()
      setState((s) => ({ ...s, [p.inviteId]: { kind: 'failed', error: e instanceof Error ? e.message : 'Could not send' } }))
    }
  }

  async function all() {
    setBusy(true)
    for (const p of people.filter((x) => state[x.inviteId]?.kind !== 'sent')) await one(p)
    setBusy(false)
  }

  const done = people.filter((p) => state[p.inviteId]?.kind === 'sent').length
  return (
    <div className="card stack" role="region" aria-label="WhatsApp the link" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>💬 WhatsApp the link ({done} of {people.length} done)</strong>
        <Button size="sm" variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
      <span className="muted" style={{ fontSize: 13 }}>
        {api
          ? 'Sent from your WhatsApp Business number, with each person’s own link.'
          : 'Each click opens WhatsApp (Web or the app) with the message and that person’s own link typed in. Press Send there, then come back for the next. The message is saved in the candidate’s chat.'}
      </span>
      {api && (
        <Button size="sm" disabled={busy || done === people.length} onClick={() => void all()}>
          Send to all {people.length - done}
        </Button>
      )}
      <ul className="stack" style={{ listStyle: 'none', padding: 0, margin: 0, gap: 4 }}>
        {people.map((p) => {
          const s = state[p.inviteId]
          return (
            <li key={p.inviteId} className="row" style={{ gap: 8, alignItems: 'center' }}>
              <Button size="sm" variant={s?.kind === 'sent' ? 'ghost' : 'secondary'} disabled={busy || api === null} onClick={() => void one(p)}>
                {s?.kind === 'sent' ? '✓ Again' : '💬 WhatsApp'}
              </Button>
              <span>{p.name}</span>
              {s?.kind === 'failed' && <span className="intake-error">{s.error}</span>}
            </li>
          )
        })}
      </ul>
    </div>
  )
}
