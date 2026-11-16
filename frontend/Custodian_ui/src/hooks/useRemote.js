import { useCallback, useEffect, useState } from 'react'
import { useApp } from './useApp'
/** Loads genuine service data. Never silently falls back to mocks. */
export function useRemote(loader, dependencies = [], { enabled = true } = {}) {
  const { refreshToken, token } = useApp()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [revision, setRevision] = useState(0)
  const reload = useCallback(() => setRevision(v => v + 1), [])
  useEffect(() => {
    if (!enabled || !token) { setData(null); setError(null); setLoading(false); return }
    let live = true
    setLoading(true); setError(null)
    Promise.resolve().then(() => loader()).then(value => { if (live) setData(value) }).catch(err => { if (live) { setData(null); setError(err) } }).finally(() => { if (live) setLoading(false) })
    return () => { live = false }
    // Loader is defined by the caller; dependencies explicitly control refreshing.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, refreshToken, revision, enabled, ...dependencies])
  return { data, loading, error, reload }
}
