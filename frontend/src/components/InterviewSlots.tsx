import { useCallback, useEffect, useState } from 'react'
import { createSlots, deleteSlot, getSlots, sendSlotInvites, type SlotView, type SlotsResponse } from '../api/booking'
import { ApiError } from '../api/client'
import { getGoogleStatus, type GoogleStatus } from '../api/interviews'
import { ConnectGoogle } from './ConnectGoogle'
import { Button, Card } from './ui'

function nextWeekday(from: Date) {
  const d = new Date(from)
  do d.setDate(d.getDate() + 1)
  while (d.getDay() === 0 || d.getDay() === 6)
  return d
}
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/**
 * Interview slots for an opening (PIPE-24): offer times, see who booked which, and send all the
 * calendar invites (Google Meet) in one click. Candidates book through the link in the
 * "Book a 30-minute call" email.
 */
export function InterviewSlots({ jobId, me, onChanged, onClose }: { jobId: string; me: string; onChanged: () => void; onClose: () => void }) {
  const start = nextWeekday(new Date())
  const [data, setData] = useState<SlotsResponse | null>(null)
  const [from, setFrom] = useState(iso(start))
  const [to, setTo] = useState(iso(new Date(start.getTime() + 4 * 86400000)))
  const [weekdaysOnly, setWeekdaysOnly] = useState(true)
  const [windows, setWindows] = useState([
    { start: '10:00', end: '13:00' },
    { start: '15:00', end: '18:00' },
  ])
  const [minutes, setMinutes] = useState(30)
  const [interviewers, setInterviewers] = useState(me)
  const [busy, setBusy] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [google, setGoogle] = useState<GoogleStatus | null>(null)
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata'

  const load = useCallback(() => {
    getSlots(jobId)
      .then(setData)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the slots'))
  }, [jobId])
  useEffect(load, [load])

  async function add() {
    setBusy('Adding slots…')
    setError(null)
    setNotice(null)
    try {
      const r = await createSlots(jobId, {
        from,
        to,
        weekdaysOnly,
        windows,
        durationMinutes: minutes,
        timeZone: zone,
        interviewerEmails: interviewers.split(/[\s,;]+/).filter(Boolean),
      })
      setData(r.slots)
      setNotice(`Added ${r.created} slot${r.created === 1 ? '' : 's'}${r.skipped ? ` (${r.skipped} already there or in the past)` : ''}.`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add slots')
    } finally {
      setBusy(null)
    }
  }

  async function sendInvites() {
    setBusy('Sending calendar invites…')
    setError(null)
    setNotice(null)
    try {
      const r = await sendSlotInvites(jobId)
      setNotice(
        `Sent ${r.scheduled} calendar invite${r.scheduled === 1 ? '' : 's'} with Google Meet links.` +
          (r.failed.length ? ` Not sent: ${r.failed.join('; ')}.` : ''),
      )
      load()
      onChanged()
    } catch (e) {
      if (e instanceof ApiError && e.status === 428) {
        setGoogle(await getGoogleStatus().catch(() => null))
        setError('Connect Google first, then click Send invites again.')
      } else setError(e instanceof Error ? e.message : 'Could not send the invites')
    } finally {
      setBusy(null)
    }
  }

  async function remove(slot: SlotView) {
    try {
      setData(await deleteSlot(slot.id))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not remove the slot')
    }
  }

  const time = (s: string) => new Date(s).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
  const day = (s: string) => new Date(s).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })
  const byDay = new Map<string, SlotView[]>()
  for (const s of data?.slots ?? []) byDay.set(day(s.startAt), [...(byDay.get(day(s.startAt)) ?? []), s])

  return (
    <Card className="stack">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0 }}>Interview slots</h2>
        <Button variant="ghost" size="sm" onClick={onClose}>
          Close
        </Button>
      </div>
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        Offer times here, then email candidates the <strong>Book a 30-minute call</strong> template: each gets their own link to pick a
        free time (no sign-in). When people have booked, click <strong>Send invites</strong> to send all the Google Calendar invites
        with Meet links at once.
      </p>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {google && (!google.available || !google.calendarConnected) && (
        <ConnectGoogle status={google} purpose="Invites go out from your own Google Calendar with a Meet link." returnTo={window.location.pathname} />
      )}
      {notice && (
        <div className="alert alert-info" role="status">
          {notice}
        </div>
      )}
      {data && (
        <div className="row" style={{ gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          <span>
            <strong>{data.free}</strong> free · <strong>{data.booked}</strong> booked, invite to send · <strong>{data.scheduled}</strong>{' '}
            invites sent
          </span>
          <Button size="sm" disabled={!!busy || data.booked === 0} onClick={() => void sendInvites()}>
            Send invites for {data.booked} booked
          </Button>
        </div>
      )}

      <details>
        <summary style={{ cursor: 'pointer' }}>Add slots</summary>
        <div className="stack" style={{ gap: 10, marginTop: 10 }}>
          <div className="row" style={{ gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
            <label className="field">
              From
              <input className="input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </label>
            <label className="field">
              To
              <input className="input" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </label>
            <label className="field" style={{ maxWidth: 110 }}>
              Length (min)
              <input className="input" type="number" min={15} max={120} step={15} value={minutes} onChange={(e) => setMinutes(Number(e.target.value) || 30)} />
            </label>
            <label className="row" style={{ gap: 6 }}>
              <input type="checkbox" checked={weekdaysOnly} onChange={(e) => setWeekdaysOnly(e.target.checked)} /> Weekdays only
            </label>
          </div>
          <div className="row" style={{ gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <span style={{ fontSize: 13 }}>Each day:</span>
            {windows.map((w, i) => (
              <span key={i} className="row" style={{ gap: 4, alignItems: 'center' }}>
                <input
                  className="input"
                  type="time"
                  aria-label={`Window ${i + 1} from`}
                  value={w.start}
                  onChange={(e) => setWindows(windows.map((x, j) => (j === i ? { ...x, start: e.target.value } : x)))}
                />
                –
                <input
                  className="input"
                  type="time"
                  aria-label={`Window ${i + 1} to`}
                  value={w.end}
                  onChange={(e) => setWindows(windows.map((x, j) => (j === i ? { ...x, end: e.target.value } : x)))}
                />
                {windows.length > 1 && (
                  <Button size="sm" variant="ghost" onClick={() => setWindows(windows.filter((_, j) => j !== i))} aria-label={`Remove window ${i + 1}`}>
                    ×
                  </Button>
                )}
              </span>
            ))}
            {windows.length < 6 && (
              <Button size="sm" variant="ghost" onClick={() => setWindows([...windows, { start: '16:00', end: '18:00' }])}>
                + Add time window
              </Button>
            )}
          </div>
          <label className="field">
            Interviewers (they get the invite too)
            <input className="input" value={interviewers} onChange={(e) => setInterviewers(e.target.value)} />
          </label>
          <p className="muted" style={{ margin: 0, fontSize: 12 }}>Times are in your time zone ({zone}); candidates see them in theirs.</p>
          <div>
            <Button size="sm" variant="secondary" disabled={!!busy} onClick={() => void add()}>
              {busy === 'Adding slots…' ? busy : 'Add slots'}
            </Button>
          </div>
        </div>
      </details>

      {busy && busy !== 'Adding slots…' && <p className="muted" role="status">{busy}</p>}
      {data && data.slots.length === 0 && <p className="muted">No slots yet. Use Add slots.</p>}
      {[...byDay.entries()].map(([d, slots]) => (
        <div key={d} className="stack" style={{ gap: 6 }}>
          <strong style={{ fontSize: 14 }}>{d}</strong>
          <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
            {slots.map((s) => (
              <span key={s.id} className={`slot-chip${s.interviewId ? ' slot-sent' : s.applicationId ? ' slot-booked' : ''}`}>
                {s.interviewId ? '✓ ' : ''}
                {time(s.startAt)}
                {s.candidateName ? ` · ${s.candidateName}` : ''}
                {!s.applicationId && (
                  <button type="button" className="slot-x" aria-label={`Remove ${d} ${time(s.startAt)}`} onClick={() => void remove(s)}>
                    ×
                  </button>
                )}
              </span>
            ))}
          </div>
        </div>
      ))}
    </Card>
  )
}
