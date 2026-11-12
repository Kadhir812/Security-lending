import { request } from './client'
/** Protected service status; requires a pre-issued Bearer JWT. */
export const foundation = { status: () => request.get('/foundation/status') }
