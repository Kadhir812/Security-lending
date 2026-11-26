/** Public barrel for per-resource service modules. Every HTTP call uses client.js. */
import { get, post, list } from './shared'
export { foundation } from './foundation'
export { loanRequests } from './loans'
export { organizations, mandates, inventory, mslas, creditLimits, haircuts, lendingFees, securityReferences, lenderCashAccounts } from './setup'
export { settlements } from './settlement'
export { marginCalls } from './margin'
export { recalls } from './recalls'
export { payments } from './payments'
export { calculationSchedule } from './calculations'
export { releases } from './releases'
export { locates } from './locates'
export { closure } from './closure'
export const auth = {
  signup: (body) => post('/api/auth/signup', body),
  login: (body) => post('/api/auth/login', body),
  me: () => get('/api/auth/me'),
}
export const collateralInquiries = {
  list: (page = 0, size = 20) => list('/api/collateral-inquiries', page, size),
}
