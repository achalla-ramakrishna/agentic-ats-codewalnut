import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { createUser, listUsers, updateUser } from '../api/users'
import { ALL_ROLES, ROLE_LABELS, type Role, type User } from '../api/types'
import { useMe } from '../auth/AuthContext'
import { Badge, Button, Card, PageHeader } from '../components/ui'

function RolePicker({ value, onChange, idPrefix }: { value: Role[]; onChange: (roles: Role[]) => void; idPrefix: string }) {
  return (
    <fieldset className="row" style={{ border: 0, padding: 0, margin: 0 }}>
      <legend className="visually-hidden">Roles</legend>
      {ALL_ROLES.map((role) => {
        const id = `${idPrefix}-${role}`
        return (
          <label key={role} htmlFor={id} className="row" style={{ gap: 4 }}>
            <input
              id={id}
              type="checkbox"
              checked={value.includes(role)}
              onChange={(e) => onChange(e.target.checked ? [...value, role] : value.filter((r) => r !== role))}
            />
            {ROLE_LABELS[role]}
          </label>
        )
      })}
    </fieldset>
  )
}

function NewUserForm({ onCreated }: { onCreated: () => void }) {
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [roles, setRoles] = useState<Role[]>([])
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await createUser({ email, name: name || undefined, roles })
      setEmail('')
      setName('')
      setRoles([])
      onCreated()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create user')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card>
      <form className="stack" onSubmit={onSubmit} aria-label="Add user">
        <h2>Add a user</h2>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field">
            Email
            <input className="input" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label className="field">
            Name
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
          </label>
        </div>
        <RolePicker value={roles} onChange={setRoles} idPrefix="new" />
        <div>
          <Button type="submit" disabled={saving || roles.length === 0 || !email}>
            Add user
          </Button>
        </div>
      </form>
    </Card>
  )
}

function UserRow({ user, isSelf, onChanged }: { user: User; isSelf: boolean; onChanged: () => void }) {
  const [editing, setEditing] = useState(false)
  const [roles, setRoles] = useState<Role[]>(user.roles)
  const [error, setError] = useState<string | null>(null)

  async function save(patch: { roles?: Role[]; active?: boolean }) {
    setError(null)
    try {
      await updateUser(user.id, patch)
      setEditing(false)
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Update failed')
    }
  }

  return (
    <tr>
      <td>
        <div>{user.name ?? '—'}</div>
        <div className="muted">{user.email}</div>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
      </td>
      <td>
        {editing ? (
          <RolePicker value={roles} onChange={setRoles} idPrefix={user.id} />
        ) : (
          <div className="row">
            {user.roles.map((role) => (
              <Badge key={role} tone="primary">
                {ROLE_LABELS[role]}
              </Badge>
            ))}
          </div>
        )}
      </td>
      <td>{user.active ? <Badge>Active</Badge> : <Badge tone="danger">Deactivated</Badge>}</td>
      <td className="muted">{user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : 'Never'}</td>
      <td>
        <div className="row">
          {editing ? (
            <>
              <Button size="sm" disabled={roles.length === 0} onClick={() => void save({ roles })}>
                Save
              </Button>
              <Button size="sm" variant="ghost" onClick={() => { setRoles(user.roles); setEditing(false) }}>
                Cancel
              </Button>
            </>
          ) : (
            <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
              Edit roles
            </Button>
          )}
          {!isSelf && (
            <Button size="sm" variant="ghost" onClick={() => void save({ active: !user.active })}>
              {user.active ? 'Deactivate' : 'Reactivate'}
            </Button>
          )}
        </div>
      </td>
    </tr>
  )
}

export function UsersPage() {
  const me = useMe()
  const [users, setUsers] = useState<User[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    listUsers()
      .then(setUsers)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load users'))
  }, [])

  useEffect(load, [load])

  return (
    <div className="stack">
      <PageHeader title="Users" description="Only people listed here can sign in. Changes apply on their next click." />
      <NewUserForm onCreated={load} />
      <Card>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        {!users && !error && <p className="muted">Loading…</p>}
        {users && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Roles</th>
                  <th>Status</th>
                  <th>Last sign-in</th>
                  <th>
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <UserRow key={user.id} user={user} isSelf={user.id === me.id} onChanged={load} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
