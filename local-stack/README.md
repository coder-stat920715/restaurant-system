# Local infrastructure

| Service | URL / port |
|---|---|
| Postgres (logical WAL; databases orders, payments, kitchen, workflow) | localhost:5432  postgres/postgres |
| Kafka (host listener) | localhost:29092 |
| Kafka Connect / Debezium | http://localhost:8083 |
| Kafka UI | http://localhost:8090 |
| MongoDB | localhost:27017 |
| Redis | localhost:6379 |
| Keycloak (realm `restaurant` imported) | http://localhost:8180  admin/admin |

Keycloak users (password `password`): alice, bob (CUSTOMER) / chef1 (CHEF, loc-1) / manager1 (RESTAURANT_MANAGER, loc-1) / admin1 (SUPER_ADMIN).

## Start
```
docker compose down -v          # only needed if you ran an older version of this stack
docker compose up -d            # also applies docker-compose.override.yml (workflow database)
```
Existing Postgres volume without the `workflow` database: `docker compose exec postgres psql -U postgres -c "CREATE DATABASE workflow;"`.

Flyway (inside each service) creates the tables, so register the Debezium connectors only after
order-service, payment-service and kitchen-inventory-service have started once:
```
register-connectors.cmd         # Windows
./register-connectors.sh        # Linux / macOS / Git Bash
```
