import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { TakeTestPage } from './TakeTestPage'

const test = {
  id: 'i1',
  title: 'Python basics',
  category: 'PYTHON',
  description: null,
  jobTitle: 'Blend – Interns',
  questionCount: 2,
  durationMinutes: 20,
  status: 'SENT',
  dueAt: '2026-10-06T00:00:00Z',
  startedAt: null,
  deadlineAt: null,
  submittedAt: null,
}

const taking = {
  test: { ...test, status: 'STARTED' },
  questions: [
    { id: 'q1', position: 1, kind: 'SINGLE_CHOICE', prompt: 'Which type is immutable?', code: null, options: ['list', 'tuple'], points: 1 },
    { id: 'q2', position: 2, kind: 'SHORT_ANSWER', prompt: 'What does this print?', code: 'print(len({1, 2, 2}))', options: [], points: 2 },
  ],
  answers: {},
  secondsLeft: 1200,
}

describe('TakeTestPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('starts the timer, answers the questions and submits', async () => {
    const fetchMock = fakeFetch([
      { path: '/candidate/tests/i1/start', method: 'POST', body: taking },
      { path: '/candidate/tests/i1/submit', method: 'POST', body: { ...test, status: 'SUBMITTED' } },
      { path: '/candidate/tests', body: [test] },
    ])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(
      <MemoryRouter initialEntries={['/tests/i1']}>
        <Routes>
          <Route path="/tests/:id" element={<TakeTestPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText(/20 minutes/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Start the test' }))
    expect(await screen.findByRole('timer')).toHaveTextContent('20:00')
    expect(screen.getByText('print(len({1, 2, 2}))')).toBeInTheDocument()

    await userEvent.click(screen.getByLabelText('tuple'))
    await userEvent.type(screen.getByLabelText('Answer to question 2'), '2')
    expect(screen.getByText(/2 of 2 answered/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }))

    expect(await screen.findByText('Thank you!')).toBeInTheDocument()
    const submit = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/submit'))!
    expect(JSON.parse(submit[1]!.body as string)).toEqual({ answers: { q1: ['1'], q2: ['2'] } })
  })
})
