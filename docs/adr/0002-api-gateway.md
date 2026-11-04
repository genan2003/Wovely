# ADR 0002 — Spring Cloud Gateway as the single entry point

- **Status**: accepted (2026-11-05) · PLAN.md 4.1 (chosen over nginx-only routing)

## Decision
`backend/gateway` (Spring Cloud Gateway, reactive) is the only public API endpoint. nginx serves the SPA and
proxies `/api` and `/ws` to it, so the browser talks to one origin (no CORS in practice). The gateway:
- routes `/api/products|categories|reviews/**` → products, `/api/inventory/**` → inventory, `/ws` and the rest
  of `/api/**` → core;
- returns 404 for `/internal/**` and strips any client-sent `X-Internal-Api-Key`;
- rejects invalid/expired bearer tokens at the edge (401) — **authorisation stays in the services**;
- rate-limits with Redis token buckets per user (JWT `uid`) or per client IP (`X-Real-IP`, set by nginx):
  `/api/auth/**` 2 req/s (burst 10), everything else 20 req/s (burst 60); fails open if Redis is down;
- issues/propagates `X-Correlation-Id`, which every service logs (`[%X{correlationId}]`) and forwards.

## Consequences
- + One place for edge concerns; services stay simple. − One more hop and process.
- The gateway listens on **8090** (8080 is commonly taken). Service ports stay bound to `127.0.0.1` for tooling.
