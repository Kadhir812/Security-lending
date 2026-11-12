import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowRight, CheckCircle2, FilePlus2, History, Pencil, RefreshCw, RotateCcw, Search } from 'lucide-react'
import * as api from '../api/resources'
import { useApp } from '../hooks/useApp'
import { useRemote } from '../hooks/useRemote'
import { ApiState, Button, DataTable, DetailGrid, Field, FooterNote, KpiStrip, PageHeading, Pagination, Panel, SideGuide, StatusBadge } from '../components/UI'
import { schemas } from './resourceSchemas'
import { display, titleCase } from '../utils/format'
const dateTimeLocal = (v) => { if (!v) return ''; const d = new Date(v); const p = n => String(n).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}` }
const prepared = (field, value) => {
  if (value === null || value === undefined) return ''
  if (field === 'eligibleSecurities') return (value.allowed_types || value.allowedTypes || []).join(', ')
  if (field === 'defaultRules') return typeof value === 'object' ? JSON.stringify(value, null, 2) : value
  return String(value)
}
const required = (val) => val !== undefined && val !== null && String(val).trim() !== ''
function prepareBody(config, values, editing, scope) {
  const body = {}
  for (const field of config.fields) {
    if (field.updateOnly && !editing) continue
    let value = values[field.name]
    if (!required(value)) { if (field.required) throw new Error(`${field.label || titleCase(field.name)} is required.`); continue }
    if (field.name === 'eligibleSecurities') {
      const allowed_types = String(value).split(',').map(v => v.trim().toUpperCase()).filter(Boolean)
      const allowed = ['EQUITY','CORPORATE_BOND','GOVT_BOND','ETF']
      if (!allowed_types.length || allowed_types.some(v => !allowed.includes(v))) throw new Error('Eligible securities must list supported AssetType values.')
      value = { allowed_types }
    } else if (field.name === 'defaultRules') { try { value = JSON.parse(value) } catch { throw new Error('defaultRules must contain valid JSON.') }; if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('defaultRules must be a JSON object.') }
    else if (field.type === 'number') { value = Number(value); if (!Number.isFinite(value) || (field.min !== undefined && value < field.min) || (field.step === 1 && !Number.isInteger(value))) throw new Error(`Invalid number for ${field.name}.`) }
    else if (field.type === 'datetime-local') value = new Date(value).toISOString()
    body[field.name] = value
  }
  if (config.resource === 'creditLimits') delete body.mslaReference
  if (config.resource === 'inventory' && editing) { delete body.lenderOrganizationReference; delete body.securityIsin }
  if (editing && values.version !== undefined && values.version !== null) body.version = Number(values.version)
  if (config.scope && !scope) throw new Error('Choose an existing MSLA reference before editing this rule.')
  return body
}
/** ResourcePage: fully routed CRUD for Custodian setup, version-safe updates and history. */
export default function ResourcePage({ name }) {
  const config = schemas[name], resource = api[config.resource]
  const { token, notify } = useApp()
  const [page, setPage] = useState(0)
  const [scope, setScope] = useState('')
  const [draftScope, setDraftScope] = useState('')
  const [editing, setEditing] = useState(null)
  const [values, setValues] = useState({})
  const [saving, setSaving] = useState(false)
  const [errorForm, setErrorForm] = useState('')
  const [response, setResponse] = useState(null)
  const [history, setHistory] = useState(null)
  const [historyError, setHistoryError] = useState('')
  const { data, loading, error, reload } = useRemote(() => config.scope ? resource.list(scope) : resource.list(page, 20), [page, scope, name], { enabled: !config.scope || !!scope })
  const entries = Array.isArray(data) ? data : data?.content || []
  const columnConfig = useMemo(() => config.columns.map(key => ({ key, label: titleCase(key), render: (value, record) => /(status)$/i.test(key) ? <StatusBadge value={value}/> : display(value, key) })), [config])
  function choose(row) { setEditing(row); setValues(Object.fromEntries([...config.fields.map(field => [field.name, field.type === 'datetime-local' ? dateTimeLocal(row[field.name]) : prepared(field.name, row[field.name])]), ['version', row.version]])); setErrorForm(''); setResponse(null); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  function reset() { setEditing(null); setValues({}); setErrorForm(''); setResponse(null) }
  async function save(event) {
    event.preventDefault(); setSaving(true); setErrorForm(''); setResponse(null)
    try {
      const body = prepareBody(config, values, editing, scope)
      let result
      if (editing) {
        if (config.resource === 'inventory') result = await resource.update(editing.lenderOrganizationReference, editing.securityIsin, body)
        else if (config.scope) result = await resource.update(scope, editing[config.key], body)
        else result = await resource.update(editing[config.key], body)
      } else if (config.scope) result = await resource.create(scope, body)
      else if (config.resource === 'creditLimits') result = await resource.create(values.mslaReference, body)
      else result = await resource.create(body)
      reset(); setResponse(result); notify(`${editing ? 'Updated' : 'Created'} successfully; inspect the returned status and version.`, 'success'); reload()
    } catch (e) { setErrorForm(e.message); if (e.status === 409) reload() }
    finally { setSaving(false) }
  }
  async function showHistory(row) { setHistory(null); setHistoryError(''); try { setHistory({ ref: row.mslaReference, data: await api.creditLimits.history(row.mslaReference) }) } catch(e) { setHistoryError(e.message) } }
  const scopedMissing = !!config.scope && !scope
  return <div className="resource-page"><PageHeading eyebrow={config.category.toUpperCase()} title={config.title} subtitle={config.subtitle} action={<Button icon={RefreshCw} variant="outline" onClick={reload}>Refresh records</Button>}/><KpiStrip items={[{label:'Records on this page',value: !token||scopedMissing?'—': loading?'…':entries.length,detail:'Live service response only'},{label:'Total records',value:!token||scopedMissing?'—': Array.isArray(data)?entries.length:data?.totalElements??'—',detail:'Backend-derived, not estimated'},{label:'Record selected',value:editing ? editing[config.key] : 'None',detail:editing?'Optimistic version loaded':'Choose Edit in table'},{label:'Data source',value:'LIVE API',detail:'No synthetic business records'}]}/><div className="work-area"><div className="work-area__main"><Panel title={editing ? `Edit ${config.title}` : `Create ${config.title}`} subtitle={editing ? 'The record version is submitted to protect concurrent updates.' : 'Only submit validated, authorized business references.'} actions={<Button icon={RotateCcw} variant="ghost" onClick={reset}>Reset</Button>}><form onSubmit={save} className="resource-form">{config.scope && <div className="resource-form__scope"><Field name="scopeMsla" label="Agreement scope (MSLA reference)" value={draftScope} onChange={setDraftScope} required hint="Fetch service records for an existing authorized MSLA."/><Button onClick={() => { setScope(draftScope.trim()); setPage(0); reset() }} icon={Search} disabled={!draftScope.trim()}>Open agreement</Button></div>}<div className="form-grid">{config.fields.filter(field => !field.updateOnly || editing).map(field => <Field key={field.name} {...field} readOnly={!!editing && ((field.name === config.key) || (config.resource === 'inventory' && ['lenderOrganizationReference','securityIsin'].includes(field.name)) || (config.resource === 'creditLimits' && field.name === 'mslaReference'))} value={values[field.name]} onChange={value => setValues(v => ({...v, [field.name]: value}))} />)}</div>{editing && <div className="version-alert">Updating server version <strong>{editing.version ?? 'unavailable'}</strong>. Conflicts return HTTP 409; refresh before resubmitting.</div>}{errorForm && <p className="form-error" role="alert">{errorForm}</p>}{response && <div className="form-success"><CheckCircle2 size={17}/> Updated state returned by API: {JSON.stringify(response)}</div>}<div className="form-actions"><Button type="submit" busy={saving} icon={editing ? Pencil : FilePlus2} disabled={!token || (config.scope && !scope)}>{editing?'Save versioned update':'Create record'}</Button><span>All required fields are marked *</span></div></form></Panel>
    <Panel title={`${config.title} · live records`} actions={token&&<Button icon={RefreshCw} variant="ghost" onClick={reload}>Reload</Button>}>{scopedMissing ? <div className="empty-inline">Enter a valid MSLA scope above and choose “Open agreement” to load haircut or fee rules.</div> : <ApiState token={token} loading={loading} error={error} reload={reload}><DataTable columns={columnConfig} rows={entries} keyField={config.key} emptyLabel="No records returned for this authorized scope." actions={row=><div className="table-actions"><Button variant="outline" icon={Pencil} onClick={()=>choose(row)}>Edit</Button>{name==='credit'&&<Button variant="ghost" icon={History} onClick={()=>showHistory(row)}>History</Button>}</div>}/>{!Array.isArray(data)&&<Pagination page={data?.page??page} size={data?.size??20} totalPages={data?.totalPages??0} totalElements={data?.totalElements??0} onPage={setPage}/>}</ApiState>}</Panel>
    {name==='credit'&&(history||historyError)&&<Panel title="Credit Limit History" actions={<Button onClick={()=>{setHistory(null);setHistoryError('')}} variant="ghost">Dismiss</Button>}>{historyError&&<p className="form-error">{historyError}</p>}{history&&<DataTable rows={Array.isArray(history.data)?history.data:history.data?.content||[]} columns={['oldLimit','newLimit','reason','changedBy','changedAt'].map(key=>({key,label:titleCase(key)}))}/>}</Panel>}
    </div><div className="work-area__side"><SideGuide items={config.guidance} fix={config.fix} note={config.note}/><div className="side-links"><strong>Related workspaces</strong>{[{to:'/organizations',label:'Organizations'},{to:'/mandates',label:'Mandates'},{to:'/mslas',label:'MSLA agreements'},{to:'/requests',label:'Pending borrow queue'}].filter(x=>x.to!==`/${name}`).map(x=><Link to={x.to} key={x.to}>{x.label}<ArrowRight size={14}/></Link>)}</div></div></div><FooterNote>Server-owned versions, business statuses and calculations are never overwritten in the browser. If you receive HTTP 409, reload and review the latest record before making another change.</FooterNote></div>
}
