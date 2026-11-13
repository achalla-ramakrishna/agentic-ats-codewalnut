import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { bytes } from '../api/aiInterviews'
import { fakeFetch } from '../test/fakeFetch'
import { VideoSpace } from './VideoSpace'

const GB = 1024 * 1024 * 1024
const MB = 1024 * 1024

describe('Space for videos (AINT-19, AINT-20)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows room left, warns when low, and frees rejected candidates’ videos', async () => {
    const fetchMock = fakeFetch([
      {
        path: '/ai-interviews/storage',
        body: { usedBytes: 120 * MB, freeBytes: 0.8 * GB, totalBytes: 4.6 * GB, interviews: 3, perInterviewBytes: 40 * MB, roomFor: 20,
          temporary: false, warning: 'Less than 1 GB left for interview videos' },
      },
      { path: '/jobs/j1/ai-interviews/closed-videos', method: 'DELETE', body: { candidates: 1, videos: 14, bytes: 40 * MB } },
      { path: '/jobs/j1/ai-interviews/closed-videos', body: { candidates: 1, videos: 14, bytes: 40 * MB } },
    ])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<VideoSpace jobId="j1" canEdit />)
    const region = await screen.findByRole('region', { name: 'Space for videos' })
    expect(region).toHaveTextContent('819 MB free of 4.6 GB · room for about 20 more interviews')
    expect(screen.getByRole('alert')).toHaveTextContent('Less than 1 GB left')
    await userEvent.click(await screen.findByRole('button', { name: /Free 40 MB: delete rejected candidates’ videos \(1\)/ }))
    expect(await screen.findByText('Deleted 14 videos and freed 40 MB.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true)
  })

  it('formats sizes', () => {
    expect(bytes(1536)).toBe('1.5 KB')
    expect(bytes(4.5 * GB)).toBe('4.5 GB')
    expect(bytes(52 * MB)).toBe('52 MB')
  })
})
