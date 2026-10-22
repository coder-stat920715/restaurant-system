CREATE TABLE IF NOT EXISTS orders (
  id uuid PRIMARY KEY,
  customer_id text NOT NULL,
  restaurant_location_id text NOT NULL,
  status text NOT NULL,
  total numeric(12,2) NOT NULL,
  lines jsonb,
  created_at timestamptz NOT NULL,
  updated_at timestamptz,
  version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS outbox_event (
  id uuid PRIMARY KEY,
  aggregate_type text NOT NULL,
  aggregate_id text NOT NULL,
  event_type text NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS saga_instance (
  order_id uuid PRIMARY KEY,
  state text NOT NULL,
  payment_id text,
  failure_reason text,
  updated_at timestamptz DEFAULT now(),
  version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS processed_message (
  event_id text PRIMARY KEY,
  processed_at timestamptz NOT NULL DEFAULT now()
);
