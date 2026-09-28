import type { Job } from '../api/tracker'
import { useStages } from './useStages'

/** Horizontal bar showing how an opening's candidates spread across stages. */
export function StageBar({ job }: { job: Job }) {
  const stages = useStages()
  if (job.total === 0) return <div className="bar" aria-hidden="true" />
  const pipeline = stages.filter((s) => !s.exit)
  return (
    <div className="bar" role="img" aria-label={`${job.total} candidates across stages`}>
      {stages.map((s) => {
        const n = job.stageCounts[s.key] ?? 0
        if (!n) return null
        const i = pipeline.findIndex((p) => p.key === s.key)
        const color = s.exit ? 'var(--color-border)' : 'var(--color-primary)'
        const opacity = s.exit ? 1 : 0.35 + (0.65 * (i + 1)) / Math.max(pipeline.length, 1)
        return <span key={s.key} title={`${s.label}: ${n}`} style={{ width: `${(n / job.total) * 100}%`, background: color, opacity }} />
      })}
    </div>
  )
}
