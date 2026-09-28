import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { createClient, listClients, type Client } from '../api/tracker'
import { useMe } from '../auth/AuthContext'
import { Button, Card, PageHeader } from '../components/ui'

export function ClientsPage() {
  const me = useMe()
  const canEdit = me.capabilities.includes('MANAGE_JOBS')
  const [clients, setClients] = useState<Client[] | null>(null)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    listClients().then(setClients).catch(() => setClients([]))
  }, [])
  useEffect(load, [load])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await createClient(name.trim())
      setName('')
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add the client')
    }
  }

  return (
    <div className="stack">
      <PageHeader title="Clients" description="Companies CodeWalnut hires for." />
      {canEdit && (
        <Card>
          <form className="row" onSubmit={onSubmit} aria-label="Add client" style={{ alignItems: 'flex-end' }}>
            <label className="field">
              Client name
              <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Blend" />
            </label>
            <Button type="submit" disabled={!name.trim()}>
              Add client
            </Button>
          </form>
          {error && (
            <div role="alert" className="alert alert-error" style={{ marginTop: 12 }}>
              {error}
            </div>
          )}
        </Card>
      )}
      <Card>
        {!clients && <p className="muted">Loading…</p>}
        {clients && clients.length === 0 && <p className="muted" style={{ margin: 0 }}>No clients yet.</p>}
        {clients && clients.length > 0 && (
          <ul style={{ margin: 0 }}>
            {clients.map((c) => (
              <li key={c.id}>{c.name}</li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
