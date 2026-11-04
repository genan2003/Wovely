# ADR 0001 — Inventory owns stock; the catalog keeps an event-fed read model

- **Status**: accepted (2026-11-05) · PLAN.md 4.2
- **Deciders**: team (chosen over "fold inventory into products" and "inventory + synchronous HTTP only")

## Context
Stock lived in two places: `products.stockQuantity` (authoritative, atomic) and `inventory_items`
(a best-effort HTTP mirror that silently drifted). Core deducted stock at products, then "mirrored" to
inventory and ignored failures. The inventory service had no real job.

## Decision
1. **Inventory is the single writer of stock.** `StockLedger` performs every change (init, reserve,
   release, restock, set level, deactivate) as one atomic, guarded `findAndModify`.
2. **Core** reserves/releases through `InventoryClient` (`/internal/inventory/stock/{productId}/…`).
   A release that can't be delivered is stored in `stock_release_retries` and replayed — stock is never
   silently lost.
3. **Products** keeps `stockQuantity` as a **read model** for catalog queries. Its seller stock endpoints are
   a facade: they forward to inventory and apply inventory's answer immediately (read-your-writes).
4. **Propagation by events**: inventory publishes `StockChanged` (full state + per-product `version`) to the
   RabbitMQ topic exchange `wovely.inventory`; products consumes it from a durable quorum queue with a
   dead-letter queue and applies it only if `version` is newer.

### Transactional outbox without transactions
MongoDB runs standalone (no multi-document transactions). The outbox mark (`outboxPending: true`) and the
`version` bump are written **in the same single-document update** as the stock change, so a change can't be
committed without being marked for publication. `StockEventRelay` publishes pending items with publisher
confirms and clears the mark only if the version is unchanged. Delivery is at-least-once; consumers are
idempotent by version, so duplicates and reordering are harmless.

## Consequences
- + One source of truth; overselling is prevented in one place (IT: 40 concurrent reservations on 5 → 5).
- + The catalog stays fast (no cross-service call per product) and survives inventory outages read-only.
- − The catalog is **eventually consistent** (typically < 1 s). Postman polls the read model; the UI shows the
  catalog value and the checkout is decided by inventory.
- − New infrastructure (RabbitMQ) to run and monitor (`:15672` management UI on localhost).
- Migration: `StockOwnershipMigration` copied every product's current stock into inventory once
  (marker `migrations/stock-ownership-v1`); it reads the `products` collection directly — the only
  cross-service data access, used once.
