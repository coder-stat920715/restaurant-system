CREATE TABLE IF NOT EXISTS outbox_event (
  id uuid PRIMARY KEY,
  aggregate_type text NOT NULL,
  aggregate_id text NOT NULL,
  event_type text NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS processed_message (
  event_id text PRIMARY KEY,
  processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS payments (
  order_id uuid PRIMARY KEY,
  payment_id text,
  amount numeric(12,2) NOT NULL,
  status text NOT NULL,                     -- CHARGED | FAILED | REVERSED
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz
);
