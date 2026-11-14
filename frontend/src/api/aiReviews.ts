import { api } from './client'
import type { Evaluation, InviteDetail } from './assessments'
import type { Stage } from './tracker'

/** People's verdicts on completed AI interviews (AINT-21): does the candidate go to the next round? */

export type Decision = 'SELECT' | 'HOLD' | 'REJECT'
export type Mark = 'GOOD' | 'OK' | 'WEAK'

export const DECISION_LABEL: Record<Decision, string> = {
  SELECT: 'Select for next round',
  HOLD: 'Maybe — discuss',
  REJECT: 'Don’t progress',
}

export const DECISION_ICON: Record<Decision, string> = { SELECT: '✅', HOLD: '🤔', REJECT: '❌' }

export const MARK_LABEL: Record<Mark, string> = { GOOD: '✓ Good', OK: '~ Okay', WEAK: '✗ Weak' }

export interface AnswerMark {
  position: number
  mark: Mark | null
  note: string | null
}

export interface VerdictBrief {
  reviewerName: string
  decision: Decision
}

export interface VerdictView {
  reviewerName: string
  reviewerEmail: string
  decision: Decision
  decisionLabel: string
  feedback: string | null
  marks: AnswerMark[]
  updatedAt: string
  mine: boolean
}

export interface QueueRow {
  inviteId: string
  candidateName: string
  jobId: string
  jobTitle: string
  stage: Stage
  submittedAt: string | null
  aiScore: number | null
  evaluation: Evaluation | null
  mine: Decision | null
  verdicts: VerdictBrief[]
}

export interface ReviewQueue {
  interviews: QueueRow[]
  toReview: number
}

export interface ReviewPage {
  inviteId: string
  candidateName: string
  jobId: string
  jobTitle: string
  stage: Stage
  detail: InviteDetail
  verdicts: VerdictView[]
}

export const reviewQueue = () => api<ReviewQueue>('/ai-reviews')
export const getReview = (inviteId: string) => api<ReviewPage>(`/ai-reviews/${inviteId}`)
export const saveVerdict = (inviteId: string, input: { decision: Decision; feedback: string; marks: AnswerMark[] }) =>
  api<ReviewPage>(`/ai-reviews/${inviteId}`, { method: 'PUT', body: JSON.stringify(input) })
