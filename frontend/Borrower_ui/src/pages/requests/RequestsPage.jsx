import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ClipboardList, Plus } from 'lucide-react'
import { DataTable, Pagination, Panel, StatusBadge } from '../../components/UI'
import { LiveGate, Refresh, ResourceBody, WorkPage } from '../../components/Work'
import { useApp } from '../../hooks/useApp'
import { useRemote } from '../../hooks/useRemote'
import { loans } from '../../api/loans'
import { number, stamp } from '../../utils/format'

export function RequestsPage() {
  const { mode } = useApp()
  const [page, setPage] = useState(0)
  const query = useRemote(() => loans.list(page, 20), [page], { enabled: mode === 'live' })
  const data = query.data
  const rows = data?.content || []

  return (
    <WorkPage
      title="My Borrow Requests"
      subtitle="Track submitted loans from acceptance through Custodian decision and contract projection."
      action={<Link to="/requests/new" className="btn btn--primary"><Plus size={15} /> Submit borrow</Link>}
      stats={[
        { label: 'Total requests', value: data?.totalElements ?? '—', detail: 'Server PageResponse' },
        { label: 'Awaiting decision', value: data ? rows.filter(row => row.status === 'PENDING').length : '—', detail: 'Current page' },
        { label: 'Approved', value: data ? rows.filter(row => row.status === 'APPROVED').length : '—', detail: 'Current page' },
        { label: 'Rejected', value: data ? rows.filter(row => row.status === 'REJECTED').length : '—', detail: 'Current page' },
      ]}
      guide={[
        'Only requests from your Borrower organization appear.',
        'Submission status (QUEUED / SENT / FAILED) differs from loan decision status (PENDING / APPROVED / REJECTED).',
        'The Custodian enforces MSLA, inventory, credit and valuation rules asynchronously.',
      ]}
      fix="U1 (project loan request) → AK (trade confirmation)"
      footer="A created request is not a confirmed securities transfer. Inspect the approval and settlement progress separately."
    >
      <LiveGate>
        <Panel
          title="Loan request ledger"
          icon={ClipboardList}
          actions={<Refresh onClick={query.reload} />}
          subtitle="GET /api/loan-requests · page numbers are zero-based"
        >
          <ResourceBody query={query} empty={!rows.length} emptyText="No real loan requests found for this identity.">
            <DataTable
              keyField="requestReference"
              rows={rows}
              columns={[
                {
                  key: 'requestReference',
                  label: 'Request ID',
                  render: value => (
                    <Link className="record-link" to={`/requests/${encodeURIComponent(value)}`}>
                      {value}
                    </Link>
                  ),
                },
                { key: 'mslaReference', label: 'MSLA' },
                { key: 'securityIsin', label: 'ISIN' },
                { key: 'quantity', label: 'Qty', render: number },
                { key: 'requestedSettlementDate', label: 'Settle' },
                { key: 'submissionStatus', label: 'Delivery', render: value => <StatusBadge value={value} /> },
                { key: 'status', label: 'Decision' },
                { key: 'requestedAt', label: 'Requested', render: stamp },
              ]}
            />
            <Pagination
              page={data?.page ?? page}
              size={data?.size ?? 20}
              totalPages={data?.totalPages ?? 0}
              totalElements={data?.totalElements ?? 0}
              onPage={setPage}
            />
          </ResourceBody>
        </Panel>
      </LiveGate>
    </WorkPage>
  )
}
