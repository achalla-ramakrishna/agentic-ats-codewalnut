import { api } from './client'
import type { AssessmentDetail, Category, CodingView, QuestionInput, QuestionKind } from './assessments'

/** The question bank and the test-paper builder (ADR-0014). */

export type Section = 'QUANT' | 'LOGICAL' | 'VERBAL' | 'FUNDAMENTALS' | 'PRACTICAL' | 'ADVANCED'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type BankStatus = 'ACTIVE' | 'REVIEW' | 'ARCHIVED'

export const SECTION_LABEL: Record<Section, string> = {
  QUANT: 'Numerical ability',
  LOGICAL: 'Logical reasoning',
  VERBAL: 'Verbal ability',
  FUNDAMENTALS: 'Fundamentals',
  PRACTICAL: 'Applied',
  ADVANCED: 'Advanced',
}
/** Who each technical band is for. */
export const SECTION_LEVEL: Partial<Record<Section, string>> = {
  FUNDAMENTALS: 'Freshers',
  PRACTICAL: '1–3 years',
  ADVANCED: '3+ years',
}
export const APTITUDE_SECTIONS: Section[] = ['QUANT', 'LOGICAL', 'VERBAL']
export const TECH_SECTIONS: Section[] = ['FUNDAMENTALS', 'PRACTICAL', 'ADVANCED']
/** Aptitude sections (kept for existing callers). */
export const SECTIONS = APTITUDE_SECTIONS
export const sectionsFor = (area: Category): Section[] => (area === 'APTITUDE' ? APTITUDE_SECTIONS : TECH_SECTIONS)
/** A section's label, with the band's audience for technical areas, e.g. "Fundamentals (Freshers)". */
export const sectionName = (s: Section) => (SECTION_LEVEL[s] ? `${SECTION_LABEL[s]} (${SECTION_LEVEL[s]})` : SECTION_LABEL[s])

/**
 * A test question's section as people read it: "QUANT" → "Numerical ability", and for role tests
 * that mix areas "JAVA:PRACTICAL" → "Java · Applied".
 */
export function sectionTitle(section: string | null | undefined, categoryLabel?: (c: Category) => string): string {
  if (!section) return ''
  const [area, band] = section.includes(':') ? section.split(':') : [null, section]
  const label = SECTION_LABEL[band as Section] ?? band
  return area ? `${categoryLabel ? categoryLabel(area as Category) : area} · ${label}` : label
}

/** Areas with a question bank, in menu order. */
export const BANK_AREAS: Category[] = ['APTITUDE', 'DSA', 'JAVA', 'PYTHON', 'JAVASCRIPT', 'REACT', 'ANGULAR', 'SQL', 'CS_FUNDAMENTALS', 'SYSTEM_DESIGN',
  'NODEJS', 'QA_AUTOMATION', 'DEVOPS', 'DATA_ANALYTICS', 'CODING']
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
  /** Coding problems: the spec and hidden tests. */
  coding: CodingView | null
}

export interface SectionPlan {
  /** Which bank, when a test mixes areas (role tests); omitted means the test's area. */
  area?: Category | null
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

export interface TopicPlan {
  section: Section
  topic: string
  easy: number
  medium: number
  hard: number
}

/** A topic with what it covers and how many approved questions it has at each level. */
export interface TopicGuide {
  id: string
  section: Section
  sectionLabel: string
  level: string | null
  name: string
  covers: string
  example: string
  easy: number
  medium: number
  hard: number
}

export interface BankOverview {
  counts: { area: Category; section: Section; sectionLabel: string; difficulty: Difficulty; count: number }[]
  topics: { section: Section; topic: string; count: number; withPictures: number }[]
  presets: Preset[]
  guide: TopicGuide[]
}

export type Order = 'EASY_FIRST' | 'HARD_FIRST' | 'BY_SECTION' | 'SHUFFLED'

/** Share of easy / medium / hard questions in a paper. */
export const MIXES: { id: string; label: string; split: [number, number, number] }[] = [
  { id: 'balanced', label: 'Balanced — 40% easy, 40% medium, 20% hard', split: [40, 40, 20] },
  { id: 'easy', label: 'Mostly easy — 60% easy, 30% medium, 10% hard', split: [60, 30, 10] },
  { id: 'hard', label: 'Challenging — 20% easy, 40% medium, 40% hard', split: [20, 40, 40] },
  { id: 'easy-only', label: 'Easy only', split: [100, 0, 0] },
  { id: 'medium-only', label: 'Medium only', split: [0, 100, 0] },
  { id: 'hard-only', label: 'Hard only', split: [0, 0, 100] },
]

/**
 * Splits a paper of perTopic questions for each topic by the mix: the paper-wide easy / medium /
 * hard counts follow the percentages, and each topic gets a spread of levels.
 */
export function planTopics(topics: { section: Section; name: string }[], perTopic: number, split: [number, number, number]): TopicPlan[] {
  const total = topics.length * perTopic
  const exact = split.map((p) => (total * p) / 100)
  const counts = exact.map(Math.floor)
  const order = exact.map((v, i) => [v - Math.floor(v), i] as const).sort((a, b) => b[0] - a[0])
  for (let k = 0; counts.reduce((a, b) => a + b, 0) < total; k++) counts[order[k % 3][1]]++
  const levels: (0 | 1 | 2)[] = []
  counts.forEach((c, level) => {
    for (let k = 0; k < c; k++) levels.push(level as 0 | 1 | 2)
  })
  const plans = topics.map((t) => ({ section: t.section, topic: t.name, easy: 0, medium: 0, hard: 0 }))
  const keys = ['easy', 'medium', 'hard'] as const
  levels.forEach((level, k) => {
    plans[k % topics.length][keys[level]]++
  })
  return plans
}

export interface BankFilter {
  area?: Category
  section?: Section
  difficulty?: Difficulty
  topic?: string
  pictures?: boolean
  status?: BankStatus
  q?: string
  page?: number
  size?: number
}

export interface RoleLevel {
  level: 'FRESHER' | 'JUNIOR' | 'MID' | 'SENIOR' | 'LEAD'
  label: string
  years: string
  preset: Preset
}

/** A role (e.g. Java backend developer) with its ready-made paper at each level. */
export interface RoleView {
  id: string
  name: string
  summary: string
  primary: Category
  areas: string[]
  levels: RoleLevel[]
}

export const listRoles = () => api<RoleView[]>('/question-bank/roles')
export const getBankOverview = (area: Category = 'APTITUDE') => api<BankOverview>(`/question-bank/overview?area=${area}`)
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
export const draftBankQuestions = (input: { area: Category; section: Section; topic: string; difficulty: Difficulty; count: number }) =>
  api<{ added: number; notes: string[]; questions: BankQuestion[] }>('/question-bank/draft', { method: 'POST', body: JSON.stringify(input) })
export const buildFromBank = (input: {
  title: string
  area: Category
  durationMinutes: number
  passPercent: number
  sections?: SectionPlan[]
  topics?: TopicPlan[]
  order: Order
}) =>
  api<AssessmentDetail>('/question-bank/build', { method: 'POST', body: JSON.stringify(input) })
export const addFromBank = (assessmentId: string, questionIds: string[]) =>
  api<AssessmentDetail>(`/assessments/${assessmentId}/from-bank`, { method: 'POST', body: JSON.stringify({ questionIds }) })
