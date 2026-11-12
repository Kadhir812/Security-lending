/** Central Axios client. All resource functions use this module; no mutation is retried. */
import axios from 'axios'

export const API_BASE = (import.meta.env.VITE_CUSTODIAN_API_URL || 'http://localhost:8081').replace(/\/$/, '')
export const TOKEN_KEY = 'custodian.jwt.session'
const useLocalProxy = import.meta.env.DEV && API_BASE === 'http://localhost:8081'

export const http = axios.create({
  baseURL: useLocalProxy ? '' : API_BASE,
  headers: { 'Cache-Control': 'no-store' },
})

export function getToken() {
  try { return sessionStorage.getItem(TOKEN_KEY) || '' } catch { return '' }
}
export function setSessionToken(value) {
  if (value?.trim()) sessionStorage.setItem(TOKEN_KEY, value.trim())
  else sessionStorage.removeItem(TOKEN_KEY)
  window.dispatchEvent(new CustomEvent('custodian:token'))
}
export function idempotencyKey() { return crypto.randomUUID() }
export class ApiError extends Error {
  constructor(status, message, data, correlationId) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.data = data
    this.correlationId = correlationId
  }
}

const messages = {
  400: 'Validation failed. Review the required fields and formats.',
  401: 'Authentication required. Sign in again to continue.',
  403: 'Access denied. Your identity is not authorized for this organization or action.',
  404: 'The resource was not found. It may have changed or become unavailable.',
  409: 'A concurrent update or business conflict occurred. Refresh the record before trying again.',
  422: 'The requested operation is not eligible under current agreement or settlement rules.',
  503: 'An upstream service is unavailable. Refresh the state before deliberately retrying.',
}

function isPublicAuth(config) {
  return config.url === '/api/auth/signup' || config.url === '/api/auth/login'
}

http.interceptors.request.use(config => {
  config.headers['X-Correlation-ID'] = crypto.randomUUID()
  const token = isPublicAuth(config) ? '' : getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  else delete config.headers.Authorization
  return config
})

http.interceptors.response.use(
  response => {
    const correlationId = response.headers['x-correlation-id'] || response.config.headers['X-Correlation-ID']
    if (correlationId) window.dispatchEvent(new CustomEvent('custodian:correlation', { detail: correlationId }))
    return response
  },
  error => {
    const { response, config } = error
    if (response?.status === 401 && !isPublicAuth(config || {})) {
      try { sessionStorage.removeItem(TOKEN_KEY) } catch { /* storage disabled */ }
      window.dispatchEvent(new CustomEvent('custodian:unauthorized'))
    }
    const correlationId = response?.headers?.['x-correlation-id'] || config?.headers?.['X-Correlation-ID']
    if (correlationId) window.dispatchEvent(new CustomEvent('custodian:correlation', { detail: correlationId }))
    return Promise.reject(error)
  },
)

export async function request(path, { method = 'GET', body, params, headers = {}, signal } = {}) {
  const cleanParams = Object.fromEntries(
    Object.entries(params || {}).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
  try {
    const response = await http.request({
      url: path,
      method,
      data: body,
      params: cleanParams,
      headers,
      signal,
    })
    return response.data
  } catch (error) {
    if (axios.isCancel(error)) throw error
    const status = error.response?.status || 0
    const data = error.response?.data ?? null
    const detail = typeof data === 'object' && data !== null ? (data.message || data.detail || data.error || data.title) : null
    const correlationId = error.response?.headers?.['x-correlation-id'] || error.config?.headers?.['X-Correlation-ID']
    const message = status
      ? detail || messages[status] || `Request failed (HTTP ${status}).`
      : 'Cannot reach the Custodian service. Check the API address, backend and CORS/proxy configuration.'
    throw new ApiError(status, message, data, correlationId)
  }
}

export const e = (ref) => encodeURIComponent(String(ref).trim())
