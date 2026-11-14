-- Core persistence for search results plus future account, alert and affiliate workflows.

CREATE TABLE account.users (
    id UUID PRIMARY KEY,
    external_subject VARCHAR(255) UNIQUE,
    email VARCHAR(320) UNIQUE,
    full_name VARCHAR(200),
    user_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE search.flight_searches (
    id UUID PRIMARY KEY,
    trip_type VARCHAR(20) NOT NULL,
    cabin_class VARCHAR(20) NOT NULL,
    currency CHAR(3) NOT NULL,
    market CHAR(2) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    adult_count SMALLINT NOT NULL,
    infant_count SMALLINT NOT NULL,
    search_status VARCHAR(30) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    disclaimer VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE search.flight_legs (
    search_id UUID NOT NULL,
    leg_number SMALLINT NOT NULL,
    origin CHAR(3) NOT NULL,
    destination CHAR(3) NOT NULL,
    departure_date DATE NOT NULL,
    CONSTRAINT pk_flight_legs PRIMARY KEY (search_id, leg_number),
    CONSTRAINT fk_flight_legs_search FOREIGN KEY (search_id)
        REFERENCES search.flight_searches (id) ON DELETE CASCADE
);

CREATE TABLE search.search_children (
    search_id UUID NOT NULL,
    child_number SMALLINT NOT NULL,
    age_years SMALLINT NOT NULL,
    CONSTRAINT pk_search_children PRIMARY KEY (search_id, child_number),
    CONSTRAINT fk_search_children_search FOREIGN KEY (search_id)
        REFERENCES search.flight_searches (id) ON DELETE CASCADE
);

CREATE TABLE search.provider_search_statuses (
    search_id UUID NOT NULL,
    provider_code VARCHAR(40) NOT NULL,
    call_status VARCHAR(20) NOT NULL,
    duration_ms BIGINT NOT NULL,
    status_message VARCHAR(300),
    CONSTRAINT pk_provider_search_statuses PRIMARY KEY (search_id, provider_code),
    CONSTRAINT fk_provider_status_search FOREIGN KEY (search_id)
        REFERENCES search.flight_searches (id) ON DELETE CASCADE
);

CREATE TABLE search.flight_offers (
    offer_token VARCHAR(36) PRIMARY KEY,
    search_id UUID NOT NULL,
    provider_code VARCHAR(40) NOT NULL,
    price_amount DECIMAL(14, 2) NOT NULL,
    price_currency CHAR(3) NOT NULL,
    stop_count SMALLINT NOT NULL,
    total_duration_minutes INTEGER NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    affiliate_redirect_available BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_flight_offers_search FOREIGN KEY (search_id)
        REFERENCES search.flight_searches (id) ON DELETE CASCADE
);

CREATE TABLE search.flight_journeys (
    offer_token VARCHAR(36) NOT NULL,
    leg_number SMALLINT NOT NULL,
    origin CHAR(3) NOT NULL,
    destination CHAR(3) NOT NULL,
    departure_date DATE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    stop_count SMALLINT NOT NULL,
    CONSTRAINT pk_flight_journeys PRIMARY KEY (offer_token, leg_number),
    CONSTRAINT fk_flight_journeys_offer FOREIGN KEY (offer_token)
        REFERENCES search.flight_offers (offer_token) ON DELETE CASCADE
);

CREATE TABLE search.flight_segments (
    offer_token VARCHAR(36) NOT NULL,
    leg_number SMALLINT NOT NULL,
    segment_number SMALLINT NOT NULL,
    origin CHAR(3) NOT NULL,
    destination CHAR(3) NOT NULL,
    carrier_code VARCHAR(8) NOT NULL,
    flight_number VARCHAR(20) NOT NULL,
    departure_at TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    CONSTRAINT pk_flight_segments PRIMARY KEY (offer_token, leg_number, segment_number),
    CONSTRAINT fk_flight_segments_journey FOREIGN KEY (offer_token, leg_number)
        REFERENCES search.flight_journeys (offer_token, leg_number) ON DELETE CASCADE
);

CREATE TABLE alerts.price_alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    origin CHAR(3) NOT NULL,
    destination CHAR(3) NOT NULL,
    trip_type VARCHAR(20) NOT NULL,
    departure_date DATE NOT NULL,
    return_date DATE,
    target_price DECIMAL(14, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_price_alerts_user FOREIGN KEY (user_id)
        REFERENCES account.users (id),
    CONSTRAINT ck_price_alert_target_price CHECK (target_price >= 0)
);

CREATE TABLE affiliate.click_tracking (
    id UUID PRIMARY KEY,
    offer_token VARCHAR(36) NOT NULL,
    provider_code VARCHAR(40) NOT NULL,
    affiliate_sub_id VARCHAR(120),
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_affiliate_click_offer FOREIGN KEY (offer_token)
        REFERENCES search.flight_offers (offer_token)
);

CREATE TABLE notifications.notification_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    alert_id UUID,
    notification_type VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    recipient_reference_hash VARCHAR(128),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(80),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id)
        REFERENCES account.users (id),
    CONSTRAINT fk_notification_alert FOREIGN KEY (alert_id)
        REFERENCES alerts.price_alerts (id)
);

CREATE INDEX idx_flight_searches_created_at
    ON search.flight_searches (created_at);
CREATE INDEX idx_flight_legs_route_date
    ON search.flight_legs (origin, destination, departure_date);
CREATE INDEX idx_flight_offers_search_price
    ON search.flight_offers (search_id, price_amount);
CREATE INDEX idx_flight_offers_expiry
    ON search.flight_offers (expires_at);
CREATE INDEX idx_price_alerts_user_active
    ON alerts.price_alerts (user_id, is_active);
CREATE INDEX idx_notification_history_user_attempted
    ON notifications.notification_history (user_id, attempted_at);
