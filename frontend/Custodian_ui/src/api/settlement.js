/** Settlement Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { get, post, ct } from './shared'
export const settlements = {
  get: (t) => get(`${ct(t)}/settlement`),
  submitCash: (t, b) => post(`${ct(t)}/settlement/cash-instructions`, b),
  cashConfirm: (t, b) => post(`${ct(t)}/settlement/cash-instructions/simulated-confirm`, b),
  securitiesConfirm: (t, b) => post(`${ct(t)}/settlement/securities/simulated-confirm`, b),
  returnConfirm: (t, b) => post(`${ct(t)}/settlement/returns/simulated-confirm`, b),
  returnFail: (t, b) => post(`${ct(t)}/settlement/returns/simulated-fail`, b),
  releaseConfirm: (t, b) => post(`${ct(t)}/settlement/collateral-releases/simulated-confirm`, b),
  releaseFail: (t, b) => post(`${ct(t)}/settlement/collateral-releases/simulated-fail`, b),
}
