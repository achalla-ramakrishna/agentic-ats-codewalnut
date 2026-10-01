import { api } from './client'
import type { DocumentKind } from './tracker'

export interface CandidateProfile {
  id: string
  name: string
  email: string | null
  phone: string | null
  dateOfBirth: string | null
  currentAddress: string | null
  permanentAddress: string | null
  college: string | null
  degree: string | null
  graduationYear: number | null
  linkedinUrl: string | null
  emergencyContact: string | null
  profileUpdatedAt: string | null
}

/** Send only changed fields; a blank string clears a field (graduationYear 0 clears it). */
export type ProfilePatch = Partial<{
  name: string
  email: string
  phone: string
  dateOfBirth: string
  currentAddress: string
  permanentAddress: string
  college: string
  degree: string
  graduationYear: number
  linkedinUrl: string
  emergencyContact: string
}>

export interface DocumentRequest {
  id: string
  kind: DocumentKind
  label: string
  requestedBy: string
  requestedAt: string
  fulfilledAt: string | null
}

export interface MyDocument {
  id: string
  kind: DocumentKind
  label: string
  fileName: string
  uploadedAt: string
}

const json = (body: unknown) => JSON.stringify(body)

export const getProfile = (candidateId: string) => api<CandidateProfile>(`/candidates/${candidateId}/profile`)
export const updateProfile = (candidateId: string, patch: ProfilePatch) =>
  api<CandidateProfile>(`/candidates/${candidateId}`, { method: 'PATCH', body: json(patch) })
export const listDocumentRequests = (candidateId: string) => api<DocumentRequest[]>(`/candidates/${candidateId}/document-requests`)
export const requestDocuments = (candidateId: string, kinds: DocumentKind[]) =>
  api<DocumentRequest[]>(`/candidates/${candidateId}/document-requests`, { method: 'POST', body: json({ kinds }) })

export const getMyProfile = () => api<CandidateProfile>('/candidate/profile')
export const updateMyProfile = (patch: ProfilePatch) => api<CandidateProfile>('/candidate/profile', { method: 'PATCH', body: json(patch) })
export const getMyDocuments = () => api<{ documents: MyDocument[]; requested: DocumentRequest[] }>('/candidate/documents')
export const uploadMyDocument = (kind: DocumentKind, file: File) => {
  const form = new FormData()
  form.append('kind', kind)
  form.append('file', file)
  return api<MyDocument>('/candidate/documents', { method: 'POST', body: form })
}

/** The fields shown and edited on a profile, in order. */
export const PROFILE_FIELDS: { key: Exclude<keyof ProfilePatch, 'name' | 'email' | 'phone'>; label: string; type?: string; multiline?: boolean }[] = [
  { key: 'dateOfBirth', label: 'Date of birth', type: 'date' },
  { key: 'currentAddress', label: 'Current address', multiline: true },
  { key: 'permanentAddress', label: 'Permanent address', multiline: true },
  { key: 'college', label: 'College / university' },
  { key: 'degree', label: 'Degree' },
  { key: 'graduationYear', label: 'Graduation year', type: 'number' },
  { key: 'linkedinUrl', label: 'LinkedIn profile', type: 'url' },
  { key: 'emergencyContact', label: 'Emergency contact (name and phone)' },
]
