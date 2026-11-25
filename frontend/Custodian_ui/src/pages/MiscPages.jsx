import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, ArrowRight, CheckCircle2, CircleHelp, ClipboardCheck, FileQuestion, RefreshCw, ShieldCheck } from 'lucide-react'
import { releases } from '../api/resources'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { ApiState, Button, ConfirmDialog, DataTable, DemoTag, DetailGrid, FooterNote, KpiStrip, PageHeading, Panel, SideGuide, StatusBadge } from '../components/UI'
import { stamp } from '../utils/format'
import { collateralInquiries } from '../api/resources'
import { Pagination } from '../components/UI'

/** ReleaseQueue: authoritative Borrower-submitted approval queue, no simulated request IDs. */
export function ReleaseQueue(){const {token,notify}=useApp();const [dialog,setDialog]=useState(null);const [result,setResult]=useState(null);const {data,loading,error,reload}=useRemote(()=>releases.queue(),[]);const rows=Array.isArray(data)?data:[]
  function approve(row){setDialog({title:'Approve final collateral release',message:'This approval authorizes a release only after the backend verifies the authenticated Custodian, matching Borrower request, returned securities and contractual obligations. The actual settlement may require a separate confirmation.',subject:`${row.transactionReference} / ${row.releaseReference}`,confirmText:'Approve release request',execute:async()=>{const value=await releases.approve(row.transactionReference,row.releaseReference);setResult(value);notify(`Release approval outcome: ${value?.status||'returned'}`,'success');reload();return value}})}
  return <div className="sub-page"><PageHeading title="Collateral-Release Approval Queue" subtitle="Review actual Borrower release submissions, verify settlement prerequisites and approve specific returned release references." action={<Button variant="outline" icon={RefreshCw} onClick={reload}>Refresh queue</Button>}/><KpiStrip items={[{label:'Received requests',value:token?rows.length:'—',detail:'Live service array'},{label:'Pending approvals',value:token?rows.filter(x=>x.status==='PENDING_APPROVAL').length:'—',detail:'Current returned statuses'},{label:'Reference source',value:'BORROWER',detail:'Never generated here'},{label:'Service API',value:'AVAILABLE',detail:'Subject to authentication'}]}/><div className="work-area"><div className="work-area__main"><Panel title="Borrower release requests"><ApiState token={token} loading={loading} error={error} reload={reload}><DataTable rows={rows} columns={[{key:'transactionReference',label:'Contract'},{key:'releaseReference',label:'Release reference'},{key:'borrowerOrganizationReference',label:'Borrower'},{key:'status',label:'State',render:v=><StatusBadge value={v}/>},{key:'receivedAt',label:'Received',render:v=>stamp(v)}]} actions={row=><Button variant="approve" icon={CheckCircle2} onClick={()=>approve(row)} disabled={row.status!=='PENDING_APPROVAL'}>Review approval</Button>}/></ApiState></Panel>{result&&<Panel title="Last approval response"><DetailGrid data={result}/></Panel>}<div className="info-banner">Final collateral release is a multi-stage workflow: Borrower request → Custodian approval → settlement instruction → external confirmation or explicitly labeled simulation.</div></div><div className="work-area__side"><SideGuide title="Release safeguards" items={['Use transactionReference and releaseReference exactly as returned by the queue.','Verify final securities return, fees and settlement obligations before approval.','Backend controls prevent duplicate and ineligible releases; HTTP 409/422 requires state review.','Settlement simulation is on the Settlement Operations page and must be explicitly enabled.']} fix="Return → release approval → final collateral payout" note="An approval response does not prove external collateral payout completion."/></div></div><FooterNote>Only released collateral confirmed by the backend can be used to satisfy contract-close conditions.</FooterNote>{dialog&&<ConfirmDialog config={dialog} onClose={()=>setDialog(null)}/>}</div>
}
/** Custodian-side BB/BA visibility backed by persisted service inbox and outbox records. */
export function CollateralInquiries(){
  const {token}=useApp();
  const [page,setPage]=useState(0);
  const query=useRemote(()=>collateralInquiries.list(page,20),[page]);
  const result=query.data||{};
  const rows=result.content||[];
  return <div className="sub-page">
    <PageHeading title="Collateral Inquiry / Report Visibility" subtitle="Review actual Borrower FIX BB inquiries and the Custodian FIX BA report publication state." action={<Button variant="outline" icon={RefreshCw} onClick={query.reload}>Refresh</Button>}/>
    <KpiStrip items={[
      {label:'Inquiries on page',value:token?rows.length:'—',detail:'Processed FIX BB records'},
      {label:'Reports published',value:token?rows.filter(row=>row.reportStatus==='PUBLISHED').length:'—',detail:'Custodian outbox confirms publish'},
      {label:'Awaiting publication',value:token?rows.filter(row=>['PENDING','PUBLISHING'].includes(row.reportStatus)).length:'—',detail:'Report event queued'},
      {label:'Message flow',value:'BB → BA',detail:'FIX 4.4 collateral messages'},
    ]}/>
    <div className="work-area"><div className="work-area__main">
      <Panel title="Borrower collateral inquiries" subtitle="Only inquiries addressed to your authenticated Custodian organization are returned.">
        <ApiState token={token} loading={query.loading} error={query.error} reload={query.reload}>
          <DataTable keyField="inquiryId" rows={rows} columns={[
            {key:'inquiryId',label:'Inquiry'}, {key:'requestReference',label:'Loan request'}, {key:'mslaReference',label:'MSLA'},
            {key:'borrowerOrganizationReference',label:'Borrower'}, {key:'receivedAt',label:'Received',render:stamp},
            {key:'processingStatus',label:'Inquiry state',render:value=><StatusBadge value={value}/>},
            {key:'reportEventId',label:'Report event'}, {key:'reportStatus',label:'BA publication',render:value=><StatusBadge value={value}/>},
            {key:'reportPublishedAt',label:'Published',render:stamp},
          ]} emptyLabel="No processed collateral inquiries were returned for this Custodian."/>
          {result.totalPages>1&&<Pagination page={result.page||0} totalPages={result.totalPages} totalElements={result.totalElements||0} size={result.size||20} onPage={setPage}/>}
        </ApiState>
      </Panel>
      <Panel title="Message progression"><div className="message-journey"><div><b>01</b><strong>Borrower inquiry · BB</strong><p>Borrower asks for collateral terms under a submitted loan request.</p></div><ArrowRight size={23}/><div><b>02</b><strong>Custodian report · BA</strong><p>Agreement terms and effective haircut rules produce the server-owned response.</p></div><ArrowRight size={23}/><div><b>03</b><strong>Settlement initiation</strong><p>Collateral instructions remain a separate contract operation.</p></div></div></Panel>
    </div><div className="work-area__side"><SideGuide title="Operational meaning" items={['Inquiry status means the FIX BB event was processed by this service.','BA status reflects the Custodian outbox publisher; PUBLISHED records a broker publish acknowledgement, not Borrower receipt.','Collateral values, haircut, and coverage are determined by the agreement and backend rules.','Settlement is tracked separately on the contract workspace.']} fix="FIX BB · CollateralInquiry / FIX BA · CollateralReport" note="The browser reads only authenticated REST responses and never connects to Kafka or service databases."/></div></div>
    <FooterNote>Report publication and settlement confirmation are separate states. A published BA is not evidence that collateral has settled.</FooterNote>
  </div>
}
const glossary = [
  ['MSLA','Master Securities Lending Agreement: contractual terms that establish eligible parties, collateral, rates and recall conditions.'],
  ['MTM','Mark-to-market: server-derived revaluation of securities and collateral exposures using a trusted valuation snapshot.'],
  ['Haircut','Risk adjustment to collateral valuation. Effective haircuts are resolved from authoritative agreement rules.'],
  ['Rebate / fee','Consideration on a lending position; fees are accrued and reconciled using server-calculated charges.'],
  ['Recall','Custodian instruction to return lent securities, subject to agreed timing and inventory controls.'],
  ['T+1','Settlement or recall timeline one business day after the applicable trade/notice date, subject to local calendar.'],
  ['Shortfall','Required eligible collateral less credited collateral as calculated by the service.'],
  ['FIX AF','Original training document labels Request For Security AF; actual current backend loan requests use project-defined U1. FIX 4.4 AF has a different standard meaning.'],
  ['FIX BB','Standard FIX 4.4 CollateralInquiry sent by Borrower to Custodian.'],
  ['FIX BA','Standard FIX 4.4 CollateralReport issued by the Custodian.'],
  ['FIX AE','Trade Capture Report mentioned in the original business workflow.'],
  ['FIX AK','Trade Capture Report Ack / project trade confirmation profile used for authorized decision notification.'],
  ['FIX f','SecurityStatus: lending security status information in the original business workflow.'],
]
