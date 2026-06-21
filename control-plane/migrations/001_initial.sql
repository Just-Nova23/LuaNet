BEGIN;

CREATE TABLE IF NOT EXISTS entitlements (
    user_id text PRIMARY KEY,
    tier text NOT NULL CHECK (tier IN ('free', 'premium')),
    expires_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS tunnel_allocations (
    id text PRIMARY KEY,
    user_id text NOT NULL,
    device_id text NOT NULL,
    profile_id text NOT NULL,
    port integer NOT NULL UNIQUE CHECK (port BETWEEN 1024 AND 65535),
    state text NOT NULL CHECK (state IN ('hold', 'active', 'grace')),
    hold_expires_at timestamptz,
    lease_expires_at timestamptz,
    grace_expires_at timestamptz,
    session_hash text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS tunnel_allocations_user_state
    ON tunnel_allocations(user_id, state);
CREATE INDEX IF NOT EXISTS tunnel_allocations_expiries
    ON tunnel_allocations(state, hold_expires_at, lease_expires_at, grace_expires_at);

CREATE TABLE IF NOT EXISTS security_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id text,
    event_type text NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS security_audit_retention ON security_audit(occurred_at);

COMMIT;

