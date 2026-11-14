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
  if (init.body !== undefined && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
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

/** POSTs JSON and saves the file the server sends back (e.g. an export), named as the server says. */
export async function download(path: string, body: unknown, fallbackName: string): Promise<void> {
  const headers = new Headers({ 'Content-Type': 'application/json' })
  const token = readCookie('XSRF-TOKEN')
  if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token))
  const response = await fetch(`/api/v1${path}`, { method: 'POST', headers, body: JSON.stringify(body), credentials: 'same-origin' })
  if (!response.ok) {
    let message = response.statusText || `Request failed (${response.status})`
    try {
      const err = (await response.json()) as { error?: string }
      if (err.error) message = err.error
    } catch {
      // keep the status text
    }
    throw new ApiError(response.status, message)
  }
  const name = /filename="?([^";]+)"?/.exec(response.headers.get('Content-Disposition') ?? '')?.[1] ?? fallbackName
  const url = URL.createObjectURL(await response.blob())
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
