import { useEffect, useRef, useState } from 'react'
import { getIntake, uploadResumes, type IntakeProgress } from '../api/insights'
import { Button, Card } from './ui'

const BATCH = 5
const MAX_BYTES = 10 * 1024 * 1024

const OUTCOME: Record<string, string> = {
  NEW_CANDIDATE: 'added',
  EXISTING_CANDIDATE: 'already a candidate — added to this opening',
  ALREADY_IN_OPENING: 'already in this opening — résumé read again',
}

/**
 * Bulk résumé upload: pick or drop many PDFs / Word files; each is read by the AI, the person is
 * found or added at Applied / Sourced with the résumé attached, and matched against the opening.
 */
export function ResumeUpload({ jobId, onProgress, onClose }: { jobId: string; onProgress: () => void; onClose: () => void }) {
  const [progress, setProgress] = useState<IntakeProgress | null>(null)
  const [sending, setSending] = useState<{ sent: number; total: number } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const onProgressRef = useRef(onProgress)
  onProgressRef.current = onProgress

  useEffect(() => {
    getIntake(jobId).then(setProgress).catch(() => undefined)
  }, [jobId])

  // While résumés are being read, check every few seconds.
  const pending = progress?.pending ?? 0
  useEffect(() => {
    if (!pending) return
    const timer = window.setInterval(() => {
      getIntake(jobId)
        .then((p) => {
          setProgress(p)
          onProgressRef.current()
        })
        .catch(() => undefined)
    }, 3000)
    return () => window.clearInterval(timer)
  }, [jobId, pending])

  async function send(list: File[]) {
    setError(null)
    const tooBig = list.filter((f) => f.size > MAX_BYTES)
    const files = list.filter((f) => f.size <= MAX_BYTES)
    if (tooBig.length) setError(`Skipped (over 10 MB): ${tooBig.map((f) => f.name).join(', ')}`)
    if (!files.length) return
    setSending({ sent: 0, total: files.length })
    try {
      for (let i = 0; i < files.length; i += BATCH) {
        await uploadResumes(jobId, files.slice(i, i + BATCH))
        setSending({ sent: Math.min(i + BATCH, files.length), total: files.length })
      }
      setProgress(await getIntake(jobId))
      onProgressRef.current()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed')
    } finally {
      setSending(null)
      if (input.current) input.current.value = ''
    }
  }

  const items = progress?.items ?? []
  return (
    <Card>
      <div className="stack" aria-label="Upload résumés" role="region">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h2 style={{ margin: 0 }}>Upload résumés</h2>
          <Button size="sm" variant="ghost" onClick={onClose}>
            Close
          </Button>
        </div>
        <p className="muted" style={{ margin: 0, fontSize: 14 }}>
          Select all the résumés you received (PDF or Word, up to 10 MB each). The AI reads each one, adds the person at{' '}
          <strong>Applied / Sourced</strong> with their résumé (or finds them if they’re already here), and matches them
          against this opening’s description. Tip: in Gmail, open the email and use “Download all” to save attachments.
        </p>
        <label
          className={`dropzone${dragging ? ' dragging' : ''}`}
          onDragOver={(e) => {
            e.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault()
            setDragging(false)
            void send(Array.from(e.dataTransfer.files))
          }}
        >
          <input
            ref={input}
            type="file"
            multiple
            accept=".pdf,.doc,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            aria-label="Résumé files"
            disabled={!!sending}
            onChange={(e) => void send(Array.from(e.target.files ?? []))}
          />
          <span>{sending ? `Uploading ${sending.sent} of ${sending.total}…` : 'Drop résumés here, or click to choose files'}</span>
        </label>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        {progress && progress.total > 0 && (
          <div className="stack" style={{ gap: 6 }}>
            <div role="status" style={{ fontSize: 14 }}>
              <strong>{progress.done}</strong> of {progress.total} read today
              {progress.pending > 0 && <> · {progress.pending} reading now…</>}
              {progress.newCandidates > 0 && <> · {progress.newCandidates} new</>}
              {progress.existing > 0 && <> · {progress.existing} already known</>}
              {progress.failed > 0 && <> · {progress.failed} couldn’t be read</>}
            </div>
            <ul className="intake-list">
              {items
                .slice()
                .reverse()
                .map((i) => (
                  <li key={i.id} className={`intake-${i.status.toLowerCase()}`}>
                    <span className="intake-file">{i.fileName}</span>{' '}
                    {i.status === 'PENDING' && <span className="muted">reading…</span>}
                    {i.status === 'DONE' && (
                      <span className="muted">
                        → <strong>{i.candidateName}</strong> {i.outcome ? OUTCOME[i.outcome] : ''}
                      </span>
                    )}
                    {i.status === 'FAILED' && <span className="intake-error">{i.error}</span>}
                  </li>
                ))}
            </ul>
          </div>
        )}
      </div>
    </Card>
  )
}
