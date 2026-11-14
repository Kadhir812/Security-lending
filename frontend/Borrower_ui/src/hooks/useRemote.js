import { useCallback, useEffect, useRef, useState } from 'react'
import { useApp } from './useApp'
/** Loads genuine service data. Never silently falls back to mocks. */
export function useRemote(loader, dependencies = [], { enabled = true, pollMs = 0, maxPolls = 0, pollCondition } = {}) {
  const { refreshToken, token } = useApp()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [revision, setRevision] = useState(0)
  const timerRef = useRef(null)
  const pollsRef = useRef(0)
  const reload = useCallback(() => setRevision(v => v + 1), [])

  useEffect(() => {
    pollsRef.current = 0
    if (!enabled || !token) {
      if (timerRef.current) clearTimeout(timerRef.current)
      timerRef.current = null
      setData(null)
      setError(null)
      setLoading(false)
      return undefined
    }

    let live = true
    const shouldPoll = (value) => {
      if (!Number.isFinite(pollMs) || pollMs <= 0) return false
      if (maxPolls > 0 && pollsRef.current >= maxPolls) return false
      if (typeof pollCondition === 'function') return Boolean(pollCondition(value))
      return false
    }

    const run = async () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }

      setLoading(true)
      setError(null)

      try {
        const value = await Promise.resolve().then(() => loader())
        if (!live) return
        setData(value)
        if (shouldPoll(value)) {
          pollsRef.current += 1
          timerRef.current = setTimeout(() => {
            if (live) setRevision(v => v + 1)
          }, pollMs)
        }
      } catch (err) {
        if (!live) return
        setData(null)
        setError(err)
      } finally {
        if (live) setLoading(false)
      }
    }

    run()

    return () => {
      live = false
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
    // Loader is defined by the caller; dependencies explicitly control refreshing.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, refreshToken, revision, enabled, ...dependencies])

  return { data, loading, error, reload }
}
