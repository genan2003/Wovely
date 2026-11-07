# ADR 0004 — Client-rendered SPA, self-hosted fonts and icons

- **Status**: accepted (2026-11-06) · PLAN.md 5.2, 5.7

## Context
The Angular project was configured for SSR (`server.ts`, Express, hydration), but the Docker image only ever
served the browser build from nginx, so the SSR path ran in `ng serve` and nowhere in production. It still
cost us: `isPlatformBrowser` checks in 11 files, an `apiOriginInterceptor`, a crash on unhandled errors and
real data leaking past E2E mocks through the HTTP transfer cache. Fonts and icons came from Google Fonts and
jsDelivr at runtime.

## Decision
- Remove SSR. nginx serves `index.html` for every SPA route.
- Install `bootstrap-icons`, `@fontsource/young-serif` and `@fontsource-variable/hanken-grotesk` from npm at
  exact versions, so the UI has no runtime dependency on a third-party CDN and sends no visitor data to one.
- Test accessibility with `@axe-core/playwright` (dev only).

## Consequences
- + One rendering path, fewer dependencies (`express`, `@angular/ssr`, `@angular/platform-server`), smaller
  initial bundle, no platform branches.
- − Crawlers get an empty shell until JavaScript runs, and first paint waits for the bundle. If public SEO
  matters later, bring SSR back for the catalog and product routes only, run it as its own container, and
  give it `API_ORIGIN`.
