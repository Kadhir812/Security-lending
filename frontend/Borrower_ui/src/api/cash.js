import { request } from './client'

const base = currency => `/cash-accounts/${encodeURIComponent(currency)}`

export const cash = {
  account: currency => request.get(base(currency)),
  transactions: currency => request.get(`${base(currency)}/transactions`),
  deposit: (currency, body, key) => request.post(`${base(currency)}/deposits`, body, key),
  withdraw: (currency, body, key) => request.post(`${base(currency)}/withdrawals`, body, key),
  trade: body => request.post('/cash-accounts/trades', body, body.tradeReference),
  trades: transactionReference => request.get(`/cash-accounts/trades/${encodeURIComponent(transactionReference)}`),
}
