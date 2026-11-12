import { api } from './client'
import type { AssessmentDetail, Evaluation, InviteStatus } from './assessments'
import type { Stage } from './tracker'

/** AI interviews (ADR-0030): build one for an opening, record answers, and the opening's ranking. */

export interface RankedCandidate {
  inviteId: string
  applicationId: string
  candidateName: string
  stage: Stage
  status: InviteStatus
  evaluation: Evaluation | null
  usesAi: boolean
  aiScore: number | null
  markedPercent: number | null
  /** Rating 1–5 per area (TECHNICAL, ARCHITECTURE, COMMUNICATION, EXPERIENCE, READINESS); 0 = not judged. */
  areas: Record<string, number>
  summary: string | null
  noticePeriod: string | null
  expectedSalary: string | null
  sentAt: string
  submittedAt: string | null
  videos: number
}

export interface JobInterviews {
  candidates: RankedCandidate[]
  sent: number
  started: number
  submitted: number
  reviewed: number
}

export const AREA_SHORT: Record<string, string> = {
  TECHNICAL: 'Tech',
  ARCHITECTURE: 'Design',
  COMMUNICATION: 'Comm.',
  EXPERIENCE: 'Exp.',
  READINESS: 'Ready',
}

export const createAiInterview = (jobId: string, durationMinutes?: number) =>
  api<{ interview: AssessmentDetail; notes: string[] }>('/ai-interviews', {
    method: 'POST',
    body: JSON.stringify({ jobId, durationMinutes }),
  })

export const jobInterviews = (jobId: string) => api<JobInterviews>(`/jobs/${jobId}/ai-interviews`)

export const reviewAgain = (inviteId: string) => api<void>(`/tests/${inviteId}/review-again`, { method: 'POST' })

/** Where staff play a recorded answer (the browser streams it, so seeking works). */
export const videoUrl = (inviteId: string, videoId: string) => `/api/v1/tests/${inviteId}/videos/${videoId}`

export function uploadAnswer(testId: string, questionId: string, video: Blob, transcript: string, seconds: number) {
  const body = new FormData()
  body.append('file', video, video.type.includes('mp4') ? 'answer.mp4' : 'answer.webm')
  body.append('transcript', transcript)
  body.append('seconds', String(Math.max(1, Math.round(seconds))))
  return api<{ videoId: string; seconds: number; sizeBytes: number }>(`/candidate/tests/${testId}/questions/${questionId}/video`, {
    method: 'POST',
    body,
  })
}
