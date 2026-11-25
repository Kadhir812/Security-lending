import { request } from './client'

export const securities = {
  active: () => request.get('/reference/securities'),
  latest: isin => request.get(`/reference/securities/${encodeURIComponent(isin)}/prices/latest`),
  history: (isin, from, to) => request.get(
    `/reference/securities/${encodeURIComponent(isin)}/prices`,
    { from, to, page: 0, size: 100 },
  ),
}
