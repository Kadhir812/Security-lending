import { Link } from 'react-router-dom'
import { Activity, AlertTriangle, ArrowRight, ArrowLeftRight, CheckCircle2, Clock3, Database, FileClock, FileText, FolderKanban, Layers3, RefreshCw, ShieldCheck, Wallet } from 'lucide-react'
import { DemoTag, StatusBadge } from '../components/UI'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { demoContracts, demoRequests } from '../mock/dashboard'
import { loanRequests } from '../api/resources'
import { referenceLabel } from '../utils/format'

const ROLE = 'Custodian'
const isCustodian = ROLE === 'Custodian'
const routes = isCustodian ? {
  requests: '/requests', review: '/requests/review/', contracts:'/contracts', collateral:'/release-queue', settlement:'/settlement', margin:'/margin', inventory:'/inventory', orgs:'/organizations', agreements:'/mslas', fees:'/payments', audit:'/audit'
} : {
  requests:'/requests', review:'/requests/', contracts:'/contracts', collateral:'/collateral', settlement:'/settlement', margin:'/margin', inventory:'/eligible-mslas', orgs:'/custodian-link', agreements:'/eligible-mslas', fees:'/fees', audit:'/activity'
}
const demoMetrics = isCustodian ? [
  ['Incoming requests','4','On the illustrative queue','requests'],['Pending approvals','3','Needs custodian review','requests'],['Active contracts','3','Illustrative contracts','contracts'],['Collateral monitoring','—','Open contract-level records','collateral'],['Margin attention','—','Review margin records','margin'],['Settlement queue','—','Open settlement processing','settlement']
] : [
  ['My loan requests','4','Illustrative submissions','requests'],['Awaiting decision','2','Borrower-side queue','requests'],['Borrowing contracts','3','Illustrative contracts','contracts'],['Cash collateral','—','Open contract-level records','collateral'],['Margin attention','—','Review margin records','margin'],['Settlement progress','—','Open contract status','settlement']
]
const labels=['Request','Approval','Collateral','Settlement','Margin','Return','Closure']
const priorityLinks = isCustodian ? [
  {icon:FileText,title:'Review incoming loan requests',detail:'Authorize or reject eligible requests',to:'/requests'},
  {icon:ShieldCheck,title:'Review collateral release queue',detail:'Check return and fee prerequisites',to:'/release-queue'},
  {icon:ArrowLeftRight,title:'Check settlement instructions',detail:'Review pending and failed instructions',to:'/settlement'},
  {icon:AlertTriangle,title:'Inspect margin obligations',detail:'Evaluate calls and shortfall conditions',to:'/margin'}
] : [
  {icon:FileText,title:'Submit a borrow request',detail:'Select eligible MSLA and security',to:'/requests/new'},
  {icon:ShieldCheck,title:'Post cash collateral',detail:'Use an approved contract reference',to:'/collateral'},
  {icon:AlertTriangle,title:'Review margin calls',detail:'Inspect collateral top-up obligations',to:'/margin'},
  {icon:Wallet,title:'Review lending fees',detail:'Allocate and submit due payments',to:'/fees'}
]
function WorkPanel({title,icon:Icon=Layers3,to,children,meta,cl}){return <section className={`os-overview-panel ${cl||''}`}><div className="os-overview-panel-head"><div><Icon size={17}/><h2>{title}</h2>{meta&&<span className="os-overview-meta">{meta}</span>}</div>{to&&<Link to={to}>Open <ArrowRight size={14}/></Link>}</div><div className="os-overview-panel-content">{children}</div></section>}
function Metric({label,value,sub,to}){return <Link className="os-overview-metric" to={to}><span>{label}</span><strong>{value}</strong><small>{sub}</small><span className="os-overview-metric-arrow"><ArrowRight size={14}/></span></Link>}
function ServiceUnavailable({text,to,label}){return <div className="os-availability"><Database size={22}/><strong>{text}</strong><p>This aggregate is not exposed by the current service. Review authoritative records in the related operation instead.</p>{to&&<Link to={to}>{label||'Open operation'} <ArrowRight size={13}/></Link>}</div>}
function WorkflowHeatmap({rows,isDemo}){
  const grouped = rows.slice(0,6).reduce((acc,r)=>{let k=r.borrowerOrganizationReference||'My requests';if(!acc[k])acc[k]=[];acc[k].push(r);return acc},{})
  const groups=Object.entries(grouped)
  return <div className="os-workflow-heatmap"><table><thead><tr><th>{isCustodian?'Borrower':'Request group'}</th>{labels.map(l=><th key={l}>{l}</th>)}</tr></thead><tbody>{groups.length?groups.map(([name,rs])=>{const pending=rs.filter(r=>['PENDING','REVIEW','SUBMITTED'].includes(String(r.status).toUpperCase())).length;const approved=rs.filter(r=>r.status==='APPROVED').length;return <tr key={name}><td title={name}>{name}</td>{labels.map((l,i)=><td key={l}><span className={`os-heat-cell ${i===0?'os-heat-neutral':i===1?(pending?'os-heat-warning':'os-heat-success'):'os-heat-unavailable'}`}>{i===0?rs.length:i===1?(pending?pending:approved||'—'):'—'}</span></td>)}</tr>}) : <tr><td colSpan={8} className="os-no-records">No requests returned for this view</td></tr>}</tbody></table>{!isDemo&&<div className="os-heat-foot">Live view: request and decision statuses only; other workflow columns require contract-specific APIs.</div>}</div>
}
export default function Dashboard(){
  const {mode,service}=useApp()
  const demo=mode==='demo'
  const {data,loading,error,reload}=useRemote(()=>loanRequests.list(0,10),[],{enabled:!demo})
  const rows=demo?demoRequests:(Array.isArray(data?.content)?data.content:[])
  const total=demo?rows.length:data?.totalElements??'—'
  const pending=rows.filter(r=>['PENDING','REVIEW','SUBMITTED'].includes(String(r.status).toUpperCase())).length
  const metrics=demo?demoMetrics.map(([label,value,sub,key])=>({label,value,sub,to:routes[key]})):[
    {label:ROLE==='Custodian'?'Incoming requests':'My loan requests',value:total,sub:'Backend paginated response',to:routes.requests},
    {label:ROLE==='Custodian'?'Pending in this page':'Awaiting decision in this page',value:loading?'…':pending,sub:'Current page only',to:routes.requests},
    {label:'Active contracts',value:'—',sub:'Portfolio aggregate unavailable',to:routes.contracts},
    {label:'Collateral monitoring',value:'—',sub:'Contract reference required',to:routes.collateral},
    {label:'Margin attention',value:'—',sub:'Open contract-level workflow',to:routes.margin},
    {label:'Settlement queue',value:'—',sub:'Open settlement workflow',to:routes.settlement}
  ]
  return <div className="os-overview">
    <div className="os-overview-intro"><div><span className="os-overview-eyebrow">{ROLE.toUpperCase()} OPERATIONS / WORKSPACE</span><h1>{isCustodian?'Operations Command Center':'Borrower Trading Workspace'}</h1><p>{isCustodian?'Review lending requests, settlement, collateral and exceptions from one place.':'Track requests, collateral, contracts, returns and desk obligations from one place.'}</p></div><div className="os-overview-intro-actions"><span className={`os-overview-source ${demo?'is-demo':''}`}>{demo?'● Illustrative demo data':service.state==='online'?'● Connected to service':'○ Service status unverified'}</span><button onClick={reload} title="Refresh requests"><RefreshCw size={14}/> Refresh</button><Link to={routes.requests}>Open Request Desk <ArrowRight size={14}/></Link></div></div>
    <div className="os-overview-metrics">{metrics.map(m=><Metric key={m.label} {...m}/>)}</div>
    {error&&!demo&&<div className="os-overview-error" role="alert">Requests could not be loaded: {error.message}. Other modules remain accessible. <button onClick={reload}>Retry</button></div>}
    <div className="os-overview-row os-overview-row-top">
      <WorkPanel title="Counterparty workflow matrix" icon={Layers3} to={routes.requests} meta="Request-stage visibility"><WorkflowHeatmap rows={rows} isDemo={demo}/></WorkPanel>
      <WorkPanel title={isCustodian?'Incoming loan requests':'My loan requests'} icon={FileText} to={routes.requests} meta={loading&&!demo?'Loading…':`${rows.length} visible`}><div className="os-overview-table"><table><thead><tr><th>Reference</th><th>{isCustodian?'Borrower':'MSLA'}</th><th>Security</th><th>Qty</th><th>Status</th></tr></thead><tbody>{rows.slice(0,7).map(r=><tr key={r.requestReference}><td><Link to={demo?routes.requests:routes.review+encodeURIComponent(r.requestReference)}>{referenceLabel(r.requestReference,'requestReference')}</Link></td><td>{isCustodian?referenceLabel(r.borrowerOrganizationReference,'borrowerOrganizationReference'):r.mslaReference}</td><td title={r.securityIsin}>{r.securityIsin}</td><td>{Number(r.quantity||0).toLocaleString('en-US')}</td><td><StatusBadge value={r.status}/></td></tr>)}{!rows.length&&<tr><td colSpan={5} className="os-no-records">{loading?'Loading…':demo?'No illustrative records':'No requests returned'}</td></tr>}</tbody></table></div></WorkPanel>
      <WorkPanel title="Priority actions" icon={AlertTriangle} meta="Quick launch"><div className="os-priority-list">{priorityLinks.map(a=><Link to={a.to} key={a.to}><span className="os-action-icon"><a.icon size={17}/></span><span><strong>{a.title}</strong><small>{a.detail}</small></span><ArrowRight size={14}/></Link>)}</div></WorkPanel>
    </div>
    <div className="os-overview-row os-overview-row-middle">
      <WorkPanel title={isCustodian?'Active loan contracts':'Borrowed contracts'} icon={FolderKanban} to={routes.contracts}>{demo?<div className="os-overview-table"><table><thead><tr><th>Contract</th><th>Security</th><th>Quantity</th><th>Status</th></tr></thead><tbody>{demoContracts.map(c=><tr key={c.transactionReference}><td><Link to={routes.contracts}>{referenceLabel(c.transactionReference,'transactionReference')}</Link></td><td>{c.security||c.symbol}</td><td>{Number(c.quantity).toLocaleString('en-US')}</td><td><StatusBadge value={c.status}/></td></tr>)}</tbody></table></div>:<ServiceUnavailable text="Contract portfolio summary unavailable" to={routes.contracts} label="Look up a contract"/>}</WorkPanel>
      <WorkPanel title="Collateral & margin monitoring" icon={ShieldCheck} to={routes.margin}>{demo?<div className="os-monitoring-grid"><div><span>Collateral requirement</span><strong>Contract-level</strong><small>Open collateral panel</small></div><div><span>Posted collateral</span><strong>Contract-level</strong><small>Review confirmations</small></div><div><span>Margin exceptions</span><strong>Review</strong><small>Requires current contract</small></div><div><span>Coverage</span><strong>Calculated by API</strong><small>Do not infer from demo</small></div></div>:<ServiceUnavailable text="Portfolio-wide margin aggregate unavailable" to={routes.margin} label="Open margin workflow"/>}</WorkPanel>
      <WorkPanel title="Settlement pipeline" icon={ArrowLeftRight} to={routes.settlement}>{demo?<div className="os-pipeline"><div><span>01</span><strong>Request</strong><small>New submissions</small></div><div><span>02</span><strong>Approval</strong><small>Eligibility checks</small></div><div><span>03</span><strong>Collateral</strong><small>Post instruction</small></div><div><span>04</span><strong>Settlement</strong><small>Confirm delivery</small></div></div>:<ServiceUnavailable text="Settlement count feed unavailable" to={routes.settlement} label="Open settlement operations"/>}</WorkPanel>
    </div>
    <div className="os-overview-row os-overview-row-bottom">
      <WorkPanel title="Connected operations" icon={Database}><div className="os-module-matrix">{[['Inventory',routes.inventory,Layers3],['Agreements',routes.agreements,FileText],[isCustodian?'Organizations':'Custodian link',routes.orgs,ShieldCheck],['Fees & payments',routes.fees,Wallet]].map(([title,to,Icon])=><Link to={to} key={to}><Icon size={19}/><strong>{title}</strong><ArrowRight size={14}/></Link>)}</div></WorkPanel>
      <WorkPanel title="Workflow lifecycle" icon={Activity}><div className="os-lifecycle-map">{['Request','Approval','Collateral','Settlement','On Loan','Return','Closure'].map((s,i)=><div key={s}><span className={i<2?'complete':''}>{i+1}</span><small>{s}</small></div>)}</div><div className="os-overview-callout">Illustrative journey · Current transaction state is available from the selected request or contract record.</div></WorkPanel>
      <WorkPanel title="System & activity" icon={Clock3} to={routes.audit}><div className="os-system-items"><div><CheckCircle2 size={16}/><span>Authenticated API connectivity</span><strong>{service.state==='online'?'Connected':'Check service'}</strong></div><div><FileClock size={16}/><span>Operational activity</span><Link to={routes.audit}>Open journal</Link></div><div><Database size={16}/><span>Workspace data mode</span><strong>{demo?'Demo fixture':'Live endpoints'}</strong></div><div><AlertTriangle size={16}/><span>Asynchronous messaging</span><strong>Verify at source</strong></div></div></WorkPanel>
    </div>
    <div className="os-overview-legend">{demo?<><DemoTag/> All mock records are illustrative and cannot be submitted as transactions.</>:'Only returned service data is authoritative. Aggregated metrics without implemented endpoints remain unavailable.'}</div>
  </div>
}
