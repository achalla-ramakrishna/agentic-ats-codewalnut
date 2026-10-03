import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
    render(<TestsSection applicationId="a1" canSend hasEmail hasPhone />)

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
})
