import { useEffect, useMemo, useState } from 'react'
import { getGoogleStatus, type GoogleStatus } from '../api/interviews'
import { postMessage } from '../api/messages'
import { canAdd, importNaukri, rankNaukri, STATUS_LABEL, type NaukriImportResult, type RankResult } from '../api/naukri'
import type { Stage } from '../api/tracker'
import { ConnectGoogle } from './ConnectGoogle'
import { EMAIL_TEMPLATES, fillTemplate } from './emailTemplates'
import { StageSelect } from './StageSelect'
import { Badge, Button, Card } from './ui'

const REQUEST = EMAIL_TEMPLATES.find((t) => t.key === 'resume_request')!

/**
 * Naukri applicant export → ranked against the opening → chosen rows become candidates → one email
 * asking each for their résumé (ADR-0026). The file is never stored; only the rows you add are.
 */
export function NaukriImport({
  jobId,
  jobTitle,
  senderName,
  onAdded,
  onClose,
}: {
  jobId: string
  jobTitle: string
  senderName: string | null
  onAdded: () => void
  onClose: () => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<RankResult | null>(null)
  const [chosen, setChosen] = useState<Set<number>>(new Set())
  const [stage, setStage] = useState<Stage>('SOURCED')
  const [added, setAdded] = useState<NaukriImportResult | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const addable = useMemo(() => (result?.rows ?? []).map((r, i) => ({ r, i })).filter(({ r }) => canAdd(r)), [result])

  async function rank() {
    if (!file) return
    setError(null)
    setBusy('Reading and ranking… this can take a minute for a long list.')
    try {
      const r = await rankNaukri(jobId, file)
      setResult(r)
      // Start with the ten best that can be added; the recruiter changes the choice.
      setChosen(new Set(r.rows.map((row, i) => ({ row, i })).filter(({ row }) => canAdd(row)).slice(0, 10).map(({ i }) => i)))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not read the file')
    } finally {
      setBusy(null)
    }
  }

  async function add() {
    if (!result) return
    setError(null)
    setBusy('Adding…')
    try {
      const rows = result.rows.filter((_, i) => chosen.has(i))
      setAdded(await importNaukri(jobId, stage, rows))
      onAdded()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add the candidates')
    } finally {
      setBusy(null)
    }
  }

  const pick = (n: number | 'all' | 'none') =>
    setChosen(new Set(n === 'none' ? [] : (n === 'all' ? addable : addable.slice(0, n)).map(({ i }) => i)))

  return (
    <Card className="stack">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0 }}>Import from Naukri</h2>
        <Button variant="ghost" size="sm" onClick={onClose}>
          Close
        </Button>
      </div>

      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}

      {!added && (
        <>
          <p className="muted" style={{ margin: 0 }}>
            Upload the applicant Excel you downloaded from Naukri. Every row is ranked against this opening’s job description;
            you choose who to add. Nothing from the file is saved except the people you add.
          </p>
          <div className="row" style={{ gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <input
              type="file"
              aria-label="Naukri Excel file"
              accept=".xlsx,.csv"
              onChange={(e) => {
                setFile(e.target.files?.[0] ?? null)
                setResult(null)
              }}
            />
            <Button onClick={rank} disabled={!file || !!busy}>
              Rank against job description
            </Button>
          </div>
        </>
      )}
      {busy && <p className="muted" role="status">{busy}</p>}

      {result && !added && (
        <>
          {!result.hasDescription && (
            <div className="alert alert-error" role="note">
              This opening has no job description, so the ranking only uses the title. Add one under Job details for a better ranking.
            </div>
          )}
          <p style={{ margin: 0 }}>
            <strong>{result.total}</strong> applicants ranked {result.rankedBy === 'AI' ? 'by AI' : 'by rules'}.{' '}
            {result.note && <span className="muted">{result.note}</span>}
          </p>
          {result.missingColumns.length > 0 && (
            <p className="muted" style={{ margin: 0 }}>
              Columns not found: {result.missingColumns.join(', ')} — the ranking is less precise.
            </p>
          )}
          <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
            <Button size="sm" variant="secondary" onClick={() => pick(10)}>
              Top 10
            </Button>
            <Button size="sm" variant="secondary" onClick={() => pick(20)}>
              Top 20
            </Button>
            <Button size="sm" variant="secondary" onClick={() => pick(50)}>
              Top 50
            </Button>
            <Button size="sm" variant="secondary" onClick={() => pick('all')}>
              All who can be added ({addable.length})
            </Button>
            <Button size="sm" variant="ghost" onClick={() => pick('none')}>
              Clear
            </Button>
          </div>
          <div style={{ maxHeight: 520, overflow: 'auto' }}>
            <table className="send-list naukri-list" aria-label="Ranked applicants">
              <tbody>
                {result.rows.map((r, i) => {
                  const p = r.profile
                  const label = STATUS_LABEL[r.status]
                  return (
                    <tr key={`${p.line}-${i}`}>
                      <td>
                        <label className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
                          <input
                            type="checkbox"
                            aria-label={`Add ${p.name ?? `row ${p.line}`}`}
                            disabled={!canAdd(r)}
                            checked={chosen.has(i)}
                            onChange={(e) =>
                              setChosen((c) => {
                                const next = new Set(c)
                                if (e.target.checked) next.add(i)
                                else next.delete(i)
                                return next
                              })
                            }
                          />
                          <span>
                            <span className="muted">{i + 1}.</span> <strong>{p.name ?? '—'}</strong>
                            <br />
                            <span className="muted" style={{ fontSize: 12 }}>
                              {[p.experience, [p.designation, p.company].filter(Boolean).join(' at '), p.currentLocation, p.noticePeriod && `Notice ${p.noticePeriod}`]
                                .filter(Boolean)
                                .join(' · ')}
                            </span>
                          </span>
                        </label>
                      </td>
                      <td>
                        <div>{r.summary}</div>
                        {r.strengths.length > 0 && (
                          <div className="muted" style={{ fontSize: 12 }}>
                            Good: {r.strengths.join('; ')}
                          </div>
                        )}
                        {r.gaps.length > 0 && (
                          <div className="muted" style={{ fontSize: 12 }}>
                            Check: {r.gaps.join('; ')}
                          </div>
                        )}
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <Badge tone={r.matchPercent >= 70 ? 'primary' : 'neutral'}>{r.matchPercent}%</Badge>
                        {label && (
                          <>
                            {' '}
                            <Badge tone="neutral">{label}</Badge>
                          </>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="row" style={{ gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <StageSelect label="Add at stage" value={stage} onChange={setStage} />
            <Button onClick={add} disabled={!!busy || chosen.size === 0}>
              Add {chosen.size} to this opening
            </Button>
          </div>
        </>
      )}

      {added && <AskForResumes added={added} jobTitle={jobTitle} senderName={senderName} onClose={onClose} />}
    </Card>
  )
}

/** Step 3: one email to everyone just added, asking for their résumé and times for a call. */
function AskForResumes({
  added,
  jobTitle,
  senderName,
  onClose,
}: {
  added: NaukriImportResult
  jobTitle: string
  senderName: string | null
  onClose: () => void
}) {
  const sender = senderName ?? 'CodeWalnut Talent Team'
  const withEmail = added.rows.filter((r) => r.hasEmail)
  const [subject, setSubject] = useState(REQUEST.subject.replaceAll('{jobTitle}', jobTitle))
  const [body, setBody] = useState(REQUEST.body.replaceAll('{jobTitle}', jobTitle).replaceAll('{senderName}', sender))
  const [google, setGoogle] = useState<GoogleStatus | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [outcome, setOutcome] = useState<{ sent: number; failed: string[] } | null>(null)

  useEffect(() => {
    getGoogleStatus()
      .then(setGoogle)
      .catch(() => setGoogle(null))
  }, [])
  const needsGoogle = google !== null && (!google.available || !google.mailConnected)

  async function send() {
    let sent = 0
    const failed: string[] = []
    for (const [n, r] of withEmail.entries()) {
      setBusy(`Sending ${n + 1} of ${withEmail.length}…`)
      try {
        const values = { candidateName: r.name, jobTitle, senderName: sender }
        await postMessage(r.applicationId, {
          channel: 'CANDIDATE',
          subject: fillTemplate(subject, values),
          body: fillTemplate(body, values),
          sendEmail: true,
        })
        sent++
      } catch (e) {
        failed.push(`${r.name}: ${e instanceof Error ? e.message : 'not sent'}`)
      }
    }
    setBusy(null)
    setOutcome({ sent, failed })
  }

  return (
    <div className="stack" style={{ gap: 10 }}>
      <div className="alert alert-info" role="status">
        Added {added.added} candidate{added.added === 1 ? '' : 's'} to this opening.
        {added.skipped.length > 0 && ` Skipped ${added.skipped.length}: ${added.skipped.join('; ')}.`}
      </div>
      {outcome ? (
        <>
          <div className={`alert ${outcome.failed.length ? 'alert-error' : 'alert-info'}`} role="status">
            Emailed {outcome.sent} candidate{outcome.sent === 1 ? '' : 's'}.
            {outcome.failed.length > 0 && ` Not sent: ${outcome.failed.join('; ')}.`} Replies come to your Gmail and show under Messages
            and Workflow → Waiting for our reply. When someone sends their résumé, open them and Schedule interview.
          </div>
          <div className="row">
            <Button onClick={onClose}>Done</Button>
          </div>
        </>
      ) : (
        <>
          <h3 style={{ margin: 0 }}>Ask them for their résumé</h3>
          <p className="muted" style={{ margin: 0, fontSize: 13 }}>
            Each person gets their own email from your Gmail ({'{firstName}'} becomes their first name). Check the text, then send.
            {withEmail.length < added.rows.length && ` ${added.rows.length - withEmail.length} without an email won’t get it.`}
          </p>
          <label className="field">
            Subject
            <input className="input" value={subject} onChange={(e) => setSubject(e.target.value)} />
          </label>
          <label className="field">
            Message
            <textarea className="input" rows={12} value={body} onChange={(e) => setBody(e.target.value)} />
          </label>
          {needsGoogle && (
            <ConnectGoogle status={google} purpose="Emails are sent from your own Gmail, so replies come back to your inbox." returnTo={window.location.pathname} />
          )}
          <div className="row" style={{ gap: 8 }}>
            <Button onClick={send} disabled={!!busy || withEmail.length === 0 || needsGoogle || !subject.trim() || !body.trim()}>
              {busy ?? `Send email to ${withEmail.length}`}
            </Button>
            <Button variant="ghost" onClick={onClose}>
              Not now
            </Button>
          </div>
        </>
      )}
    </div>
  )
}
