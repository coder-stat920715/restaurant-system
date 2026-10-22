# Code map (all classes now implemented)

Base package: `com.example.restaurant`

- **order-service**: `order` (Order, OrderService, OrderController, events), `outbox` (JDBC outbox + cleaner), `saga` (orchestrator, listener, idempotency, topics), `security` (RBAC/ABAC), `client` (PaymentServiceClient)
- **order-query-service**: `query` (read model, CDC listener, controller), `config` (security, Kafka error handling)
- **api-gateway**: `gateway` (JWT, key resolver, claim relay, fallback)
- **participants-stub**: fake Payment + Kitchen saga participants for local runs

Deviations from the first architecture doc (all deliberate fixes):
1. Outbox written via `JdbcTemplate` (`?::jsonb`), not a JPA entity (Hibernate can double-encode String JSON columns).
2. Status changes publish one `OrderStatusChanged` event. Re-publishing `OrderCreated` on every transition would restart the saga in a loop.
3. Kafka headers are read from the `ConsumerRecord` as bytes (Debezium writes raw bytes; `@Header String` is unreliable).
4. Saga dedup uses a `processed_message` row committed in the same transaction as the saga step.
