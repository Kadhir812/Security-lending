import { Link } from 'react-router-dom'
import { AlertCircle, ArrowRight, LockKeyhole, RefreshCw } from 'lucide-react'
import { ApiState, Button, FooterNote, KpiStrip, PageHeading, Panel, SideGuide } from './UI'
import { useApp } from '../hooks/useApp'
/** WorkPage: every workflow gets hierarchy, KPIs, dense primary work card, rules and footer. */
export function WorkPage({ eyebrow, title, subtitle, action, stats, guide, fix, note, footer, children }) {
  return <><PageHeading eyebrow={eyebrow || 'BORROWER WORKSPACE'} title={title} subtitle={subtitle} action={action}/><KpiStrip items={stats || [
    {label:'Service',value:'Borrower',detail:'Port 8082'}, {label:'Identity',value:'TRADER',detail:'Role controlled by backend'},
    {label:'Approval',value:'Custodian',detail:'Separate operator workflow'}, {label:'Delivery',value:'Kafka',detail:'Asynchronous projection'}
  ]}/><div className="work-area"><div className="work-area__main">{children}</div><div className="work-area__side"><SideGuide title="Workflow guidance" items={guide || ['Only borrower-scoped records are visible.','Each mutation is validated by the Borrower and Custodian services.','Refresh to see asynchronous event or settlement progress.']} fix={fix} note={note}/><div className="side-links"><strong>Related workspaces</strong>{[['Loan requests','/requests'],['Contract portfolio','/contracts'],['Margin & collateral','/margin'],['Fees & payments','/fees'],['Session & access','/auth']].map(([name,to])=><Link key={to} to={to}>{name}<ArrowRight size={14}/></Link>)}</div></div></div><FooterNote>{footer || 'Only returned service data is authoritative. Pending submissions may not yet be accepted, settled or reflected in downstream projections.'}</FooterNote></>
}
/** LiveGate: prevents demo toggle from sending real backend actions. */
export function LiveGate({ children }) { const {mode, setMode}=useApp(); if(mode==='live')return children;return <Panel title="Live operations are paused" icon={LockKeyhole}><div className="live-gate"><div><strong>The dashboard is showing demo data.</strong><p>Switch to Live to read your authenticated Borrower records and use production-shaped API requests. Demo examples never trigger mutations.</p></div><Button onClick={()=>setMode('live')}>Switch to Live <ArrowRight size={15}/></Button></div></Panel> }
/** ResourceBody: standard token / pending / error / empty handling. */
export function ResourceBody({query,empty,emptyText,children}) {const {token}=useApp();return <ApiState token={token} loading={query.loading} error={query.error} reload={query.reload} empty={empty} emptyText={emptyText}>{children}</ApiState>}
/** Refresh: explicit read retry; never reruns non-idempotent writes. */
export function Refresh({onClick}){return <Button variant="outline" icon={RefreshCw} onClick={onClick}>Refresh</Button>}
/** FormMessage: server status, validation or action result. */
export function FormMessage({error,success}) {return <>{error&&<p className="form-error" role="alert"><AlertCircle size={14}/> {typeof error==='string'?error:error.message}</p>}{success&&<div className="form-success">{success}</div>}</>}
/** ContractPicker: selection by a borrower-owned contract returned from GET /api/contracts. */
export function ContractPicker({items=[],value,onChange}){return <div className="field"><label htmlFor="contract-select">Transaction reference</label><select id="contract-select" value={value} onChange={e=>onChange(e.target.value)}><option value="">Select a live contract…</option>{items.map(item=><option key={item.transactionReference} value={item.transactionReference}>{item.transactionReference} · {item.contractSnapshot?.securityIsin || item.requestReference}</option>)}</select><small>Only borrower-owned contract projections are selectable.</small></div>}
