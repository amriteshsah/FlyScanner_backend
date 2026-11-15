CREATE TABLE account.saved_flights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    offer_token VARCHAR(36) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_saved_flights_user FOREIGN KEY (user_id)
        REFERENCES account.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_flights_offer FOREIGN KEY (offer_token)
        REFERENCES search.flight_offers (offer_token) ON DELETE CASCADE,
    CONSTRAINT uq_saved_flights_user_offer UNIQUE (user_id, offer_token)
);

CREATE INDEX idx_saved_flights_user_saved_at
    ON account.saved_flights (user_id, saved_at DESC);
