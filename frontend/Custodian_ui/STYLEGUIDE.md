# Custodian UI — Design system / Style guide

## Screenshot / palette

The CSS palette is stored in `src/styles/tokens.css`. It was sampled from `Securities Lending Command Center Dashboard.png` (1672 × 941 pixels) with Pillow RGB pixel sampling. Reference coordinates and hex colors:

| Token | Hex | Screenshot sample |
|---|---|---|
| `--color-black` | `#111111` | Sidebar at (45, 315) |
| `--color-topbar` | `#131212` | Top ticker at (115, 20) |
| `--color-shell` | `#1f1e1c` | App background at (120, 230) |
| `--color-deep-panel` | `#1c1b1a` | Dark chart at (380, 336) |
| `--color-cream-light` | `#f6f4e2` | Cream KPI at (395, 182) |
| `--color-cream-deep` | `#f3e4d2` | Main card at (440, 465) |
| `--color-cream-footer` | `#f4e7d5` | Lower card at (1310, 640) |
| `--color-green-panel` | `#e2f1cf` | Coverage KPI at (1100, 182) |
| `--color-red-panel` | `#ffd2c5` | Calls KPI at (1260, 180) |
| `--color-muted-panel` | `#f2e9de` | Last KPI at (1500, 190) |
| `--color-amber-dark` | `#643706` | Active nav at (55, 172) |

The orange is a CSS accent/gradient across the source image (`--color-orange: #ff8900`, `--color-orange-deep: #eb6b00`). Rounded warm cream panels contrast with black navigation and muted charcoals. The primary typography is **Roboto** served by Google Fonts with `Arial, sans-serif` local fallbacks; the entire app inherits that one family and `font-variant-numeric: tabular-nums`.

## Spacing / sizing

`--space-1` = 4px, `--space-2` = 8px, `--space-3` = 12px, `--space-4` = 16px, `--space-5` = 20px, `--space-6` = 24px. Radii: 5px / 8px / 11px / 14px. Screenshot shell: 48px ticker, 114px left navigation, 70px page title region. Desktop dashboard: 7 KPIs then 3 compact grid rows and a lifecycle strip. Tablet and phone layouts wrap or stack naturally.

## Component classes

- **Layout** in `layout.css`: `.app-shell`, `.ticker`, `.sidebar`, `.sidebar__link`, `.app-header`, `.search-box`, `.profile`, `.workspace-tabs`, `.global-status`, `.mode-switch`, `.search-command`.
- **Shared components** in `components.css`: `.panel`, `.panel__heading`, `.btn` plus `--primary`, `--outline`, `--approve`, `--reject`, `--danger`, `.status-badge`, `.demo-tag`, `.page-heading`, `.kpi-strip`, `.work-area`, `.side-guide`, `.footer-note`, `.field`, `.resource-form`, `.form-grid`, `.data-table`, `.pagination`, `.detail-grid`, `.api-error`, `.empty-state`, `.modal`, `.simulation-banner`, `.lifecycle`, `.toast`.
- **Dashboard** in `pages.css`: `.dashboard-kpis`, `.dashboard-kpi`, `.dashboard-grid`, `.dashboard-panel`, `.dashboard-compact-table`, `.trend-chart`, `.donut`, `.treemap`, `.fix-list`, `.quick-actions`, `.dashboard-lifecycle`.
- **Operations** in `pages.css`: `.operations-placeholder`, `.activity-timeline`, `.gap-feature`, `.message-journey`, `.glossary-list`.

Every family has an explanatory comment in its owning stylesheet. Components use external CSS files only: no inline `style` props, CSS-in-JS, Tailwind or embedded styles. SVG charts set drawing geometry through SVG attributes and use external CSS classes for visual appearance.

## Extending the design

1. Add/revise design variables in `tokens.css`, not scattered literals.
2. Keep all global browser defaults and typography in `base.css`.
3. Place shell navigation and breakpoints in `layout.css`; shared controls in `components.css`; domain pages and charts in `pages.css`.
4. Reuse `Panel`, `PageHeading`, `KpiStrip`, `SideGuide`, `FooterNote`, `DataTable`, `Field`, `ConfirmDialog`, `StatusBadge` and `DemoTag` from `src/components/UI.jsx`.
5. For new API resources add a function to `src/api/resources.js`; all HTTP must go through `src/api/client.js`. Build DTO-driven create/update forms with explicit server versions. Never mock successful real actions or server-owned financial calculations.
6. Mark synthetic or unavailable widgets with **Demo data – API pending**. In Live mode, show an unavailable explanation instead of fabricated values.


## Pixel sample provenance

From the 1672 × 941 design reference, sampled using Pillow on the exact uploaded PNG: topbar `#131413` at (90,18); sidebar `#111111` at (45,315); chart `#1c1b1a` at (380,336); cream KPI `#f6f4e2` at (395,182); cream body `#f3e4d2` at (440,465); collateral green `#e2f1cf` at (1100,182); margin red `#ffd2c5` at (1260,180); amber navigation `#643706` at (55,172). Gradients retain these reference colours as visual anchors.

The alignment pass fixes the compact table cell display (which previously broke native table column alignment), reserves heading-row space for demo provenance badges rather than overlaying data, keeps the service status in a compact fixed bottom dock, and uses a non-condensed Roboto stack to match reference text width.
