import { LANGUAGE_LABEL, type CodingView, type QuestionKind } from '../api/assessments'
import { Figure } from './Figure'

interface Previewable {
  kind: QuestionKind
  prompt: string
  code: string | null
  figure: string | null
  options: string[]
  optionFigures: string[] | null
  correct: number[]
  acceptedAnswers: string[]
  explanation: string | null
  coding?: CodingView | null
}

/** A question as a reviewer sees it: picture, options (text or pictures) and, optionally, the answer. */
export function QuestionPreview({ question: q, showAnswer }: { question: Previewable; showAnswer: boolean }) {
  return (
    <div className="stack" style={{ gap: 6 }}>
      <div style={{ whiteSpace: 'pre-wrap' }}>{q.prompt}</div>
      {q.figure && <Figure figure={q.figure} />}
      {q.code && <pre className="test-code">{q.code}</pre>}
      {q.kind === 'CODING' && q.coding ? (
        <div className="stack" style={{ gap: 6, fontSize: 14 }}>
          <span className="muted">
            Write code · {q.coding.spec.languages.map((l) => LANGUAGE_LABEL[l]).join(', ')} · {q.coding.spec.samples.length} sample
            {q.coding.spec.samples.length === 1 ? '' : 's'}, {q.coding.tests.length} hidden test{q.coding.tests.length === 1 ? '' : 's'} ·{' '}
            {q.coding.spec.timeLimitSeconds} s per test
          </span>
          {q.coding.spec.inputFormat && (
            <span>
              <span className="muted">Input:</span> {q.coding.spec.inputFormat}
            </span>
          )}
          {q.coding.spec.outputFormat && (
            <span>
              <span className="muted">Output:</span> {q.coding.spec.outputFormat}
            </span>
          )}
          <div className="run-case-grid">
            <div>
              <span className="muted">Sample input</span>
              <pre className="test-code">{q.coding.spec.samples[0]?.input || '(empty)'}</pre>
            </div>
            <div>
              <span className="muted">Expected output</span>
              <pre className="test-code">{q.coding.spec.samples[0]?.output}</pre>
            </div>
          </div>
        </div>
      ) : q.kind === 'SHORT_ANSWER' ? (
        showAnswer && (
          <div style={{ fontSize: 14 }}>
            <span className="muted">Accepted:</span> {q.acceptedAnswers.join(' / ')}
          </div>
        )
      ) : q.optionFigures ? (
        <div className="option-figures">
          {q.optionFigures.map((f, i) => (
            <div key={i} className={`option-figure${showAnswer && q.correct.includes(i) ? ' right' : ''}`}>
              <Figure figure={f} small alt={q.options[i]} />
              <span style={{ fontSize: 13, fontWeight: showAnswer && q.correct.includes(i) ? 600 : 400 }}>
                {String.fromCharCode(65 + i)} {showAnswer && q.correct.includes(i) && '✓'}
              </span>
            </div>
          ))}
        </div>
      ) : (
        <ol type="A" style={{ margin: 0, paddingLeft: 22, fontSize: 14 }}>
          {q.options.map((o, i) => (
            <li key={i} style={{ fontWeight: showAnswer && q.correct.includes(i) ? 600 : 400 }}>
              {o} {showAnswer && q.correct.includes(i) && '✓'}
            </li>
          ))}
        </ol>
      )}
      {showAnswer && q.explanation && (
        <div className="muted" style={{ fontSize: 13 }}>
          {q.explanation}
        </div>
      )}
    </div>
  )
}
