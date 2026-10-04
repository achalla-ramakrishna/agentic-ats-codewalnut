import { CASE_STATUS_LABEL, type CaseResult } from '../api/assessments'
import { Badge } from './ui'

function clip(s: string | null, max = 600) {
  if (s == null) return ''
  return s.length > max ? s.slice(0, max) + '\n…' : s
}

/**
 * Test-case results for a piece of code (ADR-0016). Candidates see their sample runs; staff see
 * every case, including hidden inputs and expected output.
 */
export function RunResults({
  cases,
  compileOutput,
  passed,
  total,
  title = 'Results',
}: {
  cases: CaseResult[]
  compileOutput: string | null
  passed: number
  total: number
  title?: string
}) {
  return (
    <div className="stack run-results" style={{ gap: 8 }} aria-label={title}>
      <div className="row" style={{ gap: 8 }}>
        <strong>{title}</strong>
        <Badge tone={passed === total && total > 0 ? 'primary' : passed === 0 ? 'danger' : 'neutral'}>
          {passed} of {total} passed
        </Badge>
      </div>
      {compileOutput && (
        <div>
          <span className="muted" style={{ fontSize: 13 }}>
            The code didn’t compile:
          </span>
          <pre className="test-code">{clip(compileOutput, 2000)}</pre>
        </div>
      )}
      {!compileOutput &&
        cases.map((c, i) => (
          <details key={i} className="run-case" open={!c.passed && i < 3}>
            <summary className="row" style={{ gap: 8 }}>
              <span>
                {c.sample ? 'Sample' : 'Hidden test'} {i + 1}
              </span>
              <Badge tone={c.passed ? 'primary' : 'danger'}>{CASE_STATUS_LABEL[c.status] ?? c.status}</Badge>
              {c.timeSeconds != null && <span className="muted" style={{ fontSize: 12 }}>{c.timeSeconds.toFixed(2)} s</span>}
            </summary>
            <div className="run-case-grid">
              {c.input != null && (
                <div>
                  <span className="muted">Input</span>
                  <pre className="test-code">{clip(c.input)}</pre>
                </div>
              )}
              {c.expected != null && (
                <div>
                  <span className="muted">Expected</span>
                  <pre className="test-code">{clip(c.expected)}</pre>
                </div>
              )}
              <div>
                <span className="muted">Output</span>
                <pre className="test-code">{clip(c.output) || '(nothing)'}</pre>
              </div>
              {c.error && (
                <div>
                  <span className="muted">Error</span>
                  <pre className="test-code">{clip(c.error)}</pre>
                </div>
              )}
            </div>
          </details>
        ))}
    </div>
  )
}
