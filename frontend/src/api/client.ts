/**
 * The only place the app talks HTTP. Sends the session cookie, echoes the
 * XSRF-TOKEN cookie as X-XSRF-TOKEN on writes, and turns error responses into
 * ApiError so callers can branch on status (401 → sign in, 403 → not allowed).
 */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((part) => part.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (method !== 'GET' && method !== 'HEAD') {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token))
  }

  const response = await fetch(`/api/v1${path}`, { ...init, method, headers, credentials: 'same-origin' })
  if (!response.ok) {
    let message = response.statusText || `Request failed (${response.status})`
    try {
      const body = (await response.json()) as { error?: string }
      if (body.error) message = body.error
    } catch {
      // Non-JSON error body; keep the status text.
    }
    throw new ApiError(response.status, message)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
