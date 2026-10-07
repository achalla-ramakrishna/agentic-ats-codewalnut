import { api } from './client'

export type Stage =
  // Only SOURCED, INTERVIEWED, SHORTLISTED, OFFER_SENT, JOINED, ON_HOLD and REJECTED are in use (PIPE-15);
  // the others appear only in older history.
  | 'SOURCED' | 'SCREENING' | 'INTERVIEWED' | 'SHORTLISTED' | 'SUBMITTED_TO_CLIENT' | 'CLIENT_INTERVIEW'
  | 'SELECTED' | 'OFFER_SENT' | 'OFFER_ACCEPTED' | 'JOINED' | 'ON_HOLD' | 'REJECTED' | 'WITHDRAWN'

export type HiringType = 'INTERNAL' | 'CLIENT_DEPLOYED' | 'DIRECT_PLACEMENT'
export type JobStatus = 'OPEN' | 'ON_HOLD' | 'CLOSED'
export type DocumentKind =
  | 'ORIGINAL_RESUME'
  | 'CODEWALNUT_RESUME'
  | 'AADHAAR'
  | 'PAN'
  | 'DEGREE_CERTIFICATE'
  | 'PHOTO'
  | 'OTHER'
export type WorkMode = 'ONSITE' | 'HYBRID' | 'REMOTE'

export const WORK_MODES: { key: WorkMode; label: string }[] = [
  { key: 'ONSITE', label: 'Office' },
  { key: 'HYBRID', label: 'Hybrid' },
  { key: 'REMOTE', label: 'Remote' },
]

export interface PublicJob {
  slug: string
  title: string
  company: string
  location: string | null
  workMode: string | null
  employmentType: string | null
  description: string | null
  acceptingApplications: boolean
}

export interface MyApplication {
  id: string
  newMessages: number
  slug: string | null
  jobTitle: string
  status: string
  appliedAt: string
}

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
  location: string | null
  workMode: WorkMode | null
  employmentType: string | null
  published: boolean
  publicSlug: string | null
  createdAt: string
  stageCounts: Partial<Record<Stage, number>>
  total: number
}

export interface ApplicationRow {
  id: string
  jobId: string
  jobTitle: string
  clientName: string | null
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
  type:
    | 'CREATED'
    | 'STAGE_CHANGED'
    | 'NOTE'
    | 'INTERVIEW_SCHEDULED'
    | 'INTERVIEW_CANCELLED'
    | 'EMAIL_SENT'
    | 'DOCS_REQUESTED'
    | 'DOC_UPLOADED'
    | 'SHARED_WITH_CLIENT'
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
/** How an opening is hired for, in menu order. */
export const HIRING_TYPES: { key: HiringType; label: string }[] = [
  { key: 'CLIENT_DEPLOYED', label: 'Client – on CodeWalnut payroll' },
  { key: 'DIRECT_PLACEMENT', label: 'Client – on client payroll' },
  { key: 'INTERNAL', label: 'Internal (CodeWalnut)' },
]
export const updateClient = (id: string, patch: { name?: string; notes?: string }) =>
  api<Client>(`/clients/${id}`, { method: 'PATCH', body: json(patch) })
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
export interface JobPatch {
  title?: string
  status?: JobStatus
  openings?: number
  description?: string
  location?: string
  workMode?: WorkMode
  employmentType?: string
  published?: boolean
  /** Moving to another client is refused while candidates are still shared with the current one. */
  clientId?: string
  hiringType?: HiringType
}

export const updateJob = (id: string, patch: JobPatch) =>
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
  AADHAAR: 'Aadhaar card (masked)',
  PAN: 'PAN card',
  DEGREE_CERTIFICATE: 'Degree certificate / marksheet',
  PHOTO: 'Passport-size photo',
  OTHER: 'Other document',
}

/** Background-verification documents, in display order. Aadhaar and PAN are government IDs. */
export const BGV_KINDS: DocumentKind[] = ['AADHAAR', 'PAN', 'DEGREE_CERTIFICATE', 'PHOTO', 'OTHER']
export const ID_KINDS: DocumentKind[] = ['AADHAAR', 'PAN']
/** Kinds a candidate can upload themselves (everything except the CodeWalnut résumé). */
export const CANDIDATE_KINDS: DocumentKind[] = ['ORIGINAL_RESUME', ...BGV_KINDS]

// ---- shareable job links ----

export const jobLink = (slug: string) => `${window.location.origin}/apply/${slug}`
export const getPublicJob = (slug: string) => api<PublicJob>(`/public/jobs/${slug}`)
export const listMyApplications = () => api<MyApplication[]>('/candidate/applications')
export const applyToJob = (input: {
  slug: string
  name: string
  phone: string
  note?: string
  consent: boolean
  resume: File
}) => {
  const form = new FormData()
  form.append('slug', input.slug)
  form.append('name', input.name)
  form.append('phone', input.phone)
  if (input.note) form.append('note', input.note)
  form.append('consent', String(input.consent))
  form.append('resume', input.resume)
  return api<MyApplication>('/candidate/applications', { method: 'POST', body: form })
}

/** One opening a candidate is in (PIPE-13). current: the application the drawer is showing. */
export interface CandidateOpening {
  applicationId: string
  jobId: string
  jobTitle: string
  clientName: string | null
  jobStatus: JobStatus
  stage: Stage
  stageLabel: string
  current: boolean
  addedAt: string
}

export const listCandidateOpenings = (applicationId: string) =>
  api<CandidateOpening[]>(`/applications/${applicationId}/openings`)
/** Put the same candidate forward for another opening (PIPE-14). */
export const addToOpening = (applicationId: string, input: { jobId: string; stage: Stage; note: string }) =>
  api<ApplicationRow>(`/applications/${applicationId}/openings`, { method: 'POST', body: json(input) })
