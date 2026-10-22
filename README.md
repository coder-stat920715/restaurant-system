# Pending implementation: drop-in files

Unzip over the project root (paths match). Then:

## Delete (obsolete)
- `order-service/src/main/java/com/example/restaurant/client/` (4 files; the PSP client now lives in payment-service)
- `participants-stub/` module (real Payment and Kitchen/Inventory services replace it; never run both)

## Start order (Windows)
1. `cd local-stack` then `docker compose down -v` (new DB layout) then `docker compose up -d`
2. Start once: order-service (8081), payment-service (8085), kitchen-inventory-service (8086)  -> Flyway creates the tables
3. `cd local-stack` then `register-connectors.cmd`  -> three Debezium outbox connectors (orders, payments, kitchen)
4. Start order-query-service (8082) and api-gateway (8080)
5. Open `requests.http` in IntelliJ and run the requests top to bottom.

## Saga message flow
order-service --(outbox/CDC)--> restaurant.events.saga-payment  --> payment-service --(outbox/CDC)--> restaurant.events.saga-reply
order-service --(outbox/CDC)--> restaurant.events.saga-kitchen  --> kitchen-service --(outbox/CDC)--> restaurant.events.saga-reply
Every command/reply is keyed by orderId, so per-order ordering is preserved.

## Tunables
- `saga.timeout` (order-service, default PT2M): a stuck saga is compensated.
- `psp.fake.failure-rate` (payment-service): e.g. 0.5 to watch retry + circuit breaker.
- `psp.fake.decline-above` (default 500).
