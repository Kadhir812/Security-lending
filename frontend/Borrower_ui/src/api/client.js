import axios from 'axios'

/** Borrower-only Axios transport; this is the ONLY HTTP implementation in the app. */
const SESSION_KEY = 'seclend.borrower.jwt'
const envToken = import.meta.env.VITE_BORROWER_JWT || ''
export const getToken = () => { const saved = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem(SESSION_KEY) : null; return saved === null ? envToken : saved }
export function setSessionToken(value) {
  if (value?.trim()) sessionStorage.setItem(SESSION_KEY, value.trim().replace(/^Bearer\s+/i, ''))
  else sessionStorage.setItem(SESSION_KEY, '')
  window.dispatchEvent(new CustomEvent('borrower:token'))
}
export const newReference = (prefix = 'BRW') => `${prefix}-${crypto.randomUUID()}`
const prefix = String(import.meta.env.VITE_BORROWER_API_URL || '/api').replace(/\/$/, '')
export const http = axios.create({ baseURL: prefix, timeout: 20000, headers: { Accept: 'application/json' } })

/** Request interceptor: never mint/refresh JWTs, add per-request correlation, preserve caller idempotency. */
http.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  config.headers.set('X-Correlation-ID', newReference('BRW').slice(0, 79))
  return config
})

/** Response interceptor: store server echo ID; classify failures; no automatic retries. */
http.interceptors.response.use(response => {
  const correlation = response.headers?.['x-correlation-id'] || response.config.headers?.['X-Correlation-ID']
  if (correlation) window.dispatchEvent(new CustomEvent('borrower:correlation', { detail: correlation }))
  return response
}, failure => {
  const status = failure.response?.status
  const correlation = failure.response?.headers?.['x-correlation-id'] || failure.config?.headers?.['X-Correlation-ID']
  if (correlation) window.dispatchEvent(new CustomEvent('borrower:correlation', { detail: correlation }))
  if (status === 401) window.dispatchEvent(new Event('borrower:unauthorized'))
  const body = failure.response?.data
  const details = typeof body === 'string' ? body : body?.detail || body?.message || body?.error || body?.title
  const labels = {
    400: 'Invalid request fields or format', 401: 'JWT missing, invalid, or expired',
    403: 'Access denied for this Borrower identity', 404: 'Resource not found or not owned by this Borrower',
    409: 'Business prerequisite or version conflict', 422: 'Domain validation failed',
    503: 'A dependent service is unavailable',
  }
  const error = new Error([labels[status] || (status ? `HTTP ${status}` : 'Cannot contact Borrower service'), details].filter(Boolean).join(' — '))
  error.status = status || null
  error.correlationId = correlation
  error.original = failure
  return Promise.reject(error)
})

export const request = {
  get: (path, params) => http.get(path, { params }).then(r => r.data),
  post: (path, body, key) => http.post(path, body, key ? { headers: { 'Idempotency-Key': key } } : {}).then(r => r.data),
}
