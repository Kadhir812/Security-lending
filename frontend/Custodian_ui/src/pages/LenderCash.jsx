import { useEffect, useState } from 'react'
import { ArrowDownToLine, ArrowUpFromLine, RefreshCw } from 'lucide-react'
import { lenderCashAccounts, organizations } from '../api/resources'
import { newReference } from '../api/client'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { ApiState, Button, ConfirmDialog, DataTable, DetailGrid, Field, FooterNote, KpiStrip, PageHeading, Panel, SideGuide } from '../components/UI'
import { money, stamp } from '../utils/format'

const currencies = ['INR', 'USD', 'EUR', 'GBP', 'CAD', 'AUD']

export default function LenderCash() {
  const { token, notify } = useApp()
  const [lenderReference, setLenderReference] = useState('')
  const [currency, setCurrency] = useState('INR')
  const [dialog, setDialog] = useState(null)
  const lenderQuery = useRemote(() => organizations.list(0, 100), [])
  const lenders = (lenderQuery.data?.content || []).filter(row => row.organizationType === 'LENDER' && row.status === 'ACTIVE')
  const account = useRemote(() => lenderCashAccounts.get(lenderReference, currency), [lenderReference, currency], { enabled: !!lenderReference })
  const history = useRemote(() => lenderCashAccounts.history(lenderReference, currency), [lenderReference, currency], { enabled: !!lenderReference })

  useEffect(() => {
    if (lenders.length && !lenders.some(row => row.organizationReference === lenderReference)) setLenderReference(lenders[0].organizationReference)
  }, [lenders, lenderReference])

  function openMovement(kind) {
    const deposit = kind === 'deposit'
    setDialog({
      title: deposit ? 'Record external lender cash deposit' : 'Record external lender cash withdrawal',
      message: deposit
        ? 'Record a cash movement that has already been externally confirmed. This form does not contact a bank.'
        : 'Record a cash withdrawal that has already been externally confirmed. The backend will reject withdrawals above available cash.',
      subject: `${lenderReference} · ${currency}`,
      danger: !deposit,
      confirmText: deposit ? 'Record deposit' : 'Record withdrawal',
      fields: [
        { name: 'amount', label: `Amount (${currency})`, type: 'number', min: 0.01, step: 0.01, required: true },
        { name: 'externalReference', label: 'External confirmation reference', required: true, max: 100, defaultValue: newReference(deposit ? 'LDEP' : 'LWDR'), hint: 'Reference from the bank or external cash operator.' },
        { name: 'description', label: 'Description', max: 500, multiline: true },
      ],
      execute: async values => {
        const body = { amount: Number(values.amount), externalReference: values.externalReference.trim(), description: values.description || null }
        const key = newReference(deposit ? 'LDEPKEY' : 'LWDRKEY')
        if (deposit) await lenderCashAccounts.deposit(lenderReference, currency, body, key)
        else await lenderCashAccounts.withdraw(lenderReference, currency, body, key)
        notify(deposit ? 'External cash deposit recorded.' : 'External cash withdrawal recorded.', 'success')
        account.reload(); history.reload()
      },
    })
  }

  return <div className="sub-page">
    <PageHeading title="Lender Cash Account" subtitle="Review lender cash and collateral balances, record externally confirmed movements, and inspect ledger history." action={<Button icon={RefreshCw} variant="outline" onClick={() => { account.reload(); history.reload() }}>Refresh account</Button>} />
    <KpiStrip items={[
      { label: 'Lender', value: lenderReference || 'Select lender', detail: 'Current active mandate scope' },
      { label: 'Cash balance', value: account.data ? money(account.data.cashBalance, currency) : '—', detail: 'Settled plus restricted cash' },
      { label: 'Restricted collateral', value: account.data ? money(account.data.restrictedBalance, currency) : '—', detail: 'Collateral held for active loans' },
      { label: 'Available cash', value: account.data ? money(account.data.availableBalance, currency) : '—', detail: 'Withdrawals cannot exceed this balance' },
    ]} />
    <div className="work-area"><div className="work-area__main">
      <Panel title="Account selection and external movements" subtitle="Custodian operators act only for lenders under their current mandate scope.">
        <div className="lookup-bar">
          <Field name="lenderOrganizationReference" label="Lender" value={lenderReference} onChange={setLenderReference} options={lenders.map(row => ({ value: row.organizationReference, label: `${row.organizationName} · ${row.organizationReference}` }))} />
          <Field name="currency" label="Currency" value={currency} onChange={setCurrency} options={currencies} />
          <Button icon={ArrowDownToLine} variant="outline" disabled={!lenderReference} onClick={() => openMovement('deposit')}>Record deposit</Button>
          <Button icon={ArrowUpFromLine} variant="reject" disabled={!lenderReference || Number(account.data?.availableBalance || 0) <= 0} onClick={() => openMovement('withdraw')}>Record withdrawal</Button>
        </div>
        <ApiState token={token} loading={lenderQuery.loading} error={lenderQuery.error} reload={lenderQuery.reload}>
          {lenders.length === 0 && <div className="empty-inline">No active lenders are available in this Custodian scope.</div>}
        </ApiState>
        {lenderReference && <ApiState token={token} loading={account.loading} error={account.error} reload={account.reload}><DetailGrid data={account.data} fields={['accountReference', 'lenderOrganizationReference', 'currency', 'cashBalance', 'restrictedBalance', 'availableBalance', 'status', 'updatedAt']} /></ApiState>}
      </Panel>
      <Panel title="Cash transaction history" subtitle="Most recent 100 entries; collateral settlement and fee receipts are posted by backend workflows.">
        <ApiState token={token} loading={history.loading} error={history.error} reload={history.reload}>
          <DataTable rows={history.data || []} columns={[
            { key: 'transactionReference', label: 'Transaction' }, { key: 'transactionType', label: 'Type' },
            { key: 'sourceReference', label: 'Source reference' }, { key: 'cashDelta', label: 'Cash change', render: value => money(value, currency) },
            { key: 'restrictedDelta', label: 'Restricted change', render: value => money(value, currency) },
            { key: 'cashBalanceAfter', label: 'Cash after', render: value => money(value, currency) },
            { key: 'createdAt', label: 'Recorded', render: stamp },
          ]} emptyLabel="No lender cash transactions have been recorded for this currency." />
        </ApiState>
      </Panel>
    </div><div className="work-area__side"><SideGuide title="Ledger safeguards" items={[
      'Collateral deposits and releases are posted only from confirmed settlement workflows.',
      'External deposit and withdrawal entries require the operator to supply an external confirmation reference.',
      'Every write uses an Idempotency-Key; a retry with the same key cannot double-post cash.',
      'Restricted collateral cannot be withdrawn; only the remaining available cash can be withdrawn.',
    ]} fix="Confirmed collateral → restricted lender cash · fee payment → available cash" note="The portal records external evidence; it is not connected to a bank." /></div></div>
    <FooterNote>These entries update the local operational ledger. External deposits and withdrawals must already have been confirmed outside the application.</FooterNote>
    {dialog && <ConfirmDialog config={dialog} onClose={() => setDialog(null)} />}
  </div>
}
