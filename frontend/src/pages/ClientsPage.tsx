import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { addContact, listContacts, removeContact, type ClientContact } from '../api/clients'
import { createClient, listClients, type Client } from '../api/tracker'
import { useMe } from '../auth/AuthContext'
import { Badge, Button, Card, PageHeader } from '../components/ui'

/** People at a client who can sign in and see what CodeWalnut shared (docs/features/client-access.md). */
function Contacts({ client, canManage }: { client: Client; canManage: boolean }) {
  const [contacts, setContacts] = useState<ClientContact[] | null>(null)
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    listContacts(client.id).then(setContacts).catch(() => setContacts([]))
  }, [client.id])
  useEffect(load, [load])

  async function onAdd(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await addContact(client.id, email.trim(), name.trim() || undefined)
      setEmail('')
      setName('')
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add the contact')
    }
  }

  async function onRemove(contact: ClientContact) {
    if (!window.confirm(`Remove ${contact.email}? They lose access straight away.`)) return
    setError(null)
    try {
      await removeContact(contact.id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not remove the contact')
    }
  }

  const active = contacts?.filter((c) => c.active) ?? []
  return (
    <div className="stack" style={{ gap: 8 }}>
      <span className="muted" style={{ fontSize: 13 }}>
        People at {client.name} who can sign in with Google and see only the candidates and documents you share with {client.name}.
      </span>
      {active.length === 0 && <span className="muted">No contacts yet.</span>}
      {active.map((c) => (
        <div key={c.id} className="row" style={{ gap: 12 }}>
          <span>
            {c.name ? `${c.name} · ` : ''}
            {c.email}
          </span>
          <span className="muted" style={{ fontSize: 12 }}>
            {c.lastLoginAt ? `last signed in ${new Date(c.lastLoginAt).toLocaleDateString()}` : 'not signed in yet'}
          </span>
          {canManage && (
            <Button size="sm" variant="ghost" onClick={() => void onRemove(c)}>
              Remove
            </Button>
          )}
        </div>
      ))}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {canManage && (
        <form className="row" onSubmit={onAdd} aria-label={`Add contact for ${client.name}`} style={{ alignItems: 'flex-end' }}>
          <label className="field">
            Email
            <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="e.g. hm@blend.com" />
          </label>
          <label className="field">
            Name (optional)
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <Button type="submit" size="sm" disabled={!email.trim()}>
            Add contact
          </Button>
        </form>
      )}
    </div>
  )
}

export function ClientsPage() {
  const me = useMe()
  const canEdit = me.capabilities.includes('MANAGE_JOBS')
  const canManageContacts = me.capabilities.includes('MANAGE_CLIENTS')
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
      <PageHeader title="Clients" description="Companies CodeWalnut hires for, and who at each can sign in." />
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
      {!clients && <p className="muted">Loading…</p>}
      {clients && clients.length === 0 && (
        <Card>
          <p className="muted" style={{ margin: 0 }}>
            No clients yet.
          </p>
        </Card>
      )}
      {clients?.map((c) => (
        <Card key={c.id} className="stack">
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <h2 style={{ margin: 0 }}>{c.name}</h2>
            <Badge>Client</Badge>
          </div>
          <Contacts client={c} canManage={canManageContacts} />
        </Card>
      ))}
    </div>
  )
}
