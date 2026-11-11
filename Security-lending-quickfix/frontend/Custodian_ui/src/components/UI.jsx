import { useEffect, useId, useState } from 'react'
import { AlertCircle, ArrowRight, Check, ChevronLeft, ChevronRight, CircleHelp, Database, LoaderCircle, RefreshCw, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { display, statusTone, titleCase } from '../utils/format'
import { useApp } from '../hooks/useApp'

/** Panel: warm, bordered container used throughout the operations workspace. */
export function Panel({ title, icon: Icon, actions, children, className = '', subtitle }) {
  return <section className={`panel ${className}`}><div className="panel__heading"><div className="panel__title-group"><h2>{Icon && <Icon size={17} strokeWidth={2.5} />} {title}</h2>{subtitle && <p>{subtitle}</p>}</div><div className="panel__actions">{actions}</div></div><div className="panel__content">{children}</div></section>
}
/** DemoTag: explicit provenance disclosure for API-unavailable visuals. */
export function DemoTag() { return <span className="demo-tag" title="Illustrative screenshot-matched data. No backend endpoint exists for this aggregate."><Database size={11} /> Demo data – API pending</span> }
/** StatusBadge: render backend status verbatim, never infer a status from HTTP success. */
export function StatusBadge({ value, tone }) { return <span className={`status-badge status-badge--${tone || statusTone(value)}`}>{String(value || 'Unavailable').replace(/_/g, ' ')}</span> }
/** Button: consistent action styling with in-flight disabled state. */
export function Button({ children, icon: Icon, variant = 'primary', busy = false, disabled = false, type = 'button', className = '', ...props }) { return <button {...props} type={type} className={`btn btn--${variant} ${className}`} disabled={busy || disabled}>{busy ? <LoaderCircle className="spin" size={15} /> : Icon && <Icon size={15} />}{children}</button> }
/** EmptyState: explains missing auth, missing API, and empty real result without fabricating rows. */
export function EmptyState({ title = 'No records to display', text = 'No matching records were returned by the Custodian service.', icon: Icon = Database, action }) { return <div className="empty-state"><Icon size={26} /><strong>{title}</strong><p>{text}</p>{action}</div> }
/** ApiState: explicit loading/error/no-token messaging for server-backed modules. */
export function ApiState({ loading, error, token, children, reload, empty, emptyText }) {
  if (!token) return <EmptyState icon={CircleHelp} title="Sign in required" text="Sign in to your Custodian account to load live records." action={<Link className="btn btn--outline" to="/auth">Sign in <ArrowRight size={14} /></Link>} />
  if (loading) return <div className="loading-state"><LoaderCircle className="spin" size={20} /> Loading authoritative service data…</div>
  if (error) return <div role="alert" className="api-error"><AlertCircle size={19} /><div><strong>{error.status ? `HTTP ${error.status}` : 'Connection error'}</strong><p>{error.message}</p>{error.correlationId && <small>Correlation: {error.correlationId}</small>}</div>{reload && <Button onClick={reload} icon={RefreshCw} variant="outline">Refresh</Button>}</div>
  if (empty) return <EmptyState text={emptyText} />
  return children
}
/** PageHeading: structured title, guidance and top-right toolbar. */
export function PageHeading({ eyebrow = 'CUSTODIAN OPERATIONS', title, subtitle, action, tags }) { return <div className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{subtitle}</p>{tags && <div className="page-heading__tags">{tags}</div>}</div><div className="page-heading__actions">{action}</div></div> }
/** KpiStrip: quick at-a-glance metrics with explicit source labels. */
export function KpiStrip({ items }) { return <div className="kpi-strip">{items.map((item, index) => <div className="kpi-strip__item" key={index}><span>{item.label}</span><strong className="tabular">{item.value ?? '—'}</strong><small>{item.detail || 'From the current workflow'}</small></div>)}</div> }
/** SideGuide: contextual controls, business rules and FIX notes. */
export function SideGuide({ title = 'Operator guidance', items = [], fix, note }) { return <aside className="side-guide"><h3><CircleHelp size={16} /> {title}</h3><ol>{items.map((item, i) => <li key={i}>{item}</li>)}</ol>{fix && <div className="side-guide__fix"><span>RELATED FIX / WORKFLOW</span><strong>{fix}</strong></div>}{note && <p className="side-guide__note">{note}</p>}</aside> }
/** FooterNote: persistent operational caution at the foot of every page. */
export function FooterNote({ children }) { return <p className="footer-note"><AlertCircle size={14} /> {children || 'All balances, contract states and eligibility checks are authoritative only when returned by the Custodian service. Workflow updates may arrive asynchronously.'}</p> }
/** DataTable: narrow, scrollable dense institutional table. */
export function DataTable({ columns, rows, onRow, emptyLabel = 'No rows returned', actions, keyField }) { return <div className="table-scroll"><table className="data-table"><thead><tr>{columns.map((col) => <th key={col.key}>{col.label}</th>)}{actions && <th>Actions</th>}</tr></thead><tbody>{rows?.length ? rows.map((item, index) => <tr key={keyField ? item[keyField] || index : index} className={onRow ? 'data-table__interactive' : ''} onClick={onRow ? () => onRow(item) : undefined}>{columns.map(col => <td key={col.key} className={col.className || ''}>{col.render ? col.render(item[col.key], item) : (col.key.toLowerCase().includes('status') ? <StatusBadge value={item[col.key]} /> : display(item[col.key], col.key))}</td>)}{actions && <td onClick={event => event.stopPropagation()}>{actions(item)}</td>}</tr>) : <tr><td className="data-table__empty" colSpan={columns.length + (actions ? 1 : 0)}>{emptyLabel}</td></tr>}</tbody></table></div> }
/** Pagination: respects the backend's zero-based PageResponse page number. */
export function Pagination({ page = 0, totalPages = 0, totalElements = 0, size = 20, onPage }) { return <div className="pagination"><span>{totalElements} records · page {page + 1} of {Math.max(totalPages, 1)} · {size} per page</span><div><Button variant="outline" icon={ChevronLeft} disabled={page <= 0} onClick={() => onPage(page - 1)}>Previous</Button><Button variant="outline" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>Next <ChevronRight size={13} /></Button></div></div> }
/** Field: accessible controlled input, schema-specific validation and helper text. */
export function Field({ name, label, value, onChange, type = 'text', options, required, readOnly, hint, min, max, minLength, step, placeholder, pattern, multiline, disabled }) {
  const id = useId()
  const props = { id, name, required, readOnly, disabled, value: value ?? '', onChange: e => onChange?.(e.target.value), placeholder, min, max, step, pattern, minLength, maxLength: type === 'text' || multiline ? max : undefined }
  return <div className="field"><label htmlFor={id}>{label || titleCase(name)} {required && <sup>*</sup>}</label>{options ? <select {...props}><option value="">Choose {label || titleCase(name)}</option>{options.map(o => <option value={typeof o === 'string' ? o : o.value} key={typeof o === 'string' ? o : o.value}>{typeof o === 'string' ? o.replace(/_/g, ' ') : o.label}</option>)}</select> : multiline ? <textarea {...props} rows={3} /> : <input {...props} type={type} />}{hint && <small>{hint}</small>}</div>
}
/** DetailGrid: displays returned DTO values without altering server ownership. */
export function DetailGrid({ data, fields }) { if (!data) return null; const entries = (fields || Object.keys(data)).map(f => [f, data[f]]); return <dl className="detail-grid">{entries.map(([key, value]) => <div key={key}><dt>{titleCase(key)}</dt><dd>{/status$/i.test(key) ? <StatusBadge value={value} /> : display(value, key)}</dd></div>)}</dl> }
/** Notice: short-lived user feedback with explicit close control. */
export function Notice() { const { notice, setNotice } = useApp(); if (!notice) return null; return <div role="status" className={`toast toast--${notice.tone}`}><span>{notice.message}</span><button type="button" aria-label="Dismiss notification" onClick={() => setNotice(null)}><X size={17} /></button></div> }
/** ConfirmDialog: uses mandatory explicit operator approval, never silently retries. */
export function ConfirmDialog({ config, onClose, onCompleted }) {
  const [values, setValues] = useState(() => Object.fromEntries((config?.fields || []).map(f => [f.name, f.defaultValue || ''])))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  useEffect(() => { const handle = (e) => { if (e.key === 'Escape' && !busy) onClose() }; window.addEventListener('keydown', handle); return () => window.removeEventListener('keydown', handle) }, [busy, onClose])
  if (!config) return null
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(null)
    try { const result = await config.execute(values); onCompleted?.(result); onClose() }
    catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }
  return <div className="modal-backdrop" role="presentation"><div className="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title"><div className="modal__heading"><div><span className="eyebrow">EXPLICIT CONFIRMATION</span><h2 id="confirm-title">{config.title}</h2></div><button type="button" className="icon-button" aria-label="Close dialog" disabled={busy} onClick={onClose}><X size={18} /></button></div><form onSubmit={submit}><div className="modal__body">{config.simulation && <div className="simulation-banner">SIMULATION – demo only · This is not proof of external settlement.</div>}<p>{config.message}</p>{config.subject && <div className="modal__subject">{config.subject}</div>}<div className="form-grid">{config.fields?.map(f => <Field key={f.name} {...f} value={values[f.name]} onChange={value => setValues(v => ({ ...v, [f.name]: value }))} />)}</div>{error && <p className="form-error" role="alert">{error}</p>}</div><div className="modal__footer"><Button variant="outline" onClick={onClose} disabled={busy}>Cancel</Button><Button type="submit" busy={busy} variant={config.danger ? 'danger' : 'primary'} icon={Check}>{config.confirmText || 'Confirm action'}</Button></div></form></div></div>
}
/** Lifecycle: the fixed business journey, current stage server-backed when possible. */
export function Lifecycle({ current = 'ON_LOAN', demo = false }) {
  const stages = [{ key: 'APPROVED', label: 'Approved' }, { key: 'PENDING_COLLATERAL', label: 'Collateral Posted' }, { key: 'PENDING_SETTLEMENT', label: 'Securities Delivered' }, { key: 'ON_LOAN', label: 'On Loan' }, { key: 'RETURNED', label: 'Return' }, { key: 'RELEASED', label: 'Final Release' }, { key: 'CLOSED', label: 'Closed' }]
  const aliases = { SETTLING: 2, PARTIALLY_SETTLED: 2, RECALLED: 3, RETURN_REQUESTED: 4, SETTLEMENT_FAILED: 2 }
  const position = stages.findIndex(s => s.key === current)
  const idx = position >= 0 ? position : aliases[current] ?? -1
  return <div className="lifecycle"><div className="lifecycle__heading"><h2>Contract Lifecycle {demo && <DemoTag />}</h2><span>Step {idx >= 0 ? idx + 1 : '—'} of 7</span></div><div className="lifecycle__track">{stages.map((stage, i) => <div key={stage.key} className={`lifecycle__step ${idx > i ? 'lifecycle__step--complete' : ''} ${idx === i ? 'lifecycle__step--current' : ''}`}><span className="lifecycle__node">{idx > i ? <Check size={14} /> : <span className="lifecycle__node-dot" />}</span><strong>{stage.label}</strong><small>{demo && i < 4 ? ['11 Feb', '11 Feb', '12 Feb', '12 Feb'][i] : '—'}</small></div>)}</div></div>
}
