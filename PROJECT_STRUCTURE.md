# Project structure: which class lives where
Root: `pom.xml` (parent, 6 modules). Base package `com.example.restaurant` except payment (`com.example.payment`), kitchen-inventory (`com.example.kitchen`), workflow (`com.example.workflow`).

## api-gateway
Edge: JWT validation, Redis rate limiting, routing. Port 8080.
- `com/example/restaurant` (main)
  - `ApiGatewayApplication.java`
- `com/example/restaurant/gateway` (main): Gateway security, rate-limit key resolver, kitchen route, fallback
  - `FallbackController.java`, `GatewaySecurityConfig.java`, `KitchenWorkflowRoute.java`
- `.` (files)
  - `pom.xml`
- `src/main/resources` (files)
  - `application.yml`

## order-service
Command side of CQRS + saga orchestrator + outbox. Port 8081. DB: orders.
- `com/example/restaurant` (main)
  - `OrderApplication.java`
- `com/example/restaurant/order` (main): Order aggregate, state machine, REST API, events
  - `CreateOrderCommand.java`, `Order.java`, `OrderController.java`, `OrderEvent.java`, `OrderLine.java`, `OrderRepository.java`, `OrderService.java`, `OrderStatus.java`
- `com/example/restaurant/outbox` (main): Transactional outbox writer and cleaner
  - `OutboxCleaner.java`, `OutboxRepository.java`
- `com/example/restaurant/replay` (main): Re-publish all orders as OrderSnapshot events (read-model rebuild)
  - `AdminReplayController.java`, `OrderReplayService.java`, `OrderSnapshot.java`
- `com/example/restaurant/saga` (main): Saga orchestrator, state, timeouts, Kafka listeners, idempotency
  - `Hdr.java`, `IdempotentProcessor.java`, `KafkaTopicsConfig.java`, `OrderSagaOrchestrator.java`, `SagaInstance.java`, `SagaListener.java`, `SagaMessage.java`, `SagaRepository.java`, `SagaState.java`, `SagaTimeoutScheduler.java`
- `com/example/restaurant/security` (main): Spring Security 6 config, RBAC hierarchy, ABAC PermissionEvaluator, method-security handler
  - `CustomMethodSecurityExpressionHandler.java`, `DefaultLocationHoursProvider.java`, `LocationHoursProvider.java`, `MethodSecurityConfig.java`, `Permission.java`, `RestaurantPermissionEvaluator.java`, `SecurityConfig.java`, `SecurityContextAttributes.java`
- `.` (files)
  - `pom.xml`
- `src/main/resources` (files)
  - `application.yml`
- `src/main/resources/db/migration` (files)
  - `V1__init.sql`
- `com/example/restaurant` (test)
  - `OrderSagaIntegrationTest.java`
- `com/example/restaurant/order` (test): Order aggregate, state machine, REST API, events
  - `OrderStateMachineTest.java`
- `com/example/restaurant/security` (test): Spring Security 6 config, RBAC hierarchy, ABAC PermissionEvaluator, method-security handler
  - `RestaurantPermissionEvaluatorTest.java`
- `com/example/restaurant/support` (test)
  - `SagaTestSupport.java`

## order-query-service
Query side of CQRS: Mongo read model, CDC projection, admin rebuild. Port 8082.
- `com/example/restaurant` (main)
  - `OrderQueryApplication.java`
- `com/example/restaurant/admin` (main): Clear / stats endpoints for read-model rebuild
  - `ReadModelAdminController.java`
- `com/example/restaurant/config` (main): Security and Kafka error handling (retry + dead-letter)
  - `KafkaConfig.java`, `MethodSecurityConfig.java`, `SecurityConfig.java`
- `com/example/restaurant/query` (main): Read model documents, CDC listeners, query REST API
  - `Hdr.java`, `OrderEvents.java`, `OrderQueryController.java`, `OrderReadModelSyncListener.java`, `OrderSnapshotEvent.java`, `OrderSnapshotListener.java`, `OrderView.java`, `OrderViewRepository.java`, `ProcessedEvent.java`
- `.` (files)
  - `pom.xml`
- `src/main/resources` (files)
  - `application.yml`
- `com/example/restaurant/query` (test): Read model documents, CDC listeners, query REST API
  - `OrderQueryApiIntegrationTest.java`, `OrderReadModelSyncListenerTest.java`, `OrderSnapshotListenerTest.java`

## payment-service
Saga participant: charges via PSP client (Resilience4j), refunds, own DB + outbox. Port 8085. DB: payments.
- `com/example/payment` (main)
  - `PaymentApplication.java`, `PaymentService.java`
- `com/example/payment/messaging` (main): Saga command listener, idempotency, Kafka config
  - `Hdr.java`, `IdempotentProcessor.java`, `KafkaConfig.java`, `PaymentCommandListener.java`
- `com/example/payment/outbox` (main): Outbox writer and cleaner
  - `OutboxCleaner.java`, `OutboxRepository.java`
- `com/example/payment/psp` (main): PSP client with Retry + CircuitBreaker, fake PSP
  - `FakePspController.java`, `PspClient.java`, `PspDeclinedException.java`
- `.` (files)
  - `pom.xml`
- `src/main/resources` (files)
  - `application.yml`
- `src/main/resources/db/migration` (files)
  - `V1__init.sql`

## kitchen-inventory-service
Saga participant: all-or-nothing stock reservation, own DB + outbox. Port 8086. DB: kitchen.
- `com/example/kitchen` (main)
  - `InventoryController.java`, `InventoryService.java`, `KitchenApplication.java`
- `com/example/kitchen/messaging` (main): Saga command listener, idempotency, Kafka config
  - `Hdr.java`, `IdempotentProcessor.java`, `KafkaConfig.java`, `KitchenCommandListener.java`
- `com/example/kitchen/outbox` (main): Outbox writer and cleaner
  - `OutboxCleaner.java`, `OutboxRepository.java`
- `.` (files)
  - `pom.xml`
- `src/main/resources` (files)
  - `application.yml`
- `src/main/resources/db/migration` (files)
  - `V1__init.sql`

## kitchen-workflow-service
Post-saga kitchen tickets with ABAC-guarded WRITE/SERVE. Port 8087. DB: workflow.
- `com/example/workflow` (main)
  - `WorkflowApplication.java`
- `com/example/workflow/messaging` (main): Order event listener that opens tickets, idempotency, Kafka config
  - `Hdr.java`, `IdempotentProcessor.java`, `KafkaConfig.java`, `OrderEventListener.java`
- `com/example/workflow/security` (main): Security config and ticket PermissionEvaluator
  - `DefaultLocationHoursProvider.java`, `LocationHoursProvider.java`, `MethodSecurityConfig.java`, `SecurityConfig.java`, `TicketPermissionEvaluator.java`
- `com/example/workflow/ticket` (main): Ticket model, repository, service (ABAC annotations), REST controller
  - `KitchenTicket.java`, `TicketController.java`, `TicketRepository.java`, `TicketService.java`, `TicketStatus.java`
- `.` (files)
  - `pom.xml`
- `src/main/resources` (files)
  - `application.yml`
- `src/main/resources/db/migration` (files)
  - `V1__init.sql`
- `com/example/workflow/security` (test): Security config and ticket PermissionEvaluator
  - `TicketPermissionEvaluatorTest.java`
- `com/example/workflow/ticket` (test): Ticket model, repository, service (ABAC annotations), REST controller
  - `TicketStatusTest.java`

## local-stack
`docker-compose.yml`, `docker-compose.override.yml`, `db/` (create databases), `*-connector.json` (Debezium), `keycloak/restaurant-realm.json`, `register-connectors.cmd|sh`.

## infra/terraform
AWS: network, security groups, WAF/CloudFront/Route 53/Shield, public ALB, ECS Fargate + Cloud Map, RDS, Redis, DocumentDB, MSK + Debezium (MSK Connect).

## Root files
`requests.http`, `requests-kitchen.http` (IntelliJ HTTP client), `Dockerfile` (one recipe for every module, `--build-arg MODULE=...`).
