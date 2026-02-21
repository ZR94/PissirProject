-- Schema for PissirProject database

-- =========================
-- PissirProject - DB schema
-- =========================

-- Optional: elenco caselli gestiti (utile per validazione e seed)
CREATE TABLE IF NOT EXISTS tollbooths (
  id TEXT PRIMARY KEY
);

INSERT INTO tollbooths(id) VALUES
  ('VC_Est'),
  ('VC_Ovest'),
  ('AT_Est'),
  ('AT_Ovest'),
  ('MI_Est'),
  ('MI_Ovest')
ON CONFLICT DO NOTHING;

-- =========================
-- Trips (viaggi)
-- =========================
CREATE TABLE IF NOT EXISTS trips (
  id BIGSERIAL PRIMARY KEY,

  -- ricavati dai topic/payload MQTT
  entry_tollbooth_id TEXT NOT NULL,
  exit_tollbooth_id  TEXT NULL,

  -- identificativi logici
  ticket_id   TEXT NULL,
  telepass_id TEXT NULL,
  plate       TEXT NULL,

  -- timing
  entry_at TIMESTAMPTZ NOT NULL,
  exit_at  TIMESTAMPTZ NULL,

  -- risultato economico
  amount_cents INTEGER NULL,
  currency TEXT NOT NULL DEFAULT 'EUR',

  -- manual exit: paid=true, telepass exit: paid=false + debito in telepass_debts
  paid BOOLEAN NOT NULL DEFAULT FALSE,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- vincoli base
  CONSTRAINT chk_amount_cents_nonnegative CHECK (amount_cents IS NULL OR amount_cents >= 0),
  CONSTRAINT chk_trip_identifier_present CHECK (
    ticket_id IS NOT NULL OR telepass_id IS NOT NULL
  )
);

-- Indici per lookup rapido dei trip "attivi" (exit_at IS NULL)
CREATE INDEX IF NOT EXISTS idx_trips_ticket_active
  ON trips(ticket_id)
  WHERE ticket_id IS NOT NULL AND exit_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_trips_telepass_active
  ON trips(telepass_id)
  WHERE telepass_id IS NOT NULL AND exit_at IS NULL;

-- Indice utile per query/report
CREATE INDEX IF NOT EXISTS idx_trips_entry_exit
  ON trips(entry_tollbooth_id, exit_tollbooth_id);

-- =========================
-- Fares (tariffe)
-- =========================
CREATE TABLE IF NOT EXISTS fares (
  id BIGSERIAL PRIMARY KEY,
  entry_tollbooth_id TEXT NOT NULL,
  exit_tollbooth_id  TEXT NOT NULL,
  amount_cents INTEGER NOT NULL,
  currency TEXT NOT NULL DEFAULT 'EUR',

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT chk_fares_amount_positive CHECK (amount_cents >= 0),
  CONSTRAINT uq_fares UNIQUE(entry_tollbooth_id, exit_tollbooth_id)
);

-- lookup pedaggio per (entry, exit)
CREATE INDEX IF NOT EXISTS idx_fares_entry_exit
  ON fares(entry_tollbooth_id, exit_tollbooth_id);

-- =========================
-- Telepass Debts (debiti)
-- =========================
CREATE TABLE IF NOT EXISTS telepass_debts (
  id BIGSERIAL PRIMARY KEY,

  telepass_id TEXT NOT NULL,
  trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

  amount_cents INTEGER NOT NULL,
  currency TEXT NOT NULL DEFAULT 'EUR',

  status TEXT NOT NULL DEFAULT 'OPEN', -- OPEN | PAID
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT chk_debt_amount_nonnegative CHECK (amount_cents >= 0),
  CONSTRAINT chk_debt_status CHECK (status IN ('OPEN', 'PAID'))
);

CREATE INDEX IF NOT EXISTS idx_telepass_debts_telepass_id ON telepass_debts(telepass_id);
CREATE INDEX IF NOT EXISTS idx_telepass_debts_trip_id ON telepass_debts(trip_id);
CREATE INDEX IF NOT EXISTS idx_telepass_debts_telepass_status
  ON telepass_debts(telepass_id, status);

INSERT INTO fares(entry_tollbooth_id, exit_tollbooth_id, amount_cents)
VALUES
  ('VC_Est', 'MI_Ovest', 720),
  ('VC_Est', 'MI_Est',   650),
  ('MI_Ovest', 'VC_Est', 720),
  ('MI_Est', 'VC_Est',   650)
ON CONFLICT (entry_tollbooth_id, exit_tollbooth_id) DO NOTHING;
