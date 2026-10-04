import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/types'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'

const recruiter: Me = {
  id: 'r1',
  email: 'recruiter@codewalnut.test',
  name: 'Dev Recruiter',
  roles: ['RECRUITER'],
  capabilities: ['MANAGE_JOBS', 'VIEW_INTERVIEWS', 'VIEW_CANDIDATES'],
  navigation: [
    { key: 'interviews', label: 'Interviews', path: '/interviews' },
    { key: 'tests', label: 'Tests', path: '/tests' },
  ],
}

const base = {
  jobId: 'j1',
  jobTitle: 'Java Developer',
  clientName: 'Acme',
  hasDescription: true,
  canGenerate: true,
  levels: [
    { id: 'JUNIOR', label: 'Junior (1–3 years)' },
    { id: 'SENIOR', label: 'Senior (5–8 years)' },
  ],
  roles: [
    { id: 'java-backend', label: 'Java backend developer' },
    { id: 'fullstack-java-react', label: 'Full-stack developer (Java + React)' },
  ],
}

const kit = {
  jobId: 'j1',
  jobTitle: 'Java Developer',
  clientName: 'Acme',
  generatedBy: 'recruiter@codewalnut.test',
  generatedAt: '2026-10-04T08:00:00Z',
  outdated: false,
  kit: {
    level: 'JUNIOR',
    levelLabel: 'Junior (1–3 years)',
    detectedLevel: 'JUNIOR',
    roleId: 'java-backend',
    roleName: 'Java backend developer',
    detectedRoleId: 'java-backend',
    skills: [
      { name: 'Java', area: 'Java', mentions: 2, mustHave: true },
      { name: 'Docker', area: 'DevOps & cloud', mentions: 1, mustHave: false },
    ],
    notes: [],
    test: {
      roleId: 'java-backend',
      roleName: 'Java backend developer',
      level: 'JUNIOR',
      levelLabel: 'Junior',
      years: '1–3 years',
      areas: ['Java', 'SQL'],
      preset: {
        id: 'java-backend-junior',
        name: 'Java backend developer — Junior',
        description: '30 questions in 60 minutes.',
        durationMinutes: 60,
        passPercent: 60,
        sections: [{ area: 'JAVA', section: 'PRACTICAL', easy: 3, medium: 4, hard: 1 }],
      },
    },
    rounds: [
      {
        name: 'Technical interview',
        minutes: 60,
        who: 'An engineer',
        purpose: 'Depth in the must-haves.',
        questions: [
          { topic: 'Java', question: 'Walk me through something you built with Java.', strong: 'Specific project.', redFlags: 'Only says "we".', source: 'job' },
        ],
        coding: [],
      },
      {
        name: 'Live coding',
        minutes: 40,
        who: 'An engineer',
        purpose: 'Share an editor.',
        questions: [],
        coding: [
          {
            id: 'two-sum', title: 'Two sum', difficulty: 'EASY', topic: 'Hashing', statement: 'Find two positions.', input: 'N and T', output: 'i j',
            sampleInput: '4 9\n2 7 11 15\n', sampleOutput: '0 1\n', approach: 'A hash map gives O(N).', lookFor: 'Asks about edge cases.',
          },
        ],
      },
    ],
    scorecard: [{ name: 'Java', guidance: 'Hands-on depth in Java.' }],
    hireBar: 'An average of 3 or more.',
    seed: 1,
  },
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/interview-kits/j1']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('InterviewKitPage (INT-28…INT-30)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('generates a kit, regenerates with a chosen level and creates the online test', async () => {
    const fetch = fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: recruiter },
      { path: '/jobs/j1/interview-kit', body: { ...base, kit: null } },
      { method: 'POST', path: '/jobs/j1/interview-kit', body: { ...base, kit } },
      { method: 'POST', path: '/question-bank/build', body: { summary: { id: 't9' }, questions: [] } },
      { path: '/assessments', body: [] },
    ])
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Generate interview kit' }))
    const skills = await screen.findByLabelText('Skills from the job description')
    expect(within(skills).getByText('Java')).toBeInTheDocument()
    expect(within(skills).getByText('Docker (nice to have)')).toBeInTheDocument()
    expect(screen.getByText(/Walk me through something you built with Java/)).toBeInTheDocument()
    expect(screen.getByText('Specific project.')).toBeInTheDocument()
    expect(screen.getByText(/Warm-up: Two sum/)).toBeInTheDocument()

    // Hide the answer guides before sharing a screen.
    await userEvent.click(screen.getByLabelText(/Show answer guides/))
    expect(screen.queryByText('Specific project.')).not.toBeInTheDocument()
    expect(screen.queryByText(/A hash map gives O\(N\)/)).not.toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Level'), 'SENIOR')
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate' }))
    const posts = fetch.mock.calls.filter(([url, init]) => String(url).includes('interview-kit') && init?.method === 'POST')
    expect(JSON.parse(String(posts[posts.length - 1][1]?.body))).toEqual({ level: 'SENIOR', roleId: 'java-backend' })

    await userEvent.click(await screen.findByRole('button', { name: 'Create this test' }))
    await vi.waitFor(() => expect(fetch.mock.calls.some(([url]) => String(url).includes('/question-bank/build'))).toBe(true))
    const build = fetch.mock.calls.find(([url]) => String(url).includes('/question-bank/build'))
    expect(JSON.parse(String(build?.[1]?.body))).toMatchObject({ area: 'JAVA', durationMinutes: 60, order: 'BY_SECTION' })
  })

  it('interviewers read the kit but cannot generate or create tests', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: { ...recruiter, capabilities: ['VIEW_INTERVIEWS'], navigation: [{ key: 'interviews', label: 'Interviews', path: '/interviews' }] } },
      { path: '/jobs/j1/interview-kit', body: { ...base, canGenerate: false, kit } },
    ])
    renderPage()
    expect(await screen.findByText(/Walk me through something you built with Java/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Regenerate' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create this test' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Open the opening' })).not.toBeInTheDocument()
  })
})
