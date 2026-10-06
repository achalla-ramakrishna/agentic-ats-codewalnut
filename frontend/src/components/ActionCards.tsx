import { useState } from 'react'
import { Link } from 'react-router-dom'
import { decideAction, type Conversation, type ProposedAction } from '../api/ask'
import { Badge, Button } from './ui'

const TYPE_LABEL: Record<ProposedAction['type'], string> = {
  MOVE_STAGE: 'Move stage',
  ADD_NOTE: 'Note',
  LOG_CONTACT: 'Log contact',
  REMIND_TEST: 'Test reminder',
  MESSAGE: 'Message',
  SHARE_WITH_CLIENT: 'Share with client',
}

const STATUS_TONE = { DONE: 'primary', FAILED: 'danger', SKIPPED: 'neutral', PENDING: 'neutral' } as const
const STATUS_LABEL = { DONE: 'Done', FAILED: 'Failed', SKIPPED: 'Skipped', PENDING: 'Waiting for you' } as const

/** WhatsApp by click-to-chat needs a tab opened during the click itself. */
const opensWhatsApp = (a: ProposedAction, waApi: boolean) => !waApi && a.params.sendWhatsApp === 'true'

/**
 * Cards for the actions Ask ATS proposed (ASK-06…ASK-09). Each runs only when the person clicks
 * Do it; messages can be edited first. Results stay on the card.
 */
export function ActionCards({
  conversationId,
  actions,
  waApi,
  onChange,
}: {
  conversationId: string
  actions: ProposedAction[]
  waApi: boolean
  onChange: (c: Conversation) => void
}) {
  const [edits, setEdits] = useState<Record<string, { subject?: string; body?: string }>>({})
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const pending = actions.filter((a) => a.status === 'PENDING')
  const batchable = pending.filter((a) => !opensWhatsApp(a, waApi))

  async function decide(a: ProposedAction, decision: 'do' | 'skip') {
    setError(null)
    const tab = decision === 'do' && opensWhatsApp(a, waApi) ? window.open('about:blank', '_blank') : null
    setBusy(a.id)
    try {
      const edit = edits[a.id] ?? {}
      const out = await decideAction(conversationId, a.id, { decision, subject: edit.subject, body: edit.body })
      if (out.whatsappLink) {
        if (tab) tab.location.href = out.whatsappLink
        else window.open(out.whatsappLink, '_blank')
      } else {
        tab?.close()
      }
      onChange(out.conversation)
      return true
    } catch (e) {
      tab?.close()
      setError(e instanceof Error ? e.message : 'That did not work')
      return false
    } finally {
      setBusy(null)
    }
  }

  async function doAll() {
    for (const a of batchable) {
      if (!(await decide(a, 'do'))) break
    }
  }

  return (
    <div className="ask-actions stack" style={{ gap: 8 }} aria-label="Proposed actions">
      <div className="row" style={{ gap: 8, justifyContent: 'space-between' }}>
        <strong>
          {pending.length > 0 ? `${pending.length} action${pending.length === 1 ? '' : 's'} for you to confirm` : 'Actions'}
        </strong>
        {batchable.length > 1 && (
          <Button size="sm" disabled={!!busy} onClick={() => void doAll()}>
            Do all {batchable.length}
          </Button>
        )}
      </div>
      {pending.length > batchable.length && batchable.length > 1 && (
        <span className="muted" style={{ fontSize: 12 }}>
          WhatsApp messages open one at a time: click Do it on each.
        </span>
      )}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {actions.map((a) => {
        const edit = edits[a.id] ?? {}
        const isMessage = a.type === 'MESSAGE'
        return (
          <div key={a.id} className={`ask-action ask-action-${a.status.toLowerCase()}`} aria-label={a.summary}>
            <div className="row" style={{ gap: 8, justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div className="stack" style={{ gap: 2, minWidth: 0 }}>
                <span className="muted" style={{ fontSize: 12 }}>
                  {TYPE_LABEL[a.type]} ·{' '}
                  <Link to={`/jobs/${a.jobId}?candidate=${a.applicationId}`}>
                    {a.candidateName}, {a.jobTitle}
                  </Link>
                </span>
                <span>{a.summary}</span>
              </div>
              <Badge tone={STATUS_TONE[a.status]}>{STATUS_LABEL[a.status]}</Badge>
            </div>
            {isMessage && a.status === 'PENDING' && (
              <div className="stack" style={{ gap: 6, marginTop: 6 }}>
                {a.params.sendEmail === 'true' && (
                  <input
                    className="input"
                    aria-label={`Subject for ${a.candidateName}`}
                    placeholder="Subject"
                    value={edit.subject ?? a.params.subject ?? ''}
                    onChange={(e) => setEdits({ ...edits, [a.id]: { ...edit, subject: e.target.value } })}
                  />
                )}
                <textarea
                  className="input"
                  aria-label={`Message to ${a.candidateName}`}
                  rows={4}
                  style={{ fontFamily: 'inherit' }}
                  value={edit.body ?? a.params.body ?? ''}
                  onChange={(e) => setEdits({ ...edits, [a.id]: { ...edit, body: e.target.value } })}
                />
              </div>
            )}
            {isMessage && a.status !== 'PENDING' && a.params.body && <p className="muted ask-action-body">{a.params.body}</p>}
            {a.status === 'PENDING' ? (
              <div className="row" style={{ gap: 6, marginTop: 6 }}>
                <Button
                  size="sm"
                  disabled={!!busy || (isMessage && !(edit.body ?? a.params.body ?? '').trim())}
                  onClick={() => void decide(a, 'do')}
                >
                  {busy === a.id ? 'Working…' : opensWhatsApp(a, waApi) ? 'Do it (opens WhatsApp)' : 'Do it'}
                </Button>
                <Button size="sm" variant="ghost" disabled={!!busy} onClick={() => void decide(a, 'skip')}>
                  Skip
                </Button>
              </div>
            ) : (
              a.result && (
                <span className="muted" style={{ fontSize: 12 }}>
                  {a.result}
                </span>
              )
            )}
          </div>
        )
      })}
    </div>
  )
}
