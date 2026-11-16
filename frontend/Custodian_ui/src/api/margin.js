/** Margin Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { get, post, ct, e, idempotencyKey } from './shared'
export const marginCalls = { list: (t) => get(`${ct(t)}/margin-calls`), get: (t, r) => get(`${ct(t)}/margin-calls/${e(r)}`), evaluate: (t) => post(`${ct(t)}/margin-calls/evaluate`), mtm: (t, key = idempotencyKey()) => post(`${ct(t)}/margin-calls/mtm`, undefined, { 'Idempotency-Key': key }) }
