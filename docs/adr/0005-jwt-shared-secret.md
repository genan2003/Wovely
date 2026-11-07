# ADR 0005 — Shared-secret JWTs verified in each service

- **Status**: accepted (2026-11-06) · PLAN.md 1.8, 2.9, 4.3 · describes the design already in place

## Context
Three services and the gateway all need to know who is calling. A call to a central auth service on every
request would add a network hop and a single point of failure, and the project has no identity server.

## Decision
- The core service signs HS256 JWTs (`sub` = username, `uid` = user id, `roles`) with `JWT_SECRET`, which every
  service receives from the environment and refuses to start without.
- Products and inventory verify the signature and trust the claims (`JwtPrincipal`), without a database lookup.
  The core service reloads the user from MongoDB on every request, so it sees a ban immediately.
- The gateway pre-checks tokens at the edge. Authorisation stays in the services.
- Access tokens live 15 minutes. Opaque refresh tokens live 7 days, are stored as SHA-256 hashes, rotate on each
  use, and a re-used token revokes the user's whole family.
- Service-to-service calls use a separate secret, `INTERNAL_API_KEY`, on `/internal/**` only.

## Consequences
- + No extra hop and no shared session store; products and inventory keep working if core is briefly down.
- − Every service holds the signing key, so any compromised service can mint tokens. Asymmetric keys (RS256 and
  a public key per service) or an identity server such as Keycloak would remove this, and CLAUDE.md allows it.
- − A banned user keeps access to products and inventory until the access token expires, at most 15 minutes.
- − Rotating `JWT_SECRET` signs everyone out and needs all services restarted together.
