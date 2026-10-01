import { useEffect, useState, type FormEvent } from 'react'
import { getProfile, PROFILE_FIELDS, updateProfile, type CandidateProfile, type ProfilePatch } from '../api/profile'
import { Button } from './ui'

type Draft = Record<string, string>

function toDraft(p: CandidateProfile): Draft {
  return {
    name: p.name,
    email: p.email ?? '',
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

/** Only the fields that changed; blank clears (graduation year 0 clears). */
export function diffProfile(before: Draft, after: Draft): ProfilePatch {
  const patch: Record<string, string | number> = {}
  for (const key of Object.keys(after)) {
    if (after[key] === before[key]) continue
    patch[key] = key === 'graduationYear' ? Number(after[key] || 0) : after[key]
  }
  return patch as ProfilePatch
}

export function ProfileFields({ draft, setDraft, includeIdentity }: { draft: Draft; setDraft: (d: Draft) => void; includeIdentity: boolean }) {
  const field = (key: string, label: string, opts: { type?: string; multiline?: boolean; required?: boolean } = {}) => (
    <label className="field" key={key}>
      {label}
      {opts.multiline ? (
        <textarea
          className="textarea"
          style={{ minHeight: 56, fontFamily: 'inherit', fontSize: 14 }}
          value={draft[key] ?? ''}
          onChange={(e) => setDraft({ ...draft, [key]: e.target.value })}
        />
      ) : (
        <input
          className="input"
          type={opts.type ?? 'text'}
          required={opts.required}
          value={draft[key] ?? ''}
          onChange={(e) => setDraft({ ...draft, [key]: e.target.value })}
        />
      )}
    </label>
  )
  return (
    <>
      {includeIdentity && field('name', 'Full name', { required: true })}
      {includeIdentity && field('email', 'Email', { type: 'email' })}
      {field('phone', 'Mobile number', { type: 'tel' })}
      {PROFILE_FIELDS.map((f) => field(f.key, f.label, { type: f.type, multiline: f.multiline }))}
    </>
  )
}

/** Contact details and background-verification profile for a candidate, in the drawer. */
export function ProfileSection({
  candidateId,
  stageLabel,
  canEdit,
  onSaved,
}: {
  candidateId: string
  stageLabel: string
  canEdit: boolean
  onSaved: (p: CandidateProfile) => void
}) {
  const [profile, setProfile] = useState<CandidateProfile | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getProfile(candidateId).then(setProfile).catch(() => setProfile(null))
  }, [candidateId])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!profile || !draft) return
    setError(null)
    try {
      const saved = await updateProfile(candidateId, diffProfile(toDraft(profile), draft))
      setProfile(saved)
      setDraft(null)
      onSaved(saved)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
    }
  }

  if (!profile) return <span className="muted">Loading profile…</span>

  if (draft) {
    return (
      <form className="stack" onSubmit={onSubmit} aria-label="Candidate profile" style={{ gap: 8 }}>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <ProfileFields draft={draft} setDraft={setDraft} includeIdentity />
        <div className="row">
          <Button type="submit" size="sm">
            Save profile
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setDraft(null)}>
            Cancel
          </Button>
        </div>
      </form>
    )
  }

  const rows: [string, string | number | null][] = [
    ['Email', profile.email],
    ['Mobile', profile.phone],
    ['Stage', stageLabel],
    ['Date of birth', profile.dateOfBirth ? new Date(profile.dateOfBirth).toLocaleDateString() : null],
    ['Current address', profile.currentAddress],
    ['Permanent address', profile.permanentAddress],
    ['College', profile.college],
    ['Degree', profile.degree],
    ['Graduation year', profile.graduationYear],
    ['LinkedIn', profile.linkedinUrl],
    ['Emergency contact', profile.emergencyContact],
  ]
  return (
    <div className="stack" style={{ gap: 4 }}>
      {rows.map(([label, value]) => (
        <div key={label}>
          <span className="muted">{label}:</span> <span style={{ whiteSpace: 'pre-wrap' }}>{value ?? '—'}</span>
        </div>
      ))}
      {canEdit && (
        <div>
          <Button size="sm" variant="ghost" onClick={() => setDraft(toDraft(profile))}>
            Edit profile
          </Button>
        </div>
      )}
    </div>
  )
}
