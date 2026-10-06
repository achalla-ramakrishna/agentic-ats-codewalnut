import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { CodeWalnutResumeEditor } from './CodeWalnutResumeEditor'

const none = {
  exists: false,
  aiAvailable: true,
  resume: null,
  showEmail: true,
  includeScreening: true,
  screening: ['Java basics test: 80% (pass mark 60%)'],
  sourceFileName: null,
  savedDocumentId: null,
  updatedBy: null,
  updatedAt: null,
}
const draft = {
  ...none,
  exists: true,
  sourceFileName: 'asha.pdf',
  resume: {
    name: 'Asha Rao',
    headline: 'Java Developer',
    location: 'Bengaluru, India',
    email: 'asha@example.com',
    summary: 'Builds Spring Boot APIs.',
    skills: [{ label: 'Backend', items: ['Java', 'Spring Boot'] }],
    sections: [{ title: 'Experience', entries: [{ title: 'Intern', subtitle: 'Acme', period: '2025', bullets: ['Built APIs.'] }] }],
  },
}

describe('CodeWalnutResumeEditor', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('drafts with AI, lets a person edit, and saves the PDF to documents', async () => {
    const fetchMock = fakeFetch([
      { path: '/applications/a1/codewalnut-resume/generate', method: 'POST', body: draft },
      { path: '/applications/a1/codewalnut-resume/save', method: 'POST', body: { documentId: 'd9', fileName: 'Asha Rao - CodeWalnut.pdf' } },
      { path: '/applications/a1/codewalnut-resume', method: 'PUT', body: { ...draft, resume: { ...draft.resume, summary: 'Builds and tests Spring Boot APIs.' } } },
      { path: '/applications/a1/codewalnut-resume', body: none },
    ])
    const onSaved = vi.fn()
    render(<CodeWalnutResumeEditor applicationId="a1" canEdit onSaved={onSaved} />)

    await userEvent.click(await screen.findByRole('button', { name: '✨ Create with AI' }))
    const summary = await screen.findByLabelText('Summary')
    expect(screen.getByText(/Check every line against the original/)).toBeInTheDocument()
    expect(screen.getByText(/Java basics test: 80%/)).toBeInTheDocument()
    await userEvent.clear(summary)
    await userEvent.type(summary, 'Builds and tests Spring Boot APIs.')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes & PDF' }))

    expect(await screen.findByText(/Saved “Asha Rao - CodeWalnut.pdf”/)).toBeInTheDocument()
    const putCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')!
    expect(JSON.parse(putCall[1]!.body as string)).toMatchObject({
      showEmail: true,
      includeScreening: true,
      resume: { summary: 'Builds and tests Spring Boot APIs.' },
    })
    expect(onSaved).toHaveBeenCalled()
  })

  it('needs an email before the PDF can be saved, and takes a GitHub link', async () => {
    const noEmail = { ...draft, resume: { ...draft.resume, email: '' } }
    const fetchMock = fakeFetch([
      { path: '/applications/a1/codewalnut-resume', method: 'PUT', body: draft },
      { path: '/applications/a1/codewalnut-resume', body: noEmail },
    ])
    render(<CodeWalnutResumeEditor applicationId="a1" canEdit onSaved={vi.fn()} />)

    expect(await screen.findByText(/Add the candidate.s email under Edit/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save PDF to documents' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Edit' }))
    await userEvent.type(screen.getByLabelText('Email'), 'asha@example.com')
    await userEvent.type(screen.getByLabelText('GitHub or portfolio (optional)'), 'github.com/asha')
    expect(screen.getByRole('button', { name: 'Save changes & PDF' })).toBeEnabled()
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')!
    expect(JSON.parse(put[1]!.body as string)).toMatchObject({ resume: { email: 'asha@example.com', link: 'github.com/asha' } })
  })
})
