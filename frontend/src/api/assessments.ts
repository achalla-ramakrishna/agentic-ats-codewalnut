import { api } from './client'
import type { Message } from './messages'

/** Online tests (ADR-0011). Correct answers appear only in staff responses. */

export type Category =
  | 'APTITUDE'
  | 'JAVA'
  | 'PYTHON'
  | 'JAVASCRIPT'
  | 'REACT'
  | 'ANGULAR'
  | 'SQL'
  | 'CS_FUNDAMENTALS'
  | 'SYSTEM_DESIGN'
  | 'NODEJS'
  | 'QA_AUTOMATION'
  | 'DEVOPS'
  | 'DATA_ANALYTICS'
  | 'CODING'
  | 'OTHER'
export type QuestionKind = 'SINGLE_CHOICE' | 'MULTI_CHOICE' | 'SHORT_ANSWER' | 'CODING'
export type InviteStatus = 'SENT' | 'STARTED' | 'SUBMITTED' | 'EXPIRED' | 'CANCELLED'

export const CATEGORY_LABEL: Record<Category, string> = {
  APTITUDE: 'Aptitude',
  JAVA: 'Java',
  PYTHON: 'Python',
  JAVASCRIPT: 'JavaScript',
  REACT: 'React',
  ANGULAR: 'Angular',
  SQL: 'SQL',
  CS_FUNDAMENTALS: 'CS fundamentals',
  SYSTEM_DESIGN: 'System design',
  NODEJS: 'Node.js',
  QA_AUTOMATION: 'Testing & QA automation',
  DEVOPS: 'DevOps & cloud',
  DATA_ANALYTICS: 'Data analytics',
  CODING: 'Coding',
  OTHER: 'Other',
}

export const KIND_LABEL: Record<QuestionKind, string> = {
  SINGLE_CHOICE: 'One right answer',
  MULTI_CHOICE: 'Several right answers',
  SHORT_ANSWER: 'Short answer',
  CODING: 'Write code',
}

// ---- coding questions (ADR-0016) ----

export type CodeLanguage = 'java' | 'python' | 'javascript' | 'cpp'

export const LANGUAGE_LABEL: Record<CodeLanguage, string> = {
  java: 'Java',
  python: 'Python',
  javascript: 'JavaScript',
  cpp: 'C++',
}

/** What goes to the program's stdin, and what it should print. */
export interface TestCase {
  input: string
  output: string
}

/** The part of a coding question candidates see. Hidden tests are never in here. */
export interface CodingSpec {
  languages: CodeLanguage[]
  starter: Partial<Record<CodeLanguage, string>>
  samples: TestCase[]
  timeLimitSeconds: number
  memoryMb: number
  inputFormat: string | null
  outputFormat: string | null
  constraints: string | null
}

/** Staff view: the spec plus the hidden tests. */
export interface CodingView {
  spec: CodingSpec
  tests: TestCase[]
}

export interface CodingInput {
  languages?: CodeLanguage[]
  starter?: Partial<Record<CodeLanguage, string>>
  samples: TestCase[]
  tests: TestCase[]
  timeLimitSeconds?: number
  inputFormat?: string
  outputFormat?: string
  constraints?: string
}

export type CaseStatus = 'PASSED' | 'WRONG_ANSWER' | 'COMPILE_ERROR' | 'RUNTIME_ERROR' | 'TIME_LIMIT' | 'MEMORY_LIMIT' | 'INTERNAL_ERROR'

export const CASE_STATUS_LABEL: Record<CaseStatus, string> = {
  PASSED: 'Passed',
  WRONG_ANSWER: 'Wrong answer',
  COMPILE_ERROR: "Didn't compile",
  RUNTIME_ERROR: 'Crashed',
  TIME_LIMIT: 'Too slow',
  MEMORY_LIMIT: 'Out of memory',
  INTERNAL_ERROR: 'Runner error',
}

/** One test case run. input/expected: shown for samples, and to staff for hidden tests. */
export interface CaseResult {
  sample: boolean
  passed: boolean
  status: CaseStatus
  output: string | null
  error: string | null
  timeSeconds: number | null
  memoryKb: number | null
  input: string | null
  expected: string | null
}

export interface RunCodeResult {
  compiled: boolean
  compileOutput: string | null
  cases: CaseResult[]
  passed: number
  total: number
  /** Sample runs left for this question (-1 for staff checks). */
  runsLeft: number
}

export interface CodeResult {
  language: CodeLanguage | null
  source: string | null
  passed: number
  total: number
  earned: number
  compileOutput: string | null
  cases: CaseResult[]
}

/** Advisory browser signals while the test was taken. */
export interface Activity {
  tabSwitches: number
  pastes: number
  pastedChars: number
  runs: number
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
  /** Candidates who started or submitted it; a test can be deleted only while this is 0. */
  taken: number
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
  coding: CodingView | null
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
  coding?: CodingInput
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
  /** Submitted and nobody who manages tests has opened the answers or marked it seen yet. */
  newResult: boolean
  /** Code grading after submit: null when the test has no coding questions. */
  grading: 'PENDING' | 'DONE' | 'FAILED' | null
}

/** A submitted test nobody has looked at yet, with where to find the candidate. */
export interface NewResult {
  invite: InviteView
  candidateName: string
  jobId: string
  jobTitle: string
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
  codeResult: CodeResult | null
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
  coding: CodingSpec | null
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
export const deleteAssessment = (id: string) => api<{ title: string; openInvites: number }>(`/assessments/${id}`, { method: 'DELETE' })
export const duplicateAssessment = (id: string) => api<AssessmentDetail>(`/assessments/${id}/duplicate`, { method: 'POST' })

// sending and results
export const sendTest = (applicationId: string, input: { assessmentId: string; dueDays: number; sendEmail: boolean; sendWhatsApp: boolean; note?: string }) =>
  api<SendResult>(`/applications/${applicationId}/tests`, json('POST', input))
export const listApplicationTests = (applicationId: string) => api<InviteView[]>(`/applications/${applicationId}/tests`)
/** A candidate's link to their test (same as in the message they got); they sign in with Google to open it. */
export const testLink = (inviteId: string) => `${window.location.origin}/tests/${inviteId}`
export const listNewResults = () => api<NewResult[]>('/tests/new-results')
export const markResultsSeen = (inviteIds: string[]) => api<{ marked: number }>('/tests/seen', json('POST', { inviteIds }))
export const listJobTests = (jobId: string) => api<InviteView[]>(`/jobs/${jobId}/tests`)
export const getTestResult = (inviteId: string) =>
  api<{ invite: InviteView; answers: AnswerReview[]; sections: SectionScore[]; activity: Activity | null }>(`/tests/${inviteId}`)
export const regradeTest = (inviteId: string) => api<InviteView>(`/tests/${inviteId}/regrade`, { method: 'POST' })
export const codingStatus = () => api<{ available: boolean; languages: CodeLanguage[] }>('/coding/status')
export const tryQuestion = (assessmentId: string, questionId: string, input: { language: CodeLanguage; source: string }) =>
  api<RunCodeResult>(`/assessments/${assessmentId}/questions/${questionId}/run`, json('POST', input))
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
export const runCode = (id: string, questionId: string, input: { language: CodeLanguage; source: string }) =>
  api<RunCodeResult>(`/candidate/tests/${id}/questions/${questionId}/run`, json('POST', input))
export const reportActivity = (id: string, input: { tabSwitches: number; pastes: number; pastedChars: number }) =>
  api<void>(`/candidate/tests/${id}/activity`, json('POST', input))

export function testStatusLabel(i: Pick<InviteView, 'status' | 'percent' | 'passed'>): string {
  switch (i.status) {
    case 'SENT':
      return 'Sent, not started'
    case 'STARTED':
      return 'In progress'
    case 'SUBMITTED':
      return i.percent == null ? 'Submitted · grading code' : `${i.percent}%${i.passed ? ' · passed' : ''}`
    case 'EXPIRED':
      return 'Not taken (past due)'
    case 'CANCELLED':
      return 'Cancelled'
  }
}
