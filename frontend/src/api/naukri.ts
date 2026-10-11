import { api } from './client'
import type { Stage } from './tracker'

/** One applicant row from a Naukri export: job-related columns only (ADR-0026). */
export interface NaukriProfile {
  line: number
  name: string | null
  email: string | null
  phone: string | null
  currentLocation: string | null
  preferredLocations: string | null
  experienceYears: number | null
  experience: string | null
  company: string | null
  designation: string | null
  salary: string | null
  noticePeriod: string | null
  headline: string | null
  keySkills: string | null
  ugDegree: string | null
  ugCollege: string | null
  pgDegree: string | null
  pgCollege: string | null
  appliedOn: string | null
}

export type NaukriRowStatus = 'NEW' | 'EXISTING_CANDIDATE' | 'ALREADY_IN_OPENING' | 'DUPLICATE_IN_FILE' | 'NO_CONTACT'

export interface RankedRow {
  profile: NaukriProfile
  matchPercent: number
  summary: string
  strengths: string[]
  gaps: string[]
  status: NaukriRowStatus
}

export interface RankResult {
  fileName: string
  rankedBy: 'AI' | 'RULES'
  note: string | null
  hasDescription: boolean
  total: number
  missingColumns: string[]
  rows: RankedRow[]
}

export interface NaukriImportResult {
  added: number
  rows: { applicationId: string; name: string; email: string | null; hasEmail: boolean }[]
  skipped: string[]
}

export const canAdd = (r: RankedRow) => r.status === 'NEW' || r.status === 'EXISTING_CANDIDATE'

export const STATUS_LABEL: Record<NaukriRowStatus, string | null> = {
  NEW: null,
  EXISTING_CANDIDATE: 'Already in the ATS',
  ALREADY_IN_OPENING: 'Already in this opening',
  DUPLICATE_IN_FILE: 'Listed twice',
  NO_CONTACT: 'No email or phone',
}

export function rankNaukri(jobId: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  return api<RankResult>(`/jobs/${jobId}/naukri/rank`, { method: 'POST', body })
}

export const importNaukri = (jobId: string, stage: Stage, rows: RankedRow[]) =>
  api<NaukriImportResult>(`/jobs/${jobId}/naukri/import`, {
    method: 'POST',
    body: JSON.stringify({
      stage,
      rows: rows.map((r) => ({ profile: r.profile, matchPercent: r.matchPercent, summary: r.summary.slice(0, 500) })),
    }),
  })
