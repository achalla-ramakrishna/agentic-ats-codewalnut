import { useRef, useState, type ClipboardEvent, type KeyboardEvent } from 'react'

const INDENT = '    '

/**
 * A plain code editor (ADR-0016): monospace text with line numbers, Tab to indent, Enter keeps the
 * indentation. Press Esc, then Tab, to move focus out (Tab is captured for indenting otherwise).
 * Kept dependency-free on purpose.
 */
export function CodeEditor({
  value,
  onChange,
  label,
  onPaste,
  readOnly = false,
  minLines = 14,
}: {
  value: string
  onChange?: (value: string) => void
  label: string
  /** Called with the number of characters pasted (an advisory signal in tests). */
  onPaste?: (chars: number) => void
  readOnly?: boolean
  minLines?: number
}) {
  const area = useRef<HTMLTextAreaElement>(null)
  const gutter = useRef<HTMLPreElement>(null)
  const [tabExits, setTabExits] = useState(false)
  const lines = Math.max(minLines, value.split('\n').length)

  function edit(next: string, cursor: number) {
    onChange?.(next)
    requestAnimationFrame(() => {
      const el = area.current
      if (el) el.selectionStart = el.selectionEnd = cursor
    })
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (readOnly) return
    const el = e.currentTarget
    const { selectionStart: start, selectionEnd: end } = el
    if (e.key === 'Escape') {
      setTabExits(true)
      return
    }
    if (e.key === 'Tab' && !tabExits) {
      e.preventDefault()
      const lineStart = value.lastIndexOf('\n', start - 1) + 1
      if (e.shiftKey) {
        const remove = value.slice(lineStart).match(/^ {1,4}/)?.[0].length ?? 0
        if (remove) edit(value.slice(0, lineStart) + value.slice(lineStart + remove), Math.max(lineStart, start - remove))
      } else {
        edit(value.slice(0, start) + INDENT + value.slice(end), start + INDENT.length)
      }
      return
    }
    setTabExits(false)
    if (e.key === 'Enter') {
      e.preventDefault()
      const lineStart = value.lastIndexOf('\n', start - 1) + 1
      const line = value.slice(lineStart, start)
      let indent = line.match(/^\s*/)?.[0] ?? ''
      if (/[{:([]\s*$/.test(line)) indent += INDENT
      edit(value.slice(0, start) + '\n' + indent + value.slice(end), start + 1 + indent.length)
    }
  }

  function onPasteEvent(e: ClipboardEvent<HTMLTextAreaElement>) {
    onPaste?.(e.clipboardData.getData('text').length)
  }

  return (
    <div className="code-editor">
      <pre className="code-gutter" ref={gutter} aria-hidden="true">
        {Array.from({ length: lines }, (_, i) => i + 1).join('\n')}
      </pre>
      <textarea
        ref={area}
        className="code-area"
        aria-label={label}
        value={value}
        readOnly={readOnly}
        rows={lines}
        spellCheck={false}
        autoCapitalize="off"
        autoComplete="off"
        autoCorrect="off"
        wrap="off"
        onChange={(e) => onChange?.(e.target.value)}
        onKeyDown={onKeyDown}
        onPaste={onPasteEvent}
        onScroll={(e) => {
          if (gutter.current) gutter.current.scrollTop = e.currentTarget.scrollTop
        }}
      />
    </div>
  )
}
