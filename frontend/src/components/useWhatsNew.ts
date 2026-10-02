import { useCallback, useEffect, useMemo, useState } from 'react'
import { useMe } from '../auth/AuthContext'
import { entriesFor, type WhatsNewEntry } from '../whatsNew'

const EVENT = 'ats:whats-new-seen'
const key = (email: string) => `ats.whatsNew.seen.${email}`

function readSeen(email: string): string[] {
  try {
    const raw = localStorage.getItem(key(email))
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string') : []
  } catch {
    return []
  }
}

/**
 * Release notes for the signed-in staff member and which ones they haven't seen. "Seen" is a
 * per-browser convenience (localStorage); if it's unavailable everything simply shows as new.
 */
export function useWhatsNew(): { entries: WhatsNewEntry[]; unseen: WhatsNewEntry[]; markAllSeen: () => void } {
  const me = useMe()
  const entries = useMemo(() => entriesFor(me.capabilities), [me.capabilities])
  const [seen, setSeen] = useState<string[]>(() => readSeen(me.email))

  useEffect(() => {
    const refresh = () => setSeen(readSeen(me.email))
    window.addEventListener(EVENT, refresh)
    window.addEventListener('storage', refresh)
    return () => {
      window.removeEventListener(EVENT, refresh)
      window.removeEventListener('storage', refresh)
    }
  }, [me.email])

  const unseen = useMemo(() => entries.filter((e) => !seen.includes(e.id)), [entries, seen])

  const markAllSeen = useCallback(() => {
    const all = Array.from(new Set([...readSeen(me.email), ...entries.map((e) => e.id)]))
    try {
      localStorage.setItem(key(me.email), JSON.stringify(all))
    } catch {
      // Private window or blocked storage: nothing to remember.
    }
    setSeen(all)
    window.dispatchEvent(new Event(EVENT))
  }, [entries, me.email])

  return { entries, unseen, markAllSeen }
}
