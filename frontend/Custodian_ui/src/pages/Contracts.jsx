import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowRight, FileText, RefreshCw, Search } from 'lucide-react'
import { loanRequests, settlements, marginCalls, recalls, payments } from '../api/resources'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { ApiState, Button, DataTable, DetailGrid, Field, FooterNote, KpiStrip, Lifecycle, PageHeading, Panel, SideGuide, StatusBadge } from '../components/UI'
import { display, money, number, stamp, titleCase } from '../utils/format'
import { demoContracts } from '../mock/dashboard'

export default function Contracts() {
  const { token, mode } = useApp()
  const [params, setParams] = useSearchParams()
  const transaction = params.get('transaction') || ''
  const [input, setInput] = useState(transaction)
  const requests = useRemote(() => loanRequests.list(0, 100), [], { enabled: mode === 'live' })
  const rows = requests.data?.content || []
  const approved = rows.filter(row => row.status === 'APPROVED' && row.transactionReference)
  const selectedRequest = approved.find(row => row.transactionReference === transaction) || null
  const settlement = useRemote(() => settlements.get(transaction), [transaction], { enabled: mode === 'live' && !!transaction })
  const mtm = useRemote(() => marginCalls.list(transaction), [transaction], { enabled: mode === 'live' && !!transaction })
  const recallQ = useRemote(() => recalls.list(transaction), [transaction], { enabled: mode === 'live' && !!transaction })
  const chargeQ = useRemote(() => payments.charges(transaction), [transaction], { enabled: mode === 'live' && !!transaction })
  const rebateQ = useRemote(() => payments.rebateCharges(transaction), [transaction], { enabled: mode === 'live' && !!transaction })
  const paymentQ = useRemote(() => payments.list(transaction), [transaction], { enabled: mode === 'live' && !!transaction })
  const instructions = settlement.data?.settlementInstructions || []
  const collateral = settlement.data?.collateralInstructions || []
  const releases = settlement.data?.collateralReleaseInstructions || []
  const marginRows = Array.isArray(mtm.data) ? mtm.data : mtm.data?.content || []
  const recallRows = Array.isArray(recallQ.data) ? recallQ.data : recallQ.data?.content || []
  const charges = Array.isArray(chargeQ.data) ? chargeQ.data : chargeQ.data?.content || []
  const paymentRows = Array.isArray(paymentQ.data) ? paymentQ.data : paymentQ.data?.content || []

  useEffect(() => setInput(transaction), [transaction])
  function inspect(event) {
    event.preventDefault()
    const value = approved.find(row => row.transactionReference === input.trim())?.transactionReference || input.trim()
    setParams(value ? { transaction: value } : {})
  }
  function select(row) {
    setInput(row.transactionReference)
    setParams({ transaction: row.transactionReference })
  }

  return <div className="sub-page">
    <PageHeading title="Loan Contract Workspace" subtitle="Open an approved loan to inspect its lifecycle and all supported contract-scoped operations in one place." action={<Button icon={RefreshCw} variant="outline" onClick={() => { requests.reload(); settlement.reload(); mtm.reload(); recallQ.reload(); chargeQ.reload(); rebateQ.reload(); paymentQ.reload() }}>Refresh contract</Button>} />
    <KpiStrip items={[
      { label: 'Approved loans', value: mode === 'demo' ? demoContracts.length : requests.data?.totalElements ?? '—', detail: mode === 'demo' ? 'Illustrative only' : 'Request service response' },
      { label: 'Selected contract', value: transaction || 'None', detail: 'Server-issued transaction reference' },
      { label: 'Contract status', value: settlement.data?.contractStatus || '—', detail: 'Live settlement projection' },
      { label: 'Outstanding', value: settlement.data ? number(settlement.data.outstandingQuantity) : '—', detail: 'Returned by Custodian service' },
    ]} />
    <div className="work-area"><div className="work-area__main">
      <Panel title="Approved loan table" icon={FileText} subtitle="Select a loan row to open its contract workspace.">
        {mode === 'demo' ? <DataTable rows={demoContracts} columns={[
          { key: 'transactionReference', label: 'Transaction' }, { key: 'borrower', label: 'Borrower' }, { key: 'security', label: 'Security' },
          { key: 'quantity', label: 'Quantity', render: number }, { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> },
        ]} /> : <ApiState token={token} loading={requests.loading} error={requests.error} reload={requests.reload}>
          <DataTable keyField="requestReference" rows={approved} onRow={select} columns={[
            { key: 'requestReference', label: 'Request' }, { key: 'transactionReference', label: 'Contract reference' },
            { key: 'borrowerOrganizationReference', label: 'Borrower' }, { key: 'mslaReference', label: 'MSLA' },
            { key: 'securityIsin', label: 'ISIN' }, { key: 'quantity', label: 'Quantity', render: number },
            { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> },
            { key: 'requestedAt', label: 'Approved request', render: stamp },
          ]} emptyLabel="No approved contract references are available in this request page." />
        </ApiState>}
      </Panel>

      {mode === 'live' && <Panel title="Open by contract reference" subtitle="Use a real transaction reference returned from an approved request.">
        <form className="lookup-bar" onSubmit={inspect}>
          <Field name="transactionReference" label="Loan contract" value={input} onChange={setInput} required options={approved.map(row => ({ value: row.transactionReference, label: `${row.transactionReference} · ${row.borrowerOrganizationReference} · ${row.securityIsin}` }))} />
          <Button type="submit" icon={Search} disabled={!input.trim()}>Open contract</Button>
        </form>
      </Panel>}

      {transaction && mode === 'live' && <>
        <Panel title={`Contract detail · ${transaction}`} subtitle="Contract parties and terms from the approved loan request and settlement projection.">
          <ApiState token={token} loading={settlement.loading} error={settlement.error} reload={settlement.reload}>
            {selectedRequest && <DetailGrid data={selectedRequest} fields={['requestReference','transactionReference','mslaReference','borrowerOrganizationReference','lenderOrganizationReference','securityIsin','quantity','requestedSettlementDate','settlementCurrency','requestedAt']} />}
            <DetailGrid data={settlement.data} fields={['transactionReference','contractStatus','quantity','deliveredQuantity','returnedQuantity','outstandingQuantity','requiredCashCollateral','confirmedCashCollateral']} />
            <Lifecycle current={settlement.data?.contractStatus || 'UNKNOWN'} />
            <div className="action-grid">{[['Settlement','settlement'],['Margin & MTM','margin'],['Recalls','recalls'],['Charges & payments','payments'],['Contract close','close']].map(([label,path]) => <Link className="action-link" key={path} to={`/${path}?transaction=${encodeURIComponent(transaction)}`}>{label}<ArrowRight size={15}/></Link>)}</div>
          </ApiState>
        </Panel>
        <Panel title="Settlement and collateral activity" actions={<Button icon={RefreshCw} variant="ghost" onClick={settlement.reload}>Refresh activity</Button>}>
          <ApiState token={token} loading={settlement.loading} error={settlement.error} reload={settlement.reload}>
            <h3 className="subsection-title">Securities delivery and returns</h3>
            <DataTable rows={instructions} columns={[
              { key: 'instructionReference', label: 'Instruction' }, { key: 'instructionType', label: 'Type' }, { key: 'quantity', label: 'Quantity', render: number },
              { key: 'status', label: 'Status' }, { key: 'confirmedAt', label: 'Confirmed', render: stamp },
            ]} emptyLabel="No securities instructions were returned." />
            <h3 className="subsection-title">Cash collateral instructions</h3>
            <DataTable rows={collateral} columns={[
              { key: 'instructionReference', label: 'Instruction' }, { key: 'amount', label: 'Amount', render: value => money(value, settlement.data?.settlementCurrency) },
              { key: 'currency', label: 'Currency' }, { key: 'status', label: 'Status' }, { key: 'confirmedAt', label: 'Confirmed', render: stamp },
            ]} emptyLabel="No collateral activity was returned." />
            <h3 className="subsection-title">Final collateral releases</h3>
            <DataTable rows={releases} columns={[{ key: 'releaseReference', label: 'Release' }, { key: 'collateralAmount', label: 'Collateral', render: value => money(value, settlement.data?.settlementCurrency) }, { key: 'rebateAmount', label: 'Accumulated rebate', render: value => money(value, settlement.data?.settlementCurrency) }, { key: 'amount', label: 'Total returned', render: value => money(value, settlement.data?.settlementCurrency) }, { key: 'status', label: 'Status' }]} emptyLabel="No final release instructions were returned." />
          </ApiState>
        </Panel>
        <Panel title="MTM and margin calls" actions={<Button icon={RefreshCw} variant="ghost" onClick={mtm.reload}>Refresh calls</Button>}>
          <ApiState token={token} loading={mtm.loading} error={mtm.error} reload={mtm.reload}>
            <DataTable rows={marginRows} columns={[
              { key: 'marginCallReference', label: 'Call' }, { key: 'requiredCollateral', label: 'Required', render: value => money(value, settlement.data?.settlementCurrency) },
              { key: 'currentCollateral', label: 'Current', render: value => money(value, settlement.data?.settlementCurrency) },
              { key: 'outstandingAmount', label: 'Outstanding', render: value => money(value, settlement.data?.settlementCurrency) },
              { key: 'dueAt', label: 'Due', render: stamp }, { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> },
            ]} emptyLabel="No margin calls were returned for this contract." />
          </ApiState>
        </Panel>
        <Panel title="Recall notices" actions={<Button icon={RefreshCw} variant="ghost" onClick={recallQ.reload}>Refresh recalls</Button>}>
          <ApiState token={token} loading={recallQ.loading} error={recallQ.error} reload={recallQ.reload}>
            <DataTable rows={recallRows} columns={[
              { key: 'recallReference', label: 'Recall' }, { key: 'quantity', label: 'Quantity', render: number },
              { key: 'dueAt', label: 'Due', render: stamp }, { key: 'reason', label: 'Reason' }, { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> },
            ]} emptyLabel="No recall notices were returned." />
          </ApiState>
        </Panel>
        <Panel title="Lending fee calculations and payment ledger">
          <ApiState token={token} loading={chargeQ.loading} error={chargeQ.error} reload={chargeQ.reload}>
            <h3 className="subsection-title">Accrued charges</h3>
            <DataTable rows={charges} columns={[
              { key: 'itemKey', label: 'Accrual' }, { key: 'amount', label: 'Calculated', render: value => money(value, settlement.data?.settlementCurrency) },
              { key: 'paidAmount', label: 'Paid', render: value => money(value, settlement.data?.settlementCurrency) },
              { key: 'availableAmount', label: 'Due', render: value => money(value, settlement.data?.settlementCurrency) }, { key: 'status', label: 'Status' },
            ]} emptyLabel="No fee charges were returned." />
          </ApiState>
          <ApiState token={token} loading={paymentQ.loading} error={paymentQ.error} reload={paymentQ.reload}>
            <h3 className="subsection-title">Payment instructions</h3>
            <DataTable rows={paymentRows} columns={[
              { key: 'paymentReference', label: 'Payment' }, { key: 'amount', label: 'Amount', render: value => money(value, settlement.data?.settlementCurrency) },
              { key: 'currency', label: 'CCY' }, { key: 'status', label: 'Status', render: value => <StatusBadge value={value} /> }, { key: 'submittedAt', label: 'Submitted', render: stamp },
            ]} emptyLabel="No fee payments were returned." />
          </ApiState>
        </Panel>
        <Panel title="Accrued daily cash collateral rebate">
          <ApiState token={token} loading={rebateQ.loading} error={rebateQ.error} reload={rebateQ.reload}>
            <DataTable rows={Array.isArray(rebateQ.data) ? rebateQ.data : rebateQ.data?.content || []} columns={['accrualDate','settledCashCollateral','annualRebatePercentage','grossAmount','amount','currency','status'].map(key => ({ key, label: titleCase(key) }))} emptyLabel="No cash rebate charges were returned." />
          </ApiState>
        </Panel>
      </>}
      {mode === 'demo' && <Panel title="Illustrative portfolio"><DataTable keyField="transactionReference" rows={demoContracts} columns={[{key:'transactionReference',label:'Contract'},{key:'borrower',label:'Borrower'},{key:'security',label:'Security'},{key:'quantity',label:'Quantity',render:number},{key:'status',label:'Status',render:value=><StatusBadge value={value}/>}]} /></Panel>}
    </div><div className="work-area__side"><SideGuide title="Contract operations" items={[
      'Select a server-approved loan contract; do not use illustrative demo references for live actions.',
      'Lifecycle status, instructions, margin calls, charges, and payments come from their service endpoints.',
      'Use the linked operation pages for authorized MTM, confirmation, return, payment, recall, and closure actions.',
      'Refresh after asynchronous Kafka updates; a submitted instruction is not a completed settlement.',
    ]} fix="Approval → collateral → delivery → on loan → returns → final release → close" note="No general contract-list endpoint is exposed; the live portfolio uses approved loan requests and transaction-scoped resources." /></div></div>
    <FooterNote>Service-returned statuses and amounts remain authoritative. The Custodian portal does not calculate collateral, fees, or market exposure.</FooterNote>
  </div>
}
