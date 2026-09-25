import type { ReactNode } from 'react'

export function Badge({ tone = 'neutral', children }: { tone?: 'neutral' | 'primary' | 'danger'; children: ReactNode }) {
  const toneClass = tone === 'neutral' ? '' : ` badge-${tone}`
  return <span className={`badge${toneClass}`}>{children}</span>
}
