import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { DrawerTab } from './CandidateDrawer'

const TABS: DrawerTab[] = ['profile', 'candidate', 'client', 'team']

/**
 * Reopens a candidate's drawer from ?candidate=<applicationId>[&tab=candidate|client|team], e.g. after
 * connecting Google or from the Messages inbox, then drops the parameters.
 */
export function useOpenFromQuery<T extends { id: string }>(rows: T[] | null, open: (row: T, tab: DrawerTab) => void) {
  const [params, setParams] = useSearchParams()
  const wanted = params.get('candidate')
  const tab = params.get('tab')
  useEffect(() => {
    if (!wanted || !rows) return
    const row = rows.find((r) => r.id === wanted)
    if (row) open(row, TABS.includes(tab as DrawerTab) ? (tab as DrawerTab) : 'profile')
    const next = new URLSearchParams(params)
    next.delete('candidate')
    next.delete('tab')
    setParams(next, { replace: true })
  }, [wanted, tab, rows, open, params, setParams])
}
