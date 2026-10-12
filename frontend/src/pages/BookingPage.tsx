import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { bookSlot, getBookingPage, type BookingPageData, type PublicSlot } from '../api/booking'
import '../components/tracker.css'
import './BookingPage.css'

/**
 * A candidate's "pick a time" page (PIPE-24), opened from the link in our email; no sign-in.
 * Times show in the candidate's own time zone.
 */
export function BookingPage() {
  const { token = '' } = useParams()
  const [data, setData] = useState<BookingPageData | null>(null)
  const [choice, setChoice] = useState<PublicSlot | null>(null)
  const [changing, setChanging] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone

  useEffect(() => {
    getBookingPage(token)
      .then(setData)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'This link could not be opened'))
  }, [token])

  async function book() {
    if (!choice) return
    setBusy(true)
    setError(null)
    try {
      setData(await bookSlot(token, choice.id))
      setChoice(null)
      setChanging(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not book that time')
      getBookingPage(token).then(setData).catch(() => undefined)
    } finally {
      setBusy(false)
    }
  }

  const day = (iso: string) => new Date(iso).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
  const time = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
  const when = (s: PublicSlot) => `${day(s.startAt)}, ${time(s.startAt)}`

  const byDay = new Map<string, PublicSlot[]>()
  for (const s of data?.slots ?? []) {
    const d = day(s.startAt)
    byDay.set(d, [...(byDay.get(d) ?? []), s])
  }

  return (
    <div className="booking-page">
      <div className="booking-card stack">
        <div className="brand-line">CodeWalnut</div>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        {!data && !error && <p className="muted">Loading…</p>}
        {data && (
          <>
            <h1 style={{ margin: 0 }}>Hi {data.firstName}, pick a time</h1>
            <p className="muted" style={{ margin: 0 }}>
              A {data.durationMinutes}-minute conversation on Google Meet about the <strong>{data.jobTitle}</strong> role. Times are shown
              in your time zone ({zone}).
            </p>
            {data.booked && !changing ? (
              <div className="alert alert-info stack" style={{ gap: 6 }} role="status">
                <strong>Booked: {when(data.booked)}</strong>
                {data.scheduled ? (
                  <span>The calendar invitation with the Google Meet link has been sent to your email. See you then!</span>
                ) : (
                  <span>You’ll receive a calendar invitation with the Google Meet link by email shortly.</span>
                )}
                {!data.scheduled && (
                  <div>
                    <button type="button" className="btn btn-secondary btn-sm" onClick={() => setChanging(true)}>
                      Change time
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <>
                {byDay.size === 0 && (
                  <p className="muted">No times are open right now. Please reply to our email and we’ll find one with you.</p>
                )}
                {[...byDay.entries()].map(([d, slots]) => (
                  <div key={d} className="stack" style={{ gap: 6 }}>
                    <strong>{d}</strong>
                    <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
                      {slots.map((s) => (
                        <button
                          key={s.id}
                          type="button"
                          className={`chip${choice?.id === s.id ? ' chip-on' : ''}`}
                          aria-pressed={choice?.id === s.id}
                          onClick={() => setChoice(s)}
                        >
                          {time(s.startAt)}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
                {choice && (
                  <div className="row" style={{ gap: 8, alignItems: 'center' }}>
                    <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void book()}>
                      {busy ? 'Booking…' : `Book ${when(choice)}`}
                    </button>
                    {changing && (
                      <button type="button" className="btn btn-ghost" onClick={() => setChanging(false)}>
                        Keep my current time
                      </button>
                    )}
                  </div>
                )}
              </>
            )}
          </>
        )}
      </div>
    </div>
  )
}
