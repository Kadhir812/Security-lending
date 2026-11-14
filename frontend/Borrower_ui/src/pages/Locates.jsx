import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowRight, Plus, RefreshCw, Search } from 'lucide-react'
import { ApiState, Button, DataTable, DetailGrid, EmptyState, Field, KpiStrip, PageHeading, Panel, StatusBadge } from '../components/UI'
import { FormMessage, LiveGate, ResourceBody, WorkPage } from '../components/Work'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { loans } from '../api/loans'
import { locates } from '../api/locates'
import { newReference } from '../api/client'
import { stamp } from '../utils/format'

const ISIN_PATTERN = /^[A-Z0-9]{12}$/

export function LocatesPage() {
  const { mode } = useApp()
  const query = useRemote(() => locates.list(), [], { enabled: mode === 'live' })
  const rows = query.data || []
  return (
    <WorkPage
      title="Locate Requests"
      subtitle="Request confirmation that a lender can make an eligible security available. Approval does not reserve inventory and remains valid until used."
      stats={[
        { label: 'Locate requests', value: query.data?.length ?? '—', detail: 'Borrower-scoped records' },
        { label: 'Pending review', value: rows.filter(row => row.status === 'PENDING').length, detail: 'Custodian decision pending' },
        { label: 'Approved', value: rows.filter(row => row.status === 'APPROVED').length, detail: 'Available until used' },
        { label: 'Inventory hold', value: 'NO', detail: 'Reserved only after loan approval' },
      ]}
      action={<Link className="btn btn--primary" to="/locates/new"><Plus size={15} /> Request locate</Link>}
      guide={[
        'Choose an eligible MSLA, active security and positive whole quantity.',
        'Custodian reviews the request against current lender inventory.',
        'Approval confirms availability and does not reserve shares.',
        'Use an approved locate in one matching loan request.',
      ]}
      fix="Borrower locate request → Custodian review → loan request"
    >
      <LiveGate>
        <Panel title="My locate requests" actions={<Button variant="outline" icon={RefreshCw} onClick={query.reload}>Refresh</Button>}>
          <ApiState token={true} loading={query.loading} error={query.error} reload={query.reload}>
            <DataTable
              keyField="locateReference"
              rows={rows}
              emptyLabel="No locate requests have been submitted."
              columns={[
                { key: 'locateReference', label: 'Locate reference', render: value => <Link to={`/locates/${encodeURIComponent(value)}`}>{value}</Link> },
                { key: 'mslaReference', label: 'MSLA' },
                { key: 'securityIsin', label: 'Security' },
                { key: 'quantity', label: 'Requested' },
                { key: 'approvedQuantity', label: 'Approved' },
                { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> },
                { key: 'requestTime', label: 'Requested at', render: stamp },
                { key: 'expiryTime', label: 'Valid until', render: value => value ? stamp(value) : 'No expiry' },
              ]}
            />
          </ApiState>
        </Panel>
      </LiveGate>
    </WorkPage>
  )
}

export function NewLocatePage() {
  const { mode, notify } = useApp()
  const navigate = useNavigate()
  const mslaQuery = useRemote(() => loans.eligible(), [], { enabled: mode === 'live' })
  const [form, setForm] = useState({ mslaReference: '', securityIsin: '', quantity: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const key = useRef(newReference('LOC'))
  const mslas = mslaQuery.data || []
  const update = (name, value) => setForm(current => ({ ...current, [name]: value }))

  async function submit(event) {
    event.preventDefault()
    setError('')
    const quantity = Number(form.quantity)
    if (!form.mslaReference || !ISIN_PATTERN.test(form.securityIsin) || !Number.isSafeInteger(quantity) || quantity <= 0) {
      setError('Select an eligible MSLA, enter a 12-character ISIN and a positive whole quantity.')
      return
    }
    setBusy(true)
    try {
      const result = await locates.submit(
        { mslaReference: form.mslaReference, securityIsin: form.securityIsin, quantity },
        key.current,
      )
      key.current = newReference('LOC')
      notify(`Locate request submitted: ${result.locateReference}`, 'success')
      navigate(`/locates/${encodeURIComponent(result.locateReference)}`)
    } catch (submitError) {
      setError(submitError.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <WorkPage
      title="Request a Locate"
      subtitle="Ask the Custodian to confirm current availability before submitting a matching loan request. Approval has no expiry."
      guide={[
        'The selected MSLA must belong to this Borrower and be active.',
        'Custodian validates the security against the MSLA and current lender inventory.',
        'Approval does not expire and does not reserve inventory.',
        'A later loan approval performs the authoritative inventory reservation.',
      ]}
      fix="Locate requests are separate from loan approval and settlement"
      footer="A locate is an availability confirmation only. Inventory can change before the loan is approved."
    >
      <LiveGate>
        <Panel title="Locate details" subtitle="The Custodian operator reviews and decides asynchronously.">
          <ResourceBody query={mslaQuery}>
            {mslas.length === 0 ? (
              <EmptyState title="No eligible MSLA" text="Create or activate an MSLA with your Custodian before requesting a locate." />
            ) : (
              <form className="resource-form" onSubmit={submit}>
                <div className="form-grid">
                  <Field
                    name="mslaReference"
                    label="Eligible MSLA"
                    required
                    value={form.mslaReference}
                    onChange={value => update('mslaReference', value)}
                    options={mslas.map(msla => ({ value: msla.mslaReference, label: `${msla.mslaReference} · ${msla.baseCurrency} · ${msla.lenderOrganizationReference}` }))}
                  />
                  <Field
                    name="securityIsin"
                    label="Security ISIN"
                    required
                    value={form.securityIsin}
                    onChange={value => update('securityIsin', value.toUpperCase().trim())}
                    pattern="[A-Z0-9]{12}"
                    max={12}
                    placeholder="INE002A01018"
                  />
                  <Field
                    name="quantity"
                    label="Requested quantity"
                    type="number"
                    min="1"
                    step="1"
                    required
                    value={form.quantity}
                    onChange={value => update('quantity', value)}
                    hint="Whole shares; the Custodian may approve a smaller amount."
                  />
                </div>
                <FormMessage error={error} />
                <div className="form-actions">
                  <Button type="submit" icon={ArrowRight} busy={busy} disabled={busy || mslaQuery.loading}>Submit locate request</Button>
                  <Link className="btn btn--outline" to="/locates">Cancel</Link>
                </div>
              </form>
            )}
          </ResourceBody>
        </Panel>
      </LiveGate>
    </WorkPage>
  )
}

export function LocateDetailPage() {
  const { reference } = useParams()
  const { mode } = useApp()
  const query = useRemote(() => locates.get(reference), [reference], { enabled: mode === 'live' && !!reference })
  const row = query.data
  const expired = row?.status === 'APPROVED' && row.expiryTime && Date.parse(row.expiryTime) <= Date.now()
  const canBorrow = row?.status === 'APPROVED' && !expired
  return (
    <WorkPage
      title="Locate Details"
      subtitle="Review the Custodian decision and its validity before linking it to a loan request."
      action={<Link className="btn btn--outline" to="/locates"><ArrowRight size={15} /> All locates</Link>}
      stats={[
        { label: 'Reference', value: reference || '—', detail: 'Borrower-owned' },
        { label: 'Decision', value: expired ? 'EXPIRED' : row?.status || '—', detail: 'Custodian response' },
        { label: 'Approved quantity', value: row?.approvedQuantity ?? '—', detail: `Requested ${row?.quantity ?? '—'}` },
        { label: 'Valid until', value: row?.expiryTime ? stamp(row.expiryTime) : 'No expiry', detail: 'Remains available until used' },
      ]}
    >
      <LiveGate>
        <Panel title="Locate request status">
          <ApiState token={true} loading={query.loading} error={query.error} reload={query.reload}>
            {row && <>
              <DetailGrid data={row} fields={['locateReference', 'mslaReference', 'securityIsin', 'quantity', 'approvedQuantity', 'status', 'responseReason', 'submissionStatus', 'requestTime', 'respondedAt', 'expiryTime']} />
              {expired && <p className="info-banner" role="status">This locate has expired. Submit a new locate request before creating a loan request.</p>}
              {canBorrow && <Link className="btn btn--primary" to={`/requests/new?locateReference=${encodeURIComponent(row.locateReference)}`}>Create matching loan request <ArrowRight size={15} /></Link>}
            </>}
          </ApiState>
        </Panel>
      </LiveGate>
    </WorkPage>
  )
}
