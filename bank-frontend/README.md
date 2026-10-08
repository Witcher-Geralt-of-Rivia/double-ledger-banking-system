# Bank Frontend

Role-based banking operations dashboard for the bank platform. Built with React + Vite, uses token auth, and renders dashboards for admins, managers, auditors, and end users.

## Quick start

```sh
npm install
npm run dev
```

The dev server defaults to http://localhost:8081. Ensure the backend is reachable at `VITE_API_BASE_URL`.

## Environment

The backend URL and feature flags come from Vite environment variables. The committed files are:

| File | Loaded for | Purpose |
|---|---|---|
| `.env` | every mode | Development defaults (`http://localhost:8080/api`, feature flags) |
| `.env.production` | `npm run build` | Clears `VITE_API_BASE_URL`, so production has no built-in backend |
| `.env.example` | never (template) | Placeholders to copy from |

To override the development defaults, create `.env.local` in `bank-frontend/` (git-ignored):

```sh
VITE_API_BASE_URL=http://localhost:8080/api
VITE_ENABLE_MOCKS=false
VITE_ENABLE_AUDIT=false
VITE_ENABLE_SECURITY=false
```

`VITE_API_BASE_URL` points to the backend REST API and must include the backend context path (`/api`). `VITE_ENABLE_MOCKS` should stay false (mock data has been removed).

`VITE_ENABLE_AUDIT` and `VITE_ENABLE_SECURITY` gate frontend routes that require backend modules not present in all environments. Keep both `false` unless those APIs are available.

Production builds (`npm run build`) must be given `VITE_API_BASE_URL` by the environment and fail without it, so a build can never fall back to a localhost or third-party backend. For a local production build, set it in `.env.production.local` (git-ignored) or inline, for example `VITE_API_BASE_URL=http://localhost:8080/api npm run build`.

`VITE_*` values are compiled into the client bundle and are publicly visible. Never put credentials, API keys or tokens in them.

## Features

- Protected routing with role checks for admin, manager, customer manager, user, and auditor views.
- Auth with localStorage token storage; login/register flows hit `/api/auth` endpoints.
- Data fetching via React Query with sensible defaults (stale 5m, retry once, no refetch on focus).
- Audit and security views: audit logs, access logs, sessions, activity feed.
- Banking flows: banks, customers, accounts, transactions, UPI profiles/payments.
- Reusable UI built on shadcn-ui/Radix + Tailwind; global toasts (shadcn + sonner) and tooltips.
- Global command palette (`Ctrl+K`) for fast module navigation and quick actions.
- Module readiness indicator in the top bar showing backend-dependent modules currently disabled by feature flags.
- Advanced theme controls now include UI styles (`Modern`, `Classic`, `Solid`) and accent palettes (`Emerald`, `Ocean`, `Royal`, `Ember`, `Jade`).
- Dashboard stat widgets are now customizable per role profile (show/hide + reorder) and saved in localStorage.
- One-click stat layout presets are available in Dashboard (Compact, Executive, Risk, Ops) for faster context switching.
- Widget composer supports drag-and-drop ordering plus reset-to-default for quick iteration.
- Named dashboard layouts can be saved, reapplied, and deleted from the widget composer.
- Transactions module now supports saved filter views (save/apply/delete) for fast investigation workflows.

## Local Changes (May 2026, Unpublished)

- New profile and forgot-password screens wired to auth flows.
- Added global command palette for fast navigation (`Ctrl+K`).
- Dashboard layout upgrades: role-based widget customization, presets, and saved layouts.
- Transactions workflow enhancements: saved filters and faster investigation flows.
- UI consistency, accessibility, and theme refinements across modules.

## Scripts

- `npm run dev` — start the Vite dev server
- `npm run build` — production bundle
- `npm run preview` — serve the production build locally
- `npm run lint` — ESLint
- `npm run test` — Vitest unit tests
- `npm run test:watch` — Vitest watch mode

## Architecture

- Entry: `src/main.tsx` mounts `App` with global styles.
- Routing: `App` sets up `BrowserRouter` with public login/register and protected routes under `DashboardLayout`. Role-based redirects use `ProtectedRoute` and `getDashboardRoute`.
- State: `AuthProvider` stores user and tokens in localStorage and exposes helpers (`hasRole`, `hasAnyRole`).
- Data: `@/lib/api-client` wraps fetch with timeout, bearer auth, and 401 handling; API modules cover bank, customer, account, transaction, UPI, audit, access log, and session endpoints.
- UI: shadcn-ui components, Radix primitives, custom cards/tables for dashboards; `Toaster` and `Sonner` for notifications.

## Project structure

- `src/pages` — routed views (dashboards, login/register, banks, customers, accounts, transactions, UPI, audit, security, payments)
- `src/components` — layout (sidebar, wrappers), tables, cards, permissions gate, error boundary
- `src/contexts` — auth provider/hooks
- `src/lib` — API client, RBAC helpers, formatting utilities
- `src/hooks` — API hook, toast hook, mobile helpers
- `src/data` — intentionally empty (live data only)
- `public` — static assets

## Backend expectations

- Auth: `/api/auth/login` and `/api/auth/register` return `accessToken`, `refreshToken`, user profile, roles.
- All API calls prefix `VITE_API_BASE_URL`; bearer token is sent automatically from localStorage.
- 401 responses clear tokens and redirect to `/login`.

## Build & deploy

```sh
npm run build   # requires VITE_API_BASE_URL, see Environment
npm run preview # optional local smoke test
```

Deploy the `dist/` directory to your hosting target. If serving behind a different origin, ensure CORS allows the frontend origin.

### Deploying to Vercel

| Setting | Value |
|---|---|
| Root Directory | `bank-frontend` |
| Framework Preset | Vite |
| Install Command | `npm install` |
| Build Command | `npm run build` |
| Output Directory | `dist` |

The install, build and output settings are pinned in [`vercel.json`](vercel.json). It also rewrites every path that is not a built file to `index.html`, so direct navigation to client-side routes such as `/login` or `/dashboard` works.

Environment variables to set in the Vercel project (for both Production and Preview):

| Variable | Required | Value |
|---|---|---|
| `VITE_API_BASE_URL` | yes | Base URL of your own backend, including `/api`, for example `https://your-backend.example.com/api` |
| `VITE_ENABLE_SECURITY` | no | Defaults to `true` from `.env.production` |
| `VITE_ENABLE_AUDIT` | no | Defaults to `true` from `.env.production` |

No backend URL is committed to this repository. The backend must also allow the deployed frontend origin: with the `prod` profile, set `FRONTEND_URL` on the backend to the Vercel URL, which drives CORS and the password-reset links.

## Troubleshooting

- Empty data: confirm backend is up and `VITE_API_BASE_URL` is correct.
- Auth loops: check tokens in localStorage and backend CORS/HTTPS settings.
- Port conflict (frontend): adjust `server.port` in `vite.config.ts`.


## Maintainer & Contact

- **Current Maintainer:** Jakub Kowalski
- **GitHub:** https://github.com/Witcher-Geralt-of-Rivia
- **Repository:** https://github.com/Witcher-Geralt-of-Rivia/double-ledger-banking-system
- **Portfolio:** https://jakub-kowalski-portfolio.vercel.app

For project questions, maintenance requests, or bug reports, use the GitHub repository/issues or the portfolio contact channels.

Original copyright and license attribution are retained in the repository root `LICENSE` file; see the root `NOTICE.md`.
