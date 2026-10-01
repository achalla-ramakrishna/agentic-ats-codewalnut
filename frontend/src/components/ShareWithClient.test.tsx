import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { ShareWithClient } from './ShareWithClient'

const doc = (id: string, kind: string, fileName: string, uploadedAt: string) => ({
  id,
  candidateId: 'p1',
  kind,
  fileName,
  contentType: 'application/pdf',
  sizeBytes: 10,
  uploadedBy: 'admin@codewalnut.com',
  uploadedAt,
})

describe('ShareWithClient', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shares the ticked parts and only the newest version of each document', async () => {
    const shared = {
      applicationId: 'a1',
      clientName: 'Acme',
      active: true,
      includeContact: true,
      includeProfile: false,
      documentIds: ['new-cw'],
      note: null,
      sharedBy: 'admin@codewalnut.com',
      sharedAt: '2026-10-01T05:00:00Z',
      revokedAt: null,
      lastViewedAt: null,
    }
    const fetch = fakeFetch([
      { path: '/applications/a1/client-share', body: { ...shared, active: false, sharedAt: null, documentIds: [] } },
      {
        path: '/candidates/p1/documents',
        body: [
          doc('new-cw', 'CODEWALNUT_RESUME', 'cw-v2.pdf', '2026-10-01T00:00:00Z'),
          doc('aadhaar', 'AADHAAR', 'aadhaar.png', '2026-09-30T00:00:00Z'),
          doc('old-cw', 'CODEWALNUT_RESUME', 'cw-v1.pdf', '2026-09-01T00:00:00Z'),
        ],
      },
      { method: 'PUT', path: '/applications/a1/client-share', body: shared },
    ])
    render(<ShareWithClient applicationId="a1" candidateId="p1" clientName="Acme" canShare onChanged={() => undefined} />)

    expect(await screen.findByText('Not shared')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Share with Acme…' }))
    expect(screen.queryByText(/cw-v1.pdf/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByLabelText(/Profile \(date of birth/))
    await userEvent.click(screen.getByLabelText(/Aadhaar card/))
    await userEvent.click(screen.getByRole('button', { name: 'Share with Acme' }))

    const put = fetch.mock.calls.find(([, init]) => init?.method === 'PUT')!
    expect(JSON.parse(put[1]!.body as string)).toEqual({ includeContact: true, includeProfile: false, documentIds: ['new-cw'], note: '' })
    expect(await screen.findByText(/Acme sees: name, opening, stage, contact details, CodeWalnut résumé/)).toBeInTheDocument()
    expect(screen.getByText(/not viewed yet/)).toBeInTheDocument()
  })

  it('is read-only without sharing rights', async () => {
    fakeFetch([
      { path: '/applications/a1/client-share', body: { applicationId: 'a1', clientName: 'Acme', active: false, documentIds: [] } },
      { path: '/candidates/p1/documents', body: [] },
    ])
    render(<ShareWithClient applicationId="a1" candidateId="p1" clientName="Acme" canShare={false} onChanged={() => undefined} />)
    expect(await screen.findByText('Not shared')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Share with Acme/ })).not.toBeInTheDocument()
  })
})
