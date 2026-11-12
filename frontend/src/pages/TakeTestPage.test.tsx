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
    { id: 'q1', position: 1, kind: 'SINGLE_CHOICE', prompt: 'Which type is immutable?', code: null, options: ['list', 'tuple'], points: 1, figure: null, optionFigures: null, section: null },
    { id: 'q2', position: 2, kind: 'SHORT_ANSWER', prompt: 'What does this print?', code: 'print(len({1, 2, 2}))', options: [], points: 2, figure: null, optionFigures: null, section: null },
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

  it('shows section headings and picture options', async () => {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"></svg>'
    const fetchMock = fakeFetch([
      { path: '/candidate/tests/i1/start', method: 'POST', body: { ...taking, questions: [
        { id: 'p1', position: 1, kind: 'SINGLE_CHOICE', prompt: 'Which figure comes next?', code: null, options: ['Figure A', 'Figure B', 'Figure C', 'Figure D'],
          points: 3, figure: svg, optionFigures: [svg, svg, svg, svg], section: 'LOGICAL' },
      ] } },
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
    await userEvent.click(await screen.findByRole('button', { name: 'Start the test' }))
    expect(await screen.findByRole('heading', { name: 'Logical reasoning' })).toBeInTheDocument()
    expect(screen.getAllByRole('img')).toHaveLength(5)
    await userEvent.click(screen.getByLabelText('Figure C'))
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }))
    await screen.findByText('Thank you!')
    const submit = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/submit'))!
    expect(JSON.parse(submit[1]!.body as string)).toEqual({ answers: { p1: ['2'] } })
  })

  it('writes code, runs it on the samples and submits it (ASMT-29)', async () => {
    const coding = {
      languages: ['python', 'java'],
      starter: { python: 'import sys\n', java: 'public class Main {}\n' },
      samples: [{ input: '2 3\n', output: '5\n' }],
      timeLimitSeconds: 2,
      memoryMb: 256,
      inputFormat: 'Two integers.',
      outputFormat: 'Their sum.',
      constraints: null,
    }
    const fetchMock = fakeFetch([
      { path: '/candidate/tests/i1/start', method: 'POST', body: { ...taking, questions: [
        { id: 'c1', position: 1, kind: 'CODING', prompt: 'Add two numbers', code: null, options: [], points: 10, figure: null, optionFigures: null, section: null, coding },
      ] } },
      { path: '/candidate/tests/i1/questions/c1/run', method: 'POST', body: {
        compiled: true, compileOutput: null, passed: 1, total: 1, runsLeft: 29,
        cases: [{ sample: true, passed: true, status: 'PASSED', output: '5\n', error: null, timeSeconds: 0.02, memoryKb: 900, input: '2 3\n', expected: '5\n' }],
      } },
      { path: '/candidate/tests/i1/activity', method: 'POST', status: 204 },
      { path: '/candidate/tests/i1/answers', method: 'PUT', body: taking },
      { path: '/candidate/tests/i1/submit', method: 'POST', body: { ...test, status: 'SUBMITTED' } },
      { path: '/candidate/tests', body: [{ ...test, category: 'CODING' }] },
    ])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(
      <MemoryRouter initialEntries={['/tests/i1']}>
        <Routes>
          <Route path="/tests/:id" element={<TakeTestPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/Coding questions: write a program/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Start the test' }))
    expect(await screen.findByText('Two integers.')).toBeInTheDocument()
    const editor = screen.getByLabelText('Code for question 1')
    expect(editor).toHaveValue('import sys\n')
    await userEvent.type(editor, 'print(sum(map(int, input().split())))')
    await userEvent.click(screen.getByRole('button', { name: 'Run on samples' }))
    expect(await screen.findByText('1 of 1 passed')).toBeInTheDocument()
    expect(screen.getByText('29 runs left')).toBeInTheDocument()

    // Switching language loads that language's starter; switching back keeps the code written.
    await userEvent.selectOptions(screen.getByLabelText('Language for question 1'), 'java')
    expect(screen.getByLabelText('Code for question 1')).toHaveValue('public class Main {}\n')
    await userEvent.selectOptions(screen.getByLabelText('Language for question 1'), 'python')
    expect(screen.getByLabelText('Code for question 1')).toHaveValue('import sys\nprint(sum(map(int, input().split())))')
    expect(screen.getByText(/1 of 1 answered/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }))
    expect(await screen.findByText('Thank you!')).toBeInTheDocument()
    const submit = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/submit'))!
    expect(JSON.parse(submit[1]!.body as string)).toEqual({ answers: { c1: ['python', 'import sys\nprint(sum(map(int, input().split())))'] } })
    const run = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/run'))!
    expect(JSON.parse(run[1]!.body as string)).toEqual({ language: 'python', source: 'import sys\nprint(sum(map(int, input().split())))' })
  })
})

describe('TakeTestPage — AI interview (AINT-03, AINT-04)', () => {
  afterEach(() => vi.unstubAllGlobals())

  const interview = { ...test, title: 'AI interview — Senior Java', category: 'INTERVIEW', questionCount: 3, durationMinutes: 75 }
  const takingInterview = {
    test: { ...interview, status: 'STARTED' },
    questions: [
      { id: 'v1', position: 1, kind: 'VIDEO', prompt: 'Introduce yourself.', code: null, options: [], points: 0, figure: null, optionFigures: null, section: 'INTRO', coding: null, maxSeconds: 60 },
      { id: 'w1', position: 2, kind: 'WRITTEN', prompt: 'What is your notice period?', code: null, options: [], points: 0, figure: null, optionFigures: null, section: 'READINESS', coding: null, maxSeconds: null },
      { id: 'v2', position: 3, kind: 'VIDEO', prompt: 'Why this role?', code: null, options: [], points: 0, figure: null, optionFigures: null, section: 'READINESS', coding: null, maxSeconds: 60 },
    ],
    answers: { v2: ['saved-video', 'I like the work'] },
    secondsLeft: 4500,
  }

  /** A stand-in for the browser recorder: one chunk of "video" when stopped. */
  class FakeRecorder {
    static isTypeSupported = (t: string) => t === 'video/webm'
    mimeType = 'video/webm'
    state = 'inactive'
    ondataavailable: ((e: { data: Blob }) => void) | null = null
    onstop: (() => void) | null = null
    start() {
      this.state = 'recording'
    }
    stop() {
      this.state = 'inactive'
      this.ondataavailable?.({ data: new Blob(['webm'], { type: 'video/webm' }) })
      this.onstop?.()
    }
  }

  it('checks the camera, goes phase by phase, records and uploads an answer, and submits', { timeout: 20000 }, async () => {
    const fetchMock = fakeFetch([
      { path: '/candidate/tests/i1/start', method: 'POST', body: takingInterview },
      { path: '/candidate/tests/i1/questions/v1/video', method: 'POST', body: { videoId: 'vid-1', seconds: 12, sizeBytes: 4 } },
      { path: '/candidate/tests/i1/activity', method: 'POST', status: 204 },
      { path: '/candidate/tests/i1/answers', method: 'PUT', body: takingInterview },
      { path: '/candidate/tests/i1/submit', method: 'POST', body: { ...interview, status: 'SUBMITTED' } },
      { path: '/candidate/tests', body: [interview] },
    ])
    const stop = vi.fn()
    vi.stubGlobal('navigator', { ...navigator, mediaDevices: { getUserMedia: vi.fn(async () => ({ getTracks: () => [{ stop }] })) } })
    vi.stubGlobal('MediaRecorder', FakeRecorder)
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:answer', revokeObjectURL: () => undefined }))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
    render(
      <MemoryRouter initialEntries={['/tests/i1']}>
        <Routes>
          <Route path="/tests/:id" element={<TakeTestPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText(/Recorded interview for/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Check camera and microphone' }))
    expect(await screen.findByLabelText('Camera preview')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Start the interview' }))

    // Phase 1 of 2: only the introduction is on screen.
    expect(await screen.findByText('Introduce yourself.')).toBeInTheDocument()
    expect(screen.queryByText('Why this role?')).not.toBeInTheDocument()
    expect(screen.getByText('1. Introduction')).toHaveAttribute('aria-current', 'step')
    await userEvent.click(screen.getByRole('button', { name: '● Record answer' }))
    expect(screen.getByRole('timer', { name: 'Recording question 1' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '■ Stop and save' }))
    expect(await screen.findByText(/✓ Saved/, {}, { timeout: 8000 })).toBeInTheDocument()
    const upload = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/questions/v1/video'))!
    expect((upload[1]!.body as FormData).get('file')).toBeInstanceOf(Blob)
    expect(screen.getByText(/2 of 3 answered/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Next: Readiness to join →' }))
    expect(await screen.findByText('What is your notice period?')).toBeInTheDocument()
    expect(screen.queryByText('Introduce yourself.')).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Answer to question 2'), '30 days')
    await userEvent.click(screen.getByRole('button', { name: 'Submit the interview' }))
    expect(await screen.findByText('Thank you!')).toBeInTheDocument()
    const submit = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/submit'))!
    expect(JSON.parse(submit[1]!.body as string).answers.w1).toEqual(['30 days'])
    expect(stop).toHaveBeenCalled() // camera off afterwards
  })
})
