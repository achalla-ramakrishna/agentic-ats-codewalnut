import { api } from './client'
import type { CandidateMessage } from './messages'
import type { DocumentKind } from './tracker'

export interface ClientContact {
  id: string
  clientId: string
  email: string
  name: string | null
  active: boolean
  lastLoginAt: string | null
}

export interface ClientShare {
  applicationId: string
  clientName: string
  active: boolean
  includeContact: boolean
  includeProfile: boolean
  documentIds: string[]
  note: string | null
  sharedBy: string | null
  sharedAt: string | null
  revokedAt: string | null
  lastViewedAt: string | null
}

export interface ClientMe {
  email: string
  name: string | null
  clientName: string
}

export interface SharedDocument {
  id: string
  kind: DocumentKind
  label: string
  fileName: string
  uploadedAt: string
}

export interface ClientCandidate {
  applicationId: string
  name: string
  jobTitle: string
  stageLabel: string
  sharedAt: string
  note: string | null
  email: string | null
  phone: string | null
  profile: {
    dateOfBirth: string | null
    currentAddress: string | null
    permanentAddress: string | null
    college: string | null
    degree: string | null
    graduationYear: number | null
    linkedinUrl: string | null
    emergencyContact: string | null
  } | null
  documents: SharedDocument[]
}

const json = (body: unknown) => JSON.stringify(body)

// staff
export const listContacts = (clientId: string) => api<ClientContact[]>(`/clients/${clientId}/contacts`)
export const addContact = (clientId: string, email: string, name?: string) =>
  api<ClientContact>(`/clients/${clientId}/contacts`, { method: 'POST', body: json({ email, name }) })
export const removeContact = (contactId: string) => api<ClientContact>(`/client-contacts/${contactId}`, { method: 'DELETE' })
export const getShare = (applicationId: string) => api<ClientShare>(`/applications/${applicationId}/client-share`)
export const saveShare = (
  applicationId: string,
  input: { includeContact: boolean; includeProfile: boolean; documentIds: string[]; note?: string },
) => api<ClientShare>(`/applications/${applicationId}/client-share`, { method: 'PUT', body: json(input) })
export const revokeShare = (applicationId: string) => api<ClientShare>(`/applications/${applicationId}/client-share`, { method: 'DELETE' })

// client portal
export const getClientMe = () => api<ClientMe>('/client/me')
export const listSharedCandidates = () => api<ClientCandidate[]>('/client/candidates')
export const clientDocumentUrl = (id: string, inline = false) => `/api/v1/client/documents/${id}${inline ? '?inline=true' : ''}`
export const listClientMessages = (applicationId: string) => api<CandidateMessage[]>(`/client/applications/${applicationId}/messages`)
export const postClientMessage = (applicationId: string, body: string) =>
  api<CandidateMessage>(`/client/applications/${applicationId}/messages`, { method: 'POST', body: json({ body }) })
