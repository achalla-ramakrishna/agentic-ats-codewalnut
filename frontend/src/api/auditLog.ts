import { api } from './client'
import type { AuditEntry, Page } from './types'

export const listAuditLog = (page: number, size = 50) =>
  api<Page<AuditEntry>>(`/audit-log?page=${page}&size=${size}`)
