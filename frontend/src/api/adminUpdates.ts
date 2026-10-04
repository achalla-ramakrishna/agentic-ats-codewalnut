import { api } from './client'

export type AdminUpdateKind = 'FEEDBACK_SUBMITTED' | 'STAGE_REACHED'
export type AdminEmailStatus = 'PENDING' | 'SENT' | 'SKIPPED' | 'FAILED' | 'NONE'

export interface AdminUpdate {
  id: string
  kind: AdminUpdateKind
  applicationId: string
  jobId: string | null
  interviewId: string | null
  title: string
  body: string
  actorEmail: string
  emailStatus: AdminEmailStatus
  emailedTo: string | null
  createdAt: string
}

export interface AdminUpdatesPage {
  updates: AdminUpdate[]
  keyStages: string[]
}

/** Admins only (ADM-14…). */
export const listAdminUpdates = () => api<AdminUpdatesPage>('/admin-updates')
