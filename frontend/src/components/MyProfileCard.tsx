import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { getMyDocuments, getMyProfile, updateMyProfile, uploadMyDocument, type CandidateProfile, type DocumentRequest, type MyDocument } from '../api/profile'
import { CANDIDATE_KINDS, DOCUMENT_LABELS, type DocumentKind } from '../api/tracker'
import { diffProfile, ProfileFields } from './ProfileSection'
import { Badge, Button, Card } from './ui'

type Draft = Record<string, string>

function draftOf(p: CandidateProfile): Draft {
  return {
    name: p.name,
    phone: p.phone ?? '',
    dateOfBirth: p.dateOfBirth ?? '',
    currentAddress: p.currentAddress ?? '',
    permanentAddress: p.permanentAddress ?? '',
    college: p.college ?? '',
    degree: p.degree ?? '',
    graduationYear: p.graduationYear ? String(p.graduationYear) : '',
    linkedinUrl: p.linkedinUrl ?? '',
    emergencyContact: p.emergencyContact ?? '',
  }
}

/**
 * The candidate's own profile and documents (docs/features/candidate-profile-and-bgv.md). Shown
 * once they have an application. Requested documents come first.
 */
export function MyProfileCard() {
  const [profile, setProfile] = useState<CandidateProfile | null>(null)
  const [missing, setMissing] = useState(false)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [documents, setDocuments] = useState<MyDocument[]>([])
  const [requested, setRequested] = useState<DocumentRequest[]>([])
  const [busy, setBusy] = useState<DocumentKind | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadDocuments = useCallback(() => {
    getMyDocuments()
      .then((d) => {
        setDocuments(d.documents)
        setRequested(d.requested)
      })
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    getMyProfile()
      .then((p) => {
        setProfile(p)
        setDraft(draftOf(p))
        loadDocuments()
      })
      .catch(() => setMissing(true))
  }, [loadDocuments])

  if (missing || !profile || !draft) return null

  async function onSave(event: FormEvent) {
    event.preventDefault()
    if (!profile || !draft) return
    setError(null)
    setNotice(null)
    try {
      const saved = await updateMyProfile(diffProfile(draftOf(profile), draft))
      setProfile(saved)
      setDraft(draftOf(saved))
      setNotice('Profile saved.')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
    }
  }

  async function onUpload(kind: DocumentKind, file: File) {
    setError(null)
    setNotice(null)
    setBusy(kind)
    try {
      await uploadMyDocument(kind, file)
      setNotice(`${DOCUMENT_LABELS[kind]} uploaded. Thank you!`)
      loadDocuments()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed')
    } finally {
      setBusy(null)
    }
  }

  const requestedKinds = requested.map((r) => r.kind)
  const order = [...CANDIDATE_KINDS].sort((a, b) => Number(requestedKinds.includes(b)) - Number(requestedKinds.includes(a)))

  return (
    <Card className="stack" style={{ marginTop: 16 }}>
      <h2 style={{ margin: 0 }}>My profile &amp; documents</h2>
      {requested.length > 0 && (
        <div className="alert alert-info">
          CodeWalnut has asked you to upload: <strong>{requested.map((r) => r.label).join(', ')}</strong>.
        </div>
      )}
      {notice && <div className="alert alert-info">{notice}</div>}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <div className="stack" style={{ gap: 8 }} aria-label="My documents" role="group">
        <strong>Documents</strong>
        <span className="muted" style={{ fontSize: 13 }}>
          PDF, Word, JPG or PNG, up to 10 MB. For Aadhaar, please upload the <strong>masked</strong> Aadhaar (only the last 4 digits
          visible), which you can download from myaadhaar.uidai.gov.in.
        </span>
        {order.map((kind) => {
          const current = documents.find((d) => d.kind === kind)
          const asked = requestedKinds.includes(kind)
          const inputId = `my-upload-${kind}`
          return (
            <div key={kind} className="row" style={{ gap: 12, flexWrap: 'wrap' }}>
              <span style={{ minWidth: 230 }}>
                {DOCUMENT_LABELS[kind]} {asked && <Badge tone="primary">Requested</Badge>}
              </span>
              <span className="muted" style={{ fontSize: 12, minWidth: 160 }}>
                {current ? `${current.fileName} · ${new Date(current.uploadedAt).toLocaleDateString()}` : 'Not uploaded'}
              </span>
              <label htmlFor={inputId} className={`btn btn-sm ${asked ? 'btn-primary' : 'btn-secondary'}`}>
                {busy === kind ? 'Uploading…' : current ? 'Replace' : 'Upload'}
                <input
                  id={inputId}
                  type="file"
                  accept={kind === 'ORIGINAL_RESUME' ? '.pdf,.doc,.docx' : '.pdf,.doc,.docx,.jpg,.jpeg,.png'}
                  className="visually-hidden"
                  aria-label={`Upload ${DOCUMENT_LABELS[kind]}`}
                  disabled={busy !== null}
                  onChange={(e) => {
                    const file = e.target.files?.[0]
                    e.target.value = ''
                    if (file) void onUpload(kind, file)
                  }}
                />
              </label>
            </div>
          )
        })}
      </div>
      <form className="stack" onSubmit={onSave} aria-label="My profile" style={{ gap: 8 }}>
        <strong>Profile</strong>
        <span className="muted" style={{ fontSize: 13 }}>Email: {profile.email} (your Google sign-in)</span>
        <ProfileFields draft={draft} setDraft={setDraft} includeIdentity={false} />
        <div>
          <Button type="submit" size="sm">
            Save profile
          </Button>
        </div>
      </form>
    </Card>
  )
}
