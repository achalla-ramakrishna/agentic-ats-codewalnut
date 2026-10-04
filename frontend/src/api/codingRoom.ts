import { api } from './client'
import type { CodeLanguage, RunCodeResult, TestCase } from './assessments'

/** Live coding rooms (INT-36…). */
export interface ProblemOption {
  id: string
  title: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  topic: string
}

export interface RoomProblem {
  id: string | null
  title: string
  statement: string
  inputFormat: string | null
  outputFormat: string | null
  samples: TestCase[]
  languages: CodeLanguage[]
  starter: Partial<Record<CodeLanguage, string>>
}

export interface PastProblem {
  title: string
  language: string
  code: string | null
  passed: number | null
  total: number | null
}

export interface RoomView {
  id: string
  interviewId: string
  candidateName: string
  linkPath: string
  status: 'OPEN' | 'ENDED'
  problem: RoomProblem
  language: CodeLanguage
  code: string | null
  codeUpdatedAt: string | null
  candidateSeenAt: string | null
  lastRun: RunCodeResult | null
  runCount: number
  history: PastProblem[]
  notes: string | null
  canManage: boolean
  endedAt: string | null
}

export interface RoomPage {
  room: RoomView | null
  canManage: boolean
  runnerAvailable: boolean
  candidateHasEmail: boolean
}

export interface ProblemInput {
  problemId?: string
  title?: string
  statement?: string
  sampleInput?: string
  sampleOutput?: string
}

export interface CandidateRoom {
  status: 'OPEN' | 'ENDED'
  jobTitle: string
  problem: RoomProblem
  language: CodeLanguage
  code: string | null
  lastRun: RunCodeResult | null
  runsLeft: number
  runnerAvailable: boolean
}

const json = (body: unknown) => JSON.stringify(body)

export const listCodingProblems = () => api<ProblemOption[]>('/coding-problems')
export const getCodingRoom = (interviewId: string) => api<RoomPage>(`/interviews/${interviewId}/coding-room`)
export const startCodingRoom = (interviewId: string, input: ProblemInput) =>
  api<RoomPage>(`/interviews/${interviewId}/coding-room`, { method: 'POST', body: json(input) })
export const endCodingRoom = (interviewId: string) => api<RoomPage>(`/interviews/${interviewId}/coding-room/end`, { method: 'POST' })
export const saveRoomNotes = (interviewId: string, notes: string) =>
  api<RoomPage>(`/interviews/${interviewId}/coding-room/notes`, { method: 'PUT', body: json({ notes }) })
export const runRoomCode = (interviewId: string) => api<RunCodeResult>(`/interviews/${interviewId}/coding-room/run`, { method: 'POST' })

export const getMyCodingRoom = (token: string) => api<CandidateRoom>(`/candidate/coding/${token}`)
export const saveMyCode = (token: string, language: CodeLanguage, code: string) =>
  api<CandidateRoom>(`/candidate/coding/${token}`, { method: 'PUT', body: json({ language, code }) })
export const runMyCode = (token: string, language: CodeLanguage, source: string) =>
  api<CandidateRoom>(`/candidate/coding/${token}/run`, { method: 'POST', body: json({ language, source }) })

/** The full link to send the candidate, e.g. in the Meet chat. */
export const roomLink = (path: string) => `${window.location.origin}${path}`
