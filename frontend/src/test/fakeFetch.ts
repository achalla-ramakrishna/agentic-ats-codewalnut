import { vi } from 'vitest'

export interface FakeRoute {
  method?: string
  path: string
  status?: number
  body?: unknown
}

/** Stubs global fetch with a small route table; returns the mock so tests can inspect calls. */
export function fakeFetch(routes: FakeRoute[]) {
  const mock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString()
    const method = (init?.method ?? 'GET').toUpperCase()
    const route = routes.find((r) => (r.method ?? 'GET') === method && url.startsWith(`/api/v1${r.path}`))
    if (!route) return new Response(JSON.stringify({ error: `No fake for ${method} ${url}` }), { status: 404 })
    const status = route.status ?? 200
    return new Response(status === 204 ? null : JSON.stringify(route.body ?? {}), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}
