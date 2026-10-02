import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { addNote, getHistory, type ApplicationRow, type HistoryEvent } from '../api/tracker'
import { useMe } from '../auth/AuthContext'
import { Conversation } from './Conversation'
import { DocumentsSection } from './DocumentsSection'
import { InterviewsPanel } from './InterviewsPanel'
import { ProfileSection } from './ProfileSection'
import { ShareWithClient } from './ShareWithClient'
import { Button } from './ui'
import { useStages } from './useStages'
import './chat.css'

function describe(event: HistoryEvent, label: (s: string | null) => string) {
  switch (event.type) {
    case 'CREATED':
      return `Added at ${label(event.toStage)}`
    case 'STAGE_CHANGED':
      return `${label(event.fromStage)} → ${label(event.toStage)}`
    case 'INTERVIEW_SCHEDULED':
      return 'Interview scheduled'
    case 'INTERVIEW_CANCELLED':
      return 'Interview cancelled'
    case 'EMAIL_SENT':
      return 'Email sent'
    case 'DOCS_REQUESTED':
      return 'Documents requested'
    case 'DOC_UPLOADED':
      return 'Document uploaded'
    case 'SHARED_WITH_CLIENT':
      return 'Client sharing changed'
    default:
      return 'Note'
  }
}

export type DrawerTab = 'profile' | 'candidate' | 'client' | 'team'

/** Everything about one candidate in one opening: profile, documents, sharing, interviews, chats, history. */
export function CandidateDrawer({
  row,
  canEdit,
  onClose,
  onChanged,
  initialTab = 'profile',
  initialTemplate,
}: {
  row: ApplicationRow
  canEdit: boolean
  onClose: () => void
  onChanged: () => void
  initialTab?: DrawerTab
  initialTemplate?: string
}) {
  const me = useMe()
  const canMessage = me.capabilities.includes('MESSAGE_CANDIDATES')
  const canSeeIds = me.capabilities.includes('VIEW_ID_DOCUMENTS')
  const canShare = me.capabilities.includes('SHARE_WITH_CLIENTS')
  const hasClient = row.clientName !== null && row.clientName !== undefined
  const [tab, setTab] = useState<DrawerTab>(canMessage && (initialTab !== 'client' || hasClient) ? initialTab : 'profile')
  const [template, setTemplate] = useState(initialTemplate)
  const stages = useStages()
  const label = (s: string | null) => stages.find((x) => x.key === s)?.label ?? s ?? ''
  const [history, setHistory] = useState<HistoryEvent[]>([])
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [contact, setContact] = useState({ name: row.name, email: row.email, phone: row.phone })

  const load = useCallback(() => {
    getHistory(row.id).then(setHistory).catch(() => undefined)
  }, [row.id])
  useEffect(load, [load])

  const changed = useCallback(() => {
    load()
    onChanged()
  }, [load, onChanged])

  async function onNote(event: FormEvent) {
    event.preventDefault()
    if (!note.trim()) return
    setError(null)
    try {
      await addNote(row.id, note.trim())
      setNote('')
      changed()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save the note')
    }
  }

  const tabs: [DrawerTab, string][] = [
    ['profile', 'Profile'],
    ['candidate', 'Chat with candidate'],
    ...(hasClient ? ([['client', `Chat with ${row.clientName}`]] as [DrawerTab, string][]) : []),
    ['team', 'Team chat'],
  ]

  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} />
      <aside className="drawer" aria-label={`Candidate ${row.name}`}>
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <div>
            <h2>{contact.name}</h2>
            <div className="muted">
              {row.jobTitle}
              {hasClient ? ` · ${row.clientName}` : ''}
            </div>
          </div>
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
        </div>
        {canMessage && (
          <div className="tabs" role="tablist" aria-label="Candidate sections">
            {tabs.map(([key, text]) => (
              <button key={key} type="button" role="tab" aria-selected={tab === key} onClick={() => setTab(key)}>
                {text}
              </button>
            ))}
          </div>
        )}
        {tab !== 'profile' && (
          <Conversation
            key={`${tab}-${template ?? ''}`}
            applicationId={row.id}
            channel={tab === 'candidate' ? 'CANDIDATE' : tab === 'client' ? 'CLIENT' : 'TEAM'}
            candidateName={contact.name}
            candidateEmail={contact.email}
            candidatePhone={contact.phone}
            clientName={row.clientName}
            jobTitle={row.jobTitle}
            me={{ email: me.email, name: me.name }}
            initialTemplate={tab === 'candidate' ? template : undefined}
            onSent={changed}
          />
        )}
        {tab === 'profile' && (
          <>
            <ProfileSection
              candidateId={row.candidateId}
              stageLabel={row.stageLabel}
              canEdit={canEdit}
              onSaved={(p) => {
                setContact({ name: p.name, email: p.email, phone: p.phone })
                onChanged()
              }}
            />
            {error && (
              <div role="alert" className="alert alert-error">
                {error}
              </div>
            )}
            <DocumentsSection
              candidateId={row.candidateId}
              candidateEmail={contact.email}
              canEdit={canEdit}
              canSeeIds={canSeeIds}
              onChanged={changed}
              onRequested={() => {
                if (canMessage) {
                  setTemplate('documents')
                  setTab('candidate')
                }
              }}
            />
            {hasClient && (
              <ShareWithClient
                applicationId={row.id}
                candidateId={row.candidateId}
                clientName={row.clientName!}
                canShare={canShare}
                onChanged={changed}
              />
            )}
            <InterviewsPanel
              applicationId={row.id}
              candidateName={contact.name}
              candidateEmail={contact.email}
              jobTitle={row.jobTitle}
              canEdit={canEdit}
              onChanged={changed}
            />
            {canEdit && (
              <form className="stack" onSubmit={onNote} style={{ gap: 8 }}>
                <label className="field">
                  Add a note
                  <textarea
                    className="textarea"
                    style={{ minHeight: 70, fontFamily: 'inherit', fontSize: 14 }}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="e.g. Strong in React, available from 1 Nov"
                  />
                </label>
                <div>
                  <Button type="submit" size="sm" disabled={!note.trim()}>
                    Save note
                  </Button>
                </div>
              </form>
            )}
            <div className="stack" style={{ gap: 8 }}>
              <strong>History</strong>
              <ul className="timeline">
                {history.map((h) => (
                  <li key={h.id}>
                    <div>{describe(h, label)}</div>
                    {h.note && <div>{h.note}</div>}
                    <div className="meta">
                      {h.actorEmail ?? 'system'} · {new Date(h.createdAt).toLocaleString()}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          </>
        )}
      </aside>
    </>
  )
}
