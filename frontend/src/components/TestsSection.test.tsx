import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { TestsSection } from './TestsSection'

const invite = {
  id: 'i1',
  applicationId: 'a1',
  assessmentId: 't1',
  title: 'Java basics',
  category: 'JAVA',
  status: 'SUBMITTED',
  sentBy: 'recruiter@codewalnut.test',
  sentAt: '2026-10-01T00:00:00Z',
  dueAt: '2026-10-04T00:00:00Z',
  startedAt: '2026-10-02T00:00:00Z',
  submittedAt: '2026-10-02T00:20:00Z',
  score: 4,
  maxScore: 5,
  percent: 80,
  passed: true,
  passPercent: 60,
  reminderCount: 0,
  lastRemindedAt: null,
  needsNudge: false,
}

describe('TestsSection', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows scores and sends a ready test by email', async () => {
    const fetchMock = fakeFetch([
      { path: '/applications/a1/tests', method: 'POST', status: 201, body: { invite: { ...invite, id: 'i2', status: 'SENT', title: 'Aptitude' }, message: { whatsappLink: null } } },
      { path: '/applications/a1/tests', body: [invite] },
      {
        path: '/assessments',
        body: [
          { id: 't2', title: 'Aptitude', category: 'APTITUDE', status: 'READY', questionCount: 6, durationMinutes: 20 },
          { id: 't3', title: 'Draft one', category: 'JAVA', status: 'DRAFT', questionCount: 1, durationMinutes: 20 },
        ],
      },
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
    ])
    render(
      <MemoryRouter>
        <TestsSection applicationId="a1" canSend hasEmail hasPhone />
      </MemoryRouter>,
    )

    expect(await screen.findByText('80% · passed')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Send test' }))
    const form = await screen.findByRole('form', { name: 'Send a test' })
    const select = within(form).getByLabelText('Test')
    expect(within(select).getAllByRole('option')).toHaveLength(1)
    await within(form).findByRole('option', { name: /Aptitude · Aptitude · 6 questions/ })
    await userEvent.click(within(form).getByRole('button', { name: 'Send test' }))

    expect(await screen.findByText(/Sent “Aptitude”/)).toBeInTheDocument()
    const send = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/applications/a1/tests') && init?.method === 'POST')!
    expect(JSON.parse(send[1]!.body as string)).toMatchObject({ assessmentId: 't2', dueDays: 3, sendEmail: true, sendWhatsApp: false })
  })

  it('opens the send form straight away from the drawer, and points to the builder when nothing is ready', async () => {
    fakeFetch([
      { path: '/applications/a1/tests', body: [] },
      { path: '/assessments', body: [{ id: 't3', title: 'Draft one', category: 'JAVA', status: 'DRAFT', questionCount: 1, durationMinutes: 20 }] },
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
    ])
    render(
      <MemoryRouter>
        <TestsSection applicationId="a1" canSend hasEmail hasPhone startOpen />
      </MemoryRouter>,
    )
    expect(await screen.findByRole('form', { name: 'Send a test' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Build one from the question bank' })).toHaveAttribute('href', '/tests')
  })

  it('shows graded code with its test cases and the browser signals (ASMT-29, ASMT-37)', async () => {
    const coding = { ...invite, id: 'i9', title: 'Coding', category: 'CODING', grading: 'DONE', score: 6, maxScore: 8, percent: 75 }
    fakeFetch([
      {
        path: '/tests/i9',
        body: {
          invite: coding,
          sections: [],
          activity: { tabSwitches: 2, pastes: 1, pastedChars: 300, runs: 4 },
          answers: [
            {
              position: 1, kind: 'CODING', prompt: 'Two sum\n\nFind the pair.', code: null, options: [], given: ['python', 'print(1)'],
              correct: [], acceptedAnswers: [], points: 8, earned: 6, figure: null, optionFigures: null, section: null,
              codeResult: {
                language: 'python', source: 'print(1)', passed: 3, total: 4, earned: 6, compileOutput: null,
                cases: [{ sample: false, passed: false, status: 'WRONG_ANSWER', output: '1\n', error: null, timeSeconds: 0.01, memoryKb: 100, input: '5\n', expected: '2\n' }],
              },
            },
          ],
        },
      },
      { path: '/applications/a1/tests', body: [coding, { ...coding, id: 'i8', title: 'Coding 2', grading: 'FAILED', percent: null }] },
      { path: '/assessments', body: [] },
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
    ])
    render(
      <MemoryRouter>
        <TestsSection applicationId="a1" canSend hasEmail hasPhone />
      </MemoryRouter>,
    )
    expect(await screen.findByText(/the code couldn’t be graded/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Grade again' })).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('button', { name: 'Answers' })[0])
    expect(await screen.findByText('3 of 4 tests passed')).toBeInTheDocument()
    expect(screen.getByText(/left the test tab 2× · pasted 1× \(300 characters\) · ran code on samples 4×/)).toBeInTheDocument()
    expect(screen.getByText('print(1)')).toBeInTheDocument()
  })
})
