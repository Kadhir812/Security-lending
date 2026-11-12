import { Link, useParams } from 'react-router-dom'
import { ArrowRight, Search } from 'lucide-react'
import { Panel } from '../components/UI'
import { WorkPage } from '../components/Work'

function LocateUnavailable({ title, reference }) {
  return (
    <WorkPage
      title={title}
      subtitle="The current Borrower service does not expose locate endpoints."
      stats={[
        { label: 'Locate API', value: 'Unavailable', detail: 'No /api/locates controller' },
        { label: 'Loan submission', value: 'Available', detail: 'Locate reference is optional' },
        { label: 'Selector', value: 'Disabled', detail: 'No locate call is made' },
        { label: 'Service', value: 'Borrower', detail: 'Port 8082' },
      ]}
      guide={[
        'The Borrower service accepts loan requests without a locateReference.',
        'This page does not fabricate locate records or contact the Custodian directly.',
        'Submit a loan request from the Requests workspace.',
      ]}
      footer="Locate selection is unavailable until the Borrower backend exposes a supported locate API. Loan submission remains available without a locate reference."
    >
      <Panel title="Locate workflow unavailable" icon={Search}>
        <div className="empty-inline" role="status">
          <strong>{reference ? `Locate ${reference} cannot be loaded.` : 'No locate endpoint is available.'}</strong>
          <p>
            This service has no GET or POST /api/locates route. The optional locate field is
            omitted from loan submissions, so this does not block borrowing.
          </p>
        </div>
        <Link className="action-link" to="/requests/new">
          Submit a borrow request without a locate <ArrowRight size={15} />
        </Link>
      </Panel>
    </WorkPage>
  )
}

export function LocatesPage() {
  return <LocateUnavailable title="Locate API Status" />
}

export function NewLocatePage() {
  return <LocateUnavailable title="Request Locate" />
}

export function LocateDetailPage() {
  const { reference } = useParams()
  return <LocateUnavailable title="Locate Details" reference={reference} />
}
