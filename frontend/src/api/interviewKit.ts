import { api } from './client'
import type { Category } from './assessments'
import type { Preset } from './questionBank'

/** Interview kits (INT-28…): staff only. */
export interface KitSkill {
  name: string
  area: string | null
  mentions: number
  mustHave: boolean
}

export interface KitQuestion {
  topic: string
  question: string
  strong: string | null
  redFlags: string | null
  /** "job" for questions written from the job description; otherwise the guide category. */
  source: string
}

export interface KitCoding {
  id: string
  title: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  topic: string
  statement: string
  input: string | null
  output: string | null
  sampleInput: string | null
  sampleOutput: string | null
  approach: string | null
  lookFor: string
}

export interface KitRound {
  name: string
  minutes: number
  who: string
  purpose: string
  questions: KitQuestion[]
  coding: KitCoding[]
}

export interface KitContent {
  level: string
  levelLabel: string
  detectedLevel: string
  roleId: string
  roleName: string
  detectedRoleId: string
  skills: KitSkill[]
  notes: string[]
  test: { roleId: string; roleName: string; level: string; levelLabel: string; years: string; areas: string[]; preset: Preset }
  rounds: KitRound[]
  scorecard: { name: string; guidance: string }[]
  hireBar: string
  seed: number
}

export interface KitView {
  jobId: string
  jobTitle: string
  clientName: string | null
  kit: KitContent
  generatedBy: string
  generatedAt: string
  outdated: boolean
}

export interface KitPage {
  jobId: string
  jobTitle: string
  clientName: string | null
  hasDescription: boolean
  kit: KitView | null
  canGenerate: boolean
  levels: { id: string; label: string }[]
  roles: { id: string; label: string }[]
}

export const getInterviewKit = (jobId: string) => api<KitPage>(`/jobs/${jobId}/interview-kit`)
export const generateInterviewKit = (jobId: string, overrides: { level?: string; roleId?: string } = {}) =>
  api<KitPage>(`/jobs/${jobId}/interview-kit`, { method: 'POST', body: JSON.stringify(overrides) })

/** The role's main bank area, for building the online test. */
export const primaryArea = (preset: Preset): Category =>
  (preset.sections.find((s) => s.area && s.area !== 'APTITUDE' && s.area !== 'CODING')?.area ?? preset.sections[0]?.area ?? 'OTHER') as Category
