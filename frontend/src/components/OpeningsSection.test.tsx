import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { OpeningsSection } from './OpeningsSection'

const opening = (o: Record<string, unknown>) => ({
  jobStatus: 'OPEN',
  stage: 'INTERVIEWED',
  stageLabel: 'Interviewed',
  current: false,
  addedAt: '2026-10-01T00:00:00Z',
  ...o,
})
const job = (id: string, title: string, client: string | null, status = 'OPEN') => ({
  id,
  title,
  client: client ? { id: `c-${client}`, name: client } : null,
  status,
})

describe('OpeningsSection (PIPE-13, PIPE-14)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists the candidate’s openings and adds them to another client’s opening', async () => {
    const fetch = fakeFetch([
      { path: '/applications/a1/openings', body: [opening({ applicationId: 'a1', jobId: 'j1', jobTitle: 'Java Developer', clientName: 'Acme', current: true })] },
      {
        path: '/jobs',
        body: [job('j1', 'Java Developer', 'Acme'), job('j2', 'Spring Boot Developer', 'Globex'), job('j3', 'Old role', 'Globex', 'CLOSED'), job('j4', 'Intern', null)],
      },
      {
        method: 'POST',
        path: '/applications/a1/openings',
        status: 201,
        body: { id: 'a2', jobId: 'j2', jobTitle: 'Spring Boot Developer', clientName: 'Globex', stage: 'SCREENING' },
      },
    ])
    const onAdded = vi.fn()
    render(
      <MemoryRouter>
        <OpeningsSection applicationId="a1" candidateName="Asha Rao" canEdit canOpenJobs onAdded={onAdded} />
      </MemoryRouter>,
    )

    const list = await screen.findByRole('list', { name: 'Openings for this candidate' })
    expect(within(list).getByText('Java Developer')).toBeInTheDocument()
    expect(within(list).getByText('This one')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Add to another opening' }))
    const select = await screen.findByLabelText('Opening')
    await screen.findByRole('option', { name: 'Spring Boot Developer' })
    // Closed openings and ones the candidate is already in aren't offered.
    expect(screen.queryByRole('option', { name: 'Old role' })).not.toBeInTheDocument()
    expect(within(select).queryByRole('option', { name: 'Java Developer' })).not.toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'CodeWalnut (internal)' })).toBeInTheDocument()

    await userEvent.selectOptions(select, 'j2')
    await userEvent.selectOptions(screen.getByLabelText('Starting stage'), 'SCREENING').catch(() => undefined)
    await userEvent.type(screen.getByLabelText(/Why they fit/), 'Strong Spring skills')
    await userEvent.click(screen.getByRole('button', { name: 'Add to opening' }))

    expect(await screen.findByText(/Asha Rao added to Spring Boot Developer \(Globex\)/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open it' })).toHaveAttribute('href', '/jobs/j2?candidate=a2')
    expect(onAdded).toHaveBeenCalled()
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(String(post?.[1]?.body))).toMatchObject({ jobId: 'j2', note: 'Strong Spring skills' })
  })

  it('offers no button to people who can’t manage openings', async () => {
    fakeFetch([{ path: '/applications/a1/openings', body: [opening({ applicationId: 'a1', jobId: 'j1', jobTitle: 'Java Developer', clientName: 'Acme', current: true })] }])
    render(
      <MemoryRouter>
        <OpeningsSection applicationId="a1" candidateName="Asha Rao" canEdit={false} canOpenJobs onAdded={() => undefined} />
      </MemoryRouter>,
    )
    await screen.findByText('Java Developer')
    expect(screen.queryByRole('button', { name: 'Add to another opening' })).not.toBeInTheDocument()
  })
})
