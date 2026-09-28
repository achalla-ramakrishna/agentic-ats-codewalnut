import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  addNote,
  DOCUMENT_LABELS,
  documentUrl,
  getHistory,
  listDocuments,
  uploadDocument,
  type ApplicationRow,
  type CandidateDocument,
  type DocumentKind,
  type HistoryEvent,
} from '../api/tracker'
import { Button } from './ui'
import { useStages } from './useStages'

function describe(event: HistoryEvent, label: (s: string | null) => string) {
  switch (event.type) {
    case 'CREATED':
      return `Added at ${label(event.toStage)}`
    case 'STAGE_CHANGED':
      return `${label(event.fromStage)} → ${label(event.toStage)}`
    default:
      return 'Note'
  }
}

function ResumeSlot({
  kind,
  documents,
  canEdit,
  onUpload,
}: {
  kind: DocumentKind
  documents: CandidateDocument[]
  canEdit: boolean
  onUpload: (kind: DocumentKind, file: File) => Promise<void>
}) {
  const versions = documents.filter((d) => d.kind === kind)
  const current = versions[0]
  const [busy, setBusy] = useState(false)
  const inputId = `upload-${kind}`

  return (
    <div className="card stack" style={{ padding: 16, gap: 8 }}>
      <strong>{DOCUMENT_LABELS[kind]}</strong>
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
            accept=".pdf,.doc,.docx"
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

export function CandidateDrawer({
  row,
  canEdit,
  onClose,
  onChanged,
}: {
  row: ApplicationRow
  canEdit: boolean
  onClose: () => void
  onChanged: () => void
}) {
  const stages = useStages()
  const label = (s: string | null) => stages.find((x) => x.key === s)?.label ?? s ?? ''
  const [history, setHistory] = useState<HistoryEvent[]>([])
  const [documents, setDocuments] = useState<CandidateDocument[]>([])
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    getHistory(row.id).then(setHistory).catch(() => undefined)
    listDocuments(row.candidateId).then(setDocuments).catch(() => undefined)
  }, [row.id, row.candidateId])

  useEffect(load, [load])

  async function onNote(event: FormEvent) {
    event.preventDefault()
    if (!note.trim()) return
    setError(null)
    try {
      await addNote(row.id, note.trim())
      setNote('')
      load()
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save the note')
    }
  }

  async function onUpload(kind: DocumentKind, file: File) {
    setError(null)
    try {
      await uploadDocument(row.candidateId, kind, file)
      load()
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed')
    }
  }

  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} />
      <aside className="drawer" aria-label={`Candidate ${row.name}`}>
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <div>
            <h2>{row.name}</h2>
            <div className="muted">{row.jobTitle}</div>
          </div>
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
        </div>
        <div className="stack" style={{ gap: 4 }}>
          <div>
            <span className="muted">Email:</span> {row.email ?? '—'}
          </div>
          <div>
            <span className="muted">Phone:</span> {row.phone ?? '—'}
          </div>
          <div>
            <span className="muted">Stage:</span> <strong>{row.stageLabel}</strong>
          </div>
        </div>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <div className="stack">
          <ResumeSlot kind="ORIGINAL_RESUME" documents={documents} canEdit={canEdit} onUpload={onUpload} />
          <ResumeSlot kind="CODEWALNUT_RESUME" documents={documents} canEdit={canEdit} onUpload={onUpload} />
        </div>
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
      </aside>
    </>
  )
}
