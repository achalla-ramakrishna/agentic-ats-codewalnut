import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { NewResults } from './NewResults'

const result = {
  candidateName: 'Asha Test',
  jobId: 'j1',
  jobTitle: 'Java developer',
  invite: {
    id: 'i1', applicationId: 'a1', assessmentId: 't1', title: 'Java freshers', category: 'JAVA', status: 'SUBMITTED',
    sentBy: 'r@x', sentAt: '2026-10-01T00:00:00Z', dueAt: '2026-10-04T00:00:00Z', startedAt: null, submittedAt: new Date().toISOString(),
    score: 12, maxScore: 15, percent: 80, passed: true, passPercent: 60, reminderCount: 0, lastRemindedAt: null, needsNudge: false,
    newResult: true,
  },
}

describe('New test results', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists results nobody has seen, links to the candidate and marks them seen', async () => {
    const fetchMock = fakeFetch([
      { path: '/tests/new-results', body: [result] },
      { path: '/tests/seen', method: 'POST', body: { marked: 1 } },
    ])
    render(
      <MemoryRouter>
        <NewResults />
      </MemoryRouter>,
    )
    expect(await screen.findByText('Asha Test')).toBeInTheDocument()
    expect(screen.getByText('80%')).toBeInTheDocument()
    expect(screen.getByText('Passed')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open' })).toHaveAttribute('href', '/jobs/j1?candidate=a1&tab=tests')
    await userEvent.click(screen.getByRole('button', { name: 'Mark seen' }))
    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/tests/seen') && init?.method === 'POST')).toBe(true),
    )
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/tests/seen'))!
    expect(JSON.parse(call[1]!.body as string)).toEqual({ inviteIds: ['i1'] })
  })
})
