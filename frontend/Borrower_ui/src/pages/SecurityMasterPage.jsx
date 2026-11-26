import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { DataTable, DetailGrid, Panel, StatusBadge } from '../components/UI'
import { LiveGate, Refresh, ResourceBody, WorkPage } from '../components/Work'
import { securities } from '../api/securities'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { number, stamp } from '../utils/format'

const windowStart = () => new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString()
const now = () => new Date().toISOString()

export default function SecurityMasterPage() {
  const { mode } = useApp()
  const [selectedIsin, setSelectedIsin] = useState('')
  const universe = useRemote(() => securities.active(), [], { enabled: mode === 'live' })
  const rows = universe.data || []
  const selected = rows.find(security => security.isin === selectedIsin) || null
  const latest = useRemote(() => securities.latest(selectedIsin), [selectedIsin], { enabled: mode === 'live' && !!selectedIsin })
  const history = useRemote(() => securities.history(selectedIsin, windowStart(), now()), [selectedIsin], { enabled: mode === 'live' && !!selectedIsin })

  useEffect(() => {
    if (rows.length && !rows.some(security => security.isin === selectedIsin)) setSelectedIsin(rows[0].isin)
  }, [rows, selectedIsin])

  return <WorkPage title="Security Master & Prices" subtitle="Browse active securities and current/recent market prices from the shared reference service." stats={[
    { label: 'Active securities', value: mode === 'live' && universe.data ? rows.length : '—', detail: 'Market reference service' },
    { label: 'Selected ISIN', value: selectedIsin || 'None', detail: 'Server-provided identity' },
    { label: 'Latest quote', value: latest.data ? number(latest.data.marketPrice) : '—', detail: latest.data?.currency || 'No quote loaded' },
    { label: 'Freshness', value: latest.data ? latest.data.stale ? 'STALE' : 'CURRENT' : '—', detail: 'Market service flag' },
  ]} guide={[
    'Securities and prices are read-only data from the shared market service.',
    'A quote marked stale may not be used as a current trade or valuation input.',
    'The Borrower service revalidates price and eligibility when processing trades and requests.',
  ]} fix="Market reference → locate → loan request" footer="Market values are informational. Borrower and Custodian services remain responsible for their authoritative calculations.">
    <LiveGate>
      <Panel title="Active securities" actions={<Refresh onClick={universe.reload} />}>
        <ResourceBody query={universe} empty={!rows.length} emptyText="No active securities were returned by the market service.">
          <DataTable rows={rows} keyField="isin" onRow={row => setSelectedIsin(row.isin)} columns={[
            { key: 'symbol', label: 'Symbol' }, { key: 'isin', label: 'ISIN' }, { key: 'securityName', label: 'Security' },
            { key: 'securityType', label: 'Type' }, { key: 'currency', label: 'Currency' }, { key: 'exchangeName', label: 'Exchange' },
          ]} />
        </ResourceBody>
      </Panel>
      {selected && <Panel title={`Quote and history · ${selected.isin}`} actions={<Link className="btn btn--outline" to={`/locates/new?isin=${encodeURIComponent(selected.isin)}`}>Start locate <ArrowRight size={14}/></Link>}>
        <DetailGrid data={selected} fields={['symbol','isin','securityName','securityType','currency','exchangeName','status']} />
        <ResourceBody query={latest}><DetailGrid data={latest.data} fields={['priceReference','marketPrice','currency','priceTimestamp','sourceName','stale']} /></ResourceBody>
        <h3 className="subsection-title">Recent market prices</h3>
        <ResourceBody query={history} empty={!(history.data?.content || []).length} emptyText="No market prices were returned in the last 30 days.">
          <DataTable rows={history.data?.content || []} columns={[
            { key: 'priceTimestamp', label: 'Timestamp', render: stamp }, { key: 'marketPrice', label: 'Price', render: number },
            { key: 'currency', label: 'Currency' }, { key: 'sourceName', label: 'Source' }, { key: 'stale', label: 'Stale', render: value => <StatusBadge value={value ? 'STALE' : 'CURRENT'} /> },
          ]} />
        </ResourceBody>
      </Panel>}
    </LiveGate>
  </WorkPage>
}
