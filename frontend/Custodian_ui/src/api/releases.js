/** Releases Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { get, post, ct, e } from './shared'
export const releases = { queue: () => get('/api/collateral-releases/requests'), approve: (t, r) => post(`${ct(t)}/collateral-releases/${e(r)}/approve`) }
