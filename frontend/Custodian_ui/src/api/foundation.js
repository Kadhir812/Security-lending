/** Foundation Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { get } from './shared'
export const foundation = { status: () => get('/api/foundation/status') }
