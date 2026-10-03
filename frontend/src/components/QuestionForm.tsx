import { useState, type FormEvent } from 'react'
import { KIND_LABEL, type QuestionInput, type QuestionKind, type QuestionView } from '../api/assessments'
import { Figure, readPicture } from './Figure'
import { Button } from './ui'

/** Add or edit a test or bank question: text or code, an optional picture, options or accepted answers. */
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

  function toggle(i: number) {
    if (kind === 'SINGLE_CHOICE') setCorrect([i])
    else setCorrect((c) => (c.includes(i) ? c.filter((x) => x !== i) : [...c, i]))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const filled = options.map((o) => o.trim())
    try {
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
          <input className="input" type="number" min={1} max={10} value={points} onChange={(e) => setPoints(Number(e.target.value))} />
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
      <label className="field">
        Code to read (optional)
        <textarea className="input test-code-input" rows={4} value={code} onChange={(e) => setCode(e.target.value)} />
      </label>
      {kind === 'SHORT_ANSWER' ? (
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
