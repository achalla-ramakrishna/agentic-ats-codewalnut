import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import {
  browserTimeZone,
  RECOMMENDATION_LABEL,
  cancelInterview,
  connectGoogleUrl,
  feedbackOpen,
  formatWhen,
  getGoogleStatus,
  listApplicationInterviews,
  logInterview,
  rescheduleInterview,
  listFeedbackSummaries,
  scheduleInterview,
  type FeedbackSummary,
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

function FeedbackLine({ interview, summary }: { interview: Interview; summary: FeedbackSummary | undefined }) {
  if (interview.status !== 'SCHEDULED') return null
  if (!feedbackOpen(interview)) {
    return <span className="meta">Feedback form opens when the interview starts.</span>
  }
  const submitted = summary?.submitted ?? 0
  const recommendations = summary?.visible ? summary.recommendations : []
  return (
    <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
      <span className="meta">
        Feedback: {submitted} of {summary?.panelSize ?? interview.interviewers.length + 1}
        {recommendations.length > 0 && ` · ${recommendations.map((r) => RECOMMENDATION_LABEL[r]).join(', ')}`}
        {summary && !summary.visible && submitted > 0 && ' · give yours to see theirs'}
      </span>
      <Link to={`/interviews/${interview.id}/feedback`}>{summary?.mineSubmitted ? 'View feedback' : 'Feedback form'}</Link>
    </div>
  )
}

/** An interview that happened outside the app (e.g. a Meet set up by hand), so the panel can give feedback (INT-33). */
function LogInterviewForm({
  applicationId,
  jobTitle,
  onLogged,
  onCancel,
}: {
  applicationId: string
  jobTitle: string
  onLogged: (i: Interview) => void
  onCancel: () => void
}) {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const [date, setDate] = useState(`${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`)
  const [time, setTime] = useState(`${pad(now.getHours())}:00`)
  const [duration, setDuration] = useState(45)
  const [interviewers, setInterviewers] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

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
      onLogged(
        await logInterview(applicationId, {
          title: `CodeWalnut interview – ${jobTitle}`,
          startAt: start.toISOString(),
          durationMinutes: duration,
          timeZone: browserTimeZone(),
          interviewerEmails: interviewers
            .split(/[,;\s]+/)
            .map((s) => s.trim())
            .filter(Boolean),
        }),
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not log the interview')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={onSubmit} aria-label="Log an interview held elsewhere" style={{ gap: 12 }}>
      <span className="muted" style={{ fontSize: 13 }}>
        For an interview that already happened (or is happening now) on a Meet or call set up outside the app. Nothing is sent to
        anyone; it just adds the interview so the panel can fill in the feedback form.
      </span>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field">
          Date
          <input className="input" type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label className="field">
          Started at
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
      <label className="field">
        Interviewers (emails, comma-separated; you are added as organiser)
        <input className="input" value={interviewers} onChange={(e) => setInterviewers(e.target.value)} placeholder="e.g. priya@codewalnut.com" />
      </label>
      <div className="row">
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving…' : 'Log interview'}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

/** Move an interview to a new time, e.g. after a missed slot (INT-20). */
function RescheduleForm({ interview, onDone, onCancel }: { interview: Interview; onDone: (i: Interview) => void; onCancel: () => void }) {
  const pad = (n: number) => String(n).padStart(2, '0')
  const [date, setDate] = useState(tomorrow())
  const [time, setTime] = useState(() => {
    const d = new Date(interview.startAt)
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  })
  const minutes = Math.round((new Date(interview.endAt).getTime() - new Date(interview.startAt).getTime()) / 60000)
  const [duration, setDuration] = useState(DURATIONS.includes(minutes) ? minutes : 45)
  const [interviewers, setInterviewers] = useState(interview.interviewers.join(', '))
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [needsGoogle, setNeedsGoogle] = useState(false)

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
      onDone(
        await rescheduleInterview(interview.id, {
          startAt: start.toISOString(),
          durationMinutes: duration,
          timeZone: browserTimeZone(),
          interviewerEmails: interviewers
            .split(/[,;\s]+/)
            .map((s) => s.trim())
            .filter(Boolean),
          reason: reason.trim(),
        }),
      )
    } catch (e) {
      if (e instanceof ApiError && e.status === 428) setNeedsGoogle(true)
      setError(e instanceof Error ? e.message : 'Could not reschedule')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={onSubmit} aria-label="Reschedule interview" style={{ gap: 10 }}>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
          {needsGoogle && (
            <>
              {' '}
              <a href={connectGoogleUrl(`${window.location.pathname}${window.location.search}`)}>Connect Google</a>
            </>
          )}
        </div>
      )}
      <div className="row" style={{ alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <label className="field">
          New date
          <input className="input" type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label className="field">
          New time
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
      <label className="field">
        Interviewers (emails, comma-separated)
        <input className="input" value={interviewers} onChange={(e) => setInterviewers(e.target.value)} />
      </label>
      <label className="field">
        Reason (optional, internal)
        <input className="input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Candidate missed the 11 am slot" />
      </label>
      <span className="muted" style={{ fontSize: 12 }}>
        {interview.meetLink
          ? 'Google moves the calendar event and emails the candidate and interviewers the new time. The Meet link stays the same.'
          : 'This interview was logged in the app, so only the time here changes; no email is sent.'}
      </span>
      <div className="row">
        <Button type="submit" size="sm" disabled={busy}>
          {busy ? 'Moving…' : 'Reschedule'}
        </Button>
        <Button size="sm" variant="ghost" onClick={onCancel}>
          Keep current time
        </Button>
      </div>
    </form>
  )
}

function InterviewItem({
  interview,
  summary,
  canEdit,
  onCancelled,
  onRescheduled,
}: {
  interview: Interview
  summary: FeedbackSummary | undefined
  canEdit: boolean
  onCancelled: () => void
  onRescheduled: (i: Interview) => void
}) {
  const [cancelling, setCancelling] = useState(false)
  const [moving, setMoving] = useState(false)
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
      <FeedbackLine interview={interview} summary={summary} />
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {canEdit && interview.status === 'SCHEDULED' && !cancelling && !moving && (
        <div className="row" style={{ gap: 8 }}>
          <Button size="sm" variant="ghost" onClick={() => setMoving(true)}>
            Reschedule
          </Button>
          {upcoming && (
            <Button size="sm" variant="ghost" onClick={() => setCancelling(true)}>
              Cancel interview
            </Button>
          )}
        </div>
      )}
      {moving && (
        <RescheduleForm
          interview={interview}
          onCancel={() => setMoving(false)}
          onDone={(moved) => {
            setMoving(false)
            onRescheduled(moved)
          }}
        />
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
  const [summaries, setSummaries] = useState<FeedbackSummary[]>([])
  const [scheduling, setScheduling] = useState(false)
  const [logging, setLogging] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(() => {
    listApplicationInterviews(applicationId).then(setInterviews).catch(() => undefined)
    listFeedbackSummaries(applicationId).then(setSummaries).catch(() => undefined)
  }, [applicationId])
  useEffect(load, [load])

  return (
    <div className="stack" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>Interviews</strong>
        {canEdit && !scheduling && !logging && (
          <div className="row" style={{ gap: 8 }}>
            <Button size="sm" variant="ghost" onClick={() => setLogging(true)}>
              Log an interview held elsewhere
            </Button>
            <Button size="sm" onClick={() => setScheduling(true)}>
              Schedule interview
            </Button>
          </div>
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
      {logging && (
        <LogInterviewForm
          applicationId={applicationId}
          jobTitle={jobTitle}
          onCancel={() => setLogging(false)}
          onLogged={() => {
            setLogging(false)
            setNotice('Interview logged. Use Feedback form below to fill in feedback.')
            load()
            onChanged()
          }}
        />
      )}
      {interviews.length === 0 && !scheduling && !logging && <span className="muted">No interviews yet.</span>}
      {interviews.length > 0 && (
        <ul className="timeline">
          {interviews.map((i) => (
            <InterviewItem
              key={i.id}
              interview={i}
              summary={summaries.find((f) => f.interviewId === i.id)}
              canEdit={canEdit}
              onCancelled={() => {
                setNotice('Interview cancelled. Google has emailed everyone.')
                load()
                onChanged()
              }}
              onRescheduled={(moved) => {
                setNotice(
                  `Rescheduled to ${formatWhen(moved)}.${moved.meetLink ? ' Google has emailed everyone; the Meet link is the same.' : ''}`,
                )
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
