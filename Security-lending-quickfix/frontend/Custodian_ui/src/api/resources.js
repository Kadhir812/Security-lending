/** Public barrel for per-resource service modules. Every HTTP call uses client.js. */
import { post } from './shared'
export { foundation } from './foundation'
export { loanRequests } from './loans'
export { organizations, mandates, inventory, mslas, creditLimits, haircuts, lendingFees } from './setup'
export { settlements } from './settlement'
export { marginCalls } from './margin'
export { recalls } from './recalls'
export { payments } from './payments'
export { releases } from './releases'
export { closure } from './closure'
export const auth = {
  signup: (body) => post('/api/auth/signup', body),
  login: (body) => post('/api/auth/login', body),
}
