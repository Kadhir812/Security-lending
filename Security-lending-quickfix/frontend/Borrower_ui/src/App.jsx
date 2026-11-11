import { useCallback, useEffect, useState } from 'react'
import { borrowerApi, getApiErrorMessage } from './api/client'
import './App.css'

const TOKEN_KEY = 'borrower.jwt.session'
const USER_KEY = 'borrower.user.session'
function getStoredUser() {
  try {
    const value = sessionStorage.getItem(USER_KEY)
    return value ? JSON.parse(value) : null
  } catch {
    return null
  }
}

async function authenticate(path, body) {
  const response = await borrowerApi.post(`/api/auth/${path}`, body)
  return response.data
}

function AuthPage({ onAuthenticated }) {
  const [signup, setSignup] = useState(false)
  const [form, setForm] = useState({ username: '', organizationName: '', email: '', password: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  function update(name, value) {
    setForm(current => ({ ...current, [name]: value }))
    setError('')
  }

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const result = signup
        ? await authenticate('signup', form)
        : await authenticate('login', { email: form.email, password: form.password })
      onAuthenticated(result)
    } catch (cause) {
      setError(getApiErrorMessage(cause, 'Unable to sign in. Check the service and try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="brand"><span className="brand__mark">SL</span><div><strong>SEC LEND</strong><small>Borrower workspace</small></div></div>
        <p className="eyebrow">SECURITIES LENDING</p>
        <h1>{signup ? 'Create your account' : 'Welcome back'}</h1>
        <p className="auth-intro">{signup ? 'Set up a demo Borrower account to explore the workspace.' : 'Sign in to your Borrower account to continue.'}</p>
        <form onSubmit={submit}>
          {signup && <>
            <label>Full name<input required minLength="2" maxLength="100" autoComplete="name" value={form.username} onChange={event => update('username', event.target.value)} placeholder="Your name" /></label>
            <label>Organization name<input required minLength="2" maxLength="255" autoComplete="organization" value={form.organizationName} onChange={event => update('organizationName', event.target.value)} placeholder="Your organization" /></label>
          </>}
          <label>Work email<input required type="email" maxLength="255" autoComplete="email" value={form.email} onChange={event => update('email', event.target.value)} placeholder="you@organization.com" /></label>
          <label>Password<input required type="password" minLength={signup ? 12 : 1} maxLength="72" autoComplete={signup ? 'new-password' : 'current-password'} value={form.password} onChange={event => update('password', event.target.value)} placeholder={signup ? 'At least 12 characters' : 'Your password'} /></label>
          {signup && <small className="field-note">Use at least 12 characters. Your password is stored as a BCrypt hash.</small>}
          {error && <div className="auth-error" role="alert">{error}</div>}
          <button className="primary-button" type="submit" disabled={busy}><span aria-hidden="true">{signup ? '▦' : '⌑'}</span>{busy ? 'Please wait…' : signup ? 'Create demo account' : 'Sign in'}</button>
        </form>
        <p className="auth-switch">{signup ? 'Already registered?' : 'New to the demo?'} <button type="button" onClick={() => { setSignup(value => !value); setError('') }}>{signup ? 'Sign in' : 'Create an account'}</button></p>
        <div className="demo-notice"><span className="icon-glyph" aria-hidden="true">✓</span><p>Demo access only. New accounts automatically receive the fixed <strong>TRADER</strong> role.</p></div>
      </section>
      <aside className="auth-story"><div className="story-grid"/><div className="story-content"><p className="eyebrow">BORROWER · DIGITAL WORKSPACE</p><h2>Move from request to settlement with clarity.</h2><p>Manage your securities-lending workflow through your dedicated Borrower account.</p><div className="story-points"><span><b aria-hidden="true">✓</b> Organization-scoped access</span><span><b aria-hidden="true">✓</b> Secure, short-lived sessions</span><span><b aria-hidden="true">✓</b> Service-validated actions</span></div></div><div className="story-footer">SEC LEND <span>DEMO ENVIRONMENT</span></div></aside>
    </main>
  )
}

function BorrowerDashboard({ user, onLogout }) {
  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <div className="brand"><span className="brand__mark">SL</span><div><strong>SEC LEND</strong><small>Borrower workspace</small></div></div>
        <button className="logout-button" onClick={onLogout}><span aria-hidden="true">↪</span> Sign out</button>
      </header>
      <section className="welcome-panel">
        <p className="eyebrow">BORROWER WORKSPACE</p>
        <h1>Welcome, {user?.username || 'Borrower'}</h1>
        <p>You’re signed in to the Borrower service as an authorized Trader.</p>
        <div className="organization-card"><span className="icon-glyph" aria-hidden="true">▦</span><div><small>ORGANIZATION</small><strong>{user?.organizationName || 'Borrower organization'}</strong><code>{user?.organizationReference}</code></div></div>
      </section>
      <section className="dashboard-note"><span className="icon-glyph" aria-hidden="true">✓</span><div><strong>Your Borrower session is active</strong><p>The API validates your token and Trader role on every protected request. Sessions expire after 15 minutes; sign in again to continue.</p></div><span className="note-arrow" aria-hidden="true">→</span></section>
      <p className="dashboard-footnote">Borrower workflows and live records appear here as the frontend is implemented. No sample transactions are presented as live data.</p>
    </main>
  )
}

function App() {
  const [token, setToken] = useState(() => {
    try { return sessionStorage.getItem(TOKEN_KEY) || '' } catch { return '' }
  })
  const [user, setUser] = useState(getStoredUser)
  const [checkedToken, setCheckedToken] = useState('')
  const [sessionError, setSessionError] = useState('')

  const clearSession = useCallback(() => {
    try { sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(USER_KEY) } catch { /* browser storage may be unavailable */ }
    setToken('')
    setUser(null)
  }, [])

  function acceptSession(result) {
    try {
      sessionStorage.setItem(TOKEN_KEY, result.accessToken)
      sessionStorage.setItem(USER_KEY, JSON.stringify(result.user))
    } catch {
      setSessionError('Browser session storage is unavailable. Enable it and try again.')
      return
    }
    setUser(result.user)
    setToken(result.accessToken)
    setSessionError('')
  }

  useEffect(() => {
    if (!token) return undefined
    let active = true
    borrowerApi.get('/api/auth/me').then(response => response.data).then(profile => {
      if (!active) return
      setUser(profile)
      try { sessionStorage.setItem(USER_KEY, JSON.stringify(profile)) } catch { /* user profile is only a display cache */ }
      setSessionError('')
    }).catch(error => {
      if (!active) return
      if (error.response?.status === 401) clearSession()
      else setSessionError(getApiErrorMessage(error, 'Unable to verify your session.'))
    }).finally(() => { if (active) setCheckedToken(token) })
    return () => { active = false }
  }, [token, clearSession])

  useEffect(() => {
    if (!token) return undefined
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
      if (!Number.isFinite(payload.exp)) return undefined
      const timer = setTimeout(() => {
        clearSession()
        setSessionError('Your session expired. Please sign in again.')
      }, Math.max(0, payload.exp * 1000 - Date.now()))
      return () => clearTimeout(timer)
    } catch {
      return undefined
    }
  }, [token, clearSession])

  const checking = Boolean(token && checkedToken !== token)
  if (checking) return <main className="session-check"><span className="loader"/>Verifying your session…</main>
  if (!token) return <><AuthPage onAuthenticated={acceptSession}/>{sessionError&&<div className="session-error" role="alert">{sessionError}</div>}</>
  return <BorrowerDashboard user={user} onLogout={clearSession}/>
}

export default App
