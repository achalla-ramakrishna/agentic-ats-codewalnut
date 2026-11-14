import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { AiReviewPage, AiReviewsPage } from './AiReviewsPage'

const row = (inviteId: string, candidateName: string, mine: string | null) => ({
  inviteId, candidateName, jobId: 'j1', jobTitle: 'Senior Java Developer', stage: 'SCREENING', submittedAt: '2026-11-13T10:00:00Z',
  aiScore: 72, evaluation: 'DONE', mine, verdicts: mine ? [{ reviewerName: 'Dev AI Reviewer', decision: mine }] : [],
})

const page = (verdicts: unknown[] = []) => ({
  inviteId: 'i1', candidateName: 'Meera Sample', jobId: 'j1', jobTitle: 'Senior Java Developer', stage: 'SCREENING',
  detail: {
    invite: { id: 'i1', submittedAt: '2026-11-13T10:00:00Z' },
    answers: [
      { position: 1, kind: 'VIDEO', prompt: 'Tell us about a service you own.', code: null, options: [], given: ['v1', 'I own payments.'],
        correct: [], acceptedAnswers: [], points: 0, earned: 0, figure: null, optionFigures: null, section: 'INTRO', codeResult: null,
        explanation: 'Clear ownership' },
    ],
    sections: [], activity: null, review: null,
  },
  verdicts,
})

describe('AI interview reviews (AINT-21)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists the interviews waiting for my verdict first', async () => {
    fakeFetch([{ path: '/ai-reviews', body: { interviews: [row('i1', 'Meera Sample', null), row('i2', 'Arun Sample', 'SELECT')], toReview: 1 } }])
    render(<MemoryRouter><AiReviewsPage /></MemoryRouter>)
    const table = await screen.findByRole('table', { name: 'AI interviews to review' })
    expect(within(table).getByRole('link', { name: 'Meera Sample' })).toHaveAttribute('href', '/ai-reviews/i1')
    expect(within(table).queryByText('Arun Sample')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Reviewed by me (1)' }))
    expect(screen.getByText('Arun Sample')).toBeInTheDocument()
    expect(screen.getByText(/✅ Dev/)).toBeInTheDocument()
  })

  it('marks answers and saves a verdict; not selecting needs a reason', async () => {
    const fetchMock = fakeFetch([
      { path: '/ai-reviews/i1', method: 'PUT', body: page([{ reviewerName: 'Me', reviewerEmail: 'me@x', decision: 'HOLD',
        decisionLabel: 'Maybe — discuss', feedback: 'Thin on testing', marks: [], updatedAt: '2026-11-14T10:00:00Z', mine: true }]) },
      { path: '/ai-reviews/i1', body: page([{ reviewerName: 'Dhaval', reviewerEmail: 'd@x', decision: 'SELECT', decisionLabel: 'Select for next round',
        feedback: 'Strong design', marks: [], updatedAt: '2026-11-14T09:00:00Z', mine: false }]) },
    ])
    render(
      <MemoryRouter initialEntries={['/ai-reviews/i1']}>
        <Routes>
          <Route path="/ai-reviews/:id" element={<AiReviewPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByRole('heading', { name: 'Meera Sample' })).toBeInTheDocument()
    expect(screen.getByText('Strong design')).toBeInTheDocument() // the other reviewer's verdict
    expect(screen.getByText('Transcript: I own payments.')).toBeInTheDocument()
    await userEvent.click(within(screen.getByRole('group', { name: 'Your mark for question 1' })).getByRole('button', { name: '✓ Good' }))
    await userEvent.click(screen.getByRole('radio', { name: /Maybe — discuss/ }))
    const save = screen.getByRole('button', { name: 'Save verdict' })
    expect(save).toBeDisabled()
    expect(screen.getByText('(required — why?)')).toBeInTheDocument()
    await userEvent.type(screen.getByRole('textbox', { name: /Feedback/ }), 'Thin on testing')
    await userEvent.click(save)
    expect(await screen.findByRole('status')).toHaveTextContent('Saved.')
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')!
    expect(JSON.parse(put[1]!.body as string)).toEqual({
      decision: 'HOLD', feedback: 'Thin on testing', marks: [{ position: 1, mark: 'GOOD', note: null }],
    })
  })
})
