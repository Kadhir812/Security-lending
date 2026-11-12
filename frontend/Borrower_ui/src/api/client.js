import axios from 'axios'

const TOKEN_KEY = 'borrower.jwt.session'
const API_BASE = (import.meta.env.VITE_BORROWER_API_URL || 'http://localhost:8082').replace(/\/$/, '')
const useLocalProxy = import.meta.env.DEV && API_BASE === 'http://localhost:8082'

export const borrowerApi = axios.create({
  baseURL: useLocalProxy ? '' : API_BASE,
  headers: { 'Cache-Control': 'no-store' },
})

function isPublicAuth(config) {
  return config.url === '/api/auth/signup' || config.url === '/api/auth/login'
}

borrowerApi.interceptors.request.use(config => {
  config.headers['X-Correlation-ID'] = crypto.randomUUID()
  let token = ''
  if (!isPublicAuth(config)) {
    try { token = sessionStorage.getItem(TOKEN_KEY) || '' } catch { /* storage disabled */ }
  }
  if (token) config.headers.Authorization = `Bearer ${token}`
  else delete config.headers.Authorization
  return config
})

borrowerApi.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401 && !isPublicAuth(error.config || {})) {
      window.dispatchEvent(new CustomEvent('borrower:unauthorized'))
    }
    return Promise.reject(error)
  },
)

export function getApiErrorMessage(error, fallback) {
  if (error.response) {
    const data = error.response.data
    return data?.detail || data?.message || `Request failed (HTTP ${error.response.status}).`
  }
  if (error.code === 'ERR_NETWORK') return 'Cannot reach the Borrower service. Check that the backend is running and the API URL or proxy is correct.'
  return error.message || fallback
}
