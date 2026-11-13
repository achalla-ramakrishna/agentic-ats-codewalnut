import { useEffect, type ReactNode } from 'react'
import { Button } from './ui'

/** A panel over the page (Esc or Close to dismiss), so a task opens where you are instead of further down. */
export function Dialog({ title, onClose, children, wide = false }: { title: string; onClose: () => void; children: ReactNode; wide?: boolean }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="dialog-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`dialog card stack${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <strong style={{ fontSize: 18 }}>{title}</strong>
          <Button size="sm" variant="ghost" onClick={onClose}>
            Close
          </Button>
        </div>
        {children}
      </div>
    </div>
  )
}
