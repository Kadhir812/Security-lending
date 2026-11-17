/** Loans Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { get, post, list, e } from './shared'
export const loanRequests = {
  list: (p, s) => list('/api/loan-requests', p, s),
  get: (ref) => get(`/api/loan-requests/${e(ref)}`),
  approve: (ref) => post(`/api/loan-requests/${e(ref)}/approve`),
  reject: (ref, reason) => post(`/api/loan-requests/${e(ref)}/reject`, { reason }),
}
