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

export interface GoogleStatus {
  available: boolean
  calendarConnected: boolean
  mailConnected: boolean
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

/** Whether this staff member has connected Google (Calendar + Gmail) in this session. */
export const getGoogleStatus = () => api<GoogleStatus>('/google/status')
/** Full-page navigation: the server sends the user to Google and back to returnTo. */
export const connectGoogleUrl = (returnTo: string) => `/api/v1/google/connect?returnTo=${encodeURIComponent(returnTo)}`
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

// ---- Feedback after the interview (INT-24…), staff only ----

export type Attendance = 'HELD' | 'CANDIDATE_NO_SHOW' | 'INTERVIEWER_COULD_NOT_JOIN' | 'ENDED_EARLY'
export type Recommendation = 'STRONG_NO' | 'NO' | 'YES' | 'STRONG_YES'

export const ATTENDANCE_LABEL: Record<Attendance, string> = {
  HELD: 'The interview happened',
  ENDED_EARLY: 'It ended early',
  CANDIDATE_NO_SHOW: "The candidate didn't join",
  INTERVIEWER_COULD_NOT_JOIN: "I couldn't join",
}

export const RECOMMENDATION_LABEL: Record<Recommendation, string> = {
  STRONG_YES: 'Strong hire',
  YES: 'Hire',
  NO: 'No hire',
  STRONG_NO: 'Strong no hire',
}

/** The 1–4 scale shared with the interview questions guide. */
export const RATING_LABEL: Record<number, string> = { 1: 'Weak', 2: 'Below the bar', 3: 'Meets the bar', 4: 'Strong' }

export interface Rating {
  competency: string
  rating: number | null
  note: string | null
}

export interface FeedbackInput {
  attendance: Attendance
  ratings: Rating[]
  strengths: string
  concerns: string
  questionsAsked: string
  recommendation: Recommendation | null
  notes: string
  /** Save as a private draft (nothing required yet) instead of submitting (INT-35). */
  draft?: boolean
}

export interface Feedback {
  authorEmail: string
  authorName: string | null
  attendance: Attendance
  ratings: Rating[]
  averageRating: number | null
  strengths: string | null
  concerns: string | null
  questionsAsked: string | null
  recommendation: Recommendation | null
  notes: string | null
  submittedAt: string
  updatedAt: string
  /** Still being filled in; only its author sees it. */
  draft: boolean
}

export interface FeedbackPage {
  interview: Interview
  onPanel: boolean
  canSubmit: boolean
  mine: Feedback | null
  others: Feedback[]
  hiddenCount: number
  competencies: { name: string; guidance: string }[]
}

export interface FeedbackSummary {
  interviewId: string
  submitted: number
  panelSize: number
  recommendations: Recommendation[]
  mineSubmitted: boolean
  visible: boolean
}

export const getFeedback = (interviewId: string) => api<FeedbackPage>(`/interviews/${interviewId}/feedback`)
export const submitFeedback = (interviewId: string, input: FeedbackInput) =>
  api<FeedbackPage>(`/interviews/${interviewId}/feedback`, { method: 'PUT', body: json(input) })
export const listFeedbackSummaries = (applicationId: string) =>
  api<FeedbackSummary[]>(`/applications/${applicationId}/interview-feedback`)
export const listFeedbackDue = () => api<Interview[]>('/interviews/feedback-due')

/** Feedback opens once the interview has started, unless it was cancelled. */
/** The form opens 15 minutes before the interview, so notes can be taken from the first minute (INT-35). */
export const FEEDBACK_OPENS_BEFORE_MS = 15 * 60 * 1000
export const feedbackOpen = (i: Interview) =>
  i.status === 'SCHEDULED' && new Date(i.startAt).getTime() - FEEDBACK_OPENS_BEFORE_MS <= Date.now()

/** An interview that has started (last 30 days) and where its feedback stands (INT-34). */
export interface RecentInterview {
  interview: Interview
  submitted: number
  panelSize: number
  onPanel: boolean
  mineSubmitted: boolean
  canSubmit: boolean
}

export const listRecentInterviews = () => api<RecentInterview[]>('/interviews/recent')

/** Record an interview held outside the app (e.g. a Meet set up by hand) so the panel can give feedback (INT-33). */
export const logInterview = (
  applicationId: string,
  input: { title: string; startAt: string; durationMinutes: number; timeZone: string; interviewerEmails: string[] },
) => api<Interview>(`/applications/${applicationId}/interviews/log`, { method: 'POST', body: json(input) })

/** Move an interview (INT-20): Google moves the calendar event and emails everyone; the Meet link stays. */
export const rescheduleInterview = (
  id: string,
  input: { startAt: string; durationMinutes: number; timeZone: string; interviewerEmails: string[]; reason: string },
) => api<Interview>(`/interviews/${id}/reschedule`, { method: 'POST', body: json(input) })
