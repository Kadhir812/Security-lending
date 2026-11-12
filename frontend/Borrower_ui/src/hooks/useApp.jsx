import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { getToken, setSessionToken } from '../api/client'
import { foundation } from '../api/foundation'
const Context = createContext(null)
/** Maintains Bearer session, API correlation, live/demo separation and refresh requests. */
export function AppProvider({ children }) {
  const [token, setToken] = useState(getToken)
  const [mode, setMode] = useState(() => getToken() ? 'live' : 'demo')
  const [correlationId, setCorrelationId] = useState('—')
  const [service, setService] = useState({ state: 'checking', message: 'Awaiting status check' })
  const [notice, setNotice] = useState(null)
  const [refreshToken, setRefreshToken] = useState(0)
  const notify = useCallback((message, tone = 'info') => setNotice({ message, tone, id: Date.now() }), [])
  const saveToken = useCallback(value => {
    setSessionToken(value)
    setToken(getToken())
    setMode(value?.trim() ? 'live' : 'demo')
    setRefreshToken(n => n + 1)
  }, [])
  const refresh = useCallback(() => setRefreshToken(n => n + 1), [])
  const checkService = useCallback(async () => {
    if (!getToken()) { setService({ state: 'offline', message: 'Sign in to connect to the Borrower service.' }); return }
    setService({ state: 'checking', message: 'Checking borrower service' })
    try { const status = await foundation.status(); setService({ state: 'online', message: `${status?.service || 'borrower-service'} (stage ${status?.stage ?? '?'})` }) }
    catch (error) { setService({ state: 'offline', message: error.message }) }
  }, [])
  useEffect(() => {
    const onCorrelation = e => setCorrelationId(e.detail)
    const onToken = () => setToken(getToken())
    const onAuth = () => {
      saveToken('')
      notify('Your Borrower session expired or was rejected. Sign in again.', 'danger')
    }
    window.addEventListener('borrower:correlation', onCorrelation)
    window.addEventListener('borrower:token', onToken)
    window.addEventListener('borrower:unauthorized', onAuth)
    return () => { window.removeEventListener('borrower:correlation', onCorrelation); window.removeEventListener('borrower:token', onToken); window.removeEventListener('borrower:unauthorized', onAuth) }
  }, [notify, saveToken])
  useEffect(() => { checkService() }, [checkService, token])
  useEffect(() => {
    if (!token) return undefined
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
      if (!Number.isFinite(payload.exp)) return undefined
      const timer = setTimeout(() => {
        saveToken('')
        notify('Your Borrower session expired. Sign in again to continue.', 'info')
      }, Math.max(0, payload.exp * 1000 - Date.now()))
      return () => clearTimeout(timer)
    } catch {
      return undefined
    }
  }, [token, saveToken, notify])
  return <Context.Provider value={{ token, saveToken, mode, setMode, correlationId, service, checkService, notice, setNotice, notify, refresh, refreshToken }}>{children}</Context.Provider>
}
export const useApp = () => { const value = useContext(Context); if (!value) throw Error('AppProvider not found'); return value }
