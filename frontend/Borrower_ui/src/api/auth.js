import { request } from './client'

export const auth = {
  signup: body => request.post('/auth/signup', body),
  login: body => request.post('/auth/login', body),
  custodianLinkToken: () => request.post('/auth/custodian-link-token'),
}
