import { useEffect, useState, type FormEvent } from 'react'
import { devLogin, getAuthConfig, GOOGLE_SIGN_IN_URL } from '../api/auth'
import type { AuthConfig } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Button, Card } from '../components/ui'
import './LoginPage.css'

export function LoginPage() {
  const { refresh } = useAuth()
  const [config, setConfig] = useState<AuthConfig | null>(null)
  const [devEmail, setDevEmail] = useState('')
  const [error, setError] = useState<string | null>(
    new URLSearchParams(window.location.search).has('error')
      ? 'Sign-in was refused. Ask an Admin to add your CodeWalnut account.'
      : null,
  )

  useEffect(() => {
    getAuthConfig()
      .then((cfg) => {
        setConfig(cfg)
        if (cfg.devUsers.length > 0) setDevEmail(cfg.devUsers[0].email)
      })
      .catch(() => setError('Cannot reach the ATS server.'))
  }, [])

  async function onDevLogin(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await devLogin(devEmail)
      await refresh()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Sign-in failed')
    }
  }

  return (
    <div className="login">
      <Card>
        <div>
          <h1>CodeWalnut ATS</h1>
          <p className="muted">Sign in with your CodeWalnut account.</p>
        </div>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        {config?.googleEnabled && (
          <a className="btn btn-primary" href={GOOGLE_SIGN_IN_URL}>
            Sign in with Google
          </a>
        )}
        {config && !config.googleEnabled && !config.devLoginEnabled && (
          <p className="alert alert-info">Google sign-in is not configured on this server.</p>
        )}
        {config?.devLoginEnabled && (
          <form className="dev" onSubmit={onDevLogin}>
            <strong>Dev login</strong>
            <span className="muted">Local development only — sign in as a seeded user.</span>
            <label className="field">
              User
              <select className="input" value={devEmail} onChange={(e) => setDevEmail(e.target.value)}>
                {config.devUsers.map((u) => (
                  <option key={u.email} value={u.email}>
                    {u.email} — {u.label}
                  </option>
                ))}
              </select>
            </label>
            <Button type="submit" variant="secondary" disabled={!devEmail}>
              Sign in as this user
            </Button>
          </form>
        )}
      </Card>
    </div>
  )
}
