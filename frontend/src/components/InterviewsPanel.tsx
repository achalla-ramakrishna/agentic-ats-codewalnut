import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import {
  browserTimeZone,
  cancelInterview,
  formatWhen,
  getGoogleStatus,
  listApplicationInterviews,
  scheduleInterview,
  type GoogleStatus,
  type Interview,
} from '../api/interviews'
import { ConnectGoogle } from './ConnectGoogle'
import { Badge, Button } from './ui'

const DURATIONS = [30, 45, 60, 90]

function tomorrow() {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function defaultMessage(candidateName: string, jobTitle: string) {
  const first = candidateName.trim().split(/\s+/)[0] ?? ''
  return (
    `Hi ${first},\n\n` +
    `Thank you for your interest in CodeWalnut. We'd like to invite you to an interview for the ${jobTitle} role.\n\n` +
    'Please join a few minutes early from a quiet place with a stable internet connection, and keep your camera on.'
  )
}

function ScheduleForm({
  applicationId,
  candidateName,
  candidateEmail,
  jobTitle,
  onScheduled,
  onCancel,
}: {
  applicationId: string
  candidateName: string
  candidateEmail: string | null
  jobTitle: string
  onScheduled: (i: Interview) => void
  onCancel: () => void
}) {
  const [status, setStatus] = useState<GoogleStatus | null>(null)
  const [title, setTitle] = useState(`CodeWalnut interview – ${jobTitle}`)
  const [date, setDate] = useState(tomorrow())
  const [time, setTime] = useState('11:00')
  const [duration, setDuration] = useState(45)
  const [interviewers, setInterviewers] = useState('')
  const [message, setMessage] = useState(defaultMessage(candidateName, jobTitle))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const timeZone = browserTimeZone()

  useEffect(() => {
    getGoogleStatus()
      .then(setStatus)
      .catch(() => setStatus(null))
  }, [])

  if (!candidateEmail) {
    return <div className="alert alert-info">Add the candidate's email address first. The invitation is sent there.</div>
  }
  if (status && (!status.available || !status.calendarConnected)) {
    return (
      <ConnectGoogle
        status={status}
        purpose="Interviews are created on your Google Calendar with a Google Meet link, and Google emails the invitation to the candidate and interviewers."
        returnTo={`${window.location.pathname}?candidate=${applicationId}`}
      />
    )
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const start = new Date(`${date}T${time}`)
    if (Number.isNaN(start.getTime())) {
      setError('Pick a date and time')
      return
    }
    setBusy(true)
    try {
      const created = await scheduleInterview(applicationId, {
        title,
        startAt: start.toISOString(),
        durationMinutes: duration,
        timeZone,
        interviewerEmails: interviewers
          .split(/[,;\s]+/)
          .map((s) => s.trim())
          .filter(Boolean),
        message,
      })
      onScheduled(created)
    } catch (e) {
      if (e instanceof ApiError && e.status === 428) {
        setStatus((s) => ({ available: true, redirectUri: s?.redirectUri ?? '', calendarConnected: false, mailConnected: s?.mailConnected ?? false }))
      } else {
        setError(e instanceof Error ? e.message : 'Could not schedule the interview')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={onSubmit} aria-label="Schedule interview" style={{ gap: 12 }}>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <label className="field">
        Title (the candidate sees this)
        <input className="input" required value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
      </label>
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field">
          Date
          <input className="input" type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label className="field">
          Time
          <input className="input" type="time" required value={time} onChange={(e) => setTime(e.target.value)} />
        </label>
        <label className="field">
          Duration
          <select className="select" value={duration} onChange={(e) => setDuration(Number(e.target.value))}>
            {DURATIONS.map((d) => (
              <option key={d} value={d}>
                {d} min
              </option>
            ))}
          </select>
        </label>
      </div>
      <span className="muted" style={{ fontSize: 12 }}>
        Time zone: {timeZone}
      </span>
      <label className="field">
        Interviewers (emails, comma-separated; optional)
        <input
          className="input"
          value={interviewers}
          onChange={(e) => setInterviewers(e.target.value)}
          placeholder="e.g. priya@codewalnut.com, arjun@codewalnut.com"
        />
      </label>
      <label className="field">
        Message to the candidate
        <textarea
          className="textarea"
          style={{ minHeight: 120, fontFamily: 'inherit', fontSize: 14 }}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          maxLength={5000}
        />
      </label>
      <span className="muted" style={{ fontSize: 12 }}>
        Google emails the invitation, with the Meet link, to {candidateEmail}
        {interviewers.trim() ? ' and the interviewers' : ''}.
      </span>
      <div className="row">
        <Button type="submit" disabled={busy || !title.trim()}>
          {busy ? 'Scheduling…' : 'Schedule & send invite'}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

function InterviewItem({ interview, canEdit, onCancelled }: { interview: Interview; canEdit: boolean; onCancelled: () => void }) {
  const [cancelling, setCancelling] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const upcoming = interview.status === 'SCHEDULED' && new Date(interview.endAt) > new Date()

  async function onConfirm() {
    setError(null)
    try {
      await cancelInterview(interview.id, reason.trim())
      setCancelling(false)
      onCancelled()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not cancel')
    }
  }

  return (
    <li>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>{interview.title}</strong>
        {interview.status === 'CANCELLED' ? <Badge>Cancelled</Badge> : upcoming ? <Badge tone="primary">Scheduled</Badge> : <Badge>Done</Badge>}
      </div>
      <div>{formatWhen(interview)}</div>
      {interview.status === 'SCHEDULED' && (
        <div className="row" style={{ gap: 12 }}>
          {interview.meetLink && (
            <a href={interview.meetLink} target="_blank" rel="noreferrer">
              Join Google Meet
            </a>
          )}
          {interview.calendarLink && (
            <a href={interview.calendarLink} target="_blank" rel="noreferrer">
              Open in Google Calendar
            </a>
          )}
        </div>
      )}
      {interview.interviewers.length > 0 && <div className="meta">Interviewers: {interview.interviewers.join(', ')}</div>}
      <div className="meta">
        Organiser: {interview.organizerEmail}
        {interview.cancelReason ? ` · Cancelled: ${interview.cancelReason}` : ''}
      </div>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {canEdit && upcoming && !cancelling && (
        <Button size="sm" variant="ghost" onClick={() => setCancelling(true)}>
          Cancel interview
        </Button>
      )}
      {cancelling && (
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field">
            Reason (optional, internal)
            <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
          <Button size="sm" variant="secondary" onClick={() => void onConfirm()}>
            Cancel & notify everyone
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setCancelling(false)}>
            Keep
          </Button>
        </div>
      )}
    </li>
  )
}

/** Interviews for one application, inside the candidate drawer (docs/features/interviews-and-scorecards.md). */
export function InterviewsPanel({
  applicationId,
  candidateName,
  candidateEmail,
  jobTitle,
  canEdit,
  onChanged,
}: {
  applicationId: string
  candidateName: string
  candidateEmail: string | null
  jobTitle: string
  canEdit: boolean
  onChanged: () => void
}) {
  const [interviews, setInterviews] = useState<Interview[]>([])
  const [scheduling, setScheduling] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(() => {
    listApplicationInterviews(applicationId).then(setInterviews).catch(() => undefined)
  }, [applicationId])
  useEffect(load, [load])

  return (
    <div className="stack" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>Interviews</strong>
        {canEdit && !scheduling && (
          <Button size="sm" onClick={() => setScheduling(true)}>
            Schedule interview
          </Button>
        )}
      </div>
      {notice && <div className="alert alert-info">{notice}</div>}
      {scheduling && (
        <ScheduleForm
          applicationId={applicationId}
          candidateName={candidateName}
          candidateEmail={candidateEmail}
          jobTitle={jobTitle}
          onCancel={() => setScheduling(false)}
          onScheduled={(i) => {
            setScheduling(false)
            setNotice(`Scheduled for ${formatWhen(i)}. Google has emailed the invitation.`)
            load()
            onChanged()
          }}
        />
      )}
      {interviews.length === 0 && !scheduling && <span className="muted">No interviews yet.</span>}
      {interviews.length > 0 && (
        <ul className="timeline">
          {interviews.map((i) => (
            <InterviewItem
              key={i.id}
              interview={i}
              canEdit={canEdit}
              onCancelled={() => {
                setNotice('Interview cancelled. Google has emailed everyone.')
                load()
                onChanged()
              }}
            />
          ))}
        </ul>
      )}
    </div>
  )
}
