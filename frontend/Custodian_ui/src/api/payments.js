/** Payments Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { get, post, ct, e, request, idempotencyKey } from './shared'
export const payments = { list: (t) => get(`${ct(t)}/payments`), charges: (t) => get(`${ct(t)}/charges`), confirm: (t, p, b) => post(`${ct(t)}/payments/${e(p)}/confirm`, b), fail: (t, p, b) => post(`${ct(t)}/payments/${e(p)}/fail`, b), accrue: (t, from, to, key = idempotencyKey()) => request(`${ct(t)}/accruals`, { method: 'POST', params: { from, to }, headers: { 'Idempotency-Key': key } }) }
