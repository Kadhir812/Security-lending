import { Link, useParams } from 'react-router-dom'
import { ArrowRight, FileSearch, Layers3 } from 'lucide-react'
import { DetailGrid, Panel } from '../../components/UI'
import { LiveGate, Refresh, ResourceBody, WorkPage } from '../../components/Work'
import { useApp } from '../../hooks/useApp'
import { useRemote } from '../../hooks/useRemote'
import { loans } from '../../api/loans'
import { number } from '../../utils/format'
import { CollateralInquiryPanel } from './CollateralInquiryPanel'

export function RequestDetailPage() {
  const { reference } = useParams()
  const { mode } = useApp()
  const query = useRemote(
    () => loans.get(reference),
    [reference],
    {
      enabled: mode === 'live' && Boolean(reference),
      pollMs: 4000,
      maxPolls: 6,
      pollCondition: value => ['PENDING', 'QUEUED', 'SENT'].includes(value?.status ?? value?.submissionStatus),
    },
  )
  const contractQuery = useRemote(
    () => loans.contract(reference),
    [reference],
    {
      enabled: mode === 'live' && Boolean(reference) && query.data?.status === 'APPROVED',
      pollMs: 4000,
      maxPolls: 6,
      pollCondition: value => !value || ['APPROVED', 'PENDING_SETTLEMENT'].includes(value?.contractSnapshot?.contractStatus),
    },
  )
  const request = query.data

  return (
    <WorkPage
      title={`Request · ${reference}`}
      subtitle="Inspect current Custodian decision, response reason and eventual contract projection."
      action={<Link className="btn btn--primary" to="/requests/new">New borrow <ArrowRight size={15} /></Link>}
      stats={[
        { label: 'Decision', value: request?.status || '—', detail: 'Returned loan status' },
        { label: 'Submission', value: request?.submissionStatus || '—', detail: 'Outgoing transport status' },
        { label: 'Quantity', value: number(request?.quantity), detail: 'Requested units' },
        { label: 'Version', value: request?.version ?? '—', detail: 'Borrower record revision' },
      ]}
      guide={[
        'PENDING is not approved; refresh after downstream event processing.',
        'REJECTED responses may include responseReason from the Custodian.',
        'APPROVED creates a contract projection; collateral and settlement are independent steps.',
      ]}
      fix="Loan U1 → Custodian AK (approval confirmation)"
    >
      <LiveGate>
        <Panel title="Loan request decision" icon={FileSearch} actions={<Refresh onClick={query.reload} />}>
          <ResourceBody query={query}>
            <DetailGrid
              data={request}
              fields={[
                'requestReference',
                'status',
                'submissionStatus',
                'responseReason',
                'mslaReference',
                'locateReference',
                'securityIsin',
                'quantity',
                'requestedSettlementDate',
                'settlementCurrency',
                'requestedAt',
                'respondedAt',
                'version',
              ]}
            />
          </ResourceBody>
        </Panel>
        {request && <CollateralInquiryPanel requestReference={reference} />}
        {request?.status === 'APPROVED' && (
          <Panel title="Contract projection" icon={Layers3} subtitle="GET /api/loan-requests/{reference}/contract">
            <ResourceBody query={contractQuery}>
              {contractQuery.data && (
                <>
                  <DetailGrid data={contractQuery.data} fields={['transactionReference', 'requestReference', 'receivedAt']} />
                  <Link to={`/contracts/${encodeURIComponent(reference)}`} className="btn btn--primary">
                    Open contract <ArrowRight size={15} />
                  </Link>
                  <Link to={`/collateral/${encodeURIComponent(reference)}`} className="btn btn--outline">
                    Post initial cash collateral
                  </Link>
                </>
              )}
            </ResourceBody>
          </Panel>
        )}
        {request?.status === 'REJECTED' && (
          <p className="info-banner">
            A rejected request cannot be submitted again under its original reference. Review the reason and create a corrected request if appropriate.
          </p>
        )}
      </LiveGate>
    </WorkPage>
  )
}
