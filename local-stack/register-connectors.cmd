@echo off
REM Run AFTER order-service, payment-service and kitchen-inventory-service have started once (Flyway creates the outbox tables).
for %%C in (orders payments kitchen) do (
  echo Registering %%C connector...
  curl.exe -s -X POST -H "Content-Type: application/json" --data @%%C-connector.json http://localhost:8083/connectors
  echo.
)
curl.exe -s http://localhost:8083/connectors
