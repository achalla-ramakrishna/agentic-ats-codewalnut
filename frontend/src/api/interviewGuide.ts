import { api } from './client'

/** Interview questions for staff who interview (INT-21). Never shown to candidates or clients. */

export type Level = 'F' | 'J' | 'S'

export const LEVEL_LABEL: Record<Level, string> = { F: 'Fresher', J: '1–3 years', S: '3+ years' }

export interface InterviewQuestion {
  level: Level
  topic: string | null
  question: string
  strong: string
  redFlags: string
  /** Set for language-specific questions, e.g. "Java". */
  language: string | null
}

export interface InterviewCategory {
  id: string
  name: string
  intro: string | null
  questions: InterviewQuestion[]
}

export interface InterviewGuide {
  howTo: string[]
  scale: { score: number; label: string; evidence: string }[]
  roles: { id: string; name: string; categories: string[] }[]
  categories: InterviewCategory[]
}

export const getInterviewGuide = () => api<InterviewGuide>('/interview-guide')
