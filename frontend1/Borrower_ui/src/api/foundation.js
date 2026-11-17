import { request } from './client'
/** Protected service status; requires an authenticated Borrower JWT. */
export const foundation = { status: () => request.get('/foundation/status') }
