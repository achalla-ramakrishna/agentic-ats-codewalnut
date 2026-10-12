import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'

const page = {
  firstName: 'Asha',
  jobTitle: 'Senior Java Developer',
  timeZone: 'Asia/Kolkata',
  durationMinutes: 30,
  booked: null,
  scheduled: false,
  slots: [
    { id: 's1', startAt: '2026-10-20T04:30:00Z', endAt: '2026-10-20T05:00:00Z' },
    { id: 's2', startAt: '2026-10-20T05:00:00Z', endAt: '2026-10-20T05:30:00Z' },
  ],
}

describe('Candidate booking page (PIPE-24)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lets a signed-out candidate pick a time with their link', async () => {
    const fetchMock = fakeFetch([
      { path: '/auth/session', status: 401, body: {} },
      { path: '/public/booking/tok123', method: 'GET', body: page },
      { path: '/public/booking/tok123', method: 'POST', body: { ...page, booked: page.slots[1], slots: [page.slots[0]] } },
    ])
    render(
      <MemoryRouter initialEntries={['/book/tok123']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Hi Asha, pick a time' })).toBeInTheDocument()
    expect(screen.getByText(/Senior Java Developer/)).toBeInTheDocument()
    const times = screen.getAllByRole('button', { pressed: false })
    await userEvent.click(times[1])
    await userEvent.click(screen.getByRole('button', { name: /^Book / }))

    expect(await screen.findByText(/^Booked: /)).toBeInTheDocument()
    expect(screen.getByText(/calendar invitation with the Google Meet link by email shortly/)).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ slotId: 's2' })
  })
})
