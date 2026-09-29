import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * Reopens a candidate's drawer from ?candidate=<applicationId>, e.g. after returning from
 * connecting Google Calendar, then drops the parameter.
 */
export function useOpenFromQuery<T extends { id: string }>(rows: T[] | null, open: (row: T) => void) {
  const [params, setParams] = useSearchParams()
  const wanted = params.get('candidate')
  useEffect(() => {
    if (!wanted || !rows) return
    const row = rows.find((r) => r.id === wanted)
    if (row) open(row)
    const next = new URLSearchParams(params)
    next.delete('candidate')
    setParams(next, { replace: true })
  }, [wanted, rows, open, params, setParams])
}
