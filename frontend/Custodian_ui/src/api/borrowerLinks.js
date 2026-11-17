import { post } from './shared'

export const borrowerLinks = {
  link: linkToken => post('/api/organizations/borrower-links', { linkToken }),
}
