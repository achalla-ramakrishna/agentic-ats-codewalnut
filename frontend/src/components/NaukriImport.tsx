import { useMemo, useState } from 'react'
import { canAdd, importNaukri, rankNaukri, STATUS_LABEL, type NaukriImportResult, type RankResult } from '../api/naukri'
import type { Stage } from '../api/tracker'
import { BatchSizeInput, useBatchSize } from './BatchSize'
import { BulkEmail } from './BulkEmail'
import { StageSelect } from './StageSelect'
import { Badge, Button, Card } from './ui'

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
  const [batch, setBatch] = useBatchSize()
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
      // Start with one batch of the best who can be added; the recruiter changes the choice.
      setChosen(new Set(r.rows.map((row, i) => ({ row, i })).filter(({ row }) => canAdd(row)).slice(0, batch).map(({ i }) => i)))
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
          <div className="row" style={{ gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <BatchSizeInput value={batch} onChange={setBatch} />
            <Button size="sm" variant="secondary" onClick={() => pick(batch)}>
              Select top {Math.min(batch, addable.length)}
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

      {added && (
        <>
          <div className="alert alert-info" role="status">
            Added {added.added} candidate{added.added === 1 ? '' : 's'} to this opening.
            {added.skipped.length > 0 && ` Skipped ${added.skipped.length}: ${added.skipped.join('; ')}.`} For the next batch, open Import from
            Naukri again with the same file: people already added can’t be picked twice.
          </div>
          <BulkEmail recipients={added.rows} jobTitle={jobTitle} senderName={senderName} title="Ask them for their résumé" onClose={onClose} />
        </>
      )}
    </Card>
  )
}
