import { useState } from 'react'

const KEY = 'ats.batchSize'
export const DEFAULT_BATCH = 30

function load(): number {
  try {
    const n = Number(window.localStorage.getItem(KEY))
    if (Number.isInteger(n) && n >= 1 && n <= 200) return n
  } catch {
    // private window or blocked storage: use the default
  }
  return DEFAULT_BATCH
}

/** How many people to email at a time (default 30); remembered in this browser. */
export function useBatchSize(): [number, (n: number) => void] {
  const [size, setSize] = useState(load)
  const update = (n: number) => {
    const clean = Math.max(1, Math.min(200, Math.round(n) || DEFAULT_BATCH))
    setSize(clean)
    try {
      window.localStorage.setItem(KEY, String(clean))
    } catch {
      // not remembered; still applies now
    }
  }
  return [size, update]
}

export function BatchSizeInput({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  const [text, setText] = useState(String(value))
  return (
    <label className="row" style={{ gap: 6, fontSize: 13 }} title="Sending in batches (about 30 at a time, a little apart) keeps emails out of spam folders">
      Batch size
      <input
        className="input"
        type="number"
        min={1}
        max={200}
        style={{ width: 72 }}
        value={text}
        onChange={(e) => {
          setText(e.target.value)
          if (e.target.value !== '') onChange(Number(e.target.value))
        }}
        onBlur={() => setText(String(value))}
      />
    </label>
  )
}
