/** Setup Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { get, post, put, del, list, e, ms } from './shared'
export const organizations = { list: (p, s) => list('/api/organizations', p, s), borrowers: (p, s) => list('/api/organizations/borrowers', p, s), activeMslaCount: (r) => get(`/api/organizations/borrowers/${e(r)}/active-msla-count`), get: (r) => get(`/api/organizations/${e(r)}`), create: (b) => post('/api/organizations', b), update: (r, b) => put(`/api/organizations/${e(r)}`, b) }
export const mandates = { list: (p, s) => list('/api/mandates', p, s), get: (r) => get(`/api/mandates/${e(r)}`), create: (b) => post('/api/mandates', b), update: (r, b) => put(`/api/mandates/${e(r)}`, b) }
export const inventory = { list: (p, s) => list('/api/inventory', p, s), get: (l, i) => get(`/api/inventory/${e(l)}/${e(i)}`), create: (b) => post('/api/inventory', b), update: (l, i, b) => put(`/api/inventory/${e(l)}/${e(i)}`, b), remove: (l, i, version) => del(`/api/inventory/${e(l)}/${e(i)}`, { version }) }
export const mslas = { list: (p, s) => list('/api/mslas', p, s), get: (r) => get(ms(r)), create: (b) => post('/api/mslas', b), createSetup: (b) => post('/api/mslas/setup', b), update: (r, b) => put(ms(r), b) }
export const creditLimits = { list: (p, s) => list('/api/credit-limits', p, s), get: (r) => get(`/api/credit-limits/${e(r)}`), history: (r) => get(`/api/credit-limits/${e(r)}/history`), create: (r, b) => post(`/api/credit-limits/${e(r)}`, b), update: (r, b) => put(`/api/credit-limits/${e(r)}`, b) }
export const haircuts = { list: (r) => get(`${ms(r)}/collateral-haircuts`), get: (r, id) => get(`${ms(r)}/collateral-haircuts/${e(id)}`), create: (r, b) => post(`${ms(r)}/collateral-haircuts`, b), update: (r, id, b) => put(`${ms(r)}/collateral-haircuts/${e(id)}`, b) }
export const lendingFees = { list: (r) => get(`${ms(r)}/lending-fees`), get: (r, id) => get(`${ms(r)}/lending-fees/${e(id)}`), create: (r, b) => post(`${ms(r)}/lending-fees`, b), update: (r, id, b) => put(`${ms(r)}/lending-fees/${e(id)}`, b), effective: (r, securityIsin, businessDate) => get(`${ms(r)}/lending-fees/effective`, { securityIsin, businessDate }) }
export const securityReferences = { list: () => get('/api/reference/securities'), latest: isin => get(`/api/reference/securities/${e(isin)}/prices/latest`), history: (isin, from, to) => get(`/api/reference/securities/${e(isin)}/prices`, { from, to }) }
export const lenderCashAccounts = {
  get: (lender, currency) => get(`/api/lenders/${e(lender)}/cash-accounts/${e(currency)}`),
  history: (lender, currency) => get(`/api/lenders/${e(lender)}/cash-accounts/${e(currency)}/transactions`),
  deposit: (lender, currency, body, key) => post(`/api/lenders/${e(lender)}/cash-accounts/${e(currency)}/deposits`, body, { 'Idempotency-Key': key }),
  withdraw: (lender, currency, body, key) => post(`/api/lenders/${e(lender)}/cash-accounts/${e(currency)}/withdrawals`, body, { 'Idempotency-Key': key }),
}
