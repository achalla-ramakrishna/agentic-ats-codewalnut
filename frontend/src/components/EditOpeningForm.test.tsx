import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Job } from '../api/tracker'
import { fakeFetch } from '../test/fakeFetch'
import { EditOpeningForm } from './EditOpeningForm'

const job = {
  id: 'j1',
  title: 'Java Dev',
  client: { id: 'c1', name: 'Acme', notes: null },
  hiringType: 'CLIENT_DEPLOYED',
  hiringTypeLabel: 'Client – CodeWalnut payroll',
  openings: 2,
  status: 'OPEN',
} as unknown as Job

const clients = [
  { id: 'c1', name: 'Acme', notes: null },
  { id: 'c2', name: 'Globex', notes: null },
]

describe('EditOpeningForm (TRK-15)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('renames the opening and moves it to another client', async () => {
    const fetch = fakeFetch([
      { path: '/clients', body: clients },
      { method: 'PATCH', path: '/jobs/j1', body: { ...job, title: 'Senior Java Developer' } },
    ])
    const onSaved = vi.fn()
    render(<EditOpeningForm job={job} onSaved={onSaved} onCancel={() => undefined} />)

    const name = screen.getByLabelText(/Name of the opening/)
    await userEvent.clear(name)
    await userEvent.type(name, 'Senior Java Developer')
    await screen.findByRole('option', { name: 'Globex' })
    await userEvent.selectOptions(screen.getByLabelText('Client'), 'c2')
    expect(screen.getByText(/no candidates in this opening are shared with Acme/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await vi.waitFor(() => expect(onSaved).toHaveBeenCalled())
    const patch = fetch.mock.calls.find(([, init]) => init?.method === 'PATCH')
    expect(JSON.parse(String(patch?.[1]?.body))).toEqual({
      title: 'Senior Java Developer',
      hiringType: 'CLIENT_DEPLOYED',
      clientId: 'c2',
      openings: 2,
    })
  })

  it('internal openings have no client, and the server’s reason is shown', async () => {
    const fetch = fakeFetch([
      { path: '/clients', body: clients },
      { method: 'PATCH', path: '/jobs/j1', status: 409, body: { error: 'Stop sharing 1 candidate with Acme first, then change the client.' } },
    ])
    render(<EditOpeningForm job={job} onSaved={() => undefined} onCancel={() => undefined} />)
    await userEvent.selectOptions(screen.getByLabelText('Hiring type'), 'INTERNAL')
    expect(screen.queryByLabelText('Client')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Stop sharing 1 candidate with Acme')
    const patch = fetch.mock.calls.find(([, init]) => init?.method === 'PATCH')
    expect(JSON.parse(String(patch?.[1]?.body))).not.toHaveProperty('clientId')
  })
})
