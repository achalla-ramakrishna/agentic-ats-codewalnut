import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { CodingRoomPanel } from './CodingRoomPanel'

const problem = {
  id: 'two-sum',
  title: 'Two sum',
  statement: 'Print the indexes of two numbers that add up to the target.',
  inputFormat: null,
  outputFormat: null,
  samples: [{ input: '4 9\n2 7 11 15', output: '0 1' }],
  languages: ['java', 'python'],
  starter: { java: 'public class Main {}', python: '' },
}

const room = {
  id: 'r1',
  interviewId: 'i1',
  candidateName: 'Asha Rao',
  linkPath: '/coding/abc123',
  status: 'OPEN',
  problem,
  language: 'python',
  code: 'print("0 1")',
  codeUpdatedAt: new Date().toISOString(),
  candidateSeenAt: new Date().toISOString(),
  lastRun: null,
  runCount: 0,
  history: [],
  notes: null,
  canManage: true,
  endedAt: null,
}

const kit = {
  jobId: 'j1',
  kit: { kit: { rounds: [{ coding: [{ id: 'kit-problem', title: 'Balanced brackets', difficulty: 'EASY' }] }] } },
}

describe('CodingRoomPanel (INT-36, INT-38)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('opens a room with a kit problem, then shows the link and the candidate’s code', async () => {
    const fetch = fakeFetch([
      { path: '/interviews/i1/coding-room', body: { room: null, canManage: true, runnerAvailable: true, candidateHasEmail: true } },
      { method: 'POST', path: '/interviews/i1/coding-room', body: { room, canManage: true, runnerAvailable: true, candidateHasEmail: true } },
      { path: '/coding-problems', body: [{ id: 'two-sum', title: 'Two sum', difficulty: 'EASY', topic: 'Arrays' }] },
      { path: '/jobs/j1/interview-kit', body: kit },
    ])
    render(<CodingRoomPanel interviewId="i1" jobId="j1" cancelled={false} />)

    const select = await screen.findByLabelText('Problem')
    expect(await screen.findByRole('option', { name: 'Balanced brackets (easy)' })).toBeInTheDocument()
    expect(select).toHaveValue('kit-problem')
    await userEvent.selectOptions(select, 'two-sum')
    await userEvent.click(screen.getByRole('button', { name: 'Open coding room' }))

    expect(await screen.findByLabelText('Candidate link')).toHaveValue(`${window.location.origin}/coding/abc123`)
    expect(screen.getByText('They have the room open.')).toBeInTheDocument()
    expect(screen.getByLabelText("Candidate's code")).toHaveValue('print("0 1")')
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({ problemId: 'two-sum' })
  })

  it('takes a typed problem', async () => {
    const fetch = fakeFetch([
      { path: '/interviews/i1/coding-room', body: { room: null, canManage: true, runnerAvailable: false, candidateHasEmail: true } },
      { method: 'POST', path: '/interviews/i1/coding-room', body: { room, canManage: true, runnerAvailable: false, candidateHasEmail: true } },
      { path: '/coding-problems', body: [] },
      { path: '/jobs/j1/interview-kit', status: 403, body: { error: 'Not allowed' } },
    ])
    render(<CodingRoomPanel interviewId="i1" jobId="j1" cancelled={false} />)

    expect(await screen.findByText(/code runner isn.t set up/)).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Problem'), '__own__')
    await userEvent.type(screen.getByLabelText('Problem title'), 'Reverse words')
    await userEvent.type(screen.getByLabelText('Problem statement'), 'Reverse the words in a line.')
    await userEvent.click(screen.getByRole('button', { name: 'Open coding room' }))

    await screen.findByLabelText('Candidate link')
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({ title: 'Reverse words', statement: 'Reverse the words in a line.' })
  })

  it('asks for the candidate’s email before a room can open', async () => {
    fakeFetch([{ path: '/interviews/i1/coding-room', body: { room: null, canManage: true, runnerAvailable: true, candidateHasEmail: false } }])
    render(<CodingRoomPanel interviewId="i1" jobId="j1" cancelled={false} />)
    expect(await screen.findByText(/Add the candidate.s email first/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Open coding room' })).not.toBeInTheDocument()
  })
})
