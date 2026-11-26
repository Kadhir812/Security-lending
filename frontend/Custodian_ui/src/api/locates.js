import { get, post, e } from './shared'

const endpoint = '/api/locate-requests'

export const locates = {
  queue: () => get(endpoint),
  approve: (reference, approvedQuantity) =>
    post(`${endpoint}/${e(reference)}/approve`, { approvedQuantity }),
  reject: (reference, reason) =>
    post(`${endpoint}/${e(reference)}/reject`, { reason }),
}
