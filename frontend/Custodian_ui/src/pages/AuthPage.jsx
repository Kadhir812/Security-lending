import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Building2, KeyRound, ShieldCheck } from 'lucide-react'
import { auth } from '../api/resources'
import { useApp } from '../hooks/useApp'
import { Button, Field } from '../components/UI'

export default function AuthPage() {
  const { token, saveToken, notify } = useApp()
  const navigate = useNavigate()
  const [signup, setSignup] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [form, setForm] = useState({ username: '', organizationName: '', email: '', password: '' })

  useEffect(() => {
    if (token) navigate('/', { replace: true })
  }, [token, navigate])

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
        ? await auth.signup(form)
        : await auth.login({ email: form.email, password: form.password })
      if (!result?.accessToken) throw new Error('The Custodian service did not return an access token.')
      saveToken(result.accessToken)
      notify(signup ? 'Custodian demo account created.' : 'Signed in to Custodian.', 'success')
      navigate('/', { replace: true })
    } catch (cause) {
      setError(cause.message || 'Unable to sign in. Check the service and try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-brand"><span className="brand-mark">//</span><strong>SEC LEND</strong><span>Custodian portal</span></div>
        <div className="auth-card__heading">
          <p className="eyebrow">CUSTODIAN OPERATIONS</p>
          <h1>{signup ? 'Create your account' : 'Welcome back'}</h1>
          <p>{signup ? 'Register a demo operator account to access the Custodian workspace.' : 'Sign in to your Custodian account to continue.'}</p>
        </div>
        <form className="auth-form" onSubmit={submit}>
          {signup && <>
            <Field name="username" label="Full name" required minLength={2} max={100} value={form.username} onChange={value => update('username', value)} placeholder="Your name" />
            <Field name="organizationName" label="Organization name" required minLength={2} max={255} value={form.organizationName} onChange={value => update('organizationName', value)} placeholder="Custodian organization" />
          </>}
          <Field name="email" label="Work email" type="email" required max={255} value={form.email} onChange={value => update('email', value)} placeholder="you@organization.com" />
          <Field name="password" label="Password" type="password" required minLength={signup ? 12 : 1} max={72} value={form.password} onChange={value => update('password', value)} placeholder={signup ? 'At least 12 characters' : 'Your password'} hint={signup ? 'Use 12 or more characters. Passwords are stored as BCrypt hashes.' : undefined} />
          {error && <div className="auth-error" role="alert">{error}</div>}
          <Button type="submit" icon={signup ? Building2 : KeyRound} busy={busy} className="auth-submit">
            {signup ? 'Create demo account' : 'Sign in'}
          </Button>
        </form>
        <p className="auth-toggle">
          {signup ? 'Already registered?' : 'New to the demo?'}{' '}
          <button type="button" onClick={() => { setSignup(!signup); setError('') }}>
            {signup ? 'Sign in' : 'Create a demo account'}
          </button>
        </p>
        <div className="auth-demo-note"><ShieldCheck size={17} /><p>Demo access only. New accounts automatically receive the fixed <strong>CUSTODIAN_OPERATOR</strong> role.</p></div>
      </section>
    </main>
  )
}
