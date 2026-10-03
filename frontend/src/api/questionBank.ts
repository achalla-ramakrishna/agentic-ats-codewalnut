import { api } from './client'
import type { AssessmentDetail, Category, QuestionInput, QuestionKind } from './assessments'

/** The question bank and the test-paper builder (ADR-0014). */

export type Section = 'QUANT' | 'LOGICAL' | 'VERBAL'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type BankStatus = 'ACTIVE' | 'REVIEW' | 'ARCHIVED'

export const SECTION_LABEL: Record<Section, string> = {
  QUANT: 'Numerical ability',
  LOGICAL: 'Logical reasoning',
  VERBAL: 'Verbal ability',
}
export const SECTIONS = Object.keys(SECTION_LABEL) as Section[]
export const DIFFICULTY_LABEL: Record<Difficulty, string> = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' }

export interface BankQuestion {
  id: string
  area: Category
  section: Section
  sectionLabel: string
  topic: string
  difficulty: Difficulty
  kind: QuestionKind
  prompt: string
  code: string | null
  figure: string | null
  options: string[]
  optionFigures: string[] | null
  correct: number[]
  acceptedAnswers: string[]
  points: number
  explanation: string | null
  source: 'BUILT_IN' | 'AI' | 'MANUAL'
  status: BankStatus
  timesUsed: number
}

export interface SectionPlan {
  section: Section
  easy: number
  medium: number
  hard: number
}

export interface Preset {
  id: string
  name: string
  description: string
  durationMinutes: number
  passPercent: number
  sections: SectionPlan[]
}

export interface BankOverview {
  counts: { area: Category; section: Section; sectionLabel: string; difficulty: Difficulty; count: number }[]
  topics: { section: Section; topic: string; count: number; withPictures: number }[]
  presets: Preset[]
}

export type Order = 'EASY_FIRST' | 'BY_SECTION' | 'SHUFFLED'

export interface BankFilter {
  section?: Section
  difficulty?: Difficulty
  topic?: string
  pictures?: boolean
  status?: BankStatus
  q?: string
  page?: number
  size?: number
}

export const getBankOverview = () => api<BankOverview>('/question-bank/overview')
export function listBank(filter: BankFilter) {
  const params = new URLSearchParams()
  Object.entries(filter).forEach(([k, v]) => {
    if (v !== undefined && v !== '' && v !== false) params.set(k, String(v))
  })
  return api<{ items: BankQuestion[]; total: number }>(`/question-bank?${params.toString()}`)
}
export const createBankQuestion = (input: { area: Category; section: Section; topic: string; difficulty: Difficulty; question: QuestionInput }) =>
  api<BankQuestion>('/question-bank', { method: 'POST', body: JSON.stringify(input) })
export const updateBankQuestion = (id: string, input: { area: Category; section: Section; topic: string; difficulty: Difficulty; question: QuestionInput }) =>
  api<BankQuestion>(`/question-bank/${id}`, { method: 'PUT', body: JSON.stringify(input) })
export const approveBankQuestion = (id: string) => api<BankQuestion>(`/question-bank/${id}/approve`, { method: 'POST' })
export const archiveBankQuestion = (id: string) => api<BankQuestion>(`/question-bank/${id}/archive`, { method: 'POST' })
export const draftBankQuestions = (input: { section: Section; topic: string; difficulty: Difficulty; count: number }) =>
  api<{ added: number; notes: string[]; questions: BankQuestion[] }>('/question-bank/draft', { method: 'POST', body: JSON.stringify(input) })
export const buildFromBank = (input: { title: string; area: Category; durationMinutes: number; passPercent: number; sections: SectionPlan[]; order: Order }) =>
  api<AssessmentDetail>('/question-bank/build', { method: 'POST', body: JSON.stringify(input) })
export const addFromBank = (assessmentId: string, questionIds: string[]) =>
  api<AssessmentDetail>(`/assessments/${assessmentId}/from-bank`, { method: 'POST', body: JSON.stringify({ questionIds }) })
