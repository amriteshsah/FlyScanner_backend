-- Credential hashes and revocable opaque bearer sessions.
-- Raw access tokens are never stored in the database.
ALTER TABLE account.users
    ADD COLUMN password_hash VARCHAR(100) NOT NULL;

CREATE TABLE account.access_token_sessions (
    token_hash CHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_access_token_user FOREIGN KEY (user_id)
        REFERENCES account.users (id) ON DELETE CASCADE
);

CREATE INDEX idx_access_token_user_expiry
    ON account.access_token_sessions (user_id, expires_at);

CREATE INDEX idx_price_alert_active_route
    ON alerts.price_alerts (is_active, origin, destination, departure_date);
