import { Layers3 } from 'lucide-react'
import { DataTable, Panel } from '../../components/UI'
import { LiveGate, Refresh, ResourceBody, WorkPage } from '../../components/Work'
import { useApp } from '../../hooks/useApp'
import { useRemote } from '../../hooks/useRemote'
import { loans } from '../../api/loans'

export function EligibleMslaPage() {
  const { mode } = useApp()
  const query = useRemote(() => loans.eligible(), [], { enabled: mode === 'live' })
  const rows = query.data || []

  return (
    <WorkPage
      title="Eligible MSLA Agreements"
      subtitle="Review the lending agreements that the Custodian currently permits this Borrower to use."
      stats={[
        { label: 'Eligible agreements', value: query.data?.length ?? '—', detail: 'Live Custodian-backed response' },
        { label: 'Access', value: 'Read only', detail: 'Borrower cannot modify' },
        { label: 'Currency', value: 'MSLA base', detail: 'Required for settlement' },
        { label: 'Eligibility', value: 'Scoped', detail: 'By Borrower organization' },
      ]}
      guide={[
        'Only active and eligible agreements appear.',
        'Settlement currency must match baseCurrency.',
        'AllowedAssetTypes constrain eligible security types; effective/expiry dates also apply.',
      ]}
      fix="MSLA eligibility before U1"
    >
      <LiveGate>
        <Panel title="Current eligible agreements" icon={Layers3} actions={<Refresh onClick={query.reload} />}>
          <ResourceBody
            query={query}
            empty={!rows.length}
            emptyText="No eligible MSLAs returned. Contact Custodian Operations to establish agreements."
          >
            <DataTable
              rows={rows}
              keyField="mslaReference"
              columns={[
                { key: 'mslaReference', label: 'MSLA' },
                { key: 'lenderOrganizationReference', label: 'Lender' },
                { key: 'custodianOrganizationReference', label: 'Custodian' },
                { key: 'baseCurrency', label: 'Currency' },
                { key: 'effectiveDate', label: 'Effective' },
                { key: 'expiryDate', label: 'Expiry' },
                { key: 'allowedAssetTypes', label: 'Allowed assets', render: value => (value || []).join(', ') || '—' },
              ]}
            />
          </ResourceBody>
        </Panel>
      </LiveGate>
    </WorkPage>
  )
}
