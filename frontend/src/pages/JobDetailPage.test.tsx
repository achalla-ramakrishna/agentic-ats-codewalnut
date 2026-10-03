import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ApplicationRow } from '../api/tracker'
import type { Me } from '../api/types'
import { App } from '../App'
import { matchesInsight, matchesSearch } from './JobDetailPage'
import type { InsightSummary } from '../api/insights'
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

const noInsights = {
  available: false,
  hasDescription: false,
  analyzed: 0,
  pending: 0,
  failed: 0,
  noResume: 0,
  notAnalyzed: 1,
  insights: [],
  contactNext: [],
  closestToSelection: [],
}

const insight: InsightSummary = {
  applicationId: 'a1',
  status: 'DONE',
  fitPercent: 50,
  headline: null,
  error: null,
  stale: false,
  met: 1,
  partial: 0,
  total: 2,
  skills: [],
  projects: 0,
  experienceMonths: 0,
  graduationYear: null,
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
      { path: '/jobs/j1/insights', body: noInsights },
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
      { path: '/jobs/j1/insights', body: noInsights },
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
      { path: '/jobs/j1/insights', body: noInsights },
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
      { path: '/jobs/j1/insights', body: noInsights },
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
      { path: '/jobs/j1/insights', body: noInsights },
      { path: '/jobs/j1', body: job },
    ])
    renderJob()

    await userEvent.type(await screen.findByLabelText('Search candidates in this opening'), 'nobody')
    expect(screen.getByText(/No candidates match “nobody”/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }))
    expect(screen.getByRole('button', { name: 'Asha Rao' })).toBeInTheDocument()
  })

  it('turns a typed instruction into AI suggestions to review', async () => {
    const fetchMock = fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: recruiter },
      { path: '/stages', body: stages },
      { path: '/jobs/j1/applications', body: [row] },
      { path: '/jobs/j1/insights', body: noInsights },
      { path: '/jobs/j1', body: job },
      { path: '/assistant/status', body: { available: true } },
      {
        method: 'POST',
        path: '/jobs/j1/assistant',
        body: {
          instruction: 'asha is shortlisted',
          summary: 'Move 1 candidate to Shortlisted.',
          aiGenerated: true,
          actions: [{ type: 'MOVE_STAGE', applicationId: 'a1', candidateName: 'Asha Rao', fromStage: 'INTERVIEWED', fromLabel: 'Interviewed', toStage: 'SHORTLISTED', toLabel: 'Shortlisted', note: null, needsReason: false }],
          unresolved: [],
          notes: [],
        },
      },
    ])
    renderJob()

    const search = await screen.findByLabelText('Search candidates in this opening')
    await userEvent.type(search, 'asha is shortlisted')
    await userEvent.click(await screen.findByRole('button', { name: '✨ Ask AI' }))

    const card = await screen.findByRole('region', { name: 'AI assistant suggestion' })
    expect(within(card).getByText(/Interviewed →/)).toBeInTheDocument()
    expect(search).toHaveValue('')
    const ask = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/jobs/j1/assistant'))!
    expect(JSON.parse(ask[1]!.body as string)).toEqual({ instruction: 'asha is shortlisted' })
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PATCH')).toBe(false)
  })

  it('shows AI match scores, résumé filters, sorting and suggestions', async () => {
    const ravi = { ...row, id: 'a2', candidateId: 'p2', name: 'Ravi Teja', email: 'ravi@example.com', phone: '9000000002', stage: 'SOURCED', stageLabel: 'Applied / Sourced' }
    const asha = { ...row, stage: 'SOURCED', stageLabel: 'Applied / Sourced' }
    fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: recruiter },
      { path: '/stages', body: stages },
      { path: '/jobs/j1/applications', body: [asha, ravi] },
      {
        path: '/jobs/j1/insights',
        body: {
          ...noInsights,
          available: true,
          hasDescription: true,
          analyzed: 2,
          notAnalyzed: 0,
          insights: [
            { ...insight, applicationId: 'a1', fitPercent: 40, projects: 0, skills: ['Python'] },
            { ...insight, applicationId: 'a2', fitPercent: 90, projects: 2, skills: ['Java', 'Spring Boot'] },
          ],
          contactNext: [{ applicationId: 'a2', candidateName: 'Ravi Teja', stageLabel: 'Applied / Sourced', fitPercent: 90, reason: 'Meets 3 of 3 requirements' }],
        },
      },
      { path: '/jobs/j1', body: { ...job, total: 2 } },
    ])
    renderJob()

    const panel = await screen.findByRole('region', { name: 'AI suggestions' })
    expect(within(panel).getByText('Meets 3 of 3 requirements')).toBeInTheDocument()
    expect(await screen.findByText('90%')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Upload résumés' })).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Sort candidates'), 'match')
    const names = () => screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('button')[0].textContent)
    expect(names()).toEqual(['Ravi Teja', 'Asha Rao'])

    await userEvent.click(screen.getByRole('button', { name: /Has projects/ }))
    expect(names()).toEqual(['Ravi Teja'])
    await userEvent.click(screen.getByRole('button', { name: /Has projects/ }))
    await userEvent.selectOptions(screen.getByLabelText('Filter by skill'), 'Python')
    expect(names()).toEqual(['Asha Rao'])
  })

  it('answers a question with the candidates it points to', async () => {
    const fetchMock = fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: recruiter },
      { path: '/stages', body: stages },
      { path: '/jobs/j1/applications', body: [row] },
      { path: '/jobs/j1/insights', body: noInsights },
      { path: '/jobs/j1', body: job },
      { path: '/assistant/status', body: { available: true } },
      { path: '/applications/a1/insight', body: { applicationId: 'a1', status: 'NONE' } },
      {
        method: 'POST',
        path: '/jobs/j1/assistant',
        body: {
          instruction: 'who has worked on spring boot projects?',
          summary: 'Answered.',
          aiGenerated: true,
          actions: [],
          unresolved: [],
          notes: [],
          answer: 'Asha built two Spring Boot projects.',
          matches: [{ applicationId: 'a1', name: 'Asha Rao', stageLabel: 'Interviewed', fitPercent: 80, reason: 'Two Spring Boot projects' }],
        },
      },
    ])
    renderJob()

    await userEvent.type(await screen.findByLabelText('Search candidates in this opening'), 'who has worked on spring boot projects?')
    await userEvent.click(await screen.findByRole('button', { name: '✨ Ask AI' }))

    const card = await screen.findByRole('region', { name: 'AI assistant suggestion' })
    expect(within(card).getByText('Answered by AI')).toBeInTheDocument()
    expect(within(card).getByText('Asha built two Spring Boot projects.')).toBeInTheDocument()
    expect(within(card).getByText(/80% match · Two Spring Boot projects/)).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: /Apply/ })).not.toBeInTheDocument()
    await userEvent.click(within(card).getByRole('button', { name: 'Asha Rao' }))
    // The candidate drawer opens and loads the AI reading of their résumé.
    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/applications/a1/insight'))).toBe(true),
    )
  })
})

describe('matchesInsight', () => {
  it('needs a read résumé and every chosen filter to match', () => {
    const i = { ...insight, fitPercent: 80, projects: 1, experienceMonths: 0, skills: ['React'], graduationYear: 2026 }
    expect(matchesInsight(undefined, [], '', '')).toBe(true)
    expect(matchesInsight(undefined, ['strong'], '', '')).toBe(false)
    expect(matchesInsight(i, ['strong', 'projects', 'fresher'], 'react', '2026')).toBe(true)
    expect(matchesInsight(i, ['experience'], '', '')).toBe(false)
    expect(matchesInsight(i, [], 'Java', '')).toBe(false)
    expect(matchesInsight({ ...i, status: 'PENDING' }, ['strong'], '', '')).toBe(false)
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
