import { request } from './client'
const url = (v) => encodeURIComponent(v)
/** All borrower loan routes are backed by LoanRequestController.java. */
export const loans = {
  list: (page = 0, size = 20) => request.get('/loan-requests', { page, size }),
  eligible: () => request.get('/loan-requests/eligible-mslas'),
  submit: (body, idempotencyKey) => request.post('/loan-requests', body, idempotencyKey),
  get: ref => request.get(`/loan-requests/${url(ref)}`),
  contract: ref => request.get(`/loan-requests/${url(ref)}/contract`),
  cash: (ref, body) => request.post(`/loan-requests/${url(ref)}/collateral/cash-instructions`, body),
  // BB/BA REST mappings shown in the OLD audit are absent in the updated controller.
  // Do not call them or claim a supported API until the server exposes them again.
}
