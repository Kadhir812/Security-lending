import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { getToken, setSessionToken } from '../api/client'
import { foundation } from '../api/resources'
const AppContext = createContext(null)
export function AppProvider({ children }) {
  const [token, setToken] = useState(getToken)
  const [mode, setMode] = useState(() => getToken() ? 'live' : 'demo')
  const [demoSimulations, setDemoSimulations] = useState(false)
  const [correlationId, setCorrelationId] = useState('—')
  const [service, setService] = useState({ state: 'checking', message: 'Checking service' })
  const [notice, setNotice] = useState(null)
  const [refreshToken, setRefreshToken] = useState(0)
  const notify = useCallback((message, tone = 'info') => { setNotice({ message, tone, id: Date.now() }) }, [])
  const saveToken = useCallback((value) => { setSessionToken(value); setToken(getToken()); if (value?.trim()) setMode('live'); setRefreshToken(v => v + 1) }, [])
  const refresh = useCallback(() => setRefreshToken(v => v + 1), [])
  const checkService = useCallback(async () => {
    if (!getToken()) {
      setService({ state: 'offline', message: 'Sign in to connect to the Custodian service.' })
      return
    }
    setService({ state: 'checking', message: 'Checking service' })
    try { const result = await foundation.status(); setService({ state: 'online', message: result?.service || 'Custodian service online', data: result }) }
    catch (error) { setService({ state: 'offline', message: error.message }) }
  }, [])
  useEffect(() => {
    const correlation = (event) => setCorrelationId(event.detail)
    const changed = () => setToken(getToken())
    const unauthorized = () => { setToken(getToken()); notify('Your session expired or was rejected. Sign in again.', 'danger') }
    window.addEventListener('custodian:correlation', correlation)
    window.addEventListener('custodian:token', changed)
    window.addEventListener('custodian:unauthorized', unauthorized)
    return () => { window.removeEventListener('custodian:correlation', correlation); window.removeEventListener('custodian:token', changed); window.removeEventListener('custodian:unauthorized', unauthorized) }
  }, [notify])
  useEffect(() => { checkService() }, [checkService, refreshToken])
  useEffect(() => {
    if (!token) return undefined
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
      if (!Number.isFinite(payload.exp)) return undefined
      const timer = setTimeout(() => {
        saveToken('')
        notify('Your session expired. Sign in again to continue.', 'info')
      }, Math.max(0, payload.exp * 1000 - Date.now()))
      return () => clearTimeout(timer)
    } catch {
      return undefined
    }
  }, [token, saveToken, notify])
  return <AppContext.Provider value={{ token, saveToken, mode, setMode, demoSimulations, setDemoSimulations, correlationId, service, checkService, notice, setNotice, notify, refresh, refreshToken }}>{children}</AppContext.Provider>
}
export function useApp() { const value = useContext(AppContext); if (!value) throw new Error('App provider missing'); return value }
