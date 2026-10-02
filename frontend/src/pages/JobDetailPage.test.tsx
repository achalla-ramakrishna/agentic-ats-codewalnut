import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ApplicationRow } from '../api/tracker'
import type { Me } from '../api/types'
import { App } from '../App'
import { matchesSearch } from './JobDetailPage'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'

const recruiter: Me = {
  id: 'r1',
  email: 'recruiter@codewalnut.test',
  name: 'Dev Recruiter',
  roles: ['RECRUITER'],
  capabilities: ['VIEW_JOBS', 'MANAGE_JOBS', 'VIEW_CANDIDATES', 'MESSAGE_CANDIDATES'],
  navigation: [{ key: 'jobs', label: 'Openings', path: '/jobs' }],
}

const stages = [
  { key: 'SOURCED', label: 'Applied / Sourced', exit: false },
  { key: 'INTERVIEWED', label: 'Interviewed', exit: false },
  { key: 'SUBMITTED_TO_CLIENT', label: 'Submitted to client', exit: false },
  { key: 'REJECTED', label: 'Rejected', exit: true },
]

const job = {
  id: 'j1',
  title: 'Acme – Interns',
  client: { id: 'c1', name: 'Acme', notes: null },
  hiringType: 'CLIENT_DEPLOYED',
  hiringTypeLabel: 'Client – CodeWalnut payroll',
  openings: 10,
  status: 'OPEN',
  description: null,
  createdAt: '2026-09-28T00:00:00Z',
  stageCounts: { INTERVIEWED: 1 },
  total: 1,
}

const row = {
  id: 'a1',
  jobId: 'j1',
  jobTitle: 'Acme – Interns',
  clientName: 'Acme',
  candidateId: 'p1',
  name: 'Asha Rao',
  email: 'asha@example.com',
  phone: '9000000001',
  stage: 'INTERVIEWED',
  stageLabel: 'Interviewed',
  updatedAt: '2026-09-28T00:00:00Z',
  lastNote: null,
  documents: ['ORIGINAL_RESUME'],
}

function renderJob() {
  return render(
    <MemoryRouter initialEntries={['/jobs/j1']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('JobDetailPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the pipeline with stage counts and résumé markers', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: recruiter },
      { path: '/stages', body: stages },
      { path: '/jobs/j1/applications', body: [row] },
      { path: '/jobs/j1', body: job },
    ])
    renderJob()

    expect(await screen.findByRole('heading', { name: 'Acme – Interns' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Asha Rao' })).toBeInTheDocument()
    expect(screen.getByTitle('Original résumé')).toHaveClass('has')
    expect(screen.getByTitle('CodeWalnut résumé')).not.toHaveClass('has')
    expect(screen.getByRole('button', { name: /Interviewed 1/ })).toBeInTheDocument()
  })

  it('previews a pasted table before importing', async () => {
    const fetchMock = fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: recruiter },
      { path: '/stages', body: stages },
      { path: '/jobs/j1/applications', body: [] },
      {
        method: 'POST',
        path: '/jobs/j1/applications/import',
        body: {
          dryRun: true,
          added: 1,
          skipped: 1,
          rows: [
            { line: 2, name: 'Asha Rao', email: 'asha@example.com', phone: '9000000001', outcome: 'NEW', issues: [] },
            { line: 3, name: 'Kavya', email: null, phone: null, outcome: 'ALREADY_IN_OPENING', issues: ['No phone'] },
          ],
        },
      },
      { path: '/jobs/j1', body: { ...job, stageCounts: {}, total: 0 } },
    ])
    renderJob()

    await userEvent.click(await screen.findByRole('button', { name: 'Import from spreadsheet' }))
    await userEvent.type(screen.getByLabelText('Pasted rows'), 'name\temail\nAsha Rao\tasha@example.com')
    await userEvent.click(screen.getByRole('button', { name: 'Preview' }))

    const table = await screen.findByRole('table')
    expect(within(table).getByText('Will be added')).toBeInTheDocument()
    expect(within(table).getByText('No phone')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import 1' })).toBeEnabled()

    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/import'))!
    expect(JSON.parse(call[1]!.body as string)).toMatchObject({ stage: 'SOURCED', dryRun: true })
  })

  it('offers to email the candidate after a stage change, with the matching template', async () => {
    vi.stubGlobal('prompt', vi.fn(() => 'Not the right fit for this role'))
    const tab = { location: { href: '' }, close: vi.fn() }
    vi.stubGlobal('open', vi.fn(() => tab))
    const fetchMock = fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: recruiter },
      { path: '/stages', body: stages },
      { path: '/jobs/j1/applications', body: [row] },
      { path: '/jobs/j1', body: job },
      { method: 'PATCH', path: '/applications/a1/stage', body: { ...row, stage: 'REJECTED', stageLabel: 'Rejected' } },
      { path: '/applications/a1/history', body: [] },
      { path: '/candidates/p1/documents', body: [] },
      { path: '/applications/a1/messages', body: [] },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
      {
        method: 'POST',
        path: '/applications/a1/messages',
        status: 201,
        body: { id: 'm1', channel: 'CANDIDATE', authorType: 'STAFF', authorEmail: recruiter.email, authorName: 'Dev Recruiter', subject: 's', body: 'b', emailed: true, createdAt: '2026-09-29T00:00:00Z' },
      },
    ])
    renderJob()

    await userEvent.selectOptions(await screen.findByLabelText('Stage for Asha Rao'), 'REJECTED')
    expect(await screen.findByText(/Let them know\?/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Email Asha' }))

    const drawer = await screen.findByRole('complementary', { name: 'Candidate Asha Rao' })
    expect(within(drawer).getByRole('tab', { name: 'Chat with candidate' })).toHaveAttribute('aria-selected', 'true')
    expect(within(drawer).getByLabelText('Email subject')).toHaveValue('Your application for Acme – Interns at CodeWalnut')
    const message = within(drawer).getByLabelText('Message') as HTMLTextAreaElement
    expect(message.value).toContain('Hi Asha,')
    expect(message.value).toContain('not to move forward')
    expect(message.value).toContain('Dev Recruiter')

    await userEvent.click(await within(drawer).findByRole('button', { name: 'Send email & WhatsApp' }))
    expect(await within(drawer).findByText('Emailed to asha@example.com from your Gmail.')).toBeInTheDocument()
    expect(tab.close).toHaveBeenCalled()
    const post = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/messages') && init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toMatchObject({ channel: 'CANDIDATE', sendEmail: true, sendWhatsApp: true })
  })

  it('finds a candidate by name, email or phone without scrolling', async () => {
    const more = [
      row,
      { ...row, id: 'a2', candidateId: 'p2', name: 'Kiran Kumar', email: 'kiran@example.com', phone: '9876543210', stage: 'REJECTED', stageLabel: 'Rejected' },
      { ...row, id: 'a3', candidateId: 'p3', name: 'Meera Iyer', email: 'meera@example.com', phone: '9123456780' },
    ]
    fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: recruiter },
      { path: '/stages', body: stages },
      { path: '/jobs/j1/applications', body: more },
      { path: '/jobs/j1', body: { ...job, total: 3 } },
      { path: '/applications/a3/history', body: [] },
      { path: '/candidates/p3/profile', body: { id: 'p3', name: 'Meera Iyer', email: 'meera@example.com', phone: '9123456780' } },
      { path: '/candidates/p3/documents', body: [] },
      { path: '/candidates/p3/document-requests', body: [] },
      { path: '/applications/a3/client-share', body: { applicationId: 'a3', clientName: 'Acme', active: false, documentIds: [] } },
      { path: '/applications/a3/interviews', body: [] },
    ])
    renderJob()

    const search = await screen.findByLabelText('Search candidates in this opening')
    await screen.findByRole('button', { name: 'Kiran Kumar' })
    await userEvent.type(search, 'kumar')
    expect(screen.getByRole('button', { name: 'Kiran Kumar' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Asha Rao' })).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Showing 1 of 3')

    await userEvent.clear(search)
    await userEvent.type(search, '91234')
    expect(screen.getByRole('button', { name: 'Meera Iyer' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Kiran Kumar' })).not.toBeInTheDocument()
    await userEvent.type(search, '{Enter}')
    expect(await screen.findByRole('complementary', { name: 'Candidate Meera Iyer' })).toBeInTheDocument()
  })

  it('combines search with the stage filter and offers to clear', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: recruiter },
      { path: '/stages', body: stages },
      { path: '/jobs/j1/applications', body: [row] },
      { path: '/jobs/j1', body: job },
    ])
    renderJob()

    await userEvent.type(await screen.findByLabelText('Search candidates in this opening'), 'nobody')
    expect(screen.getByText(/No candidates match “nobody”/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }))
    expect(screen.getByRole('button', { name: 'Asha Rao' })).toBeInTheDocument()
  })
})

describe('matchesSearch', () => {
  const r = { ...row, name: 'Asha Rao', email: 'asha.rao@example.com', phone: '+919000000001' } as unknown as ApplicationRow
  it('matches any part of the name or email, every word must match', () => {
    expect(matchesSearch(r, 'asha')).toBe(true)
    expect(matchesSearch(r, 'RAO asha')).toBe(true)
    expect(matchesSearch(r, 'example.com')).toBe(true)
    expect(matchesSearch(r, 'asha kumar')).toBe(false)
  })
  it('matches phone digits with or without +91 and spacing', () => {
    expect(matchesSearch(r, '9000000001')).toBe(true)
    expect(matchesSearch(r, '+91 9000000001')).toBe(true)
    expect(matchesSearch(r, '919000000001')).toBe(true)
    expect(matchesSearch(r, '0001')).toBe(true)
    expect(matchesSearch(r, '12')).toBe(false)
  })
  it('an empty search matches everyone', () => {
    expect(matchesSearch(r, '   ')).toBe(true)
  })
})
