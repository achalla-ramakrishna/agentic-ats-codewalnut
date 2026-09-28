import { useEffect, useState } from 'react'
import { getStages, type StageOption } from '../api/tracker'

let cache: StageOption[] | null = null

/** Stage list from the server (single source of truth), fetched once per page load. */
export function useStages(): StageOption[] {
  const [stages, setStages] = useState<StageOption[]>(cache ?? [])
  useEffect(() => {
    if (cache) return
    getStages()
      .then((s) => {
        cache = s
        setStages(s)
      })
      .catch(() => undefined)
  }, [])
  return stages
}
