import { Link } from 'react-router-dom'
import { ArrowRight, ShieldCheck } from 'lucide-react'
import { Panel } from '../../components/UI'
import { WorkPage } from '../../components/Work'

export function CollateralInquiryPage() {
  return (
    <WorkPage
      title="Collateral Inquiry / Report · FIX BB / BA"
      subtitle="Collateral inquiries and report history are available from each loan request."
      stats={[
        { label: 'Inquiry', value: 'POST', detail: 'Per loan request' },
        { label: 'Report history', value: 'GET', detail: 'Per loan request' },
        { label: 'Receipt', value: 'Queued', detail: 'Not a collateral report' },
        { label: 'Settlement', value: 'Separate', detail: 'Not implied by receipt' },
      ]}
      guide={[
        'Open a loan request to submit a collateral inquiry and review returned reports.',
        'A queued inquiry receipt is not a collateral report.',
        'Collateral reporting does not post cash or confirm settlement.',
      ]}
      fix="POST collateral-inquiries → GET collateral-reports"
      footer="Kafka and FIX processing remain inside the backend; the frontend uses only authenticated REST endpoints."
    >
      <Panel title="Open a loan request" icon={ShieldCheck}>
        <div className="empty-inline">
          <strong>BB/BA actions are request-scoped.</strong>
          <p>Choose an existing request to submit an inquiry or display its collateral reports and current pending state.</p>
        </div>
        <Link className="action-link" to="/requests">
          View loan requests <ArrowRight size={15} />
        </Link>
      </Panel>
    </WorkPage>
  )
}
