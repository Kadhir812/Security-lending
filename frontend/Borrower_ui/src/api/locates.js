import { request } from './client'

const url = value => encodeURIComponent(value)

export const locates = {
  list: () => request.get('/locates'),
  submit: (body, idempotencyKey) => request.post('/locates', body, idempotencyKey),
  get: reference => request.get(`/locates/${url(reference)}`),
}
