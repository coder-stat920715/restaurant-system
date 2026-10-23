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

CREATE TABLE IF NOT EXISTS inventory (
  menu_item_id text PRIMARY KEY,
  stock int NOT NULL CHECK (stock >= 0)
);

CREATE TABLE IF NOT EXISTS reservations (
  order_id uuid PRIMARY KEY,
  status text NOT NULL,                      -- RESERVED | FAILED
  created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO inventory(menu_item_id, stock) VALUES ('burger', 100), ('pizza', 50), ('salad', 20)
ON CONFLICT (menu_item_id) DO NOTHING;
