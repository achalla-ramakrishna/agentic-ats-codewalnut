import { api } from './client'

/** CodeWalnut-branded résumés for clients (ADR-0012). Never contains a phone number. */

export interface Entry {
  title: string
  subtitle: string
  period: string
  bullets: string[]
}

export interface BrandedResume {
  name: string
  headline: string
  location: string
  email: string
  summary: string
  skills: { label: string; items: string[] }[]
  sections: { title: string; entries: Entry[] }[]
}

export interface DraftResponse {
  exists: boolean
  aiAvailable: boolean
  resume: BrandedResume | null
  showEmail: boolean
  includeScreening: boolean
  screening: string[]
  sourceFileName: string | null
  savedDocumentId: string | null
  updatedBy: string | null
  updatedAt: string | null
}

const base = (applicationId: string) => `/applications/${applicationId}/codewalnut-resume`

export const getCodeWalnutResume = (applicationId: string) => api<DraftResponse>(base(applicationId))
export const generateCodeWalnutResume = (applicationId: string) =>
  api<DraftResponse>(`${base(applicationId)}/generate`, { method: 'POST' })
export const updateCodeWalnutResume = (applicationId: string, input: { resume: BrandedResume; showEmail: boolean; includeScreening: boolean }) =>
  api<DraftResponse>(base(applicationId), { method: 'PUT', body: JSON.stringify(input) })
export const saveCodeWalnutResume = (applicationId: string) =>
  api<{ documentId: string; fileName: string }>(`${base(applicationId)}/save`, { method: 'POST' })
export const codeWalnutResumePdfUrl = (applicationId: string) => `/api/v1${base(applicationId)}.pdf`
export const codeWalnutResumeDocxUrl = (applicationId: string) => `/api/v1${base(applicationId)}.docx`
