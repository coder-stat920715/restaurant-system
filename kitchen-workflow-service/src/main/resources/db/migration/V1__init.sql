-- what the kitchen needs to know about an order (filled from OrderCreated events)
CREATE TABLE IF NOT EXISTS order_snapshot (
  order_id uuid PRIMARY KEY,
  customer_id text NOT NULL,
  location_id text NOT NULL,
  lines jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- the post-saga workflow: QUEUED -> PREPARING -> READY -> SERVED
CREATE TABLE IF NOT EXISTS kitchen_ticket (
  id uuid PRIMARY KEY,
  order_id uuid NOT NULL UNIQUE,
  location_id text NOT NULL,
  status text NOT NULL,
  items jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz,
  updated_by text
);
CREATE INDEX IF NOT EXISTS idx_ticket_location_status ON kitchen_ticket(location_id, status);

CREATE TABLE IF NOT EXISTS processed_message (
  event_id text PRIMARY KEY,
  processed_at timestamptz NOT NULL DEFAULT now()
);
