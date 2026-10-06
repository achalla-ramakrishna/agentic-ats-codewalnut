import { api } from './client'

/** The workflow view (WF-01…): who was contacted, what happened, what to do next. */
export interface Contact {
  at: string
  how: string
  by: string | null
}

export interface NextStep {
  code: string
  label: string
  urgent: boolean
}

export interface WorkflowRow {
  applicationId: string
  jobId: string
  jobTitle: string
  clientName: string | null
  candidateId: string
  candidateName: string
  stage: string
  stageLabel: string
  closed: boolean
  inStageSince: string
  addedAt: string
  lastContact: Contact | null
  contacts: number
  awaitingReply: boolean
  candidateWroteAt: string | null
  test: { title: string; status: string; percent: number | null; passed: boolean | null; at: string } | null
  interview: { interviewId: string; status: 'UPCOMING' | 'DONE' | 'CANCELLED'; startAt: string; feedbackGiven: number; panel: number } | null
  client: { client: string; sharedAt: string; viewedAt: string | null } | null
  documentsPending: number
  nextStep: NextStep | null
  lastActivityAt: string
}

export type WorkflowFilter = 'active' | 'urgent' | 'reply' | 'never' | 'quiet' | 'closed'

export interface WorkflowBoard {
  rows: WorkflowRow[]
  counts: Record<WorkflowFilter, number>
  openings: { id: string; title: string; clientName: string | null; status: string }[]
  canLog: boolean
}

export interface TimelineItem {
  at: string
  kind: 'CONTACT' | 'MESSAGE_IN' | 'STAGE' | 'TEST' | 'INTERVIEW' | 'CLIENT' | 'DOCUMENT' | 'NOTE' | 'TEAM' | 'ADDED'
  title: string
  detail: string | null
  by: string | null
}

export interface Timeline {
  applicationId: string
  candidateName: string
  jobTitle: string
  items: TimelineItem[]
}

export type ContactHow = 'CALL' | 'WHATSAPP' | 'EMAIL' | 'MEETING' | 'OTHER'

export const HOW_LABEL: Record<ContactHow, string> = {
  CALL: 'Call',
  WHATSAPP: 'WhatsApp (outside the app)',
  EMAIL: 'Email (outside the app)',
  MEETING: 'Met in person',
  OTHER: 'Other',
}

export const getWorkflow = (jobId?: string) => api<WorkflowBoard>(`/workflow${jobId ? `?jobId=${jobId}` : ''}`)
export const getTimeline = (applicationId: string) => api<Timeline>(`/applications/${applicationId}/timeline`)
export const logContact = (applicationId: string, how: ContactHow, note: string) =>
  api<Timeline>(`/applications/${applicationId}/contacts`, { method: 'POST', body: JSON.stringify({ how, note }) })
