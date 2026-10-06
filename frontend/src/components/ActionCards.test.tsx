import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ProposedAction } from '../api/ask'
import { fakeFetch } from '../test/fakeFetch'
import { ActionCards } from './ActionCards'

const base = { applicationId: 'a1', jobId: 'j1', candidateName: 'Asha Rao', jobTitle: 'Java Intern', status: 'PENDING', result: null, doneBy: null, doneAt: null } as const
const move: ProposedAction = { ...base, id: 'x1', type: 'MOVE_STAGE', summary: 'Move Asha Rao (Java Intern): Screening → Shortlisted', params: { stage: 'SHORTLISTED' } }
const note: ProposedAction = { ...base, id: 'x2', type: 'ADD_NOTE', summary: 'Note on Asha Rao: prefers mornings', params: { note: 'prefers mornings' } }
const whatsapp: ProposedAction = {
  ...base,
  id: 'x3',
  type: 'MESSAGE',
  summary: 'WhatsApp to Asha Rao',
  params: { body: 'Hi Asha, are you free today? Priya, CodeWalnut', sendEmail: 'false', sendWhatsApp: 'true' },
}
const conversation = (actions: ProposedAction[]) => ({
  id: 'c1',
  title: 't',
  updatedAt: '',
  messages: [{ role: 'assistant', text: 'ok', at: '', actions }],
})

function renderCards(actions: ProposedAction[], onChange = vi.fn()) {
  render(
    <MemoryRouter>
      <ActionCards conversationId="c1" actions={actions} waApi={false} onChange={onChange} />
    </MemoryRouter>,
  )
  return onChange
}

describe('ActionCards (ASK-06…ASK-09)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('does all the non-WhatsApp actions one after another', async () => {
    const fetch = fakeFetch([
      { method: 'POST', path: '/ask/conversations/c1/actions/x1', body: { conversation: conversation([{ ...move, status: 'DONE' }, note]), whatsappLink: null } },
      { method: 'POST', path: '/ask/conversations/c1/actions/x2', body: { conversation: conversation([{ ...move, status: 'DONE' }, { ...note, status: 'DONE' }]), whatsappLink: null } },
    ])
    const onChange = renderCards([move, note])
    expect(screen.getByText('2 actions for you to confirm')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Do all 2' }))
    expect(onChange).toHaveBeenCalledTimes(2)
    const posts = fetch.mock.calls.filter(([, init]) => init?.method === 'POST')
    expect(posts.map(([url]) => String(url))).toEqual(['/api/v1/ask/conversations/c1/actions/x1', '/api/v1/ask/conversations/c1/actions/x2'])
    expect(JSON.parse(posts[0][1]!.body as string)).toMatchObject({ decision: 'do' })
  })

  it('lets you edit a WhatsApp message, then opens WhatsApp with it', async () => {
    const fetch = fakeFetch([
      { method: 'POST', path: '/ask/conversations/c1/actions/x3', body: { conversation: conversation([{ ...whatsapp, status: 'DONE' }]), whatsappLink: 'https://wa.me/91980?text=x' } },
    ])
    const tab = { location: { href: '' }, close: vi.fn() }
    const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    renderCards([whatsapp])
    expect(screen.queryByRole('button', { name: /Do all/ })).not.toBeInTheDocument()

    const box = screen.getByLabelText('Message to Asha Rao')
    await userEvent.clear(box)
    await userEvent.type(box, 'Hi Asha, edited. Priya')
    await userEvent.click(screen.getByRole('button', { name: 'Do it (opens WhatsApp)' }))
    expect(open).toHaveBeenCalledWith('about:blank', '_blank')
    await vi.waitFor(() => expect(tab.location.href).toBe('https://wa.me/91980?text=x'))
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toMatchObject({ decision: 'do', body: 'Hi Asha, edited. Priya' })
    open.mockRestore()
  })

  it('shows what happened once decided', () => {
    renderCards([{ ...move, status: 'FAILED', result: 'Moving to Rejected needs a reason' }, { ...note, status: 'SKIPPED', result: 'Skipped' }])
    expect(screen.getByText('Failed')).toBeInTheDocument()
    expect(screen.getByText('Moving to Rejected needs a reason')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Do it' })).not.toBeInTheDocument()
  })
})
