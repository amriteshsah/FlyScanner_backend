ALTER TABLE alerts.price_alerts
    ADD COLUMN market CHAR(2) NOT NULL DEFAULT 'IN';
