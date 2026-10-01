import { useCallback, useEffect, useState } from 'react'
import { getShare, revokeShare, saveShare, type ClientShare } from '../api/clients'
import { DOCUMENT_LABELS, listDocuments, type CandidateDocument } from '../api/tracker'
import { Badge, Button } from './ui'

/**
 * Choose exactly what the client sees about this candidate (docs/features/client-access.md).
 * Name, opening and stage are always shared; everything else only when ticked.
 */
export function ShareWithClient({
  applicationId,
  candidateId,
  clientName,
  canShare,
  onChanged,
}: {
  applicationId: string
  candidateId: string
  clientName: string
  canShare: boolean
  onChanged: () => void
}) {
  const [share, setShare] = useState<ClientShare | null>(null)
  const [documents, setDocuments] = useState<CandidateDocument[]>([])
  const [editing, setEditing] = useState(false)
  const [contact, setContact] = useState(true)
  const [profile, setProfile] = useState(true)
  const [picked, setPicked] = useState<string[]>([])
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    getShare(applicationId).then(setShare).catch(() => setShare(null))
    listDocuments(candidateId).then(setDocuments).catch(() => setDocuments([]))
  }, [applicationId, candidateId])
  useEffect(load, [load])

  // The newest version of each kind.
  const newest = (docs: CandidateDocument[]) => docs.filter((d, i) => docs.findIndex((x) => x.kind === d.kind) === i)
  const latest = newest(documents)
  const sharedLabels = share?.active
    ? [
        share.includeContact ? 'contact details' : null,
        share.includeProfile ? 'profile' : null,
        ...documents.filter((d) => share.documentIds.includes(d.id)).map((d) => DOCUMENT_LABELS[d.kind]),
      ].filter(Boolean)
    : []

  async function startEditing() {
    // Documents may have been uploaded since the drawer opened.
    let docs = documents
    try {
      docs = await listDocuments(candidateId)
      setDocuments(docs)
    } catch {
      // keep what we have
    }
    const current = newest(docs)
    setContact(share?.active ? share.includeContact : true)
    setProfile(share?.active ? share.includeProfile : true)
    setPicked(share?.active ? current.filter((d) => share.documentIds.includes(d.id)).map((d) => d.id) : current.map((d) => d.id))
    setNote(share?.note ?? '')
    setEditing(true)
  }

  async function onSave() {
    setError(null)
    try {
      setShare(await saveShare(applicationId, { includeContact: contact, includeProfile: profile, documentIds: picked, note }))
      setEditing(false)
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not share')
    }
  }

  async function onStop() {
    setError(null)
    try {
      setShare(await revokeShare(applicationId))
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not stop sharing')
    }
  }

  return (
    <div className="card stack" style={{ padding: 12, gap: 8 }} aria-label={`Share with ${clientName}`}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>Share with {clientName}</strong>
        {share?.active ? <Badge tone="primary">Shared</Badge> : <Badge>Not shared</Badge>}
      </div>
      {share?.active && (
        <div style={{ fontSize: 13 }}>
          {clientName} sees: name, opening, stage{sharedLabels.length ? `, ${sharedLabels.join(', ')}` : ''}.
          <div className="muted">
            Shared by {share.sharedBy} on {new Date(share.sharedAt!).toLocaleDateString()}
            {share.lastViewedAt ? ` · ${clientName} last viewed ${new Date(share.lastViewedAt).toLocaleString()}` : ` · not viewed yet`}
          </div>
        </div>
      )}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {editing && (
        <div className="stack" style={{ gap: 6 }}>
          <label className="row" style={{ gap: 8, fontSize: 14, flexWrap: 'nowrap', alignItems: 'flex-start' }}>
            <input type="checkbox" checked={contact} onChange={(e) => setContact(e.target.checked)} />
            Contact details (email, mobile)
          </label>
          <label className="row" style={{ gap: 8, fontSize: 14, flexWrap: 'nowrap', alignItems: 'flex-start' }}>
            <input type="checkbox" checked={profile} onChange={(e) => setProfile(e.target.checked)} />
            Profile (date of birth, addresses, education, emergency contact)
          </label>
          {latest.map((d) => (
            <label key={d.id} className="row" style={{ gap: 8, fontSize: 14, flexWrap: 'nowrap', alignItems: 'flex-start' }}>
              <input
                type="checkbox"
                checked={picked.includes(d.id)}
                onChange={(e) => setPicked(e.target.checked ? [...picked, d.id] : picked.filter((x) => x !== d.id))}
              />
              {DOCUMENT_LABELS[d.kind]} <span className="muted">({d.fileName})</span>
            </label>
          ))}
          <label className="field">
            Note for {clientName} (optional)
            <input className="input" value={note} maxLength={1000} onChange={(e) => setNote(e.target.value)} placeholder="e.g. Documents for background verification" />
          </label>
          <span className="muted" style={{ fontSize: 12 }}>
            {clientName}'s contacts see this when they sign in. Share only what they need; you can stop sharing at any time.
          </span>
          <div className="row">
            <Button size="sm" onClick={() => void onSave()}>
              {share?.active ? 'Update sharing' : `Share with ${clientName}`}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>
              Cancel
            </Button>
          </div>
        </div>
      )}
      {canShare && !editing && (
        <div className="row">
          <Button size="sm" variant="secondary" onClick={() => void startEditing()}>
            {share?.active ? 'Change what is shared' : `Share with ${clientName}…`}
          </Button>
          {share?.active && (
            <Button size="sm" variant="ghost" onClick={() => void onStop()}>
              Stop sharing
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
