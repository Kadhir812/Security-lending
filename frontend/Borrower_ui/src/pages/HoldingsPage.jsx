import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Layers3 } from 'lucide-react'
import { DataTable, DetailGrid, Panel, StatusBadge } from '../components/UI'
import { LiveGate, Refresh, ResourceBody, WorkPage } from '../components/Work'
import { loans } from '../api/loans'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { number } from '../utils/format'

export default function HoldingsPage() {
  const { mode } = useApp()
  const [selectedIsin, setSelectedIsin] = useState('')
  const query = useRemote(() => loans.holdings(), [], { enabled: mode === 'live' })
  const rows = query.data?.positions || []
  const selected = rows.find(row => row.securityIsin === selectedIsin) || null
  useEffect(() => {
    if (rows.length && !rows.some(row => row.securityIsin === selectedIsin)) setSelectedIsin(rows[0].securityIsin)
  }, [rows, selectedIsin])

  return <WorkPage title="Borrower Holdings" subtitle="Inspect owned shares, outstanding borrowed securities, and open short quantities from the Borrower service." stats={[
    { label: 'Security positions', value: query.data?.securityCount ?? '—', detail: 'Backend holdings projection' },
    { label: 'Open borrowed contracts', value: query.data?.borrowedContractCount ?? '—', detail: 'Contracts with outstanding shares' },
    { label: 'Source', value: 'LIVE LEDGER', detail: 'Trades and contract projections' },
    { label: 'Write access', value: 'READ ONLY', detail: 'Trade and return workflows own changes' },
  ]} guide={[
    'Owned shares reflect recorded BUY and SELL executions.',
    'Borrowed outstanding quantity comes from contract projections and decreases when returns are confirmed.',
    'Open short quantity comes from SELL_SHORT less BUY_TO_COVER for each contract.',
    'Return shares from the Returns workflow; do not edit holdings directly.',
  ]} fix="Borrowed holdings → trade / cover → return request" footer="Holdings are read-only projections. Trades, settled returns, and Custodian settlement events update the source records.">
    <LiveGate>
      <Panel title="Security positions" icon={Layers3} actions={<Refresh onClick={query.reload} />}>
        <ResourceBody query={query} empty={!rows.length} emptyText="No owned or borrowed securities were returned for this account.">
          <DataTable rows={rows} keyField="securityIsin" onRow={row => setSelectedIsin(row.securityIsin)} columns={[
            { key: 'symbol', label: 'Symbol' }, { key: 'securityIsin', label: 'ISIN' }, { key: 'securityName', label: 'Security' },
            { key: 'currency', label: 'CCY' }, { key: 'ownedQuantity', label: 'Owned', render: number },
            { key: 'borrowedOutstandingQuantity', label: 'Borrowed outstanding', render: number },
            { key: 'openShortQuantity', label: 'Open short', render: number },
            { key: 'borrowedNotShortedQuantity', label: 'Borrowed not shorted', render: number },
          ]} />
        </ResourceBody>
      </Panel>
      {selected && <Panel title={`Position detail · ${selected.securityIsin}`} actions={<div className="table-actions"><Link className="btn btn--outline" to={`/trade?isin=${encodeURIComponent(selected.securityIsin)}`}>Open trading <ArrowRight size={14}/></Link><Link className="btn btn--primary" to="/returns">Return shares <ArrowRight size={14}/></Link></div>}>
        <DetailGrid data={selected} fields={['symbol','securityIsin','securityName','securityType','currency','ownedQuantity','borrowedOutstandingQuantity','openShortQuantity','borrowedNotShortedQuantity']} />
        {selected.borrowedOutstandingQuantity > 0 && <div className="info-banner"><StatusBadge value="ON LOAN"/> Confirmed borrowed shares remain outstanding until the return settlement is reflected in the contract projection.</div>}
      </Panel>}
    </LiveGate>
  </WorkPage>
}
