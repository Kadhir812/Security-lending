import { useState } from 'react'
import { AlertTriangle, CheckCircle2, RefreshCw, XCircle } from 'lucide-react'
import { ApiState, Button, ConfirmDialog, DataTable, FooterNote, KpiStrip, PageHeading, Panel, StatusBadge } from '../components/UI'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { locates } from '../api/resources'
import { stamp } from '../utils/format'

export default function LocateQueue() {
  const { token, notify } = useApp()
  const [dialog, setDialog] = useState(null)
  const { data, loading, error, reload } = useRemote(() => locates.queue(), [])
  const rows = Array.isArray(data) ? data : []

  function review(row, decision) {
    if (decision === 'approve') {
      const maximum = Math.min(Number(row.quantity), Number(row.availableQuantity))
      setDialog({
        title: 'Approve locate availability',
        message: 'Approval confirms current availability for one hour. It does not reserve inventory; loan approval checks and reserves inventory separately.',
        subject: `${row.locateReference} · ${row.securityIsin} · requested ${row.quantity}`,
        confirmText: 'Approve quantity',
        fields: [{
          name: 'approvedQuantity',
          label: `Approved whole shares (max ${maximum})`,
          type: 'number',
          min: 1,
          max: maximum,
          step: 1,
          required: true,
          defaultValue: String(maximum),
        }],
        execute: async values => {
          const quantity = Number(values.approvedQuantity)
          if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > maximum) {
            throw Error(`Enter a whole quantity from 1 to ${maximum}. Refresh the queue if inventory changed.`)
          }
          const result = await locates.approve(row.locateReference, quantity)
          notify(`Locate ${row.locateReference} approved for ${quantity} shares`, 'success')
          reload()
          return result
        },
      })
      return
    }

    setDialog({
      title: 'Reject locate request',
      message: 'Provide a clear reason. The Borrower will see the rejection in its locate request history.',
      subject: `${row.locateReference} · ${row.securityIsin} · requested ${row.quantity}`,
      danger: true,
      confirmText: 'Reject locate',
      fields: [{ name: 'reason', label: 'Rejection reason', required: true, multiline: true, max: 500 }],
      execute: async values => {
        const result = await locates.reject(row.locateReference, values.reason)
        notify(`Locate ${row.locateReference} rejected`, 'success')
        reload()
        return result
      },
    })
  }

  return (
    <div className="sub-page">
      <PageHeading
        title="Locate Review Queue"
        subtitle="Review Borrower availability requests against the lender’s current inventory."
        action={<Button variant="outline" icon={RefreshCw} onClick={reload}>Refresh queue</Button>}
      />
      <KpiStrip items={[
        { label: 'Pending locates', value: token ? rows.length : '—', detail: 'Scoped to this Custodian' },
        { label: 'Inventory policy', value: 'NO RESERVATION', detail: 'Availability confirmation only' },
        { label: 'Approval validity', value: '1 HOUR', detail: 'From Custodian decision' },
        { label: 'Loan approval', value: 'RECHECKED', detail: 'Reservation happens later' },
      ]} />
      <Panel title="Borrower locate requests">
        <ApiState token={token} loading={loading} error={error} reload={reload}>
          <DataTable
            keyField="locateReference"
            rows={rows}
            emptyLabel="There are no pending locate requests."
            columns={[
              { key: 'locateReference', label: 'Locate reference' },
              { key: 'borrowerOrganizationReference', label: 'Borrower' },
              { key: 'lenderOrganizationReference', label: 'Lender' },
              { key: 'mslaReference', label: 'MSLA' },
              { key: 'securityIsin', label: 'Security' },
              { key: 'quantity', label: 'Requested' },
              { key: 'availableQuantity', label: 'Available now' },
              { key: 'requestTime', label: 'Received', render: stamp },
              { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> },
            ]}
            actions={row => (
              <div className="action-bar">
                <Button
                  variant="approve"
                  icon={CheckCircle2}
                  onClick={() => review(row, 'approve')}
                  disabled={Number(row.availableQuantity) < 1}
                >
                  Approve
                </Button>
                <Button variant="danger" icon={XCircle} onClick={() => review(row, 'reject')}>Reject</Button>
              </div>
            )}
          />
        </ApiState>
      </Panel>
      <div className="info-banner"><AlertTriangle size={17} /> Availability can change after a locate is approved. The actual loan approval rechecks inventory and creates the reservation.</div>
      <FooterNote>Locate approval is not a loan approval, inventory reservation, settlement, or securities delivery.</FooterNote>
      {dialog && <ConfirmDialog config={dialog} onClose={() => setDialog(null)} />}
    </div>
  )
}
