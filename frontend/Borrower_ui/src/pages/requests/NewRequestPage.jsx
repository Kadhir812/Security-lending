import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, FilePlus2 } from 'lucide-react'
import { Button, ConfirmDialog, Field, Panel } from '../../components/UI'
import { FormMessage, LiveGate, WorkPage } from '../../components/Work'
import { useApp } from '../../hooks/useApp'
import { useRemote } from '../../hooks/useRemote'
import { newReference } from '../../api/client'
import { loans } from '../../api/loans'
import { number } from '../../utils/format'

const ISIN_PATTERN = /^[A-Z0-9]{12}$/

function getToday() {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

export function NewRequestPage() {
  const { mode, notify, refresh } = useApp()
  const navigate = useNavigate()
  const mslaQuery = useRemote(() => loans.eligible(), [], { enabled: mode === 'live' })
  const [form, setForm] = useState({
    mslaReference: '',
    securityIsin: '',
    quantity: '',
    requestedSettlementDate: '',
    settlementCurrency: '',
  })
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState(null)
  const [sending, setSending] = useState(false)
  const idempotencyKey = useRef(null)
  if (idempotencyKey.current === null) idempotencyKey.current = newReference('LOAN')

  const mslas = mslaQuery.data || []
  const selectedMsla = mslas.find(msla => msla.mslaReference === form.mslaReference)
  const updateForm = (field, value) => setForm(current => ({ ...current, [field]: value }))

  function prepareSubmission(event) {
    event.preventDefault()
    if (sending) return
    setError('')

    const quantity = Number(form.quantity)
    const settlementDate = form.requestedSettlementDate

    if (!selectedMsla) {
      setError('Select an eligible live MSLA.')
      return
    }
    if (!ISIN_PATTERN.test(form.securityIsin)) {
      setError('Security ISIN must be exactly 12 uppercase alphanumeric characters.')
      return
    }
    if (!Number.isSafeInteger(quantity) || quantity < 1) {
      setError('Quantity must be a positive whole number.')
      return
    }
    if (
      !settlementDate
      || settlementDate < getToday()
      || settlementDate < selectedMsla.effectiveDate
      || (selectedMsla.expiryDate && settlementDate > selectedMsla.expiryDate)
    ) {
      setError('Settlement date must be today or later, within the effective MSLA period.')
      return
    }
    if (selectedMsla.baseCurrency !== form.settlementCurrency) {
      setError('Settlement currency must equal the selected MSLA base currency.')
      return
    }

    setSending(true)

    const submission = {
      mslaReference: form.mslaReference,
      securityIsin: form.securityIsin,
      quantity,
      requestedSettlementDate: settlementDate,
      settlementCurrency: form.settlementCurrency,
    }

    setDialog({
      title: 'Submit borrow request',
      subject: `${submission.securityIsin} · ${number(quantity)} · ${submission.mslaReference}`,
      message: 'The request is submitted without locateReference because the Borrower API has no locate routes. Custodian approval, collateral posting and settlement remain separate steps.',
      confirmText: 'Submit loan request',
      execute: async () => {
        setSending(true)
        try {
          const result = await loans.submit(submission, idempotencyKey.current)
          idempotencyKey.current = newReference('LOAN')
          notify(`Loan submitted: ${result.requestReference}`, 'success')
          refresh()
          navigate(`/requests/${encodeURIComponent(result.requestReference)}`)
          return result
        } finally {
          setSending(false)
        }
      },
    })
  }

  return (
    <WorkPage
      title="Submit Borrow Request"
      subtitle="Create a new securities borrowing instruction against a live, eligible MSLA."
      stats={[
        { label: 'Eligible MSLAs', value: mslas.length, detail: 'Service-supplied' },
        { label: 'Locate', value: 'Optional', detail: 'Selector disabled; field omitted' },
        { label: 'Settlement', value: form.requestedSettlementDate || '—', detail: 'Effective date window' },
        { label: 'Delivery', value: 'U1 / Kafka', detail: 'Not the loan approval' },
      ]}
      guide={[
        'MSLA must be active, borrower-owned, and allow this security’s type.',
        'Security must be ACTIVE in Market Data and use the same currency as the MSLA.',
        'The current Borrower API has no locate routes; the optional locateReference is omitted.',
        'Repeated submissions use the same idempotency key until success.',
      ]}
      fix="U1: Borrow request; AK after Custodian approval"
      footer="There is no direct Borrower-side approval API. The Custodian alone approves or rejects the request."
    >
      <LiveGate>
        <Panel
          title="Borrowing instruction"
          icon={FilePlus2}
          subtitle="LoanRequestSubmission.java · required fields and exact JSON names"
        >
          <form className="resource-form" onSubmit={prepareSubmission}>
            {mslaQuery.error && <FormMessage error={mslaQuery.error} />}
            <div className="form-grid">
              <Field
                name="mslaReference"
                label="Eligible MSLA"
                required
                value={form.mslaReference}
                onChange={value => {
                  const msla = mslas.find(item => item.mslaReference === value)
                  setForm(current => ({
                    ...current,
                    mslaReference: value,
                    settlementCurrency: msla?.baseCurrency || '',
                  }))
                }}
                options={mslas.map(msla => ({
                  value: msla.mslaReference,
                  label: `${msla.mslaReference} · ${msla.baseCurrency} · exp ${msla.expiryDate || 'open'}`,
                }))}
                hint="Loaded from GET /api/loan-requests/eligible-mslas"
              />
              <Field
                name="securityIsin"
                label="Security ISIN"
                required
                value={form.securityIsin}
                onChange={value => updateForm('securityIsin', value.toUpperCase().trim())}
                pattern="[A-Z0-9]{12}"
                max={12}
                placeholder="INE002A01018"
                hint="Must be ACTIVE and eligible under the selected MSLA"
              />
              <Field
                name="quantity"
                label="Loan quantity"
                type="number"
                min="1"
                step="1"
                required
                value={form.quantity}
                onChange={value => updateForm('quantity', value)}
                hint="Positive whole securities quantity"
              />
              <Field
                name="requestedSettlementDate"
                label="Requested settlement date"
                type="date"
                min={getToday()}
                required
                value={form.requestedSettlementDate}
                onChange={value => updateForm('requestedSettlementDate', value)}
                hint="Today or later; must fall in MSLA effective/expiry period"
              />
              <Field
                name="settlementCurrency"
                label="Settlement currency"
                readOnly
                required
                value={form.settlementCurrency}
                hint="Automatically taken from eligible MSLA baseCurrency"
              />
              <Field
                name="locateReference"
                label="Approved locate (optional)"
                value="Locate API unavailable"
                options={['Locate API unavailable']}
                disabled
                hint="Loan submission does not require this field."
              />
            </div>
            <FormMessage error={error} />
            <div className="form-actions">
              <Button type="submit" icon={ArrowRight} disabled={sending || mslaQuery.loading || !mslas.length}>
                Review submission
              </Button>
              <Link className="btn btn--outline" to="/requests">Cancel</Link>
              <span>Idempotency-Key is sent by Axios on POST.</span>
            </div>
          </form>
        </Panel>
      </LiveGate>
      {dialog && <ConfirmDialog config={dialog} onClose={() => { setDialog(null); setSending(false) }} />}
    </WorkPage>
  )
}
