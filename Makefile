# Wovely — common dev/test tasks. Run `make help`.
SERVICES   := wovely products inventory
FRONTEND   := frontend/frontend
NEWMAN     := npx --yes newman@6
POSTMAN    := postman
ENV        ?= local

.PHONY: help up down logs test test-backend test-it test-frontend test-e2e test-api seed postman diagrams

help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Build and start the whole stack (docker compose)
	docker compose up -d --build

down: ## Stop the stack
	docker compose down

logs: ## Follow logs of all services
	docker compose logs -f

test: test-backend test-frontend ## Fast tests (unit + web slice, no Docker needed)

test-backend: ## Backend unit/web-slice tests (mvn test) for all services
	@for s in $(SERVICES); do echo "== $$s"; (cd backend/$$s && mvn -B -q test) || exit 1; done

test-it: ## Backend unit + integration tests with Testcontainers + JaCoCo (needs Docker)
	@for s in $(SERVICES); do echo "== $$s"; (cd backend/$$s && mvn -B verify) || exit 1; done

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
