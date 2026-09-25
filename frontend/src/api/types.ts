export type Role = 'ADMIN' | 'RECRUITER' | 'HIRING_MANAGER' | 'ACCOUNT_MANAGER' | 'INTERVIEWER' | 'APPROVER'

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Admin',
  RECRUITER: 'Recruiter',
  HIRING_MANAGER: 'Hiring Manager',
  ACCOUNT_MANAGER: 'Account Manager',
  INTERVIEWER: 'Interviewer',
  APPROVER: 'Approver',
}

export const ALL_ROLES = Object.keys(ROLE_LABELS) as Role[]

export interface NavItem {
  key: string
  label: string
  path: string
}

export interface Me {
  id: string
  email: string
  name: string | null
  roles: Role[]
  capabilities: string[]
  navigation: NavItem[]
}

export interface AuthConfig {
  googleEnabled: boolean
  devLoginEnabled: boolean
  devUsers: { email: string; label: string }[]
}

export interface User {
  id: string
  email: string
  name: string | null
  roles: Role[]
  active: boolean
  lastLoginAt: string | null
  createdAt: string
}

export interface AuditEntry {
  id: string
  createdAt: string
  actorEmail: string | null
  action: string
  entityType: string | null
  entityId: string | null
  details: string | null
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type SessionType = 'STAFF' | 'CANDIDATE'

export interface Session {
  type: SessionType | null
}

export interface CandidateMe {
  id: string
  email: string
  name: string | null
}
