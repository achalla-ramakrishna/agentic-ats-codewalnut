import { GOOGLE_SIGN_IN_URL } from '../api/auth'
import { ApiError } from '../api/client'
import { Button } from './ui'

/** The request failed because the sign-in has gone (e.g. it expired): signing in again fixes it. */
export const isSignedOut = (e: unknown) => e instanceof ApiError && e.status === 401

/**
 * Lets a candidate sign in again in a new tab without leaving the page, so an unsent recording or unsaved answers
 * stay where they are. Then they retry here (or saving resumes by itself).
 */
export function SignedOutNotice({ retryLabel }: { retryLabel?: string }) {
  return (
    <div role="alert" className="alert alert-error stack" style={{ gap: 6 }}>
      <span>
        You were signed out. Nothing is lost: keep this page open, click <strong>Sign in again</strong> (it opens a new tab), sign
        in with the same Google account, close that tab, and come back here to{' '}
        {retryLabel ? (
          <>
            click <strong>{retryLabel}</strong>
          </>
        ) : (
          'carry on — your answers are saved again automatically'
        )}
        .
      </span>
      <div>
        <Button size="sm" onClick={() => window.open(GOOGLE_SIGN_IN_URL, '_blank')}>
          Sign in again
        </Button>
      </div>
    </div>
  )
}
