import { useCallback, useEffect, useState } from 'react'
import { listDocumentRequests, requestDocuments, type DocumentRequest } from '../api/profile'
import {
  BGV_KINDS,
  DOCUMENT_LABELS,
  documentUrl,
  ID_KINDS,
  listDocuments,
  uploadDocument,
  type CandidateDocument,
  type DocumentKind,
} from '../api/tracker'
import { Button } from './ui'

function DocumentSlot({
  kind,
  documents,
  request,
  canEdit,
  candidateEmail,
  onUpload,
}: {
  kind: DocumentKind
  documents: CandidateDocument[]
  request: DocumentRequest | undefined
  canEdit: boolean
  candidateEmail: string | null
  onUpload: (kind: DocumentKind, file: File) => Promise<void>
}) {
  const versions = documents.filter((d) => d.kind === kind)
  const current = versions[0]
  const [busy, setBusy] = useState(false)
  const inputId = `upload-${kind}`
  const images = kind !== 'ORIGINAL_RESUME' && kind !== 'CODEWALNUT_RESUME'

  return (
    <div className="card stack" style={{ padding: 12, gap: 6 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>{DOCUMENT_LABELS[kind]}</strong>
        {!current && request && !request.fulfilledAt && (
          <span className="muted" style={{ fontSize: 12 }}>
            Requested {new Date(request.requestedAt).toLocaleDateString()}
          </span>
        )}
      </div>
      {current ? (
        <div className="row">
          <a href={documentUrl(current.id, true)} target="_blank" rel="noreferrer">
            {current.fileName}
          </a>
          <a className="muted" href={documentUrl(current.id)} style={{ fontSize: 12 }}>
            Download
          </a>
          <span className="muted" style={{ fontSize: 12 }}>
            {new Date(current.uploadedAt).toLocaleDateString()}
            {versions.length > 1 ? ` · ${versions.length} versions` : ''}
            {candidateEmail && current.uploadedBy === candidateEmail ? ' · uploaded by the candidate' : ''}
          </span>
        </div>
      ) : (
        <span className="muted">Not uploaded yet</span>
      )}
      {canEdit && (
        <label htmlFor={inputId} className="btn btn-secondary btn-sm" style={{ alignSelf: 'flex-start' }}>
          {busy ? 'Uploading…' : current ? 'Upload new version' : 'Upload'}
          <input
            id={inputId}
            type="file"
            accept={images ? '.pdf,.doc,.docx,.jpg,.jpeg,.png' : '.pdf,.doc,.docx'}
            className="visually-hidden"
            disabled={busy}
            onChange={async (e) => {
              const file = e.target.files?.[0]
              e.target.value = ''
              if (!file) return
              setBusy(true)
              try {
                await onUpload(kind, file)
              } finally {
                setBusy(false)
              }
            }}
          />
        </label>
      )}
    </div>
  )
}

/**
 * Résumés and background-verification documents. Aadhaar and PAN only for people allowed to
 * see government IDs (the API enforces it; this just hides empty slots).
 */
export function DocumentsSection({
  candidateId,
  candidateEmail,
  canEdit,
  canSeeIds,
  onChanged,
  onRequested,
}: {
  candidateId: string
  candidateEmail: string | null
  canEdit: boolean
  canSeeIds: boolean
  onChanged: () => void
  onRequested: (kinds: DocumentKind[]) => void
}) {
  const [documents, setDocuments] = useState<CandidateDocument[]>([])
  const [requests, setRequests] = useState<DocumentRequest[]>([])
  const [asking, setAsking] = useState(false)
  const [picked, setPicked] = useState<DocumentKind[]>(['AADHAAR', 'PAN', 'DEGREE_CERTIFICATE', 'PHOTO'])
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    listDocuments(candidateId).then(setDocuments).catch(() => undefined)
    listDocumentRequests(candidateId).then(setRequests).catch(() => undefined)
  }, [candidateId])
  useEffect(load, [load])

  async function onUpload(kind: DocumentKind, file: File) {
    setError(null)
    try {
      await uploadDocument(candidateId, kind, file)
      load()
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed')
    }
  }

  async function onAsk() {
    setError(null)
    try {
      await requestDocuments(candidateId, picked)
      setAsking(false)
      load()
      onChanged()
      onRequested(picked)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not request documents')
    }
  }

  const bgv = BGV_KINDS.filter((k) => canSeeIds || !ID_KINDS.includes(k))
  const latestRequest = (kind: DocumentKind) => requests.find((r) => r.kind === kind)
  const slot = (kind: DocumentKind) => (
    <DocumentSlot key={kind} kind={kind} documents={documents} request={latestRequest(kind)}
      canEdit={canEdit}
      candidateEmail={candidateEmail}
      onUpload={onUpload}
    />
  )

  return (
    <div className="stack" style={{ gap: 8 }}>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <strong>Résumés</strong>
      {slot('ORIGINAL_RESUME')}
      {slot('CODEWALNUT_RESUME')}
      <div className="row" style={{ justifyContent: 'space-between', marginTop: 8 }}>
        <strong>Background verification</strong>
        {canEdit && !asking && (
          <Button size="sm" variant="secondary" onClick={() => setAsking(true)}>
            Request from candidate
          </Button>
        )}
      </div>
      {asking && (
        <div className="card stack" style={{ padding: 12, gap: 6 }} role="group" aria-label="Request documents">
          <span className="muted" style={{ fontSize: 13 }}>
            The candidate uploads these from their CodeWalnut candidate page (Google sign-in). Next, you can email them.
          </span>
          {(['ORIGINAL_RESUME', ...BGV_KINDS] as DocumentKind[]).map((k) => (
            <label key={k} className="row" style={{ gap: 8, fontSize: 14, flexWrap: 'nowrap', alignItems: 'flex-start' }}>
              <input
                type="checkbox"
                checked={picked.includes(k)}
                onChange={(e) => setPicked(e.target.checked ? [...picked, k] : picked.filter((x) => x !== k))}
              />
              {DOCUMENT_LABELS[k]}
            </label>
          ))}
          <div className="row">
            <Button size="sm" disabled={picked.length === 0} onClick={() => void onAsk()}>
              Request {picked.length} document{picked.length === 1 ? '' : 's'}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setAsking(false)}>
              Cancel
            </Button>
          </div>
        </div>
      )}
      {bgv.map(slot)}
    </div>
  )
}
