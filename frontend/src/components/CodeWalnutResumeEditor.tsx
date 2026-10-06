import { useEffect, useState } from 'react'
import {
  codeWalnutResumeDocxUrl,
  codeWalnutResumePdfUrl,
  generateCodeWalnutResume,
  getCodeWalnutResume,
  saveCodeWalnutResume,
  updateCodeWalnutResume,
  type BrandedResume,
  type DraftResponse,
  type Entry,
} from '../api/codewalnutResume'
import { Badge, Button } from './ui'

const EMPTY_ENTRY: Entry = { title: '', subtitle: '', period: '', bullets: [] }

/** Lines ↔ bullets, keeping a trailing empty line while typing. */
const toLines = (bullets: string[]) => bullets.join('\n')
const fromLines = (text: string) => text.split('\n')

/**
 * The CodeWalnut résumé for this candidate and opening (ADR-0012): the AI drafts it from the
 * original résumé, a person edits it, then saves the PDF to the candidate's documents for sharing.
 */
export function CodeWalnutResumeEditor({
  applicationId,
  canEdit,
  onSaved,
}: {
  applicationId: string
  canEdit: boolean
  onSaved: () => void
}) {
  const [draft, setDraft] = useState<DraftResponse | null>(null)
  const [resume, setResume] = useState<BrandedResume | null>(null)
  const [includeScreening, setIncludeScreening] = useState(true)
  const [open, setOpen] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  function take(d: DraftResponse) {
    setDraft(d)
    setResume(d.resume)
    setIncludeScreening(d.includeScreening)
    setDirty(false)
  }

  useEffect(() => {
    getCodeWalnutResume(applicationId).then(take).catch(() => setDraft(null))
  }, [applicationId])

  function edit(change: (r: BrandedResume) => BrandedResume) {
    setResume((r) => (r ? change(r) : r))
    setDirty(true)
  }

  async function run(label: string, action: () => Promise<void>) {
    setBusy(label)
    setError(null)
    setMessage(null)
    try {
      await action()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    } finally {
      setBusy(null)
    }
  }

  const persist = async () => {
    if (resume && dirty) take(await updateCodeWalnutResume(applicationId, { resume, showEmail: true, includeScreening }))
  }

  const generate = () =>
    run('generate', async () => {
      if (draft?.exists && !window.confirm('Make a fresh AI draft? Your edits to the current one will be replaced.')) return
      take(await generateCodeWalnutResume(applicationId))
      setOpen(true)
      setMessage('Draft ready. Check every line against the original résumé, then save.')
    })

  if (!draft) return null
  return (
    <section className="stack" aria-label="CodeWalnut résumé" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="row" style={{ gap: 8 }}>
          <h3 style={{ margin: 0 }}>CodeWalnut résumé</h3>
          {draft.exists && draft.savedDocumentId && <Badge>Saved to documents</Badge>}
        </span>
        <span className="row" style={{ gap: 6 }}>
          {draft.exists && (
            <Button size="sm" variant="ghost" onClick={() => setOpen(!open)}>
              {open ? 'Hide' : 'Edit'}
            </Button>
          )}
          {canEdit && draft.aiAvailable && (
            <Button size="sm" variant={draft.exists ? 'ghost' : 'secondary'} disabled={!!busy} onClick={() => void generate()}>
              {busy === 'generate' ? 'Writing… (up to a minute)' : draft.exists ? '✨ Re-create' : '✨ Create with AI'}
            </Button>
          )}
        </span>
      </div>
      {!draft.exists && (
        <p className="muted" style={{ margin: 0, fontSize: 14 }}>
          The client-ready version of their résumé, in CodeWalnut’s format: their mobile number removed, a summary tailored
          to this opening, and passed CodeWalnut tests. Made from the original résumé.
        </p>
      )}
      {message && <div className="alert alert-info">{message}</div>}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {draft.exists && resume && !resume.email.trim() && (
        <div className="alert alert-info">Add the candidate&apos;s email under Edit. CodeWalnut résumés always show it.</div>
      )}
      {draft.exists && resume && (
        <div className="row" style={{ gap: 6 }}>
          <Button
            size="sm"
            variant="secondary"
            disabled={!!busy}
            onClick={() =>
              void run('preview', async () => {
                await persist()
                window.open(codeWalnutResumePdfUrl(applicationId), '_blank')
              })
            }
          >
            Preview PDF
          </Button>
          <Button
            size="sm"
            variant="ghost"
            disabled={!!busy}
            onClick={() =>
              void run('docx', async () => {
                await persist()
                window.location.href = codeWalnutResumeDocxUrl(applicationId)
              })
            }
          >
            Download Word
          </Button>
          {canEdit && (
            <Button
              size="sm"
              disabled={!!busy || !resume.email.trim()}
              title={resume.email.trim() ? undefined : 'Add their email first'}
              onClick={() =>
                void run('save', async () => {
                  await persist()
                  const saved = await saveCodeWalnutResume(applicationId)
                  setDraft((d) => (d ? { ...d, savedDocumentId: saved.documentId } : d))
                  setMessage(`Saved “${saved.fileName}” as their CodeWalnut résumé. Share it with the client from “Share with client”.`)
                  onSaved()
                })
              }
            >
              {busy === 'save' ? 'Saving…' : dirty ? 'Save changes & PDF' : 'Save PDF to documents'}
            </Button>
          )}
        </div>
      )}
      {draft.exists && resume && open && (
        <div className="stack cwr-editor" style={{ gap: 10 }}>
          <p className="muted" style={{ margin: 0, fontSize: 13 }}>
            Made from {draft.sourceFileName ?? 'the original résumé'}
            {draft.updatedBy ? ` · last changed by ${draft.updatedBy}` : ''}. Phone numbers and social links are always removed; the email is always shown.
          </p>
          <fieldset disabled={!canEdit} className="stack" style={{ gap: 8, border: 0, padding: 0, margin: 0 }}>
            <div className="row" style={{ alignItems: 'flex-end' }}>
              <label className="field" style={{ flex: '1 1 180px' }}>
                Name
                <input className="input" value={resume.name} onChange={(e) => edit((r) => ({ ...r, name: e.target.value }))} />
              </label>
              <label className="field" style={{ flex: '1 1 160px' }}>
                City
                <input className="input" value={resume.location} onChange={(e) => edit((r) => ({ ...r, location: e.target.value }))} />
              </label>
            </div>
            <div className="row" style={{ alignItems: 'flex-end' }}>
              <label className="field" style={{ flex: '1 1 200px' }}>
                Email
                <input className="input" type="email" value={resume.email} onChange={(e) => edit((r) => ({ ...r, email: e.target.value }))} />
              </label>
              <label className="field" style={{ flex: '1 1 200px' }}>
                GitHub or portfolio (optional)
                <input
                  className="input"
                  placeholder="github.com/username"
                  value={resume.link ?? ''}
                  onChange={(e) => edit((r) => ({ ...r, link: e.target.value }))}
                />
              </label>
            </div>
            <label className="field">
              Title line
              <input className="input" value={resume.headline} onChange={(e) => edit((r) => ({ ...r, headline: e.target.value }))} />
            </label>
            <label className="field">
              Summary
              <textarea className="input" rows={3} value={resume.summary} onChange={(e) => edit((r) => ({ ...r, summary: e.target.value }))} />
            </label>
            <div className="row" style={{ gap: 16 }}>
              <label className="row" style={{ gap: 6 }}>
                <input
                  type="checkbox"
                  checked={includeScreening}
                  onChange={(e) => {
                    setIncludeScreening(e.target.checked)
                    setDirty(true)
                  }}
                />{' '}
                Add “CodeWalnut screening” {draft.screening.length ? `(${draft.screening.join('; ')})` : '(no passed tests yet)'}
              </label>
            </div>

            <strong>Technical skills</strong>
            {resume.skills.map((g, gi) => (
              <div key={gi} className="row" style={{ gap: 6, flexWrap: 'nowrap' }}>
                <input
                  className="input"
                  aria-label={`Skill group ${gi + 1} label`}
                  style={{ maxWidth: 160 }}
                  value={g.label}
                  onChange={(e) => edit((r) => ({ ...r, skills: r.skills.map((x, i) => (i === gi ? { ...x, label: e.target.value } : x)) }))}
                />
                <input
                  className="input"
                  aria-label={`Skill group ${gi + 1} skills`}
                  value={g.items.join(', ')}
                  onChange={(e) =>
                    edit((r) => ({ ...r, skills: r.skills.map((x, i) => (i === gi ? { ...x, items: e.target.value.split(',').map((s) => s.trimStart()) } : x)) }))
                  }
                />
                <Button size="sm" variant="ghost" onClick={() => edit((r) => ({ ...r, skills: r.skills.filter((_x, i) => i !== gi) }))}>
                  ✕
                </Button>
              </div>
            ))}
            <div>
              <Button size="sm" variant="ghost" onClick={() => edit((r) => ({ ...r, skills: [...r.skills, { label: '', items: [] }] }))}>
                + Skill group
              </Button>
            </div>

            {resume.sections.map((s, si) => (
              <div key={si} className="stack cwr-section" style={{ gap: 6 }}>
                <div className="row" style={{ gap: 6, flexWrap: 'nowrap' }}>
                  <input
                    className="input"
                    aria-label={`Section ${si + 1} title`}
                    style={{ fontWeight: 600 }}
                    value={s.title}
                    onChange={(e) => edit((r) => ({ ...r, sections: r.sections.map((x, i) => (i === si ? { ...x, title: e.target.value } : x)) }))}
                  />
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={si === 0}
                    aria-label={`Move section ${si + 1} up`}
                    onClick={() =>
                      edit((r) => {
                        const next = [...r.sections]
                        ;[next[si - 1], next[si]] = [next[si], next[si - 1]]
                        return { ...r, sections: next }
                      })
                    }
                  >
                    ↑
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => edit((r) => ({ ...r, sections: r.sections.filter((_x, i) => i !== si) }))}>
                    Remove section
                  </Button>
                </div>
                {s.entries.map((en, ei) => {
                  const setEntry = (change: Partial<Entry>) =>
                    edit((r) => ({
                      ...r,
                      sections: r.sections.map((x, i) =>
                        i === si ? { ...x, entries: x.entries.map((y, j) => (j === ei ? { ...y, ...change } : y)) } : x,
                      ),
                    }))
                  return (
                    <div key={ei} className="stack" style={{ gap: 4, paddingLeft: 10 }}>
                      <div className="row" style={{ gap: 6 }}>
                        <input className="input" placeholder="Role / project / degree" style={{ flex: '2 1 160px' }} value={en.title} onChange={(e) => setEntry({ title: e.target.value })} />
                        <input className="input" placeholder="Organisation / stack / institution" style={{ flex: '2 1 160px' }} value={en.subtitle} onChange={(e) => setEntry({ subtitle: e.target.value })} />
                        <input className="input" placeholder="Dates" style={{ flex: '1 1 100px' }} value={en.period} onChange={(e) => setEntry({ period: e.target.value })} />
                      </div>
                      <textarea
                        className="input"
                        rows={Math.max(2, en.bullets.length)}
                        placeholder="One bullet per line"
                        aria-label={`Bullets for ${s.title} ${ei + 1}`}
                        value={toLines(en.bullets)}
                        onChange={(e) => setEntry({ bullets: fromLines(e.target.value) })}
                      />
                      <div>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() =>
                            edit((r) => ({ ...r, sections: r.sections.map((x, i) => (i === si ? { ...x, entries: x.entries.filter((_y, j) => j !== ei) } : x)) }))
                          }
                        >
                          Remove entry
                        </Button>
                      </div>
                    </div>
                  )
                })}
                <div>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => edit((r) => ({ ...r, sections: r.sections.map((x, i) => (i === si ? { ...x, entries: [...x.entries, { ...EMPTY_ENTRY }] } : x)) }))}
                  >
                    + Entry
                  </Button>
                </div>
              </div>
            ))}
            <div>
              <Button size="sm" variant="ghost" onClick={() => edit((r) => ({ ...r, sections: [...r.sections, { title: 'New section', entries: [{ ...EMPTY_ENTRY }] }] }))}>
                + Section
              </Button>
            </div>
          </fieldset>
          {canEdit && dirty && (
            <div className="row">
              <Button size="sm" disabled={!!busy} onClick={() => void run('update', persist)}>
                Save changes
              </Button>
              <span className="muted" style={{ fontSize: 13 }}>
                Unsaved changes
              </span>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
