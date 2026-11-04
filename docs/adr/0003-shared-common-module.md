# ADR 0003 — `wovely-common` module and a Maven reactor

- **Status**: accepted (2026-11-05) · PLAN.md 4.3

## Decision
`backend/pom.xml` is an aggregator (common, wovely, products, inventory, gateway). `backend/common`
(`com.wovely:wovely-common`) is a Spring Boot auto-configuration shared by the servlet services: error model +
`GlobalExceptionHandler`, internal API-key filter, claims-based JWT security (`ClaimsSecurityConfiguration`
for products/inventory), CORS, the internal `RestTemplate` (timeouts, API key, correlation id), the correlation
filter, OpenAPI/Swagger, Actuator, and the `StockChanged` event contract. The gateway is reactive and does not
use it. One `backend/Dockerfile` builds any service (`--build-arg SERVICE=…`, non-root, Alpine JRE).

## Consequences
- + Fixes land once (e.g. 405/415 handling was missing in all three services).
- − Services must be built through the reactor (`mvn -f backend/pom.xml -pl <svc> -am …`); CI and the
  Dockerfile do this. Remember `mvn clean` after moving classes (stale `target/classes` caused duplicate beans).
