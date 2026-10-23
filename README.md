# Restaurant Management System

Spring Boot 3.3 / Java 21 microservices: API gateway, orchestrated saga (order, payment, kitchen/inventory),
transactional outbox + Debezium CDC, CQRS (Postgres write model, MongoDB read model), RBAC + ABAC security,
Resilience4j, kitchen workflow, Terraform for AWS. See PROJECT_STRUCTURE.md for what lives where.

## Build
```
mvn clean install -DskipTests      # fast, no Docker needed
mvn clean install                  # also runs unit tests; integration tests need Docker and skip themselves without it
```

## Run locally (Windows / IntelliJ friendly)
1. `cd local-stack`, then `docker compose up -d` (see local-stack/README.md)
2. Start once, in any order: order-service (8081), payment-service (8085), kitchen-inventory-service (8086), kitchen-workflow-service (8087)
3. `register-connectors.cmd` in local-stack (three Debezium outbox connectors)
4. Start order-query-service (8082) and api-gateway (8080)
5. Run `requests.http`, then `requests-kitchen.http` in IntelliJ's HTTP client

Run a module: `mvn -pl order-service spring-boot:run` (or the main class `OrderApplication` in IntelliJ).

## Flows
- Create order: gateway, order-service (order + outbox row in one tx), Debezium, Kafka, saga orchestrator
- Saga: payment-service (calls PSP with retry/circuit breaker), kitchen-inventory-service (reserves stock); failures compensate
- Read side: order-query-service projects events into MongoDB; replay via /admin endpoints
- After COMPLETED: kitchen-workflow-service opens a ticket (QUEUED, PREPARING, READY, SERVED) with ABAC-protected transitions
