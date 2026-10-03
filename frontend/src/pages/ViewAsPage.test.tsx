import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { ViewAsBanner } from '../components/ViewAsBanner'
import { fakeFetch } from '../test/fakeFetch'
import { ViewAsPage } from './ViewAsPage'

const options = {
  candidates: [{ email: 'nila@example.com', name: 'Nila Rao', openings: ['Blend – Interns'] }],
  clients: [{ email: 'hm@blend.example', name: 'Meera', clientName: 'Blend' }],
  roles: [
    { role: 'RECRUITER', label: 'Recruiter' },
    { role: 'HIRING_MANAGER', label: 'Hiring Manager' },
  ],
  minutes: 30,
}

describe('View as', () => {
  const assign = vi.fn()
  beforeEach(() => {
    Object.defineProperty(window, 'location', { value: { ...window.location, assign }, writable: true, configurable: true })
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    assign.mockReset()
  })

  it('starts viewing as a candidate, a client contact or a role', async () => {
    const fetchMock = fakeFetch([
      { path: '/admin/view-as/options', body: options },
      { path: '/admin/view-as', method: 'POST', body: { kind: 'CLIENT', label: 'Meera (Blend)', expiresAt: '2026-10-03T10:30:00Z' } },
    ])
    render(<ViewAsPage />)

    expect(await screen.findByText('Nila Rao')).toBeInTheDocument()
    expect(screen.getByText(/Blend – Interns/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hiring Manager' })).toBeInTheDocument()
    const buttons = screen.getAllByRole('button', { name: 'View as' })
    await userEvent.click(buttons[0]) // the client contact card comes first
    await vi.waitFor(() => expect(assign).toHaveBeenCalledWith('/'))
    const start = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(start[1]!.body as string)).toEqual({ kind: 'CLIENT', email: 'hm@blend.example' })
  })

  it('shows a read-only banner with Back to admin', async () => {
    const fetchMock = fakeFetch([
      { path: '/auth/session', body: { type: 'CANDIDATE', viewAs: { kind: 'CANDIDATE', label: 'Nila Rao', expiresAt: '2026-10-03T10:30:00Z' } } },
      { path: '/candidate/me', body: { email: 'nila@example.com', name: 'Nila Rao' } },
      { path: '/admin/view-as/stop', method: 'POST', status: 204 },
    ])
    render(
      <AuthProvider>
        <ViewAsBanner />
      </AuthProvider>,
    )

    expect(await screen.findByText(/Viewing as/)).toHaveTextContent('Viewing as Nila Rao (candidate) · read-only')
    await userEvent.click(screen.getByRole('button', { name: 'Back to admin' }))
    await vi.waitFor(() => expect(assign).toHaveBeenCalledWith('/admin/view-as'))
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/admin/view-as/stop'))).toBe(true)
  })
})
