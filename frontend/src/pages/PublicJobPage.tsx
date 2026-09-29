import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getAuthConfig, GOOGLE_SIGN_IN_URL } from '../api/auth'
import { applyToJob, getPublicJob, type PublicJob } from '../api/tracker'
import { useAuth } from '../auth/AuthContext'
import { JobDescription } from '../components/JobDescription'
import { Button, Card } from '../components/ui'
import './PublicJobPage.css'

export const RETURN_TO_KEY = 'ats.returnTo'

function rememberReturn(path: string) {
  try {
    sessionStorage.setItem(RETURN_TO_KEY, path)
  } catch {
    // Storage blocked: after sign-in the candidate lands on their home page instead.
  }
}

function ApplyForm({ job, defaultName }: { job: PublicJob; defaultName: string }) {
  const navigate = useNavigate()
  const [name, setName] = useState(defaultName)
  const [phone, setPhone] = useState('')
  const [note, setNote] = useState('')
  const [consent, setConsent] = useState(false)
  const [resume, setResume] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!resume) return
    setError(null)
    setBusy(true)
    try {
      await applyToJob({ slug: job.slug, name, phone, note: note || undefined, consent, resume })
      navigate('/', { replace: true, state: { applied: job.title } })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not submit your application')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={onSubmit} aria-label="Apply">
      <h2>Apply for this role</h2>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <label className="field">
        Full name
        <input className="input" required value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <label className="field">
        Phone
        <input className="input" required type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="e.g. 98765 43210" />
      </label>
      <label className="field">
        Résumé (PDF or Word, up to 10 MB)
        <input className="input" type="file" accept=".pdf,.doc,.docx" onChange={(e) => setResume(e.target.files?.[0] ?? null)} style={{ paddingTop: 6 }} />
      </label>
      <label className="field">
        Anything you'd like us to know? (optional)
        <textarea className="textarea" style={{ minHeight: 80, fontFamily: 'inherit', fontSize: 14 }} value={note} onChange={(e) => setNote(e.target.value)} />
      </label>
      <label className="row" style={{ alignItems: 'flex-start', fontSize: 13 }}>
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} style={{ marginTop: 3 }} />
        <span>I agree that CodeWalnut may store my details and résumé to process this application.</span>
      </label>
      <div>
        <Button type="submit" disabled={busy || !consent || !resume || !name.trim() || !phone.trim()}>
          {busy ? 'Submitting…' : 'Submit application'}
        </Button>
      </div>
    </form>
  )
}

/** The page behind a shared job link. Works signed out, as a candidate, and (preview) as staff. */
export function PublicJobPage() {
  const { slug = '' } = useParams()
  const { state } = useAuth()
  const navigate = useNavigate()
  const [job, setJob] = useState<PublicJob | null>(null)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    getPublicJob(slug).then(setJob).catch(() => setNotFound(true))
  }, [slug])

  async function signInToApply() {
    rememberReturn(`/apply/${slug}`)
    try {
      const config = await getAuthConfig()
      if (config.googleEnabled) {
        window.location.href = GOOGLE_SIGN_IN_URL
        return
      }
    } catch {
      // fall through to the login page
    }
    navigate('/login')
  }

  if (notFound) {
    return (
      <div className="public-job">
        <Card>
          <h1>This job link isn't active</h1>
          <p className="muted">The role may have been filled or the link switched off.</p>
        </Card>
      </div>
    )
  }
  if (!job) return <p className="muted" style={{ padding: 24 }}>Loading…</p>

  const meta = [job.location, job.workMode, job.employmentType].filter(Boolean)

  return (
    <div className="public-job">
      <header className="public-job-brand">
        <img src="/favicon.svg" alt="" />
        <span>{job.company} Careers</span>
      </header>
      <Card className="stack">
        <div>
          <h1>{job.title}</h1>
          <div className="muted">
            {job.company}
            {meta.length > 0 && ` · ${meta.join(' · ')}`}
          </div>
        </div>
        {job.description ? <JobDescription text={job.description} /> : <p className="muted">Details coming soon.</p>}
      </Card>
      <Card>
        {!job.acceptingApplications ? (
          <p style={{ margin: 0 }}>This role is no longer accepting applications.</p>
        ) : state.status === 'candidate' ? (
          <ApplyForm job={job} defaultName={state.candidate.name ?? ''} />
        ) : state.status === 'signed-in' ? (
          <p className="muted" style={{ margin: 0 }}>
            You're signed in as CodeWalnut staff — this is how candidates see the page. <Link to="/jobs">Back to openings</Link>
          </p>
        ) : (
          <div className="stack">
            <h2>Interested?</h2>
            <p className="muted" style={{ margin: 0 }}>
              Sign in with your Google account (any Gmail works) to apply and upload your résumé. It takes about two minutes.
            </p>
            <div>
              <Button onClick={() => void signInToApply()}>Apply — continue with Google</Button>
            </div>
          </div>
        )}
      </Card>
    </div>
  )
}
