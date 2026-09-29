import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'

const job = {
  slug: 'abcDEF234567',
  title: 'React Intern',
  company: 'CodeWalnut',
  location: 'Bengaluru',
  workMode: 'Office',
  employmentType: 'Internship · 6 months',
  description: 'Build UIs.\n\nWhat you will do\n- Ship React features\n- Write tests',
  acceptingApplications: true,
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

describe('PublicJobPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    sessionStorage.clear()
  })

  it('shows the job to signed-out visitors and asks them to sign in to apply', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: null } },
      { path: '/public/jobs/abcDEF234567', body: job },
      { path: '/auth/config', body: { googleEnabled: false, googleRedirectUri: null, devLoginEnabled: true, accessCodeRequired: false, devUsers: [] } },
    ])
    renderAt('/apply/abcDEF234567')

    expect(await screen.findByRole('heading', { name: 'React Intern' })).toBeInTheDocument()
    expect(screen.getByText(/Bengaluru · Office · Internship · 6 months/)).toBeInTheDocument()
    expect(screen.getByText('Ship React features').tagName).toBe('LI')

    await userEvent.click(screen.getByRole('button', { name: /Apply — continue with Google/ }))
    expect(sessionStorage.getItem('ats.returnTo')).toBe('/apply/abcDEF234567')
    expect(await screen.findByRole('button', { name: 'Sign in as this user' })).toBeInTheDocument()
  })

  it('lets a signed-in candidate apply with a résumé', async () => {
    const fetchMock = fakeFetch([
      { path: '/auth/session', body: { type: 'CANDIDATE' } },
      { path: '/candidate/me', body: { id: 'c1', email: 'asha@gmail.com', name: 'Asha Rao' } },
      { path: '/public/jobs/abcDEF234567', body: job },
      { method: 'POST', path: '/candidate/applications', status: 201, body: { slug: job.slug, jobTitle: job.title, status: 'Applied', appliedAt: '2026-09-29T00:00:00Z' } },
      { path: '/candidate/applications', body: [{ slug: job.slug, jobTitle: job.title, status: 'Applied', appliedAt: '2026-09-29T00:00:00Z' }] },
    ])
    renderAt('/apply/abcDEF234567')

    const form = await screen.findByRole('form', { name: 'Apply' })
    expect(screen.getByLabelText('Full name')).toHaveValue('Asha Rao')
    await userEvent.type(screen.getByLabelText('Phone'), '9000000001')
    await userEvent.upload(screen.getByLabelText(/Résumé/), new File(['%PDF-1.4'], 'cv.pdf', { type: 'application/pdf' }))
    const submit = screen.getByRole('button', { name: 'Submit application' })
    expect(submit).toBeDisabled()
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(submit)

    expect(await screen.findByRole('heading', { name: 'My applications' })).toBeInTheDocument()
    expect(await screen.findByText('React Intern', { selector: '.alert strong' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Applied' })).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    const body = post[1]!.body as FormData
    expect(body.get('slug')).toBe('abcDEF234567')
    expect(body.get('consent')).toBe('true')
    expect((body.get('resume') as File).name).toBe('cv.pdf')
    expect(form).not.toBeInTheDocument()
  })

  it('says when a link is not active', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: null } },
      { path: '/public/jobs/nope', status: 404, body: { error: "This job link isn't active" } },
    ])
    renderAt('/apply/nope')
    expect(await screen.findByRole('heading', { name: "This job link isn't active" })).toBeInTheDocument()
  })
})
