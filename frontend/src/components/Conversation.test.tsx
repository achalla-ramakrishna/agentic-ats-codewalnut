import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { Conversation } from './Conversation'

const me = { email: 'recruiter@codewalnut.test', name: 'Dev Recruiter' }

const fromCandidate = {
  id: 'm1',
  channel: 'CANDIDATE',
  authorType: 'CANDIDATE',
  authorEmail: 'asha@example.com',
  authorName: 'Asha Rao',
  subject: null,
  body: 'Is the role remote?',
  emailed: false,
  createdAt: '2026-09-29T05:00:00Z',
}

describe('Conversation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the candidate conversation and asks to connect Gmail before emailing', async () => {
    fakeFetch([
      { path: '/applications/a1/messages', body: [fromCandidate] },
      { path: '/google/status', body: { available: true, calendarConnected: false, mailConnected: false, redirectUri: 'x' } },
    ])
    render(
      <Conversation applicationId="a1" channel="CANDIDATE" candidateName="Asha Rao" candidateEmail="asha@example.com" jobTitle="React Intern" me={me} />,
    )

    expect(await screen.findByText('Is the role remote?')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Start from a template'), 'shortlisted')
    expect(screen.getByLabelText('Email subject')).toHaveValue("You're shortlisted – React Intern")
    expect(await screen.findByRole('button', { name: 'Connect Google (Calendar & Gmail)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send email' })).toBeDisabled()

    await userEvent.click(screen.getByLabelText(/Also email it to asha@example.com/))
    expect(screen.queryByRole('button', { name: 'Connect Google (Calendar & Gmail)' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send' })).toBeEnabled()
  })

  it('posts to the internal team chat without email', async () => {
    const fetch = fakeFetch([
      { path: '/applications/a1/messages', body: [] },
      {
        method: 'POST',
        path: '/applications/a1/messages',
        status: 201,
        body: { ...fromCandidate, id: 'm2', channel: 'TEAM', authorType: 'STAFF', authorEmail: me.email, body: 'Strong React' },
      },
    ])
    render(<Conversation applicationId="a1" channel="TEAM" candidateName="Asha Rao" candidateEmail="asha@example.com" jobTitle="React Intern" me={me} />)

    expect(await screen.findByText(/Only CodeWalnut staff see this/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Start from a template')).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Message to the team'), 'Strong React')
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))

    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ channel: 'TEAM', body: 'Strong React', sendEmail: false })
  })

  it('never offers email without an address', async () => {
    fakeFetch([
      { path: '/applications/a1/messages', body: [] },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
    ])
    render(<Conversation applicationId="a1" channel="CANDIDATE" candidateName="Asha Rao" candidateEmail={null} jobTitle="React Intern" me={me} />)

    expect(await screen.findByText(/No email address on file/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox')).not.toBeChecked()
    expect(screen.getByRole('checkbox')).toBeDisabled()
  })
})
