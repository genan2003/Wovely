# Wovely

## Members
* Chințoiu Vlăduț-Andrei - vladut.chintoiu@stud.fils.upb.ro
* Fekete Anna-Maria - anna_maria.fekete@stud.fils.upb.ro
* Omer Genan - genan.omer@stud.fils.upb.ro

## Description 
Wovely is a web application designed to provide users with a space in which they can sell or buy hand-made goods. The web app ensures careful stock management, wallet capabilities, a review system and different delivery options, which can present a carbon-emission free mode.

---

### Story 1: Platform Administration & Moderation
**User Story:** As an Admin, I want a comprehensive dashboard to moderate content and assist users, so that I can maintain a safe, functional, and high-quality marketplace.

**Key Features:**
* **Listing Management:** Ability to approve, edit, or delete ads that violate terms.
* **User Governance:** Ability to warn, suspend, or ban users based on disruptive behavior.
* **Order Intervention:** Access to order details to manually adjust quantities, prices, or statuses in case users need assistance.
* **Safety Monitoring:** A "Flagged Content" queue for reported chat logs and products.

**Acceptance Criteria:**
* Admin can search for users by email or username.
* Admin can view chat logs only if a conversation has been officially reported.
* All manual order changes made by admins must be logged in an audit trail.

---

### Story 2: Seller Inventory & Logistics
**User Story:** As a Seller, I want an intuitive inventory and order management system, so that I can focus on my craft rather than technical data entry or shipping logistics.

**Key Features:**
* **"No-Code" Lookup:** Search for items by name or photo while creating an order, avoiding the need for manual SKU/code entry.
* **Smart Inventory:** Automatic stock deduction upon purchase and real-time restocking if an order is canceled.
* **Integrated Logistics:** A "Ship with Platform" option where shipping is handled automatically to remove the need for manual carrier negotiation.
* **Order Flexibility:** Ability to modify order details (e.g., color or size) if requested by the customer after placement but before processing.
* **Status Tracking:** Real-time visibility of the order lifecycle (e.g., Paid -> Processing -> Shipped).

**Acceptance Criteria:**
* Inventory count must update automatically in the storefront once an order is placed.
* The "Order Edit" button must be disabled once the order status moves beyond "Processing."
* Sellers must be able to see a clear status timeline for every active order.

---

### Story 3: Buyer Experience & Sustainability
**User Story:** As a Buyer, I want a personalized, transparent, and eco-conscious shopping experience, so that I can support artists while minimizing my environmental impact.

**Key Features:**
* **Discovery Tools:** Browse handmade products by grouping categories and viewing social proof (reviews/comments from previous purchasers).
* **Green Shipping:** Easy shipping options with a focus on low to zero CO2 emissions (e.g., carbon-neutral delivery or local pickup).
* **Pre-Purchase Chat:** A built-in messaging tool to discuss questions or custom requests directly with the seller.
* **Social Proof:** A review system that displays comments and ratings from verified buyers.

**Acceptance Criteria:**
* Search results must be filterable by "Zero-Emission" or "Eco-friendly" delivery.
* The chat interface must support real-time notifications for both parties.
* Reviews must clearly distinguish "Verified Purchase" entries to ensure trust.

---

## Architecture

The browser only talks to nginx, which serves the Angular app and forwards `/api` and `/ws` to the gateway.

| Part | Tech | Port | Responsibility |
|---|---|---|---|
| Web app | Angular 21 on nginx | 4200 | The user interface |
| Gateway | Spring Cloud Gateway, Redis | 8090 | Routing, JWT pre-check, rate limits, blocks `/internal/**` |
| Core service | Spring Boot 4 | 8081 | Accounts, orders, wallet, chat, returns, reports, audit |
| Products service | Spring Boot 4 | 8082 | Catalog, categories, reviews, moderation |
| Inventory service | Spring Boot 4 | 8083 | The single owner of stock |
| RabbitMQ | RabbitMQ 4 | 15672 (UI) | Stock change events from inventory to products |
| MongoDB | MongoDB 9 | 27017 | One shared database, `wp2-project` |

Start with the [container diagram](docs/diagrams/c4/02-containers.puml), then see
[Diagrams](#diagrams) and [decision records](docs/adr/).

## Run with Docker

You need Docker with Compose, and GNU Make.

1. Create your configuration. Compose refuses to start until the secrets are set.
   ```bash
   cp .env.example .env
   ```
   Then fill in `.env`:
   ```bash
   JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
   INTERNAL_API_KEY=$(openssl rand -hex 32)
   RABBITMQ_PASSWORD=$(openssl rand -hex 16)
   ```
2. Build and start everything. This waits until every service is healthy.
   ```bash
   make up
   ```
3. Add demo products and reviews. A new install has an empty catalog.
   ```bash
   make seed
   ```
4. Open <http://localhost:4200>.

With `SPRING_PROFILES_ACTIVE=dev` these accounts are created for you:

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| Seller | `seller` | `seller123` |
| Buyer | `testuser` | `user123` |

`make logs` follows the logs and `make down` stops the stack. To also delete all data, run `docker compose down -v`.

If you open the site on any address other than `http://localhost:4200`, add that origin to
`CORS_ALLOWED_ORIGINS`, or the gateway answers every `POST` with 403.

## Local development

Run the code you are changing on your machine and leave the rest to Docker. You need JDK 17, Maven and Node 20.

**Frontend only.** Keep the Docker stack running. `ng serve` forwards `/api` and `/ws` to the gateway on port 8090.
```bash
cd frontend/frontend
npm ci
npm start          # http://localhost:4200, so stop the Docker frontend first
```

**Backend services.** Start the infrastructure, install the shared module once, then run each service you need.
```bash
make infra                                                  # MongoDB, Redis, RabbitMQ
mvn -f backend/pom.xml -pl common -am install -DskipTests   # again after changing backend/common
set -a; source .env; set +a                                 # in bash; provides the secrets
cd backend/wovely && mvn spring-boot:run                    # or products, inventory, gateway
```
To run without Docker at all, install MongoDB, Redis and RabbitMQ yourself on their default ports and skip `make infra`.

## Testing

| Command | Runs | Needs |
|---|---|---|
| `make test` | Backend unit and web-slice tests, frontend Vitest | Nothing else |
| `make test-it` | The above for the backend, plus integration tests and JaCoCo coverage | Docker (Testcontainers) |
| `make test-e2e` | Playwright flows, accessibility checks in both themes, 360 px layout checks; the API is mocked | Nothing else |
| `make test-api` | The Postman scenario through newman | A running stack |

To test one backend service, use `mvn -f backend/pom.xml -pl <service> -am test`, or `verify` to include its integration tests.

`make test-api` creates real orders and users, so point it at a stack whose data you can lose.

### Postman

Import `postman/Wovely.postman_collection.json` with `postman/local.postman_environment.json`, or
`docker.postman_environment.json`, then run the folders in order: later requests use ids saved by earlier ones.
`ENV=docker make test-api` selects the environment. The collections are generated, so edit
`scripts/postman/build_collection.py` and run `make postman`.

## API documentation

Each running service serves Swagger UI: [core](http://localhost:8081/swagger-ui.html),
[products](http://localhost:8082/swagger-ui.html), [inventory](http://localhost:8083/swagger-ui.html).
`make openapi` exports the specs to [`docs/api/`](docs/api/).

## Diagrams

The PlantUML sources are in [`docs/diagrams/`](docs/diagrams/). `make diagrams` renders them to
`docs/diagrams/rendered/` as SVG; that folder is not committed.

| Question | Diagrams |
|---|---|
| What is the system made of? | [Context](docs/diagrams/c4/01-context.puml), [containers](docs/diagrams/c4/02-containers.puml), components: [core](docs/diagrams/c4/03-components-core.puml), [products](docs/diagrams/c4/04-components-products.puml), [inventory](docs/diagrams/c4/05-components-inventory.puml) |
| How is it deployed and organised? | [Docker Compose](docs/diagrams/deployment/docker-compose.puml), [backend packages](docs/diagrams/package/backend-packages.puml), [frontend structure](docs/diagrams/package/frontend-structure.puml) |
| Who can do what? | [Buyer](docs/diagrams/usecase/buyer.puml), [seller](docs/diagrams/usecase/seller.puml), [admin](docs/diagrams/usecase/admin.puml) |
| What data is there? | Classes: [core](docs/diagrams/class/domain-core.puml), [products](docs/diagrams/class/domain-products.puml), [inventory](docs/diagrams/class/domain-inventory.puml); [MongoDB collections](docs/diagrams/er/mongo-collections.puml) |
| What states can things be in? | [Order](docs/diagrams/state/order-status.puml), [account](docs/diagrams/state/account-status.puml), [listing](docs/diagrams/state/product-status.puml), [return](docs/diagrams/state/return-status.puml) |
| How do the main flows work? | [Log in](docs/diagrams/sequence/login-jwt.puml), [register](docs/diagrams/sequence/register.puml), [checkout](docs/diagrams/sequence/checkout-stock-deduction.puml), [cancel and restock](docs/diagrams/sequence/cancel-auto-restock.puml), [admin intervention](docs/diagrams/sequence/admin-order-intervention-audit.puml), [return](docs/diagrams/sequence/return-request.puml), [chat](docs/diagrams/sequence/chat-messaging.puml), [report and moderate](docs/diagrams/sequence/report-and-moderation.puml), [product approval](docs/diagrams/sequence/product-approval.puml), [manual sale](docs/diagrams/sequence/seller-manual-order.puml), [verified review](docs/diagrams/sequence/verified-review.puml) |
| What are the step-by-step processes? | [Checkout](docs/diagrams/activity/checkout.puml), [returns](docs/diagrams/activity/return-flow.puml), [seller onboarding](docs/diagrams/activity/seller-onboarding.puml) |

## Project documents

- [`PLAN.md`](PLAN.md): the improvement roadmap and its status
- [`docs/adr/`](docs/adr/): architecture decisions
- [`CLAUDE.md`](CLAUDE.md): working rules and code conventions
