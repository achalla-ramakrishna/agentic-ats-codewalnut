import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Me, User } from '../api/types'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'

const admin: Me = {
  id: 'admin-id',
  email: 'admin@codewalnut.test',
  name: 'Dev Admin',
  roles: ['ADMIN'],
  capabilities: ['MANAGE_USERS'],
  navigation: [{ key: 'users', label: 'Users', path: '/admin/users' }],
}

const users: User[] = [
  { id: 'admin-id', email: 'admin@codewalnut.test', name: 'Dev Admin', roles: ['ADMIN'], active: true, lastLoginAt: null, createdAt: '2026-09-25T00:00:00Z' },
  { id: 'r1', email: 'recruiter@codewalnut.test', name: 'Dev Recruiter', roles: ['RECRUITER'], active: true, lastLoginAt: null, createdAt: '2026-09-25T00:00:00Z' },
]

function renderUsers() {
  return render(
    <MemoryRouter initialEntries={['/admin/users']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('UsersPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('does not offer to deactivate yourself (AUTH-09)', async () => {
    fakeFetch([
      { path: '/me', body: admin },
      { path: '/users', body: users },
    ])

    renderUsers()

    const selfRow = (await screen.findByText('admin@codewalnut.test', { selector: 'div' })).closest('tr')!
    const otherRow = screen.getByText('recruiter@codewalnut.test', { selector: 'div' }).closest('tr')!
    expect(within(selfRow).queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
    expect(within(otherRow).getByRole('button', { name: 'Deactivate' })).toBeInTheDocument()
  })

  it('creates a user with the chosen roles (AUTH-08)', async () => {
    const fetchMock = fakeFetch([
      { path: '/me', body: admin },
      { path: '/users', body: users },
      { method: 'POST', path: '/users', status: 201, body: users[1] },
    ])
    renderUsers()

    const form = await screen.findByRole('form', { name: 'Add user' })
    await userEvent.type(within(form).getByLabelText('Email'), 'new.person@codewalnut.com')
    await userEvent.click(within(form).getByLabelText('Recruiter'))
    await userEvent.click(within(form).getByLabelText('Interviewer'))
    await userEvent.click(within(form).getByRole('button', { name: 'Add user' }))

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({
      email: 'new.person@codewalnut.com',
      roles: ['RECRUITER', 'INTERVIEWER'],
    })
  })

  it('shows the server error when creation is refused', async () => {
    fakeFetch([
      { path: '/me', body: admin },
      { path: '/users', body: users },
      { method: 'POST', path: '/users', status: 409, body: { error: 'A user with this email already exists' } },
    ])
    renderUsers()

    const form = await screen.findByRole('form', { name: 'Add user' })
    await userEvent.type(within(form).getByLabelText('Email'), 'recruiter@codewalnut.test')
    await userEvent.click(within(form).getByLabelText('Recruiter'))
    await userEvent.click(within(form).getByRole('button', { name: 'Add user' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('A user with this email already exists')
  })
})
