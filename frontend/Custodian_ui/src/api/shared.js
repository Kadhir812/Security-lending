/** Standard typed request wrappers. All endpoints flow through the single REST client. */
import { request, e } from './client'
export { idempotencyKey } from './client'
/** @template T @param {string} path @param {Record<string,string|number>} [params] @returns {Promise<T>} */
export const get = (path, params) => request(path, { params })
/** @template T @param {string} path @param {object} [body] @param {Record<string,string>} [headers] @returns {Promise<T>} */
export const post = (path, body, headers) => request(path, { method: 'POST', body, headers })
/** @template T @param {string} path @param {object} body @returns {Promise<T>} */
export const put = (path, body) => request(path, { method: 'PUT', body })
/** @template T @param {string} path @param {number} [page] @param {number} [size] @returns {Promise<import('./types').PageResponse<T>>} */
export const list = (path, page = 0, size = 20) => get(path, { page, size })
export const ct = ref => `/api/contracts/${e(ref)}`
export const ms = ref => `/api/mslas/${e(ref)}`
export { e, request }
