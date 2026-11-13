import { useEffect, useState } from 'react'
import { previewSet, type SetQuestion } from '../api/aiInterviews'
import { clock } from './VideoAnswer'

const KIND: Record<SetQuestion['kind'], string> = {
  VIDEO: 'Spoken answer',
  WRITTEN: 'Typed answer',
  FILL_BLANK: 'Fill in the blank',
  MULTIPLE_CHOICE: 'Multiple choice',
}

/** Read a hand-written AI interview set before creating it: questions by phase, with answers and what to look for. */
export function QuestionSetPreview({ set }: { set: number }) {
  const [questions, setQuestions] = useState<SetQuestion[] | null>(null)
  const [letter, setLetter] = useState('')
  const [answers, setAnswers] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setQuestions(null)
    previewSet(set)
      .then((r) => {
        setQuestions(r.questions)
        setLetter(r.letter)
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the questions'))
  }, [set])

  if (error) return <div role="alert" className="alert alert-error">{error}</div>
  if (!questions) return <p className="muted">Loading the questions…</p>
  const phases = [...new Set(questions.map((q) => q.phase))]
  const spoken = questions.filter((q) => q.kind === 'VIDEO').length
  return (
    <div className="stack" style={{ gap: 8 }} role="region" aria-label={`Set ${letter} questions`}>
      <div className="row" style={{ gap: 12, justifyContent: 'space-between' }}>
        <span>
          <strong>Set {letter}</strong>{' '}
          <span className="muted">
            · {questions.length} questions · {spoken} spoken · {questions.filter((q) => q.kind === 'WRITTEN' && q.phase === 'TECHNICAL').length} coding ·
            time limit 120 min
          </span>
        </span>
        <label className="row" style={{ gap: 6, fontSize: 14 }}>
          <input type="checkbox" checked={answers} onChange={(e) => setAnswers(e.target.checked)} /> Show answers and what to look for
        </label>
      </div>
      {phases.map((phase, pi) => {
        const list = questions.filter((q) => q.phase === phase)
        return (
          <details key={phase} open={pi < 2} className="set-phase">
            <summary>
              <strong>
                {pi + 1}. {list[0].phaseLabel}
              </strong>{' '}
              <span className="muted">({list.length})</span>
            </summary>
            <ol className="set-questions">
              {list.map((q, i) => (
                <li key={i}>
                  <div>{q.prompt}</div>
                  <div className="muted" style={{ fontSize: 12 }}>
                    {KIND[q.kind]}
                    {q.maxSeconds ? ` · up to ${clock(q.maxSeconds)}` : ''}
                  </div>
                  {q.code && <pre className="test-code">{q.code}</pre>}
                  {q.kind === 'MULTIPLE_CHOICE' && (
                    <ol type="A" style={{ margin: '4px 0 0', paddingLeft: 22, fontSize: 14 }}>
                      {q.options.map((o, oi) => (
                        <li key={oi} style={{ fontWeight: answers && oi === q.correctOption ? 600 : 400 }}>
                          {o} {answers && oi === q.correctOption && '✓'}
                        </li>
                      ))}
                    </ol>
                  )}
                  {answers && q.kind === 'FILL_BLANK' && (
                    <div style={{ fontSize: 14 }}>
                      <span className="muted">Answer:</span> {q.acceptedAnswers.join(' / ')}
                    </div>
                  )}
                  {answers && q.lookFor && (
                    <div className="look-for">
                      <span className="muted">Look for:</span> {q.lookFor}
                    </div>
                  )}
                </li>
              ))}
            </ol>
          </details>
        )
      })}
    </div>
  )
}
