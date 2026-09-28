import type { Stage } from '../api/tracker'
import { useStages } from './useStages'

export function StageSelect({
  value,
  onChange,
  disabled,
  label,
}: {
  value: Stage
  onChange: (stage: Stage) => void
  disabled?: boolean
  label: string
}) {
  const stages = useStages()
  return (
    <select
      className="select"
      aria-label={label}
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value as Stage)}
    >
      {stages.length === 0 && <option value={value}>{value}</option>}
      <optgroup label="Pipeline">
        {stages.filter((s) => !s.exit).map((s) => (
          <option key={s.key} value={s.key}>
            {s.label}
          </option>
        ))}
      </optgroup>
      <optgroup label="Closed / paused">
        {stages.filter((s) => s.exit).map((s) => (
          <option key={s.key} value={s.key}>
            {s.label}
          </option>
        ))}
      </optgroup>
    </select>
  )
}
