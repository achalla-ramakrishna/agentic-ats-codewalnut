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

  it('creates the chosen question set, or all four (AINT-11)', async () => {
    const fetchMock = fakeFetch([
      { path: '/jobs/j1/ai-interviews', body: { candidates: [], sent: 0, started: 0, submitted: 0, reviewed: 0 } },
      { path: '/stages', body: [] },
      { path: '/ai-interviews', method: 'POST', body: { interview: { summary: { id: 't9' }, questions: [] }, notes: [] } },
    ])
    vi.spyOn(window, 'alert').mockImplementation(() => undefined)
    render(
      <MemoryRouter>
        <AiInterviewPanel jobId="j1" canEdit onOpen={() => undefined} onChanged={() => undefined} onClose={() => undefined} />
      </MemoryRouter>,
    )
    await userEvent.selectOptions(await screen.findByLabelText('Question set'), 'all')
    await userEvent.click(screen.getByRole('button', { name: /Create all four sets/ }))
    const posts = await vi.waitFor(() => {
      const calls = fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/ai-interviews') && init?.method === 'POST')
      expect(calls).toHaveLength(4)
      return calls
    })
    expect(posts.map(([, init]) => JSON.parse(init!.body as string).questionSet)).toEqual([1, 2, 3, 4])
  })

  it('previews a set’s questions with answers and what to look for before creating it', async () => {
    fakeFetch([
      { path: '/jobs/j1/ai-interviews', body: { candidates: [], sent: 0, started: 0, submitted: 0, reviewed: 0 } },
      { path: '/stages', body: [] },
      {
        path: '/ai-interviews/sets/2',
        body: {
          set: 2, letter: 'B', minutes: 120,
          questions: [
            { phase: 'INTRO', phaseLabel: 'Introduction', kind: 'VIDEO', prompt: 'In one minute: your current role.', code: null, options: [],
              correctOption: null, acceptedAnswers: [], maxSeconds: 60, lookFor: 'Clear scope.' },
            { phase: 'TECHNICAL', phaseLabel: 'Technical round', kind: 'MULTIPLE_CHOICE', prompt: 'What does this print?',
              code: 'System.out.println(1);', options: ['0', '1'], correctOption: 1, acceptedAnswers: [], maxSeconds: null, lookFor: null },
          ],
        },
      },
    ])
    render(
      <MemoryRouter>
        <AiInterviewPanel jobId="j1" canEdit onOpen={() => undefined} onChanged={() => undefined} onClose={() => undefined} />
      </MemoryRouter>,
    )
    await userEvent.selectOptions(await screen.findByLabelText('Question set'), '2')
    await userEvent.click(screen.getByRole('button', { name: 'Preview questions' }))
    const preview = await screen.findByRole('region', { name: 'Set B questions' })
    expect(preview).toHaveTextContent('2 questions · 1 spoken')
    expect(preview).toHaveTextContent('Look for: Clear scope.')
    expect(preview).toHaveTextContent('1 ✓')
    await userEvent.click(screen.getByLabelText('Show answers and what to look for'))
    expect(preview).not.toHaveTextContent('Look for')
  })
})
