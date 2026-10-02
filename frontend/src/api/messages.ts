import { api } from './client'

export type MessageChannel = 'CANDIDATE' | 'TEAM' | 'CLIENT'

export interface Message {
  id: string
  channel: MessageChannel
  authorType: 'STAFF' | 'CANDIDATE'
  authorEmail: string
  authorName: string | null
  subject: string | null
  body: string
  emailed: boolean
  /** OPENED, SENT, DELIVERED, READ, FAILED, RECEIVED; null when WhatsApp wasn't used. */
  whatsapp: string | null
  createdAt: string
  /** Only when sending without the WhatsApp Business API: opens WhatsApp with the text ready. */
  whatsappLink?: string | null
  warnings?: string[]
}

export interface CandidateMessage {
  fromMe: boolean
  authorName: string
  subject: string | null
  body: string
  createdAt: string
  viaWhatsApp?: boolean
}

export interface InboxItem {
  applicationId: string
  jobId: string
  jobTitle: string
  candidateName: string
  channel: MessageChannel
  clientName: string | null
  lastAuthorName: string
  lastFromExternal: boolean
  preview: string
  lastAt: string
  awaitingReply: boolean
}

const json = (body: unknown) => JSON.stringify(body)

export const listMessages = (applicationId: string, channel: MessageChannel) =>
  api<Message[]>(`/applications/${applicationId}/messages?channel=${channel}`)
export const postMessage = (
  applicationId: string,
  input: { channel: MessageChannel; body: string; subject?: string; sendEmail: boolean; sendWhatsApp?: boolean },
) => api<Message>(`/applications/${applicationId}/messages`, { method: 'POST', body: json(input) })
export const getInbox = () => api<InboxItem[]>('/messages/inbox')
export const getWhatsAppStatus = () => api<{ apiEnabled: boolean; repliesEnabled: boolean }>('/whatsapp/status')

const WHATSAPP_LABELS: Record<string, string> = {
  OPENED: 'WhatsApp opened',
  SENT: 'WhatsApp sent',
  DELIVERED: 'WhatsApp delivered',
  READ: 'WhatsApp read',
  FAILED: 'WhatsApp failed',
  RECEIVED: 'via WhatsApp',
}
export const whatsappLabel = (status: string | null | undefined) => (status ? (WHATSAPP_LABELS[status] ?? null) : null)

export const listMyMessages = (applicationId: string) =>
  api<CandidateMessage[]>(`/candidate/applications/${applicationId}/messages`)
export const postMyMessage = (applicationId: string, body: string) =>
  api<CandidateMessage>(`/candidate/applications/${applicationId}/messages`, { method: 'POST', body: json({ body }) })
