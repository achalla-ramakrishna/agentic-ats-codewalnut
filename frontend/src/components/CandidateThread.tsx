import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { listMyMessages, postMyMessage, type CandidateMessage } from '../api/messages'
import { Button } from './ui'
import './chat.css'

const REFRESH_MS = 30_000

/** The candidate's conversation with CodeWalnut about one application. */
export function CandidateThread({ applicationId, jobTitle, onRead }: { applicationId: string; jobTitle: string; onRead?: () => void }) {
  const [messages, setMessages] = useState<CandidateMessage[] | null>(null)
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const listRef = useRef<HTMLUListElement>(null)

  const load = useCallback(() => {
    listMyMessages(applicationId)
      .then((m) => {
        setMessages(m)
        onRead?.()
      })
      .catch(() => setMessages((m) => m ?? []))
  }, [applicationId, onRead])

  useEffect(() => {
    load()
    const timer = window.setInterval(load, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [load])

  useEffect(() => {
    const list = listRef.current
    if (list) list.scrollTop = list.scrollHeight
  }, [messages])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!text.trim()) return
    setBusy(true)
    setError(null)
    try {
      await postMyMessage(applicationId, text)
      setText('')
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not send')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="chat">
      <h3 style={{ margin: 0 }}>Messages about {jobTitle}</h3>
      {messages && messages.length === 0 && (
        <p className="muted" style={{ margin: 0 }}>
          No messages yet. Have a question about your application? Ask here and our team will reply.
        </p>
      )}
      {messages && messages.length > 0 && (
        <ul className="chat-list" ref={listRef} aria-label="Conversation with CodeWalnut">
          {messages.map((m, i) => (
            <li key={`${m.createdAt}-${i}`} className={`chat-msg${m.fromMe ? ' mine' : ''}`}>
              {m.subject && <div className="chat-subject">{m.subject}</div>}
              <div className="chat-body">{m.body}</div>
              <div className="chat-meta">
                {m.authorName} · {new Date(m.createdAt).toLocaleString()}
              </div>
            </li>
          ))}
        </ul>
      )}
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      <form className="stack" onSubmit={onSubmit} aria-label="Reply to CodeWalnut" style={{ gap: 8 }}>
        <label className="field">
          Your message
          <textarea
            className="textarea"
            style={{ minHeight: 80, fontFamily: 'inherit', fontSize: 14 }}
            value={text}
            maxLength={5000}
            onChange={(e) => setText(e.target.value)}
          />
        </label>
        <div>
          <Button type="submit" size="sm" disabled={busy || !text.trim()}>
            {busy ? 'Sending…' : 'Send'}
          </Button>
        </div>
      </form>
    </div>
  )
}
