import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/types'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'

const interviewer: Me = {
  id: 'u1',
  email: 'interviewer@codewalnut.test',
  name: 'Dev Interviewer',
  roles: ['INTERVIEWER'],
  capabilities: ['VIEW_INTERVIEWS'],
  navigation: [{ key: 'interviews', label: 'Interviews', path: '/interviews' }],
}

const interview = (id: string, name: string) => ({
  id,
  applicationId: 'a1',
  jobId: 'j1',
  jobTitle: 'Java Intern',
  candidateName: name,
  title: 'CodeWalnut interview',
  startAt: '2026-10-04T05:30:00Z',
  endAt: '2026-10-04T06:15:00Z',
  timeZone: 'Asia/Kolkata',
  interviewers: ['interviewer@codewalnut.test'],
  message: null,
  status: 'SCHEDULED',
  meetLink: 'https://meet.google.com/abc-defg-hij',
  calendarLink: null,
  organizerEmail: 'recruiter@codewalnut.test',
  cancelReason: null,
  createdAt: '2026-10-01T00:00:00Z',
})

describe('InterviewsPage (INT-34)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists recent interviews with a Give feedback button', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: interviewer },
      { path: '/interviews/feedback-due', body: [] },
      { path: '/interviews/recent', body: [
        { interview: interview('i1', 'Asha Rao'), submitted: 0, panelSize: 2, onPanel: true, mineSubmitted: false, canSubmit: true },
        { interview: interview('i2', 'Ravi Kumar'), submitted: 2, panelSize: 2, onPanel: true, mineSubmitted: true, canSubmit: true },
      ] },
      { path: '/interviews', body: [] },
    ])
    render(
      <MemoryRouter initialEntries={['/interviews']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    )
    const table = await screen.findByRole('table', { name: 'Recent interviews' })
    const asha = within(table).getByText('Asha Rao').closest('tr')!
    expect(within(asha).getByRole('link', { name: 'Give feedback' })).toHaveAttribute('href', '/interviews/i1/feedback')
    expect(within(asha).getByText('Yours is due')).toBeInTheDocument()
    const ravi = within(table).getByText('Ravi Kumar').closest('tr')!
    expect(within(ravi).getByRole('link', { name: 'View feedback' })).toBeInTheDocument()
  })
})
