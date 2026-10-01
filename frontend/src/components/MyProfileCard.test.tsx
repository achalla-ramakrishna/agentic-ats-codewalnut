import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { MyProfileCard } from './MyProfileCard'

const profile = {
  id: 'p1',
  name: 'Asha Rao',
  email: 'asha@example.com',
  phone: '9000000001',
  dateOfBirth: null,
  currentAddress: null,
  permanentAddress: null,
  college: null,
  degree: null,
  graduationYear: null,
  linkedinUrl: null,
  emergencyContact: null,
  profileUpdatedAt: null,
}

describe('MyProfileCard', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows requested documents first, uploads them and saves only changed fields', async () => {
    const fetch = fakeFetch([
      { path: '/candidate/profile', body: profile },
      {
        path: '/candidate/documents',
        body: { documents: [], requested: [{ id: 'r1', kind: 'AADHAAR', label: 'Aadhaar card (masked)', requestedBy: 'x', requestedAt: '2026-10-01T00:00:00Z', fulfilledAt: null }] },
      },
      { method: 'POST', path: '/candidate/documents', status: 201, body: { id: 'd1', kind: 'AADHAAR', label: 'Aadhaar card (masked)', fileName: 'a.png', uploadedAt: '2026-10-01T00:00:00Z' } },
      { method: 'PATCH', path: '/candidate/profile', body: { ...profile, college: 'Fake Institute' } },
    ])
    render(<MyProfileCard />)

    expect(await screen.findByText(/CodeWalnut has asked you to upload/)).toBeInTheDocument()
    expect(screen.getAllByText('Requested')).toHaveLength(1)
    await userEvent.upload(screen.getByLabelText('Upload Aadhaar card (masked)'), new File(['x'], 'a.png', { type: 'image/png' }))
    expect(await screen.findByText('Aadhaar card (masked) uploaded. Thank you!')).toBeInTheDocument()
    const upload = fetch.mock.calls.find(([url, init]) => String(url).endsWith('/candidate/documents') && init?.method === 'POST')!
    expect((upload[1]!.body as FormData).get('kind')).toBe('AADHAAR')

    await userEvent.type(screen.getByLabelText('College / university'), 'Fake Institute')
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }))
    const patch = fetch.mock.calls.find(([, init]) => init?.method === 'PATCH')!
    expect(JSON.parse(patch[1]!.body as string)).toEqual({ college: 'Fake Institute' })
  })

  it('stays hidden for someone who has not applied yet', async () => {
    fakeFetch([{ path: '/candidate/profile', status: 404, body: { error: 'No application yet' } }])
    const { container } = render(<MyProfileCard />)
    await new Promise((r) => setTimeout(r, 20))
    expect(container).toBeEmptyDOMElement()
  })
})
