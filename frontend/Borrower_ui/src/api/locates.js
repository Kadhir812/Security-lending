import { request } from './client'
/** New locate endpoints confirmed in updated Borrower LocateController.java. */
export const locates = {
  list: () => request.get('/locates'),
  submit: body => request.post('/locates', body),
  get: ref => request.get(`/locates/${encodeURIComponent(ref)}`),
}
