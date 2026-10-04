import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/types'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'

const admin: Me = {
  id: 'a1',
  email: 'admin@codewalnut.test',
  name: 'Dev Admin',
  roles: ['ADMIN'],
  capabilities: ['VIEW_ADMIN_UPDATES'],
  navigation: [{ key: 'admin-updates', label: 'Admin updates', path: '/admin/updates' }],
}

const base = { applicationId: 'app1', jobId: 'j1', actorEmail: 'priya@codewalnut.test', emailedTo: 'admin@codewalnut.test', createdAt: '2026-10-04T08:00:00Z' }

describe('AdminUpdatesPage (ADM-16)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists feedback and stage updates with links and email status', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: admin },
      {
        path: '/admin-updates',
        body: {
          keyStages: ['Shortlisted', 'Selected', 'Offer accepted', 'Joined'],
          updates: [
            { ...base, id: 'u1', kind: 'FEEDBACK_SUBMITTED', interviewId: 'i1', title: 'Asha Rao: Hire from Priya (Java Intern)', body: 'Candidate: Asha Rao\nRecommendation: Hire', emailStatus: 'SENT' },
            { ...base, id: 'u2', kind: 'STAGE_REACHED', interviewId: null, title: 'Ravi Kumar: Shortlisted (Java Intern)', body: 'Candidate: Ravi Kumar', emailStatus: 'SKIPPED' },
          ],
        },
      },
    ])
    render(
      <MemoryRouter initialEntries={['/admin/updates']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByText('Asha Rao: Hire from Priya (Java Intern)')).toBeInTheDocument()
    expect(screen.getByText(/Shortlisted, Selected, Offer accepted, Joined/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open feedback' })).toHaveAttribute('href', '/interviews/i1/feedback')
    expect(screen.getByText(/sender hadn’t connected Gmail/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Stage changes' }))
    expect(screen.queryByText('Asha Rao: Hire from Priya (Java Intern)')).not.toBeInTheDocument()
    expect(screen.getByText('Ravi Kumar: Shortlisted (Java Intern)')).toBeInTheDocument()
  })
})
