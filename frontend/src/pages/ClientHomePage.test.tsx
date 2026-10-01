import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'

const shared = {
  applicationId: 'a1',
  name: 'Asha Rao',
  jobTitle: 'Software Engineering Intern',
  stageLabel: 'Joined',
  sharedAt: '2026-10-01T05:00:00Z',
  note: 'For background verification',
  email: null,
  phone: null,
  profile: {
    dateOfBirth: '2003-01-02',
    currentAddress: '12 Fake Street',
    permanentAddress: null,
    college: 'Fake Institute',
    degree: 'B.Tech',
    graduationYear: 2025,
    linkedinUrl: null,
    emergencyContact: null,
  },
  documents: [{ id: 'd1', kind: 'AADHAAR', label: 'Aadhaar card (masked)', fileName: 'aadhaar.png', uploadedAt: '2026-10-01T04:00:00Z' }],
}

describe('Client home (a client contact signed in)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows only what CodeWalnut shared, and lets the client ask a question', async () => {
    const fetch = fakeFetch([
      { path: '/auth/session', body: { type: 'CLIENT' } },
      { path: '/client/me', body: { email: 'hm@acme.example', name: 'Hema Manager', clientName: 'Acme' } },
      { path: '/client/candidates', body: [shared] },
      { path: '/client/applications/a1/messages', body: [] },
      {
        method: 'POST',
        path: '/client/applications/a1/messages',
        status: 201,
        body: { fromMe: true, authorName: 'You', subject: null, body: 'Please share the PAN too', createdAt: '2026-10-01T06:00:00Z' },
      },
    ])
    render(
      <MemoryRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    )

    const card = await screen.findByRole('region', { name: 'Candidate Asha Rao' })
    expect(screen.getByText('CodeWalnut × Acme')).toBeInTheDocument()
    expect(within(card).getByText('Joined')).toBeInTheDocument()
    expect(within(card).getByText('Fake Institute')).toBeInTheDocument()
    expect(within(card).queryByText('Contact')).not.toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'Download' })).toHaveAttribute('href', '/api/v1/client/documents/d1')
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()

    await userEvent.click(within(card).getByRole('button', { name: 'Message CodeWalnut about this candidate' }))
    await userEvent.type(await within(card).findByLabelText('Your message'), 'Please share the PAN too')
    await userEvent.click(within(card).getByRole('button', { name: 'Send' }))
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ body: 'Please share the PAN too' })
  })
})
