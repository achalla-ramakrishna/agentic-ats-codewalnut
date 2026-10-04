import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
  capabilities: ['VIEW_DASHBOARD', 'VIEW_INTERVIEWS'],
  navigation: [{ key: 'interviews', label: 'Interviews', path: '/interviews' }],
}

const interview = {
  id: 'i1',
  applicationId: 'a1',
  jobId: 'j1',
  jobTitle: 'Java Intern',
  candidateId: 'p1',
  candidateName: 'Asha Rao',
  title: 'CodeWalnut interview',
  startAt: '2026-01-15T05:30:00Z',
  endAt: '2026-01-15T06:15:00Z',
  timeZone: 'Asia/Kolkata',
  interviewers: ['interviewer@codewalnut.test'],
  message: null,
  status: 'SCHEDULED',
  meetLink: 'https://meet.google.com/abc-defg-hij',
  calendarLink: null,
  organizerEmail: 'recruiter@codewalnut.test',
  cancelReason: null,
  createdAt: '2026-01-01T00:00:00Z',
}

const competencies = [
  { name: 'Problem solving', guidance: 'Breaks the problem down.' },
  { name: 'Communication', guidance: 'Explains clearly.' },
]

const theirs = {
  authorEmail: 'recruiter@codewalnut.test',
  authorName: 'Dev Recruiter',
  attendance: 'HELD',
  ratings: [{ competency: 'Communication', rating: 4, note: null }],
  averageRating: 4,
  strengths: 'Clear explanations',
  concerns: null,
  questionsAsked: null,
  recommendation: 'STRONG_YES',
  notes: null,
  submittedAt: '2026-01-15T07:00:00Z',
  updatedAt: '2026-01-15T07:00:00Z',
}

const before = { interview, onPanel: true, canSubmit: true, mine: null, others: [], hiddenCount: 1, competencies }

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/interviews/i1/feedback']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('InterviewFeedbackPage (INT-24…INT-26)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('hides the panel until you submit, then sends ratings and a recommendation', async () => {
    const mine = { ...theirs, authorEmail: interviewer.email, authorName: interviewer.name, recommendation: 'YES', averageRating: 3, strengths: 'Good with hash maps' }
    const fetch = fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: interviewer },
      { path: '/interviews/i1/feedback', body: before },
      { method: 'PUT', path: '/interviews/i1/feedback', body: { ...before, mine, others: [theirs], hiddenCount: 0 } },
    ])
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Feedback: Asha Rao' })).toBeInTheDocument()
    expect(screen.getByText(/1 other person has given feedback/)).toBeInTheDocument()
    expect(screen.queryByText('Clear explanations')).not.toBeInTheDocument()

    // A recommendation is required when the interview happened.
    await userEvent.click(screen.getByRole('button', { name: 'Problem solving: 3 Meets the bar' }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit feedback' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Choose an overall recommendation')

    await userEvent.type(screen.getByLabelText('Problem solving note'), 'Found the O(n) approach')
    await userEvent.type(screen.getByLabelText('Strengths'), 'Good with hash maps')
    await userEvent.click(screen.getByRole('button', { name: 'Hire' }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit feedback' }))

    expect(await screen.findByText('Thanks, your feedback is saved.')).toBeInTheDocument()
    expect(screen.getByText('Clear explanations')).toBeInTheDocument()
    const put = fetch.mock.calls.find(([, init]) => init?.method === 'PUT')
    const body = JSON.parse(String(put?.[1]?.body)) as Record<string, unknown>
    expect(body).toMatchObject({
      attendance: 'HELD',
      recommendation: 'YES',
      strengths: 'Good with hash maps',
      ratings: [
        { competency: 'Problem solving', rating: 3, note: 'Found the O(n) approach' },
        { competency: 'Communication', rating: null, note: null },
      ],
    })
  }, 15000)

  it('a no-show needs only a note', async () => {
    const fetch = fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: interviewer },
      { path: '/interviews/i1/feedback', body: before },
      { method: 'PUT', path: '/interviews/i1/feedback', body: before },
    ])
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: "The candidate didn't join" }))
    expect(screen.queryByRole('group', { name: 'Problem solving' })).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('What happened'), 'Waited 15 minutes')
    const form = screen.getByRole('form', { name: 'Interview feedback' })
    await userEvent.click(within(form).getByRole('button', { name: 'Submit feedback' }))

    await screen.findByText('Thanks, your feedback is saved.')
    const put = fetch.mock.calls.find(([, init]) => init?.method === 'PUT')
    expect(JSON.parse(String(put?.[1]?.body))).toMatchObject({
      attendance: 'CANDIDATE_NO_SHOW',
      ratings: [],
      recommendation: null,
      notes: 'Waited 15 minutes',
    })
  })
})
