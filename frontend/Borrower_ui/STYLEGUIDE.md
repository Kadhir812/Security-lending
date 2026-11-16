# Borrower UI Style Guide

The Borrower interface shares the Custodian design DNA from `Securities Lending Command Center Dashboard.png` (1672 × 941). Pixel hex colors were sampled in the Custodian reference and copied into `src/styles/tokens.css` without guessing. The same **Roboto Condensed** family is used everywhere (Google Fonts with Arial Narrow fallback). Monetary/quantity text uses tabular numerals.

## Palette / tokens

| Role | CSS variable | Hex |
|---|---|---|
| Near-black ticker / header | `--color-topbar` | `#131212` |
| Sidebar | `--color-black` | `#111111` |
| Work canvas | `--color-shell` | `#1f1e1c` |
| Dark chart | `--color-deep-panel` | `#1c1b1a` |
| Warm cream cards | `--color-cream` | `#f3e5d3` |
| Cream highlight | `--color-cream-light` | `#f6f4e2` |
| Collateral-green KPI | `--color-green-panel` | `#e2f1cf` |
| Margin-alert red KPI | `--color-red-panel` | `#ffd2c5` |
| Orange action/chart | `--color-orange` | `#ff8900` |
| Orange deep | `--color-orange-deep` | `#eb6b00` |
| Success green | `--color-green` | `#035f31` |
| Alert red | `--color-red` | `#9d2421` |

Spacing tokens `--space-1` through `--space-6` = **4, 8, 12, 16, 20, 24px**. Card radius: `--radius-sm:8px`, `--radius-md:11px`, `--radius-lg:14px`. Reuse `--shadow-card`, `--shadow-lift`, and `--shadow-orange`.

## CSS ownership

- `tokens.css`: palette, typography, sizing and elevation.
- `base.css`: reset, font and numeric consistency.
- `layout.css`: ticker, sidebar, header, status bar, responsive drawer and command palette.
- `components.css`: `.panel`, `.btn` variants, `.status-badge`, `.demo-tag`, `.page-heading`, `.kpi-strip`, `.work-area`, `.side-guide`, `.form-grid`, `.field`, `.data-table`, `.pagination`, `.detail-grid`, `.modal`, `.lifecycle`, `.toast`.
- `pages.css`: `.dashboard-*`, `.trend-chart`, `.donut`, `.treemap`, `.alert-list`, `.credit-*`, `.fix-list`, `.quick-actions`.
- `borrower.css`: Borrower-specific alignment adjustments and `.borrower-panel-actions`, `.live-gate`, `.subsection-title`, `.form-separated`, `.auth-feature-grid`, `.contract-lifecycle`.

All component class definitions have comment headers. No Tailwind, CSS-in-JS or inline CSS. SVG chart attributes express graphic coordinates, not runtime styling.

## Extending the design

1. Add a domain API function to the appropriate `src/api` module and keep Axios logic in `src/api/client.js`.
2. Create a routed page in `src/pages`, and register it in `src/App.jsx` and secondary tabs in `src/layouts/Shell.jsx`.
3. Wrap with `WorkPage` to obtain subtitle, KPIs, guidance, action panel and footer; display `ResourceBody` for authoritative server data.
4. Add a named class (with comment) to `borrower.css`; use existing component tokens and accessible controls.
5. For any new demo-only visual, add an explicit `DemoTag` and implement an API-unavailable state in Live. Do not send demo rows to the backend.
6. Always confirm a mutation using `ConfirmDialog` and honor server business/authorization responses.
