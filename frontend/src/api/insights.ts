import { api } from './client'

/** Résumé intelligence (ADR-0010). Advisory: it helps people decide; it never moves anyone. */

export type InsightStatus = 'PENDING' | 'DONE' | 'FAILED' | 'NO_RESUME'

export interface InsightSummary {
  applicationId: string
  status: InsightStatus
  fitPercent: number | null
  headline: string | null
  error: string | null
  stale: boolean
  met: number
  partial: number
  total: number
  skills: string[]
  projects: number
  experienceMonths: number
  graduationYear: number | null
}

export interface Suggestion {
  applicationId: string
  candidateName: string
  stageLabel: string
  fitPercent: number | null
  reason: string
  /** Closest to selection: 0–100 from interview feedback, test and résumé match. */
  readiness?: number | null
}

export interface InsightsResponse {
  available: boolean
  hasDescription: boolean
  analyzed: number
  pending: number
  failed: number
  noResume: number
  notAnalyzed: number
  insights: InsightSummary[]
  contactNext: Suggestion[]
  closestToSelection: Suggestion[]
}

export interface Requirement {
  requirement: string
  assessment: 'MET' | 'PARTIAL' | 'NOT_EVIDENT' | string
  evidence: string
}

export interface ResumeProfile {
  name: string
  email: string
  phone: string
  location: string
  currentRole: string
  experienceMonths: number | null
  graduationYear: number | null
  education: string
  skills: string[] | null
  experience: { role: string; organisation: string; period: string; summary: string }[] | null
  projects: { name: string; summary: string }[] | null
  headline: string
  requirements: Requirement[] | null
  strengths: string[] | null
  gaps: string[] | null
  questionsToAsk: string[] | null
}

export interface InsightDetail {
  applicationId: string
  status: InsightStatus | 'NONE'
  fitPercent: number | null
  headline: string | null
  error: string | null
  stale: boolean
  model: string | null
  analyzedAt: string | null
  documentFileName: string | null
  profile: ResumeProfile | null
}

export interface IntakeItem {
  id: string
  fileName: string
  status: 'PENDING' | 'DONE' | 'FAILED'
  outcome: 'NEW_CANDIDATE' | 'EXISTING_CANDIDATE' | 'ALREADY_IN_OPENING' | null
  applicationId: string | null
  candidateName: string | null
  error: string | null
}

export interface IntakeProgress {
  total: number
  pending: number
  done: number
  failed: number
  newCandidates: number
  existing: number
  items: IntakeItem[]
}

export function uploadResumes(jobId: string, files: File[]) {
  const body = new FormData()
  files.forEach((f) => body.append('files', f))
  return api<IntakeProgress>(`/jobs/${jobId}/resumes`, { method: 'POST', body })
}
export const getIntake = (jobId: string) => api<IntakeProgress>(`/jobs/${jobId}/resumes`)
export const getInsights = (jobId: string) => api<InsightsResponse>(`/jobs/${jobId}/insights`)
export const analyzeAll = (jobId: string) =>
  api<{ queued: number; noResume: number; upToDate: number }>(`/jobs/${jobId}/insights/analyze`, { method: 'POST' })
export const getInsight = (applicationId: string) => api<InsightDetail>(`/applications/${applicationId}/insight`)
export const reanalyze = (applicationId: string) =>
  api<InsightDetail>(`/applications/${applicationId}/insight`, { method: 'POST' })
