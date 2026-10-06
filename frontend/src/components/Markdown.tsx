import { Fragment, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

/**
 * A small, safe Markdown renderer for Ask ATS answers: paragraphs, headings, bullet and numbered
 * lists, tables, **bold**, _italic_, `code` and links. App links (/jobs/…) stay in the app; other
 * links open in a new tab; anything else is plain text (no HTML is ever injected).
 */
export function Markdown({ text }: { text: string }) {
  const blocks: ReactNode[] = []
  const lines = text.replace(/\r/g, '').split('\n')
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) {
      i++
      continue
    }
    const heading = line.match(/^(#{1,4})\s+(.*)$/)
    if (heading) {
      blocks.push(<p key={i} className="md-heading">{inline(heading[2])}</p>)
      i++
      continue
    }
    if (/^\s*\|.*\|\s*$/.test(line) && i + 1 < lines.length && /^\s*\|[\s:|-]+\|\s*$/.test(lines[i + 1])) {
      const header = cells(line)
      const rows: string[][] = []
      i += 2
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) rows.push(cells(lines[i++]))
      blocks.push(
        <div key={i} className="table-wrap">
          <table className="table md-table">
            <thead>
              <tr>{header.map((h, k) => <th key={k}>{inline(h)}</th>)}</tr>
            </thead>
            <tbody>
              {rows.map((r, k) => (
                <tr key={k}>{r.map((c, m) => <td key={m}>{inline(c)}</td>)}</tr>
              ))}
            </tbody>
          </table>
        </div>,
      )
      continue
    }
    const bullet = /^\s*[-*•]\s+/
    const numbered = /^\s*\d+[.)]\s+/
    if (bullet.test(line) || numbered.test(line)) {
      const ordered = numbered.test(line)
      const pattern = ordered ? numbered : bullet
      const items: string[] = []
      while (i < lines.length && pattern.test(lines[i])) {
        let item = lines[i++].replace(pattern, '')
        // Indented continuation lines belong to the item.
        while (i < lines.length && /^\s{2,}\S/.test(lines[i]) && !bullet.test(lines[i]) && !numbered.test(lines[i])) {
          item += ' ' + lines[i++].trim()
        }
        items.push(item)
      }
      const List = ordered ? 'ol' : 'ul'
      blocks.push(
        <List key={i} className="md-list">
          {items.map((it, k) => <li key={k}>{inline(it)}</li>)}
        </List>,
      )
      continue
    }
    const para: string[] = []
    while (i < lines.length && lines[i].trim() && !/^(#{1,4})\s/.test(lines[i]) && !bullet.test(lines[i]) && !numbered.test(lines[i]) && !/^\s*\|/.test(lines[i])) {
      para.push(lines[i++])
    }
    blocks.push(
      <p key={i}>
        {para.map((p, k) => (
          <Fragment key={k}>
            {k > 0 && <br />}
            {inline(p)}
          </Fragment>
        ))}
      </p>,
    )
  }
  return <div className="markdown">{blocks}</div>
}

function cells(row: string) {
  return row.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim())
}

const TOKEN = /(\[[^\]]+\]\([^)\s]+\)|\*\*[^*]+\*\*|`[^`]+`|(?<![\w])_[^_]+_(?![\w]))/g

function inline(text: string): ReactNode[] {
  return text.split(TOKEN).filter((part) => part !== '').map((part, k) => {
    const link = part.match(/^\[([^\]]+)\]\(([^)\s]+)\)$/)
    if (link) {
      const [, label, href] = link
      if (href.startsWith('/') && !href.startsWith('//')) return <Link key={k} to={href}>{label}</Link>
      if (/^https?:\/\//i.test(href)) {
        return (
          <a key={k} href={href} target="_blank" rel="noreferrer">
            {label}
          </a>
        )
      }
      return label
    }
    if (/^\*\*[^*]+\*\*$/.test(part)) return <strong key={k}>{part.slice(2, -2)}</strong>
    if (/^`[^`]+`$/.test(part)) return <code key={k}>{part.slice(1, -1)}</code>
    if (/^_[^_]+_$/.test(part)) return <em key={k}>{part.slice(1, -1)}</em>
    return part
  })
}
