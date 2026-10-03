import { useState } from 'react'
import { stopViewAs } from '../api/viewAs'
import { useAuth } from '../auth/AuthContext'

const KIND = { CANDIDATE: 'candidate', CLIENT: 'client contact', ROLE: 'role' }

/** Always on top while an admin views the app as someone else. */
export function ViewAsBanner() {
  const { viewAs } = useAuth()
  const [leaving, setLeaving] = useState(false)
  if (!viewAs) return null
  const until = new Date(viewAs.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  return (
    <div className="view-as-banner" role="status">
      <span>
        👁 Viewing as <strong>{viewAs.label}</strong> ({KIND[viewAs.kind]}) · read-only, nothing you do here is saved or sent ·
        ends {until}
      </span>
      <button
        type="button"
        className="btn btn-sm btn-secondary"
        disabled={leaving}
        onClick={async () => {
          setLeaving(true)
          try {
            await stopViewAs()
          } finally {
            window.location.assign('/admin/view-as')
          }
        }}
      >
        Back to admin
      </button>
    </div>
  )
}
