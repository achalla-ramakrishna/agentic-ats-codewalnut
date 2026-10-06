import { api } from './client'

/** Ask ATS (ASK-01…): a staff member's chats with the ATS assistant. */
export interface AskStatus {
  available: boolean
  suggestions: string[]
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  at: string
}

export interface ConversationSummary {
  id: string
  title: string
  updatedAt: string
}

export interface Conversation extends ConversationSummary {
  messages: ChatMessage[]
}

export const getAskStatus = () => api<AskStatus>('/ask/status')
export const listConversations = () => api<ConversationSummary[]>('/ask/conversations')
export const getConversation = (id: string) => api<Conversation>(`/ask/conversations/${id}`)
export const deleteConversation = (id: string) => api<void>(`/ask/conversations/${id}`, { method: 'DELETE' })
export const askAts = (question: string, conversationId: string | null) =>
  api<Conversation>('/ask', { method: 'POST', body: JSON.stringify({ question, conversationId }) })
