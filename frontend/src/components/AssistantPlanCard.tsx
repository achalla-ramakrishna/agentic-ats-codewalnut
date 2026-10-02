import { useState } from 'react'
import type { AssistantPlan, ProposedAction } from '../api/assistant'
import { addNote, moveStage } from '../api/tracker'
import { Badge, Button } from './ui'

const NEEDS_REASON = ['REJECTED', 'WITHDRAWN']

interface Row extends ProposedAction {
  key: string
  checked: boolean
  reason: string
}

/**
 * What the AI assistant proposes, for a person to check and apply. Nothing changes until
 * "Apply" — and then through the same stage and note actions as doing it by hand (ADR-0009).
 */
export function AssistantPlanCard({ plan, onApplied, onClose }: { plan: AssistantPlan; onApplied: () => void; onClose: () => void }) {
  const [rows, setRows] = useState<Row[]>(() =>
    plan.actions.map((a, i) => ({ ...a, key: `a${i}`, checked: true, reason: a.note ?? '' })),
  )
  const [picked, setPicked] = useState<Record<number, string>>({})
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<string | null>(null)
  const [errors, setErrors] = useState<string[]>([])

  function pick(index: number, option: AssistantPlan['unresolved'][number]['options'][number]) {
    const u = plan.unresolved[index]
    if (!u.toStage) return
    setPicked((p) => ({ ...p, [index]: option.applicationId }))
    setRows((r) => [
      ...r.filter((x) => x.key !== `u${index}`),
      {
        key: `u${index}`,
        type: 'MOVE_STAGE',
        applicationId: option.applicationId,
        candidateName: option.name,
        fromStage: 'SOURCED',
        fromLabel: option.stageLabel,
        toStage: u.toStage,
        toLabel: u.toLabel,
        note: u.note,
        needsReason: NEEDS_REASON.includes(u.toStage!) && !u.note,
        checked: true,
        reason: u.note ?? '',
      },
    ])
  }

  const chosen = rows.filter((r) => r.checked)
  const missingReason = chosen.some((r) => r.type === 'MOVE_STAGE' && NEEDS_REASON.includes(r.toStage ?? '') && !r.reason.trim())

  async function apply() {
    setBusy(true)
    setErrors([])
    const failed: string[] = []
    let done = 0
    for (const r of chosen) {
      try {
        if (r.type === 'MOVE_STAGE' && r.toStage) await moveStage(r.applicationId, r.toStage, r.reason.trim() || undefined)
        else if (r.type === 'ADD_NOTE' && r.note) await addNote(r.applicationId, r.note)
        done++
      } catch (e) {
        failed.push(`${r.candidateName}: ${e instanceof Error ? e.message : 'failed'}`)
      }
    }
    setBusy(false)
    setErrors(failed)
    setResult(`Applied ${done} change${done === 1 ? '' : 's'}.`)
    setRows((all) => all.filter((r) => !r.checked || failed.some((f) => f.startsWith(`${r.candidateName}:`))))
    onApplied()
  }

  return (
    <div className="card stack" role="region" aria-label="AI assistant suggestion" style={{ gap: 10, borderColor: 'var(--color-primary)' }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="row" style={{ gap: 8 }}>
          <Badge tone="primary">Suggested by AI</Badge>
          <span className="muted" style={{ fontSize: 13 }}>
            Check before applying. Nothing has changed yet.
          </span>
        </span>
        <Button size="sm" variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
      <div style={{ fontSize: 14 }}>
        <span className="muted">You asked:</span> “{plan.instruction}”
      </div>
      {plan.summary && <div>{plan.summary}</div>}
      {rows.length > 0 && (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }} className="stack">
          {rows.map((r) => (
            <li key={r.key} className="stack" style={{ gap: 4 }}>
              <label className="row" style={{ gap: 8, flexWrap: 'nowrap', alignItems: 'flex-start' }}>
                <input
                  type="checkbox"
                  checked={r.checked}
                  onChange={(e) => setRows((all) => all.map((x) => (x.key === r.key ? { ...x, checked: e.target.checked } : x)))}
                />
                <span>
                  <strong>{r.candidateName}</strong>{' '}
                  {r.type === 'MOVE_STAGE' ? (
                    <>
                      {r.fromLabel} → <strong>{r.toLabel}</strong>
                    </>
                  ) : (
                    <>add note: “{r.note}”</>
                  )}
                </span>
              </label>
              {r.type === 'MOVE_STAGE' && NEEDS_REASON.includes(r.toStage ?? '') && r.checked && (
                <input
                  className="input"
                  aria-label={`Reason for ${r.candidateName}`}
                  placeholder="Reason (required)"
                  value={r.reason}
                  onChange={(e) => setRows((all) => all.map((x) => (x.key === r.key ? { ...x, reason: e.target.value } : x)))}
                  style={{ marginLeft: 24, maxWidth: 420 }}
                />
              )}
            </li>
          ))}
        </ul>
      )}
      {plan.unresolved.map((u, i) => (
        <div key={`${u.mention}-${i}`} style={{ fontSize: 14 }}>
          {u.options.length === 0 ? (
            <span className="muted">“{u.mention}”: no one by that name in this opening.</span>
          ) : (
            <span className="row" style={{ gap: 6 }}>
              <span>
                Which “{u.mention}”{u.toLabel ? ` to move to ${u.toLabel}` : ''}?
              </span>
              {u.options.map((o) => (
                <Button
                  key={o.applicationId}
                  size="sm"
                  variant={picked[i] === o.applicationId ? 'primary' : 'secondary'}
                  onClick={() => pick(i, o)}
                  disabled={!u.toStage}
                >
                  {o.name} ({o.stageLabel})
                </Button>
              ))}
            </span>
          )}
        </div>
      ))}
      {plan.notes.map((n) => (
        <div key={n} className="muted" style={{ fontSize: 13 }}>
          {n}
        </div>
      ))}
      {result && <div className="alert alert-info">{result}</div>}
      {errors.length > 0 && (
        <div role="alert" className="alert alert-error">
          {errors.join(' ')}
        </div>
      )}
      <div className="row">
        <Button disabled={busy || chosen.length === 0 || missingReason} onClick={() => void apply()}>
          {busy ? 'Applying…' : `Apply ${chosen.length} change${chosen.length === 1 ? '' : 's'}`}
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </div>
  )
}
