import { useEffect, useState } from 'react'
import { RefreshCw, Search } from 'lucide-react'
import { securityReferences } from '../api/resources'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { ApiState, Button, DataTable, DetailGrid, Field, FooterNote, KpiStrip, PageHeading, Panel, SideGuide } from '../components/UI'
import { stamp } from '../utils/format'

const windowStart = () => new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString()
const now = () => new Date().toISOString()

export default function Securities() {
  const { token } = useApp()
  const [search, setSearch] = useState('')
  const [selectedIsin, setSelectedIsin] = useState('')
  const [from, setFrom] = useState(windowStart)
  const [to, setTo] = useState(now)
  const universe = useRemote(() => securityReferences.list(), [])
  const securities = Array.isArray(universe.data) ? universe.data : []
  const selected = securities.find(security => security.isin === selectedIsin) || null
  const filtered = securities.filter(security => `${security.symbol} ${security.isin} ${security.securityName}`.toLowerCase().includes(search.trim().toLowerCase()))
  const latest = useRemote(() => securityReferences.latest(selectedIsin), [selectedIsin], { enabled: !!selectedIsin })
  const history = useRemote(() => securityReferences.history(selectedIsin, from, to), [selectedIsin, from, to], { enabled: !!selectedIsin && !!from && !!to })

  useEffect(() => {
    if (securities.length && !securities.some(security => security.isin === selectedIsin)) setSelectedIsin(securities[0].isin)
  }, [securities, selectedIsin])

  return <div className="sub-page">
    <PageHeading title="Security Master & Market Prices" subtitle="Browse active reference securities and inspect authoritative latest and recent market quotes." action={<Button icon={RefreshCw} variant="outline" onClick={() => { universe.reload(); latest.reload(); history.reload() }}>Refresh market data</Button>} />
    <KpiStrip items={[
      { label: 'Active securities', value: token ? securities.length : '—', detail: 'Market reference service' },
      { label: 'Selected ISIN', value: selectedIsin || 'None', detail: 'Reference identifier' },
      { label: 'Quote source', value: latest.data?.sourceName || (latest.loading ? 'Loading' : '—'), detail: 'Latest service quote' },
      { label: 'Quote freshness', value: latest.data ? latest.data.stale ? 'STALE' : 'CURRENT' : '—', detail: 'Server-provided flag' },
    ]} />
    <div className="work-area"><div className="work-area__main">
      <Panel title="Active security master" subtitle="Rows and identifiers come from the shared Security Market service.">
        <div className="lookup-bar"><Field name="securitySearch" label="Search symbol, ISIN, or name" value={search} onChange={setSearch} placeholder="Search securities"/><Button variant="outline" icon={Search} onClick={() => setSearch('')}>Clear</Button></div>
        <ApiState token={token} loading={universe.loading} error={universe.error} reload={universe.reload}>
          <DataTable keyField="isin" rows={filtered} onRow={row => setSelectedIsin(row.isin)} emptyLabel="No active securities match the search." columns={[
            { key: 'symbol', label: 'Symbol' }, { key: 'isin', label: 'ISIN' }, { key: 'securityName', label: 'Security' },
            { key: 'securityType', label: 'Type' }, { key: 'currency', label: 'Currency' }, { key: 'exchangeName', label: 'Exchange' },
          ]} />
        </ApiState>
      </Panel>
      {selected && <Panel title={`Market view · ${selected.isin}`} subtitle="Prices are informational market references and are not altered or calculated in the Custodian portal.">
        <DetailGrid data={selected} fields={['symbol', 'isin', 'securityName', 'securityType', 'currency', 'exchangeName', 'status']} />
        <ApiState token={token} loading={latest.loading} error={latest.error} reload={latest.reload}>
          {latest.data && <DetailGrid data={latest.data} fields={['marketPrice', 'currency', 'priceTimestamp', 'sourceName', 'stale']} />}
        </ApiState>
        <h3 className="subsection-title">Price history · last 30 days</h3>
        <ApiState token={token} loading={history.loading} error={history.error} reload={history.reload}>
          <DataTable rows={history.data?.content || []} columns={[
            { key: 'priceTimestamp', label: 'Timestamp', render: stamp }, { key: 'marketPrice', label: 'Price' },
            { key: 'currency', label: 'Currency' }, { key: 'sourceName', label: 'Source' }, { key: 'stale', label: 'Stale' },
          ]} emptyLabel="No price history was returned for this period." />
        </ApiState>
      </Panel>}
    </div><div className="work-area__side"><SideGuide title="Security reference" items={[
      'The shared market service owns security names, ISINs, exchanges, currencies, and quote timestamps.',
      'The portal only displays quotes; loan valuations and collateral calculations remain server-owned.',
      'A stale quote is displayed with the service-provided stale flag and must not be mistaken for a current price.',
    ]} fix="Security Master → inventory / eligibility / valuation" note="Custodian access is authenticated and read-only for this module." /></div></div>
    <FooterNote>Quotes may be stale or unavailable. The Custodian approval and valuation services revalidate their own market data.</FooterNote>
  </div>
}
