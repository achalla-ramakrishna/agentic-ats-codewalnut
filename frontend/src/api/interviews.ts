import { api } from './client'

export type InterviewStatus = 'SCHEDULED' | 'CANCELLED'

export interface Interview {
  id: string
  applicationId: string
  jobId: string
  jobTitle: string
  candidateId: string
  candidateName: string
  title: string
  startAt: string
  endAt: string
  timeZone: string
  interviewers: string[]
  message: string | null
  status: InterviewStatus
  meetLink: string | null
  calendarLink: string | null
  organizerEmail: string
  cancelReason: string | null
  createdAt: string
}

export interface CandidateInterview {
  jobTitle: string
  title: string
  startAt: string
  endAt: string
  timeZone: string
  meetLink: string | null
}

export interface CalendarStatus {
  available: boolean
  connected: boolean
  redirectUri: string
}

export interface ScheduleInput {
  title: string
  startAt: string
  durationMinutes: number
  timeZone: string
  interviewerEmails: string[]
  message: string
}

export interface CandidateContact {
  id: string
  name: string
  email: string | null
  phone: string | null
}

const json = (body: unknown) => JSON.stringify(body)

export const getCalendarStatus = () => api<CalendarStatus>('/calendar/status')
/** Full-page navigation: the server sends the user to Google and back to returnTo. */
export const connectCalendarUrl = (returnTo: string) =>
  `/api/v1/calendar/connect?returnTo=${encodeURIComponent(returnTo)}`
export const listUpcomingInterviews = () => api<Interview[]>('/interviews')
export const listApplicationInterviews = (applicationId: string) =>
  api<Interview[]>(`/applications/${applicationId}/interviews`)
export const scheduleInterview = (applicationId: string, input: ScheduleInput) =>
  api<Interview>(`/applications/${applicationId}/interviews`, { method: 'POST', body: json(input) })
export const cancelInterview = (id: string, reason?: string) =>
  api<Interview>(`/interviews/${id}/cancel`, { method: 'POST', body: json({ reason: reason || null }) })
export const listMyInterviews = () => api<CandidateInterview[]>('/candidate/interviews')
export const updateCandidate = (id: string, patch: { name?: string; email?: string; phone?: string }) =>
  api<CandidateContact>(`/candidates/${id}`, { method: 'PATCH', body: json(patch) })

export const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata'

/** "Tue, 7 Oct 2026, 11:00 am – 11:45 am IST", in the interview's own time zone. */
export function formatWhen(i: { startAt: string; endAt: string; timeZone: string }) {
  const options: Intl.DateTimeFormatOptions = {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZone: i.timeZone,
    timeZoneName: 'short',
  }
  let format: Intl.DateTimeFormat
  try {
    format = new Intl.DateTimeFormat('en-IN', options)
  } catch {
    format = new Intl.DateTimeFormat('en-IN', { ...options, timeZone: undefined })
  }
  return format.formatRange(new Date(i.startAt), new Date(i.endAt))
}
