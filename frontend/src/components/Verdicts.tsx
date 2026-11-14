import { DECISION_ICON, DECISION_LABEL, type Decision, type VerdictBrief } from '../api/aiReviews'
import { Badge } from './ui'

export const verdictTone = (d: Decision) => (d === 'SELECT' ? 'primary' : d === 'REJECT' ? 'danger' : 'neutral')

/** Reviewers' verdicts on an AI interview as small badges: ✅ Dhaval, 🤔 Priya (AINT-21). */
export function Verdicts({ verdicts }: { verdicts: VerdictBrief[] }) {
  if (!verdicts.length) return <span className="muted">–</span>
  return (
    <span className="row" style={{ gap: 4, flexWrap: 'wrap' }}>
      {verdicts.map((v) => (
        <span key={v.reviewerName} title={`${v.reviewerName}: ${DECISION_LABEL[v.decision]}`}>
          <Badge tone={verdictTone(v.decision)}>
            {DECISION_ICON[v.decision]} {v.reviewerName.split(' ')[0]}
          </Badge>
        </span>
      ))}
    </span>
  )
}

