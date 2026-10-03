import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
    render(<TestsPage />)

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
})
