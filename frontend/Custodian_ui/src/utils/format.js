/** Presentation only: never compute server-owned valuations. */
const referenceAliases = new Map()
const nextReferenceAlias = new Map([['LOAN', 1], ['CTR', 4]])
const fieldAliasKinds = {
  requestReference: 'LOAN', transactionReference: 'CTR', contractReference: 'CTR',
  borrowerOrganizationReference: 'BORROWER', lenderOrganizationReference: 'LENDER', custodianOrganizationReference: 'CUSTODIAN',
  calculationResultReference: 'CALC', closureReference: 'CLOSE', confirmationReference: 'CONF', externalConfirmationReference: 'CONF',
  instructionReference: 'INSTR', locateReference: 'LOCATE', recallReference: 'RECALL', paymentReference: 'PAY',
  marginCallReference: 'MTM', releaseReference: 'RELEASE', resultReference: 'RESULT', sourceReference: 'SOURCE',
  accountReference: 'ACCOUNT', externalReference: 'EXT', mandateReference: 'MANDATE', existingMandateReference: 'MANDATE',
  mslaReference: 'MSLA', organizationReference: 'ORG', lenderReference: 'LENDER', securityReference: 'SEC',
}
const prefixAliasKinds = {
  LR: 'LOAN', LOAN: 'LOAN', TX: 'CTR', CTR: 'CTR', CONTRACT: 'CTR', TRANSACTION: 'CTR',
  CALCULATIONRESULT: 'CALC', CALC: 'CALC', CLOSURE: 'CLOSE', CLOSE: 'CLOSE', CONFIRMATION: 'CONF', CONF: 'CONF',
  INSTRUCTION: 'INSTR', SETTLEMENTINSTRUCTION: 'INSTR', INSTR: 'INSTR', PAYMENT: 'PAY', FEEPAYMENT: 'PAY', PAY: 'PAY',
  MARGINCALL: 'MTM', MARGIN: 'MTM', MTM: 'MTM', ORGANIZATION: 'ORG', ORG: 'ORG', SECURITY: 'SEC', SEC: 'SEC',
  SETTLEMENT: 'SETTLE', SETTLE: 'SETTLE', COLLATERAL: 'COLL', COLL: 'COLL', INVENTORY: 'INV', INV: 'INV',
  FEE: 'FEE', HAIRCUT: 'HAIRCUT', SCHEDULE: 'SCHED', EVENT: 'EVENT', MESSAGE: 'MSG', OUTBOX: 'OUTBOX',
  BORROWER: 'BORROWER', LENDER: 'LENDER', CUSTODIAN: 'CUSTODIAN', LOCATE: 'LOCATE', RECALL: 'RECALL',
  RELEASE: 'RELEASE', MSLA: 'MSLA', MANDATE: 'MANDATE', ACCOUNT: 'ACCOUNT', RESULT: 'RESULT', SOURCE: 'SOURCE',
}
function referenceAlias(value, key = '') {
  if (typeof value !== 'string') return value
  if (/^[A-Z]+-\d{3,}$/i.test(value)) return value
  const fieldKind = fieldAliasKinds[key] || (key.endsWith('Reference') ? key.replace(/Reference$/, '').replace(/([a-z])([A-Z])/g, '$1 $2').split(' ')[0].toUpperCase().slice(0, 6) : '')
  const opaque = value.match(/^([A-Z][A-Z0-9_-]*)-([\da-f-]{16,})$/i)
  const bareUuid = /^[\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12}$|^[\da-f]{32}$/i.test(value)
  const legacyId = /^(LR|TX)-[A-Z0-9-]+$/i.test(value)
  const legacyKind = /^LR-/i.test(value) ? 'LOAN' : /^TX-/i.test(value) ? 'CTR' : ''
  if (!opaque && !bareUuid && !legacyId) return value
  const sourcePrefix = opaque?.[1]?.toUpperCase()
  const normalizedPrefix = sourcePrefix?.replace(/[^A-Z0-9]/g, '')
  const kind = fieldKind || legacyKind || prefixAliasKinds[normalizedPrefix] || normalizedPrefix?.slice(0, 8) || 'REF'
  if (!referenceAliases.has(value)) {
    let alias = ''
    try { alias = JSON.parse(sessionStorage.getItem('custodian-readable-references-v2') || '{}')[value] || '' } catch { /* session storage unavailable */ }
    if (!alias) {
      const next = nextReferenceAlias.get(kind) || 1
      alias = `${kind}-${String(next).padStart(3, '0')}`
      nextReferenceAlias.set(kind, next + 1)
      try {
        const stored = JSON.parse(sessionStorage.getItem('custodian-readable-references-v2') || '{}')
        stored[value] = alias
        sessionStorage.setItem('custodian-readable-references-v2', JSON.stringify(stored))
      } catch { /* session storage unavailable */ }
    }
    const storedNumber = Number(alias.match(/-(\d+)$/)?.[1])
    if (Number.isFinite(storedNumber)) nextReferenceAlias.set(kind, Math.max(nextReferenceAlias.get(kind) || 1, storedNumber + 1))
    referenceAliases.set(value, alias)
  }
  return referenceAliases.get(value)
}
export const referenceLabel = referenceAlias
export const displayReferenceText = value => value === null || value === undefined ? value : String(value).replace(/\b(?:(?:[A-Z][A-Z0-9_-]*-)?(?:[\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12}|[\da-f]{32})|(?:LR|TX)-[A-Z0-9-]+)\b/gi, reference => referenceAlias(reference))
export const number = (value, locale = 'en-IN') => value === undefined || value === null || value === '' ? '—' : new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(Number(value))
export const money = (value, currency = 'INR') => value === undefined || value === null || value === '' ? '—' : new Intl.NumberFormat('en-IN', { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value))
export const crores = (value) => `${money(value)} Cr`
export const stamp = (value) => { if (!value) return '—'; const date = new Date(value); return Number.isNaN(date.valueOf()) ? String(value) : new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Kolkata' }).format(date) }
export const display = (v, key = '') => {
  if (v === null || v === undefined || v === '') return '—'
  if (typeof v === 'boolean') return v ? 'Yes' : 'No'
  if (typeof v === 'object') return JSON.stringify(v)
  if (/Reference$/i.test(key) || /^[A-Z][A-Z0-9_-]*-[\da-f-]{16,}$/i.test(String(v)) || /^[\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12}$|^[\da-f]{32}$/i.test(String(v))) return referenceAlias(String(v), key)
  if (/At$|Timestamp$/.test(key)) return stamp(v)
  if (typeof v === 'number' && /(Amount|Value|Limit|Collateral|Balance|Exposure|Fee)/i.test(key) && !/(Pct|Percentage)/.test(key)) return money(v)
  if (typeof v === 'number') return number(v)
  return displayReferenceText(v)
}
export const titleCase = (value) => String(value).replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').replace(/\b\w/g, m => m.toUpperCase())
export const statusTone = (s) => { const t = String(s || '').toUpperCase(); return /^(ACTIVE|APPROVED|ON_LOAN|CONFIRMED|SETTLED|SATISFIED|CLOSED|PUBLISHED|RELEASED)$/.test(t) ? 'success' : /(FAIL|REJECT|OVERDUE|BREACH|DEFAULT|EXPIRED)/.test(t) ? 'danger' : /(PENDING|PROCESSING|OPEN|DRAFT|REVIEW|IN_PROGRESS|SUBMITTED)/.test(t) ? 'warning' : 'info' }
