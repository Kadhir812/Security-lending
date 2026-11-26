import { get, put } from './shared'

export const calculationSchedule = {
  get: () => get('/api/calculations/schedule'),
  update: intervalMinutes => put('/api/calculations/schedule', { intervalMinutes }),
}
