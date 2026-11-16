SHELL := /usr/bin/env bash

TASK_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
COMPOSE := docker compose --project-directory $(TASK_DIR) -f $(TASK_DIR)/compose.yaml -f $(TASK_DIR)/compose.local.yaml

.PHONY: help config prepare up bootstrap smoke ps logs down

help: ## Show available commands
	@awk 'BEGIN {FS = ":.*## "; printf "Usage: make <target>\n\n"} /^[a-zA-Z0-9_-]+:.*## / {printf "  %-12s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

config: ## Validate the merged composition
	@$(COMPOSE) config --quiet

prepare: ## Verify required local files
	@$(TASK_DIR)/scripts/prepare-maelstro.sh

up: prepare ## Start the Maelstro and geOrchestra stack
	@$(COMPOSE) up -d --remove-orphans
	@$(TASK_DIR)/scripts/ensure-backend-driver.sh

bootstrap: ## Create users, resources, and sample data
	@$(TASK_DIR)/scripts/bootstrap.sh

smoke: ## Check the main Maelstro and modern Datafeeder routes
	@$(TASK_DIR)/scripts/smoke-test.sh

ps: ## Show service status
	@$(COMPOSE) ps -a

logs: ## Follow service logs
	@$(COMPOSE) logs -f --tail=200

down: ## Stop services without deleting volumes
	@$(COMPOSE) down
