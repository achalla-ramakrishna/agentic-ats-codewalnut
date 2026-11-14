import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AssessmentDetail } from '../api/assessments'
import { fakeFetch } from '../test/fakeFetch'
import { TestsPage } from './TestsPage'

const summary = {
  id: 't1',
  title: 'Java basics',
  category: 'JAVA',
  description: null,
  durationMinutes: 30,
  passPercent: 60,
  status: 'DRAFT',
  questionCount: 0,
  totalPoints: 0,
  invites: 0,
  taken: 0,
  updatedAt: '2026-10-03T00:00:00Z',
} as const

const empty: AssessmentDetail = { summary, questions: [] }
const drafted: AssessmentDetail = {
  summary: { ...summary, questionCount: 1, totalPoints: 1 },
  questions: [
    {
      id: 'q1',
      position: 1,
      kind: 'SINGLE_CHOICE',
      prompt: 'Which collection keeps insertion order?',
      code: null,
      options: ['HashSet', 'LinkedHashSet'],
      correct: [1],
      acceptedAnswers: [],
      points: 1,
      explanation: null,
      aiDrafted: true,
      figure: null,
      optionFigures: null,
      section: null,
      topic: null,
      difficulty: null,
      coding: null,
    },
  ],
}

describe('TestsPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates a test, drafts questions with AI for review and marks it ready', async () => {
    const fetchMock = fakeFetch([
      { path: '/assessments/t1/draft', method: 'POST', body: { added: 1, notes: [], assessment: drafted } },
      { path: '/assessments/t1/publish', method: 'POST', body: { ...drafted, summary: { ...drafted.summary, status: 'READY' } } },
      { path: '/assessments/t1', body: empty },
      { path: '/assessments', method: 'POST', body: empty },
      { path: '/assessments', body: [] },
    ])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(
      <MemoryRouter>
        <TestsPage />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: 'New test' }))
    const form = screen.getByRole('form', { name: 'New test' })
    await userEvent.type(within(form).getByLabelText('Title'), 'Java basics')
    await userEvent.selectOptions(within(form).getByLabelText('Kind'), 'JAVA')
    await userEvent.click(within(form).getByRole('button', { name: 'Create' }))

    const drafting = await screen.findByRole('region', { name: 'Draft questions with AI' })
    await userEvent.click(within(drafting).getByRole('button', { name: 'Draft' }))
    expect(await screen.findByText('Which collection keeps insertion order?')).toBeInTheDocument()
    expect(screen.getByText('AI draft — check it')).toBeInTheDocument()
    expect(screen.getByText(/Check each one/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Mark ready' }))
    expect(await screen.findByText('Ready to send')).toBeInTheDocument()
    const create = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/assessments') && init?.method === 'POST')!
    expect(JSON.parse(create[1]!.body as string)).toMatchObject({ title: 'Java basics', category: 'JAVA', durationMinutes: 30, passPercent: 60 })
  })

  it('deletes a test nobody has taken, warning about open links', async () => {
    const sent = { ...empty, summary: { ...summary, status: 'READY', invites: 1, taken: 0 } }
    const fetchMock = fakeFetch([
      { path: '/assessments/t1', method: 'DELETE', body: { title: 'Java basics', openInvites: 1 } },
      { path: '/assessments/t1', body: sent },
      { path: '/assessments', body: [{ ...summary, status: 'READY', invites: 1 }] },
    ])
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(
      <MemoryRouter>
        <TestsPage />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: 'Java basics' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    expect(confirm.mock.calls[0][0]).toMatch(/1 candidate was sent this test but hasn't started/)
    expect(await screen.findByText('Deleted “Java basics”.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true)
  })

  it('hides Delete once someone has taken the test', async () => {
    fakeFetch([
      { path: '/assessments/t1', body: { ...empty, summary: { ...summary, status: 'READY', invites: 2, taken: 1 } } },
      { path: '/assessments', body: [{ ...summary, status: 'READY', invites: 2, taken: 1 }] },
    ])
    render(
      <MemoryRouter>
        <TestsPage />
      </MemoryRouter>,
    )
    await userEvent.click(await screen.findByRole('button', { name: 'Java basics' }))
    expect(await screen.findByText(/1 candidate has taken it/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })
  it('shows archived tests and deletes ticked ones for good with their answers and videos (ASMT-41)', async () => {
    const archived = (id: string, title: string) => ({ ...summary, id, title, category: 'INTERVIEW', status: 'ARCHIVED', invites: 2, taken: 2 })
    const fetchMock = fakeFetch([
      { path: '/assessments/a1', method: 'DELETE', body: { title: 'AI interview A', openInvites: 0, taken: 2, videos: 20, bytes: 60 * 1024 * 1024 } },
      { path: '/assessments/a2', method: 'DELETE', body: { title: 'AI interview B', openInvites: 0, taken: 1, videos: 10, bytes: 30 * 1024 * 1024 } },
      { path: '/assessments', body: [archived('a1', 'AI interview A'), archived('a2', 'AI interview B'), { ...summary, status: 'READY' }] },
    ])
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(
      <MemoryRouter>
        <TestsPage />
      </MemoryRouter>,
    )
    await screen.findByRole('button', { name: 'Java basics' })
    expect(screen.queryByRole('button', { name: 'AI interview A' })).not.toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'ARCHIVED')
    expect(screen.queryByRole('button', { name: 'Java basics' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Tick all shown'))
    await userEvent.click(screen.getByRole('button', { name: 'Clean up 2 ticked' }))
    expect(confirm.mock.calls[0][0]).toMatch(/Delete 2 archived for good — with every candidate’s answers, scores, AI interview videos/)
    expect(await screen.findByText(/Tidied up: 2 archived deleted for good \(30 videos, 90 MB freed\)\./)).toBeInTheDocument()
    const deletes = fetchMock.mock.calls.filter(([, init]) => init?.method === 'DELETE').map(([url]) => String(url))
    expect(deletes).toEqual(['/api/v1/assessments/a1?forGood=true', '/api/v1/assessments/a2?forGood=true'])
  })
})
