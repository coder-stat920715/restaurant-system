#!/usr/bin/env bash
# Run AFTER order-service, payment-service and kitchen-inventory-service have started once.
for c in orders payments kitchen; do
  echo "Registering $c connector..."
  curl -s -X POST -H 'Content-Type: application/json' --data @$c-connector.json http://localhost:8083/connectors; echo
done
curl -s http://localhost:8083/connectors; echo
