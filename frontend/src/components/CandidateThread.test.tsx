import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { CandidateThread } from './CandidateThread'

describe('CandidateThread', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows messages from CodeWalnut and sends a reply', async () => {
    const fetch = fakeFetch([
      {
        path: '/candidate/applications/a1/messages',
        body: [{ fromMe: false, authorName: 'Priya, CodeWalnut', subject: "You're shortlisted", body: 'Congrats!', createdAt: '2026-09-29T05:00:00Z' }],
      },
      {
        method: 'POST',
        path: '/candidate/applications/a1/messages',
        status: 201,
        body: { fromMe: true, authorName: 'You', subject: null, body: 'Thank you!', createdAt: '2026-09-29T06:00:00Z' },
      },
    ])
    const onRead = vi.fn()
    render(<CandidateThread applicationId="a1" jobTitle="React Intern" onRead={onRead} />)

    expect(await screen.findByText('Congrats!')).toBeInTheDocument()
    expect(screen.getByText(/Priya, CodeWalnut/)).toBeInTheDocument()
    expect(onRead).toHaveBeenCalled()
    await userEvent.type(screen.getByLabelText('Your message'), 'Thank you!')
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))

    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ body: 'Thank you!' })
  })
})
