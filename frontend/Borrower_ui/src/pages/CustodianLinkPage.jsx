import { useState } from 'react'
import { Copy, KeyRound } from 'lucide-react'
import { Button, Field, Panel } from '../components/UI'
import { FormMessage, LiveGate, WorkPage } from '../components/Work'
import { auth } from '../api/auth'
import { useApp } from '../hooks/useApp'

export default function CustodianLinkPage() {
  const { mode, token } = useApp()
  const [linkToken, setLinkToken] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function createCode() {
    setBusy(true)
    setError('')
    setLinkToken('')
    try {
      const result = await auth.custodianLinkToken()
      setLinkToken(result.linkToken)
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy(false)
    }
  }

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(linkToken)
    } catch {
      setError('Could not copy the code. Select and copy it manually.')
    }
  }

  return (
    <WorkPage
      title="Link with Custodian"
      subtitle="Generate a short-lived code for a Custodian operator to link this Borrower identity."
      stats={[
        { label: 'Code lifetime', value: '24 hours', detail: 'Expires automatically' },
        { label: 'Uses', value: 'One', detail: 'Single-use code' },
        { label: 'Approval', value: 'Custodian operator', detail: 'Required to link' },
        { label: 'Admin access', value: 'Not required', detail: 'Borrower receives TRADER access' },
      ]}
      guide={[
        'Generate the code and send it securely to your Custodian operator.',
        'The operator enters it in Custodian → Organizations → Link Borrower.',
        'After linking, the operator must create an active mandate and MSLA.',
      ]}
      footer="This code links identity only. It does not create an MSLA or authorize a loan."
    >
      <LiveGate>
        <Panel title="One-time link code" icon={KeyRound}>
          <p>Give this code to your Custodian operator. It expires in 24 hours and can be used once.</p>
          <div className="form-actions">
            <Button icon={KeyRound} busy={busy} disabled={!token || busy} onClick={createCode}>
              Generate code
            </Button>
          </div>
          <FormMessage error={error} />
          {linkToken && (
            <>
              <Field
                name="linkToken"
                label="Link code"
                value={linkToken}
                readOnly
                multiline
                max={4096}
              />
              <div className="form-actions">
                <Button icon={Copy} variant="outline" onClick={copyCode}>Copy code</Button>
              </div>
            </>
          )}
        </Panel>
      </LiveGate>
    </WorkPage>
  )
}
