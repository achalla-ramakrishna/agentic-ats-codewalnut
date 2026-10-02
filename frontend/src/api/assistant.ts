import { api } from './client'
import type { Stage } from './tracker'

export interface ProposedAction {
  type: 'MOVE_STAGE' | 'ADD_NOTE'
  applicationId: string
  candidateName: string
  fromStage: Stage
  fromLabel: string
  toStage: Stage | null
  toLabel: string | null
  note: string | null
  needsReason: boolean
}

export interface AssistantPlan {
  instruction: string
  summary: string
  actions: ProposedAction[]
  unresolved: {
    mention: string
    options: { applicationId: string; name: string; stageLabel: string }[]
    toStage: Stage | null
    toLabel: string | null
    note: string | null
  }[]
  notes: string[]
  aiGenerated: boolean
}

export const getAssistantStatus = () => api<{ available: boolean }>('/assistant/status')
export const askAssistant = (jobId: string, instruction: string) =>
  api<AssistantPlan>(`/jobs/${jobId}/assistant`, { method: 'POST', body: JSON.stringify({ instruction }) })
