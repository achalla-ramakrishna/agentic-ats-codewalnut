import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Markdown } from '../components/Markdown'
import { fakeFetch } from '../test/fakeFetch'
import { AskPage } from './AskPage'

const answer =
  '**2 shortlisted** for Java Intern:\n\n- [Asha Rao](/jobs/j1?candidate=a1) — Shortlisted\n- [Ravi K](/jobs/j1?candidate=a2) — Shortlisted\n\n| Name | Fit |\n|---|---|\n| Asha Rao | 82% |'
const chat = {
  id: 'c1',
  title: 'Who is shortlisted?',
  updatedAt: '2026-10-06T10:00:00Z',
  messages: [
    { role: 'user', text: 'Who is shortlisted?', at: '2026-10-06T10:00:00Z' },
    { role: 'assistant', text: answer, at: '2026-10-06T10:00:05Z' },
  ],
}

function renderPage(path = '/ask') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/ask" element={<AskPage />} />
        <Route path="/jobs/:id" element={<p>Opening page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AskPage (ASK-01…ASK-04)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('asks a suggested question, shows the answer with links and lists the chat', async () => {
    const fetch = fakeFetch([
      { path: '/ask/status', body: { available: true, suggestions: ['Who is shortlisted?', 'Any new test results?'] } },
      { path: '/ask/conversations', body: [] },
      { method: 'POST', path: '/ask', body: chat },
    ])
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Who is shortlisted?' }))
    expect(await screen.findByRole('link', { name: 'Asha Rao' })).toHaveAttribute('href', '/jobs/j1?candidate=a1')
    expect(screen.getByText('2 shortlisted')).toBeInTheDocument()
    expect(screen.getByRole('table')).toHaveTextContent('82%')
    const chats = screen.getByRole('complementary', { name: 'Your chats' })
    expect(within(chats).getByText('Who is shortlisted?')).toBeInTheDocument()
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ question: 'Who is shortlisted?', conversationId: null })

    await userEvent.click(screen.getByRole('link', { name: 'Asha Rao' }))
    expect(screen.getByText('Opening page')).toBeInTheDocument()
  })

  it('continues an earlier chat with Enter', async () => {
    const fetch = fakeFetch([
      { path: '/ask/status', body: { available: true, suggestions: [] } },
      { path: '/ask/conversations/c1', body: chat },
      { path: '/ask/conversations', body: [{ id: 'c1', title: chat.title, updatedAt: chat.updatedAt }] },
      {
        method: 'POST',
        path: '/ask',
        body: { ...chat, messages: [...chat.messages, { role: 'user', text: 'and Ravi?', at: '' }, { role: 'assistant', text: 'Ravi is in Screening.', at: '' }] },
      },
    ])
    renderPage('/ask?c=c1')

    expect(await screen.findByRole('link', { name: 'Ravi K' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Your question'), 'and Ravi?{Enter}')
    expect(await screen.findByText('Ravi is in Screening.')).toBeInTheDocument()
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({ question: 'and Ravi?', conversationId: 'c1' })
  })

  it('keeps the question when asking fails, and says when the AI is off', async () => {
    fakeFetch([
      { path: '/ask/status', body: { available: true, suggestions: [] } },
      { path: '/ask/conversations', body: [] },
      { method: 'POST', path: '/ask', status: 502, body: { error: 'Ask ATS is busy right now.' } },
    ])
    renderPage()
    await userEvent.type(await screen.findByLabelText('Your question'), 'who joined?')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('busy')
    expect(screen.getByLabelText('Your question')).toHaveValue('who joined?')
  })
})

describe('Markdown', () => {
  it('renders lists, bold and app links, and never raw HTML', () => {
    render(
      <MemoryRouter>
        <Markdown text={'1. **One** <b>x</b>\n2. [Two](/jobs/j2) and [ext](https://example.com) and [bad](javascript:alert(1))'} />
      </MemoryRouter>,
    )
    expect(screen.getByText('One').tagName).toBe('STRONG')
    expect(screen.getByText(/<b>x<\/b>/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Two' })).toHaveAttribute('href', '/jobs/j2')
    expect(screen.getByRole('link', { name: 'ext' })).toHaveAttribute('target', '_blank')
    expect(screen.queryByRole('link', { name: 'bad' })).not.toBeInTheDocument()
  })
})
