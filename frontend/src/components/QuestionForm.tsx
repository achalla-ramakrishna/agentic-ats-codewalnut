import { useState, type FormEvent } from 'react'
import {
  KIND_LABEL,
  LANGUAGE_LABEL,
  type CodeLanguage,
  type QuestionInput,
  type QuestionKind,
  type QuestionView,
  type TestCase,
} from '../api/assessments'
import { Figure, readPicture } from './Figure'
import { Button } from './ui'

const ALL_LANGUAGES = Object.keys(LANGUAGE_LABEL) as CodeLanguage[]

/** Input and expected output pairs for a coding question (ADR-0016). */
function TestCases({ label, cases, onChange }: { label: string; cases: TestCase[]; onChange: (cases: TestCase[]) => void }) {
  return (
    <fieldset className="stack" style={{ gap: 6, border: 0, padding: 0, margin: 0 }}>
      <legend style={{ fontSize: 14 }}>{label}</legend>
      {cases.map((c, i) => (
        <div key={i} className="run-case-grid">
          <label className="field">
            Input {i + 1}
            <textarea
              className="input test-code-input"
              rows={3}
              value={c.input}
              onChange={(e) => onChange(cases.map((x, j) => (j === i ? { ...x, input: e.target.value } : x)))}
            />
          </label>
          <label className="field">
            Expected output {i + 1}
            <textarea
              className="input test-code-input"
              rows={3}
              value={c.output}
              onChange={(e) => onChange(cases.map((x, j) => (j === i ? { ...x, output: e.target.value } : x)))}
            />
          </label>
          {cases.length > 1 && (
            <div>
              <Button size="sm" variant="ghost" onClick={() => onChange(cases.filter((_, j) => j !== i))}>
                Remove
              </Button>
            </div>
          )}
        </div>
      ))}
      <div>
        <Button size="sm" variant="ghost" onClick={() => onChange([...cases, { input: '', output: '' }])}>
          + Test case
        </Button>
      </div>
    </fieldset>
  )
}

/** Add or edit a test or bank question: text or code, an optional picture, options, accepted answers or test cases. */
export function QuestionForm({
  initial,
  onSave,
  onCancel,
}: {
  initial?: QuestionView
  onSave: (q: QuestionInput) => Promise<void>
  onCancel: () => void
}) {
  const [kind, setKind] = useState<QuestionKind>(initial?.kind ?? 'SINGLE_CHOICE')
  const [prompt, setPrompt] = useState(initial?.prompt ?? '')
  const [code, setCode] = useState(initial?.code ?? '')
  const [options, setOptions] = useState<string[]>(initial?.options.length ? initial.options : ['', '', '', ''])
  const [correct, setCorrect] = useState<number[]>(initial?.correct ?? [])
  const [accepted, setAccepted] = useState((initial?.acceptedAnswers ?? []).join('\n'))
  const [points, setPoints] = useState(initial?.points ?? 1)
  const [explanation, setExplanation] = useState(initial?.explanation ?? '')
  const [figure, setFigure] = useState<string | null>(initial?.figure ?? null)
  const [error, setError] = useState<string | null>(null)
  const spec = initial?.coding?.spec
  const [languages, setLanguages] = useState<CodeLanguage[]>(spec?.languages ?? ALL_LANGUAGES)
  const [inputFormat, setInputFormat] = useState(spec?.inputFormat ?? '')
  const [outputFormat, setOutputFormat] = useState(spec?.outputFormat ?? '')
  const [constraints, setConstraints] = useState(spec?.constraints ?? '')
  const [samples, setSamples] = useState<TestCase[]>(spec?.samples.length ? spec.samples : [{ input: '', output: '' }])
  const [tests, setTests] = useState<TestCase[]>(initial?.coding?.tests.length ? initial.coding.tests : [{ input: '', output: '' }])
  const [timeLimit, setTimeLimit] = useState(spec?.timeLimitSeconds ?? 2)
  const [starter, setStarter] = useState<Partial<Record<CodeLanguage, string>>>(spec?.starter ?? {})
  const coding = kind === 'CODING'

  function toggle(i: number) {
    if (kind === 'SINGLE_CHOICE') setCorrect([i])
    else setCorrect((c) => (c.includes(i) ? c.filter((x) => x !== i) : [...c, i]))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const filled = options.map((o) => o.trim())
    try {
      if (coding) {
        const keep = (c: TestCase[]) => c.filter((t) => t.output.trim() || t.input.trim())
        await onSave({
          kind,
          prompt,
          points,
          explanation: explanation.trim() || undefined,
          figure,
          coding: {
            languages,
            starter: Object.fromEntries(languages.filter((l) => starter[l]?.trim()).map((l) => [l, starter[l]!])),
            samples: keep(samples),
            tests: keep(tests),
            timeLimitSeconds: timeLimit,
            inputFormat: inputFormat.trim() || undefined,
            outputFormat: outputFormat.trim() || undefined,
            constraints: constraints.trim() || undefined,
          },
        })
        return
      }
      await onSave({
        kind,
        prompt,
        code: code.trim() || undefined,
        options: kind === 'SHORT_ANSWER' ? [] : filled.filter((o) => o),
        // keep indexes pointing at the same options after dropping blanks
        correct: kind === 'SHORT_ANSWER' ? [] : correct.filter((i) => filled[i]).map((i) => filled.slice(0, i).filter((o) => o).length),
        acceptedAnswers: kind === 'SHORT_ANSWER' ? accepted.split('\n').map((a) => a.trim()).filter(Boolean) : [],
        points,
        explanation: explanation.trim() || undefined,
        figure,
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save the question')
    }
  }

  return (
    <form className="stack card" onSubmit={submit} aria-label={initial ? 'Edit question' : 'New question'} style={{ gap: 8 }}>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field">
          Type
          <select className="select" value={kind} onChange={(e) => setKind(e.target.value as QuestionKind)}>
            {(Object.keys(KIND_LABEL) as QuestionKind[]).map((k) => (
              <option key={k} value={k}>
                {KIND_LABEL[k]}
              </option>
            ))}
          </select>
        </label>
        <label className="field" style={{ maxWidth: 100 }}>
          Points
          <input className="input" type="number" min={1} max={coding ? 20 : 10} value={points} onChange={(e) => setPoints(Number(e.target.value))} />
        </label>
      </div>
      <label className="field">
        Question
        <textarea className="input" rows={2} required value={prompt} onChange={(e) => setPrompt(e.target.value)} />
      </label>
      <div className="stack" style={{ gap: 6 }}>
        <span style={{ fontSize: 14 }}>Picture (optional)</span>
        {figure && (
          <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
            <Figure figure={figure} small />
            <Button size="sm" variant="ghost" onClick={() => setFigure(null)}>
              Remove picture
            </Button>
          </div>
        )}
        <input
          type="file"
          accept="image/png,image/jpeg"
          aria-label="Question picture"
          onChange={async (e) => {
            const file = e.target.files?.[0]
            if (!file) return
            try {
              setFigure(await readPicture(file))
              setError(null)
            } catch (err) {
              setError(err instanceof Error ? err.message : 'The picture could not be used')
            }
          }}
        />
        {initial?.optionFigures && <span className="muted" style={{ fontSize: 13 }}>The options are pictures; they stay as long as the number of options stays the same.</span>}
      </div>
      {!coding && (
        <label className="field">
          Code to read (optional)
          <textarea className="input test-code-input" rows={4} value={code} onChange={(e) => setCode(e.target.value)} />
        </label>
      )}
      {coding ? (
        <div className="stack" style={{ gap: 8 }}>
          <p className="muted" style={{ margin: 0, fontSize: 13 }}>
            The candidate’s program reads the input from standard input and prints the answer. Samples are shown to the candidate; hidden tests
            aren’t. Points are given in proportion to the tests passed (samples count too).
          </p>
          <fieldset className="row" style={{ gap: 12, border: 0, padding: 0, margin: 0 }}>
            <legend style={{ fontSize: 14 }}>Languages allowed</legend>
            {ALL_LANGUAGES.map((l) => (
              <label key={l} className="row" style={{ gap: 4 }}>
                <input
                  type="checkbox"
                  checked={languages.includes(l)}
                  onChange={(e) => setLanguages((all) => (e.target.checked ? [...all, l] : all.filter((x) => x !== l)))}
                />
                {LANGUAGE_LABEL[l]}
              </label>
            ))}
          </fieldset>
          <label className="field">
            Input format
            <textarea className="input" rows={2} value={inputFormat} onChange={(e) => setInputFormat(e.target.value)} />
          </label>
          <label className="field">
            Output format
            <textarea className="input" rows={2} value={outputFormat} onChange={(e) => setOutputFormat(e.target.value)} />
          </label>
          <label className="field">
            Limits (optional, e.g. 1 ≤ N ≤ 10^5)
            <input className="input" value={constraints} onChange={(e) => setConstraints(e.target.value)} />
          </label>
          <label className="field" style={{ maxWidth: 220 }}>
            Time limit per test (seconds)
            <input className="input" type="number" min={0.5} max={10} step={0.5} value={timeLimit} onChange={(e) => setTimeLimit(Number(e.target.value))} />
          </label>
          <TestCases label="Sample tests (shown to the candidate)" cases={samples} onChange={setSamples} />
          <TestCases label="Hidden tests (used for grading; include edge cases and a large input)" cases={tests} onChange={setTests} />
          <details>
            <summary style={{ fontSize: 14 }}>Starter code (optional — a default that reads the input is used otherwise)</summary>
            <div className="stack" style={{ gap: 6, marginTop: 6 }}>
              {languages.map((l) => (
                <label key={l} className="field">
                  {LANGUAGE_LABEL[l]}
                  <textarea
                    className="input test-code-input"
                    rows={6}
                    value={starter[l] ?? ''}
                    onChange={(e) => setStarter((all) => ({ ...all, [l]: e.target.value }))}
                  />
                </label>
              ))}
            </div>
          </details>
        </div>
      ) : kind === 'SHORT_ANSWER' ? (
        <label className="field">
          Accepted answers (one per line; case and extra spaces don’t matter)
          <textarea className="input" rows={2} value={accepted} onChange={(e) => setAccepted(e.target.value)} />
        </label>
      ) : (
        <fieldset className="stack" style={{ gap: 6, border: 0, padding: 0, margin: 0 }}>
          <legend style={{ fontSize: 14 }}>Options — tick the right {kind === 'SINGLE_CHOICE' ? 'one' : 'ones'}</legend>
          {options.map((o, i) => (
            <div key={i} className="row" style={{ gap: 8, flexWrap: 'nowrap' }}>
              <input
                type={kind === 'SINGLE_CHOICE' ? 'radio' : 'checkbox'}
                name="correct"
                aria-label={`Option ${i + 1} is right`}
                checked={correct.includes(i)}
                onChange={() => toggle(i)}
              />
              <input
                className="input"
                aria-label={`Option ${i + 1}`}
                value={o}
                onChange={(e) => setOptions((all) => all.map((x, j) => (j === i ? e.target.value : x)))}
              />
            </div>
          ))}
          {options.length < 8 && (
            <div>
              <Button size="sm" variant="ghost" onClick={() => setOptions((all) => [...all, ''])}>
                + Option
              </Button>
            </div>
          )}
        </fieldset>
      )}
      <label className="field">
        Explanation for reviewers (optional)
        <input className="input" value={explanation} onChange={(e) => setExplanation(e.target.value)} />
      </label>
      <div className="row">
        <Button type="submit">Save question</Button>
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
