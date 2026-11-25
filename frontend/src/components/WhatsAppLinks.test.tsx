import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { WhatsAppLinks } from './WhatsAppLinks'

const people = [
  { inviteId: 'i1', name: 'Asha Rao' },
  { inviteId: 'i2', name: 'Ravi Kumar' },
]

describe('WhatsApp the AI interview link (ASMT-43)', () => {
  afterEach(() => vi.restoreAllMocks())

  it('opens WhatsApp with the message typed in for each person, when there is no Business API', async () => {
    fakeFetch([
      { path: '/whatsapp/status', body: { apiEnabled: false, repliesEnabled: false } },
      { path: '/tests/i1/whatsapp', method: 'POST', body: { invite: {}, message: { whatsappLink: 'https://wa.me/919876543210?text=Hi' } } },
      { path: '/tests/i2/whatsapp', method: 'POST', status: 400, body: { error: "phone: add the candidate's mobile number first" } },
    ])
    const win = { location: { href: '' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(win as unknown as Window)
    render(<WhatsAppLinks people={people} onClose={() => undefined} />)
    const buttons = await screen.findAllByRole('button', { name: '💬 WhatsApp' })
    await vi.waitFor(() => expect(buttons[0]).toBeEnabled())
    await userEvent.click(buttons[0])
    await vi.waitFor(() => expect(win.location.href).toBe('https://wa.me/919876543210?text=Hi'))
    expect(screen.getByRole('button', { name: '✓ Again' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '💬 WhatsApp' }))
    expect(await screen.findByText(/mobile number/)).toBeInTheDocument()
    expect(win.close).toHaveBeenCalled()
    expect(screen.getByText('💬 WhatsApp the link (1 of 2 done)')).toBeInTheDocument()
  })

  it('sends to everyone in one click with the Business API', async () => {
    const fetchMock = fakeFetch([
      { path: '/whatsapp/status', body: { apiEnabled: true, repliesEnabled: false } },
      { path: '/tests/i', method: 'POST', body: { invite: {}, message: { whatsappLink: null } } },
    ])
    const open = vi.spyOn(window, 'open')
    render(<WhatsAppLinks people={people} onClose={() => undefined} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Send to all 2' }))
    await vi.waitFor(() => expect(screen.getByText('💬 WhatsApp the link (2 of 2 done)')).toBeInTheDocument())
    expect(open).not.toHaveBeenCalled()
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/whatsapp'))).toHaveLength(2)
  })
})
