import { useState } from 'react'
import { CheckCircle2, Link2 } from 'lucide-react'
import { borrowerLinks } from '../api/borrowerLinks'
import { Button, DetailGrid, Field, Panel } from '../components/UI'
import { useApp } from '../hooks/useApp'

export function BorrowerIdentityLinkPanel() {
  const { token, notify } = useApp()
  const [linkToken, setLinkToken] = useState('')
  const [linked, setLinked] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    if (busy || !linkToken.trim()) return
    setBusy(true)
    setError('')
    setLinked(null)
    try {
      const result = await borrowerLinks.link(linkToken.trim())
      setLinked(result)
      setLinkToken('')
      notify(`Borrower ${result.organizationReference} linked`, 'success')
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Panel title="Link Borrower identity" subtitle="Paste the one-time code generated in Borrower → Link with Custodian.">
      <form className="resource-form" onSubmit={submit}>
        <Field
          name="linkToken"
          label="One-time link code"
          value={linkToken}
          onChange={setLinkToken}
          required
          multiline
          max={4096}
          hint="Expires in 24 hours and can be used once."
        />
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="form-actions">
          <Button type="submit" icon={Link2} busy={busy} disabled={!token || busy || !linkToken.trim()}>
            Link Borrower
          </Button>
          <span>Creates the Borrower organization and grants this identity TRADER access.</span>
        </div>
      </form>
      {linked && (
        <div className="form-success">
          <CheckCircle2 size={17} /> Borrower linked. Next create an active lender mandate and MSLA.
          <DetailGrid data={linked} fields={['organizationReference', 'organizationName', 'username', 'email', 'status']} />
        </div>
      )}
    </Panel>
  )
}
