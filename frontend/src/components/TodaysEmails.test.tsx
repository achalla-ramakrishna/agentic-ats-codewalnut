import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { WorkflowRow } from '../api/workflow'
import { fakeFetch } from '../test/fakeFetch'
import { TodaysEmails } from './TodaysEmails'

const row = (id: string, name: string, due: string | null, extra: Partial<WorkflowRow> = {}) =>
  ({
    applicationId: id, jobId: 'j1', jobTitle: 'Senior Java Developer', clientName: null, candidateId: `c-${id}`, candidateName: name,
    hasPhone: true, hasEmail: true, stage: 'SOURCED', stageLabel: 'Applied / Sourced', closed: false, inStageSince: '2026-10-01T00:00:00Z',
    addedAt: '2026-10-01T00:00:00Z', lastContact: null, contacts: 0, awaitingReply: false, candidateWroteAt: null, test: null,
    interview: null, client: null, documentsPending: 0, nextStep: null, lastActivityAt: '2026-10-01T00:00:00Z', emails: [], due, ...extra,
  }) as WorkflowRow

describe("Today's emails (PIPE-25)", () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends what is due in one click, warmest first, within the daily limit', async () => {
    const fetchMock = fakeFetch([
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
      { path: '/jobs/j1/slots/send-invites', method: 'POST', body: { scheduled: 1, failed: [] } },
      { path: '/booking-links', method: 'POST', body: { links: { a2: 'https://ats.example/book/t2' }, problems: {} } },
      ...['a2', 'a3', 'a4'].map((id) => ({ path: `/applications/${id}/messages`, method: 'POST', status: 201, body: {} })),
    ])
    const onDone = vi.fn()
    render(
      <TodaysEmails
        rows={[
          row('a1', 'Booked Person', 'SEND_INVITE'),
          row('a2', 'Resume Person', 'BOOKING_LINK'),
          row('a3', 'New One', 'RESUME_REQUEST'),
          row('a4', 'New Two', 'RESUME_REQUEST'),
          row('a5', 'Wrote To Us', null, { awaitingReply: true }),
        ]}
        senderName="Priya"
        onDone={onDone}
      />,
    )
    expect(screen.getByText(/4 due · 1 wrote to you – reply personally/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Review & send' }))
    expect(await screen.findByRole('button', { name: 'Send 3 emails and 1 invite' })).toBeEnabled()

    await userEvent.click(screen.getByRole('button', { name: 'Send 3 emails and 1 invite' }))
    expect(await screen.findByText('Calendar invites sent: 1')).toBeInTheDocument()
    expect(screen.getByText(/Book a 30-minute call \(booking link\): 1 sent/)).toBeInTheDocument()
    expect(screen.getByText(/Ask for résumé \(job portal applicants\): 2 sent/)).toBeInTheDocument()
    expect(onDone).toHaveBeenCalled()

    const posts = fetchMock.mock.calls.filter(([url, init]) => String(url).includes('/messages') && init?.method === 'POST')
    expect(posts.map(([url]) => String(url))).toEqual(['/api/v1/applications/a2/messages', '/api/v1/applications/a3/messages', '/api/v1/applications/a4/messages'])
    const booking = JSON.parse(posts[0][1]!.body as string)
    expect(booking).toMatchObject({ templateKey: 'book_slot', sendEmail: true })
    expect(booking.body).toContain('https://ats.example/book/t2')
    expect(JSON.parse(posts[1][1]!.body as string).body).toContain('Hi New,')
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/applications/a5/'))).toBe(false)
  })
})
