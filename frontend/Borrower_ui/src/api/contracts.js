import { request } from './client'
const base = ref => `/contracts/${encodeURIComponent(ref)}`
/** Scoped live borrower contract projection and its action-specific children. */
export const contracts = {
  settlement: ref => request.get(`${base(ref)}/settlement`),
  marginCall: (ref, call) => request.get(`${base(ref)}/margin-calls/${encodeURIComponent(call)}`),
  topUps: (ref, call) => request.get(`${base(ref)}/margin-calls/${encodeURIComponent(call)}/top-ups`),
  topUp: (ref, call, body) => request.post(`${base(ref)}/margin-calls/${encodeURIComponent(call)}/top-ups`, body),
  returnProgress: ref => request.get(`${base(ref)}/returns`),
  submitReturn: (ref, body) => request.post(`${base(ref)}/returns`, body),
  charges: ref => request.get(`${base(ref)}/charges`),
  payments: ref => request.get(`${base(ref)}/payments`),
  submitPayment: (ref, body, paymentReference) => request.post(`${base(ref)}/payments`, body, paymentReference),
  releases: ref => request.get(`${base(ref)}/collateral-releases`),
  requestRelease: (ref, body) => request.post(`${base(ref)}/collateral-releases`, body),
}
