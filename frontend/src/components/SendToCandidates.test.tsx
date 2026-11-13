import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AssessmentSummary } from '../api/assessments'
import { fakeFetch } from '../test/fakeFetch'
import { SendToCandidates } from './SendToCandidates'

const test: AssessmentSummary = {
  id: 't1', title: 'Java freshers', category: 'JAVA', description: null, durationMinutes: 30, passPercent: 60, status: 'READY',
  questionCount: 20, totalPoints: 36, invites: 1, taken: 0, createdBy: 'r@x', updatedAt: '2026-10-03T10:00:00Z',
} as unknown as AssessmentSummary

const app = (id: string, name: string, email: string | null) => ({
  id, jobId: 'j1', jobTitle: 'Java developer', clientName: null, candidateId: `c-${id}`, name, email, phone: null,
  stage: 'SCREENING', stageLabel: 'Screening', updatedAt: '2026-10-03T10:00:00Z', lastNote: null, documents: [],
})

describe('Send to candidates', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends a ready test to several candidates of an opening and reports each outcome', async () => {
    const fetchMock = fakeFetch([
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
      { path: '/jobs/j1/applications', body: [app('a1', 'Asha Test', 'asha@x.test'), app('a2', 'Ravi Test', 'ravi@x.test'), app('a3', 'Meena Test', 'meena@x.test'), app('a4', 'No Mail', null)] },
      { path: '/jobs/j1/tests', body: [{ id: 'i9', applicationId: 'a3', assessmentId: 't1', status: 'SENT', title: 'Java freshers' }] },
      { path: '/jobs', body: [{ id: 'j1', title: 'Java developer', client: null, status: 'OPEN', total: 4 }] },
      { path: '/applications/a1/tests', method: 'POST', status: 201, body: { invite: { id: 'n1' }, message: {} } },
      { path: '/applications/a2/tests', method: 'POST', status: 409, body: { error: 'Ravi Test already has this test open' } },
    ])
    const onSent = vi.fn()
    render(<SendToCandidates test={test} onSent={onSent} onClose={() => undefined} />)

    await userEvent.selectOptions(await screen.findByLabelText('Opening'), 'j1')
    // People already sent it are hidden until asked for.
    await screen.findByRole('checkbox', { name: 'Send to Asha Test' })
    expect(screen.queryByText('Meena Test')).not.toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Hide people already sent it'))
    expect(await screen.findByText('Already sent · Sent, not started')).toBeInTheDocument()
    expect(screen.getByText('No email')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Send to Meena Test' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Send to No Mail' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Select all who can get it (2)' }))
    await userEvent.click(screen.getByRole('button', { name: 'Send to 2 candidates' }))

    expect(await screen.findByText('Sent to 1: Asha Test.')).toBeInTheDocument()
    expect(screen.getByText(/Ravi Test: Ravi Test already has this test open/)).toBeInTheDocument()
    expect(onSent).toHaveBeenCalled()
    const sent = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/applications/a1/tests') && init?.method === 'POST')!
    expect(JSON.parse(sent[1]!.body as string)).toMatchObject({ assessmentId: 't1', dueDays: 3, sendEmail: true, sendWhatsApp: false })
  })

  it('selects only the candidates whose résumé matches the test’s stack (ASMT-39)', async () => {
    fakeFetch([
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
      { path: '/jobs/j1/applications', body: [app('a1', 'Asha Test', 'asha@x.test'), app('a2', 'Ravi Test', 'ravi@x.test'), app('a3', 'Meena Test', 'meena@x.test')] },
      { path: '/jobs/j1/tests', body: [] },
      {
        path: '/jobs/j1/backgrounds',
        body: [
          { applicationId: 'a1', track: 'JAVA', evidence: ['Java', 'Spring'], read: true },
          { applicationId: 'a2', track: 'MERN', evidence: ['React'], read: true },
          { applicationId: 'a3', track: null, evidence: [], read: false },
        ],
      },
      { path: '/jobs', body: [{ id: 'j1', title: 'Interns', client: null, status: 'OPEN', total: 3 }] },
    ])
    render(<SendToCandidates test={test} onSent={() => undefined} onClose={() => undefined} />)

    await userEvent.selectOptions(await screen.findByLabelText('Opening'), 'j1')
    await userEvent.click(await screen.findByRole('button', { name: 'Select next 1 with a Java background' }))
    expect(screen.getByRole('checkbox', { name: 'Send to Asha Test' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Send to Ravi Test' })).not.toBeChecked()
    expect(screen.getByText('MERN')).toBeInTheDocument()
    expect(screen.getByText(/1 résumé hasn’t been read by the AI yet/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send to 1 candidate' })).toBeEnabled()
  })

  it('asks to connect Gmail before emailing, or sends the link without email', async () => {
    const fetchMock = fakeFetch([
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
      { path: '/google/status', body: { available: true, calendarConnected: false, mailConnected: false, redirectUri: '' } },
      { path: '/jobs/j1/applications', body: [app('a1', 'Asha Test', 'asha@x.test')] },
      { path: '/jobs/j1/tests', body: [] },
      { path: '/jobs', body: [{ id: 'j1', title: 'Java developer', client: null, status: 'OPEN', total: 1 }] },
      { path: '/applications/a1/tests', method: 'POST', status: 201, body: { invite: { id: 'n1' }, message: {} } },
    ])
    render(<SendToCandidates test={test} onSent={() => undefined} onClose={() => undefined} />)
    await userEvent.selectOptions(await screen.findByLabelText('Opening'), 'j1')
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Send to Asha Test' }))
    expect(await screen.findByRole('button', { name: /Connect Google/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send to 1 candidate' })).toBeDisabled()

    await userEvent.click(screen.getByRole('checkbox', { name: 'Email' }))
    expect(screen.queryByRole('button', { name: /Connect Google/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Send to 1 candidate' }))
    expect(await screen.findByText('Sent to 1: Asha Test.')).toBeInTheDocument()
    const sent = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/applications/a1/tests') && init?.method === 'POST')!
    expect(JSON.parse(sent[1]!.body as string)).toMatchObject({ sendEmail: false })
  })

  it('finds people by name, selects everyone shown, and keeps Send in one row', async () => {
    fakeFetch([
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
      { path: '/jobs/j1/applications', body: [app('a1', 'Asha Test', 'asha@x.test'), app('a2', 'Ravi Test', 'ravi@x.test'), app('a3', 'Ravindra Test', 'ravindra@x.test')] },
      { path: '/jobs/j1/tests', body: [] },
      { path: '/jobs', body: [{ id: 'j1', title: 'Java developer', client: null, status: 'OPEN', total: 3 }] },
    ])
    render(<SendToCandidates test={{ ...test, jobId: 'j1' } as AssessmentSummary} onSent={() => undefined} onClose={() => undefined} />)
    // An AI interview (or any test that knows its opening) starts there.
    expect(await screen.findByRole('checkbox', { name: 'Send to Asha Test' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Find candidates'), 'ravi')
    expect(screen.queryByText('Asha Test')).not.toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Select everyone shown'))
    expect(screen.getByText('2 selected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send to 2 candidates' })).toBeEnabled()
  })

  it('lists candidates from all openings, each with their opening', async () => {
    const fetchMock = fakeFetch([
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
      { path: '/jobs/j1/applications', body: [app('a1', 'Asha Test', 'asha@x.test')] },
      { path: '/jobs/j2/applications', body: [{ ...app('b1', 'Bala Test', 'bala@x.test'), jobId: 'j2', jobTitle: 'React developer' }] },
      { path: '/jobs/j1/tests', body: [] },
      { path: '/jobs/j2/tests', body: [] },
      { path: '/jobs', body: [{ id: 'j1', title: 'Java developer', client: null, status: 'OPEN', total: 1 }, { id: 'j2', title: 'React developer', client: null, status: 'OPEN', total: 1 }] },
      { path: '/applications/b1/tests', method: 'POST', status: 201, body: { invite: { id: 'n2' }, message: {} } },
    ])
    render(<SendToCandidates test={test} onSent={() => undefined} onClose={() => undefined} />)
    await userEvent.selectOptions(await screen.findByLabelText('Opening'), '__all__')
    expect(await screen.findByRole('checkbox', { name: 'Send to Bala Test' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Send to Asha Test' })).toBeInTheDocument()
    expect(screen.getAllByText('React developer').length).toBeGreaterThan(0)
    expect(screen.queryByText(/Add people by email/)).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Find candidates'), 'react')
    expect(screen.queryByRole('checkbox', { name: 'Send to Asha Test' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Send to Bala Test' }))
    await userEvent.click(screen.getByRole('button', { name: 'Send to 1 candidate' }))
    expect(await screen.findByText('Sent to 1: Bala Test.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/applications/b1/tests') && init?.method === 'POST')).toBe(true)
  })

  it('keeps ticks across searches, shows them as chips, and adds new email addresses', async () => {
    const fetchMock = fakeFetch([
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
      { path: '/jobs/j1/applications', method: 'POST', status: 201, body: { ...app('a9', 'Nisha Patel', 'nisha@x.test') } },
      { path: '/jobs/j1/applications', body: [app('a1', 'Asha Test', 'asha@x.test'), app('a2', 'Ravi Test', 'ravi@x.test'), app('a3', 'Meena Test', 'meena@x.test')] },
      { path: '/jobs/j1/tests', body: [] },
      { path: '/jobs', body: [{ id: 'j1', title: 'Java developer', client: null, status: 'OPEN', total: 3 }] },
      { path: '/applications/', method: 'POST', status: 201, body: { invite: { id: 'n1' }, message: {} } },
    ])
    render(<SendToCandidates test={{ ...test, jobId: 'j1' } as AssessmentSummary} onSent={() => undefined} onClose={() => undefined} />)
    const find = await screen.findByLabelText('Find candidates')
    await userEvent.type(find, 'asha')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Send to Asha Test' }))
    await userEvent.clear(find)
    await userEvent.type(find, 'meena')
    await userEvent.click(screen.getByRole('checkbox', { name: 'Send to Meena Test' }))
    const chips = screen.getByRole('list', { name: 'Selected people' })
    expect(chips).toHaveTextContent('Asha Test')
    expect(chips).toHaveTextContent('Meena Test')
    await userEvent.click(screen.getByRole('button', { name: 'Remove Meena Test' }))
    expect(chips).not.toHaveTextContent('Meena Test')

    await userEvent.type(screen.getByLabelText(/Also send to/), 'Nisha Patel <nisha@x.test>')
    expect(chips).toHaveTextContent('Nisha Patel · new')
    await userEvent.click(screen.getByRole('button', { name: 'Send to 2 candidates' }))
    expect(await screen.findByText(/Sent to 2: Asha Test, Nisha Patel/)).toBeInTheDocument()
    const added = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/jobs/j1/applications') && init?.method === 'POST')!
    expect(JSON.parse(added[1]!.body as string)).toMatchObject({ name: 'Nisha Patel', email: 'nisha@x.test' })
  })
})
