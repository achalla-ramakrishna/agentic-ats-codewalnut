import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/types'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch, type FakeRoute } from '../test/fakeFetch'

const recruiter: Me = {
  id: 'u1',
  email: 'priya@codewalnut.test',
  name: 'Priya Sharma',
  roles: ['RECRUITER'],
  capabilities: ['VIEW_DASHBOARD', 'VIEW_CANDIDATES', 'MESSAGE_CANDIDATES'],
  navigation: [{ key: 'workflow', label: 'Workflow', path: '/workflow' }],
}
const signedIn: FakeRoute[] = [
  { path: '/auth/session', body: { type: 'STAFF' } },
  { path: '/me', body: recruiter },
]

const base = {
  jobId: 'j1',
  jobTitle: 'Java Intern',
  clientName: null,
  candidateId: 'p',
  hasPhone: true,
  hasEmail: true,
  closed: false,
  inStageSince: '2026-10-01T00:00:00Z',
  addedAt: '2026-10-01T00:00:00Z',
  test: null,
  interview: null,
  client: null,
  documentsPending: 0,
  lastActivityAt: '2026-10-05T00:00:00Z',
  candidateWroteAt: null,
}
const rows = [
  {
    ...base,
    applicationId: 'a1',
    candidateName: 'Rekha Rao',
    stage: 'SCREENING',
    stageLabel: 'Screening',
    lastContact: { at: '2026-10-04T00:00:00Z', how: 'Message on their candidate page', by: 'recruiter@codewalnut.test' },
    contacts: 1,
    awaitingReply: true,
    candidateWroteAt: '2026-10-05T00:00:00Z',
    nextStep: { code: 'REPLY', label: 'Reply to their message', urgent: true },
  },
  {
    ...base,
    applicationId: 'a2',
    candidateName: 'Nina Shah',
    stage: 'SOURCED',
    stageLabel: 'Applied / Sourced',
    lastContact: null,
    contacts: 0,
    awaitingReply: false,
    nextStep: { code: 'FIRST_CONTACT', label: 'Get in touch: email, WhatsApp or call', urgent: true },
  },
  {
    ...base,
    applicationId: 'a3',
    candidateName: 'Om Prakash',
    stage: 'JOINED',
    stageLabel: 'Joined',
    closed: true,
    lastContact: { at: '2026-09-01T00:00:00Z', how: 'Email', by: 'admin@codewalnut.test' },
    contacts: 4,
    awaitingReply: false,
    nextStep: null,
  },
]
const board = {
  rows,
  counts: { active: 2, urgent: 2, reply: 1, never: 1, quiet: 0, closed: 1 },
  openings: [{ id: 'j1', title: 'Java Intern', clientName: null, status: 'OPEN' }],
  canLog: true,
}
const timeline = {
  applicationId: 'a2',
  candidateName: 'Nina Shah',
  jobTitle: 'Java Intern',
  items: [{ at: '2026-10-01T00:00:00Z', kind: 'ADDED', title: 'Added at Applied / Sourced', detail: null, by: 'recruiter@codewalnut.test' }],
}

function renderPage(path = '/workflow') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('WorkflowPage (WF-01…WF-05)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows who is waiting, who was never contacted, and filters', async () => {
    fakeFetch([...signedIn, { path: '/whatsapp/status', body: { apiEnabled: false } }, { path: '/workflow', body: board }])
    renderPage()

    expect(await screen.findByText('Rekha Rao')).toBeInTheDocument()
    expect(screen.getByRole('table')).toHaveTextContent('Never contacted')
    expect(screen.getByText(/They wrote/)).toBeInTheDocument()
    expect(screen.queryByText('Om Prakash')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reply to their message' })).toHaveAttribute('href', '/jobs/j1?candidate=a1&tab=candidate')

    await userEvent.click(screen.getByRole('tab', { name: /Never contacted/ }))
    expect(screen.queryByText('Rekha Rao')).not.toBeInTheDocument()
    expect(screen.getByText('Nina Shah')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: /Joined \/ rejected/ }))
    expect(screen.getByText('Om Prakash')).toBeInTheDocument()
  })

  it('emails everyone on a tab with the template that fits it (PIPE-22)', async () => {
    const fetchMock = fakeFetch([
      ...signedIn,
      { path: '/whatsapp/status', body: { apiEnabled: false } },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
      { path: '/workflow', body: board },
      { path: '/applications/a1/messages', method: 'POST', status: 201, body: {} },
      { path: '/booking-links', method: 'POST', body: { links: { a1: 'https://ats.example/book/tok123' }, problems: {} } },
    ])
    renderPage('/workflow?show=reply')

    expect(await screen.findByText('Rekha Rao')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Select first 1' }))
    expect(screen.getByRole('checkbox', { name: 'Select Rekha Rao' })).toBeChecked()
    await userEvent.click(screen.getByRole('button', { name: 'Email selected (1)' }))

    // Waiting for our reply → the "pick a time" email with each person's own booking link (PIPE-24).
    expect(screen.getByLabelText('Template')).toHaveValue('book_slot')
    await userEvent.click(await screen.findByRole('button', { name: 'Send email to 1' }))
    expect(await screen.findByText(/Emailed 1 candidate/)).toBeInTheDocument()
    const sent = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/applications/a1/messages'))!
    const body = JSON.parse(sent[1]!.body as string)
    expect(body).toMatchObject({ channel: 'CANDIDATE', sendEmail: true })
    expect(body.body).toContain('Hi Rekha,')
    expect(body.body).toContain('Java Intern role')
    expect(body.body).toContain('https://ats.example/book/tok123')
    expect(body.body).not.toContain('{bookingLink}')
    expect(body.subject).toBe('Pick a time for a 30-minute call – Java Intern at CodeWalnut')
    expect(body.templateKey).toBe('book_slot')
  })

  it('offers the résumé request on "Never contacted"', async () => {
    fakeFetch([
      ...signedIn,
      { path: '/whatsapp/status', body: { apiEnabled: false } },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
      { path: '/workflow', body: board },
    ])
    renderPage('/workflow?show=never')
    expect(await screen.findByText('Nina Shah')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Nina Shah' }))
    await userEvent.click(screen.getByRole('button', { name: 'Email selected (1)' }))
    expect(screen.getByLabelText('Template')).toHaveValue('resume_request')
  })

  it('works by stage: shows emails sent, finds who has not had the shortlisted email, sends it and moves them (PIPE-23)', async () => {
    const people = [
      {
        ...rows[0],
        applicationId: 's1',
        candidateName: 'Asha Rao',
        stage: 'SHORTLISTED',
        stageLabel: 'Shortlisted',
        awaitingReply: false,
        nextStep: null,
        emails: [
          { templateKey: 'shortlisted', subject: "You're shortlisted", at: '2026-10-11T10:00:00Z', by: 'priya@codewalnut.test' },
          { templateKey: 'resume_request', subject: 'Résumé', at: '2026-10-09T10:00:00Z', by: 'priya@codewalnut.test' },
        ],
      },
      { ...rows[1], applicationId: 's2', candidateName: 'Bala Test', stage: 'SHORTLISTED', stageLabel: 'Shortlisted', emails: [] },
      { ...rows[1], applicationId: 's3', candidateName: 'Chitra Test', emails: [] },
    ]
    const fetchMock = fakeFetch([
      ...signedIn,
      { path: '/whatsapp/status', body: { apiEnabled: false } },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
      { path: '/workflow', body: { ...board, rows: people } },
      { path: '/applications/s2/messages', method: 'POST', status: 201, body: {} },
      { path: '/applications/s2/stage', method: 'PATCH', body: {} },
    ])
    renderPage()

    expect(await screen.findByText('Asha Rao')).toBeInTheDocument()
    expect(screen.getByText('Shortlisted', { selector: '.wf-email-chip' })).toBeInTheDocument()
    expect(screen.getByText('Résumé request', { selector: '.wf-email-chip' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /^Shortlisted 2/ }))
    expect(screen.queryByText('Chitra Test')).not.toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Filter by email sent'), 'not:shortlisted')
    expect(screen.queryByText('Asha Rao')).not.toBeInTheDocument()
    expect(screen.getByText('Bala Test')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Select first 1' }))
    await userEvent.click(screen.getByRole('button', { name: 'Email selected (1)' }))
    expect(screen.getByLabelText('Template')).toHaveValue('shortlisted')
    expect(screen.getByLabelText(/Also move them to Shortlisted/)).toBeChecked()
    await userEvent.click(await screen.findByRole('button', { name: 'Send email to 1' }))
    expect(await screen.findByText(/Emailed 1 candidate/)).toBeInTheDocument()
    const sent = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/applications/s2/messages'))!
    expect(JSON.parse(sent[1]!.body as string)).toMatchObject({ templateKey: 'shortlisted', sendEmail: true })
  })

  it('opens the history and logs a call', async () => {
    const fetch = fakeFetch([
      ...signedIn,
      { path: '/whatsapp/status', body: { apiEnabled: false } },
      { path: '/workflow', body: board },
      { path: '/applications/a2/timeline', body: timeline },
      {
        method: 'POST',
        path: '/applications/a2/contacts',
        body: { ...timeline, items: [{ at: '2026-10-06T00:00:00Z', kind: 'CONTACT', title: 'Call (logged)', detail: 'Interested', by: 'recruiter@codewalnut.test' }, ...timeline.items] },
      },
    ])
    renderPage('/workflow?job=j1')
    await userEvent.click(await screen.findByRole('button', { name: 'History of Nina Shah' }))
    const history = await screen.findByRole('list', { name: 'What happened with Nina Shah' })
    expect(within(history).getByText('Added at Applied / Sourced')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('What was said'), 'Interested')
    await userEvent.click(screen.getByRole('button', { name: 'Log it' }))
    expect(await within(history).findByText('Call (logged)')).toBeInTheDocument()
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ how: 'CALL', note: 'Interested' })
    expect(fetch.mock.calls.some(([url]) => String(url).includes('/workflow?jobId=j1'))).toBe(true)
  })

  it('opens WhatsApp with a suggested message and records it', async () => {
    const fetch = fakeFetch([
      ...signedIn,
      { path: '/whatsapp/status', body: { apiEnabled: false } },
      { path: '/workflow', body: board },
      { method: 'POST', path: '/applications/a2/messages', body: { id: 'm1', whatsapp: 'OPENED', whatsappLink: 'https://wa.me/919800000001?text=Hi' } },
    ])
    const tab = { location: { href: '' }, close: vi.fn() }
    const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'WhatsApp Nina Shah' }))
    expect(await screen.findByText(/WhatsApp opened for Nina Shah/)).toBeInTheDocument()
    expect(open).toHaveBeenCalledWith('about:blank', '_blank')
    expect(tab.location.href).toBe('https://wa.me/919800000001?text=Hi')
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    const body = JSON.parse(post[1]!.body as string)
    expect(body).toMatchObject({ channel: 'CANDIDATE', sendEmail: false, sendWhatsApp: true })
    expect(body.body).toMatch(/^Hi Nina, this is Priya from CodeWalnut\. We came across your profile for the Java Intern role/)
    open.mockRestore()
  })

  it('with the Business API, shows the message before sending, and needs a mobile number', async () => {
    const noPhone = { ...rows[1], applicationId: 'a4', candidateName: 'Ravi Kumar', hasPhone: false }
    const fetch = fakeFetch([
      ...signedIn,
      { path: '/whatsapp/status', body: { apiEnabled: true } },
      { path: '/workflow', body: { ...board, rows: [...rows, noPhone] } },
      { method: 'POST', path: '/applications/a2/messages', body: { id: 'm1', whatsapp: 'SENT', whatsappLink: null } },
    ])
    renderPage()
    expect(await screen.findByRole('button', { name: 'WhatsApp Ravi Kumar' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'WhatsApp Nina Shah' }))
    const box = screen.getByLabelText('WhatsApp message')
    await userEvent.clear(box)
    await userEvent.type(box, 'Hi Nina, are you free today?')
    expect(fetch.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Send on WhatsApp' }))
    expect(await screen.findByText('Sent to Nina Shah on WhatsApp.')).toBeInTheDocument()
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string).body).toBe('Hi Nina, are you free today?')
  })
})
