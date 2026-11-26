/** Settlement Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { get, post, ct } from './shared'
export const settlements = {
  get: (t) => get(`${ct(t)}/settlement`),
  submitCash: (t, b) => post(`${ct(t)}/settlement/cash-instructions`, b),
  cashConfirm: (t, b) => post(`${ct(t)}/settlement/cash-instructions/confirm`, b),
  securitiesConfirm: (t, b) => post(`${ct(t)}/settlement/securities/confirm`, b),
  returnConfirm: (t, b) => post(`${ct(t)}/settlement/returns/confirm`, b),
  returnFail: (t, b) => post(`${ct(t)}/settlement/returns/fail`, b),
  releaseConfirm: (t, b) => post(`${ct(t)}/settlement/collateral-releases/confirm`, b),
  releaseFail: (t, b) => post(`${ct(t)}/settlement/collateral-releases/fail`, b),
}
