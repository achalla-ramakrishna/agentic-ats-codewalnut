import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  askAts,
  deleteConversation,
  getAskStatus,
  getConversation,
  listConversations,
  type AskStatus,
  type ChatMessage,
  type ConversationSummary,
} from '../api/ask'
import { getWhatsAppStatus } from '../api/messages'
import { ActionCards } from '../components/ActionCards'
import { Markdown } from '../components/Markdown'
import { Button } from '../components/ui'
import './AskPage.css'

function when(iso: string) {
  const d = new Date(iso)
  const today = new Date()
  return d.toDateString() === today.toDateString()
    ? d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
    : d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
}

/**
 * Ask ATS (docs/features/ai-assistance.md, ASK-01…ASK-05): a chat about openings, candidates,
 * interviews, feedback and tests, with each person's past chats on the left. It only reads.
 */
export function AskPage() {
  const [params, setParams] = useSearchParams()
  const chatId = params.get('c')
  const [status, setStatus] = useState<AskStatus | null>(null)
  const [chats, setChats] = useState<ConversationSummary[]>([])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const end = useRef<HTMLDivElement>(null)
  const box = useRef<HTMLTextAreaElement>(null)
  /** The chat just created by asking: its messages are already here. */
  const justAsked = useRef<string | null>(null)

  const [waApi, setWaApi] = useState(false)
  useEffect(() => {
    getWhatsAppStatus()
      .then((s) => setWaApi(s.apiEnabled))
      .catch(() => setWaApi(false))
  }, [])

  useEffect(() => {
    getAskStatus()
      .then(setStatus)
      .catch(() => setStatus({ available: false, suggestions: [] }))
    listConversations()
      .then(setChats)
      .catch(() => setChats([]))
  }, [])

  useEffect(() => {
    setError(null)
    if (!chatId) {
      setMessages([])
      return
    }
    if (justAsked.current === chatId) return
    getConversation(chatId)
      .then((c) => setMessages(c.messages))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not open that chat'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chatId])

  useEffect(() => {
    end.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' })
  }, [messages, busy])

  async function send(text: string) {
    const q = text.trim()
    if (!q || busy) return
    setError(null)
    setBusy(true)
    setQuestion('')
    setMessages((m) => [...m, { role: 'user', text: q, at: new Date().toISOString() }])
    try {
      const chat = await askAts(q, chatId)
      setMessages(chat.messages)
      setChats((cs) => [{ id: chat.id, title: chat.title, updatedAt: chat.updatedAt }, ...cs.filter((c) => c.id !== chat.id)])
      if (chat.id !== chatId) {
        justAsked.current = chat.id
        setParams({ c: chat.id })
      }
    } catch (e) {
      setMessages((m) => m.slice(0, -1))
      setQuestion(q)
      setError(e instanceof Error ? e.message : 'Ask ATS could not answer. Please try again.')
    } finally {
      setBusy(false)
      box.current?.focus()
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void send(question)
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      void send(question)
    }
  }

  async function remove(id: string) {
    if (!window.confirm('Delete this chat?')) return
    try {
      await deleteConversation(id)
      setChats((cs) => cs.filter((c) => c.id !== id))
      if (id === chatId) setParams({})
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete the chat')
    }
  }

  function newChat() {
    setParams({})
    setMessages([])
    setError(null)
    box.current?.focus()
  }

  return (
    <div className="ask">
      <aside className="ask-chats" aria-label="Your chats">
        <Button variant="secondary" onClick={newChat}>
          + New chat
        </Button>
        <nav className="ask-chat-list">
          {chats.length === 0 && <p className="muted ask-empty-list">Your chats appear here.</p>}
          {chats.map((c) => (
            <div key={c.id} className={`ask-chat-item${c.id === chatId ? ' active' : ''}`}>
              <button type="button" className="ask-chat-open" onClick={() => setParams({ c: c.id })} title={c.title}>
                <span className="ask-chat-title">{c.title}</span>
                <span className="muted ask-chat-when">{when(c.updatedAt)}</span>
              </button>
              <button type="button" className="ask-chat-delete" aria-label={`Delete chat ${c.title}`} onClick={() => void remove(c.id)}>
                ×
              </button>
            </div>
          ))}
        </nav>
      </aside>

      <section className="ask-main" aria-label="Ask ATS">
        <div className="ask-thread" aria-live="polite">
          {messages.length === 0 && !busy && (
            <div className="ask-welcome">
              <h1>Ask ATS</h1>
              <p className="muted">
                Ask about openings, candidates, interviews, feedback and tests, or how to do something in the ATS. It looks up the
                live data you can see and only reads; nothing is changed.
              </p>
              {status && !status.available && (
                <div className="alert alert-info">Ask ATS isn&apos;t switched on yet: the AI key needs to be set up.</div>
              )}
              {status?.available && (
                <div className="ask-suggestions">
                  {status.suggestions.map((s) => (
                    <button key={s} type="button" className="ask-suggestion" onClick={() => void send(s)}>
                      {s}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
          {messages.map((m, i) =>
            m.role === 'user' ? (
              <div key={i} className="ask-msg ask-msg-user">
                {m.text}
              </div>
            ) : (
              <div key={i} className="ask-msg ask-msg-assistant">
                <Markdown text={m.text} />
                {m.actions && m.actions.length > 0 && chatId && (
                  <ActionCards conversationId={chatId} actions={m.actions} waApi={waApi} onChange={(c) => setMessages(c.messages)} />
                )}
              </div>
            ),
          )}
          {busy && (
            <div className="ask-msg ask-msg-assistant ask-thinking" role="status">
              Looking it up…
            </div>
          )}
          <div ref={end} />
        </div>

        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
        <form className="ask-input" onSubmit={onSubmit}>
          <textarea
            ref={box}
            className="input"
            aria-label="Your question"
            placeholder="Ask anything about the ATS… e.g. Who is shortlisted for the Java openings?"
            rows={2}
            value={question}
            disabled={status?.available === false}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={onKeyDown}
          />
          <Button type="submit" disabled={busy || !question.trim() || status?.available === false}>
            {busy ? 'Asking…' : 'Ask'}
          </Button>
        </form>
        <p className="muted ask-note">
          Answers come from the AI using live ATS data you&apos;re allowed to see. Check anything important before acting on it.
        </p>
      </section>
    </div>
  )
}
