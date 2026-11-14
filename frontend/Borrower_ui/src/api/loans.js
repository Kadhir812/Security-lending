import { request } from './client'
const url = (v) => encodeURIComponent(v)
/** All borrower loan routes are backed by LoanRequestController.java. */
export const loans = {
  list: (page = 0, size = 20) => request.get('/loan-requests', { page, size }),
  eligible: () => request.get('/loan-requests/eligible-mslas'),
  submit: (body, idempotencyKey) => request.post('/loan-requests', body, idempotencyKey),
  get: ref => request.get(`/loan-requests/${url(ref)}`),
  collateralInquiry: ref => request.post(`/loan-requests/${url(ref)}/collateral-inquiries`),
  collateralReports: ref => request.get(`/loan-requests/${url(ref)}/collateral-reports`),
  contract: ref => request.get(`/loan-requests/${url(ref)}/contract`),
  cash: (ref, body) => request.post(`/loan-requests/${url(ref)}/collateral/cash-instructions`, body),
}
