import { useEffect, useRef, useState } from 'react'
import { revealQuestion, uploadAnswer, type RevealedQuestion } from '../api/aiInterviews'
import { Button } from './ui'

/** The browser's speech-to-text (Chrome, Edge); not in TypeScript's DOM types. */
interface Recognition {
  lang: string
  continuous: boolean
  interimResults: boolean
  onresult: ((e: { resultIndex: number; results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }> }) => void) | null
  onend: (() => void) | null
  onerror: (() => void) | null
  start: () => void
  stop: () => void
}
type RecognitionCtor = new () => Recognition

export function speechRecognition(): RecognitionCtor | null {
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}

/** The first recording format this browser supports (WebM in Chrome/Edge/Firefox, MP4 in Safari). */
function recordingType(): string | undefined {
  if (typeof MediaRecorder === 'undefined') return undefined
  return ['video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4'].find((t) => MediaRecorder.isTypeSupported(t))
}

export function clock(seconds: number) {
  const s = Math.max(0, Math.round(seconds))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/**
 * A recorded answer with a working seek bar. Chrome's recordings carry no length, so the player
 * reads it by jumping to the end once.
 */
export function RecordedVideo({ src, label }: { src: string; label: string }) {
  return (
    <video
      className="video-answer"
      controls
      playsInline
      preload="metadata"
      src={src}
      aria-label={label}
      onLoadedMetadata={(e) => {
        const v = e.currentTarget
        if (v.duration === Infinity) {
          v.addEventListener('durationchange', () => (v.currentTime = 0), { once: true })
          v.currentTime = 1e9
        }
      }}
    />
  )
}

/** The camera preview (or the recorded answer) for a video question. */
function Preview({ stream, src }: { stream: MediaStream | null; src: string | null }) {
  const ref = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    const v = ref.current
    if (!v) return
    if (src) {
      v.srcObject = null
      v.src = src
    } else {
      v.removeAttribute('src')
      v.srcObject = stream
    }
  }, [stream, src])
  return src ? (
    <RecordedVideo src={src} label="Your recorded answer" />
  ) : (
    <video ref={ref} className="video-answer mirrored" autoPlay muted playsInline aria-label="Camera preview" />
  )
}

/**
 * One spoken answer (AI interviews, ADR-0030): record on camera up to the limit, the browser writes
 * down what was said, and the recording uploads when it stops. Recording again replaces it.
 * value: [video id, transcript] as saved.
 *
 * Reading checks (AINT-24, ADR-0031): the question stays hidden until the candidate opens it; they get
 * thinkSeconds to read and think, then recording starts on its own; takesLeft limits recording again;
 * leaving the window while recording is counted for the team.
 */
export function VideoAnswer({
  testId,
  questionId,
  position,
  maxSeconds,
  stream,
  value,
  busy,
  hidden = false,
  revealedAt = null,
  thinkSeconds = 0,
  takesLeft = null,
  onRevealed,
  onRecording,
  onSaved,
}: {
  testId: string
  questionId: string
  position: number
  maxSeconds: number
  stream: MediaStream | null
  value: string[]
  /** Another answer is being recorded or uploaded. */
  busy: boolean
  /** The question's text hasn't been shown yet. */
  hidden?: boolean
  /** When the question was first shown (the thinking time counts from then, even after a reload). */
  revealedAt?: string | null
  thinkSeconds?: number
  /** Recordings left for this question; null = no limit. */
  takesLeft?: number | null
  onRevealed?: (q: RevealedQuestion) => void
  /** True from Show question / Record until the upload has finished (or failed), so the page can hold Next and Submit. */
  onRecording: (busy: boolean) => void
  onSaved: (videoId: string, transcript: string) => void
}) {
  const [state, setState] = useState<'idle' | 'opening' | 'thinking' | 'recording' | 'uploading' | 'error'>('idle')
  const [thinkLeft, setThinkLeft] = useState(0)
  const [left, setLeftTakes] = useState<number | null>(takesLeft)
  const focusLosses = useRef(0)
  const thinkUntil = useRef(0)
  const [elapsed, setElapsed] = useState(0)
  const [heard, setHeard] = useState('')
  const [playback, setPlayback] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])
  const recognition = useRef<Recognition | null>(null)
  const finalText = useRef('')
  const startedAt = useRef(0)
  const pending = useRef<{ blob: Blob; transcript: string; seconds: number; focusLosses: number } | null>(null)
  const saved = (value[0] ?? '') !== ''
  const canTranscribe = speechRecognition() !== null

  useEffect(() => () => {
    recognition.current?.stop()
    if (recorder.current?.state === 'recording') recorder.current.stop()
  }, [])
  useEffect(() => () => {
    if (playback) URL.revokeObjectURL(playback)
  }, [playback])

  useEffect(() => {
    if (state !== 'recording') return
    const timer = window.setInterval(() => {
      const s = (Date.now() - startedAt.current) / 1000
      setElapsed(s)
      if (s >= maxSeconds) stop()
    }, 250)
    return () => window.clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state, maxSeconds])

  // Thinking time: a countdown, then recording starts on its own.
  useEffect(() => {
    if (state !== 'thinking') return
    const tick = () => {
      const s = Math.max(0, Math.ceil((thinkUntil.current - Date.now()) / 1000))
      setThinkLeft(s)
      if (s === 0) start()
    }
    tick()
    const timer = window.setInterval(tick, 250)
    return () => window.clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state])

  // Leaving the window or tab while recording is noted for the team (a signal, not a penalty).
  useEffect(() => {
    if (state !== 'recording') return
    const lost = () => {
      focusLosses.current += 1
    }
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') lost()
    }
    window.addEventListener('blur', lost)
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      window.removeEventListener('blur', lost)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [state])

  // A question opened before a reload: carry on with the thinking time that's left.
  useEffect(() => {
    if (!revealedAt || saved || hidden) return
    const until = new Date(revealedAt).getTime() + thinkSeconds * 1000
    if (until > Date.now() && state === 'idle') {
      thinkUntil.current = until
      setState('thinking')
      onRecording(true)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function open() {
    setError(null)
    if (!stream) {
      setError('Turn on your camera and microphone first (button at the top).')
      return
    }
    setState('opening')
    onRecording(true)
    try {
      const q = await revealQuestion(testId, questionId)
      onRevealed?.(q)
      setLeftTakes(q.takesLeft)
      thinkUntil.current = Date.now() + q.thinkSecondsLeft * 1000
      setState('thinking')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not open the question.')
      setState('idle')
      onRecording(false)
    }
  }

  function listen() {
    const Ctor = speechRecognition()
    if (!Ctor) return
    const r = new Ctor()
    r.lang = 'en-IN'
    r.continuous = true
    r.interimResults = true
    r.onresult = (e) => {
      let interim = ''
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const text = e.results[i][0].transcript
        if (e.results[i].isFinal) finalText.current = `${finalText.current} ${text}`.trim()
        else interim += text
      }
      setHeard(`${finalText.current} ${interim}`.trim())
    }
    // Chrome stops listening after a pause; carry on while recording.
    r.onend = () => {
      if (recorder.current?.state === 'recording' && recognition.current === r) {
        try {
          r.start()
        } catch {
          // already restarting
        }
      }
    }
    r.onerror = () => undefined
    recognition.current = r
    try {
      r.start()
    } catch {
      recognition.current = null
    }
  }

  function start() {
    setError(null)
    if (!stream) {
      setError('Turn on your camera and microphone first (button at the top).')
      setState('idle')
      onRecording(false)
      return
    }
    const type = recordingType()
    let rec: MediaRecorder
    try {
      // About 1.9 MB a minute: clear faces and speech, so a volume holds many more interviews.
      rec = new MediaRecorder(stream, { mimeType: type, videoBitsPerSecond: 200_000, audioBitsPerSecond: 48_000 })
    } catch {
      setError('This browser can’t record video. Please use Chrome or Edge on a laptop or desktop.')
      setState('idle')
      onRecording(false)
      return
    }
    chunks.current = []
    focusLosses.current = 0
    finalText.current = ''
    setHeard('')
    setPlayback(null)
    rec.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.current.push(e.data)
    }
    rec.onstop = () => {
      const blob = new Blob(chunks.current, { type: rec.mimeType || type || 'video/webm' })
      const seconds = Math.min(maxSeconds, (Date.now() - startedAt.current) / 1000)
      setPlayback(URL.createObjectURL(blob))
      pending.current = { blob, transcript: finalText.current, seconds, focusLosses: focusLosses.current }
      void send()
    }
    recorder.current = rec
    startedAt.current = Date.now()
    setElapsed(0)
    rec.start(1000)
    listen()
    setState('recording')
    onRecording(true)
  }

  function stop() {
    const r = recognition.current
    recognition.current = null
    r?.stop()
    if (recorder.current?.state === 'recording') recorder.current.stop()
  }

  async function send() {
    const p = pending.current
    if (!p) return
    setState('uploading')
    // Give the speech engine a moment to finish the last sentence.
    await new Promise((resolve) => window.setTimeout(resolve, 600))
    const transcript = (finalText.current || p.transcript).trim()
    try {
      const result = await uploadAnswer(testId, questionId, p.blob, transcript, p.seconds, p.focusLosses)
      pending.current = null
      setLeftTakes((n) => (n === null ? null : Math.max(0, n - 1)))
      setHeard(transcript)
      onSaved(result.videoId, transcript)
      setState('idle')
      onRecording(false)
    } catch (e) {
      // Stay "busy" so the page won't move on and lose this recording; retry or record again.
      setError(`${e instanceof Error ? e.message : 'Could not upload your answer.'} Your recording is kept — click Try uploading again.`)
      setState('error')
    }
  }

  const remaining = maxSeconds - elapsed
  const transcript = heard || value[1] || ''
  const canRetake = left === null || left > 0
  if (hidden && state !== 'thinking' && state !== 'recording') {
    return (
      <div className="stack" style={{ gap: 8 }}>
        <p className="alert alert-info" style={{ margin: 0 }}>
          This question is shown when you start it. You then have {clock(thinkSeconds)} to read it and think, and recording starts on its own
          (up to {clock(maxSeconds)}).
          {left !== null && left > 1 && ` You can record it again ${left - 1 === 1 ? 'once' : `${left - 1} times`}.`}
        </p>
        <div className="row" style={{ gap: 8 }}>
          <Button disabled={busy || state === 'opening'} onClick={() => void open()}>
            {state === 'opening' ? 'Opening…' : '▶ Show question and start'}
          </Button>
        </div>
        {error && (
          <div role="alert" className="alert alert-error">
            {error}
          </div>
        )}
      </div>
    )
  }
  return (
    <div className="stack" style={{ gap: 8 }}>
      <Preview stream={state === 'recording' || (!playback && !saved) ? stream : null} src={state === 'recording' ? null : playback} />
      <div className="row" style={{ gap: 8 }}>
        {state === 'recording' ? (
          <>
            <Button onClick={stop}>■ Stop and save</Button>
            <strong role="timer" aria-label={`Recording question ${position}`} className={remaining < 15 ? 'test-timer low' : 'test-timer'}>
              ● {clock(elapsed)} / {clock(maxSeconds)}
            </strong>
          </>
        ) : state === 'thinking' ? (
          <>
            <strong role="timer" aria-label={`Thinking time for question ${position}`} className={thinkLeft <= 5 ? 'test-timer low' : 'test-timer'}>
              Recording starts in {clock(thinkLeft)}
            </strong>
            <Button onClick={start}>● Start recording now</Button>
          </>
        ) : state === 'uploading' ? (
          <span className="muted">Uploading your answer…</span>
        ) : (
          <>
            {(!saved || canRetake) && (
              <Button variant={saved ? 'secondary' : 'primary'} disabled={busy && state !== 'error'} onClick={start}>
                {saved ? `↻ Record again${left !== null ? ` (${left} left)` : ''}` : '● Record answer'}
              </Button>
            )}
            <span className="muted" style={{ fontSize: 13 }}>
              Up to {clock(maxSeconds)}
              {saved && ' · ✓ Saved'}
              {saved && !canRetake && ' · no more recordings for this question'}
            </span>
            {state === 'error' && (
              <Button
                variant="secondary"
                onClick={() => {
                  onRecording(true)
                  void send()
                }}
              >
                Try uploading again
              </Button>
            )}
          </>
        )}
      </div>
      {error && (
        <div role="alert" className="alert alert-error">
          {error}
        </div>
      )}
      {!canTranscribe && (
        <p className="alert alert-info" style={{ margin: 0 }}>
          This browser can’t write down what you say, so your answer can only be watched, not read by the reviewer. Chrome or Edge is best.
        </p>
      )}
      {transcript && state !== 'recording' && (
        <details>
          <summary className="muted" style={{ fontSize: 13 }}>
            What we heard
          </summary>
          <p style={{ margin: '6px 0 0', fontSize: 14 }}>{transcript}</p>
        </details>
      )}
    </div>
  )
}
