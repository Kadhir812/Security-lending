import { useEffect, useMemo, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Activity, ArrowLeftRight, Bell, BookOpen, Building2, Check, ChevronDown, CircleDollarSign, ClipboardCheck, Clock3, CreditCard, FileCheck2, FileClock, FileText, FolderKanban, Grid2X2, Handshake, Layers3, LogOut, Minimize2, MoreHorizontal, Plus, RefreshCw, Search, Settings2, ShieldCheck, SlidersHorizontal, Square, Wallet, X } from 'lucide-react'
import { useApp } from '../hooks/useApp'
import { Notice } from '../components/UI'
import { useRemote } from '../hooks/useRemote'
import { auth } from '../api/auth'

const ROLE = 'Borrower'
const otherURL = import.meta.env.VITE_CUSTODIAN_UI_URL || 'http://localhost:5173'
const iconByKey = { workspace: Grid2X2, requests: FileText, contracts: FolderKanban, collateral: ShieldCheck, inventory: Layers3, settlement: ArrowLeftRight, margin: Activity, reports: FileClock, locates: Search, organizations: Building2, mandates: Handshake, mslas: FileCheck2, credit: CreditCard, haircuts: SlidersHorizontal, fees: CircleDollarSign, recalls: RefreshCw, payments: Wallet, releases: ClipboardCheck, close: Check, audit: Clock3, link: Handshake, glossary: BookOpen }
const menu = [{"key": "requests", "text": "Loan Requests", "to": "/requests"}, {"key": "contracts", "text": "Contracts", "to": "/contracts"}, {"key": "collateral", "text": "Cash Collateral", "to": "/collateral"}, {"key": "inventory", "text": "Eligible MSLAs", "to": "/eligible-mslas"}, {"key": "settlement", "text": "Settlement", "to": "/settlement"}, {"key": "margin", "text": "Margin Calls", "to": "/margin"}, {"key": "reports", "text": "Activity", "to": "/activity"}, {"key": "locates", "text": "Locates", "to": "/locates"}, {"key": "newloan", "text": "New Loan Request", "to": "/requests/new"}, {"key": "newlocate", "text": "New Locate", "to": "/locates/new"}, {"key": "returns", "text": "Returns", "to": "/returns"}, {"key": "fees", "text": "Fees & Payments", "to": "/fees"}, {"key": "releases", "text": "Collateral Release", "to": "/releases"}, {"key": "link", "text": "Custodian Link", "to": "/custodian-link"}, {"key": "glossary", "text": "Glossary", "to": "/glossary"}, {"key": "inquiry", "text": "Collateral Inquiries", "to": "/collateral-inquiries"}]
const dockKeys = ["workspace", "requests", "contracts", "collateral", "inventory", "settlement", "margin", "reports"]
const routes = [{ key:'workspace', text:'Workspace', to:'/' }, ...menu]
const routeLabel = path => {
  const match = [...routes].sort((a,b)=>b.to.length-a.to.length).find(r => r.to !== '/' && (path === r.to || path.startsWith(r.to + '/')))
  if (match) return match.text
  return 'Operations Command Center'
}
const userPath = ROLE === 'Custodian' ? '/audit' : '/activity'
function BrandLogo(){return <span className="os-brand-mark" aria-hidden="true"><i/><i/><i/><i/></span>}
function getSavedTabs(){try{const tabs=JSON.parse(sessionStorage.getItem(`slo-${ROLE}-tabs`)||'[]');return Array.isArray(tabs) ? tabs.filter(x=>x&&typeof x.to==='string'&&typeof x.text==='string').slice(-7):[]}catch{return []}}
function useWorkspace(){
  const {pathname}=useLocation(), navigate=useNavigate()
  const [tabs,setTabs]=useState(getSavedTabs)
  const [layout,setLayout]=useState(()=>{try{return sessionStorage.getItem(`slo-${ROLE}-layout`)||'balanced'}catch{return 'balanced'}})
  useEffect(()=>{if(pathname==='/')return;setTabs(prev=>prev.some(tab=>tab.to===pathname)?prev:[...prev.slice(-6),{to:pathname,text:routeLabel(pathname)}])},[pathname])
  useEffect(()=>{try{sessionStorage.setItem(`slo-${ROLE}-tabs`,JSON.stringify(tabs))}catch{/* storage restricted */}},[tabs])
  useEffect(()=>{try{sessionStorage.setItem(`slo-${ROLE}-layout`,layout)}catch{/* storage restricted */}},[layout])
  const closeTab=(to)=>{setTabs(prev=>prev.filter(tab=>tab.to!==to));if(pathname===to)navigate('/')}
  return {tabs,setTabs,closeTab,layout,setLayout}
}
function LaunchItem({item,click,selected=false,compact=false}){
  const Icon=iconByKey[item.key]||FileText
  return <Link to={item.to} onClick={click} className={`${compact?'os-launch-row':'os-dock-link'} ${selected?'is-active':''}`} title={item.text}><Icon size={compact?17:21} strokeWidth={1.9}/><span>{item.text}</span></Link>
}
function AppDock({pathname,openAll,setOpenAll}){
  const dockItems=dockKeys.map(k=>routes.find(r=>r.key===k)).filter(Boolean)
  const selectedKey = [...dockItems].sort((a,b)=>b.to.length-a.to.length).find(r=>r.to!=='/'&&(pathname===r.to||pathname.startsWith(r.to+'/')))?.key||'workspace'
  const hiddenSelection = pathname !== '/' && !dockItems.some(r=>r.to!=='/'&&(pathname===r.to||pathname.startsWith(r.to+'/')))
  const otherItems=menu.filter(item=>!dockKeys.includes(item.key))
  return <nav className="os-dock" aria-label="Primary application navigation">
    {dockItems.map((item,i)=><LaunchItem item={item} selected={selectedKey===item.key} key={`${item.to}-${i}`} />)}
    <div className="os-more-host"><button type="button" className={`os-dock-link os-dock-button ${openAll||hiddenSelection?'is-active':''}`} onClick={()=>setOpenAll(!openAll)} aria-haspopup="menu" aria-expanded={openAll}><MoreHorizontal size={22}/><span>More</span></button>
      {openAll&&<div className="os-more-menu" role="menu"><div className="os-menu-title">All {ROLE} modules</div>{otherItems.map(item=><LaunchItem key={item.to} item={item} compact click={()=>setOpenAll(false)}/>)}</div>}
    </div>
  </nav>
}
export default function Shell(){
  const {token,saveToken,mode,setMode,service,checkService,correlationId}=useApp()
  const profile = useRemote(() => auth.me(), [token], { enabled: !!token })
  const {pathname}=useLocation(),navigate=useNavigate()
  const {tabs,setTabs,closeTab,layout,setLayout}=useWorkspace()
  const [query,setQuery]=useState(''),[commandOpen,setCommandOpen]=useState(false),[profileOpen,setProfileOpen]=useState(false),[openAll,setOpenAll]=useState(false),[layoutOpen,setLayoutOpen]=useState(false),[minimized,setMinimized]=useState(false),[maximized,setMaximized]=useState(false)
  const results=useMemo(()=>routes.filter(r=>(r.text+' '+r.to).toLowerCase().includes(query.toLowerCase())),[query])
  useEffect(()=>{setMinimized(false);setMaximized(false);setOpenAll(false);setLayoutOpen(false)},[pathname])
  useEffect(()=>{
    const onKey=e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();setCommandOpen(v=>!v)}if(e.key==='Escape'){setCommandOpen(false);setOpenAll(false);setProfileOpen(false);setLayoutOpen(false)}}
    window.addEventListener('keydown',onKey);return()=>window.removeEventListener('keydown',onKey)
  },[])
  const title=routeLabel(pathname)
  const current=routes.find(r=>r.to===pathname)||routes.find(r=>r.to!=='/'&&pathname.startsWith(r.to+'/'))
  const CurrentIcon=iconByKey[current?.key]||Grid2X2
  const activeTabs=tabs.slice(-6)
  const roleName=ROLE==='Custodian'?'Custodian Ops':'Borrower Desk'
  const searchResultClick=path=>{navigate(path);setCommandOpen(false);setQuery('')}
  const saveWorkspace=()=>{try{sessionStorage.setItem(`slo-${ROLE}-snapshot`,JSON.stringify({tabs,layout,path:pathname}));window.dispatchEvent(new CustomEvent('os:workspace-saved'))}catch{};setLayoutOpen(false)}
  const restoreWorkspace=()=>{try{const s=JSON.parse(sessionStorage.getItem(`slo-${ROLE}-snapshot`)||'null');if(s?.layout)setLayout(s.layout);if(Array.isArray(s?.tabs))setTabs(s.tabs.filter(t=>t&&typeof t.to==='string'&&typeof t.text==='string').slice(-7));if(s?.path)navigate(s.path)}catch{};setLayoutOpen(false)}
  return <div className={`os-shell os-layout-${layout} ${maximized?'os-maximized':''}`}>
    <header className="os-global-header">
      <Link to="/" className="os-brand"><BrandLogo/><span><strong>Securities Lending OS</strong><small>Institutional Lending &amp; Collateral Platform</small></span></Link>
      <div className="os-role-switch" role="group" aria-label="Active application"><span className="os-role-active">{ROLE}</span><a href={otherURL} title={`Open separate ${ROLE==='Custodian'?'Borrower':'Custodian'} application`}>{ROLE==='Custodian'?'Borrower':'Custodian'}</a></div>
      <nav className="os-top-shortcuts" aria-label="Module shortcuts"><Link to="/" className={pathname==='/'?'active':''}><Grid2X2 size={16}/> Workspace</Link>{(ROLE==='Custodian'?['requests','contracts','collateral','settlement','margin']:['requests','contracts','collateral','margin','settlement']).map(k=>{const item=routes.find(r=>r.key===k);return item?<Link key={item.key} to={item.to} className={pathname.startsWith(item.to)&&item.to!=='/'?'active':''}>{item.text}</Link>:null})}</nav>
      <div className="os-header-right"><button className="os-search-trigger" onClick={()=>setCommandOpen(true)} aria-label="Search commands"><Search size={16}/><span>Search or run command...</span><kbd>Ctrl + K</kbd></button><Link className="os-icon-tool" to={userPath} title="Activity and notifications"><Bell size={20}/><i/></Link><div className="os-profile-root"><button className="os-avatar" onClick={()=>setProfileOpen(v=>!v)} aria-expanded={profileOpen}>{ROLE[0]}</button>{profileOpen&&<div className="os-profile-menu"><strong>{profile.data?.username || roleName}</strong><span>{profile.data?.email || (profile.loading ? 'Loading account identity…' : token ? 'Authenticated session' : 'Not signed in')}</span><small className="os-profile-organization">{profile.data?.organizationName || ''}{profile.data?.organizationReference ? ` · ${profile.data.organizationReference}` : ''}</small>{profile.error && <button className="os-profile-refresh" onClick={profile.reload}>Account profile unavailable — retry</button>}<div className="os-dropdown-divider"/><div className="os-profile-controls"><button className={mode==='live'?'selected':''} onClick={()=>setMode('live')}>Live overview</button><button className={mode==='demo'?'selected':''} onClick={()=>setMode('demo')}>Demo overview</button></div><Link to={ROLE==='Custodian'?'/audit':'/custodian-link'} onClick={()=>setProfileOpen(false)}><Settings2 size={15}/> Account &amp; workspace</Link><button onClick={()=>{['tabs','layout','snapshot'].forEach(key=>sessionStorage.removeItem('slo-Borrower-'+key));saveToken('');navigate('/auth')}}><LogOut size={15}/> Sign out</button></div>}</div></div>
    </header>
    <div className="os-tab-strip"><Link to="/" className={`os-workspace-home ${pathname==='/'?'selected':''}`}><Grid2X2 size={16}/> My Workspace</Link><div className="os-tab-scroller">{activeTabs.map(tab=><div className={`os-document-tab ${pathname===tab.to?'selected':''}`} key={tab.to}><button onClick={()=>navigate(tab.to)}>{tab.text}</button><button aria-label={`Close ${tab.text}`} onClick={()=>closeTab(tab.to)}><X size={14}/></button></div>)}<button className="os-add-tab" onClick={()=>setCommandOpen(true)} title="Open module"><Plus size={18}/></button></div><div className="os-workspace-actions"><div className="os-layout-root"><button className="os-plain-button" onClick={()=>setLayoutOpen(v=>!v)}><Grid2X2 size={14}/> Layout <ChevronDown size={14}/></button>{layoutOpen&&<div className="os-layout-menu">{[['balanced','Balanced grid'],['compact','Compact tables'],['focus','Focus mode']].map(([k,v])=><button key={k} onClick={()=>{setLayout(k);setLayoutOpen(false)}}>{layout===k?<Check size={15}/>:<span style={{width:15}}/>}{v}</button>)}<div className="os-dropdown-divider"/><button onClick={restoreWorkspace}><RefreshCw size={15}/> Restore saved view</button></div>}</div><button className="os-save-button" onClick={saveWorkspace}><FolderKanban size={16}/> <span>Save Workspace</span></button></div></div>
    <div className="os-page-caption"><div className="os-page-location"><CurrentIcon size={16}/><span>{ROLE} workspace</span><span className="os-breadcrumb-arrow">/</span><b>{title}</b></div><div className="os-page-tools"><span className="os-service-indicator" title={service.message}><i className={`os-dot-${service.state}`}/> {service.state==='online'?'API connected':service.state==='checking'?'Checking API':'API not connected'}</span><button onClick={checkService} title="Check API status"><RefreshCw size={14}/></button><span className="os-data-label">{pathname==='/' && mode==='demo'?'DEMO OVERVIEW':pathname==='/'?'LIVE OVERVIEW':'BACKEND OPERATIONS'}</span></div></div>
    <main className="os-canvas"><div className={`os-route-window ${minimized?'os-window-minimized':''}`}><div className="os-route-window-header"><div><CurrentIcon size={17}/><strong>{title}</strong><span className="os-window-subtitle">{ROLE} operations</span></div><div className="os-window-controls"><button onClick={()=>setMinimized(v=>!v)} title={minimized?'Restore window':'Minimize window'}><Minimize2 size={15}/></button><button onClick={()=>setMaximized(v=>!v)} title={maximized?'Restore size':'Maximize window'}><Square size={14}/></button><button onClick={()=>navigate('/')} title="Close workspace"><X size={17}/></button></div></div>{!minimized&&<div className="os-window-body"><Outlet/></div>}{minimized&&<div className="os-minimized-placeholder"><Grid2X2 size={25}/> {title} is minimized. <button onClick={()=>setMinimized(false)}>Restore window</button></div>}</div></main>
    <AppDock pathname={pathname} openAll={openAll} setOpenAll={setOpenAll}/><div className="os-workspace-counter"><Grid2X2 size={14}/>{tabs.length||1} open workspace{tabs.length===1?'':'s'}</div>
    <div className="os-system-footer"><span className={`os-connection-dot os-dot-${service.state}`}/><button onClick={checkService}>{ROLE} API: {service.state==='online'?'Connected':'Not connected'}</button><span className="os-footer-separator"/><span>{mode==='demo'?'Overview is illustrative; all operational actions use real APIs':'Operational data uses backend APIs'}</span><span className="os-footer-correlation">Correlation: {correlationId}</span></div>
    <Notice/>
    {commandOpen&&<div className="os-command-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)setCommandOpen(false)}}><div className="os-command-box" role="dialog" aria-modal="true" aria-label="Navigate to application module"><div className="os-command-input"><Search size={21}/><input autoFocus value={query} onChange={e=>setQuery(e.target.value)} placeholder={`Search ${ROLE.toLowerCase()} modules...`}/><kbd>ESC</kbd></div><div className="os-command-list">{results.length?results.map(item=>{const Icon=iconByKey[item.key]||FileText;return <button key={item.to} onClick={()=>searchResultClick(item.to)}><Icon size={17}/><span>{item.text}</span><small>{item.to}</small></button>}):<div className="os-no-results">No matching modules</div>}</div><div className="os-command-foot">Navigation only · Search specific records inside each module · Ctrl+K</div></div></div>}
  </div>
}
