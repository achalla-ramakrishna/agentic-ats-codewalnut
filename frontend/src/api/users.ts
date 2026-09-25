import { api } from './client'
import type { Role, User } from './types'

export const listUsers = () => api<User[]>('/users')

export const createUser = (input: { email: string; name?: string; roles: Role[] }) =>
  api<User>('/users', { method: 'POST', body: JSON.stringify(input) })

export const updateUser = (id: string, patch: { name?: string; roles?: Role[]; active?: boolean }) =>
  api<User>(`/users/${id}`, { method: 'PATCH', body: JSON.stringify(patch) })
