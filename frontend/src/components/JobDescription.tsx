import type { ReactNode } from 'react'

const BULLET = /^\s*[-*•]\s+/

/**
 * Renders a plain-text job description: blank lines separate paragraphs, and runs of lines
 * starting with "-", "*" or "•" become bullet lists (so a heading line followed by bullets works).
 * Text only — no HTML is interpreted.
 */
export function JobDescription({ text }: { text: string }) {
  const out: ReactNode[] = []
  const blocks = text.replace(/\r\n/g, '\n').split(/\n\s*\n/).map((b) => b.trim()).filter(Boolean)
  blocks.forEach((block, b) => {
    let para: string[] = []
    let items: string[] = []
    const flushPara = () => {
      if (para.length) {
        const lines = para
        out.push(
          <p key={`${b}-p${out.length}`}>
            {lines.map((l, j) => (
              <span key={j}>
                {j > 0 && <br />}
                {l}
              </span>
            ))}
          </p>,
        )
        para = []
      }
    }
    const flushList = () => {
      if (items.length) {
        out.push(
          <ul key={`${b}-u${out.length}`}>
            {items.map((l, j) => (
              <li key={j}>{l}</li>
            ))}
          </ul>,
        )
        items = []
      }
    }
    for (const line of block.split('\n')) {
      if (BULLET.test(line)) {
        flushPara()
        items.push(line.replace(BULLET, ''))
      } else {
        flushList()
        para.push(line)
      }
    }
    flushPara()
    flushList()
  })
  return <div className="job-description">{out}</div>
}
