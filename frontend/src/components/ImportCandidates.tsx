import { useState } from 'react'
import { importCandidates, type ImportOutcome, type ImportResult, type Stage } from '../api/tracker'
import { StageSelect } from './StageSelect'
import { Badge, Button, Card } from './ui'

const OUTCOME: Record<ImportOutcome, { text: string; tone: 'primary' | 'neutral' | 'danger' }> = {
  NEW: { text: 'Will be added', tone: 'primary' },
  EXISTING_CANDIDATE: { text: 'Known person — will be added', tone: 'primary' },
  ALREADY_IN_OPENING: { text: 'Already in this opening — skipped', tone: 'neutral' },
  DUPLICATE_IN_PASTE: { text: 'Duplicate row — skipped', tone: 'neutral' },
  ERROR: { text: 'Cannot add — skipped', tone: 'danger' },
}

/** Paste rows from Excel / Google Sheets, preview, then import. */
export function ImportCandidates({ jobId, onDone, onCancel }: { jobId: string; onDone: (added: number) => void; onCancel: () => void }) {
  const [text, setText] = useState('')
  const [stage, setStage] = useState<Stage>('SOURCED')
  const [preview, setPreview] = useState<ImportResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(dryRun: boolean) {
    setError(null)
    setBusy(true)
    try {
      const result = await importCandidates(jobId, text, stage, dryRun)
      if (dryRun) setPreview(result)
      else onDone(result.added)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Import failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="stack">
      <h2>Import from a spreadsheet</h2>
      <p className="muted" style={{ margin: 0 }}>
        Copy the rows (with the header row: name, email, phone) from Excel or Google Sheets and paste below. Nothing is
        saved until you click Import.
      </p>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <textarea
        aria-label="Pasted rows"
        className="textarea"
        value={text}
        onChange={(e) => {
          setText(e.target.value)
          setPreview(null)
        }}
        placeholder={'slno\tname\temail\tphone\n1\tAsha Rao\tasha@example.com\t9000000000'}
      />
      <div className="row">
        <span>Put everyone in stage</span>
        <StageSelect label="Import stage" value={stage} onChange={(s) => { setStage(s); setPreview(null) }} />
        <Button variant="secondary" disabled={!text.trim() || busy} onClick={() => void run(true)}>
          Preview
        </Button>
        <Button disabled={!preview || preview.added === 0 || busy} onClick={() => void run(false)}>
          {preview ? `Import ${preview.added}` : 'Import'}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
      {preview && (
        <div className="table-wrap">
          <p style={{ margin: 0 }}>
            <strong>{preview.added}</strong> will be added, {preview.skipped} skipped.
          </p>
          <table className="table">
            <thead>
              <tr>
                <th>Row</th>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Result</th>
              </tr>
            </thead>
            <tbody>
              {preview.rows.map((r) => (
                <tr key={r.line}>
                  <td className="muted">{r.line}</td>
                  <td>{r.name || '—'}</td>
                  <td>{r.email ?? '—'}</td>
                  <td>{r.phone ?? '—'}</td>
                  <td>
                    <Badge tone={OUTCOME[r.outcome].tone}>{OUTCOME[r.outcome].text}</Badge>
                    {r.issues.map((i) => (
                      <div key={i} className="issue">
                        {i}
                      </div>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
