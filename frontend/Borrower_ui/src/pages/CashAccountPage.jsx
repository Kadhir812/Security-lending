import { useState } from 'react'
import { Banknote, CircleDollarSign, TrendingDown, TrendingUp } from 'lucide-react'
import { Button, ConfirmDialog, DataTable, Field, Panel } from '../components/UI'
import { FormMessage, LiveGate, Refresh, ResourceBody, WorkPage } from '../components/Work'
import { cash } from '../api/cash'
import { loans } from '../api/loans'
import { newReference } from '../api/client'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { money, stamp } from '../utils/format'

const movement = () => ({ amount: '', sourceReference: newReference('CASH') })
const tradeDraft = () => ({
  tradeReference: newReference('TRD'), transactionReference: '',
  tradeType: 'SELL_SHORT', quantity: '', price: '',
})

export default function CashAccountPage() {
  const { mode, notify, refresh } = useApp()
  const [currency, setCurrency] = useState('INR')
  const [trade, setTrade] = useState(tradeDraft)
  const contractsQ = useRemote(() => loans.activeContracts(), [], { enabled: mode === 'live' })
  const contractOptions = (contractsQ.data || []).map(row => ({
    value: row.transactionReference,
    label: `${row.transactionReference} · ${row.securityIsin} · ${row.contractSnapshot?.outstandingQuantity ?? row.quantity} shares`,
  }))
  const accountQ = useRemote(() => cash.account(currency), [currency], { enabled: mode === 'live' })
  const historyQ = useRemote(() => cash.transactions(currency), [currency], { enabled: mode === 'live' })
  const tradeQ = useRemote(() => cash.trades(trade.transactionReference),
    [trade.transactionReference], { enabled: mode === 'live' && !!trade.transactionReference })
  const [deposit, setDeposit] = useState(movement)
  const [withdrawal, setWithdrawal] = useState(movement)
  const [dialog, setDialog] = useState(null)
  const [error, setError] = useState('')

  function refreshAll() {
    accountQ.reload()
    historyQ.reload()
    refresh()
  }

  function submitMovement(event, kind) {
    event.preventDefault()
    setError('')
    const form = kind === 'DEPOSIT' ? deposit : withdrawal
    if (!Number.isFinite(Number(form.amount)) || Number(form.amount) <= 0) {
      setError('Enter a positive amount.')
      return
    }
    const description = kind === 'DEPOSIT'
      ? 'Record an external deposit that has already reached this account.'
      : 'Withdraw cash from the available balance. Reserved collateral and order funds cannot be withdrawn.'
    setDialog({
      title: kind === 'DEPOSIT' ? 'Record external cash deposit' : 'Withdraw available cash',
      subject: `${currency} ${form.amount} · ${form.sourceReference}`,
      message: description,
      confirmText: kind === 'DEPOSIT' ? 'Record deposit' : 'Confirm withdrawal',
      execute: async () => {
        const body = { amount: Number(form.amount), sourceReference: form.sourceReference }
        if (kind === 'DEPOSIT') await cash.deposit(currency, body, form.sourceReference)
        else await cash.withdraw(currency, body, form.sourceReference)
        notify(`${kind === 'DEPOSIT' ? 'Deposit' : 'Withdrawal'} recorded in the cash ledger.`, 'success')
        if (kind === 'DEPOSIT') setDeposit(movement())
        else setWithdrawal(movement())
        refreshAll()
      },
    })
  }

  function submitTrade(event) {
    event.preventDefault()
    setError('')
    if (!contractOptions.some(option => option.value === trade.transactionReference) || !trade.tradeReference.trim()
        || !Number.isFinite(Number(trade.quantity)) || Number(trade.quantity) <= 0
        || !Number.isFinite(Number(trade.price)) || Number(trade.price) <= 0) {
      setError('Enter an on-loan contract, trade reference, positive quantity and execution price.')
      return
    }
    const label = trade.tradeType === 'SELL_SHORT' ? 'short sale' : 'buy-to-cover'
    setDialog({
      title: `Record confirmed ${label}`,
      subject: `${trade.transactionReference} · ${trade.quantity} shares @ ${currency} ${trade.price}`,
      message: 'Record this only after the trade execution is confirmed. A short sale credits proceeds; a buy-to-cover debits cash and matches the oldest open short fills to calculate realized P&L.',
      confirmText: 'Record execution',
      execute: async () => {
        const result = await cash.trade({
          ...trade, quantity: Number(trade.quantity), price: Number(trade.price),
        })
        notify(`Trade recorded. Realized P&L: ${money(result.realizedProfitLoss, currency)}`, 'success')
        setTrade(tradeDraft())
        refreshAll()
      },
    })
  }

  const balance = accountQ.data || {}
  return <WorkPage
    title="Cash Account & Trading"
    subtitle="Fund the borrower cash account, control spendable balance, and record short-sale and buy-to-cover executions."
    stats={[
      { label: 'Cash balance', value: money(balance.cashBalance, currency), detail: currency },
      { label: 'Reserved', value: money(balance.reservedBalance, currency), detail: 'Collateral and open holds' },
      { label: 'Available', value: money(balance.availableBalance, currency), detail: 'Can be spent or withdrawn' },
      { label: 'Account', value: balance.status || '—', detail: balance.accountReference || 'Created at zero balance' },
    ]}
    guide={[
      'New accounts start at zero. Deposit and withdrawal record IDs are generated by the portal.',
      'Collateral and submitted fee payments reserve cash until the Custodian confirms or fails settlement.',
      'A short sale credits proceeds only for confirmed borrowed shares. Buy-to-cover uses FIFO short lots and records realized P&L.',
      'Withdrawals and buys cannot use reserved cash or reduce the account below zero.',
    ]}
    fix="Cash deposit → reserve collateral → settlement confirmation → short sale → buy to cover → return → collateral release"
    footer="Portal references are generated for you. The portal is not connected to a bank or broker to verify external cash movements or trades."
  >
    <LiveGate>
      <Panel title="Account currency" icon={CircleDollarSign}>
        <div className="form-grid">
          <Field name="currency" label="Currency" options={['INR', 'USD', 'EUR', 'GBP']} value={currency} onChange={setCurrency} />
        </div>
      </Panel>
      <Panel title="Cash deposits" icon={TrendingUp} subtitle="Record externally received cash; the portal creates its tracking reference automatically.">
        <form className="resource-form" onSubmit={event => submitMovement(event, 'DEPOSIT')}>
          <div className="form-grid">
            <Field name="amount" label={`Deposit amount (${currency})`} type="number" min="0.01" step="0.01" required value={deposit.amount} onChange={amount => setDeposit(value => ({ ...value, amount }))} />
            <Field name="sourceReference" label="Deposit tracking reference" value={deposit.sourceReference} readOnly hint="Generated automatically for this ledger entry." />
          </div>
          <FormMessage error={error} />
          <div className="form-actions"><Button type="submit" icon={Banknote}>Review deposit</Button></div>
        </form>
      </Panel>
      <Panel title="Withdrawals" icon={TrendingDown} subtitle="Only the available balance can be withdrawn; reserved collateral remains unavailable.">
        <form className="resource-form" onSubmit={event => submitMovement(event, 'WITHDRAWAL')}>
          <div className="form-grid">
            <Field name="amount" label={`Withdrawal amount (${currency})`} type="number" min="0.01" step="0.01" required value={withdrawal.amount} onChange={amount => setWithdrawal(value => ({ ...value, amount }))} />
            <Field name="sourceReference" label="Withdrawal tracking reference" value={withdrawal.sourceReference} readOnly hint="Generated automatically for this ledger entry." />
          </div>
          <FormMessage error={error} />
          <div className="form-actions"><Button type="submit" icon={TrendingDown}>Review withdrawal</Button></div>
        </form>
      </Panel>
      <Panel title="Short sale and buy-to-cover" icon={CircleDollarSign} subtitle="Enter executions against an ON_LOAN contract. The server computes proceeds, cash debit and FIFO realized P&L.">
        <form className="resource-form" onSubmit={submitTrade}>
          <div className="form-grid">
            <Field name="transactionReference" label="Active loan contract" required value={trade.transactionReference} onChange={transactionReference => setTrade(value => ({ ...value, transactionReference }))} options={contractOptions} />
            <Field name="tradeType" label="Execution type" options={[{ value: 'SELL_SHORT', label: 'Sell short' }, { value: 'BUY_TO_COVER', label: 'Buy to cover' }]} value={trade.tradeType} onChange={tradeType => setTrade(value => ({ ...value, tradeType }))} />
            <Field name="quantity" label="Executed shares" type="number" min="1" step="1" required value={trade.quantity} onChange={quantity => setTrade(value => ({ ...value, quantity }))} />
            <Field name="price" label={`Execution price (${currency})`} type="number" min="0.000001" step="0.000001" required value={trade.price} onChange={price => setTrade(value => ({ ...value, price }))} />
            <Field name="tradeReference" label="Trade tracking reference" value={trade.tradeReference} readOnly hint="Generated automatically; no broker reference entry is needed." />
          </div>
          <FormMessage error={error} />
          <div className="form-actions"><Button type="submit" icon={CircleDollarSign}>Review execution</Button></div>
        </form>
      </Panel>
      <Panel title="Recorded trade activity" icon={CircleDollarSign} actions={<Refresh onClick={tradeQ.reload} />}>
        <ResourceBody query={tradeQ} empty={!tradeQ.data?.length} emptyText="Enter a contract reference above to view its trade executions.">
          <DataTable rows={tradeQ.data || []} columns={[
            { key: 'tradeTime', label: 'Execution time', render: stamp },
            { key: 'tradeReference', label: 'Broker reference' },
            { key: 'tradeType', label: 'Execution' },
            { key: 'quantity', label: 'Shares' },
            { key: 'price', label: 'Price', render: value => money(value, currency) },
            { key: 'notional', label: 'Cash value', render: value => money(value, currency) },
            { key: 'realizedProfitLoss', label: 'Realized P&L', render: value => money(value, currency) },
          ]} />
        </ResourceBody>
      </Panel>
      <Panel title="Available cash" icon={CircleDollarSign} actions={<Refresh onClick={refreshAll} />}>
        <ResourceBody query={accountQ}>
          <DataTable rows={[balance]} columns={[
            { key: 'accountReference', label: 'Account' }, { key: 'currency', label: 'Currency' },
            { key: 'cashBalance', label: 'Cash', render: (value, row) => money(value, row.currency) },
            { key: 'reservedBalance', label: 'Reserved', render: (value, row) => money(value, row.currency) },
            { key: 'availableBalance', label: 'Available', render: (value, row) => money(value, row.currency) },
            { key: 'status', label: 'Status' },
          ]} />
        </ResourceBody>
      </Panel>
      <Panel title="Cash transaction history" icon={Banknote} actions={<Refresh onClick={historyQ.reload} />}>
        <ResourceBody query={historyQ} empty={!historyQ.data?.length} emptyText="No cash movements have been recorded.">
          <DataTable rows={historyQ.data || []} columns={[
            { key: 'createdAt', label: 'Time', render: stamp },
            { key: 'transactionReference', label: 'Transaction' },
            { key: 'transactionType', label: 'Type' },
            { key: 'sourceReference', label: 'Source' },
            { key: 'cashDelta', label: 'Cash change', render: (value, row) => money(value, currency) },
            { key: 'reservedDelta', label: 'Reserved change', render: (value, row) => money(value, currency) },
            { key: 'availableBalanceAfter', label: 'Available after', render: (value, row) => money(value, currency) },
          ]} />
        </ResourceBody>
      </Panel>
    </LiveGate>
    {dialog && <ConfirmDialog config={dialog} onClose={() => setDialog(null)} />}
  </WorkPage>
}
