import { useEffect, useRef, useState } from 'react'
import { uploadAnswer } from '../api/aiInterviews'
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
 */
export function VideoAnswer({
  testId,
  questionId,
  position,
  maxSeconds,
  stream,
  value,
  busy,
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
  /** True from Record until the upload has finished (or failed), so the page can hold Next and Submit. */
  onRecording: (busy: boolean) => void
  onSaved: (videoId: string, transcript: string) => void
}) {
  const [state, setState] = useState<'idle' | 'recording' | 'uploading' | 'error'>('idle')
  const [elapsed, setElapsed] = useState(0)
  const [heard, setHeard] = useState('')
  const [playback, setPlayback] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])
  const recognition = useRef<Recognition | null>(null)
  const finalText = useRef('')
  const startedAt = useRef(0)
  const pending = useRef<{ blob: Blob; transcript: string; seconds: number } | null>(null)
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
      return
    }
    const type = recordingType()
    let rec: MediaRecorder
    try {
      rec = new MediaRecorder(stream, { mimeType: type, videoBitsPerSecond: 350_000, audioBitsPerSecond: 64_000 })
    } catch {
      setError('This browser can’t record video. Please use Chrome or Edge on a laptop or desktop.')
      return
    }
    chunks.current = []
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
      pending.current = { blob, transcript: finalText.current, seconds }
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
      const result = await uploadAnswer(testId, questionId, p.blob, transcript, p.seconds)
      pending.current = null
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

  const left = maxSeconds - elapsed
  const transcript = heard || value[1] || ''
  return (
    <div className="stack" style={{ gap: 8 }}>
      <Preview stream={state === 'recording' || (!playback && !saved) ? stream : null} src={state === 'recording' ? null : playback} />
      <div className="row" style={{ gap: 8 }}>
        {state === 'recording' ? (
          <>
            <Button onClick={stop}>■ Stop and save</Button>
            <strong role="timer" aria-label={`Recording question ${position}`} className={left < 15 ? 'test-timer low' : 'test-timer'}>
              ● {clock(elapsed)} / {clock(maxSeconds)}
            </strong>
          </>
        ) : state === 'uploading' ? (
          <span className="muted">Uploading your answer…</span>
        ) : (
          <>
            <Button variant={saved ? 'secondary' : 'primary'} disabled={busy && state !== 'error'} onClick={start}>
              {saved ? '↻ Record again' : '● Record answer'}
            </Button>
            <span className="muted" style={{ fontSize: 13 }}>
              Up to {clock(maxSeconds)}
              {saved && ' · ✓ Saved'}
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
