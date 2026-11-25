import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CheckCircle2, Eye, RefreshCw, XCircle } from 'lucide-react'
import { loanRequests, locates } from '../api/resources'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { ApiState, Button, ConfirmDialog, DataTable, DetailGrid, FooterNote, KpiStrip, PageHeading, Pagination, Panel, SideGuide, StatusBadge } from '../components/UI'
import { stamp } from '../utils/format'

const loanColumns = [
  { key: 'requestReference', label: 'Request reference' },
  { key: 'borrowerOrganizationReference', label: 'Borrower' },
  { key: 'mslaReference', label: 'MSLA' },
  { key: 'securityIsin', label: 'Security' },
  { key: 'quantity', label: 'Quantity' },
  { key: 'requestedSettlementDate', label: 'Settlement date' },
  { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> },
  { key: 'requestedAt', label: 'Received', render: stamp },
]
const locateColumns = [
  { key: 'locateReference', label: 'Locate reference' },
  { key: 'borrowerOrganizationReference', label: 'Borrower' },
  { key: 'lenderOrganizationReference', label: 'Lender' },
  { key: 'mslaReference', label: 'MSLA' },
  { key: 'securityIsin', label: 'Security' },
  { key: 'quantity', label: 'Requested' },
  { key: 'availableQuantity', label: 'Available now' },
  { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> },
  { key: 'requestTime', label: 'Received', render: stamp },
]

export default function LendingQueue() {
  const { token, notify } = useApp()
  const [page, setPage] = useState(0)
  const [loanFilter, setLoanFilter] = useState('ALL')
  const [dialog, setDialog] = useState(null)
  const [selected, setSelected] = useState(null)
  const loans = useRemote(() => loanRequests.list(page, 20), [page])
  const locate = useRemote(() => locates.queue(), [])
  const loanRows = (loans.data?.content || []).filter(row => loanFilter === 'ALL' || row.status === loanFilter)
  const locateRows = Array.isArray(locate.data) ? locate.data : []
  const pendingLoans = (loans.data?.content || []).filter(row => row.status === 'PENDING').length
  const pendingLocates = locateRows.filter(row => row.status === 'PENDING').length

  function reviewLocate(row, decision) {
    const subject = `${row.locateReference} · ${row.securityIsin} · requested ${row.quantity}`
    if (decision === 'approve') {
      const maximum = Math.min(Number(row.quantity), Number(row.availableQuantity))
      setDialog({
        title: 'Approve locate availability',
        message: 'This confirms availability at decision time. It does not reserve inventory; loan approval checks and reserves shares separately.',
        subject,
        confirmText: 'Approve quantity',
        fields: [{ name: 'approvedQuantity', label: `Approved whole shares (max ${maximum})`, type: 'number', min: 1, max: maximum, step: 1, required: true, defaultValue: String(maximum) }],
        execute: async values => {
          const quantity = Number(values.approvedQuantity)
          if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > maximum) throw Error(`Enter a whole quantity from 1 to ${maximum}. Refresh if inventory changed.`)
          const result = await locates.approve(row.locateReference, quantity)
          notify(`Locate ${row.locateReference} approved for ${quantity} shares`, 'success')
          locate.reload()
          return result
        },
      })
    } else {
      setDialog({
        title: 'Reject locate request',
        message: 'Provide a clear reason. The Borrower will see it in locate request history.',
        subject,
        danger: true,
        confirmText: 'Reject locate',
        fields: [{ name: 'reason', label: 'Rejection reason', required: true, multiline: true, max: 500 }],
        execute: async values => {
          const result = await locates.reject(row.locateReference, values.reason)
          notify(`Locate ${row.locateReference} rejected`, 'success')
          locate.reload()
          return result
        },
      })
    }
  }

  function reviewLoan(row, decision) {
    setSelected({ type: 'loan', row })
    setDialog({
      title: decision === 'approve' ? 'Approve Borrow Request' : 'Reject Borrow Request',
      message: decision === 'approve'
        ? 'The service validates the MSLA, inventory, credit, and valuation before creating the contract.'
        : 'Reject the request with a clear explanation. The decision is sent asynchronously to the Borrower.',
      subject: `${row.requestReference} · ${row.securityIsin}`,
      danger: decision === 'reject',
      confirmText: decision === 'approve' ? 'Approve request' : 'Reject request',
      fields: decision === 'reject' ? [{ name: 'reason', label: 'Rejection reason', required: true, max: 500, multiline: true }] : [],
      execute: async values => {
        const result = decision === 'approve'
          ? await loanRequests.approve(row.requestReference)
          : await loanRequests.reject(row.requestReference, values.reason)
        notify(`Returned decision: ${result.status || 'received'}`, 'success')
        setSelected({ type: 'loan', row: result })
        loans.reload()
        return result
      },
    })
  }

  return (
    <div className="sub-page">
      <PageHeading
        title="Locate & Loan Request Desk"
        subtitle="Review incoming availability requests and Borrower loan requests from one Custodian workspace."
        action={<Button icon={RefreshCw} variant="outline" onClick={() => { locate.reload(); loans.reload() }}>Refresh both queues</Button>}
      />
      <KpiStrip items={[
        { label: 'Pending locates', value: token ? pendingLocates : '—', detail: 'Current locate queue response' },
        { label: 'Pending loans', value: token ? pendingLoans : '—', detail: 'Current loan page only' },
        { label: 'Locate behavior', value: 'NO RESERVATION', detail: 'Loan approval rechecks supply' },
        { label: 'Loan decisions', value: 'BACKEND', detail: 'Eligibility and valuation are server-owned' },
      ]} />
      <div className="work-area">
        <div className="work-area__main">
          <Panel title="Incoming Locate Requests" subtitle="Availability confirmation does not reserve lender inventory.">
            <ApiState token={token} loading={locate.loading} error={locate.error} reload={locate.reload}>
              <DataTable
                keyField="locateReference"
                rows={locateRows}
                columns={locateColumns}
                emptyLabel="There are no pending locate requests."
                onRow={row => setSelected({ type: 'locate', row })}
                actions={row => <div className="table-actions">
                  <Button variant="outline" icon={Eye} onClick={() => setSelected({ type: 'locate', row })}>Details</Button>
                  <Button variant="approve" icon={CheckCircle2} disabled={row.status !== 'PENDING' || Number(row.availableQuantity) < 1} onClick={() => reviewLocate(row, 'approve')}>Approve</Button>
                  <Button variant="reject" icon={XCircle} disabled={row.status !== 'PENDING'} onClick={() => reviewLocate(row, 'reject')}>Reject</Button>
                </div>}
              />
            </ApiState>
          </Panel>

          <Panel title="Incoming Loan Requests" subtitle="The loan approval rechecks all agreement and supply rules, then reserves inventory.">
            <div className="panel-toolbar"><label>Status <select className="toolbar-select" value={loanFilter} onChange={event => setLoanFilter(event.target.value)}><option value="ALL">All statuses</option>{['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED'].map(value => <option key={value}>{value}</option>)}</select></label></div>
            <ApiState token={token} loading={loans.loading} error={loans.error} reload={loans.reload}>
              <DataTable
                keyField="requestReference"
                rows={loanRows}
                columns={loanColumns}
                emptyLabel="No loan requests match this status."
                onRow={row => setSelected({ type: 'loan', row })}
                actions={row => <div className="table-actions">
                  <Button variant="outline" icon={Eye} onClick={() => setSelected({ type: 'loan', row })}>Details</Button>
                  <Button variant="approve" disabled={row.status !== 'PENDING'} onClick={() => reviewLoan(row, 'approve')}>Approve</Button>
                  <Button variant="reject" disabled={row.status !== 'PENDING'} onClick={() => reviewLoan(row, 'reject')}>Reject</Button>
                </div>}
              />
              <Pagination page={loans.data?.page ?? page} size={loans.data?.size ?? 20} totalPages={loans.data?.totalPages ?? 0} totalElements={loans.data?.totalElements ?? 0} onPage={setPage} />
            </ApiState>
          </Panel>

          {selected && <Panel
            title={`${selected.type === 'locate' ? 'Locate' : 'Loan'} request details`}
            actions={selected.type === 'loan' && selected.row.requestReference && <Link className="btn btn--outline" to={`/requests/review/${encodeURIComponent(selected.row.requestReference)}`}>Full loan review</Link>}
          >
            <DetailGrid data={selected.row} />
          </Panel>}
        </div>
        <div className="work-area__side">
          <SideGuide
            title="Request controls"
            items={[
              'Locate approval confirms available quantity only; it does not reserve shares.',
              'Loan approval rechecks current MSLA, inventory, credit, and valuation, then creates the authoritative contract.',
              'Use Details for an in-page summary or Full loan review for agreement, inventory, and credit context.',
              'Refresh after asynchronous Kafka updates. A failed or unknown decision must not be retried automatically.',
            ]}
            fix="Borrower locate → Custodian locate decision → Borrower loan request → Custodian loan decision"
            note="The two request types stay distinct and use their existing backend decision endpoints."
          />
        </div>
      </div>
      <FooterNote>Locate approval is not a loan approval, inventory reservation, settlement, or securities delivery. Loan decisions and downstream Borrower updates can arrive asynchronously.</FooterNote>
      {dialog && <ConfirmDialog config={dialog} onClose={() => setDialog(null)} />}
    </div>
  )
}
