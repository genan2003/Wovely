# Wovely — common dev/test tasks. Run `make help`.
SERVICES   := common wovely products inventory gateway
BACKEND    := backend/pom.xml
FRONTEND   := frontend/frontend
NEWMAN     := npx --yes newman@6
POSTMAN    := postman
ENV        ?= local

.PHONY: help up down logs test test-backend test-it test-frontend test-e2e test-api seed postman diagrams openapi

help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Build and start the whole stack (docker compose); open http://localhost:4200
	docker compose up -d --build --wait

down: ## Stop the stack
	docker compose down

logs: ## Follow logs of all services
	docker compose logs -f

test: test-backend test-frontend ## Fast tests (unit + web slice, no Docker needed)

test-backend: ## Backend unit/web-slice tests for every module (reactor build: common first)
	mvn -B -q -f $(BACKEND) test

test-it: ## Backend unit + integration tests with Testcontainers + JaCoCo (needs Docker)
	mvn -B -f $(BACKEND) verify

test-frontend: ## Angular unit tests (Vitest)
	cd $(FRONTEND) && npx ng test --watch=false

test-e2e: ## Playwright end-to-end tests (starts ng serve)
	cd $(FRONTEND) && npx playwright test

test-api: ## Postman/newman API scenario against a running stack (ENV=local|docker)
	@mkdir -p $(POSTMAN)/results
	$(NEWMAN) run $(POSTMAN)/Wovely.postman_collection.json -e $(POSTMAN)/$(ENV).postman_environment.json \
		--reporters cli,junit --reporter-junit-export $(POSTMAN)/results/newman.xml

seed: ## Load demo products and reviews into a running stack
	$(NEWMAN) run $(POSTMAN)/Wovely.seed.postman_collection.json -e $(POSTMAN)/$(ENV).postman_environment.json

postman: ## Regenerate the Postman collections from scripts/postman/build_collection.py
	python3 scripts/postman/build_collection.py

diagrams: ## Render PlantUML diagrams to docs/diagrams/rendered
	@mkdir -p docs/diagrams/rendered
	find docs/diagrams -name '*.puml' ! -name '_*' -print0 | xargs -0 -r plantuml -tsvg -o "$(CURDIR)/docs/diagrams/rendered"

openapi: ## Export each service's OpenAPI spec from a running stack to docs/api/ (Swagger UI: :808x/swagger-ui.html)
	@mkdir -p docs/api
	@for p in wovely:8081 products:8082 inventory:8083; do n=$${p%%:*}; port=$${p##*:}; \
	  curl -fsS http://localhost:$$port/v3/api-docs | python3 -m json.tool --sort-keys > docs/api/$$n.openapi.json && echo "docs/api/$$n.openapi.json"; done
