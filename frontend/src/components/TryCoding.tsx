import { useState } from 'react'
import { LANGUAGE_LABEL, tryQuestion, type CodeLanguage, type CodingView, type RunCodeResult } from '../api/assessments'
import { CodeEditor } from './CodeEditor'
import { RunResults } from './RunResults'
import { Button } from './ui'

/** Staff checking a coding question: run a solution against every test, hidden ones included (ADR-0016). */
export function TryCoding({ assessmentId, questionId, coding }: { assessmentId: string; questionId: string; coding: CodingView }) {
  const [open, setOpen] = useState(false)
  const [language, setLanguage] = useState<CodeLanguage>(coding.spec.languages[0])
  const [source, setSource] = useState(coding.spec.starter[coding.spec.languages[0]] ?? '')
  const [result, setResult] = useState<RunCodeResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!open) {
    return (
      <div>
        <Button size="sm" variant="ghost" onClick={() => setOpen(true)}>
          Try a solution
        </Button>
      </div>
    )
  }
  async function run() {
    setBusy(true)
    setError(null)
    try {
      setResult(await tryQuestion(assessmentId, questionId, { language, source }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not run the code')
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="stack" style={{ gap: 6 }}>
      <div className="row" style={{ gap: 8 }}>
        <select
          className="input"
          style={{ width: 'auto' }}
          aria-label="Language"
          value={language}
          onChange={(e) => {
            const l = e.target.value as CodeLanguage
            setLanguage(l)
            setSource(coding.spec.starter[l] ?? '')
            setResult(null)
          }}
        >
          {coding.spec.languages.map((l) => (
            <option key={l} value={l}>
              {LANGUAGE_LABEL[l]}
            </option>
          ))}
        </select>
        <Button size="sm" disabled={busy || !source.trim()} onClick={() => void run()}>
          {busy ? 'Running…' : 'Run on all tests'}
        </Button>
        <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>
          Close
        </Button>
      </div>
      <CodeEditor label="Solution to try" value={source} onChange={setSource} minLines={10} />
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {result && <RunResults cases={result.cases} compileOutput={result.compileOutput} passed={result.passed} total={result.total} title="All tests" />}
    </div>
  )
}
