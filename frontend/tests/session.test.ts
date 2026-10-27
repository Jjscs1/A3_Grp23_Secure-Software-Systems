import { afterEach, expect, it, vi } from 'vitest'
import { watchIdle } from '../src/session'

afterEach(() => { vi.useRealTimers() })

it('ends after 90 seconds without activity and sends no automatic keepalive', () => {
  vi.useFakeTimers()
  const end = vi.fn()
  const activity = vi.fn().mockResolvedValue(undefined)
  const stop = watchIdle(end, activity)
  vi.advanceTimersByTime(89_000)
  expect(end).not.toHaveBeenCalled()
  vi.advanceTimersByTime(1000)
  expect(end).toHaveBeenCalledTimes(1)
  expect(activity).not.toHaveBeenCalled()
  vi.advanceTimersByTime(90_000)
  expect(end).toHaveBeenCalledTimes(1)
  stop()
})
it('does not extend idle for script-generated events', () => {
  vi.useFakeTimers()
  const end = vi.fn()
  const activity = vi.fn().mockResolvedValue(undefined)
  const stop = watchIdle(end, activity)
  vi.advanceTimersByTime(80_000)
  window.dispatchEvent(new Event('pointermove'))
  vi.advanceTimersByTime(10_000)
  expect(end).toHaveBeenCalledOnce()
  expect(activity).not.toHaveBeenCalled()
  stop()
})
it('removes timers when signed out or unmounted', () => {
  vi.useFakeTimers()
  const end = vi.fn()
  const stop = watchIdle(end, vi.fn())
  stop()
  vi.advanceTimersByTime(100_000)
  expect(end).not.toHaveBeenCalled()
})

it('reports trusted activity, but throttles repeated events', () => {
  vi.useFakeTimers()
  const listeners = new Map<string, (event: Event) => void>()
  const target = {
    addEventListener: (name: string, fn: (event: Event) => void) => listeners.set(name, fn),
    removeEventListener: (name: string) => listeners.delete(name),
    setInterval: window.setInterval.bind(window),
    clearInterval: window.clearInterval.bind(window),
  } as unknown as Window
  const end = vi.fn()
  const activity = vi.fn().mockResolvedValue(undefined)
  const stop = watchIdle(end, activity, target)
  vi.advanceTimersByTime(20_000)
  listeners.get('keydown')?.({ isTrusted: true } as Event)
  listeners.get('pointermove')?.({ isTrusted: true } as Event)
  expect(activity).toHaveBeenCalledTimes(1)
  vi.advanceTimersByTime(70_000)
  expect(end).not.toHaveBeenCalled()
  vi.advanceTimersByTime(20_000)
  expect(end).toHaveBeenCalledOnce()
  stop()
})
