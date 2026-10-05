import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { fakeFetch } from '../test/fakeFetch'

const config = { googleEnabled: true, googleRedirectUri: 'https://ats.example/login/oauth2/code/google', devLoginEnabled: false, accessCodeRequired: false, devUsers: [] }

describe('LoginPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('says which email was refused and what to do', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: null } },
      { path: '/auth/config', body: { ...config, signInRefused: { email: 'arun.k@codewalnut.com', reason: 'NOT_PROVISIONED' } } },
    ])
    render(
      <MemoryRouter initialEntries={['/login?error']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/arun.k@codewalnut.com isn't a CodeWalnut ATS user yet/)).toBeInTheDocument()
  })

  it('a deactivated user is told to ask for reactivation', async () => {
    fakeFetch([
      { path: '/auth/session', body: { type: null } },
      { path: '/auth/config', body: { ...config, signInRefused: { email: 'old@codewalnut.com', reason: 'DEACTIVATED' } } },
    ])
    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/old@codewalnut.com has been deactivated/)).toBeInTheDocument()
  })
})
