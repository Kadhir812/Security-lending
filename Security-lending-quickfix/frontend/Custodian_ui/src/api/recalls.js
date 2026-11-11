/** Recalls Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { get, post, ct, e } from './shared'
export const recalls = { list: (t) => get(`${ct(t)}/recalls`), get: (t, r) => get(`${ct(t)}/recalls/${e(r)}`), create: (t, b) => post(`${ct(t)}/recalls`, b) }
