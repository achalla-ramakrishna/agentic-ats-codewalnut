import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { api, ApiError } from './client'

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('echoes the XSRF cookie as a header on writes (AUTH-12)', async () => {
    document.cookie = 'XSRF-TOKEN=abc%2D123'
    const fetchMock = fakeFetch([{ method: 'POST', path: '/users', status: 201, body: { id: '1' } }])

    await api('/users', { method: 'POST', body: '{}' })

    const headers = fetchMock.mock.calls[0][1]?.headers as Headers
    expect(headers.get('X-XSRF-TOKEN')).toBe('abc-123')
    expect(headers.get('Content-Type')).toBe('application/json')
  })

  it('does not send the XSRF header on reads', async () => {
    document.cookie = 'XSRF-TOKEN=abc'
    const fetchMock = fakeFetch([{ path: '/me', body: {} }])

    await api('/me')

    const headers = fetchMock.mock.calls[0][1]?.headers as Headers
    expect(headers.has('X-XSRF-TOKEN')).toBe(false)
  })

  it('turns error responses into ApiError with the server message', async () => {
    fakeFetch([{ path: '/users', status: 403, body: { error: 'Missing permission: MANAGE_USERS' } }])

    const error = await api('/users').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(403)
    expect((error as ApiError).message).toBe('Missing permission: MANAGE_USERS')
  })
})
