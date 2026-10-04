import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { InterviewsPanel } from './InterviewsPanel'

const scheduled = {
  id: 'i1',
  applicationId: 'a1',
  jobId: 'j1',
  jobTitle: 'React Intern',
  candidateId: 'p1',
  candidateName: 'Asha Rao',
  title: 'CodeWalnut interview – React Intern',
  startAt: '2030-01-15T05:30:00Z',
  endAt: '2030-01-15T06:15:00Z',
  timeZone: 'Asia/Kolkata',
  interviewers: ['priya@codewalnut.test'],
  message: null,
  status: 'SCHEDULED',
  meetLink: 'https://meet.google.com/abc-defg-hij',
  calendarLink: 'https://calendar.google.com/event?eid=1',
  organizerEmail: 'recruiter@codewalnut.test',
  cancelReason: null,
  createdAt: '2029-12-01T00:00:00Z',
}

function renderPanel(email: string | null = 'asha@example.com') {
  return render(
    <MemoryRouter>
      <InterviewsPanel
        applicationId="a1"
        candidateName="Asha Rao"
        candidateEmail={email}
        jobTitle="React Intern"
        canEdit
        onChanged={() => undefined}
      />
    </MemoryRouter>,
  )
}

describe('InterviewsPanel', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('schedules on Google Calendar and sends the invite details', async () => {
    const fetch = fakeFetch([
      { path: '/applications/a1/interviews', body: [] },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
      { method: 'POST', path: '/applications/a1/interviews', status: 201, body: scheduled },
    ])
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: 'Schedule interview' }))
    const form = await screen.findByRole('form', { name: 'Schedule interview' })
    expect((screen.getByLabelText(/Message to the candidate/) as HTMLTextAreaElement).value).toContain('Hi Asha,')
    await userEvent.type(screen.getByLabelText(/Interviewers/), 'priya@codewalnut.test, arjun@codewalnut.test')
    await userEvent.click(screen.getByRole('button', { name: 'Schedule & send invite' }))

    expect(await screen.findByText(/Google has emailed the invitation/)).toBeInTheDocument()
    expect(form).not.toBeInTheDocument()
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')
    const body = JSON.parse(String(post?.[1]?.body)) as Record<string, unknown>
    expect(body).toMatchObject({
      title: 'CodeWalnut interview – React Intern',
      durationMinutes: 45,
      interviewerEmails: ['priya@codewalnut.test', 'arjun@codewalnut.test'],
    })
    expect(typeof body.timeZone).toBe('string')
    expect(new Date(String(body.startAt)).getTime()).toBeGreaterThan(Date.now())
  })

  it('asks to connect Google Calendar first', async () => {
    fakeFetch([
      { path: '/applications/a1/interviews', body: [] },
      { path: '/google/status', body: { available: true, calendarConnected: false, mailConnected: false, redirectUri: 'x' } },
    ])
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: 'Schedule interview' }))
    expect(await screen.findByRole('button', { name: 'Connect Google (Calendar & Gmail)' })).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Schedule interview' })).not.toBeInTheDocument()
  })

  it('needs the candidate email before scheduling', async () => {
    fakeFetch([
      { path: '/applications/a1/interviews', body: [] },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
    ])
    renderPanel(null)

    await userEvent.click(await screen.findByRole('button', { name: 'Schedule interview' }))
    expect(await screen.findByText(/Add the candidate's email address first/)).toBeInTheDocument()
  })

  it('shows the Meet link and cancels with a reason', async () => {
    const fetch = fakeFetch([
      { path: '/applications/a1/interviews', body: [scheduled] },
      { method: 'POST', path: '/interviews/i1/cancel', body: { ...scheduled, status: 'CANCELLED' } },
    ])
    renderPanel()

    expect(await screen.findByRole('link', { name: 'Join Google Meet' })).toHaveAttribute('href', scheduled.meetLink)
    expect(screen.getByText('Tue, 15 Jan, 2030, 11:00 – 11:45 am IST')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancel interview' }))
    await userEvent.type(screen.getByLabelText(/Reason/), 'Candidate asked to move it')
    await userEvent.click(screen.getByRole('button', { name: 'Cancel & notify everyone' }))

    await waitFor(() => expect(screen.getByText(/Interview cancelled/)).toBeInTheDocument())
    const post = fetch.mock.calls.find(([url]) => String(url).includes('/cancel'))
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({ reason: 'Candidate asked to move it' })
  })

  it('shows feedback progress and links to the form once the interview has started (INT-25)', async () => {
    const past = { ...scheduled, startAt: '2026-01-15T05:30:00Z', endAt: '2026-01-15T06:15:00Z' }
    fakeFetch([
      { path: '/applications/a1/interviews', body: [past] },
      {
        path: '/applications/a1/interview-feedback',
        body: [{ interviewId: 'i1', submitted: 1, panelSize: 2, recommendations: ['YES'], mineSubmitted: false, visible: true }],
      },
    ])
    renderPanel()

    expect(await screen.findByText(/Feedback: 1 of 2 · Hire/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Feedback form' })).toHaveAttribute('href', '/interviews/i1/feedback')
  })

  it('offers the feedback form even before the scheduled time (INT-35)', async () => {
    fakeFetch([
      { path: '/applications/a1/interviews', body: [scheduled] },
      { path: '/applications/a1/interview-feedback', body: [] },
    ])
    renderPanel()

    expect(await screen.findByRole('link', { name: 'Join Google Meet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Feedback form' })).toHaveAttribute('href', '/interviews/i1/feedback')
  })

  it('logs an interview held elsewhere so feedback can be given (INT-33)', async () => {
    const fetch = fakeFetch([
      { path: '/applications/a1/interviews', body: [] },
      { path: '/applications/a1/interview-feedback', body: [] },
      { method: 'POST', path: '/applications/a1/interviews/log', status: 201, body: { ...scheduled, meetLink: null } },
    ])
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: 'Log an interview held elsewhere' }))
    await userEvent.type(screen.getByLabelText(/Interviewers/), 'priya@codewalnut.test')
    await userEvent.click(screen.getByRole('button', { name: 'Log interview' }))

    expect(await screen.findByText(/Interview logged/)).toBeInTheDocument()
    const post = fetch.mock.calls.find(([url, init]) => String(url).includes('/interviews/log') && init?.method === 'POST')
    const body = JSON.parse(String(post?.[1]?.body)) as Record<string, unknown>
    expect(body).toMatchObject({ durationMinutes: 45, interviewerEmails: ['priya@codewalnut.test'] })
    expect(new Date(String(body.startAt)).getTime()).toBeLessThanOrEqual(Date.now())
  })

  it('reschedules a missed interview and keeps the Meet link (INT-20)', async () => {
    const past = { ...scheduled, startAt: '2026-01-15T05:30:00Z', endAt: '2026-01-15T06:15:00Z' }
    const moved = { ...scheduled, startAt: '2030-01-16T09:00:00Z', endAt: '2030-01-16T09:45:00Z' }
    const fetch = fakeFetch([
      { path: '/applications/a1/interviews', body: [past] },
      { path: '/applications/a1/interview-feedback', body: [] },
      { method: 'POST', path: '/interviews/i1/reschedule', body: moved },
    ])
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: 'Reschedule' }))
    expect(screen.getByText(/The Meet link stays the same/)).toBeInTheDocument()
    expect((screen.getByLabelText(/Interviewers/) as HTMLInputElement).value).toBe('priya@codewalnut.test')
    await userEvent.type(screen.getByLabelText(/Reason/), 'Candidate missed the slot')
    await userEvent.click(screen.getByRole('button', { name: 'Reschedule' }))

    expect(await screen.findByText(/Rescheduled to .* the Meet link is the same/)).toBeInTheDocument()
    const post = fetch.mock.calls.find(([url]) => String(url).includes('/reschedule'))
    const body = JSON.parse(String(post?.[1]?.body)) as Record<string, unknown>
    expect(body).toMatchObject({ durationMinutes: 45, interviewerEmails: ['priya@codewalnut.test'], reason: 'Candidate missed the slot' })
    expect(new Date(String(body.startAt)).getTime()).toBeGreaterThan(Date.now())
  })
})
