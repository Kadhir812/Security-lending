import { useEffect, useState } from 'react'
import { Activity, RefreshCw, Search } from 'lucide-react'
import { calculationSchedule, loanRequests, marginCalls, payments, settlements } from '../api/resources'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { ApiState, Button, DataTable, DetailGrid, Field, FooterNote, KpiStrip, PageHeading, Panel } from '../components/UI'
import { titleCase } from '../utils/format'

const utcDay = (offset = 0) => {
  const date = new Date()
  date.setUTCDate(date.getUTCDate() + offset)
  return date.toISOString().slice(0, 10)
}
const rows = data => Array.isArray(data) ? data : data?.content || []

export default function Calculations() {
  const { token, notify } = useApp()
  const [params, setParams] = useState({ transaction: '', from: utcDay(-1), to: utcDay(-1) })
  const [input, setInput] = useState(params.transaction)
  const [busy, setBusy] = useState('')
  const [outcome, setOutcome] = useState(null)
  const [intervalMinutes, setIntervalMinutes] = useState('60')
  const [savingSchedule, setSavingSchedule] = useState(false)
  const requests = useRemote(() => loanRequests.list(0, 100), [], { enabled: !!token })
  const schedule = useRemote(() => calculationSchedule.get(), [], { enabled: !!token })
  const contracts = rows(requests.data).filter(x => x.status === 'APPROVED' && x.transactionReference)
  const collateral = useRemote(() => settlements.get(params.transaction), [params.transaction], { enabled: !!params.transaction })
  const fees = useRemote(() => payments.charges(params.transaction), [params.transaction], { enabled: !!params.transaction })
  const rebates = useRemote(() => payments.rebateCharges(params.transaction), [params.transaction], { enabled: !!params.transaction })
  const mtm = useRemote(() => marginCalls.list(params.transaction), [params.transaction], { enabled: !!params.transaction })
  useEffect(() => setInput(params.transaction), [params.transaction])
  useEffect(() => { if (schedule.data?.intervalMinutes) setIntervalMinutes(String(schedule.data.intervalMinutes)) }, [schedule.data])

  async function saveSchedule(event) {
    event.preventDefault()
    setSavingSchedule(true)
    try {
      const result = await calculationSchedule.update(Number(intervalMinutes))
      schedule.reload()
      notify(`Calculation schedule updated to every ${result.intervalMinutes} minutes.`, 'success')
    } catch (error) { notify(error.message, 'danger') }
    finally { setSavingSchedule(false) }
  }

  async function run(event, kind) {
    event.preventDefault()
    setBusy(kind)
    try {
      const result = kind === 'fees'
        ? await payments.accrue(params.transaction, params.from, params.to)
        : await payments.accrueRebates(params.transaction, params.from, params.to)
      setOutcome(result)
      notify(`${kind === 'fees' ? 'Fee' : 'Cash rebate'} calculation returned by the service.`, 'success')
      if (kind === 'fees') fees.reload(); else rebates.reload()
    } catch (error) { notify(error.message, 'danger') }
    finally { setBusy('') }
  }
  async function runMtm() {
    setBusy('mtm')
    try { setOutcome(await marginCalls.mtm(params.transaction)); notify('MTM result returned by the service.', 'success'); mtm.reload() }
    catch (error) { notify(error.message, 'danger') }
    finally { setBusy('') }
  }

  return <div className="sub-page">
    <PageHeading eyebrow="BACKEND CALCULATION WORKSPACE" title="Calculations" subtitle="Review authoritative fee, cash rebate, collateral and mark-to-market outputs; request each calculation on demand." />
    <KpiStrip items={[
      { label: 'Selected contract', value: params.transaction || 'None', detail: 'Server-issued transaction reference' },
      { label: 'Fee / rebate period', value: `${params.from} → ${params.to}`, detail: 'Completed UTC days' },
      { label: 'Fee and rebate schedule', value: schedule.data ? `Every ${schedule.data.intervalMinutes} min` : 'Unavailable', detail: 'Active Custodian service cadence' },
      { label: 'Calculation authority', value: 'Backend', detail: 'No browser-side financial formulas' },
    ]} />
    <Panel title="Contract and date range" subtitle="Select a real approved contract before requesting calculations.">
      <form className="lookup-bar" onSubmit={event => { event.preventDefault(); setParams(current => ({ ...current, transaction: input.trim() })) }}>
        <Field name="transaction" label="Approved loan contract" value={input} onChange={setInput} required options={contracts.map(row => ({ value: row.transactionReference, label: `${row.transactionReference} · ${row.borrowerOrganizationReference} · ${row.securityIsin}` }))} />
        <Button type="submit" icon={Search} disabled={!input.trim()}>Load contract</Button>
      </form>
      <div className="lookup-bar calculation-dates">{['from', 'to'].map(key => <Field key={key} name={key} label={titleCase(key)} type="date" required value={params[key]} onChange={value => setParams(current => ({ ...current, [key]: value }))} />)}</div>
    </Panel>
    <Panel title="Automatic calculation schedule" subtitle="Change the interval used by the Custodian service for scheduled lending-fee and cash-rebate accruals.">
      <form className="lookup-bar" onSubmit={saveSchedule}>
        <Field name="intervalMinutes" label="Run interval (minutes)" type="number" min={5} max={1440} step={1} required value={intervalMinutes} onChange={setIntervalMinutes} hint="Allowed range: 5 minutes to 24 hours. The Custodian service persists this schedule across restarts." />
        <Button type="submit" busy={savingSchedule} disabled={!token || schedule.loading}>Save schedule</Button>
      </form>
      <ApiState token={token} loading={schedule.loading} error={schedule.error} reload={schedule.reload}>
        <DetailGrid data={schedule.data} fields={['intervalMinutes','nextRunAt','lastRunAt','lastPostedCount','persisted']} />
      </ApiState>
    </Panel>
    {params.transaction && <>
      <Panel title="On-demand calculations" subtitle="Each action calls the authenticated Custodian service; calculation rules and inputs remain server-owned.">
        <div className="action-bar">
          <form className="inline-action" onSubmit={event => run(event, 'fees')}><Button type="submit" icon={Activity} busy={busy === 'fees'} disabled={!token || !!busy}>Calculate fees</Button></form>
          <form className="inline-action" onSubmit={event => run(event, 'rebates')}><Button type="submit" icon={Activity} busy={busy === 'rebates'} disabled={!token || !!busy}>Calculate cash rebates</Button></form>
          <Button icon={Activity} busy={busy === 'mtm'} disabled={!token || !!busy} onClick={runMtm}>Run MTM</Button>
          <Button variant="outline" icon={RefreshCw} disabled={!!busy} onClick={() => { collateral.reload(); fees.reload(); rebates.reload(); mtm.reload() }}>Refresh tables</Button>
        </div>
      </Panel>
      {outcome && <Panel title="Latest calculation response"><DetailGrid data={outcome} /></Panel>}
      <Panel title="Accrued fee calculation results"><ApiState token={token} loading={fees.loading} error={fees.error} reload={fees.reload}><DataTable rows={rows(fees.data)} columns={['calculationResultReference','itemKey','amount','currency','status','accrualDate'].map(key => ({ key, label: titleCase(key) }))} emptyLabel="No fee results returned for this contract." /></ApiState></Panel>
      <Panel title="Cash collateral rebate results"><ApiState token={token} loading={rebates.loading} error={rebates.error} reload={rebates.reload}><DataTable rows={rows(rebates.data)} columns={['resultReference','accrualDate','settledCashCollateral','annualRebatePercentage','grossAmount','amount','currency','dayCountBasis','status'].map(key => ({ key, label: titleCase(key) }))} emptyLabel="No rebate results returned. Rebate is based on confirmed settled CASH collateral." /></ApiState></Panel>
      <Panel title="Settled collateral activity"><ApiState token={token} loading={collateral.loading} error={collateral.error} reload={collateral.reload}><DataTable rows={[...(collateral.data?.collateralInstructions || []), ...(collateral.data?.collateralReleaseInstructions || [])]} columns={['instructionReference','instructionType','amount','currency','status','settledAt'].map(key => ({ key, label: titleCase(key) }))} emptyLabel="No collateral settlement activity returned." /></ApiState></Panel>
      <Panel title="Mark-to-market calculations"><ApiState token={token} loading={mtm.loading} error={mtm.error} reload={mtm.reload}><DataTable rows={rows(mtm.data)} columns={['marginCallReference','requiredCollateral','currentCollateral','shortfallAmount','outstandingAmount','status','dueAt'].map(key => ({ key, label: titleCase(key) }))} emptyLabel="No MTM or margin-call results returned." /></ApiState></Panel>
    </>}
    <FooterNote>Fee, rebate, haircut, collateral and MTM results are calculated or validated by backend services. Rebates use confirmed settled CASH collateral and the approved annual rate.</FooterNote>
  </div>
}
