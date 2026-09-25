import type { CandidateMe } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Button, Card, PageHeader } from '../components/ui'
import './CandidateHomePage.css'

/** Candidate area (see docs/features/candidate-portal.md). Applications arrive with chunk 1–2. */
export function CandidateHomePage({ candidate }: { candidate: CandidateMe }) {
  const { signOut } = useAuth()

  return (
    <div className="candidate">
      <header className="candidate-bar">
        <div className="brand">
          <img src="/favicon.svg" alt="" />
          <span>CodeWalnut Careers</span>
        </div>
        <div className="row">
          <span className="muted">{candidate.email}</span>
          <Button variant="secondary" size="sm" onClick={() => void signOut()}>
            Sign out
          </Button>
        </div>
      </header>
      <main className="candidate-main">
        <PageHeader
          title={`Hi${candidate.name ? ` ${candidate.name.split(' ')[0]}` : ''}`}
          description="Track your applications with CodeWalnut here."
        />
        <Card className="stack">
          <h2>My applications</h2>
          <p className="muted">You haven't applied to any roles yet. Open roles will be listed on our careers page soon.</p>
        </Card>
      </main>
    </div>
  )
}
