/** Presentation only: never compute server-owned valuations. */
export const number = (value, locale = 'en-IN') => value === undefined || value === null || value === '' ? '—' : new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(Number(value))
export const money = (value, currency = 'INR') => value === undefined || value === null || value === '' ? '—' : new Intl.NumberFormat('en-IN', { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value))
export const crores = (value) => `${money(value)} Cr`
export const stamp = (value) => { if (!value) return '—'; const date = new Date(value); return Number.isNaN(date.valueOf()) ? String(value) : new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Kolkata' }).format(date) }
export const display = (v, key = '') => {
  if (v === null || v === undefined || v === '') return '—'
  if (typeof v === 'boolean') return v ? 'Yes' : 'No'
  if (typeof v === 'object') return JSON.stringify(v)
  if (/At$|Timestamp$/.test(key)) return stamp(v)
  if (typeof v === 'number' && /(Amount|Value|Limit|Collateral|Balance|Exposure|Fee)/i.test(key) && !/(Pct|Percentage)/.test(key)) return money(v)
  if (typeof v === 'number') return number(v)
  return String(v)
}
export const titleCase = (value) => String(value).replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').replace(/\b\w/g, m => m.toUpperCase())
export const statusTone = (s) => { const t = String(s || '').toUpperCase(); return /^(ACTIVE|APPROVED|ON_LOAN|CONFIRMED|SETTLED|SATISFIED|CLOSED|PUBLISHED|RELEASED)$/.test(t) ? 'success' : /(FAIL|REJECT|OVERDUE|BREACH|DEFAULT|EXPIRED)/.test(t) ? 'danger' : /(PENDING|PROCESSING|OPEN|DRAFT|REVIEW|IN_PROGRESS|SUBMITTED)/.test(t) ? 'warning' : 'info' }
