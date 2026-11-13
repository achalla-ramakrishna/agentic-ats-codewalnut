import { useCallback, useEffect, useState } from 'react'
import { bytes, closedVideos, deleteClosedVideos, videoStorage, type VideoStorage } from '../api/aiInterviews'
import { Button } from './ui'

/**
 * Room for interview videos (AINT-19), with a warning before candidates hit upload errors, and (for
 * people who manage the opening) deleting the videos of rejected or withdrawn candidates (AINT-20).
 */
export function VideoSpace({ jobId, canEdit }: { jobId: string; canEdit: boolean }) {
  const [space, setSpace] = useState<VideoStorage | null>(null)
  const [closed, setClosed] = useState<{ candidates: number; videos: number; bytes: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback(() => {
    videoStorage().then(setSpace).catch(() => setSpace(null))
    if (canEdit) closedVideos(jobId).then(setClosed).catch(() => setClosed(null))
  }, [jobId, canEdit])
  useEffect(load, [load])

  async function free() {
    if (!closed || !window.confirm(`Delete ${closed.videos} videos of ${closed.candidates} rejected or withdrawn candidate(s), freeing ${bytes(closed.bytes)}? Their transcripts, written answers and reviews stay.`)) return
    setBusy(true)
    try {
      const r = await deleteClosedVideos(jobId)
      setMessage(`Deleted ${r.videos} videos and freed ${bytes(r.bytes)}.`)
      load()
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Could not delete the videos')
    } finally {
      setBusy(false)
    }
  }

  if (!space) return null
  const known = space.totalBytes > 0
  const usedShare = known ? Math.min(1, (space.totalBytes - space.freeBytes) / space.totalBytes) : 0
  const low = !!space.warning
  return (
    <div className="stack" style={{ gap: 6 }} role="region" aria-label="Space for videos">
      <div className="row" style={{ gap: 12, justifyContent: 'space-between', fontSize: 14 }}>
        <span>
          <strong>Space for videos:</strong> {known ? `${bytes(space.freeBytes)} free of ${bytes(space.totalBytes)}` : 'unknown'}
          {space.roomFor >= 0 && ` · room for about ${space.roomFor} more interview${space.roomFor === 1 ? '' : 's'}`}
          <span className="muted">
            {' '}
            ({space.interviews} recorded, {bytes(space.usedBytes)}; about {bytes(space.perInterviewBytes)} each)
          </span>
        </span>
        {canEdit && closed && closed.videos > 0 && (
          <Button size="sm" variant="secondary" disabled={busy} onClick={() => void free()}>
            {busy ? 'Deleting…' : `Free ${bytes(closed.bytes)}: delete rejected candidates’ videos (${closed.candidates})`}
          </Button>
        )}
      </div>
      {known && (
        <div className={`space-bar${low ? ' low' : ''}`} aria-hidden="true">
          <span style={{ width: `${Math.round(usedShare * 100)}%` }} />
        </div>
      )}
      {space.warning && (
        <div role="alert" className="alert alert-error">
          {space.warning}
        </div>
      )}
      {message && (
        <div role="status" className="alert alert-info">
          {message}
        </div>
      )}
    </div>
  )
}
