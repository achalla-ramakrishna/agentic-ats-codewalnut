import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { AiInterviewPanel } from './AiInterviewPanel'

const row = (name: string, score: number | null, status: string, extra: object = {}) => ({
  inviteId: `i-${name}`,
  applicationId: `a-${name}`,
  candidateName: name,
  stage: 'SCREENING',
  status,
  evaluation: status === 'SUBMITTED' ? 'DONE' : null,
  usesAi: true,
  aiScore: score,
  markedPercent: 80,
  areas: score ? { TECHNICAL: 4, ARCHITECTURE: 3, COMMUNICATION: 5, EXPERIENCE: 4, READINESS: 2 } : {},
  summary: score ? `${name} explained the payments design clearly.` : null,
  noticePeriod: score ? '30 days' : null,
  expectedSalary: score ? '25 LPA' : null,
  sentAt: '2026-11-01T00:00:00Z',
  submittedAt: null,
  videos: score ? 12 : 0,
  ...extra,
})

describe('AiInterviewPanel (AINT-07)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('ranks reviewed candidates and shortlists the ones picked', async () => {
    const fetchMock = fakeFetch([
      {
        path: '/jobs/j1/ai-interviews',
        body: {
          candidates: [row('Asha Rao', 86, 'SUBMITTED'), row('Ravi Kumar', 61, 'SUBMITTED', { usesAi: false }), row('Meena Iyer', null, 'SENT')],
          sent: 3,
          started: 0,
          submitted: 2,
          reviewed: 2,
        },
      },
      { path: '/stages', body: [{ key: 'SCREENING', label: 'Screening' }, { key: 'SHORTLISTED', label: 'Shortlisted' }] },
      { path: '/applications/', method: 'PATCH', body: {} },
    ])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const onOpen = vi.fn()
    render(
      <MemoryRouter>
        <AiInterviewPanel jobId="j1" canEdit onOpen={onOpen} onChanged={() => undefined} onClose={() => undefined} />
      </MemoryRouter>,
    )
    const table = await screen.findByRole('table', { name: 'AI interview ranking' })
    expect(table).toHaveTextContent('Asha Rao')
    expect(table).toHaveTextContent('86')
    expect(table).toHaveTextContent('30 days · 25 LPA')
    expect(table).toHaveTextContent('basic') // Ravi's review was by rules
    // Only submitted by default; untick to see who hasn't taken it.
    expect(table).not.toHaveTextContent('Meena Iyer')
    await userEvent.click(screen.getByLabelText('Only submitted'))
    expect(screen.getByRole('table', { name: 'AI interview ranking' })).toHaveTextContent('Meena Iyer')

    await userEvent.click(screen.getByRole('button', { name: 'Asha Rao' }))
    expect(onOpen).toHaveBeenCalledWith('a-Asha Rao')

    await userEvent.click(screen.getByLabelText('Select Asha Rao'))
    await userEvent.click(screen.getByRole('button', { name: 'Shortlist selected (1)' }))
    expect(await screen.findByText(/1 moved to Shortlisted/)).toBeInTheDocument()
    const moved = fetchMock.mock.calls.find(([, init]) => init?.method === 'PATCH')!
    expect(String(moved[0])).toContain('/stage')
    expect(JSON.parse(moved[1]!.body as string).stage).toBe('SHORTLISTED')
  })
})
