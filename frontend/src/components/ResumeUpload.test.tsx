import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { ResumeUpload } from './ResumeUpload'

const done = {
  total: 7,
  pending: 0,
  done: 6,
  failed: 1,
  newCandidates: 5,
  existing: 1,
  items: [
    { id: 'i1', fileName: 'asha.pdf', status: 'DONE', outcome: 'NEW_CANDIDATE', applicationId: 'a1', candidateName: 'Asha Rao', error: null },
    { id: 'i2', fileName: 'old.doc', status: 'FAILED', outcome: null, applicationId: null, candidateName: null, error: 'Old Word (.doc) files can’t be read.' },
  ],
}

describe('ResumeUpload', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uploads many résumés in small batches and shows what happened to each', async () => {
    const fetchMock = fakeFetch([
      { path: '/jobs/j1/resumes', body: done },
      { method: 'POST', path: '/jobs/j1/resumes', body: { ...done, pending: 5 } },
    ])
    const onProgress = vi.fn()
    render(<ResumeUpload jobId="j1" onProgress={onProgress} onClose={() => undefined} />)

    const files = Array.from({ length: 7 }, (_, i) => new File(['%PDF'], `cv${i}.pdf`, { type: 'application/pdf' }))
    await userEvent.upload(screen.getByLabelText('Résumé files'), files)

    expect(await screen.findByText(/6/, { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('5 new · 1 already known · 1 couldn’t be read')
    expect(screen.getByText('Asha Rao')).toBeInTheDocument()
    expect(screen.getByText('Old Word (.doc) files can’t be read.')).toBeInTheDocument()
    const posts = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')
    expect(posts.map(([, init]) => (init!.body as FormData).getAll('files').length)).toEqual([5, 2])
    expect(onProgress).toHaveBeenCalled()
  })
  it('keeps résumés the AI couldn’t read while it was out of credit, and tries again on request', async () => {
    const waiting = {
      ...done,
      items: [
        ...done.items,
        { id: 'i3', fileName: 'meera.pdf', status: 'FAILED', outcome: null, applicationId: null, candidateName: null,
          error: 'The AI is unavailable: the Anthropic account has run out of credit. It will try again automatically.' },
      ],
    }
    const fetchMock = fakeFetch([
      { method: 'POST', path: '/jobs/j1/resumes/retry', body: { uploads: 1, readings: 0 } },
      { path: '/jobs/j1/resumes', body: waiting },
    ])
    render(<ResumeUpload jobId="j1" onProgress={() => undefined} onClose={() => undefined} />)
    expect(await screen.findByText(/1 résumé is kept and will be read automatically/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again now (1)' }))
    await vi.waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/resumes/retry'))).toBe(true))
  })
})
