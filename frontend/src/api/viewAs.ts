import { api } from './client'
import type { ViewAsInfo } from './types'

/** "View as" for admins (ADR-0013): read-only, time-limited, audited. */

export interface ViewAsOptions {
  candidates: { email: string; name: string; openings: string[] }[]
  clients: { email: string; name: string | null; clientName: string }[]
  roles: { role: string; label: string }[]
  minutes: number
}

export const getViewAsOptions = (q?: string) =>
  api<ViewAsOptions>(`/admin/view-as/options${q ? `?q=${encodeURIComponent(q)}` : ''}`)
export const startViewAs = (input: { kind: 'CANDIDATE' | 'CLIENT' | 'ROLE'; email?: string; role?: string }) =>
  api<ViewAsInfo>('/admin/view-as', { method: 'POST', body: JSON.stringify(input) })
export const stopViewAs = () => api<void>('/admin/view-as/stop', { method: 'POST' })
