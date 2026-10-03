import { api } from './client'
import type { Message } from './messages'

/** Online tests (ADR-0011). Correct answers appear only in staff responses. */

export type Category = 'APTITUDE' | 'JAVA' | 'PYTHON' | 'JAVASCRIPT' | 'SQL' | 'OTHER'
export type QuestionKind = 'SINGLE_CHOICE' | 'MULTI_CHOICE' | 'SHORT_ANSWER'
export type InviteStatus = 'SENT' | 'STARTED' | 'SUBMITTED' | 'EXPIRED' | 'CANCELLED'

export const CATEGORY_LABEL: Record<Category, string> = {
  APTITUDE: 'Aptitude',
  JAVA: 'Java',
  PYTHON: 'Python',
  JAVASCRIPT: 'JavaScript',
  SQL: 'SQL',
  OTHER: 'Other',
}

export const KIND_LABEL: Record<QuestionKind, string> = {
  SINGLE_CHOICE: 'One right answer',
  MULTI_CHOICE: 'Several right answers',
  SHORT_ANSWER: 'Short answer',
}

export interface AssessmentSummary {
  id: string
  title: string
  category: Category
  description: string | null
  durationMinutes: number
  passPercent: number
  status: 'DRAFT' | 'READY' | 'ARCHIVED'
  questionCount: number
  totalPoints: number
  invites: number
  updatedAt: string
}

export interface QuestionView {
  id: string
  position: number
  kind: QuestionKind
  prompt: string
  code: string | null
  options: string[]
  correct: number[]
  acceptedAnswers: string[]
  points: number
  explanation: string | null
  aiDrafted: boolean
  /** SVG drawn by the app, or an uploaded PNG/JPEG data URI. */
  figure: string | null
  /** One picture per option when the options are figures. */
  optionFigures: string[] | null
  section: string | null
  topic: string | null
  difficulty: 'EASY' | 'MEDIUM' | 'HARD' | null
}

export interface AssessmentDetail {
  summary: AssessmentSummary
  questions: QuestionView[]
}

export interface QuestionInput {
  kind: QuestionKind
  prompt: string
  code?: string
  options?: string[]
  correct?: number[]
  acceptedAnswers?: string[]
  points: number
  explanation?: string
  figure?: string | null
}

export interface InviteView {
  id: string
  applicationId: string
  assessmentId: string
  title: string
  category: Category
  status: InviteStatus
  sentBy: string
  sentAt: string
  dueAt: string
  startedAt: string | null
  submittedAt: string | null
  score: number | null
  maxScore: number | null
  percent: number | null
  passed: boolean | null
  passPercent: number
  reminderCount: number
  lastRemindedAt: string | null
  needsNudge: boolean
}

export interface SendResult {
  invite: InviteView
  message: Message
}

export interface AnswerReview {
  position: number
  kind: QuestionKind
  prompt: string
  code: string | null
  options: string[]
  given: string[]
  correct: number[]
  acceptedAnswers: string[]
  points: number
  earned: number
  figure: string | null
  optionFigures: string[] | null
  section: string | null
}

export interface SectionScore {
  section: string
  label: string
  score: number
  max: number
  questions: number
}

export interface MyTest {
  id: string
  title: string
  category: Category
  description: string | null
  jobTitle: string
  questionCount: number
  durationMinutes: number
  status: InviteStatus
  dueAt: string
  startedAt: string | null
  deadlineAt: string | null
  submittedAt: string | null
}

export interface CandidateQuestion {
  id: string
  position: number
  kind: QuestionKind
  prompt: string
  code: string | null
  options: string[]
  points: number
  figure: string | null
  optionFigures: string[] | null
  section: string | null
}

export interface TakeTest {
  test: MyTest
  questions: CandidateQuestion[]
  answers: Record<string, string[]>
  secondsLeft: number
}

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  body: body === undefined ? undefined : JSON.stringify(body),
})

// library
export const listAssessments = () => api<AssessmentSummary[]>('/assessments')
export const getAssessment = (id: string) => api<AssessmentDetail>(`/assessments/${id}`)
export const createAssessment = (input: { title: string; category: Category; description?: string; durationMinutes: number; passPercent: number }) =>
  api<AssessmentDetail>('/assessments', json('POST', input))
export const updateAssessment = (id: string, input: Partial<{ title: string; category: Category; description: string; durationMinutes: number; passPercent: number }>) =>
  api<AssessmentDetail>(`/assessments/${id}`, json('PATCH', input))
export const addQuestion = (id: string, q: QuestionInput) => api<AssessmentDetail>(`/assessments/${id}/questions`, json('POST', q))
export const updateQuestion = (id: string, questionId: string, q: QuestionInput) =>
  api<AssessmentDetail>(`/assessments/${id}/questions/${questionId}`, json('PUT', q))
export const deleteQuestion = (id: string, questionId: string) =>
  api<AssessmentDetail>(`/assessments/${id}/questions/${questionId}`, { method: 'DELETE' })
export const draftQuestions = (id: string, input: { topic?: string; level?: string; count: number }) =>
  api<{ added: number; notes: string[]; assessment: AssessmentDetail }>(`/assessments/${id}/draft`, json('POST', input))
export const publishAssessment = (id: string) => api<AssessmentDetail>(`/assessments/${id}/publish`, { method: 'POST' })
export const unpublishAssessment = (id: string) => api<AssessmentDetail>(`/assessments/${id}/unpublish`, { method: 'POST' })
export const archiveAssessment = (id: string) => api<AssessmentDetail>(`/assessments/${id}/archive`, { method: 'POST' })
export const duplicateAssessment = (id: string) => api<AssessmentDetail>(`/assessments/${id}/duplicate`, { method: 'POST' })

// sending and results
export const sendTest = (applicationId: string, input: { assessmentId: string; dueDays: number; sendEmail: boolean; sendWhatsApp: boolean; note?: string }) =>
  api<SendResult>(`/applications/${applicationId}/tests`, json('POST', input))
export const listApplicationTests = (applicationId: string) => api<InviteView[]>(`/applications/${applicationId}/tests`)
export const listJobTests = (jobId: string) => api<InviteView[]>(`/jobs/${jobId}/tests`)
export const getTestResult = (inviteId: string) =>
  api<{ invite: InviteView; answers: AnswerReview[]; sections: SectionScore[] }>(`/tests/${inviteId}`)
export const remindTest = (inviteId: string, input: { sendEmail: boolean; sendWhatsApp: boolean }) =>
  api<SendResult>(`/tests/${inviteId}/remind`, json('POST', input))
export const cancelTest = (inviteId: string) => api<InviteView>(`/tests/${inviteId}/cancel`, { method: 'POST' })

// candidate
export const listMyTests = () => api<MyTest[]>('/candidate/tests')
export const startTest = (id: string) => api<TakeTest>(`/candidate/tests/${id}/start`, { method: 'POST' })
export const resumeTest = (id: string) => api<TakeTest>(`/candidate/tests/${id}`)
export const saveAnswers = (id: string, answers: Record<string, string[]>) =>
  api<TakeTest>(`/candidate/tests/${id}/answers`, json('PUT', { answers }))
export const submitTest = (id: string, answers: Record<string, string[]>) =>
  api<MyTest>(`/candidate/tests/${id}/submit`, json('POST', { answers }))

export function testStatusLabel(i: Pick<InviteView, 'status' | 'percent' | 'passed'>): string {
  switch (i.status) {
    case 'SENT':
      return 'Sent, not started'
    case 'STARTED':
      return 'In progress'
    case 'SUBMITTED':
      return `${i.percent}%${i.passed ? ' · passed' : ''}`
    case 'EXPIRED':
      return 'Not taken (past due)'
    case 'CANCELLED':
      return 'Cancelled'
  }
}
