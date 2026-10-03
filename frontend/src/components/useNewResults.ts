import { useCallback, useEffect, useState } from 'react'
import { listNewResults, type NewResult } from '../api/assessments'

const EVENT = 'ats:new-results-changed'

/** Tell every useNewResults() to refresh, e.g. after opening a result's answers or marking it seen. */
export function newResultsChanged() {
  window.dispatchEvent(new Event(EVENT))
}

/**
 * Submitted tests nobody has looked at yet (ASMT-32), for the Tests badge and the New results list.
 * Refreshes every minute and whenever newResultsChanged() is called. Off when enabled is false.
 */
export function useNewResults(enabled: boolean) {
  const [results, setResults] = useState<NewResult[]>([])
  const refresh = useCallback(() => {
    if (!enabled) return
    listNewResults()
      .then(setResults)
      .catch(() => undefined)
  }, [enabled])
  useEffect(() => {
    if (!enabled) return
    refresh()
    const timer = window.setInterval(refresh, 60_000)
    window.addEventListener(EVENT, refresh)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener(EVENT, refresh)
    }
  }, [enabled, refresh])
  return { results, refresh }
}
