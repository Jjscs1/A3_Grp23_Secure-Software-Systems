import { apiFetch } from './api/http'

let openingToken: string | undefined
export interface SessionState { castReady: boolean }
let opening: Promise<SessionState> | undefined

// StrictMode mounts effects twice. Only one bootstrap request is made per access token.
export function startSession(token: string, idToken: string): Promise<SessionState> {
  if (openingToken !== token || !opening) {
    openingToken = token
    opening = apiFetch<void>('/api/session/start', { method: 'POST', json: { idToken } })
      .then(() => apiFetch<SessionState>('/api/session/status'))
  }
  return opening
}

/** Only trusted user interaction extends the polling-terminal session, never a timer alone. */
export function watchIdle(onEnd: () => void, onActivity: () => Promise<void>,
  target: Window = window, now: () => number = Date.now): () => void {
  let lastActivity = now()
  let lastSent = lastActivity
  let stopped = false
  const end = () => {
    if (stopped) return
    stopped = true
    onEnd()
  }
  const interact = (event: Event) => {
    if (stopped || !event.isTrusted) return
    const time = now()
    if (time - lastActivity >= 90_000) { end(); return }
    lastActivity = time
    if (time - lastSent >= 15_000) {
      lastSent = time
      void onActivity().catch(end)
    }
  }
  const events = ['pointerdown', 'keydown', 'pointermove', 'wheel', 'touchstart']
  events.forEach(name => target.addEventListener(name, interact, { passive: true }))
  const timer = target.setInterval(() => {
    if (now() - lastActivity >= 90_000) end()
  }, 1000)
  return () => {
    stopped = true
    target.clearInterval(timer)
    events.forEach(name => target.removeEventListener(name, interact))
  }
}
