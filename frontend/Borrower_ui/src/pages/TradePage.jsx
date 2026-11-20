import { useEffect, useMemo, useState } from 'react'
import { Activity, ArrowDown, ArrowUp, BarChart3, Clock3, RefreshCw, Search, Star, TrendingUp } from 'lucide-react'
import { Button, ConfirmDialog, DataTable, Field, Panel, StatusBadge } from '../components/UI'
import { FormMessage, LiveGate, ResourceBody, WorkPage } from '../components/Work'
import { cash } from '../api/cash'
import { loans } from '../api/loans'
import { newReference } from '../api/client'
import { securities } from '../api/securities'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { money, number, stamp } from '../utils/format'
import './TradePage.css'

const sixMonthsAgo = () => new Date(Date.now() - 183 * 24 * 60 * 60 * 1000).toISOString()
const now = () => new Date().toISOString()
const modes = [
  { value: 'BUY', label: 'Buy' }, { value: 'SELL', label: 'Sell' },
  { value: 'SELL_SHORT', label: 'Short Sell' }, { value: 'BUY_TO_COVER', label: 'Cover' },
]
const currencyCode = code => code || 'INR'

function PriceChart({ prices, currency }) {
  if (!prices.length) return <div className="trade-chart-empty">No six-month price history was returned for this security.</div>
  const values = prices.map(row => Number(row.marketPrice))
  const low = Math.min(...values), high = Math.max(...values), span = high - low || 1
  const points = values.map((value, index) => `${(index / Math.max(values.length - 1, 1)) * 900},${270 - ((value - low) / span) * 230}`).join(' ')
  const area = `0,280 ${points} 900,280`
  return <div className="trade-chart-wrap">
    <svg className="trade-chart" viewBox="0 0 900 300" role="img" aria-label={`Six month ${currency} price history`} preserveAspectRatio="none">
      {[0, 1, 2, 3].map(index => <line key={index} x1="0" x2="900" y1={40 + index * 75} y2={40 + index * 75} />)}
      <polygon className="trade-chart-area" points={area} />
      <polyline className="trade-chart-line" points={points} />
    </svg>
    <div className="trade-chart-axis"><span>{stamp(prices[0].priceTimestamp)}</span><span>{stamp(prices[Math.floor(prices.length / 2)].priceTimestamp)}</span><span>{stamp(prices.at(-1).priceTimestamp)}</span></div>
    <div className="trade-chart-range"><span>Low {money(low, currency)}</span><span>High {money(high, currency)}</span></div>
  </div>
}

function positionsFromActivity(activity = []) {
  const grouped = new Map()
  activity.filter(row => ['BUY', 'SELL'].includes(row.tradeType) && row.securityIsin).forEach(row => {
    const position = grouped.get(row.securityIsin) || { securityIsin: row.securityIsin, quantity: 0, buyQuantity: 0, buyNotional: 0, realized: 0 }
    const quantity = Number(row.quantity || 0), price = Number(row.price || 0)
    if (row.tradeType === 'BUY') { position.quantity += quantity; position.buyQuantity += quantity; position.buyNotional += quantity * price }
    else position.quantity -= quantity
    position.realized += Number(row.realizedProfitLoss || 0)
    grouped.set(row.securityIsin, position)
  })
  return [...grouped.values()].filter(row => row.quantity > 0).map(row => ({
    ...row, averagePrice: row.buyQuantity ? row.buyNotional / row.buyQuantity : 0,
  }))
}

export default function TradePage() {
  const { mode, notify, refresh } = useApp()
  const [query, setQuery] = useState('')
  const [selectedIsin, setSelectedIsin] = useState('')
  const [tradeType, setTradeType] = useState('BUY')
  const [quantity, setQuantity] = useState('')
  const [contractReference, setContractReference] = useState('')
  const [dialog, setDialog] = useState(null)
  const [error, setError] = useState('')
  const securitiesQ = useRemote(() => securities.active(), [], { enabled: mode === 'live' })
  const stockRows = securitiesQ.data || []
  useEffect(() => {
    if (!selectedIsin && stockRows.length) setSelectedIsin(stockRows[0].isin)
    else if (selectedIsin && !stockRows.some(row => row.isin === selectedIsin) && stockRows.length) setSelectedIsin(stockRows[0].isin)
  }, [stockRows, selectedIsin])
  const watchQ = useRemote(async () => Promise.all(stockRows.map(async security => {
    try { return { ...security, quote: await securities.latest(security.isin) } }
    catch { return { ...security, quote: null } }
  })), [stockRows], { enabled: mode === 'live' && stockRows.length > 0 })
  const selectedSecurity = stockRows.find(row => row.isin === selectedIsin)
  const quoteQ = useRemote(() => securities.latest(selectedIsin), [selectedIsin], { enabled: mode === 'live' && !!selectedIsin })
  const historyQ = useRemote(() => securities.history(selectedIsin, sixMonthsAgo(), now()), [selectedIsin], { enabled: mode === 'live' && !!selectedIsin })
  const activityQ = useRemote(() => cash.tradeActivity(), [], { enabled: mode === 'live' })
  const contractsQ = useRemote(() => loans.activeContracts(), [], { enabled: mode === 'live' })
  const currency = currencyCode(selectedSecurity?.currency || quoteQ.data?.currency)
  const accountQ = useRemote(() => cash.account(currency), [currency], { enabled: mode === 'live' })
  const historyPrices = [...(historyQ.data?.content || [])].sort((a, b) => new Date(a.priceTimestamp) - new Date(b.priceTimestamp))
  const filteredWatch = (watchQ.data || []).filter(row => `${row.symbol} ${row.securityName} ${row.isin}`.toLowerCase().includes(query.toLowerCase()))
  const quote = quoteQ.data
  const shares = Number(quantity || 0)
  const orderValue = quote?.marketPrice ? Number(quote.marketPrice) * shares : 0
  const balance = accountQ.data || {}
  const isCashBuy = tradeType === 'BUY'
  const selectedContracts = (contractsQ.data || []).filter(row => row.securityIsin === selectedIsin)
  const activity = activityQ.data || []
  const positions = useMemo(() => positionsFromActivity(activity), [activity])
  const openPosition = positions.find(row => row.securityIsin === selectedIsin)
  const netRealized = activity.reduce((sum, row) => sum + Number(row.realizedProfitLoss || 0), 0)

  function submitOrder(event) {
    event.preventDefault(); setError('')
    if (!selectedSecurity || !quote || quote.stale) { setError('Select a security with a current market quote.'); return }
    if (!Number.isSafeInteger(shares) || shares < 1) { setError('Enter a positive whole number of shares.'); return }
    const contractTrade = ['SELL_SHORT', 'BUY_TO_COVER'].includes(tradeType)
    const contract = selectedContracts.find(row => row.transactionReference === contractReference)
    if (contractTrade && !contract) { setError('Choose an on-loan contract for this security.'); return }
    if (isCashBuy && orderValue > Number(balance.availableBalance || 0)) {
      setError('Order value exceeds available borrower cash. Add cash or reduce the quantity.'); return
    }
    if (tradeType === 'SELL' && shares > Number(openPosition?.quantity || 0)) {
      setError('Sell quantity exceeds shares currently held in this borrower account.'); return
    }
    const tradeReference = newReference('TRD')
    const body = {
      tradeReference, tradeType, securityIsin: selectedIsin, quantity: shares,
      ...(contractTrade ? { transactionReference: contractReference } : {}),
    }
    setDialog({
      title: `Review ${modes.find(item => item.value === tradeType)?.label} order`,
      subject: `${selectedSecurity.symbol} · ${number(shares)} shares · ${money(orderValue, currency)}`,
      message: `${tradeType === 'BUY' ? 'Available cash will be debited' : tradeType === 'SELL' ? 'Sale proceeds will be credited' : 'The borrowed contract position and cash ledger will be updated'} at the current security-master quote. The server rechecks price, holdings, loan status, and available cash when it records the execution.`,
      confirmText: 'Record trade',
      execute: async () => {
        const result = await cash.trade(body)
        notify(`${selectedSecurity.symbol} ${tradeType.replaceAll('_', ' ').toLowerCase()} recorded at ${money(result.price, currency)}.`, 'success')
        setQuantity(''); setDialog(null); activityQ.reload(); accountQ.reload(); refresh()
        return result
      },
    })
  }

  const orderButton = tradeType === 'BUY' ? 'Buy' : tradeType === 'SELL' ? 'Sell' : tradeType === 'SELL_SHORT' ? 'Short Sell' : 'Buy to Cover'
  const positionsColumns = [
    { key: 'symbol', label: 'Symbol' },
    { key: 'quantity', label: 'Shares', render: number },
    { key: 'averagePrice', label: 'Avg. cost', render: value => money(value, currency) },
    { key: 'lastPrice', label: 'Last price', render: value => money(value, currency) },
    { key: 'unrealized', label: 'Unrealized P&L', render: value => <span className={Number(value) >= 0 ? 'trade-positive' : 'trade-negative'}>{money(value, currency)}</span> },
  ]
  const positionRows = positions.map(position => {
    const security = stockRows.find(row => row.isin === position.securityIsin)
    const last = Number(watchQ.data?.find(row => row.isin === position.securityIsin)?.quote?.marketPrice || 0)
    return { ...position, symbol: security?.symbol || position.securityIsin, lastPrice: last, unrealized: (last - position.averagePrice) * position.quantity }
  })

  return <WorkPage title="Trade" subtitle="Buy and sell securities using available borrower cash, or trade confirmed borrowed shares at security-master prices."
    stats={[
      { label: 'Buying power', value: money(balance.availableBalance, currency), detail: 'Available borrower cash after reservations' },
      { label: 'Open positions', value: positionRows.length, detail: 'Derived from recorded buy and sell executions' },
      { label: 'Realized P&L', value: money(netRealized, currency), detail: 'Backend FIFO match results' },
      { label: 'Market data', value: quote?.stale ? 'STALE' : quote ? 'LIVE RECORD' : 'LOADING', detail: quote?.sourceName || 'Security master' },
    ]}
    guide={[
      'Select a security to load its market-master quote and six-month simulated price history.',
      'Buy debits spendable cash. Sell credits proceeds and cannot exceed your recorded shares.',
      'Short Sell and Cover require an ON_LOAN borrower contract for the selected security.',
      'Every execution is written to borrower_trade_activity and borrower_cash_transaction in the same database transaction.',
    ]}
    fix="Security master quote → cash balance check → borrower trade activity + cash ledger"
    footer="The repository’s demo price history is simulated, not exchange data. Trades and cash balances are recorded by the authenticated Borrower backend."
  >
    <LiveGate>
      <div className="trade-terminal">
        <Panel title="Market watch" icon={TrendingUp} subtitle="Live security master records and latest stored prices">
          <label className="trade-search"><Search size={15}/><input aria-label="Search securities" placeholder="Search symbol, name or ISIN" value={query} onChange={event => setQuery(event.target.value)}/></label>
          <ResourceBody query={securitiesQ} empty={!stockRows.length} emptyText="No active securities are available from the security master.">
            <div className="trade-watch-list">{filteredWatch.map(row => <button key={row.isin} type="button" className={`trade-watch-row ${row.isin === selectedIsin ? 'is-selected' : ''}`} onClick={() => { setSelectedIsin(row.isin); setTradeType('BUY') }}>
              <Star size={14}/><span><strong>{row.symbol}</strong><small>{row.securityName}</small></span><b>{row.quote ? money(row.quote.marketPrice, row.currency) : '—'}</b><small>{row.quote ? stamp(row.quote.priceTimestamp) : 'No quote'}</small>
            </button>)}</div>
          </ResourceBody>
        </Panel>

        <Panel title={selectedSecurity?.symbol || 'Select a security'} icon={Activity} subtitle={`${selectedSecurity?.securityName || 'Market chart'} · ${selectedIsin || 'No ISIN selected'}`} actions={<Button icon={RefreshCw} variant="ghost" onClick={() => { quoteQ.reload(); historyQ.reload(); watchQ.reload() }}>Refresh</Button>}>
          <ResourceBody query={quoteQ} empty={!quote} emptyText="Select an active security with an available market-master quote.">
            {quote && <div className="trade-quote-heading"><div><strong>{money(quote.marketPrice, currency)}</strong><span>{quote.sourceName} · {stamp(quote.priceTimestamp)} {quote.stale && <StatusBadge value="STALE"/>}</span></div><span className="trade-quote-badge"><BarChart3 size={14}/> 6M</span></div>}
            <ResourceBody query={historyQ}><PriceChart prices={historyPrices} currency={currency}/></ResourceBody>
          </ResourceBody>
        </Panel>

        <Panel title="Place trade" icon={ArrowUp} subtitle="Execution uses the latest market-master quote; the backend validates the quote and available balance.">
          <form className="trade-order-form" onSubmit={submitOrder}>
            <div className="trade-mode-tabs" role="tablist" aria-label="Trade action">{modes.map(item => <button key={item.value} type="button" className={tradeType === item.value ? 'active' : ''} onClick={() => setTradeType(item.value)}>{item.label}</button>)}</div>
            <label className="trade-select-label">Security<select value={selectedIsin} onChange={event => setSelectedIsin(event.target.value)}><option value="">Select security</option>{stockRows.map(row => <option key={row.isin} value={row.isin}>{row.symbol} · {row.securityName} · {row.isin}</option>)}</select></label>
            {['SELL_SHORT', 'BUY_TO_COVER'].includes(tradeType) && <Field name="contractReference" label="Borrowed loan contract" required value={contractReference} onChange={setContractReference} options={selectedContracts.map(row => ({ value: row.transactionReference, label: `${row.transactionReference} · ${row.contractSnapshot?.outstandingQuantity ?? row.quantity} shares` }))}/>}
            <div className="trade-order-fields">
              <Field name="price" label={`Market price (${currency})`} value={quote?.marketPrice ?? ''} readOnly hint={quote?.priceTimestamp ? `Updated ${stamp(quote.priceTimestamp)}` : 'Waiting for market quote'}/>
              <Field name="quantity" label="Quantity" type="number" min="1" step="1" required value={quantity} onChange={setQuantity}/>
            </div>
            <div className="trade-order-summary"><span>Order value</span><strong>{money(orderValue, currency)}</strong></div>
            <div className="trade-order-summary"><span>Available buying power</span><strong>{money(balance.availableBalance, currency)}</strong></div>
            {openPosition && <div className="trade-order-summary"><span>Shares available to sell</span><strong>{number(openPosition.quantity)}</strong></div>}
            <FormMessage error={error}/>
            <Button type="submit" className={tradeType === 'SELL' || tradeType === 'BUY_TO_COVER' ? 'trade-submit trade-submit--sell' : 'trade-submit'} disabled={!quote || (quote.stale && quote.sourceName !== 'DEMO_SIMULATED') || !selectedIsin || !shares || (isCashBuy && orderValue > Number(balance.availableBalance || 0))} icon={tradeType === 'SELL' ? ArrowDown : ArrowUp}>{orderButton} {selectedSecurity?.symbol || ''}</Button>
          </form>
        </Panel>
      </div>

      <Panel title="Open positions" icon={BarChart3} subtitle="Borrower holdings derived from saved Buy and Sell executions">
        <DataTable rows={positionRows} columns={positionsColumns} emptyLabel="No stock positions yet. Record a Buy trade to create one."/>
      </Panel>
      <Panel title="Trade activity" icon={Clock3} actions={<Button icon={RefreshCw} variant="ghost" onClick={activityQ.reload}>Refresh</Button>}>
        <ResourceBody query={activityQ} empty={!activity.length} emptyText="No trades have been recorded for this borrower account.">
          <DataTable rows={activity} columns={[
            { key: 'tradeTime', label: 'Time', render: stamp }, { key: 'tradeReference', label: 'Trade ID' },
            { key: 'securityIsin', label: 'ISIN' }, { key: 'tradeType', label: 'Side' },
            { key: 'quantity', label: 'Shares', render: number }, { key: 'price', label: 'Price', render: value => money(value, currency) },
            { key: 'realizedProfitLoss', label: 'Realized P&L', render: value => money(value, currency) },
          ]}/>
        </ResourceBody>
      </Panel>
    </LiveGate>
    {dialog && <ConfirmDialog config={dialog} onClose={() => setDialog(null)}/>}
  </WorkPage>
}
