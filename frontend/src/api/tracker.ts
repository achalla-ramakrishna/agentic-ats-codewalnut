import { api } from './client'

export type Stage =
  | 'SOURCED' | 'SCREENING' | 'INTERVIEWED' | 'SHORTLISTED' | 'SUBMITTED_TO_CLIENT' | 'CLIENT_INTERVIEW'
  | 'SELECTED' | 'OFFER_SENT' | 'OFFER_ACCEPTED' | 'JOINED' | 'ON_HOLD' | 'REJECTED' | 'WITHDRAWN'

export type HiringType = 'INTERNAL' | 'CLIENT_DEPLOYED' | 'DIRECT_PLACEMENT'
export type JobStatus = 'OPEN' | 'ON_HOLD' | 'CLOSED'
export type DocumentKind = 'ORIGINAL_RESUME' | 'CODEWALNUT_RESUME'

export interface StageOption {
  key: Stage
  label: string
  exit: boolean
}

export interface Client {
  id: string
  name: string
  notes: string | null
}

export interface Job {
  id: string
  title: string
  client: Client | null
  hiringType: HiringType
  hiringTypeLabel: string
  openings: number | null
  status: JobStatus
  description: string | null
  createdAt: string
  stageCounts: Partial<Record<Stage, number>>
  total: number
}

export interface ApplicationRow {
  id: string
  jobId: string
  jobTitle: string
  candidateId: string
  name: string
  email: string | null
  phone: string | null
  stage: Stage
  stageLabel: string
  updatedAt: string
  lastNote: string | null
  documents: DocumentKind[]
}

export type ImportOutcome = 'NEW' | 'EXISTING_CANDIDATE' | 'ALREADY_IN_OPENING' | 'DUPLICATE_IN_PASTE' | 'ERROR'

export interface ImportRow {
  line: number
  name: string | null
  email: string | null
  phone: string | null
  outcome: ImportOutcome
  issues: string[]
}

export interface ImportResult {
  dryRun: boolean
  added: number
  skipped: number
  rows: ImportRow[]
}

export interface HistoryEvent {
  id: string
  applicationId: string
  candidateName: string
  jobTitle: string
  type: 'CREATED' | 'STAGE_CHANGED' | 'NOTE'
  fromStage: Stage | null
  toStage: Stage | null
  note: string | null
  actorEmail: string | null
  createdAt: string
}

export interface CandidateDocument {
  id: string
  candidateId: string
  kind: DocumentKind
  fileName: string
  contentType: string
  sizeBytes: number
  uploadedBy: string | null
  uploadedAt: string
}

export interface Dashboard {
  openJobs: Job[]
  recentActivity: HistoryEvent[]
}

const json = (body: unknown) => JSON.stringify(body)

export const getStages = () => api<StageOption[]>('/stages')
export const getDashboard = () => api<Dashboard>('/dashboard')
export const listClients = () => api<Client[]>('/clients')
export const createClient = (name: string, notes?: string) =>
  api<Client>('/clients', { method: 'POST', body: json({ name, notes }) })
export const listJobs = () => api<Job[]>('/jobs')
export const getJob = (id: string) => api<Job>(`/jobs/${id}`)
export const createJob = (input: {
  title: string
  clientId?: string
  hiringType: HiringType
  openings?: number
  description?: string
}) => api<Job>('/jobs', { method: 'POST', body: json(input) })
export const updateJob = (id: string, patch: { status?: JobStatus; openings?: number }) =>
  api<Job>(`/jobs/${id}`, { method: 'PATCH', body: json(patch) })
export const listApplications = (jobId: string) => api<ApplicationRow[]>(`/jobs/${jobId}/applications`)
export const addCandidate = (
  jobId: string,
  input: { name: string; email?: string; phone?: string; stage: Stage; note?: string },
) => api<ApplicationRow>(`/jobs/${jobId}/applications`, { method: 'POST', body: json(input) })
export const importCandidates = (jobId: string, text: string, stage: Stage, dryRun: boolean) =>
  api<ImportResult>(`/jobs/${jobId}/applications/import`, { method: 'POST', body: json({ text, stage, dryRun }) })
export const searchApplications = (q: string, stage?: Stage) => {
  const params = new URLSearchParams()
  if (q) params.set('q', q)
  if (stage) params.set('stage', stage)
  return api<ApplicationRow[]>(`/applications?${params.toString()}`)
}
export const moveStage = (applicationId: string, stage: Stage, note?: string) =>
  api<ApplicationRow>(`/applications/${applicationId}/stage`, { method: 'PATCH', body: json({ stage, note }) })
export const addNote = (applicationId: string, text: string) =>
  api<HistoryEvent>(`/applications/${applicationId}/notes`, { method: 'POST', body: json({ text }) })
export const getHistory = (applicationId: string) => api<HistoryEvent[]>(`/applications/${applicationId}/history`)
export const listDocuments = (candidateId: string) => api<CandidateDocument[]>(`/candidates/${candidateId}/documents`)
export const uploadDocument = (candidateId: string, kind: DocumentKind, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api<CandidateDocument>(`/candidates/${candidateId}/documents?kind=${kind}`, { method: 'POST', body: form })
}
export const documentUrl = (id: string, inline = false) => `/api/v1/documents/${id}${inline ? '?inline=true' : ''}`

export const DOCUMENT_LABELS: Record<DocumentKind, string> = {
  ORIGINAL_RESUME: 'Original résumé',
  CODEWALNUT_RESUME: 'CodeWalnut résumé',
}
