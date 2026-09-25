import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'
import type { Me } from './api/types'
import { AuthProvider } from './auth/AuthContext'
import { fakeFetch } from './test/fakeFetch'

const interviewer: Me = {
  id: 'u1',
  email: 'interviewer@codewalnut.test',
  name: 'Dev Interviewer',
  roles: ['INTERVIEWER'],
  capabilities: ['VIEW_DASHBOARD', 'VIEW_INTERVIEWS'],
  navigation: [
    { key: 'dashboard', label: 'Dashboard', path: '/' },
    { key: 'interviews', label: 'Interviews', path: '/interviews' },
  ],
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('App', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the login page with dev users when signed out', async () => {
    fakeFetch([
      { path: '/me', status: 401, body: { error: 'Not signed in' } },
      {
        path: '/auth/config',
        body: { googleEnabled: false, devLoginEnabled: true, devUsers: [{ email: 'admin@codewalnut.test', label: 'Admin' }] },
      },
    ])

    renderAt('/')

    expect(await screen.findByRole('button', { name: 'Sign in as this user' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /admin@codewalnut.test/ })).toBeInTheDocument()
    expect(screen.queryByText('Sign in with Google')).not.toBeInTheDocument()
  })

  it('renders only the navigation the server returns (AUTH-07)', async () => {
    fakeFetch([{ path: '/me', body: interviewer }])

    renderAt('/')

    const nav = await screen.findByRole('navigation', { name: 'Main' })
    const links = Array.from(nav.querySelectorAll('a')).map((a) => a.textContent)
    expect(links).toEqual(['Dashboard', 'Interviews'])
    expect(screen.getByText('Interviewer')).toBeInTheDocument()
  })

  it('shows "Not available" for a page outside the role', async () => {
    fakeFetch([{ path: '/me', body: interviewer }])

    renderAt('/admin/users')

    expect(await screen.findByRole('heading', { name: 'Not available' })).toBeInTheDocument()
  })

  it('signs out and returns to the login page', async () => {
    const fetchMock = fakeFetch([
      { path: '/me', body: interviewer },
      { method: 'POST', path: '/auth/logout', status: 204 },
      { path: '/auth/config', body: { googleEnabled: true, devLoginEnabled: false, devUsers: [] } },
    ])

    renderAt('/')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('link', { name: 'Sign in with Google' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/google',
    )
    expect(fetchMock.mock.calls.some(([url, init]) => url === '/api/v1/auth/logout' && init?.method === 'POST')).toBe(true)
  })
})
