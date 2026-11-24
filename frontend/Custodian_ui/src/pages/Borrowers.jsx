import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, RefreshCw, Search } from 'lucide-react'
import { organizations } from '../api/resources'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { ApiState, Button, DataTable, DetailGrid, Field, FooterNote, KpiStrip, PageHeading, Pagination, Panel, SideGuide, StatusBadge } from '../components/UI'
import { stamp } from '../utils/format'

const columns = [
  { key: 'organizationReference', label: 'Borrower reference' },
  { key: 'organizationName', label: 'Organization' },
  { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> },
  { key: 'createdAt', label: 'Added', render: stamp },
]

export default function Borrowers() {
  const { token } = useApp()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('ALL')
  const [selectedReference, setSelectedReference] = useState('')
  const { data, loading, error, reload } = useRemote(
    () => organizations.borrowers(page, 20),
    [page],
  )
  const relationship = useRemote(
    () => organizations.activeMslaCount(selectedReference),
    [selectedReference],
    { enabled: !!selectedReference },
  )
  const rows = data?.content || []
  const selected = rows.find(row => row.organizationReference === selectedReference) || null
  const filteredRows = useMemo(() => {
    const search = query.trim().toLowerCase()
    return rows.filter(row => {
      const matchesSearch = !search ||
        `${row.organizationName} ${row.organizationReference}`.toLowerCase().includes(search)
      const matchesStatus = status === 'ALL' || row.status === status
      return matchesSearch && matchesStatus
    })
  }, [rows, query, status])

  useEffect(() => {
    if (rows.length && !rows.some(row => row.organizationReference === selectedReference)) {
      setSelectedReference(rows[0].organizationReference)
    }
  }, [rows, selectedReference])

  const activeCount = rows.filter(row => row.status === 'ACTIVE').length
  const inactiveCount = rows.length - activeCount

  return (
    <div className="sub-page">
      <PageHeading
        eyebrow="RELATIONSHIPS"
        title="Borrower Directory"
        subtitle="Review Borrower organizations visible to this Custodian and start an agreement setup from a selected Borrower."
        action={<Button variant="outline" icon={RefreshCw} onClick={reload}>Refresh borrowers</Button>}
      />
      <KpiStrip items={[
        { label: 'Borrowers on page', value: token ? rows.length : '—', detail: 'Visible to this Custodian' },
        { label: 'Active', value: token ? activeCount : '—', detail: 'Eligible for new agreements' },
        { label: 'Inactive', value: token ? inactiveCount : '—', detail: 'Cannot start new agreements' },
        { label: 'Total registered', value: token ? data?.totalElements ?? '—' : '—', detail: 'Backend-paginated directory' },
      ]} />

      <div className="work-area">
        <div className="work-area__main">
          <Panel title="Registered Borrowers" subtitle="Search and filter the current page of scoped Borrower organizations.">
            <div className="lookup-bar">
              <Field
                name="borrowerSearch"
                label="Search name or reference"
                value={query}
                onChange={setQuery}
                placeholder="Organization or BOR- reference"
              />
              <Field
                name="borrowerStatus"
                label="Status"
                value={status}
                onChange={setStatus}
                options={[{ value: 'ALL', label: 'All statuses' }, 'ACTIVE', 'INACTIVE']}
              />
              <Button variant="outline" icon={Search} onClick={() => { setQuery(''); setStatus('ALL') }}>
                Clear filters
              </Button>
            </div>
            <ApiState token={token} loading={loading} error={error} reload={reload}>
              <DataTable
                keyField="organizationReference"
                rows={filteredRows}
                columns={columns}
                emptyLabel={rows.length ? 'No Borrowers match these filters.' : 'No Borrower organizations are registered in this scope.'}
                onRow={row => setSelectedReference(row.organizationReference)}
              />
              <Pagination
                page={data?.page ?? page}
                size={data?.size ?? 20}
                totalPages={data?.totalPages ?? 0}
                totalElements={data?.totalElements ?? 0}
                onPage={nextPage => setPage(nextPage)}
              />
            </ApiState>
          </Panel>

          {selected && (
            <Panel
              title="Selected Borrower"
          subtitle="Organization and current relationship details returned by the Custodian service."
              actions={<Button
                icon={ArrowRight}
                disabled={selected.status !== 'ACTIVE'}
                onClick={() => navigate(`/mslas?borrowerOrganizationReference=${encodeURIComponent(selected.organizationReference)}`)}
              >Create MSLA</Button>}
            >
              <div className="info-banner">
                Active MSLAs with this Custodian: {relationship.loading ? 'Loading…' : relationship.error ? 'Unavailable' : relationship.data ?? 0}
                {!relationship.loading && !relationship.error && (
                  <span> · {relationship.data > 0 ? 'Active relationship' : 'No active relationship'}</span>
                )}
              </div>
              {relationship.error && <div className="info-banner">Could not load MSLA relationship status. Refresh the page and try again.</div>}
              <DetailGrid
                data={selected}
                fields={['organizationReference', 'organizationName', 'organizationType', 'status', 'version', 'createdAt', 'updatedAt']}
              />
              {selected.status !== 'ACTIVE' && (
                <div className="info-banner">This Borrower is inactive. Activate the organization before creating a new MSLA.</div>
              )}
            </Panel>
          )}
        </div>

        <div className="work-area__side">
          <SideGuide
            title="Borrower relationships"
            items={[
              'The list contains Borrower organizations registered by this Custodian or already visible through this Custodian’s agreements.',
              'An active Borrower can be selected as part of the combined MSLA and Lending Setup form.',
              'Borrower identity linking and organization maintenance remain available in Organizations.',
            ]}
            fix="MSLA setup → locate → loan request"
            note="The directory does not invent agreement status or expose Borrowers outside the authenticated Custodian scope."
          />
        </div>
      </div>
      <FooterNote>Borrower identity, status, and Custodian visibility are returned by the backend. A successful directory read does not imply that an active MSLA exists.</FooterNote>
    </div>
  )
}
