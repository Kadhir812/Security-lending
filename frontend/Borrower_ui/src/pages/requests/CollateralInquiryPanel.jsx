import { useState } from 'react'
import { ArrowRight, ShieldCheck } from 'lucide-react'
import { Button, DataTable, Panel } from '../../components/UI'
import { FormMessage, Refresh, ResourceBody } from '../../components/Work'
import { useApp } from '../../hooks/useApp'
import { useRemote } from '../../hooks/useRemote'
import { loans } from '../../api/loans'
import { stamp } from '../../utils/format'

export function CollateralInquiryPanel({ requestReference }) {
  const { mode } = useApp()
  const reportQuery = useRemote(
    () => loans.collateralReports(requestReference),
    [requestReference],
    { enabled: mode === 'live' },
  )
  const [receipt, setReceipt] = useState(null)
  const [submitError, setSubmitError] = useState('')
  const [sending, setSending] = useState(false)
  const reports = reportQuery.data || []

  async function submitInquiry() {
    setSending(true)
    setSubmitError('')
    try {
      const result = await loans.collateralInquiry(requestReference)
      setReceipt(result)
      reportQuery.reload()
    } catch (error) {
      setSubmitError(error.message || 'The collateral inquiry could not be submitted.')
    } finally {
      setSending(false)
    }
  }

  return (
    <Panel
      title="Collateral inquiry · FIX BB / BA"
      icon={ShieldCheck}
      actions={<Refresh onClick={reportQuery.reload} />}
      subtitle={`POST /api/loan-requests/${requestReference}/collateral-inquiries · GET collateral-reports`}
    >
      {receipt && (
        <div className="info-banner" role="status">
          Inquiry {receipt.inquiryId} was accepted with status {receipt.status}. This receipt confirms
          the request was queued; it is not a collateral report, cash posting or settlement.
        </div>
      )}
      <FormMessage error={submitError} />
      <ResourceBody
        query={reportQuery}
        empty={!reports.length}
        emptyText={receipt
          ? 'The inquiry is pending; no collateral report has been returned yet. Refresh to check again.'
          : 'No collateral report has been returned for this request yet.'}
      >
        <DataTable
          keyField="reportId"
          rows={reports}
          columns={[
            { key: 'reportId', label: 'Report' },
            { key: 'inquiryId', label: 'Inquiry' },
            { key: 'status', label: 'Status' },
            { key: 'collateralType', label: 'Collateral' },
            { key: 'currency', label: 'Currency' },
            { key: 'haircutPercentage', label: 'Haircut', render: value => value == null ? '—' : `${value}%` },
            { key: 'coveragePercentage', label: 'Coverage', render: value => value == null ? '—' : `${value}%` },
            { key: 'receivedAt', label: 'Received', render: stamp },
          ]}
        />
      </ResourceBody>
      <div className="form-actions">
        <Button onClick={submitInquiry} icon={ArrowRight} busy={sending} disabled={Boolean(receipt)}>
          {receipt ? 'Inquiry queued' : 'Request collateral inquiry'}
        </Button>
        <span>Receipt does not mean cash settlement.</span>
      </div>
    </Panel>
  )
}
