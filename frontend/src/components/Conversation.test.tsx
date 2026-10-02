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

    await userEvent.click(screen.getByLabelText(/Email to asha@example.com/))
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
    expect(JSON.parse(post[1]!.body as string)).toEqual({ channel: 'TEAM', body: 'Strong React', sendEmail: false, sendWhatsApp: false })
  })

  it('never offers email without an address', async () => {
    fakeFetch([
      { path: '/applications/a1/messages', body: [] },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
    ])
    render(<Conversation applicationId="a1" channel="CANDIDATE" candidateName="Asha Rao" candidateEmail={null} jobTitle="React Intern" me={me} />)

    const email = await screen.findByLabelText(/Email: no email address on file/)
    expect(email).not.toBeChecked()
    expect(email).toBeDisabled()
  })

  it('opens WhatsApp with the message ready when there is no Business API', async () => {
    const tab = { location: { href: '' }, close: vi.fn() }
    const open = vi.fn(() => tab)
    vi.stubGlobal('open', open)
    const fetch = fakeFetch([
      { path: '/applications/a1/messages', body: [] },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
      {
        method: 'POST',
        path: '/applications/a1/messages',
        status: 201,
        body: { ...fromCandidate, id: 'm3', authorType: 'STAFF', authorEmail: me.email, body: 'Can you send your Aadhaar card?', whatsapp: 'OPENED', whatsappLink: 'https://wa.me/919000000001?text=Can' },
      },
    ])
    render(
      <Conversation
        applicationId="a1"
        channel="CANDIDATE"
        candidateName="Asha Rao"
        candidateEmail="asha@example.com"
        candidatePhone="9000000001"
        jobTitle="React Intern"
        me={me}
      />,
    )

    await userEvent.click(await screen.findByLabelText(/Email to asha@example.com/))
    expect(screen.getByLabelText(/WhatsApp to 9000000001/)).toBeChecked()
    expect(await screen.findByText(/press Send there/)).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Message'), 'Can you send your Aadhaar card?')
    await userEvent.click(screen.getByRole('button', { name: 'Send WhatsApp' }))

    expect(await screen.findByText(/WhatsApp opened in a new tab/)).toBeInTheDocument()
    expect(open).toHaveBeenCalledWith('about:blank', '_blank')
    expect(tab.location.href).toBe('https://wa.me/919000000001?text=Can')
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toMatchObject({ sendEmail: false, sendWhatsApp: true })
  })
})
