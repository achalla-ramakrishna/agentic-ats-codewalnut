import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/types'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'
import { WHATS_NEW } from '../whatsNew'

const interviewer: Me = {
  id: 'u1',
  email: 'panel@codewalnut.test',
  name: 'Pat Panel',
  roles: ['INTERVIEWER'],
  capabilities: ['VIEW_DASHBOARD', 'VIEW_INTERVIEWS'],
  navigation: [
    { key: 'dashboard', label: 'Dashboard', path: '/' },
    { key: 'interviews', label: 'Interviews', path: '/interviews' },
  ],
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe("What's new", () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => vi.unstubAllGlobals())

  it('badges unseen notes, shows only what the role can use, and clears once read', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: interviewer },
      { path: '/dashboard', body: { openJobs: [], recentActivity: [] } },
      { path: '/interviews', body: [] },
    ])
    renderAt('/')

    const visible = WHATS_NEW.filter((e) => !e.capability || interviewer.capabilities.includes(e.capability))
    const nav = await screen.findByRole('navigation', { name: 'Main' })
    expect(within(nav).getByLabelText(`${visible.length} new`)).toBeInTheDocument()
    expect(await screen.findByRole('status')).toHaveTextContent(`New: ${visible[0].title}`)

    await userEvent.click(within(nav).getByRole('link', { name: /What's new/ }))
    expect(await screen.findByRole('heading', { name: "What's new", level: 1 })).toBeInTheDocument()
    const titles = screen.getAllByRole('article').map((a) => a.getAttribute('aria-label'))
    expect(titles).toEqual(visible.map((e) => e.title))
    expect(titles).toContain('Schedule interviews with Google Meet')
    expect(titles).not.toContain('Shareable job links')
    expect(screen.getAllByText('New')).toHaveLength(visible.length)
    expect(within(nav).queryByLabelText(/new$/)).not.toBeInTheDocument()

    await userEvent.click(within(nav).getByRole('link', { name: 'Dashboard' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('dismissing the dashboard note marks everything seen', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: 'STAFF' } },
      { path: '/me', body: interviewer },
      { path: '/dashboard', body: { openJobs: [], recentActivity: [] } },
      { path: '/interviews', body: [] },
    ])
    renderAt('/')

    await userEvent.click(await screen.findByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem('ats.whatsNew.seen.panel@codewalnut.test')!)).toContain(WHATS_NEW[0].id)
  })

  it('entries have unique ids and are newest first', () => {
    expect(new Set(WHATS_NEW.map((e) => e.id)).size).toBe(WHATS_NEW.length)
    const dates = WHATS_NEW.map((e) => e.date)
    expect([...dates].sort().reverse()).toEqual(dates)
  })
})
