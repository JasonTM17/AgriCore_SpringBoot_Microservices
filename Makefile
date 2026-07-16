.PHONY: test build infra-up infra-down verify

test:
	mvn -B test

verify:
	mvn -B verify

build:
	mvn -B -DskipTests package

infra-up:
	docker compose -f docker-compose.infrastructure.yml up -d

infra-down:
	docker compose -f docker-compose.infrastructure.yml down

stack-up:
	docker compose up --build -d

stack-down:
	docker compose down
