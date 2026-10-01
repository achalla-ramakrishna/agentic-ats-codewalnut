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
  createdAt: string
}

export interface CandidateMessage {
  fromMe: boolean
  authorName: string
  subject: string | null
  body: string
  createdAt: string
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
  input: { channel: MessageChannel; body: string; subject?: string; sendEmail: boolean },
) => api<Message>(`/applications/${applicationId}/messages`, { method: 'POST', body: json(input) })
export const getInbox = () => api<InboxItem[]>('/messages/inbox')

export const listMyMessages = (applicationId: string) =>
  api<CandidateMessage[]>(`/candidate/applications/${applicationId}/messages`)
export const postMyMessage = (applicationId: string, body: string) =>
  api<CandidateMessage>(`/candidate/applications/${applicationId}/messages`, { method: 'POST', body: json({ body }) })
