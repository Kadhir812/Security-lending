import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { Activity, AlertTriangle, ArrowRight, Banknote, ClipboardCheck, CreditCard, RefreshCw, RotateCcw, ShieldCheck } from 'lucide-react'
import { Button, ConfirmDialog, DataTable, DetailGrid, Field, Modal, Panel, StatusBadge } from '../components/UI'
import { ContractPicker, FormMessage, LiveGate, Refresh, ResourceBody, WorkPage } from '../components/Work'
import { contracts } from '../api/contracts'
import { loans } from '../api/loans'
import { newReference } from '../api/client'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { money, number, stamp } from '../utils/format'

/** useScope: select an approved loan request and resolve its supported contract projection. */
function useScope(){const {requestReference:routeReference}=useParams();const {mode}=useApp();const navigate=useNavigate();const {pathname}=useLocation();const [chosenReference,setChosenReference]=useState('');const portfolio=useRemote(()=>loans.list(0,100),[],{enabled:mode==='live'});const requestReference=routeReference||chosenReference;const items=(portfolio.data?.content||[]).filter(request=>request.status==='APPROVED');const contractQuery=useRemote(()=>loans.contract(requestReference),[requestReference],{enabled:mode==='live'&&!!requestReference});const row=contractQuery.data;const tx=row?.transactionReference;return {requestReference,tx,row,items,portfolio,contractQuery,pick:next=>{if(routeReference)navigate(`/${pathname.split('/')[1]}/${encodeURIComponent(next)}`);else setChosenReference(next)}}}
function ScopePanel({scope}){return <Panel title="Approved loan request" icon={ClipboardCheck} subtitle="Contract operations resolve through the request-scoped projection"><ResourceBody query={scope.portfolio}><ContractPicker items={scope.items} value={scope.requestReference} onChange={scope.pick}/></ResourceBody>{scope.requestReference&&<ResourceBody query={scope.contractQuery}>{scope.row&&<DetailGrid data={scope.row} fields={['requestReference','transactionReference','receivedAt']}/>}</ResourceBody>}</Panel>}
const cashForm=()=>({instructionReference:newReference('TOPUP'),amount:''})
/** MarginPage: new margin-call list GET and a positive CASH top-up per call. */
export function MarginPage(){const scope=useScope();const {mode,notify,refresh}=useApp();const [callRef,setCallRef]=useState('');const [selected,setSelected]=useState('');const callDetailQ=useRemote(()=>contracts.marginCall(scope.tx,selected),[scope.tx,selected],{enabled:mode==='live'&&!!scope.tx&&!!selected});const topQ=useRemote(()=>contracts.topUps(scope.tx,selected),[scope.tx,selected],{enabled:mode==='live'&&!!scope.tx&&!!selected});const [form,setForm]=useState(cashForm);const [formOpen,setFormOpen]=useState(false);const [error,setError]=useState('');const [dialog,setDialog]=useState(null);const [busy,setBusy]=useState(false);const currency=scope.row?.contractSnapshot?.settlementCurrency||'INR';const activeCall=callDetailQ.data;const availableCalls=(scope.row?.contractSnapshot?.marginCalls||[]).filter(call=>['OPEN','PARTIALLY_SATISFIED','BREACHED'].includes(call.status))
function loadCall(e){e.preventDefault();setSelected(callRef);setError('')}
function prepare(e){e.preventDefault();setError('');if(!selected||!['OPEN','PARTIALLY_SATISFIED','BREACHED'].includes(activeCall?.status)){setError('Load a margin call in OPEN, PARTIALLY_SATISFIED or BREACHED status.');return}if(!/^[A-Za-z0-9._:-]{1,80}$/.test(form.instructionReference)||!Number.isFinite(Number(form.amount))||!(Number(form.amount)>0)){setError('Instruction reference and positive CASH amount are required.');return}const body={instructionReference:form.instructionReference,amount:Number(form.amount),currency,collateralType:'CASH'};setDialog({title:'Submit margin CASH top-up',subject:`${scope.tx} · ${selected} · ${currency} ${form.amount}`,message:'Send a real cash top-up against this call. The instruction is SUBMITTED until confirmed by the Custodian; a submitted top-up does not instantly satisfy the margin call.',confirmText:'Submit top-up',execute:async()=>{setBusy(true);try{const result=await contracts.topUp(scope.tx,selected,body);notify(`Top-up ${result.instructionReference} submitted`,'success');refresh();callDetailQ.reload();topQ.reload();setForm(cashForm());setFormOpen(false);return result}finally{setBusy(false)}}})}
return <WorkPage title="Margin Calls & CASH Top-Ups" subtitle="Review a known Custodian margin-call reference and submit eligible CASH top-ups." stats={[{label:'Margin call',value:activeCall?.status||'—',detail:selected?'Returned by detail endpoint':'Reference required'},{label:'Shortfall',value:activeCall?money(activeCall.outstandingAmount,currency):'—',detail:'Server-returned value'},{label:'Contract',value:scope.tx||'—',detail:'Borrower-owned'},{label:'Settlement currency',value:currency,detail:'CASH only'}]} guide={['The Borrower API exposes call details by reference but no contract-wide call list.','Use a known marginCallReference from the Custodian workflow to load the detail.','Only active calls accept top-ups; the Custodian confirms any submitted instruction.']} fix="Intraday MTM → Margin call → CASH top-up" footer="Custodian exclusively calculates and raises margin calls; Borrower may inspect a known call and submit CASH. No synthetic amounts are sent."><LiveGate><ScopePanel scope={scope}/>{scope.tx&&<Panel title="Margin call details" icon={AlertTriangle} actions={<div className="action-bar"><Refresh onClick={callDetailQ.reload}/><Refresh onClick={topQ.reload}/></div>} subtitle="GET /api/contracts/{transactionReference}/margin-calls/{marginCallReference}"><form className="resource-form" onSubmit={loadCall}><div className="form-grid"><Field name="marginCallReference" label="Margin call" required value={callRef} options={availableCalls.map(call=>({value:call.marginCallReference,label:`${call.marginCallReference} ? ${call.status} ? ${money(call.outstandingAmount,currency)}`}))} onChange={setCallRef}/></div><div className="form-actions"><Button type="submit" icon={ArrowRight} disabled={!callRef.trim()}>Load margin call</Button></div></form>{!selected&&<div className="info-banner">The Borrower API does not expose a margin-call list. Enter a call reference already provided by the Custodian.</div>}{selected&&<><ResourceBody query={callDetailQ}><DetailGrid data={activeCall} fields={['marginCallReference','status','requiredCollateral','currentCollateral','outstandingAmount','dueAt','valuationResultReference','updatedAt']}/></ResourceBody><ResourceBody query={topQ}><div className="table-actions"><h3 className="subsection-title">Existing instructions for {selected}</h3><Button icon={ArrowRight} disabled={!activeCall||!['OPEN','PARTIALLY_SATISFIED','BREACHED'].includes(activeCall.status)} onClick={()=>setFormOpen(true)}>New CASH top-up</Button></div><DataTable rows={topQ.data||[]} columns={[{key:'instructionReference',label:'Instruction'},{key:'amount',label:'Amount'},{key:'currency',label:'CCY'},{key:'status',label:'Status'},{key:'submittedAt',label:'Submitted',render:stamp}]}/></ResourceBody>{activeCall&&formOpen&&<Modal title="Submit margin CASH top-up" subtitle="Enter a positive amount for the selected active margin call." onClose={()=>setFormOpen(false)}><form className="resource-form form-separated" onSubmit={prepare}><div className="form-grid"><Field name="instructionReference" label="Top-up tracking reference" value={form.instructionReference} readOnly/><Field name="amount" label="CASH amount" required type="number" min="0.01" step="0.01" value={form.amount} onChange={v=>setForm(s=>({...s,amount:v}))}/><Field name="currency" label="Currency" value={currency} readOnly/><Field name="collateralType" label="Collateral type" value="CASH" readOnly/></div><FormMessage error={error}/><div className="form-actions"><Button icon={ArrowRight} type="submit" disabled={busy||!['OPEN','PARTIALLY_SATISFIED','BREACHED'].includes(activeCall.status)}>Review top-up</Button></div></form></Modal>}</>}</Panel>}</LiveGate>{dialog&&<ConfirmDialog config={dialog} onClose={()=>setDialog(null)}/>}</WorkPage>}
/** ReturnsPage: owned return events and nested Custodian recall projection from GET /returns. */
export function ReturnsPage(){const scope=useScope();const {mode,notify,refresh}=useApp();const q=useRemote(()=>contracts.returnProgress(scope.tx),[scope.tx],{enabled:mode==='live'&&!!scope.tx});const data=q.data;const [form,setForm]=useState({instructionReference:newReference('RETURN'),recallReference:'',quantity:''});const [formOpen,setFormOpen]=useState(false);const [error,setError]=useState('');const [dialog,setDialog]=useState(null);const [busy,setBusy]=useState(false);const returns=data?.returns||[];const recalls=data?.recalls||[];const pending=returns.filter(r=>['SUBMITTED','PENDING','PROCESSING'].includes(r.status)).reduce((sum,r)=>sum+Number(r.quantity||0),0);const available=Math.max(0,Number(data?.outstandingQuantity||0)-pending)
function prepare(e){e.preventDefault();setError('');const qty=Number(form.quantity);if(!['ON_LOAN','RECALLED'].includes(data?.contractStatus)){setError('Only ON_LOAN or RECALLED contracts may submit a return.');return}if(!Number.isSafeInteger(qty)||qty<1||qty>available||!/^[A-Za-z0-9._:-]{1,80}$/.test(form.instructionReference)){setError(`Enter a positive whole quantity no greater than ${number(available)} after pending returns.`);return}if(form.recallReference&&!recalls.some(r=>r.recallReference===form.recallReference&&['OPEN','PROCESSING','PARTIALLY_RETURNED','OVERDUE','DEFAULTED'].includes(r.status))){setError('Linked recall must exist and be accepting returns.');return}const body={instructionReference:form.instructionReference,quantity:qty,...(form.recallReference?{recallReference:form.recallReference}:{})};setDialog({title:'Submit securities return',message:'This dispatches a Borrower return instruction. Custodian settlement confirmation is a separate step; the outstanding quantity changes only after authoritative processing.',subject:`${scope.tx} · ${number(qty)} units`,confirmText:'Submit return instruction',execute:async()=>{setBusy(true);try{const result=await contracts.submitReturn(scope.tx,body);notify(`Return instruction ${result.instructionReference} submitted`,'success');refresh();q.reload();setForm({instructionReference:newReference('RETURN'),recallReference:'',quantity:''});setFormOpen(false);return result}finally{setBusy(false)}}})}
return <WorkPage title="Securities Returns & Recalls" subtitle="Review T+1 Custodian recalls and submit accurate whole-unit returns against outstanding borrowed shares." stats={[{label:'Outstanding',value:number(data?.outstandingQuantity),detail:'Live contract snapshot'},{label:'Pending returns',value:number(pending),detail:'Submitted and processing'},{label:'Available to return',value:number(scope.tx?available:null),detail:'Excludes pending instructions'},{label:'Recall notices',value:data?.recalls?.length??'—',detail:'Included in return projection'}]} guide={['Only ON_LOAN / RECALLED contracts can submit securities returns.','Requested quantity must not exceed outstanding less pending returns.','When a recall reference is supplied, the amount must satisfy its remaining quantity and accepted status.','Custodian owns the recall and actual return confirmation.']} fix="T+1 Recall Notice → Borrower return → Custodian confirm"><LiveGate><ScopePanel scope={scope}/>{scope.tx&&<><Panel title="Recall notices" icon={AlertTriangle} subtitle="Read from existing contract return projection"><ResourceBody query={q}><DataTable rows={recalls} columns={[{key:'recallReference',label:'Recall ID'},{key:'requestedQuantity',label:'Requested',render:number},{key:'returnedQuantity',label:'Returned',render:number},{key:'dueAt',label:'Due at',render:stamp},{key:'status',label:'Status'}]}/></ResourceBody></Panel><Panel title="Submitted returns" icon={RotateCcw} actions={<div className="table-actions"><Refresh onClick={q.reload}/><Button icon={ArrowRight} disabled={busy||available<=0||!["ON_LOAN","RECALLED"].includes(data?.contractStatus)} onClick={()=>{setError("");setFormOpen(true)}}>New return</Button></div>}><ResourceBody query={q}><DataTable rows={returns} columns={[{key:'instructionReference',label:'Instruction'},{key:'recallReference',label:'Recall'},{key:'quantity',label:'Quantity',render:number},{key:'status',label:'Status'},{key:'submittedAt',label:'Submitted',render:stamp}]}/></ResourceBody></Panel>{formOpen&&<Modal title="New securities return" subtitle="Provide the return details; the backend validates recall and settlement eligibility." onClose={()=>setFormOpen(false)}><form className="resource-form" onSubmit={prepare}><div className="form-grid"><Field name="instructionReference" label="Return tracking reference" value={form.instructionReference} readOnly/><Field name="quantity" label="Return quantity" type="number" min="1" step="1" required value={form.quantity} onChange={v=>setForm(s=>({...s,quantity:v}))} hint={`Available after pending instructions: ${number(available)}`}/><Field name="recallReference" label="Link Custodian recall (optional)" options={recalls.filter(r=>['OPEN','PROCESSING','PARTIALLY_RETURNED','OVERDUE','DEFAULTED'].includes(r.status)).map(r=>({value:r.recallReference,label:`${r.recallReference} · ${r.status}`}))} value={form.recallReference} onChange={v=>setForm(s=>({...s,recallReference:v}))}/></div><FormMessage error={error}/><div className="form-actions"><Button type="submit" icon={ArrowRight} disabled={busy||available<=0||!['ON_LOAN','RECALLED'].includes(data?.contractStatus)}>Review return</Button></div></form></Modal>}</>}</LiveGate>{dialog&&<ConfirmDialog config={dialog} onClose={()=>setDialog(null)}/>}</WorkPage>}
/** FeesPage: server charges, single allocation payment; correct DTO and header key. */
export function FeesPage() {
  const scope = useScope()
  const { mode, notify, refresh } = useApp()
  const chargesQ = useRemote(() => contracts.charges(scope.tx), [scope.tx], {
    enabled: mode === 'live' && !!scope.tx,
  })
  const paysQ = useRemote(() => contracts.payments(scope.tx), [scope.tx], {
    enabled: mode === 'live' && !!scope.tx,
  })
  const charges = chargesQ.data || []
  const [itemKey, setItemKey] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const selected =
    charges.find(charge => charge.itemKey === itemKey) ||
    charges.find(charge => Number(charge.availableAmount) > 0)
  const [form, setForm] = useState({
    amount: '',
    paymentReference: newReference('PAY'),
  })
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState(null)
  const [busy, setBusy] = useState(false)
  const contractStatus = scope.row?.contractSnapshot?.contractStatus
  const paymentEligible = ['ON_LOAN', 'RETURNED'].includes(contractStatus)
  const currency = selected?.currency || scope.row?.contractSnapshot?.settlementCurrency || 'INR'
  const due = charges.reduce((sum, charge) => sum + Number(charge.availableAmount || 0), 0)

  function prepare(event) {
    event.preventDefault()
    setError('')
    const amount = Number(form.amount)
    if (!paymentEligible) {
      setError('Fee payments require an ON_LOAN or RETURNED contract.')
      return
    }
    if (
      !selected ||
      !selected.latestResultReference ||
      !/^LENDING_FEE:\d{4}-\d{2}-\d{2}$/.test(selected.itemKey)
    ) {
      setError('A payable server charge with a valid accrual result reference is required.')
      return
    }
    if (
      !/^[A-Za-z0-9._:-]{1,80}$/.test(form.paymentReference) ||
      !Number.isFinite(amount) ||
      amount < 0.01 ||
      !/^\d+(\.\d{1,2})?$/.test(form.amount) ||
      amount > Number(selected.availableAmount)
    ) {
      setError(
        'Provide a valid payment reference and amount within the selected available charge balance, to two decimals.',
      )
      return
    }
    const body = {
      amount,
      currency,
      allocations: [
        {
          calculationResultReference: selected.latestResultReference,
          itemKey: selected.itemKey,
          amount,
        },
      ],
    }
    setDialog({
      title: 'Submit fee payment instruction',
      message:
        'This sends a Borrower fee payment with one valid charge allocation; it does not debit a bank account or mark the payment PAID. Only the Custodian can confirm or fail a submitted payment.',
      subject: `${scope.tx} · ${currency} ${form.amount} · ${selected.itemKey}`,
      confirmText: 'Submit fee payment',
      execute: async () => {
        setBusy(true)
        try {
          const result = await contracts.submitPayment(
            scope.tx,
            body,
            form.paymentReference,
          )
          notify(`Payment ${result.paymentReference} submitted`, 'success')
          refresh()
          chargesQ.reload()
          paysQ.reload()
          setForm({ amount: '', paymentReference: newReference('PAY') })
          return result
        } finally {
          setBusy(false)
        }
      },
    })
  }

  return (
    <WorkPage
      title="Lending Fees & Payments"
      subtitle="Reconcile Custodian-accrued lending fees and allocate payments without bypassing contractual accounting."
      stats={[
        { label: 'Charge items', value: chargesQ.data?.length ?? '—', detail: 'Live charge list' },
        {
          label: 'Available payable',
          value: chargesQ.data ? money(due, currency) : '—',
          detail: 'Excludes submitted/pending allocations',
        },
        { label: 'Payments', value: paysQ.data?.length ?? '—', detail: 'Submitted/payment status' },
        {
          label: 'Contract state',
          value: contractStatus || '—',
          detail: 'ON_LOAN or RETURNED required',
        },
      ]}
      guide={[
        'The Custodian accrues fees and exposes charges by itemKey.',
        'Final accrued fees remain payable after return until the contract is closed.',
        'Payments must allocate to a current calculationResultReference and matching LENDING_FEE:YYYY-MM-DD key.',
        'The sum of allocation amounts equals the payment amount exactly.',
        'Idempotency-Key identifies the paymentReference and is reused only for the same payload.',
      ]}
      fix="Custodian fee accrual → Borrower payment → Custodian confirm/fail"
      footer="The Borrower can submit a payment but cannot mark it PAID. The Custodian validates allocations and confirms settlement."
    >
      <LiveGate>
        <ScopePanel scope={scope} />
        {scope.tx && (
          <>
            <Panel
              title="Accrued lending fee charges"
              icon={Banknote}
              actions={<Refresh onClick={chargesQ.reload} />}
              subtitle="GET /api/contracts/{transactionReference}/charges"
            >
              <ResourceBody query={chargesQ}>
                <DataTable
                  rows={charges}
                  columns={[
                    { key: 'itemKey', label: 'Item key' },
                    { key: 'accrualDate', label: 'Accrued' },
                    { key: 'amount', label: 'Gross', render: (value, row) => money(value, row.currency) },
                    { key: 'paidAmount', label: 'Paid', render: (value, row) => money(value, row.currency) },
                    { key: 'pendingAmount', label: 'Pending', render: (value, row) => money(value, row.currency) },
                    { key: 'availableAmount', label: 'Available', render: (value, row) => money(value, row.currency) },
                    { key: 'latestResultReference', label: 'Result UUID' },
                  ]}
                />
              </ResourceBody>
            </Panel>
            <Panel
              title="Borrower payments"
              icon={CreditCard}
              actions={<div className="table-actions"><Refresh onClick={paysQ.reload} /><Button icon={ArrowRight} disabled={!selected || !paymentEligible} onClick={() => { setError(''); setFormOpen(true) }}>New payment</Button></div>}
            >
              <ResourceBody query={paysQ}>
                <DataTable rows={paysQ.data || []} columns={[{ key: 'paymentReference', label: 'Payment' }, { key: 'amount', label: 'Amount', render: (value, row) => money(value, row.currency) }, { key: 'currency', label: 'CCY' }, { key: 'status', label: 'Status' }, { key: 'submittedAt', label: 'Submitted', render: stamp }]} />
              </ResourceBody>
            </Panel>
            {formOpen && <Modal title="Submit fee payment" subtitle="Allocate a payment to one current server charge." onClose={() => setFormOpen(false)}>
              <form className="resource-form" onSubmit={prepare}>
                <div className="form-grid">
                  <Field
                    name="itemKey"
                    label="Charge allocation"
                    required
                    value={selected?.itemKey || ''}
                    onChange={setItemKey}
                    options={charges
                      .filter(charge => Number(charge.availableAmount) > 0)
                      .map(charge => ({
                        value: charge.itemKey,
                        label: `${charge.itemKey} · available ${money(charge.availableAmount, charge.currency)}`,
                      }))}
                    hint="Required: LENDING_FEE:YYYY-MM-DD"
                  />
                  <Field
                    name="amount"
                    label="Payment amount"
                    required
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={form.amount}
                    onChange={value => setForm(state => ({ ...state, amount: value }))}
                    hint={`Max available ${money(selected?.availableAmount, currency)}`}
                  />
                  <Field
                    name="paymentReference"
                    label="Idempotency / payment reference"
                    required
                    value={form.paymentReference}
                    readOnly
                    hint="Generated automatically for this payment."
                  />
                  <Field
                    name="currency"
                    label="Charge currency"
                    value={currency}
                    readOnly
                  />
                  <Field
                    name="calculationResultReference"
                    label="Calculation result UUID"
                    value={selected?.latestResultReference || ''}
                    readOnly
                    hint="From latest server charge projection"
                  />
                </div>
                <FormMessage error={error} />
                <div className="form-actions">
                  <Button
                    type="submit"
                    icon={ArrowRight}
                    disabled={busy || !selected || !paymentEligible}
                  >
                    Review payment allocation
                  </Button>
                </div>
              </form>
            </Modal>}
          </>
        )}
      </LiveGate>
      {dialog && <ConfirmDialog config={dialog} onClose={() => setDialog(null)} />}
    </WorkPage>
  )
}
/** ReleasesPage: request collateral release only after full return, Custodian approves. */
export function ReleasesPage(){const scope=useScope();const {mode,notify,refresh}=useApp();const q=useRemote(()=>contracts.releases(scope.tx),[scope.tx],{enabled:mode==='live'&&!!scope.tx});const releases=q.data||[];const [releaseReference,setRef]=useState(newReference('REL'));const [formOpen,setFormOpen]=useState(false);const [error,setError]=useState('');const [dialog,setDialog]=useState(null);const [busy,setBusy]=useState(false);const status=scope.row?.contractSnapshot?.contractStatus;const outstanding=Number(scope.row?.contractSnapshot?.outstandingQuantity||0);const pending=releases.some(r=>['SUBMITTED','PENDING','APPROVED'].includes(r.status))
function prepare(e){e.preventDefault();setError('');if(status!=='RETURNED'||outstanding!==0){setError('All securities must be confirmed RETURNED with zero outstanding quantity before final collateral release.');return}if(pending){setError('An existing release request is already in progress.');return}if(!/^[A-Za-z0-9._:-]{1,48}$/.test(releaseReference)){setError('Release reference must be 1–48 ASCII letters, digits, periods, underscores, colons or dashes.');return}const body={releaseReference};setDialog({title:'Request final collateral release',message:'The Borrower requests release of the remaining pledged CASH only after completed securities return. Custodian approval and simulated settlement are separate events.',subject:`${scope.tx} · ${releaseReference}`,confirmText:'Send release request',execute:async()=>{setBusy(true);try{const result=await contracts.requestRelease(scope.tx,body);notify(`Release request ${result.releaseReference} submitted`,'success');refresh();q.reload();setRef(newReference('REL'));setFormOpen(false);return result}finally{setBusy(false)}}})}
return <WorkPage title="Final Collateral Release Requests" subtitle="After securities are fully returned, request Custodian authorization to release remaining cash collateral." stats={[{label:'Contract',value:scope.tx||'—',detail:'Borrower-owned transaction'},{label:'Contract state',value:status||'—',detail:'RETURNED required'},{label:'Outstanding units',value:number(scope.row?outstanding:null),detail:'Must be zero'},{label:'Release requests',value:q.data?.length??'—',detail:'Server-projected'}]} guide={['All securities must be returned; outstandingQuantity must be zero.','A SUBMITTED, PENDING or APPROVED release blocks duplicate requests.','Approval and collateral settlement are Custodian-only actions.']} fix="Borrower release request → Custodian approval → final settlement"><LiveGate><ScopePanel scope={scope}/>{scope.tx&&<><Panel title="Release approval status" icon={ShieldCheck} actions={<div className="table-actions"><Refresh onClick={q.reload}/><Button icon={ArrowRight} disabled={busy||pending||status!=="RETURNED"||outstanding!==0} onClick={()=>{setError("");setFormOpen(true)}}>New release request</Button></div>}><ResourceBody query={q}><DataTable rows={releases} columns={[{key:'releaseReference',label:'Release ref'},{key:'status',label:'Status'},{key:'submittedAt',label:'Submitted',render:stamp},{key:'approvedAt',label:'Approved',render:stamp}]}/></ResourceBody></Panel>{formOpen&&<Modal title="Initiate final release request" subtitle="Custodian approval and settlement remain separate." onClose={()=>setFormOpen(false)}><form onSubmit={prepare} className="resource-form"><div className="form-grid"><Field name="releaseReference" label="Release tracking reference" value={releaseReference} readOnly hint="Generated automatically for this request."/></div><FormMessage error={error}/><div className="form-actions"><Button type="submit" icon={ArrowRight} disabled={busy||pending||status!=='RETURNED'||outstanding!==0}>Review release request</Button></div></form></Modal>}</>}</LiveGate>{dialog&&<ConfirmDialog config={dialog} onClose={()=>setDialog(null)}/>}</WorkPage>}
/** ActivityPage: derives only timestamps returned by authenticated loan-request reads. */
export function ActivityPage(){const {mode}=useApp();const q=useRemote(()=>loans.list(0,100),[],{enabled:mode==='live'});const entries=(q.data?.content||[]).flatMap(r=>[{key:`${r.requestReference}-request`,reference:r.requestReference,time:r.requestedAt,type:'LOAN_REQUEST_SUBMITTED',status:r.submissionStatus,detail:r.securityIsin},{key:`${r.requestReference}-decision`,reference:r.requestReference,time:r.respondedAt,type:'LOAN_DECISION',status:r.status,detail:r.responseReason||'Custodian decision'}].filter(x=>x.time)).sort((a,b)=>new Date(b.time)-new Date(a.time));return <WorkPage title="Borrower Activity Timeline" subtitle="A transparent, read-only view assembled from authenticated loan-request timestamps." stats={[{label:'Recent request events',value:q.data?.content?.length??'—',detail:'Up to latest 100 requests'},{label:'Contract events',value:'Unavailable',detail:'No contract-list endpoint'},{label:'Timeline source',value:'Derived',detail:'No audit stream API'},{label:'FIX journal',value:'Unavailable',detail:'No REST read endpoint'}]} guide={['This screen derives request timestamps only; it is not a complete tamper-evident audit log.','Open an approved loan request for its contract projection and instruction histories.','The FIX/Kafka journal has no Borrower REST read endpoint.']} fix="Borrower request events / read-only projection" footer="Derived request timeline only: not a unified audit log. Contract projection timestamps are not listed by the Borrower API."><LiveGate><Panel title="Recent loan-request events" icon={Activity} actions={<Refresh onClick={q.reload}/>} subtitle="Loan requestedAt/respondedAt only"><ResourceBody query={q} empty={!entries.length} emptyText="No event timestamps returned by the live service."><DataTable rows={entries} keyField="key" columns={[{key:'time',label:'Timestamp',render:stamp},{key:'reference',label:'Reference'},{key:'type',label:'Recorded event'},{key:'status',label:'Status'},{key:'detail',label:'Details'}]}/></ResourceBody></Panel></LiveGate></WorkPage>}
