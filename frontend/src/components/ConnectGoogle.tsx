import { connectGoogleUrl, type GoogleStatus } from '../api/interviews'
import { Button } from './ui'

/** Asks the staff member to connect their own Google account (Calendar + Gmail) once per sign-in. */
export function ConnectGoogle({
  status,
  purpose,
  returnTo,
}: {
  status: GoogleStatus | null
  purpose: string
  returnTo: string
}) {
  if (status && !status.available) {
    return (
      <div className="alert alert-info">
        Google Calendar and Gmail aren't set up for this app yet. An admin needs to enable them (see the Railway deploy guide).
      </div>
    )
  }
  return (
    <div className="alert alert-info stack" style={{ gap: 8 }}>
      <span>{purpose} Connect your Google account once per sign-in.</span>
      <div>
        <Button size="sm" onClick={() => window.location.assign(connectGoogleUrl(returnTo))}>
          Connect Google (Calendar &amp; Gmail)
        </Button>
      </div>
    </div>
  )
}
