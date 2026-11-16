import { request } from './client'

export const auth = {
  signup: body => request.post('/auth/signup', body),
  login: body => request.post('/auth/login', body),
  me: () => request.get('/auth/me'),
  custodianLinkToken: () => request.post('/auth/custodian-link-token'),
}
