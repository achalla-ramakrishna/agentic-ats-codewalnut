import { useState } from 'react'
import { LANGUAGE_LABEL, runCode, type CodeLanguage, type CodingSpec, type RunCodeResult } from '../api/assessments'
import { CodeEditor } from './CodeEditor'
import { RunResults } from './RunResults'
import { Button } from './ui'

/**
 * A coding question while taking a test (ADR-0016). The answer is [language, source]. Run checks
 * the code against the sample tests only (a limited number of times); on submit it is graded
 * against hidden tests too.
 */
export function CodingQuestion({
  testId,
  questionId,
  position,
  spec,
  value,
  onChange,
  onPaste,
}: {
  testId: string
  questionId: string
  position: number
  spec: CodingSpec
  value: string[]
  onChange: (value: string[]) => void
  onPaste: (chars: number) => void
}) {
  const language = (spec.languages.includes(value[0] as CodeLanguage) ? value[0] : spec.languages[0]) as CodeLanguage
  const source = value[1] ?? spec.starter[language] ?? ''
  // Code written in other languages, kept while switching back and forth.
  const [drafts, setDrafts] = useState<Partial<Record<CodeLanguage, string>>>({})
  const [result, setResult] = useState<RunCodeResult | null>(null)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function switchTo(next: CodeLanguage) {
    if (next === language) return
    setDrafts((d) => ({ ...d, [language]: source }))
    onChange([next, drafts[next] ?? spec.starter[next] ?? ''])
    setResult(null)
  }

  async function run() {
    setRunning(true)
    setError(null)
    try {
      onChange([language, source])
      setResult(await runCode(testId, questionId, { language, source }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not run the code')
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="stack" style={{ gap: 10 }}>
      {(spec.inputFormat || spec.outputFormat || spec.constraints) && (
        <dl className="coding-spec">
          {spec.inputFormat && (
            <>
              <dt>Input</dt>
              <dd>{spec.inputFormat}</dd>
            </>
          )}
          {spec.outputFormat && (
            <>
              <dt>Output</dt>
              <dd>{spec.outputFormat}</dd>
            </>
          )}
          {spec.constraints && (
            <>
              <dt>Limits</dt>
              <dd>{spec.constraints}</dd>
            </>
          )}
        </dl>
      )}
      {spec.samples.map((s, i) => (
        <div key={i} className="run-case-grid">
          <div>
            <span className="muted">Sample input {spec.samples.length > 1 ? i + 1 : ''}</span>
            <pre className="test-code">{s.input || '(empty)'}</pre>
          </div>
          <div>
            <span className="muted">Expected output</span>
            <pre className="test-code">{s.output}</pre>
          </div>
        </div>
      ))}
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        Read the input from standard input and print the answer to standard output, exactly as shown. Your code is checked against these
        samples and hidden tests; you get points for each test it passes. Time limit {spec.timeLimitSeconds} s per test.
      </p>
      <div className="row" style={{ gap: 8 }}>
        <label className="row" style={{ gap: 6 }}>
          <span className="muted" style={{ fontSize: 13 }}>
            Language
          </span>
          <select
            className="input"
            style={{ width: 'auto' }}
            aria-label={`Language for question ${position}`}
            value={language}
            onChange={(e) => switchTo(e.target.value as CodeLanguage)}
          >
            {spec.languages.map((l) => (
              <option key={l} value={l}>
                {LANGUAGE_LABEL[l]}
              </option>
            ))}
          </select>
        </label>
        {language === 'java' && (
          <span className="muted" style={{ fontSize: 12 }}>
            Keep the class name Main.
          </span>
        )}
      </div>
      <CodeEditor label={`Code for question ${position}`} value={source} onChange={(v) => onChange([language, v])} onPaste={onPaste} />
      <div className="row" style={{ gap: 8 }}>
        <Button variant="secondary" disabled={running || !source.trim()} onClick={() => void run()}>
          {running ? 'Running…' : 'Run on samples'}
        </Button>
        {result && result.runsLeft >= 0 && (
          <span className="muted" style={{ fontSize: 13 }}>
            {result.runsLeft} run{result.runsLeft === 1 ? '' : 's'} left
          </span>
        )}
      </div>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {result && <RunResults cases={result.cases} compileOutput={result.compileOutput} passed={result.passed} total={result.total} title="Sample results" />}
    </div>
  )
}
