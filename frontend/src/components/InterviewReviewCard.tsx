import type { InterviewReview } from '../api/assessments'
import { Badge } from './ui'

/** 1–5 as filled dots, e.g. ●●●○○; "–" when there was nothing to judge on. */
export function Dots({ rating }: { rating: number }) {
  if (!rating) return <span className="muted rating-dots" title="Not enough to judge">–</span>
  return (
    <span className="rating-dots" title={`${rating} of 5`} aria-label={`${rating} of 5`}>
      {'●'.repeat(rating)}
      <span className="muted">{'○'.repeat(5 - rating)}</span>
    </span>
  )
}

/** The agent's review of an AI interview (ADR-0030): score, areas with evidence, and things to check. Advisory. */
export function InterviewReviewCard({ review }: { review: InterviewReview }) {
  if (review.status === 'PENDING') {
    return <p className="alert alert-info" style={{ margin: '6px 0 0' }}>The AI is reviewing this interview — usually a minute or two.</p>
  }
  if (review.status === 'FAILED' || !review.areas.length) {
    return <p className="alert alert-error" style={{ margin: '6px 0 0' }}>The review couldn’t be done. Click Review again, or watch the answers below.</p>
  }
  return (
    <div className="card stack" style={{ gap: 8, marginTop: 6, fontSize: 14 }} role="region" aria-label="Interview review">
      <div className="row" style={{ gap: 8, justifyContent: 'space-between' }}>
        <strong>
          {review.aiScore != null ? `Score ${review.aiScore}/100` : 'No score'}
          {review.markedPercent != null && <span className="muted"> · marked answers {review.markedPercent}%</span>}
        </strong>
        <Badge tone="neutral">{review.usesAi ? 'AI review — advisory' : 'Basic review (rules, not AI)'}</Badge>
      </div>
      {review.summary && <p style={{ margin: 0 }}>{review.summary}</p>}
      <table className="bank-grid" aria-label="Ratings by area" style={{ fontSize: 13 }}>
        <tbody>
          {review.areas.map((a) => (
            <tr key={a.area}>
              <td style={{ textAlign: 'left', whiteSpace: 'nowrap' }}>{a.label}</td>
              <td>
                <Dots rating={a.rating} />
              </td>
              <td style={{ textAlign: 'left' }}>
                {a.reason}
                {a.evidence && <div className="muted">“{a.evidence}”</div>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {(review.noticePeriod || review.expectedSalary) && (
        <div>
          {review.noticePeriod && (
            <span>
              <span className="muted">Notice:</span> {review.noticePeriod}{' '}
            </span>
          )}
          {review.expectedSalary && (
            <span>
              <span className="muted">· Salary:</span> {review.expectedSalary}
            </span>
          )}
        </div>
      )}
      {review.strengths.length > 0 && (
        <div>
          <span className="muted">Strengths</span>
          <ul style={{ margin: '2px 0 0', paddingLeft: 20 }}>
            {review.strengths.map((s) => (
              <li key={s}>{s}</li>
            ))}
          </ul>
        </div>
      )}
      {review.toCheck.length > 0 && (
        <div>
          <span className="muted">To check in a live interview</span>
          <ul style={{ margin: '2px 0 0', paddingLeft: 20 }}>
            {review.toCheck.map((s) => (
              <li key={s}>{s}</li>
            ))}
          </ul>
        </div>
      )}
      <span className="muted" style={{ fontSize: 12 }}>
        The score is worked out by the app from these ratings (technical 30%, design 20%, communication 20%, experience 15%, readiness 15%; marked
        answers count for a fifth). Spoken answers were read from automatic transcripts — watch the videos before deciding.
      </span>
    </div>
  )
}
