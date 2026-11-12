import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowRight, Banknote, Clock3, FileText, Layers3, ShieldCheck } from 'lucide-react'
import { Button, ConfirmDialog, DataTable, DetailGrid, Field, Lifecycle, Panel } from '../components/UI'
import { ContractPicker, FormMessage, LiveGate, ResourceBody, Refresh, WorkPage } from '../components/Work'
import { contracts } from '../api/contracts'
import { loans } from '../api/loans'
import { newReference } from '../api/client'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { money, number, stamp } from '../utils/format'

const SNAPSHOT_FIELDS = [
  'contractStatus',
  'securityIsin',
  'quantity',
  'deliveredQuantity',
  'returnedQuantity',
  'outstandingQuantity',
  'settlementCurrency',
  'requiredCashCollateral',
  'confirmedCashCollateral',
  'feeRate',
  'settlementDate',
  'loanStartDate',
  'maturityDate',
]

/** ContractPortfolio: the service exposes approved requests and request-scoped contract views. */
export function ContractPortfolioPage() {
  const { mode } = useApp()
  const query = useRemote(() => loans.list(0, 100), [], { enabled: mode === 'live' })
  const requests = query.data?.content || []
  const approved = requests.filter(request => request.status === 'APPROVED')

  return (
    <WorkPage
      title="Approved Borrow Requests"
      subtitle="Open an approved loan request to resolve its contract projection; no general contract-list endpoint is exposed."
      action={<Link className="btn btn--primary" to="/requests/new">New borrow <ArrowRight size={15} /></Link>}
      stats={[
        { label: 'Approved requests', value: query.data ? approved.length : '—', detail: 'Current request page' },
        { label: 'Awaiting decision', value: query.data ? requests.filter(request => request.status === 'PENDING').length : '—', detail: 'Current request page' },
        { label: 'Rejected', value: query.data ? requests.filter(request => request.status === 'REJECTED').length : '—', detail: 'Current request page' },
        { label: 'Contract access', value: 'Per request', detail: 'GET /loan-requests/{reference}/contract' },
      ]}
      guide={[
        'The Borrower API does not expose GET /api/contracts.',
        'Approved requests can be opened to retrieve their individual contract projection.',
        'Settlement, return, fee and release endpoints remain scoped by the returned transaction reference.',
      ]}
      fix="Approved loan request → request-scoped contract projection"
    >
      <LiveGate>
        <Panel title="Approved loan requests" icon={Layers3} actions={<Refresh onClick={query.reload} />} subtitle="GET /api/loan-requests">
          <ResourceBody query={query} empty={!approved.length} emptyText="No approved loan requests were returned for this identity.">
            <DataTable
              rows={approved}
              keyField="requestReference"
              columns={[
                { key: 'requestReference', label: 'Request', render: value => <Link to={`/contracts/${encodeURIComponent(value)}`} className="record-link">{value}</Link> },
                { key: 'mslaReference', label: 'MSLA' },
                { key: 'securityIsin', label: 'Security ISIN' },
                { key: 'quantity', label: 'Quantity', render: number },
                { key: 'requestedSettlementDate', label: 'Settlement date' },
                { key: 'requestedAt', label: 'Requested', render: stamp },
              ]}
            />
          </ResourceBody>
        </Panel>
      </LiveGate>
    </WorkPage>
  )
}

/** ContractDetail: uses the supported request-scoped projection endpoint. */
export function ContractDetailPage() {
  const { requestReference } = useParams()
  const { mode } = useApp()
  const query = useRemote(() => loans.contract(requestReference), [requestReference], { enabled: mode === 'live' })
  const projection = query.data
  const snapshot = projection?.contractSnapshot

  return (
    <WorkPage
      title={`Contract · ${projection?.transactionReference || requestReference}`}
      subtitle="Inspect the Borrower-owned projection and act on authorized settlement, margin and return workflows."
      stats={[
        { label: 'Status', value: snapshot?.contractStatus || '—', detail: 'From server snapshot' },
        { label: 'Quantity', value: number(snapshot?.quantity), detail: 'Borrowed securities' },
        { label: 'Outstanding', value: number(snapshot?.outstandingQuantity), detail: 'Subject to return' },
        { label: 'CASH coverage', value: money(snapshot?.confirmedCashCollateral, snapshot?.settlementCurrency || 'INR'), detail: 'Confirmed collateral' },
      ]}
      guide={[
        'The contract is resolved from its approved loan request; no contract-list API is used.',
        'Post initial collateral only for APPROVED / PENDING_SETTLEMENT contracts.',
        'Return securities and request final release only when the server prerequisites are met.',
      ]}
      fix="AK → initial settlement → margin → return → release"
    >
      <LiveGate>
        <Panel title="Contract record" icon={FileText} actions={<Refresh onClick={query.reload} />}>
          <ResourceBody query={query}>
            {projection && (
              <>
                <DetailGrid data={projection} fields={['transactionReference', 'requestReference', 'receivedAt']} />
                <DetailGrid data={snapshot} fields={SNAPSHOT_FIELDS} />
                <div className="action-grid">
                  <Link className="action-link" to={`/settlement/${encodeURIComponent(requestReference)}`}>Settlement <ArrowRight size={15} /></Link>
                  <Link className="action-link" to={`/margin/${encodeURIComponent(requestReference)}`}>Margin calls <ArrowRight size={15} /></Link>
                  <Link className="action-link" to={`/returns/${encodeURIComponent(requestReference)}`}>Return shares <ArrowRight size={15} /></Link>
                  <Link className="action-link" to={`/fees/${encodeURIComponent(requestReference)}`}>Fees &amp; payments <ArrowRight size={15} /></Link>
                  <Link className="action-link" to={`/releases/${encodeURIComponent(requestReference)}`}>Collateral release <ArrowRight size={15} /></Link>
                  <Link className="action-link" to={`/collateral/${encodeURIComponent(requestReference)}`}>Initial cash collateral <ArrowRight size={15} /></Link>
                </div>
              </>
            )}
          </ResourceBody>
        </Panel>
        {snapshot && <div className="dashboard-lifecycle contract-lifecycle"><Lifecycle current={snapshot.contractStatus || 'UNKNOWN'} /></div>}
      </LiveGate>
    </WorkPage>
  )
}

/** Settlement: read-only GET by transaction reference after resolving an approved request. */
export function SettlementPage() {
  const { requestReference: routeReference } = useParams()
  const navigate = useNavigate()
  const { mode } = useApp()
  const requestQuery = useRemote(() => loans.list(0, 100), [], { enabled: mode === 'live' })
  const [chosenReference, setChosenReference] = useState('')
  const requestReference = routeReference || chosenReference
  const approvedRequests = (requestQuery.data?.content || []).filter(request => request.status === 'APPROVED')
  const projectionQuery = useRemote(() => loans.contract(requestReference), [requestReference], { enabled: mode === 'live' && Boolean(requestReference) })
  const transactionReference = projectionQuery.data?.transactionReference
  const query = useRemote(() => contracts.settlement(transactionReference), [transactionReference], { enabled: mode === 'live' && Boolean(transactionReference) })
  const data = query.data
  const instructions = data?.settlementInstructions || []
  const collateral = data?.collateralInstructions || []
  const releases = data?.collateralReleaseInstructions || []

  function chooseRequest(reference) {
    if (routeReference) navigate(`/settlement/${encodeURIComponent(reference)}`)
    else setChosenReference(reference)
  }

  return (
    <WorkPage
      title="Settlement Operations Tracker"
      subtitle="Monitor securities delivery, CASH collateral and final release state without initiating Custodian-only confirmations."
      stats={[
        { label: 'Contract state', value: data?.contractStatus || '—', detail: 'Current projection' },
        { label: 'Delivered units', value: number(data?.deliveredQuantity), detail: 'Confirmed settlement' },
        { label: 'Outstanding', value: number(data?.outstandingQuantity), detail: 'After returns' },
        { label: 'Cash confirmed', value: money(data?.confirmedCashCollateral), detail: 'Value returned by service' },
      ]}
      guide={[
        'Custodian controls settlement simulation and confirmations; Borrower can only observe its projection.',
        'A submitted cash instruction is not a confirmed settlement.',
        'Securities delivery and cash posting are distinct instructions; refresh after event processing.',
      ]}
      fix="U2 initial securities delivery / cash collateral event"
    >
      <LiveGate>
        <Panel title="Select approved loan request" icon={FileText}>
          <ResourceBody query={requestQuery}>
            <ContractPicker items={approvedRequests} value={requestReference} onChange={chooseRequest} />
          </ResourceBody>
        </Panel>
        {requestReference && (
          <Panel
            title={`Settlement view · ${transactionReference || requestReference}`}
            icon={Clock3}
            actions={<Refresh onClick={query.reload} />}
          >
            <ResourceBody query={projectionQuery}>
              {transactionReference ? (
                <ResourceBody query={query}>
                  <DetailGrid data={data} fields={['transactionReference', 'contractStatus', 'quantity', 'deliveredQuantity', 'returnedQuantity', 'outstandingQuantity', 'requiredCashCollateral', 'confirmedCashCollateral']} />
                  <h3 className="subsection-title">Settlement instructions</h3>
                  <DataTable rows={instructions} columns={[
                    { key: 'instructionReference', label: 'Instruction' },
                    { key: 'instructionType', label: 'Type' },
                    { key: 'quantity', label: 'Quantity' },
                    { key: 'status', label: 'Status' },
                    { key: 'confirmedAt', label: 'Confirmed', render: stamp },
                  ]} />
                  <h3 className="subsection-title">Collateral instructions</h3>
                  <DataTable rows={collateral} columns={[
                    { key: 'instructionReference', label: 'Instruction' },
                    { key: 'amount', label: 'Amount' },
                    { key: 'currency', label: 'CCY' },
                    { key: 'collateralType', label: 'Type' },
                    { key: 'status', label: 'Status' },
                  ]} />
                  <h3 className="subsection-title">Release instructions</h3>
                  <DataTable rows={releases} columns={[
                    { key: 'releaseReference', label: 'Release' },
                    { key: 'amount', label: 'Amount' },
                    { key: 'status', label: 'Status' },
                  ]} />
                </ResourceBody>
              ) : (
                <div className="empty-inline" role="status">No transaction reference is available for this request yet.</div>
              )}
            </ResourceBody>
          </Panel>
        )}
      </LiveGate>
    </WorkPage>
  )
}

/** InitialCash: approved request reference, exact CASH DTO and state prerequisites. */
export function CashCollateralPage() {
  const { requestReference: routeReference } = useParams()
  const navigate = useNavigate()
  const { mode, notify, refresh } = useApp()
  const requestQuery = useRemote(() => loans.list(0, 100), [], { enabled: mode === 'live' })
  const [chosenReference, setChosenReference] = useState('')
  const requestReference = routeReference || chosenReference
  const contractQuery = useRemote(() => loans.contract(requestReference), [requestReference], { enabled: mode === 'live' && Boolean(requestReference) })
  const snapshot = contractQuery.data?.contractSnapshot
  const [form, setForm] = useState({ instructionReference: newReference('CASH'), amount: '', currency: '', collateralType: 'CASH' })
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState(null)
  const [busy, setBusy] = useState(false)

  const currency = snapshot?.settlementCurrency || ''
  const status = snapshot?.contractStatus

  function chooseRequest(reference) {
    if (routeReference) navigate(`/collateral/${encodeURIComponent(reference)}`)
    else setChosenReference(reference)
  }

  function prepare(event) {
    event.preventDefault()
    setError('')
    if (!['APPROVED', 'PENDING_SETTLEMENT'].includes(status)) {
      setError('Only APPROVED and PENDING_SETTLEMENT contracts accept initial CASH collateral.')
      return
    }
    if (!/^[A-Za-z0-9._:-]{1,80}$/.test(form.instructionReference) || !(Number(form.amount) > 0) || !currency) {
      setError('Enter a valid instruction reference and positive amount with a contract currency.')
      return
    }
    const body = { instructionReference: form.instructionReference, amount: Number(form.amount), currency, collateralType: 'CASH' }
    setDialog({
      title: 'Post initial CASH collateral',
      message: 'This submits a CASH instruction for an approved loan, not a confirmed bank/depository settlement. Custodian processing and Kafka projection follow.',
      subject: `${requestReference} · ${currency} ${form.amount}`,
      confirmText: 'Submit cash instruction',
      execute: async () => {
        setBusy(true)
        try {
          const result = await loans.cash(requestReference, body)
          notify(`CASH instruction ${result.instructionReference} submitted`, 'success')
          refresh()
          contractQuery.reload()
          setForm({ instructionReference: newReference('CASH'), amount: '', currency: '', collateralType: 'CASH' })
          return result
        } finally {
          setBusy(false)
        }
      },
    })
  }

  return (
    <WorkPage
      title="Initial Cash Collateral"
      subtitle="Provide the initial cash backing for a Custodian-approved lending transaction."
      stats={[
        { label: 'Contract', value: contractQuery.data?.transactionReference || '—', detail: 'From request projection' },
        { label: 'Status', value: status || '—', detail: 'APPROVED / PENDING_SETTLEMENT required' },
        { label: 'Required', value: money(snapshot?.requiredCashCollateral, currency || 'INR'), detail: 'Custodian valuation' },
        { label: 'Currency', value: currency || '—', detail: 'Must match contract' },
      ]}
      fix="Borrower COLLATERAL_INSTRUCTION → Custodian settlement"
      guide={[
        'Select the approved Borrower request, not the transaction reference.',
        'Only CASH collateral is currently supported; currency must match contract.',
        'A unique instructionReference is required. Confirmation remains Custodian-controlled.',
      ]}
    >
      <LiveGate>
        <Panel title="Select approved loan" icon={FileText}>
          <ResourceBody query={requestQuery}>
            <div className="field">
              <label htmlFor="cash-loan-ref">Loan request reference</label>
              <select id="cash-loan-ref" value={requestReference} onChange={event => chooseRequest(event.target.value)}>
                <option value="">Select a request...</option>
                {(requestQuery.data?.content || []).filter(request => request.status === 'APPROVED').map(request => (
                  <option key={request.requestReference} value={request.requestReference}>
                    {request.requestReference} · {request.securityIsin}
                  </option>
                ))}
              </select>
              <small>Loans outside the first 100 requests may be opened from their detail page.</small>
            </div>
          </ResourceBody>
        </Panel>
        {requestReference && (
          <Panel title="CASH collateral instruction" icon={Banknote}>
            <ResourceBody query={contractQuery}>
              {snapshot && (
                <form className="resource-form" onSubmit={prepare}>
                  <div className="form-grid">
                    <Field name="instructionReference" label="Unique instruction reference" required value={form.instructionReference} onChange={value => setForm(current => ({ ...current, instructionReference: value }))} max={80} />
                    <Field name="amount" label="CASH amount" required type="number" min="0.01" step="0.01" value={form.amount} onChange={value => setForm(current => ({ ...current, amount: value }))} />
                    <Field name="currency" label="Currency" value={currency} readOnly hint="Must equal contract settlementCurrency" />
                    <Field name="collateralType" label="Collateral type" value="CASH" readOnly hint="Backend currently only supports CASH" />
                  </div>
                  <FormMessage error={error} />
                  <div className="form-actions">
                    <Button disabled={busy || !['APPROVED', 'PENDING_SETTLEMENT'].includes(status)} type="submit" icon={ShieldCheck}>Review cash instruction</Button>
                  </div>
                </form>
              )}
            </ResourceBody>
          </Panel>
        )}
      </LiveGate>
      {dialog && <ConfirmDialog config={dialog} onClose={() => setDialog(null)} />}
    </WorkPage>
  )
}
