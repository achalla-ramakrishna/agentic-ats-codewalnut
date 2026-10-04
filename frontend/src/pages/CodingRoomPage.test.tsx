import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { CodingRoomPage } from './CodingRoomPage'

const problem = {
  id: 'two-sum',
  title: 'Two sum',
  statement: 'Print the indexes of two numbers that add up to the target.',
  inputFormat: 'n and target, then n numbers',
  outputFormat: 'Two indexes',
  samples: [{ input: '4 9\n2 7 11 15', output: '0 1' }],
  languages: ['java', 'python'],
  starter: { java: 'public class Main {}', python: '# read input\n' },
}

const room = {
  status: 'OPEN',
  jobTitle: 'Java Intern',
  problem,
  language: 'java',
  code: null,
  lastRun: null,
  runsLeft: 200,
  runnerAvailable: true,
}

const ran = {
  ...room,
  language: 'python',
  code: 'print("0 1")',
  runsLeft: 199,
  lastRun: {
    compiled: true,
    compileOutput: null,
    passed: 1,
    total: 1,
    runsLeft: 199,
    cases: [{ sample: true, passed: true, status: 'PASSED', output: '0 1', error: null, timeSeconds: 0.1, memoryKb: null, input: '4 9', expected: '0 1' }],
  },
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/coding/tok']}>
      <Routes>
        <Route path="/coding/:token" element={<CodingRoomPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('CodingRoomPage (INT-37)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the problem with the starter, switches language and runs on samples', async () => {
    const fetch = fakeFetch([
      { path: '/candidate/coding/tok/run', method: 'POST', body: ran },
      { path: '/candidate/coding/tok', method: 'PUT', body: room },
      { path: '/candidate/coding/tok', body: room },
    ])
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Two sum' })).toBeInTheDocument()
    expect(screen.getByLabelText('Your code')).toHaveValue('public class Main {}')
    // The untouched starter is swapped for the new language's.
    await userEvent.selectOptions(screen.getByLabelText('Language'), 'python')
    expect(screen.getByLabelText('Your code')).toHaveValue('# read input\n')

    await userEvent.clear(screen.getByLabelText('Your code'))
    await userEvent.type(screen.getByLabelText('Your code'), 'print("0 1")')
    await userEvent.click(screen.getByRole('button', { name: 'Run on samples' }))
    expect(await screen.findByText('1 of 1 passed')).toBeInTheDocument()
    expect(screen.getByText('199 runs left')).toBeInTheDocument()
    const run = fetch.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(String(run?.[1]?.body))).toEqual({ language: 'python', source: 'print("0 1")' })
  })

  it('is read-only once the interviewer ends the round', async () => {
    fakeFetch([{ path: '/candidate/coding/tok', body: { ...room, status: 'ENDED', code: 'class Main {}' } }])
    renderPage()
    expect(await screen.findByText(/ended this coding round/)).toBeInTheDocument()
    expect(screen.getByLabelText('Your code')).toHaveAttribute('readonly')
    expect(screen.queryByRole('button', { name: 'Run on samples' })).not.toBeInTheDocument()
  })

  it('says so when the link is not theirs', async () => {
    fakeFetch([{ path: '/candidate/coding/tok', status: 404, body: { error: 'Coding room not found' } }])
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Coding room not found')
  })
})
