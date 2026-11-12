import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { getToken, setSessionToken } from '../api/client'
import { foundation } from '../api/foundation'
const Context = createContext(null)
/** Maintains Bearer session, API correlation, live/demo separation and refresh requests. */
export function AppProvider({ children }) {
  const [token, setToken] = useState(getToken)
  const [mode, setMode] = useState('demo')
  const [correlationId, setCorrelationId] = useState('—')
  const [service, setService] = useState({ state: 'checking', message: 'Awaiting status check' })
  const [notice, setNotice] = useState(null)
  const [refreshToken, setRefreshToken] = useState(0)
  const notify = useCallback((message, tone = 'info') => setNotice({ message, tone, id: Date.now() }), [])
  const saveToken = useCallback(value => { setSessionToken(value); setToken(getToken()); setRefreshToken(n => n + 1) }, [])
  const refresh = useCallback(() => setRefreshToken(n => n + 1), [])
  const checkService = useCallback(async () => {
    if (!getToken()) { setService({ state: 'offline', message: 'Supply a Borrower JWT to query protected /api/foundation/status' }); return }
    setService({ state: 'checking', message: 'Checking borrower service' })
    try { const status = await foundation.status(); setService({ state: 'online', message: `${status?.service || 'borrower-service'} (stage ${status?.stage ?? '?'})` }) }
    catch (error) { setService({ state: 'offline', message: error.message }) }
  }, [])
  useEffect(() => {
    const onCorrelation = e => setCorrelationId(e.detail)
    const onToken = () => setToken(getToken())
    const onAuth = () => notify('Borrower JWT rejected. Verify signature, audience, expiry and local TRADER role.', 'danger')
    window.addEventListener('borrower:correlation', onCorrelation)
    window.addEventListener('borrower:token', onToken)
    window.addEventListener('borrower:unauthorized', onAuth)
    return () => { window.removeEventListener('borrower:correlation', onCorrelation); window.removeEventListener('borrower:token', onToken); window.removeEventListener('borrower:unauthorized', onAuth) }
  }, [notify])
  useEffect(() => { checkService() }, [checkService, token])
  return <Context.Provider value={{ token, saveToken, mode, setMode, correlationId, service, checkService, notice, setNotice, notify, refresh, refreshToken }}>{children}</Context.Provider>
}
export const useApp = () => { const value = useContext(Context); if (!value) throw Error('AppProvider not found'); return value }
