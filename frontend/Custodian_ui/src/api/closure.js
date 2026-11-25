/** Closure Custodian REST resource functions; DTO-aligned, no client-side business calculations. */
import { post, ct } from './shared'
export const closure = { close: (t, b) => post(`${ct(t)}/close`, b) }
