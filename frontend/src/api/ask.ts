import { api } from './client'

/** Ask ATS (ASK-01…): a staff member's chats with the ATS assistant. */
export interface AskStatus {
  available: boolean
  suggestions: string[]
}

export type ActionType = 'MOVE_STAGE' | 'ADD_NOTE' | 'LOG_CONTACT' | 'REMIND_TEST' | 'MESSAGE' | 'SHARE_WITH_CLIENT'

/** An action Ask ATS proposed (ASK-06…): nothing happens until the person clicks Do it. */
export interface ProposedAction {
  id: string
  type: ActionType
  applicationId: string
  jobId: string
  candidateName: string
  jobTitle: string
  summary: string
  params: Record<string, string>
  status: 'PENDING' | 'DONE' | 'SKIPPED' | 'FAILED'
  result: string | null
  doneBy: string | null
  doneAt: string | null
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  at: string
  actions?: ProposedAction[] | null
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
export const decideAction = (
  conversationId: string,
  actionId: string,
  input: { decision: 'do' | 'skip'; subject?: string; body?: string },
) =>
  api<{ conversation: Conversation; whatsappLink: string | null }>(`/ask/conversations/${conversationId}/actions/${actionId}`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
