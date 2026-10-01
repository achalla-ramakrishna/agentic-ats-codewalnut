import { useCallback, useEffect, useState } from 'react'
import {
  clientDocumentUrl,
  listClientMessages,
  listSharedCandidates,
  postClientMessage,
  type ClientCandidate,
  type ClientMe,
} from '../api/clients'
import { useAuth } from '../auth/AuthContext'
import { ExternalThread } from '../components/CandidateThread'
import { Badge, Button, Card, PageHeader } from '../components/ui'
import './CandidateHomePage.css'

function Detail({ label, value }: { label: string; value: string | number | null | undefined }) {
  if (value === null || value === undefined || value === '') return null
  return (
    <div>
      <span className="muted">{label}:</span> <span style={{ whiteSpace: 'pre-wrap' }}>{value}</span>
    </div>
  )
}

function CandidateCard({ c }: { c: ClientCandidate }) {
  const [chat, setChat] = useState(false)
  const load = useCallback(() => listClientMessages(c.applicationId), [c.applicationId])
  const send = useCallback((body: string) => postClientMessage(c.applicationId, body), [c.applicationId])
  const p = c.profile
  return (
    <Card className="stack" role="region" aria-label={`Candidate ${c.name}`}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <div>
          <h2 style={{ margin: 0 }}>{c.name}</h2>
          <div className="muted">{c.jobTitle}</div>
        </div>
        <Badge tone="primary">{c.stageLabel}</Badge>
      </div>
      {c.note && <div className="alert alert-info">{c.note}</div>}
      {(c.email || c.phone) && (
        <div className="stack" style={{ gap: 4 }}>
          <strong>Contact</strong>
          <Detail label="Email" value={c.email} />
          <Detail label="Phone" value={c.phone} />
        </div>
      )}
      {p && (
        <div className="stack" style={{ gap: 4 }}>
          <strong>Profile</strong>
          <Detail label="Date of birth" value={p.dateOfBirth ? new Date(p.dateOfBirth).toLocaleDateString() : null} />
          <Detail label="Current address" value={p.currentAddress} />
          <Detail label="Permanent address" value={p.permanentAddress} />
          <Detail label="College" value={p.college} />
          <Detail label="Degree" value={p.degree} />
          <Detail label="Graduation year" value={p.graduationYear} />
          <Detail label="LinkedIn" value={p.linkedinUrl} />
          <Detail label="Emergency contact" value={p.emergencyContact} />
        </div>
      )}
      <div className="stack" style={{ gap: 4 }}>
        <strong>Documents</strong>
        {c.documents.length === 0 && <span className="muted">No documents shared yet.</span>}
        {c.documents.map((d) => (
          <div key={d.id} className="row" style={{ gap: 12 }}>
            <span style={{ minWidth: 220 }}>{d.label}</span>
            <a href={clientDocumentUrl(d.id, true)} target="_blank" rel="noreferrer">
              View
            </a>
            <a href={clientDocumentUrl(d.id)}>Download</a>
            <span className="muted" style={{ fontSize: 12 }}>
              {d.fileName}
            </span>
          </div>
        ))}
      </div>
      <div className="muted" style={{ fontSize: 12 }}>
        Shared by CodeWalnut on {new Date(c.sharedAt).toLocaleDateString()}
      </div>
      <div>
        <Button size="sm" variant={chat ? 'ghost' : 'secondary'} onClick={() => setChat((v) => !v)}>
          {chat ? 'Hide messages' : 'Message CodeWalnut about this candidate'}
        </Button>
      </div>
      {chat && (
        <ExternalThread
          title={`Messages about ${c.name}`}
          emptyText="Need something else for this candidate? Ask here and the CodeWalnut team will reply."
          load={load}
          send={send}
        />
      )}
    </Card>
  )
}

/**
 * What a client contact (e.g. Blend's hiring manager) sees: only the candidates and the parts
 * CodeWalnut shared with their company (docs/features/client-access.md).
 */
export function ClientHomePage({ client }: { client: ClientMe }) {
  const { signOut } = useAuth()
  const [candidates, setCandidates] = useState<ClientCandidate[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listSharedCandidates()
      .then(setCandidates)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load candidates'))
  }, [])

  return (
    <div className="candidate">
      <header className="candidate-bar">
        <div className="brand">
          <img src="/favicon.svg" alt="" />
          <span>CodeWalnut × {client.clientName}</span>
        </div>
        <div className="row">
          <span className="muted">{client.email}</span>
          <Button variant="secondary" size="sm" onClick={() => void signOut()}>
            Sign out
          </Button>
        </div>
      </header>
      <main className="candidate-main stack">
        <PageHeader
          title={`Hi${client.name ? ` ${client.name.split(' ')[0]}` : ''}`}
          description={`Candidates CodeWalnut has shared with ${client.clientName}, with their details and documents.`}
        />
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        {!candidates && !error && <p className="muted">Loading…</p>}
        {candidates && candidates.length === 0 && (
          <Card>
            <p className="muted" style={{ margin: 0 }}>
              Nothing has been shared with you yet. CodeWalnut will let you know when candidates are ready.
            </p>
          </Card>
        )}
        {candidates?.map((c) => <CandidateCard key={c.applicationId} c={c} />)}
      </main>
    </div>
  )
}
